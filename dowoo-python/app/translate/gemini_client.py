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


# api-spec.md §9.2 구현: dowoo/src/ai/geminiClient.ts + prompt.ts 로직 이식.
# 매 요청마다 시작 키를 무작위로 골라 순차 폴백하고, 401/403/429일 때만 다음 키로 넘어간다.
async def translate_stream(
    api_keys: list[str],
    model: str,
    system_prompt: str,
    translation_note: str,
    original_text: str,
    thinking_budget: Optional[int],
) -> AsyncIterator[dict]:
    keys = [k.strip() for k in api_keys if k.strip() and ASCII_PRINTABLE_RE.match(k.strip())]
    if not keys:
        yield {"event": "error", "data": {"code": "INVALID_API_KEY", "message": "번역에 사용할 API 키가 없습니다."}}
        return

    resolved_prompt = resolve_system_prompt(system_prompt, translation_note or "")
    total_lines = max(1, len(original_text.split("\n")))
    yield {"event": "start", "data": {"totalLines": total_lines}}

    start_index = random.randrange(len(keys))
    last_error: Optional[dict] = None

    for attempt in range(len(keys)):
        key_index = (start_index + attempt) % len(keys)
        client = genai.Client(api_key=keys[key_index])

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

            yield {"event": "progress", "data": {"percent": 100}}
            yield {"event": "done", "data": {"translatedText": "\n".join(translated_lines)}}
            return

        except Exception as exc:
            last_error = _to_error_payload(exc)
            if _is_auth_or_quota_error(exc) and attempt < len(keys) - 1:
                continue
            yield {"event": "error", "data": last_error}
            return

    yield {"event": "error", "data": last_error or {"code": "UPSTREAM_ERROR", "message": "번역에 실패했습니다."}}
