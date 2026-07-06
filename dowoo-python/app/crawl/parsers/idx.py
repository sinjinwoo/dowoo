import re
from urllib.parse import urlparse

from bs4 import BeautifulSoup

from app.crawl.parsers.sanitize import strip_ad_lines

SOURCE_SITE = "ixdzs8.com"

# 책 소개 페이지로 돌아가는 링크(/read/{bookId}/, 회차 파일명 없음)의 텍스트가 곧 책 제목이다.
# 단, 같은 href를 쓰는 제네릭 버튼("书籍页" = "책 페이지")도 있어서 그건 걸러낸다.
BOOK_LINK_HREF_RE = re.compile(r"^/read/\d+/$")
GENERIC_BOOK_LINK_LABELS = {"书籍页", "書籍頁"}

# 첫 장/마지막 장에서는 이전/다음 링크가 실제 회차가 아니라 책 소개 페이지(/read/{bookId}/) 또는
# end 페이지(/read/{bookId}/end.html)를 가리킨다 - 이 경우 더 이상 이동할 챕터가 없는 것으로 처리한다.
NO_PREV_CHAPTER_RE = re.compile(r"/read/\d+/$")
NO_NEXT_CHAPTER_RE = re.compile(r"/read/\d+/end\.html$")

# 회차 URL 경로(/read/{bookId}/pNNN.html)에서 책 ID를 뽑는다 - 같은 책의 서로 다른 회차를
# 서재에서 하나로 묶어보기 위한 식별자라, HTML 내용이 아니라 URL 구조에서 뽑는 게 안정적이다.
BOOK_ID_RE = re.compile(r"^/read/(\d+)/")


def parse_idx(html: str, url: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")
    # 챕터 제목
    title = ""

    # 1순위: 진행바의 현재 챕터 제목
    title_el = soup.select_one(".read-prog-val h4")
    if title_el:
        title = title_el.get_text(strip=True)

    # 2순위: 기존 구조
    if not title:
        title_el = soup.select_one(".page-d-name")
        if title_el:
            title = title_el.get_text(strip=True)

    # 3순위: article 제목
    if not title:
        h3 = soup.select_one("article h3")
        if h3:
            title = h3.get_text(strip=True)

    # 책 제목
    book_title = None

    # 1순위: 읽기 페이지 breadcrumb
    book_link = soup.select_one("#read-more a[href^='/read/']")
    if book_link:
        book_title = book_link.get_text(strip=True)

    # 2순위: 기존 방식
    if not book_title:
        for link in soup.find_all("a", href=BOOK_LINK_HREF_RE):
            text = link.get_text(strip=True)
            if text and text not in GENERIC_BOOK_LINK_LABELS:
                book_title = text
                break

    paragraphs = soup.select("article section p")
    # 문단 <p> 내부에 실제 줄바꿈이 섞여 있으면 그 문단 하나가 여러 줄로 쪼개져 원문/번역
    # 줄 수가 어긋난다(다른 파서들과 같은 문제) - 문단 내부 공백은 전부 스페이스로 접어
    # "문단 하나 = 줄 하나"를 보장한다.
    content = "\n\n".join(" ".join(p.get_text(strip=True).split()) for p in paragraphs)
    content = strip_ad_lines(content)

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

    book_id_match = BOOK_ID_RE.match(urlparse(url).path)

    return {
        "title": title,
        "book_title": book_title or None,
        "content": content,
        "prev": prev_url,
        "next": next_url,
        "source_site": SOURCE_SITE,
        "source_book_id": book_id_match.group(1) if book_id_match else None,
    }
