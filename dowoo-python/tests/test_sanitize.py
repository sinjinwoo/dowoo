"""
app.crawl.parsers.sanitize.strip_ad_lines가 크롤링한 본문에서 사이트 광고/워터마크 줄만
정확히 걸러내고, 소설 내 시스템 메시지 블록 같은 정상 콘텐츠는 건드리지 않는지 검증한다.
"""

from app.crawl.parsers.sanitize import strip_ad_lines


def _obfuscate(domain: str) -> str:
    """실제 사이트가 쓰는 방식대로 도메인 글자마다 결합 취소선 문자를 붙인다."""
    return "".join(ch + "̶" for ch in domain)


def test_strips_combining_mark_obfuscated_domain_watermark():
    ad_line = f"[{_obfuscate('twkan.com')} 을 검색하세요]"
    text = f"{ad_line}\n정상적인 소설 본문 첫 줄입니다."

    result = strip_ad_lines(text)

    assert ad_line not in result.splitlines()
    assert "정상적인 소설 본문 첫 줄입니다." in result.splitlines()


def test_strips_plain_text_ad_line_with_site_name_and_phrase():
    ad_line = "이 소설은 twkan에서 검색하세요"
    text = f"{ad_line}\n정상적인 소설 본문입니다."

    result = strip_ad_lines(text)

    assert ad_line not in result.splitlines()
    assert "정상적인 소설 본문입니다." in result.splitlines()


def test_does_not_strip_novel_system_message_bracket_block():
    # 사이트명이 없으면 아무리 브라켓으로 감싸져 있어도(litRPG 시스템 메시지 등) 광고로
    # 오판하면 안 된다.
    system_message = "【任务：영약을 손에 넣어라】"
    text = f"{system_message}\n다음 문장입니다."

    result = strip_ad_lines(text)

    assert system_message in result.splitlines()
    assert "다음 문장입니다." in result.splitlines()


def test_does_not_strip_line_with_site_name_but_no_ad_phrase():
    # 우연히 사이트명과 비슷한 단어가 본문에 있어도 광고성 문구가 함께 없으면 지우지 않는다.
    line = "twkan이라는 이름의 등장인물이 나타났다"
    text = f"{line}\n다음 문장입니다."

    result = strip_ad_lines(text)

    assert line in result.splitlines()


def test_preserves_blank_lines_and_line_order():
    text = "첫 줄\n\n둘째 줄"

    assert strip_ad_lines(text) == text


def test_strips_mathematical_italic_domain_watermark():
    # 𝑡𝑤𝑘𝑎𝑛.𝑐𝑜𝑚 - 유니코드 Mathematical Alphanumeric Symbols(이탤릭체)로 통째로 바꿔치기.
    ad_line = "【記住本站域名 台灣小說網解書荒，𝑡𝑤𝑘𝑎𝑛.𝑐𝑜𝑚超實用 】"
    text = f"{ad_line}\n정상적인 소설 본문 첫 줄입니다."

    result = strip_ad_lines(text)

    assert ad_line not in result.splitlines()
    assert "정상적인 소설 본문 첫 줄입니다." in result.splitlines()


def test_strips_mathematical_bold_domain_watermark():
    ad_line = "【寫到這裡我希望讀者記一下我們域名 追台灣小說認準台灣小說網，𝐭𝐰𝐤𝐚𝐧.𝐜𝐨𝐦超靠譜 】"
    text = f"{ad_line}\n다음 문장입니다."

    result = strip_ad_lines(text)

    assert ad_line not in result.splitlines()
    assert "다음 문장입니다." in result.splitlines()


def test_strips_negative_circled_domain_watermark():
    # 🅣🅦🅚🅐🅝.🅒🅞🅜 - NFKC 정규화로도 안 풀리는 이모지풍 동그라미 문자로 바꿔치기.
    ad_line = "【寫到這裡我希望讀者記一下我們域名 台灣小說網解悶好，🅣🅦🅚🅐🅝.🅒🅞🅜隨時看 】"
    text = f"{ad_line}\n다음 문장입니다."

    result = strip_ad_lines(text)

    assert ad_line not in result.splitlines()
    assert "다음 문장입니다." in result.splitlines()


def test_strips_domain_with_separator_characters_between_letters():
    # 결합 문자가 아니라 평범한 구분 문자(슬래시, 가운뎃점 등)로 글자를 벌려놓는 방식.
    for obfuscated in ("t/w/k/a/n.com", "t·w·k·a·n.com", "t-w-k-a-n.com"):
        ad_line = f"이 소설은 {obfuscated}에서 검색하세요"
        text = f"{ad_line}\n다음 문장입니다."

        result = strip_ad_lines(text)

        assert ad_line not in result.splitlines(), f"failed for {obfuscated!r}"
        assert "다음 문장입니다." in result.splitlines()


def test_does_not_fold_normal_cjk_and_hangul_text():
    # fold 로직이 한중일 문자에는 손대지 않아야 한다(글자 이름이 라틴 계열이 아니므로).
    text = "정상적인 소설 본문 첫 줄입니다. 記住本站域名 같은 한자도 안전한가?"

    assert strip_ad_lines(text) == text