import asyncio
import random
import re
from typing import AsyncIterator, Optional

from google import genai

ASCII_PRINTABLE_RE = re.compile(r"^[\x21-\x7e]+$")
STATUS_RE = re.compile(r"\b(4\d{2}|5\d{2})\b")
HANGUL_RE = re.compile(r"[가-힣]")
CJK_RE = re.compile(r"[가-힣一-鿿㐀-䶿]")
# 판단하기에 텍스트가 너무 짧으면(고유명사 한두 개 등) 오탐 위험이 커서 검사를 건너뛴다.
UNTRANSLATED_CHECK_MIN_CHARS = 20
# 이 비율 밑으로 한글이 나오면 "원문을 그대로 베낀" 것으로 간주한다 - 정상 번역도 한자
# 고유명사를 섞어 쓰지만 이 정도로 한글 비율이 낮아지지는 않는다.
UNTRANSLATED_HANGUL_RATIO = 0.3
# 미번역(원문 그대로 베끼기) 감지 시 같은 (모델, 키) 조합으로 재시도할 최대 횟수 - 샘플링
# 변동성 때문에 한 번 실패했다고 그 키/모델 자체가 문제인 건 아닌 경우가 많아, 바로 다음
# 키로 넘기기 전에 몇 번 더 찔러본다. 사용자에게는 어떤 시도도 화면에 노출되지 않는다.
UNTRANSLATED_MAX_ATTEMPTS = 3
# 같은 (모델, 키)로 재시도할 때마다 대기하는 시간(초) - 딜레이 없이 연달아 3번 쏘면 RPM(분당
# 요청 수) 한도에 걸릴 수 있어, 재시도 횟수가 늘수록 더 오래 쉰다. 마지막 시도 전에만 대기하면
# 되므로 실제로는 앞의 UNTRANSLATED_MAX_ATTEMPTS - 1개만 쓰인다.
UNTRANSLATED_RETRY_BACKOFF_SECONDS = (5, 10, 15)
# 서로 다른 키 2개가 같은 모델에서 똑같이 "미번역" 판정을 받으면 키 문제가 아니라 그
# 모델이 이 콘텐츠를 결정론적으로 못 다루는 것으로 본다(docs/troubleshooting/29 참고) -
# 이 경우 남은 키를 마저 태우지 않고 바로 다음 모델로 넘어가 낭비 시간을 줄인다.
UNTRANSLATED_MODEL_ESCALATE_AFTER_KEYS = 2

# 챕터 전체를 한 번의 요청으로 보내면 아주 긴 화에서 응답이 느려지거나(첫 줄이 나오기까지 몇 분씩
# "생각"만 하는 구간, docs/troubleshooting/18 참고) 중간에 끊길 위험이 커진다 - 원문을 1만자
# 단위로 잘라 순차적으로 번역한다. 줄 단위 1:1 대응이 깨지면 안 되므로 반드시 줄 경계에서만 자른다.
CHUNK_SIZE_CHARS = 10000

# 2026-07-23: generateContent → Interactions API(client.interactions.create)로 전환.
# Interactions API의 GenerationConfig에는 정수형 thinking_budget 필드가 아예 없고 문자열 enum
# thinking_level("minimal"/"low"/"medium"/"high")만 존재한다(실제 SDK google.genai.interactions.
# GenerationConfig 구조로 확인, 2026-07-23). 기존 UI/DB는 여전히 정수 예산만 저장하므로 모든
# 모델 호출 시점에 이를 4단계 enum으로 근사 변환한다.
def _budget_to_thinking_level(thinking_budget: int) -> str:
    """기존 UI/DB는 여전히 정수 예산(thinkingBudget)만 저장하므로, 신세대 모델 호출 시점에
    이를 4단계 enum으로 근사 변환한다. -1(무제한/다이내믹 사고)은 가장 높은 단계로 매핑한다."""
    if thinking_budget < 0:
        return "high"
    if thinking_budget == 0:
        return "minimal"
    if thinking_budget <= 4096:
        return "low"
    if thinking_budget <= 16384:
        return "medium"
    return "high"


def resolve_system_prompt(template: str, memo: str) -> str:
    return template.replace("{{memo}}", memo or "")


