import re

from bs4 import BeautifulSoup

from app.crawl.parsers.shuba_family import extract_bookinfo

BR_RE = re.compile(r"<br\s*/?>", re.IGNORECASE)
MULTI_NEWLINE_RE = re.compile(r"\n{3,}")
REMOVE_SELECTOR = "script, ins, .contentadv, .bottom-ad"


def parse_twkan(html: str) -> dict:
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

    return {
        "title": bookinfo["chaptername"],
        "content": text,
        "prev": bookinfo["previewPage"],
        "next": bookinfo["nextPage"],
    }
