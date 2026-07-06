import re
from urllib.parse import urlparse

from bs4 import BeautifulSoup

from app.crawl.parsers.sanitize import strip_ad_lines
from app.crawl.parsers.shuba_family import extract_bookinfo

SOURCE_SITE = "69shuba.com"

BR_RE = re.compile(r"<br\s*/?>", re.IGNORECASE)
MULTI_NEWLINE_RE = re.compile(r"\n{3,}")
REMOVE_SELECTOR = "h1, .txtinfo, #txtright, .contentadv, .bottom-ad, .page1, script, ins"

# 첫 장/마지막 장에서는 이전/다음 링크가 실제 회차가 아니라 목차 페이지(book/{bookId}.htm 또는 .html)를
# 가리킨다. 확장자가 .htm/.html 중 어느 쪽인지 매번 헷갈렸던 지점이라 둘 다 매치하도록 둔다.
NO_CHAPTER_RE = re.compile(r"/book/\d+\.html?$")

# 회차 URL 경로(/txt/{bookId}/{chapterId})에서 책 ID를 뽑는다.
BOOK_ID_RE = re.compile(r"^/txt/(\d+)/")


def parse_69shuba(html: str, url: str) -> dict:
    bookinfo = extract_bookinfo(html)
    soup = BeautifulSoup(html, "html.parser")

    title = bookinfo["chaptername"]
    if not title:
        h1 = soup.select_one("h1.hide720")
        title = h1.get_text(strip=True) if h1 else ""

    container = soup.select_one(".txtnav")
    content_html = ""
    if container is not None:
        for el in container.select(REMOVE_SELECTOR):
            el.decompose()
        content_html = container.decode_contents()

    content_html = BR_RE.sub("\n", content_html).replace("&nbsp;", " ")
    text = BeautifulSoup(content_html, "html.parser").get_text()
    # 원본 HTML에 섞인 \r\n/\r 및 들여쓰기 공백이 그대로 남으면 "\r"만 있는 유령 줄이 생겨
    # 프론트의 원문/번역 줄 단위 매칭이 밀린다 - 줄 경계를 통일하고 줄마다 공백을 제거한다.
    text = "\n".join(line.strip() for line in text.splitlines())
    text = strip_ad_lines(text)
    text = MULTI_NEWLINE_RE.sub("\n\n", text).strip()

    prev_url = bookinfo["previewPage"]
    next_url = bookinfo["nextPage"]
    if prev_url and NO_CHAPTER_RE.search(prev_url):
        prev_url = None
    if next_url and NO_CHAPTER_RE.search(next_url):
        next_url = None

    book_id_match = BOOK_ID_RE.match(urlparse(url).path)

    return {
        "title": title,
        "book_title": bookinfo["articlename"] or None,
        "content": text,
        "prev": prev_url,
        "next": next_url,
        "source_site": SOURCE_SITE,
        "source_book_id": book_id_match.group(1) if book_id_match else None,
    }
