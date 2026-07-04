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
| [10-vite-config-dead-import-after-cleanup.md](10-vite-config-dead-import-after-cleanup.md) | 프론트 마이그레이션 후 `npm run dev`가 vite.config.ts에서 죽음 |
| [11-preview-model-billing-and-silent-sdk-failures.md](11-preview-model-billing-and-silent-sdk-failures.md) | 무료 키로 번역이 계속 실패 (preview 모델은 결제 필요 + SDK가 조용히 빈 응답만 줌) |
| [12-book-title-vs-chapter-title-per-site.md](12-book-title-vs-chapter-title-per-site.md) | 서재의 책 제목이 챕터 제목과 똑같이 저장됨 |
| [13-jwt-filter-skipped-on-sse-async-dispatch.md](13-jwt-filter-skipped-on-sse-async-dispatch.md) | 번역 중지 시 로그에 `AuthorizationDeniedException` |
| [14-translate-stream-missing-auth-header.md](14-translate-stream-missing-auth-header.md) | 로그인 후 "불러오기"는 되는데 번역 스트림만 401 |
| [15-chapter-nav-index-vs-prevnext-url.md](15-chapter-nav-index-vs-prevnext-url.md) | 이전편/다음편 버튼이 작동하지 않음 |
| [16-translation-lost-on-stream-interrupt.md](16-translation-lost-on-stream-interrupt.md) | 번역 중지 후 돌아오면 진행 상황이 사라지고 재번역됨 |
| [17-sse-close-exception-after-successful-done.md](17-sse-close-exception-after-successful-done.md) | 번역이 성공적으로 끝났는데도 네트워크 에러 모달이 뜸 |
| [18-idle-timeout-too-short-for-long-chapters.md](18-idle-timeout-too-short-for-long-chapters.md) | 본문이 매우 길면 번역이 지연 에러(TRANSLATE_TIMEOUT)로 끊김 |
| [19-last-chapter-shows-crawl-error.md](19-last-chapter-shows-crawl-error.md) | 마지막 화에서 "다음 편" 클릭 시 안내 대신 크롤링 오류(PARSE_FAILED)가 뜸 |
| [20-exceptionhandler-fails-on-committed-sse-response.md](20-exceptionhandler-fails-on-committed-sse-response.md) | 번역 스트리밍 중 다음 편 이동 시 GlobalExceptionHandler에서 2차 예외 로그 |
| [21-chunk-translation-key-rpm-concentration.md](21-chunk-translation-key-rpm-concentration.md) | 청크 번역 도입 시 청크마다 같은 키만 써서 RPM 한도에 몰릴 뻔한 설계 문제 |
| [22-chunk-buffering-broke-realtime-streaming.md](22-chunk-buffering-broke-realtime-streaming.md) | 청크 번역 도입 후 실시간 스트리밍이 안 되고 번역이 끝나야 한 번에 보임 |

공통적으로 얻은 교훈:

