# ixdzs8.com만 계속 PARSE_FAILED — 실제로는 Cloudflare가 아니라 JS 리다이렉트 챌린지

## 증상

`curl_cffi`로 전환한 뒤([[06-playwright-blocked-by-cloudflare.md]]) 나머지 3개 사이트는 정상화됐는데 `ixdzs8.com`만 계속 `PARSE_FAILED`가 발생했다.

## 원인

실제로 받아온 HTML을 직접 확인해보니 본문이 아니라 아주 작은(422바이트) 페이지였다.

```html
<!DOCTYPE html>
<html>
<head><title>正在验证浏览器</title></head>
<body>
<p>請稍等，正在進行安全驗證...</p>
<script>
    let token = "MTc4MzA2MzU2MzpkOTc4MjA4OWMxYzliY2QyZTE2MTU1NTgyNjJjNzU2NWU5M2Y2NWY2ZmM4MGU4MTFmY2RiZjdiYzcyOWYzZWM0";
    window.location.href = location.pathname + "?challenge=" + encodeURIComponent(token);
</script>
</body>
</html>
```

이건 진짜 Cloudflare 챌린지가 아니라, ixdzs8.com 자체의 **단순한 JS 리다이렉트 한 번짜리 검증**이었다. 페이지에 심어둔 `token` 값을 그대로 쿼리스트링에 붙여서 같은 경로로 한 번 더 요청하면 통과되는 구조였다(실제 계산/CAPTCHA 없음). `curl_cffi`는 이 `<script>`를 실행하지 않으니 리다이렉트를 따라가지 못해 매번 이 인터스티셜 페이지만 받아온 것이다.

## 해결

세션(쿠키 유지) 상태로 이 리다이렉트를 코드로 직접 한 번 더 따라가도록 처리했다.

1. 응답 HTML에서 `token = "..."` 패턴과 `window.location.href` 리다이렉트 흔적을 감지한다.
2. 감지되면 `location.pathname + "?challenge=" + token`에 해당하는 URL을 만들어 **같은 `Session`**으로 다시 요청한다 (쿠키가 유지되어야 통과됨 — 실제로 두 번째 응답에서 `PHPSESSID` 쿠키가 세팅되는 것을 확인).
3. 두 번째 응답이 진짜 콘텐츠다.

```python
def _extract_challenge_token(html: str) -> Optional[str]:
    if "window.location.href" not in html or "challenge=" not in html:
        return None
    match = CHALLENGE_TOKEN_RE.search(html)
    return match.group(1) if match else None

def _build_challenge_url(original_url: str, token: str) -> str:
    parts = urlsplit(original_url)
    return urlunsplit((parts.scheme, parts.netloc, parts.path, f"challenge={quote(token, safe='')}", ""))

# fetch_impersonated 안에서:
html = _decode_html(response)
token = _extract_challenge_token(html)
if token is None:
    return html
challenge_response = await _get(session, _build_challenge_url(url, token), headers)
return _decode_html(challenge_response)
```

## 참고

- "크롤링이 실패한다 = Cloudflare/CAPTCHA다"라고 성급히 단정하지 말 것. 실제로 받아온 raw HTML을 항상 직접 눈으로 확인해야 한다 — 이번 케이스는 계산이 필요 없는 단순 토큰 리다이렉트였는데도, 처음엔 다른 사이트들과 똑같이 "봇 차단"으로 오해할 뻔했다.
- 이런 패턴(토큰을 쿼리에 붙여 같은 경로로 재요청)은 실제 CAPTCHA 없이 단순 스크립트 실행 여부만 확인하는 저강도 봇 차단에서 흔하다. 헤드리스 브라우저 없이도 요청 한 번을 더 보내는 것으로 충분히 우회 가능하다.
- 관련 파일: `dowoo-python/app/crawl/fetcher.py`
