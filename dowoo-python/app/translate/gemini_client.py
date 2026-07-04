import random
import re
from typing import AsyncIterator, Optional

from google import genai
from google.genai import types

ASCII_PRINTABLE_RE = re.compile(r"^[\x21-\x7e]+$")
STATUS_RE = re.compile(r"\b(4\d{2}|5\d{2})\b")

# 챕터 전체를 한 번의 요청으로 보내면 아주 긴 화에서 응답이 느려지거나(첫 줄이 나오기까지 몇 분씩
# "생각"만 하는 구간, docs/troubleshooting/18 참고) 중간에 끊길 위험이 커진다 - 원문을 1만자
# 단위로 잘라 순차적으로 번역한다. 줄 단위 1:1 대응이 깨지면 안 되므로 반드시 줄 경계에서만 자른다.
CHUNK_SIZE_CHARS = 10000


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
    """청크 하나를 한 (모델, 키) 조합으로 시도한다. 스트리밍 도중 실패하면 그때까지 받은 줄은
    버리고 _attempt_failed만 알린다 - 실패한 시도의 부분 출력이 다음 재시도 결과와 섞이면 안 된다."""
    client = genai.Client(api_key=api_key)
    config_kwargs: dict = {"system_instruction": resolved_prompt}
    if thinking_budget is not None:
        config_kwargs["thinking_config"] = types.ThinkingConfig(thinking_budget=thinking_budget)

    buffer = ""
    translated_lines: list[str] = []

    try:
        stream = await client.aio.models.generate_content_stream(
            model=model,
            contents=chunk_text,
            config=types.GenerateContentConfig(**config_kwargs),
        )
        async for chunk in stream:
            text = chunk.text
            if not text:
                continue
            buffer += text
            while "\n" in buffer:
                line, buffer = buffer.split("\n", 1)
                translated_lines.append(line)

        if buffer:
            translated_lines.append(buffer)

        if translated_lines:
            yield {"event": "_chunk_done", "data": {"lines": translated_lines}}
            return

        # 일부 SDK 오류는 예외를 던지지 않고 빈 스트림만 반환한다(예: 무효화된 키, preview 모델 결제 미설정 등).
        yield {
            "event": "_attempt_failed",
            "data": {"code": "INVALID_API_KEY", "message": "API 키가 올바르지 않거나 응답이 비어 있습니다."},
            "retryable": True,
        }
    except Exception as exc:
        error = _to_error_payload(exc)
        yield {"event": "_attempt_failed", "data": error, "retryable": _is_auth_or_quota_error(exc)}


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
            for key_offset in range(len(keys)):
                key_index = (key_start_index + key_offset) % len(keys)

                attempt_failed = None
                chunk_lines: list[str] = []
                async for item in _try_one_key(
                    model, keys[key_index], resolved_prompt, chunk_text, thinking_budget
                ):
                    if item["event"] == "_attempt_failed":
                        attempt_failed = item
                    else:
                        chunk_lines = item["data"]["lines"]

                if attempt_failed is not None:
                    last_error = attempt_failed["data"]
                    retryable = attempt_failed["retryable"]
                    if not retryable:
                        break
                    continue

                for line in chunk_lines:
                    all_lines.append(line)
                    yield {"event": "line", "data": {"index": line_count, "text": line}}
                    line_count += 1
                    percent = min(95, int(line_count / total_lines * 100))
                    yield {"event": "progress", "data": {"percent": percent}}

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