- **Spring Boot 4는 자동 설정이 기능별로 잘게 쪼개졌다.** "라이브러리가 클래스패스에 있으면 예전처럼 자동 설정되겠지"라는 가정이 Flyway, Jackson에서 연달아 깨졌다. 새 기능을 붙일 때는 해당 `spring-boot-starter-*`가 있는지부터 확인한다. (01, 02, 03)
- **에러 메시지를 감추지 말 것.** `GlobalExceptionHandler`가 원인 메시지를 `null`로 버리고 있어서 UTF-8 인코딩 문제를 처음엔 진단할 수 없었다. (05)
- **크롤링 실패 = Cloudflare/CAPTCHA라고 단정하지 말 것.** 실제 응답 HTML을 직접 확인해야 진짜 원인(단순 토큰 리다이렉트 vs 진짜 봇 감지)을 알 수 있다. (06, 08)
- **클라이언트 에러만으로 원인이 안 잡히면 상대 서버 로그를 같이 볼 것.** 422/CRAWL_TARGET_ERROR의 진짜 원인은 Core API 로그가 아니라 `ai-api` 로그의 "Unsupported upgrade request" 한 줄에 있었다. (09)
- **디렉터리를 지울 때는 `src/`뿐 아니라 설정 파일도 검색할 것.** `src/crawl/`을 지우면서 `vite.config.ts`의 import는 놓쳤다. 사전 타입 체크에 에러가 여러 개 뜨면 "내가 안 건드린 파일이니 무관하겠지"라고 넘겨짚지 말고 하나씩 확인한다. (10)
- **서드파티 SDK/외부 서비스가 "실패해도 예외를 안 던지는" 경우를 항상 의심할 것.** 빈 응답을 "성공"으로 오판하면 재시도/폴백 로직 자체가 무력화된다. preview·실험 버전 모델/기능은 이름만으로 결제·권한 요구사항을 알 수 없으니 공식 문서에서 상태를 확인한다. (11)
- **같은 `href`/셀렉터에 여러 후보가 매치될 수 있다는 걸 가정할 것.** `find()`(첫 매치)만 믿지 말고 `find_all()`로 전부 확인 후 필터링한다. (12)
- **비동기 반환 타입(`SseEmitter`/`DeferredResult`)을 쓰면 커스텀 `OncePerRequestFilter`가 ASYNC 재디스패치에서 기본적으로 스킵된다는 걸 잊지 말 것.** 스레드로컬 상태(SecurityContext 등)를 채우는 필터라면 `shouldNotFilterAsyncDispatch()`를 오버라이드해야 한다. (13)
- **인증을 나중에 추가하는 리팩터링 때는 공용 API 클라이언트를 안 거치고 직접 `fetch`하는 코드가 없는지 grep으로 확인할 것.** SSE/스트리밍처럼 예외적으로 raw fetch를 쓰는 코드가 사각지대가 되기 쉽다. (14)
- **타입에 필드가 존재한다고 해서 UI가 그 필드를 실제로 쓰고 있다고 가정하지 말 것.** 스캐폴딩 단계에서 타입만 먼저 정의되고 배선은 나중으로 미뤄졌을 수 있다. (15)
- **스트리밍 응답을 다루는 서버 코드는 정상 종료 경로뿐 아니라 클라이언트가 언제든 연결을 끊을 수 있다는 전제로 모든 종료 경로(에러/타임아웃/연결 끊김)의 부분 결과 처리를 설계할 것.** (16)
- **try-with-resources의 암묵적 `close()`가 던지는 예외는 같은 try 블록의 `catch`에 잡힌다는 걸 기억할 것.** "이미 성공적으로 끝나고 반환하는" 경로에서 리소스 정리 실패가 성공 처리 자체를 덮어쓰지 않도록, 정상 처리와 리소스 정리의 예외 범위를 분리한다. (17)
- **사이트 마크업 문자열은 한 글자 차이(`.htm` vs `.html`)로 완전히 다른 뜻이 될 수 있다.** 대화로 여러 번 확인하며 오락가락하느니, 정규식을 애초에 관대하게 만들어(`.html?`) 어느 쪽이든 매치하게 하는 편이 안전하다. 또한 크롤링 결과(`nextUrl` 등)는 크롤링 시점의 스냅샷이라 파서를 고쳐도 이미 저장된 데이터는 자동으로 갱신되지 않는다는 것도 기억할 것. (19)
- **전역 예외 핸들러가 하나의 Content-Type만 가정하고 항상 같은 바디를 쓰려 하면 안 된다.** SSE 등 스트리밍 응답은 이미 다른 Content-Type으로 커밋돼 있을 수 있으므로 `HttpServletResponse.isCommitted()`로 분기해야 한다. (20)
- **"직전에 성공한 걸 재사용"과 "여러 리소스에 부하 분산"은 충돌할 수 있다.** 하나의 논리적 작업이 내부적으로 여러 번의 API 호출로 쪼개진다면(예: 긴 챕터를 청크로 나눠 번역), 재시도 효율만 보고 같은 자원(API 키)을 계속 재사용하면 그 자원에 부하가 몰린다. 결과 품질에 영향을 주는 축(모델)과 순수 자원 축(키)을 구분해서, 품질 축은 유지하고 자원 축만 라운드로빈으로 분산시킨다. (21)
- **재시도/에러 격리를 위한 안전장치가 스트리밍의 실시간성 자체를 깨뜨리지 않는지 확인할 것.** "끝난 뒤 한꺼번에 검증하고 내보내기"는 안전해 보이지만, 스트리밍 아키텍처에서는 그 자체로 스트리밍을 꺼버리는 것과 같다. 리팩터링 전에 기존 기능이 암묵적으로 어떤 코드 경로에 의존하는지 목록화해두지 않으면 다른 목적으로 코드를 고치다가 조용히 회귀시키기 쉽다. (22)
