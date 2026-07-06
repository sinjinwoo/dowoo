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
AD_PHRASE_TOKENS = ("검색하세요", "검색해보세요", "请搜索", "本站域名", "记住本站", "提供最新")
AD_LINE_MAX_CHARS = 60


def _is_combining_watermark(line: str) -> bool:
    marks = len(COMBINING_MARK_RE.findall(line))
    if marks < COMBINING_WATERMARK_MIN_MARKS:
        return False
    return marks / max(len(line), 1) >= COMBINING_WATERMARK_MIN_RATIO


def _is_known_ad_line(line: str) -> bool:
    if not line or len(line) > AD_LINE_MAX_CHARS:
        return False
    lowered = line.lower()
    has_site = any(token in lowered for token in AD_SITE_TOKENS)
    if not has_site:
        return False
    return any(token in line for token in AD_PHRASE_TOKENS) or "www." in lowered


def strip_ad_lines(text: str) -> str:
    """스크래핑한 본문에 섞여 들어온 사이트 광고/워터마크 줄을 제거한다.

    이런 줄이 청크 맨 앞에 오면 flash-lite 계열 모델이 구조적 이질성 때문에 그 청크
    전체를 번역하지 않고 원문 그대로 베끼는 결정론적 오류를 일으킨다
    (docs/troubleshooting/29-flash-lite-deterministic-passthrough-on-chapter-opening.md).
    """
    lines = text.splitlines()
    kept = [line for line in lines if not (_is_combining_watermark(line) or _is_known_ad_line(line))]
    return "\n".join(kept)