def _split_into_chunks(text: str, chunk_size: int = CHUNK_SIZE_CHARS) -> list[str]:
    lines = text.split("\n")
    chunks: list[str] = []
    current: list[str] = []
    current_len = 0
    for line in lines:
        extra = len(line) + (1 if current else 0)  # 줄바꿈으로 다시 이어붙일 때의 길이까지 계산
        if current and current_len + extra > chunk_size:
            chunks.append("\n".join(current))
            current = [line]
            current_len = len(line)
        else:
            current.append(line)
            current_len += extra
    if current:
        chunks.append("\n".join(current))
    return chunks or [""]


def _get_status(exc: Exception) -> Optional[int]:
    for attr in ("status_code", "code"):
        value = getattr(exc, attr, None)
        if isinstance(value, int):
            return value
    match = STATUS_RE.search(str(exc))
    return int(match.group(1)) if match else None


def _is_auth_or_quota_error(exc: Exception) -> bool:
    status = _get_status(exc)
    if status is not None:
        return status in (400, 401, 403, 429)
    message = str(exc).lower()
    return any(keyword in message for keyword in ("api key", "permission", "quota", "rate limit"))


def _looks_untranslated(text: str) -> bool:
    """일부 모델(특히 flash-lite 계열)이 지침을 무시하고 원문(중국어 등)을 그대로 베껴
    내보내는 경우가 있다 - 한글 비율이 비정상적으로 낮으면 번역 실패로 간주해 재시도한다."""
    cjk_count = len(CJK_RE.findall(text))
    if cjk_count < UNTRANSLATED_CHECK_MIN_CHARS:
        return False
    hangul_count = len(HANGUL_RE.findall(text))
    return (hangul_count / cjk_count) < UNTRANSLATED_HANGUL_RATIO


def _to_error_payload(exc: Exception) -> dict:
    status = _get_status(exc)
    if status == 400:
        return {"code": "INVALID_API_KEY", "message": "API 키가 올바르지 않습니다."}
    if status in (401, 403):
        return {"code": "INVALID_API_KEY", "message": "API 키가 올바르지 않거나 사용 권한이 없습니다."}
    if status == 429:
        return {"code": "QUOTA_EXCEEDED", "message": "구글 서버 사용량 한도를 초과했습니다."}
    if status is not None and status >= 500:
        return {"code": "UPSTREAM_ERROR", "message": "구글 서버에 일시적인 오류가 발생했습니다."}
    return {"code": "UPSTREAM_ERROR", "message": f"번역 중 알 수 없는 오류가 발생했습니다: {exc}"}


