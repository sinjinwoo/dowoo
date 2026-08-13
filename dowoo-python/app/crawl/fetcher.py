import asyncio
import random
import re
from typing import Optional
from urllib.parse import quote, urlsplit, urlunsplit

from curl_cffi import requests as curl_requests

from app.exceptions import CrawlError

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
)

# utf-8을 엄격 모드로 먼저 시도하고, 실패할 때만(=진짜 GBK 계열 사이트일 때만) gb18030/gbk로
# 폴백한다. gb18030은 거의 모든 바이트를 에러 없이 억지로 디코딩해버려서 strict가 아니면
# UTF-8 사이트까지 깨진 텍스트로 잘못 디코딩하게 된다.
ENCODING_CANDIDATES = ("utf-8", "gb18030", "gbk")

# 일부 사이트(ixdzs8.com)는 실제 콘텐츠 대신 `token`이 박힌 JS 리다이렉트 챌린지 페이지를
# 한 번 거치게 한다. 세션(쿠키 유지) 상태로 그 리다이렉트를 그대로 한 번 더 따라가면 통과된다.
CHALLENGE_TOKEN_RE = re.compile(r'token\s*=\s*"([^"]+)"')


def _decode_html(response) -> str:
    for encoding in ENCODING_CANDIDATES:
        try:
            return response.content.decode(encoding, errors="strict")
        except (UnicodeDecodeError, LookupError):
            continue
    return response.content.decode("utf-8", errors="ignore")


def _extract_challenge_token(html: str) -> Optional[str]:
    if "window.location.href" not in html or "challenge=" not in html:
        return None
    match = CHALLENGE_TOKEN_RE.search(html)
    return match.group(1) if match else None


def _build_challenge_url(original_url: str, token: str) -> str:
    parts = urlsplit(original_url)
    return urlunsplit((parts.scheme, parts.netloc, parts.path, f"challenge={quote(token, safe='')}", ""))


def _get_sync(session, url: str, headers: dict, verify: bool):
    return session.get(url, headers=headers, timeout=20, impersonate="chrome", verify=verify)


async def _get(session, url: str, headers: dict, verify: bool):
    return await asyncio.to_thread(_get_sync, session, url, headers, verify)


# 헤드리스 브라우저는 Cloudflare에 자동화 브라우저로 감지되어 챌린지를 통과하지 못하는
# 사이트(69shuba.com)가 있어, TLS 지문까지 실제 크롬처럼 흉내내는 curl_cffi로 통일한다.
#
# verify=False가 필요한 사이트(m.xsw.tw)가 있다: 서버가 리프 인증서만 보내고 중간(intermediate)
# 인증서를 체인에 포함하지 않아, 브라우저는 캐시된 중간 인증서로 우회 검증하지만 curl류
# 클라이언트는 "unable to get local issuer certificate"(curl 에러 60)로 실패한다
# (openssl s_client로 확인: issuer가 Certum DV TLS G2 R39 CA인데 체인에 리프 인증서 1장뿐).
# 서버 쪽 설정 문제라 클라이언트에서 고칠 수 없으므로, 로그인/자격증명이 오가지 않는 읽기전용
# 크롤링 대상이라는 점을 감안해 해당 호스트에 한해 인증서 검증을 끈다.
async def fetch_impersonated(url: str, max_retries: int = 5, verify: bool = True) -> str:
    headers = {"User-Agent": USER_AGENT, "Referer": url}
    session = curl_requests.Session()

    last_status = None
    for attempt in range(max_retries):
        try:
            response = await _get(session, url, headers, verify)
        except Exception as e:
            raise CrawlError("CRAWL_TARGET_ERROR", 502, f"대상 사이트 요청 실패: {e}") from e

        if response.status_code == 200:
            html = _decode_html(response)
            token = _extract_challenge_token(html)
            if token is None:
                return html

            challenge_response = await _get(session, _build_challenge_url(url, token), headers, verify)
            if challenge_response.status_code == 200:
                return _decode_html(challenge_response)
            last_status = challenge_response.status_code
        else:
            last_status = response.status_code

        if last_status == 403 and attempt < max_retries - 1:
            await asyncio.sleep(random.uniform(5.0, 5.5))
            continue
        break

    raise CrawlError("CRAWL_TARGET_ERROR", 502, f"대상 사이트 응답 오류: {last_status}")
