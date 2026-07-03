from bs4 import BeautifulSoup


def parse_idx(html: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")

    title_el = soup.select_one(".page-d-name")
    title = title_el.get_text(strip=True) if title_el else ""
    if not title:
        h3 = soup.select_one("article h3")
        title = h3.get_text(strip=True) if h3 else ""

    paragraphs = soup.select("article section p")
    content = "\n\n".join(p.get_text(strip=True) for p in paragraphs)

    prev_el = soup.select_one(".chapter-pre")
    next_el = soup.select_one(".chapter-next")

    return {
        "title": title,
        "content": content,
        "prev": prev_el.get("href") if prev_el else None,
        "next": next_el.get("href") if next_el else None,
    }
