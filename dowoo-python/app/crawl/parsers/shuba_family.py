import re
from typing import Optional


def _extract_field(html: str, key: str) -> Optional[str]:
    pattern = re.compile(key + r"\s*:\s*(?:'([^']*)'|\"([^\"]*)\")")
    match = pattern.search(html)
    if not match:
        return None
    return match.group(1) if match.group(1) is not None else match.group(2)


def extract_bookinfo(html: str) -> dict:
    return {
        "articlename": _extract_field(html, "articlename") or "",
        "chaptername": _extract_field(html, "chaptername") or "",
        "previewPage": _extract_field(html, "preview_page"),
        "nextPage": _extract_field(html, "next_page"),
    }
