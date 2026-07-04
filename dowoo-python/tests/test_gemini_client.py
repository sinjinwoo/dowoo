"""
gemini_client.translate_stream의 키 로테이션/에러 분류 로직을 검증한다.

실제 Gemini API는 절대 호출하지 않는다 - genai.Client를 fake로 갈아끼워서 네트워크
없이 "여러 키를 순환하는지", "재시도 가능/불가능 에러를 올바르게 구분하는지",
"빈 응답도 실패로 취급하는지" 같은 로직만 검증한다. 실제 키로 Gemini 연동 자체가
되는지는 별도의 수동 스모크 테스트로 확인할 것 (이 테스트 스위트의 대상이 아님).
"""

from unittest.mock import MagicMock, patch

from app.translate.gemini_client import (
    _get_status,
    _is_auth_or_quota_error,
    _to_error_payload,
    resolve_system_prompt,
    translate_stream,
)


class _FakeChunk:
    def __init__(self, text):
        self.text = text


async def _fake_stream(texts):
    for text in texts:
        yield _FakeChunk(text)


class _FakeStatusError(Exception):
    def __init__(self, status_code, message="error"):
        super().__init__(message)
        self.status_code = status_code


def _client_returning(texts):
    """항상 주어진 텍스트 조각들을 스트리밍하는 fake genai.Client 인스턴스."""
    client = MagicMock()

    async def generate_content_stream(**kwargs):
        return _fake_stream(texts)

    client.aio.models.generate_content_stream = generate_content_stream
    return client


def _client_raising(exc):
    """generate_content_stream 호출 시 항상 예외를 던지는 fake genai.Client 인스턴스."""
    client = MagicMock()

    async def generate_content_stream(**kwargs):
        raise exc

    client.aio.models.generate_content_stream = generate_content_stream
    return client


def _patch_genai_client(clients_by_key):
    """api_key별로 다른 fake client를 반환하도록 genai.Client 생성을 패치한다."""
    return patch(
        "app.translate.gemini_client.genai.Client",
        side_effect=lambda api_key: clients_by_key[api_key],
    )


async def _collect(agen):
    return [item async for item in agen]


# ---- 순수 함수 ----


def test_resolve_system_prompt_substitutes_memo():
    assert resolve_system_prompt("prefix {{memo}} suffix", "GLOSSARY") == "prefix GLOSSARY suffix"


def test_resolve_system_prompt_empty_memo():
    assert resolve_system_prompt("prefix {{memo}} suffix", "") == "prefix  suffix"


def test_get_status_from_status_code_attribute():
    assert _get_status(_FakeStatusError(429)) == 429


def test_get_status_from_message_fallback():
    assert _get_status(Exception("received 503 from upstream")) == 503


def test_is_auth_or_quota_error_by_status():
    assert _is_auth_or_quota_error(_FakeStatusError(401)) is True
    assert _is_auth_or_quota_error(_FakeStatusError(429)) is True
    assert _is_auth_or_quota_error(_FakeStatusError(500)) is False


def test_to_error_payload_maps_status_codes():
    assert _to_error_payload(_FakeStatusError(401))["code"] == "INVALID_API_KEY"
    assert _to_error_payload(_FakeStatusError(429))["code"] == "QUOTA_EXCEEDED"
    assert _to_error_payload(_FakeStatusError(500))["code"] == "UPSTREAM_ERROR"


# ---- translate_stream ----


async def test_no_api_keys_yields_error_without_calling_gemini():
    events = await _collect(translate_stream(
        api_keys=[], models=["gemini-2.5-flash"], system_prompt="s", translation_note="",
        original_text="hello", thinking_budget=None,
    ))

    assert events == [{"event": "error", "data": {"code": "INVALID_API_KEY", "message": "번역에 사용할 API 키가 없습니다."}}]


async def test_no_models_yields_error():
    events = await _collect(translate_stream(
        api_keys=["key-a"], models=[], system_prompt="s", translation_note="",
        original_text="hello", thinking_budget=None,
    ))

    assert events[-1]["event"] == "error"
    assert events[-1]["data"]["code"] == "UPSTREAM_ERROR"


