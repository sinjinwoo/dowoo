import re

from bs4 import BeautifulSoup

from app.crawl.parsers.shuba_family import extract_bookinfo

BR_RE = re.compile(r"<br\s*/?>", re.IGNORECASE)
MULTI_NEWLINE_RE = re.compile(r"\n{3,}")
REMOVE_SELECTOR = "h1, .txtinfo, #txtright, .contentadv, .bottom-ad, .page1, script, ins"

# 첫 장/마지막 장에서는 이전/다음 링크가 실제 회차가 아니라 목차 페이지(book/{bookId}.html)를 가리킨다.
NO_CHAPTER_RE = re.compile(r"/book/\d+\.html$")


def parse_69shuba(html: str) -> dict:
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
    text = MULTI_NEWLINE_RE.sub("\n\n", text).strip()

    prev_url = bookinfo["previewPage"]
    next_url = bookinfo["nextPage"]
    if prev_url and NO_CHAPTER_RE.search(prev_url):
        prev_url = None
    if next_url and NO_CHAPTER_RE.search(next_url):
        next_url = None

    return {
        "title": title,
        "book_title": bookinfo["articlename"] or None,
        "content": text,
        "prev": prev_url,
        "next": next_url,
    }
