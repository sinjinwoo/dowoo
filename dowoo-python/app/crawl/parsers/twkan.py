import re
from urllib.parse import urlparse

from bs4 import BeautifulSoup

from app.crawl.parsers.shuba_family import extract_bookinfo

SOURCE_SITE = "twkan.com"

BR_RE = re.compile(r"<br\s*/?>", re.IGNORECASE)
MULTI_NEWLINE_RE = re.compile(r"\n{3,}")
REMOVE_SELECTOR = "script, ins, .contentadv, .bottom-ad"

# 첫 장에서는 이전 링크가 작품 소개 페이지(book/{bookId}/index.html)를,
# 마지막 장에서는 다음 링크가 end 페이지(txt/{bookId}/end.html)를 가리킨다.
NO_PREV_CHAPTER_RE = re.compile(r"/book/\d+/index\.html$")
NO_NEXT_CHAPTER_RE = re.compile(r"/txt/\d+/end\.html$")

# 회차 URL 경로(/txt/{bookId}/{chapterId}.html)에서 책 ID를 뽑는다.
BOOK_ID_RE = re.compile(r"^/txt/(\d+)/")


def parse_twkan(html: str, url: str) -> dict:
    bookinfo = extract_bookinfo(html)
    soup = BeautifulSoup(html, "html.parser")

    container = soup.select_one("#txtcontent0") or soup.select_one("#txtcontent1")
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
    if prev_url and NO_PREV_CHAPTER_RE.search(prev_url):
        prev_url = None
    if next_url and NO_NEXT_CHAPTER_RE.search(next_url):
        next_url = None

    book_id_match = BOOK_ID_RE.match(urlparse(url).path)

    return {
        "title": bookinfo["chaptername"],
        "book_title": bookinfo["articlename"] or None,
        "content": text,
        "prev": prev_url,
        "next": next_url,
        "source_site": SOURCE_SITE,
        "source_book_id": book_id_match.group(1) if book_id_match else None,
    }
