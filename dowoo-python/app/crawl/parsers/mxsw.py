import re

from bs4 import BeautifulSoup

BR_RE = re.compile(r"<br\s*/?>", re.IGNORECASE)
MULTI_NEWLINE_RE = re.compile(r"\n{3,}")


def parse_mxsw(html: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")

    title_el = soup.select_one("#nr_title")
    title = title_el.get_text(strip=True) if title_el else ""
    if not title:
        title_tag = soup.select_one("title")
        title = title_tag.get_text(strip=True) if title_tag else ""

    content_el = soup.select_one("#nr1")
    content_html = content_el.decode_contents() if content_el else ""
    content_html = BR_RE.sub("\n", content_html).replace("&nbsp;", " ")
    text = BeautifulSoup(content_html, "html.parser").get_text()
    text = MULTI_NEWLINE_RE.sub("\n\n", text).strip()

    prev_el = soup.select_one("#pb_prev") or soup.select_one("#pb_prev1")
    next_el = soup.select_one("#pb_next") or soup.select_one("#pb_next1")

    return {
        "title": title,
        "content": text,
        "prev": prev_el.get("href") if prev_el else None,
        "next": next_el.get("href") if next_el else None,
    }