async def _try_one_key(
    model: str,
    api_key: str,
    resolved_prompt: str,
    chunk_text: str,
    thinking_budget: Optional[int],
) -> AsyncIterator[dict]:
    """청크 하나를 한 (모델, 키) 조합으로 시도한다. 줄이 도착하는 대로 바로 _line을 내보내
    실시간 스트리밍을 유지한다 - 청크 전체를 모았다가 한 번에 내보내면 화면이 멈춰있다가
    번역이 다 끝나야 한꺼번에 나타나는 것처럼 보인다.
    다만 원문을 그대로 베끼는 응답(예: flash-lite가 지침을 무시하고 중국어 원문을 그대로
    반환하는 경우)인지 판단할 만큼의 분량(UNTRANSLATED_CHECK_MIN_CHARS)이 모이기 전까지는
    줄을 버퍼링했다가 판정 후 한꺼번에 내보낸다 - 체감 지연은 첫 한두 줄 정도라 실시간성은
    거의 그대로 유지되면서도, 실패로 판정된 줄은 프론트에 노출되기 전에 걸러낼 수 있다.
    미번역 판정은 같은 (모델, 키)로 최대 UNTRANSLATED_MAX_ATTEMPTS번까지 조용히 재시도한다 -
    키/모델 자체보다 샘플링 변동성 때문인 경우가 많아, 바로 다음 키로 넘기기 전에 몇 번
    더 찔러보는 편이 저렴하고 효과적이다. 이 재시도들은 사용자 화면에 전혀 노출되지 않는다.
    재시도 사이에는 UNTRANSLATED_RETRY_BACKOFF_SECONDS만큼 대기한다 - 같은 키로 딜레이 없이
    연달아 요청하면 RPM 한도에 걸릴 수 있기 때문이다. 다른 키로 넘어갈 때는 별도 쿼터
    버킷이라 보고 대기 없이 바로 요청한다."""
    client = genai.Client(api_key=api_key)
    generation_config: dict = {}
    if thinking_budget is not None:
        generation_config["thinking_level"] = _budget_to_thinking_level(thinking_budget)

    for attempt in range(UNTRANSLATED_MAX_ATTEMPTS):
        is_last_attempt = attempt == UNTRANSLATED_MAX_ATTEMPTS - 1
        buffer = ""
        translated_lines: list[str] = []
        pending_lines: list[str] = []
        pending_cjk_count = 0
        passed_check = False
        failed_untranslated = False

        def _consume(line: str) -> bool:
            """줄 하나를 판정 버퍼에 추가한다. False면 원문을 그대로 베낀 것으로 판단되어
            이 시도 전체를 실패 처리해야 한다는 뜻이다."""
            nonlocal pending_cjk_count, passed_check
            pending_lines.append(line)
            pending_cjk_count += len(CJK_RE.findall(line))
            if passed_check or pending_cjk_count < UNTRANSLATED_CHECK_MIN_CHARS:
                return True
            return not _looks_untranslated("\n".join(pending_lines))

        try:
            stream = await client.aio.interactions.create(
                model=model,
                input=chunk_text,
                stream=True,
                system_instruction=resolved_prompt,
                **({"generation_config": generation_config} if generation_config else {}),
            )
            async for event in stream:
                if event.event_type != "step.delta":
                    continue
                delta = event.delta
                if getattr(delta, "type", None) != "text":
                    continue
                text = delta.text
                if not text:
                    continue
                buffer += text
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    if not _consume(line):
                        failed_untranslated = True
                        break
                    if not passed_check and pending_cjk_count >= UNTRANSLATED_CHECK_MIN_CHARS:
                        passed_check = True
                    if passed_check:
                        for pending in pending_lines:
                            translated_lines.append(pending)
                            yield {"event": "_line", "data": pending}
                        pending_lines = []
                if failed_untranslated:
                    break

            if not failed_untranslated and buffer:
                if _consume(buffer):
                    passed_check = True  # 스트림이 끝났으니 남은 버퍼는 그대로 확정한다.
                    for pending in pending_lines:
                        translated_lines.append(pending)
                        yield {"event": "_line", "data": pending}
                    pending_lines = []
                else:
                    failed_untranslated = True

            if failed_untranslated:
                if not is_last_attempt:
                    # 같은 (모델, 키)로 조용히 재시도 - 아직 아무 줄도 내보내지 않았다. 딜레이 없이
                    # 바로 재요청하면 RPM 한도에 걸릴 수 있어 재시도 전에 잠시 쉰다.
                    await asyncio.sleep(UNTRANSLATED_RETRY_BACKOFF_SECONDS[attempt])
                    continue
                yield {
                    "event": "_attempt_failed",
                    "data": {"code": "UPSTREAM_ERROR", "message": "모델이 번역 대신 원문을 그대로 반환했습니다."},
                    "retryable": True,
                    "reason": "untranslated",
                }
                return

            if translated_lines:
                return

            # 일부 SDK 오류는 예외를 던지지 않고 빈 스트림만 반환한다(예: 무효화된 키, preview 모델 결제 미설정 등).
            yield {
                "event": "_attempt_failed",
                "data": {"code": "INVALID_API_KEY", "message": "API 키가 올바르지 않거나 응답이 비어 있습니다."},
                "retryable": True,
            }
            return
        except Exception as exc:
            error = _to_error_payload(exc)
            yield {"event": "_attempt_failed", "data": error, "retryable": _is_auth_or_quota_error(exc)}
            return


