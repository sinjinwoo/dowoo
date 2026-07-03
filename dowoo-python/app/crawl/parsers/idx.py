import re

from bs4 import BeautifulSoup

# 책 소개 페이지로 돌아가는 링크(/read/{bookId}/, 회차 파일명 없음)의 텍스트가 곧 책 제목이다.
# 단, 같은 href를 쓰는 제네릭 버튼("书籍页" = "책 페이지")도 있어서 그건 걸러낸다.
BOOK_LINK_HREF_RE = re.compile(r"^/read/\d+/$")
GENERIC_BOOK_LINK_LABELS = {"书籍页", "書籍頁"}


def parse_idx(html: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")

    title_el = soup.select_one(".page-d-name")
    title = title_el.get_text(strip=True) if title_el else ""
    if not title:
        h3 = soup.select_one("article h3")
        title = h3.get_text(strip=True) if h3 else ""

    book_title = None
    for link in soup.find_all("a", href=BOOK_LINK_HREF_RE):
        text = link.get_text(strip=True)
        if text and text not in GENERIC_BOOK_LINK_LABELS:
            book_title = text
            break

    paragraphs = soup.select("article section p")
    content = "\n\n".join(p.get_text(strip=True) for p in paragraphs)

    prev_el = soup.select_one(".chapter-pre")
    next_el = soup.select_one(".chapter-next")

    return {
        "title": title,
        "book_title": book_title or None,
        "content": content,
        "prev": prev_el.get("href") if prev_el else None,
        "next": next_el.get("href") if next_el else None,
    }
