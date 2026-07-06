"""
gemini_client.translate_stream의 키 로테이션/에러 분류 로직을 검증한다.

실제 Gemini API는 절대 호출하지 않는다 - genai.Client를 fake로 갈아끼워서 네트워크
없이 "여러 키를 순환하는지", "재시도 가능/불가능 에러를 올바르게 구분하는지",
"빈 응답도 실패로 취급하는지" 같은 로직만 검증한다. 실제 키로 Gemini 연동 자체가
되는지는 별도의 수동 스모크 테스트로 확인할 것 (이 테스트 스위트의 대상이 아님).
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.translate.gemini_client import (
    UNTRANSLATED_MAX_ATTEMPTS,
    _get_status,
    _is_auth_or_quota_error,
    _looks_untranslated,
    _split_into_chunks,
    _to_error_payload,
    resolve_system_prompt,
    translate_stream,
)


@pytest.fixture(autouse=True)
def _no_real_sleep():
    """미번역 재시도 백오프(5초/10초)가 테스트를 실제로 지연시키지 않도록 asyncio.sleep을 무력화한다."""
    with patch("app.translate.gemini_client.asyncio.sleep", new=AsyncMock()) as mock_sleep:
        yield mock_sleep


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
            original_text="원문 첫 줄\n원문 둘째 줄", thinking_budget=None,
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


# ---- 미번역(원문 그대로 베끼기) 감지 ----


def test_looks_untranslated_true_for_chinese_passthrough():
    # 원문 중국어를 그대로 베낀 응답 - 한글이 거의 없다.
    assert _looks_untranslated("这是没有被翻译的原文内容测试文本用于检测汉字比例是否过低") is True


def test_looks_untranslated_false_for_normal_korean_translation():
    assert _looks_untranslated("이것은 정상적으로 번역된 한국어 문장입니다 아주 자연스럽게 번역되었습니다") is False


def test_looks_untranslated_false_when_too_short_to_judge():
    # 판단 기준(20자) 미만이면 오탐 방지를 위해 판단을 보류(정상으로 취급)한다.
    assert _looks_untranslated("张三") is False


async def test_untranslated_response_falls_back_to_next_key_without_leaking_lines():
    chinese_passthrough = "这是没有被翻译的原文内容测试文本用于检测汉字比例是否过低"
    bad_client = _client_returning([chinese_passthrough])
    good_client = _client_returning(["정상적으로 번역된 한국어 문장입니다 아주 자연스럽게 번역되었습니다"])

    with _patch_genai_client({"bad-key": bad_client, "good-key": good_client}), \
            patch("app.translate.gemini_client.random.randrange", return_value=0):
        events = await _collect(translate_stream(
            api_keys=["bad-key", "good-key"], models=["gemini-2.5-flash"], system_prompt="s",
            translation_note="", original_text="원문", thinking_budget=None,
        ))

    line_events = [e for e in events if e["event"] == "line"]
    assert chinese_passthrough not in [e["data"]["text"] for e in line_events]
    assert events[-1]["event"] == "done"
    assert "정상적으로 번역된" in events[-1]["data"]["translatedText"]


async def test_untranslated_response_retries_same_key_before_rotating():
    # 같은 키가 처음 두 번은 원문을 그대로 베끼다가 세 번째 시도에서 정상 번역을 내놓는
    # 상황을 재현한다 - 키를 하나만 주고도 성공해야 하며(=같은 키로 재시도했다는 뜻),
    # genai.Client 생성 자체는 한 번만 일어나야 한다(재시도가 새 클라이언트를 안 만듦).
    chinese_passthrough = "这是没有被翻译的原文内容测试文本用于检测汉字比例是否过低"
    call_count = {"n": 0}
    client = MagicMock()

    async def generate_content_stream(**kwargs):
        call_count["n"] += 1
        if call_count["n"] < 3:
            return _fake_stream([chinese_passthrough])
        return _fake_stream(["정상적으로 번역된 한국어 문장입니다 아주 자연스럽게 번역되었습니다"])

    client.aio.models.generate_content_stream = generate_content_stream

    with patch("app.translate.gemini_client.genai.Client", return_value=client) as client_ctor, \
            patch("app.translate.gemini_client.random.randrange", return_value=0):
        events = await _collect(translate_stream(
            api_keys=["only-key"], models=["gemini-2.5-flash"], system_prompt="s",
            translation_note="", original_text="원문", thinking_budget=None,
        ))

    assert call_count["n"] == 3
    assert client_ctor.call_count == 1
    assert events[-1]["event"] == "done"
    assert "정상적으로 번역된" in events[-1]["data"]["translatedText"]


async def test_untranslated_retry_backs_off_between_same_key_attempts(_no_real_sleep):
    # 같은 (모델, 키)로 재시도할 때는 딜레이 없이 연달아 쏘면 RPM 한도에 걸릴 수 있어 재시도
    # 사이에 대기해야 한다 - 실패 2번(재시도 유발) 후 성공하는 상황에서 대기 시간이 재시도
    # 횟수에 따라 늘어나는지(5초 -> 10초) 검증한다.
    chinese_passthrough = "这是没有被翻译的原文内容测试文本用于检测汉字比例是否过低"
    call_count = {"n": 0}
    client = MagicMock()

    async def generate_content_stream(**kwargs):
        call_count["n"] += 1
        if call_count["n"] < 3:
            return _fake_stream([chinese_passthrough])
        return _fake_stream(["정상적으로 번역된 한국어 문장입니다 아주 자연스럽게 번역되었습니다"])

    client.aio.models.generate_content_stream = generate_content_stream

    with patch("app.translate.gemini_client.genai.Client", return_value=client), \
            patch("app.translate.gemini_client.random.randrange", return_value=0):
        events = await _collect(translate_stream(
            api_keys=["only-key"], models=["gemini-2.5-flash"], system_prompt="s",
            translation_note="", original_text="원문", thinking_budget=None,
        ))

    assert events[-1]["event"] == "done"
    assert _no_real_sleep.await_args_list == [((5,),), ((10,),)]


# ---- 청크 분할 ----


def test_split_into_chunks_keeps_short_text_as_single_chunk():
    assert _split_into_chunks("한 줄\n두 줄", chunk_size=10000) == ["한 줄\n두 줄"]


def test_split_into_chunks_splits_on_line_boundaries_only():
    line1 = "a" * 4000
    line2 = "b" * 4000
    line3 = "c" * 4000
    text = "\n".join([line1, line2, line3])

    chunks = _split_into_chunks(text, chunk_size=10000)

    # line1+line2는 8001자로 1만자 이하라 한 청크에 들어가지만, 거기에 line3까지 더하면
    # 1만자를 넘으므로 line3는 다음 청크로 분리돼야 한다 - 어떤 청크도 줄 중간에서 잘리면 안 된다.
    assert chunks == [f"{line1}\n{line2}", line3]
    assert "\n".join(chunks[0].split("\n") + chunks[1].split("\n")) == text


async def test_multi_chunk_translation_calls_gemini_once_per_chunk_and_concatenates_in_order():
    line1 = "a" * 4000
    line2 = "b" * 4000
    line3 = "c" * 4000
    original_text = "\n".join([line1, line2, line3])

    call_outputs = [["translated-chunk-1"], ["translated-chunk-2"]]
    call_count = {"n": 0}
    client = MagicMock()

    async def generate_content_stream(**kwargs):
        outputs = call_outputs[call_count["n"]]
        call_count["n"] += 1
        return _fake_stream(outputs)

    client.aio.models.generate_content_stream = generate_content_stream

    with _patch_genai_client({"key-a": client}):
        events = await _collect(translate_stream(
            api_keys=["key-a"], models=["gemini-2.5-flash"], system_prompt="s",
            translation_note="", original_text=original_text, thinking_budget=None,
        ))

    assert call_count["n"] == 2  # 청크 2개 -> Gemini 호출도 2번, 챕터 전체를 한 번에 보내지 않음
    line_events = [e for e in events if e["event"] == "line"]
    assert [e["data"]["index"] for e in line_events] == [0, 1]
    assert [e["data"]["text"] for e in line_events] == ["translated-chunk-1", "translated-chunk-2"]
    assert events[-1] == {"event": "done", "data": {"translatedText": "translated-chunk-1\ntranslated-chunk-2"}}


async def test_untranslated_failure_on_two_keys_escalates_to_next_model_without_trying_remaining_keys():
    # model-a는 (가정상) 이 청크를 결정론적으로 원문 그대로 베끼는 가벼운 모델이라고 하자 -
    # 어떤 키로 시도해도 항상 실패한다. 서로 다른 키 2개가 똑같이 실패하면 키 문제가
    # 아니라 모델/콘텐츠 문제로 보고, 3번째 키는 아예 시도하지 않고 바로 model-b로
    # 넘어가야 한다(안 그러면 결정론적 실패인데도 남은 키를 전부 태우며 시간을 낭비함).
    chinese_passthrough = "这是没有被翻译的原文内容测试文本用于检测汉字比例是否过低"
    call_log: list[tuple[str, str]] = []

    def make_client(api_key):
        client = MagicMock()

        async def generate_content_stream(model, **kwargs):
            call_log.append((api_key, model))
            if model == "model-a":
                return _fake_stream([chinese_passthrough])
            return _fake_stream(["정상적으로 번역된 한국어 문장입니다 아주 자연스럽게 번역되었습니다"])

        client.aio.models.generate_content_stream = generate_content_stream
        return client

    with patch("app.translate.gemini_client.genai.Client", side_effect=make_client), \
            patch("app.translate.gemini_client.random.randrange", return_value=0):
        events = await _collect(translate_stream(
            api_keys=["key-1", "key-2", "key-3"], models=["model-a", "model-b"], system_prompt="s",
            translation_note="", original_text="원문", thinking_budget=None,
        ))

    model_a_calls = [c for c in call_log if c[1] == "model-a"]
    assert len(model_a_calls) == 2 * UNTRANSLATED_MAX_ATTEMPTS  # key-1, key-2 각각 최대 재시도까지만
    assert ("key-3", "model-a") not in call_log
    assert events[-1]["event"] == "done"
    assert "정상적으로 번역된" in events[-1]["data"]["translatedText"]


async def test_multi_chunk_translation_round_robins_key_after_each_success_to_spread_rpm_load():
    # 청크가 여러 개인 긴 챕터에서 매번 같은 키만 쓰면 그 키에만 요청이 몰려 RPM 한도에
    # 걸리기 쉽다 - 청크가 성공할 때마다 다음 키로 넘어가서 부하가 분산되는지 검증한다.
    line1 = "a" * 4000
    line2 = "b" * 4000
    line3 = "c" * 4000
    original_text = "\n".join([line1, line2, line3])

    call_log: list[str] = []

    def make_client(api_key):
        client = MagicMock()

        async def generate_content_stream(**kwargs):
            call_log.append(api_key)
            return _fake_stream([f"translated-by-{api_key}"])

        client.aio.models.generate_content_stream = generate_content_stream
        return client

    with patch("app.translate.gemini_client.genai.Client", side_effect=make_client), \
            patch("app.translate.gemini_client.random.randrange", return_value=0):
        events = await _collect(translate_stream(
            api_keys=["key-a", "key-b"], models=["gemini-2.5-flash"], system_prompt="s",
            translation_note="", original_text=original_text, thinking_budget=None,
        ))

    assert call_log == ["key-a", "key-b"]  # 청크1은 key-a, 청크2는 key-a를 또 쓰지 않고 key-b로
    assert events[-1] == {
        "event": "done",
        "data": {"translatedText": "translated-by-key-a\ntranslated-by-key-b"},
    }
