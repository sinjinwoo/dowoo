import re

from bs4 import BeautifulSoup

# 책 소개 페이지로 돌아가는 링크(/read/{bookId}/, 회차 파일명 없음)의 텍스트가 곧 책 제목이다.
# 단, 같은 href를 쓰는 제네릭 버튼("书籍页" = "책 페이지")도 있어서 그건 걸러낸다.
BOOK_LINK_HREF_RE = re.compile(r"^/read/\d+/$")
GENERIC_BOOK_LINK_LABELS = {"书籍页", "書籍頁"}

# 첫 장/마지막 장에서는 이전/다음 링크가 실제 회차가 아니라 책 소개 페이지(/read/{bookId}/) 또는
# end 페이지(/read/{bookId}/end.html)를 가리킨다 - 이 경우 더 이상 이동할 챕터가 없는 것으로 처리한다.
NO_PREV_CHAPTER_RE = re.compile(r"/read/\d+/$")
NO_NEXT_CHAPTER_RE = re.compile(r"/read/\d+/end\.html$")


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

    # 정상 회차에서는 <a class="chapter-pre/chapter-next" href="...">가, 첫/마지막 장에서는 클릭 불가능한
    # <div class="read-pre/read-next" data-url="...">로 대체된다 - 두 형태 모두 시도해서 값을 얻는다.
    prev_el = soup.select_one(".chapter-pre") or soup.select_one(".read-pre")
    next_el = soup.select_one(".chapter-next") or soup.select_one(".read-next")

    prev_url = (prev_el.get("href") or prev_el.get("data-url")) if prev_el else None
    next_url = (next_el.get("href") or next_el.get("data-url")) if next_el else None

    if prev_url and NO_PREV_CHAPTER_RE.search(prev_url):
        prev_url = None
    if next_url and NO_NEXT_CHAPTER_RE.search(next_url):
        next_url = None

    return {
        "title": title,
        "book_title": book_title or None,
        "content": content,
        "prev": prev_url,
        "next": next_url,
    }