# api-spec.md §9.2 구현: dowoo/src/ai/geminiClient.ts + prompt.ts 로직 이식 + 모델 폴백 추가.
# "키 우선, 모델 다음" 순서 - 모델 하나마다 키를 전부 순회(무작위 시작 + 순차 폴백)하고,
# 그 모델의 키를 다 소진해야만 다음 모델로 넘어간다. 인증/키/응답없음 계열 오류만 다음 키로,
# 그 외(예: 모델 이름 자체가 잘못됨) 오류는 그 모델의 남은 키를 건너뛰고 바로 다음 모델로 넘어간다.
#
# 원문은 1만자 단위 청크로 나눠 순차 번역한다(전체 챕터를 한 번에 보내지 않음). 청크마다 모델/키
# 순환을 처음부터 다시 하지 않고, 직전 청크에서 성공한 조합부터 이어서 시도한다 - 이미 잘 되는
# 조합이 있는데 매 청크마다 실패를 반복할 이유가 없다.
async def translate_stream(
    api_keys: list[str],
    models: list[str],
    system_prompt: str,
    translation_note: str,
    original_text: str,
    thinking_budget: Optional[int],
) -> AsyncIterator[dict]:
    keys = [k.strip() for k in api_keys if k.strip() and ASCII_PRINTABLE_RE.match(k.strip())]
    if not keys:
        yield {"event": "error", "data": {"code": "INVALID_API_KEY", "message": "번역에 사용할 API 키가 없습니다."}}
        return
    if not models:
        yield {"event": "error", "data": {"code": "UPSTREAM_ERROR", "message": "번역에 사용할 모델이 지정되지 않았습니다."}}
        return

    resolved_prompt = resolve_system_prompt(system_prompt, translation_note or "")
    total_lines = max(1, len(original_text.split("\n")))
    yield {"event": "start", "data": {"totalLines": total_lines}}

    chunks = _split_into_chunks(original_text)
    all_lines: list[str] = []
    line_count = 0
    model_start_index = 0
    key_start_index = random.randrange(len(keys))

    for chunk_text in chunks:
        last_error: Optional[dict] = None
        chunk_succeeded = False

        for model_offset in range(len(models)):
            model_index = (model_start_index + model_offset) % len(models)
            model = models[model_index]

            retryable = True
            untranslated_failure_count = 0
            for key_offset in range(len(keys)):
                key_index = (key_start_index + key_offset) % len(keys)

                attempt_failed = None
                async for item in _try_one_key(
                    model, keys[key_index], resolved_prompt, chunk_text, thinking_budget
                ):
                    if item["event"] == "_attempt_failed":
                        attempt_failed = item
                        break
                    # _line - 도착하는 대로 바로 실제 line/progress 이벤트로 내보내 실시간
                    # 스트리밍을 유지한다(청크가 끝날 때까지 모아뒀다가 한 번에 내보내지 않음).
                    line = item["data"]
                    all_lines.append(line)
                    yield {"event": "line", "data": {"index": line_count, "text": line}}
                    line_count += 1
                    percent = min(95, int(line_count / total_lines * 100))
                    yield {"event": "progress", "data": {"percent": percent}}

                if attempt_failed is not None:
                    last_error = attempt_failed["data"]
                    retryable = attempt_failed["retryable"]
                    if not retryable:
                        break
                    if attempt_failed.get("reason") == "untranslated":
                        untranslated_failure_count += 1
                        if untranslated_failure_count >= UNTRANSLATED_MODEL_ESCALATE_AFTER_KEYS:
                            break
                    continue

                model_start_index = model_index
                # 성공한 키를 그대로 또 쓰지 않고 다음 키로 넘긴다 - 안 그러면 청크가 많은 긴
                # 챕터에서 계속 같은 키에만 요청이 몰려 RPM(분당 요청 수) 한도에 걸리기 쉽다.
                # 모델은 그대로 유지한다(청크마다 다른 모델을 쓰면 한 챕터 안에서 번역 톤이
                # 달라질 수 있고, RPM 쿼터는 보통 키+모델 조합별이라 어차피 모델을 유지해도
                # 키만 바꾸면 그 모델의 부하가 여러 키로 분산된다).
                key_start_index = (key_index + 1) % len(keys)
                chunk_succeeded = True
                break

            if chunk_succeeded:
                break

        if not chunk_succeeded:
            yield {"event": "error", "data": last_error or {"code": "UPSTREAM_ERROR", "message": "번역에 실패했습니다."}}
            return

    yield {"event": "progress", "data": {"percent": 100}}
    yield {"event": "done", "data": {"translatedText": "\n".join(all_lines)}}
