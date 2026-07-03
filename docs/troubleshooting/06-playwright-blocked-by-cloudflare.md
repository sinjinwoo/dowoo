# 헤드리스(+헤드풀) Playwright가 69shuba.com의 Cloudflare에 계속 막힘

## 증상

크롤러(`dowoo-python`) 초기 구현은 4개 사이트(`ixdzs8.com`, `m.xsw.tw`, `69shuba.com`, `twkan.com`) 모두 Playwright(Chromium)로 통일해서 가져오도록 만들었다. `ixdzs8.com`, `m.xsw.tw`, `twkan.com`은 문제없이 크롤링됐지만 `69shuba.com`만 계속 실패했다.

```json
{"status":502, "error":{"code":"PARSE_FAILED", "details": "본문/제목을 추출하지 못했습니다..."}}
```

실제로 받아온 HTML을 확인하면 본문이 아니라 Cloudflare의 인터스티셜 페이지였다.

```html
<title>Just a moment...</title>
```

`wait_until="networkidle"`로 더 오래 기다려도(8초), 헤드리스(`headless=True`)가 아니라 Xvfb 가상 디스플레이를 띄운 헤드풀 모드(`headless=False`)로 바꿔도 결과는 동일했다.

## 원인

이건 단순 타이밍 문제가 아니라 Cloudflare가 **자동화된 브라우저 자체를 감지**해서 챌린지를 통과시켜주지 않는 것이었다. Playwright/Chromium은 CDP(Chrome DevTools Protocol) 사용 흔적이나 컨테이너의 데이터센터 IP 등 여러 신호로 자동화 브라우저임이 드러나기 쉽고, 헤드풀로 바꿔도(디스플레이 유무와 무관한 감지 신호가 더 많으므로) 근본적으로 해결되지 않는다.

## 해결

브라우저를 실제로 렌더링하는 대신, **TLS/JA3 지문까지 실제 Chrome처럼 흉내내는 HTTP 클라이언트**로 접근 방식을 완전히 바꿨다. `curl_cffi` 라이브러리의 `impersonate="chrome"` 옵션이 이를 지원한다.

```python
from curl_cffi import requests as curl_requests

response = curl_requests.get(url, headers=headers, timeout=20, impersonate="chrome")
```

이 방식으로 바꾸자 69shuba.com을 포함한 4개 사이트 모두 정상적으로 크롤링됐다. Playwright/Chromium/Xvfb 의존성을 전부 제거해 Docker 이미지도 훨씬 가벼워지고 빌드도 빨라졌다(Chromium 다운로드 단계가 없어짐).

## 참고

- 브라우저 자동화 감지(bot detection)는 "진짜 브라우저를 실행하느냐"보다 "TLS 핸드셰이크·HTTP 헤더 순서 등 네트워크 계층 지문이 진짜 브라우저와 일치하느냐"를 더 신뢰성 있게 보는 경우가 많다. Playwright/Selenium 같은 브라우저 자동화가 항상 정답은 아니며, 사이트별로 막히는 지점이 다르므로 실제 요청을 날려보고 받은 HTML을 직접 확인하는 진단이 필수적이다.
- 이 시도 이후에도 `ixdzs8.com`에서 별도의 토큰 리다이렉트 문제가 남아있었다 — [[07-ixdzs8-js-redirect-challenge.md]] 참고.
- 관련 파일: `dowoo-python/app/crawl/fetcher.py`, `dowoo-python/app/crawl/registry.py`, `dowoo-python/requirements.txt`, `dowoo-python/Dockerfile`
