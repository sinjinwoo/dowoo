from urllib.parse import urljoin, urlparse

from app.crawl.fetcher import fetch_impersonated
from app.crawl.parsers.idx import parse_idx
from app.crawl.parsers.mxsw import parse_mxsw
from app.crawl.parsers.shuba69 import parse_69shuba
from app.crawl.parsers.twkan import parse_twkan
from app.exceptions import CrawlError

# 4개 사이트 모두 curl_cffi(impersonate="chrome")로 통일해서 가져온다.
# 헤드리스 브라우저(Playwright)는 69shuba.com의 Cloudflare가 자동화 브라우저로 감지해
# 챌린지를 계속 막아서 포기 - TLS 지문까지 흉내내는 curl_cffi가 훨씬 안정적으로 뚫린다.
SITE_REGISTRY = {
    "ixdzs8.com": {"parser": parse_idx, "fetch": fetch_impersonated},
    "m.xsw.tw": {"parser": parse_mxsw, "fetch": fetch_impersonated},
    "69shuba.com": {"parser": parse_69shuba, "fetch": fetch_impersonated},
    "twkan.com": {"parser": parse_twkan, "fetch": fetch_impersonated},
}


def _resolve_site(hostname: str):
    for suffix, entry in SITE_REGISTRY.items():
        if hostname == suffix or hostname.endswith("." + suffix):
            return suffix, entry
    return None, None


def _resolve_relative(maybe_relative, base):
    if not maybe_relative:
        return None
    return urljoin(base, maybe_relative)


async def crawl_chapter(url: str) -> dict:
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        raise CrawlError("INVALID_URL", 400, "http/https 주소만 지원합니다.")

    site_key, entry = _resolve_site(parsed.hostname)
    if entry is None:
        raise CrawlError("UNSUPPORTED_SITE", 400, f"지원하지 않는 사이트입니다: {parsed.hostname}")

    html = await entry["fetch"](url)
    raw = entry["parser"](html)

    if not raw.get("title") and not raw.get("content"):
        raise CrawlError("PARSE_FAILED", 502, "본문/제목을 추출하지 못했습니다(사이트 마크업 변경 추정).")

    return {
        "title": raw["title"],
        "bookTitle": raw.get("book_title"),
        "content": raw["content"],
        "prevUrl": _resolve_relative(raw.get("prev"), url),
        "nextUrl": _resolve_relative(raw.get("next"), url),
        "siteName": site_key,
    }