async def test_successful_translation_streams_lines_and_done():
    client = _client_returning(["line one\n", "line two"])

    with _patch_genai_client({"key-a": client}):
        events = await _collect(translate_stream(
            api_keys=["key-a"], models=["gemini-2.5-flash"], system_prompt="s", translation_note="",
            original_text="원문 두 줄", thinking_budget=None,
        ))

    assert events[0] == {"event": "start", "data": {"totalLines": 2}}
    line_events = [e for e in events if e["event"] == "line"]
    assert [e["data"]["text"] for e in line_events] == ["line one", "line two"]
    assert events[-1] == {"event": "done", "data": {"translatedText": "line one\nline two"}}


async def test_key_rotation_falls_back_to_next_key_on_auth_error():
    failing_client = _client_raising(_FakeStatusError(401, "invalid key"))
    working_client = _client_returning(["translated"])

    with _patch_genai_client({"bad-key": failing_client, "good-key": working_client}), \
            patch("app.translate.gemini_client.random.randrange", return_value=0):
        events = await _collect(translate_stream(
            api_keys=["bad-key", "good-key"], models=["gemini-2.5-flash"], system_prompt="s",
            translation_note="", original_text="원문", thinking_budget=None,
        ))

    assert events[-1] == {"event": "done", "data": {"translatedText": "translated"}}


async def test_all_keys_exhausted_yields_last_error():
    client_a = _client_raising(_FakeStatusError(429, "quota"))
    client_b = _client_raising(_FakeStatusError(429, "quota"))

    with _patch_genai_client({"key-a": client_a, "key-b": client_b}), \
            patch("app.translate.gemini_client.random.randrange", return_value=0):
        events = await _collect(translate_stream(
            api_keys=["key-a", "key-b"], models=["gemini-2.5-flash"], system_prompt="s",
            translation_note="", original_text="원문", thinking_budget=None,
        ))

    assert events[-1] == {"event": "error", "data": {"code": "QUOTA_EXCEEDED", "message": "구글 서버 사용량 한도를 초과했습니다."}}


async def test_non_retryable_error_skips_remaining_keys_for_that_model():
    # model-a는 (가정상) 잘못된 모델명이라 500을 던진다고 하자 - 키 문제가 아니므로
    # 재시도 없이(retryable=False) 바로 다음 모델로 넘어가야 하고, key-2는 아예 호출되면 안 된다.
    call_log = []

    def make_client(api_key):
        client = MagicMock()

        async def generate_content_stream(model, **kwargs):
            call_log.append((api_key, model))
            if model == "model-a":
                raise _FakeStatusError(500, "bad model name")
            return _fake_stream(["ok"])

        client.aio.models.generate_content_stream = generate_content_stream
        return client

    with patch("app.translate.gemini_client.genai.Client", side_effect=make_client), \
            patch("app.translate.gemini_client.random.randrange", return_value=0):
        events = await _collect(translate_stream(
            api_keys=["key-1", "key-2"], models=["model-a", "model-b"], system_prompt="s",
            translation_note="", original_text="원문", thinking_budget=None,
        ))

    assert events[-1] == {"event": "done", "data": {"translatedText": "ok"}}
    assert call_log == [("key-1", "model-a"), ("key-1", "model-b")]


async def test_empty_response_without_exception_is_treated_as_retryable_failure():
    empty_client = _client_returning([])  # 예외 없이 청크를 하나도 안 보내는 빈 스트림
    working_client = _client_returning(["ok"])

    with _patch_genai_client({"bad-key": empty_client, "good-key": working_client}), \
            patch("app.translate.gemini_client.random.randrange", return_value=0):
        events = await _collect(translate_stream(
            api_keys=["bad-key", "good-key"], models=["gemini-2.5-flash"], system_prompt="s",
            translation_note="", original_text="원문", thinking_budget=None,
        ))

    assert events[-1] == {"event": "done", "data": {"translatedText": "ok"}}
