# 트러블슈팅 기록

백엔드 전환(Spring Boot Core API + FastAPI AI API) 작업 중 겪은 문제와 해결 과정을 원인별로 정리한 문서다. 비슷한 증상을 다시 만났을 때 처음부터 재진단하지 않도록 남겨둔다.

| 문서 | 증상 요약 |
| --- | --- |
| [01-docker-gradle-wrapper-version.md](01-docker-gradle-wrapper-version.md) | Docker 빌드 시 "Spring Boot plugin requires Gradle 8.14+/9.x" |
| [02-spring-boot4-flyway-not-running.md](02-spring-boot4-flyway-not-running.md) | Flyway가 조용히 실행 안 되고 `missing table` 에러 |
| [03-spring-boot4-jackson3-objectmapper.md](03-spring-boot4-jackson3-objectmapper.md) | `ObjectMapper` 빈을 찾을 수 없음 (Jackson 3 전환) |
| [04-uuid-path-variable-500-error.md](04-uuid-path-variable-500-error.md) | 잘못된 UUID 경로 변수가 400이 아니라 500으로 응답 |
| [05-windows-curl-korean-encoding.md](05-windows-curl-korean-encoding.md) | Windows curl로 한글 JSON 전송 시 "요청 본문을 읽을 수 없습니다" |
| [06-playwright-blocked-by-cloudflare.md](06-playwright-blocked-by-cloudflare.md) | Playwright(헤드리스/헤드풀)가 69shuba.com Cloudflare에 계속 막힘 |
| [07-html-encoding-detection-order-bug.md](07-html-encoding-detection-order-bug.md) | 인코딩 자동 감지 순서 오류로 UTF-8 사이트가 깨져서 나옴 |
| [08-ixdzs8-js-redirect-challenge.md](08-ixdzs8-js-redirect-challenge.md) | ixdzs8.com만 계속 PARSE_FAILED (JS 토큰 리다이렉트) |
| [09-jdk-httpclient-http2-vs-uvicorn.md](09-jdk-httpclient-http2-vs-uvicorn.md) | Core API → AI API 호출이 전부 422 (HTTP/2 업그레이드 충돌) |

공통적으로 얻은 교훈:

- **Spring Boot 4는 자동 설정이 기능별로 잘게 쪼개졌다.** "라이브러리가 클래스패스에 있으면 예전처럼 자동 설정되겠지"라는 가정이 Flyway, Jackson에서 연달아 깨졌다. 새 기능을 붙일 때는 해당 `spring-boot-starter-*`가 있는지부터 확인한다. (01, 02, 03)
- **에러 메시지를 감추지 말 것.** `GlobalExceptionHandler`가 원인 메시지를 `null`로 버리고 있어서 UTF-8 인코딩 문제를 처음엔 진단할 수 없었다. (05)
- **크롤링 실패 = Cloudflare/CAPTCHA라고 단정하지 말 것.** 실제 응답 HTML을 직접 확인해야 진짜 원인(단순 토큰 리다이렉트 vs 진짜 봇 감지)을 알 수 있다. (06, 08)
- **클라이언트 에러만으로 원인이 안 잡히면 상대 서버 로그를 같이 볼 것.** 422/CRAWL_TARGET_ERROR의 진짜 원인은 Core API 로그가 아니라 `ai-api` 로그의 "Unsupported upgrade request" 한 줄에 있었다. (09)
