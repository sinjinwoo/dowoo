import random
import re
from typing import AsyncIterator, Optional

from google import genai
from google.genai import types

ASCII_PRINTABLE_RE = re.compile(r"^[\x21-\x7e]+$")
STATUS_RE = re.compile(r"\b(4\d{2}|5\d{2})\b")


def resolve_system_prompt(template: str, memo: str) -> str:
    return template.replace("{{memo}}", memo or "")


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
    original_text: str,
    thinking_budget: Optional[int],
    total_lines: int,
) -> AsyncIterator[dict]:
    """한 (모델, 키) 조합으로 한 번 시도한다. 성공하면 done까지 yield, 실패하면 result 이벤트로 원인만 yield."""
    client = genai.Client(api_key=api_key)
    config_kwargs: dict = {"system_instruction": resolved_prompt}
    if thinking_budget is not None:
        config_kwargs["thinking_config"] = types.ThinkingConfig(thinking_budget=thinking_budget)

    buffer = ""
    translated_lines: list[str] = []

    try:
        stream = await client.aio.models.generate_content_stream(
            model=model,
            contents=original_text,
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
                percent = min(95, int(len(translated_lines) / total_lines * 100))
                yield {"event": "line", "data": {"index": len(translated_lines) - 1, "text": line}}
                yield {"event": "progress", "data": {"percent": percent}}

        if buffer:
            translated_lines.append(buffer)
            yield {"event": "line", "data": {"index": len(translated_lines) - 1, "text": buffer}}

        if translated_lines:
            yield {"event": "progress", "data": {"percent": 100}}
            yield {"event": "done", "data": {"translatedText": "\n".join(translated_lines)}}
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

    last_error: Optional[dict] = None

    for model in models:
        start_index = random.randrange(len(keys))
        for attempt in range(len(keys)):
            key_index = (start_index + attempt) % len(keys)

            retryable = True
            async for item in _try_one_key(
                model, keys[key_index], resolved_prompt, original_text, thinking_budget, total_lines
            ):
                if item["event"] == "_attempt_failed":
                    last_error = item["data"]
                    retryable = item["retryable"]
                    break
                yield item
            else:
                # for...else: break 없이 스트림이 끝났다 = done까지 정상적으로 yield됨
                return

            if not retryable:
                # 키 문제가 아닌 오류(예: 잘못된 모델 이름)는 이 모델의 나머지 키를 시도할 필요가 없다.
                break

        # 이 모델의 키를 모두 소진했거나(정상 루프 종료) 키 무관 오류로 중단됨 - 다음 모델로 넘어간다

    yield {"event": "error", "data": last_error or {"code": "UPSTREAM_ERROR", "message": "번역에 실패했습니다."}}
