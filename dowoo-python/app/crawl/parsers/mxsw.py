import re
from urllib.parse import urlparse

from bs4 import BeautifulSoup

SOURCE_SITE = "m.xsw.tw"

BR_RE = re.compile(r"<br\s*/?>", re.IGNORECASE)
MULTI_NEWLINE_RE = re.compile(r"\n{3,}")
# 책 소개 페이지로 돌아가는 링크(/{bookId}/, 회차 파일명 없음). 링크 텍스트에 "信息頁" 접미사가 붙어있다.
BOOK_LINK_HREF_RE = re.compile(r"^/\d+/$")
BOOK_TITLE_SUFFIX_RE = re.compile(r"信息頁$")

# 회차 URL 경로(/{bookId}/{chapterId}.html)에서 책 ID를 뽑는다.
BOOK_ID_RE = re.compile(r"^/(\d+)/")


def parse_mxsw(html: str, url: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")

    title_el = soup.select_one("#nr_title")
    title = title_el.get_text(strip=True) if title_el else ""
    if not title:
        title_tag = soup.select_one("title")
        title = title_tag.get_text(strip=True) if title_tag else ""

    book_link = soup.find("a", href=BOOK_LINK_HREF_RE)
    book_title = BOOK_TITLE_SUFFIX_RE.sub("", book_link.get_text(strip=True)) if book_link else None

    content_el = soup.select_one("#nr1")
    content_html = content_el.decode_contents() if content_el else ""
    content_html = BR_RE.sub("\n", content_html).replace("&nbsp;", " ")
    text = BeautifulSoup(content_html, "html.parser").get_text()
    # 원본 HTML에 섞인 \r\n/\r 및 들여쓰기 공백이 그대로 남으면 "\r"만 있는 유령 줄이 생겨
    # 프론트의 원문/번역 줄 단위 매칭이 밀린다 - 줄 경계를 통일하고 줄마다 공백을 제거한다.
    text = "\n".join(line.strip() for line in text.splitlines())
    text = MULTI_NEWLINE_RE.sub("\n\n", text).strip()

    prev_el = soup.select_one("#pb_prev") or soup.select_one("#pb_prev1")
    next_el = soup.select_one("#pb_next") or soup.select_one("#pb_next1")

    prev_url = prev_el.get("href") if prev_el else None
    next_url = next_el.get("href") if next_el else None

    # 첫 장/마지막 장에서는 이전/다음 링크가 회차가 아니라 작품 메인 페이지(/{bookId}/)를 가리킨다.
    if prev_url and BOOK_LINK_HREF_RE.search(prev_url):
        prev_url = None
    if next_url and BOOK_LINK_HREF_RE.search(next_url):
        next_url = None

    book_id_match = BOOK_ID_RE.match(urlparse(url).path)

    return {
        "title": title,
        "book_title": book_title or None,
        "content": text,
        "prev": prev_url,
        "next": next_url,
        "source_site": SOURCE_SITE,
        "source_book_id": book_id_match.group(1) if book_id_match else None,
    }
