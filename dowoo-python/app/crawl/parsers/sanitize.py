import re

# 옵스큐어 유니코드 결합 문자(악센트)를 글자마다 끼워 넣어 도메인을 가린 광고 워터마크 탐지용.
# 예: "t + COMBINING LONG STROKE OVERLAY, w + COMBINING LONG STROKE OVERLAY, ..." 식으로
# domain의 각 글자 뒤에 결합 문자를 붙여 렌더링하면 취소선이 그어진 것처럼 보인다.
# 실제 소설 본문에는 이 정도 밀도로 결합 문자가 나타나지 않는다. 사이트명 자체가 무엇이든
# (69shuba/twkan 외에 새 사이트가 같은 수법을 써도) 문자열 매칭 없이 밀도만으로 잡아낸다.
COMBINING_MARK_RE = re.compile(
    "["
    r"̀-ͯ"  # Combining Diacritical Marks
    r"᪰-᫿"  # Combining Diacritical Marks Extended
    r"᷀-᷿"  # Combining Diacritical Marks Supplement
    r"⃐-⃿"  # Combining Diacritical Marks for Symbols
    r"︠-︯"  # Combining Half Marks
    "]"
)
COMBINING_WATERMARK_MIN_MARKS = 4
COMBINING_WATERMARK_MIN_RATIO = 0.15

# 크롤링 대상 사이트가 본문에 평문으로 섞어 넣는 광고 줄에는 항상 자기 사이트 도메인/이름이
# 들어간다는 공통점이 있다 - "사이트명 키워드 + 광고성 문구"가 함께 있을 때만 걸러내므로,
# 소설 내 시스템 메시지 블록(【任务...】 등)처럼 사이트명이 없는 브라켓 문단은 걸리지 않는다.
# (결합 문자로 도메인을 가린 난독화 버전은 이 목록과 무관하게 COMBINING_MARK_RE가 잡는다.)
AD_SITE_TOKENS = ("twkan", "69shuba", "ixdzs", "xsw.tw", "m.xsw")
AD_PHRASE_TOKENS = (
    "请搜索",
    "本站域名",
    "记住本站",
    "提供最新",
    "我们域名",
    "我們域名",
)
AD_LINE_MAX_CHARS = 60

# 도메인을 결합 문자가 아니라 "다른 글꼴처럼 보이는 별개 코드포인트"로 통째로 바꿔치기하는 수법
# (수학 볼드/이탤릭 𝑡𝑤𝑘𝑎𝑛, 동그라미/네모 🅣🅦🅚🅐🅝, 전각 ｔｗｋａｎ 등) 대응.
# 이런 문자들은 유니코드 이름에 항상 원래 라틴 알파벳이 그대로 들어있어
# ("MATHEMATICAL ITALIC SMALL T", "NEGATIVE CIRCLED LATIN CAPITAL LETTER T") 이름에서 마지막
# 한 글자만 뽑아내면 블록이 무엇이든(NFKC 호환 분해 매핑이 없는 이모지풍 문자 포함) 되돌릴 수 있다.
_FANCY_LATIN_LETTER_RE = re.compile(r"(?:LATIN |MATHEMATICAL \w+ )(SMALL|CAPITAL)(?: LETTER)? ([A-Z])\b")


def _fold_fancy_letter(ch: str) -> str:
    if ch.isascii():
        return ch
    match = _FANCY_LATIN_LETTER_RE.search(unicodedata.name(ch, ""))
    if not match:
        return ch
    letter = match.group(2)
    return letter.lower() if match.group(1) == "SMALL" else letter


def _fold_fancy_letters(line: str) -> str:
    return "".join(_fold_fancy_letter(ch) for ch in line)


# 도메인 글자 사이사이에 (결합 문자가 아니라) 슬래시/가운뎃점/공백류 같은 평범한 구분 문자를
# 끼워 넣어 "twkan" 문자열 자체를 끊어버리는 수법 대응 - 사이트 토큰 글자마다 그 사이에 문자가
# 아닌 것(공백/기호/제로폭 문자 등)이 최대 2개까지 끼어 있어도 매칭되게 한다.
_FUZZY_FILLER = r"[\W_]{0,2}"


def _build_fuzzy_token_re(token: str) -> re.Pattern:
    return re.compile(_FUZZY_FILLER.join(re.escape(ch) for ch in token))


_AD_SITE_TOKEN_PATTERNS = tuple(_build_fuzzy_token_re(token) for token in AD_SITE_TOKENS)


def _is_combining_watermark(line: str) -> bool:
    marks = len(COMBINING_MARK_RE.findall(line))
    if marks < COMBINING_WATERMARK_MIN_MARKS:
        return False
    return marks / max(len(line), 1) >= COMBINING_WATERMARK_MIN_RATIO


def _is_known_ad_line(line: str) -> bool:
    if not line or len(line) > AD_LINE_MAX_CHARS:
        return False
    folded = _fold_fancy_letters(line).lower()
    has_site = any(pattern.search(folded) for pattern in _AD_SITE_TOKEN_PATTERNS)
    if not has_site:
        return False
    return any(token in line for token in AD_PHRASE_TOKENS) or "www." in folded

def strip_ad_lines(text: str) -> str:
    """스크래핑한 본문에 섞여 들어온 사이트 광고/워터마크 줄을 제거한다.

    이런 줄이 청크 맨 앞에 오면 flash-lite 계열 모델이 구조적 이질성 때문에 그 청크
    전체를 번역하지 않고 원문 그대로 베끼는 결정론적 오류를 일으킨다
    (docs/troubleshooting/29-flash-lite-deterministic-passthrough-on-chapter-opening.md).
    """
    lines = text.splitlines()
    kept = [line for line in lines if not (_is_combining_watermark(line) or _is_known_ad_line(line))]
    return "\n".join(kept)
