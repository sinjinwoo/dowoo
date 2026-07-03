# Core API → AI API 호출이 전부 422로 실패 (JDK HttpClient의 HTTP/2 업그레이드 시도)

## 증상

Core API에서 AI API(`ai-api:8000`)의 `/internal/crawl`을 호출하는 `HttpCrawlClient`를 실제로 붙이고 `/api/v1/read`를 테스트하니, 정상적인 요청 바디(`{"url": "..."}`)를 보냈는데도 항상 다음과 같은 에러가 났다.

```json
{"status":422, "message":"크롤링에 실패했습니다.", "error":{"code":"CRAWL_TARGET_ERROR", "details":null}}
```

같은 시각 `ai-api` 로그를 보면:

```
WARNING:  Unsupported upgrade request.
INFO:     172.20.0.4:51634 - "POST /internal/crawl HTTP/1.1" 422 Unprocessable Entity
```

422는 FastAPI/Pydantic이 요청 바디 검증에 실패했을 때 자동으로 내려주는 기본 상태 코드다 — 즉 Core API가 보낸 요청 바디가 `CrawlRequest(url: str)` 스키마와 맞지 않는 것으로 우비콘(uvicorn)이 파싱했다는 뜻이었다.

## 원인

Core API의 HTTP 클라이언트(`RestClient`, `TranslateService`의 raw `java.net.http.HttpClient` 둘 다)를 별다른 설정 없이 기본값으로 생성했다. **JDK의 `java.net.http.HttpClient`는 기본적으로 HTTP/2를 우선 시도하며, 평문(cleartext) 연결에서도 HTTP/1.1 요청에 업그레이드 헤더(`Connection: Upgrade`, `Upgrade: h2c` 등)를 끼워 넣어 서버가 HTTP/2를 지원하면 전환을 시도**한다. 그런데 uvicorn은 HTTP/1.1만 지원하고 이 업그레이드 시도를 제대로 처리하지 못해("Unsupported upgrade request") 요청을 잘못 파싱하고, 결과적으로 FastAPI 핸들러에는 `url` 필드가 비어있거나 손상된 형태로 전달되어 Pydantic이 422를 반환했다.

## 해결

Core API 쪽에서 AI API로 나가는 모든 HTTP 클라이언트를 **HTTP/1.1로 고정**했다.

```java
// HttpCrawlClient (RestClient)
HttpClient jdkHttpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
this.restClient = RestClient.builder()
        .baseUrl(aiApiBaseUrl)
        .defaultHeader("X-Internal-Token", internalToken)
        .requestFactory(new JdkClientHttpRequestFactory(jdkHttpClient))
        .build();
```

```java
// TranslateService (raw HttpClient, 번역 스트림 릴레이용)
private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
```

수정 후 재빌드하니 `/api/v1/read`, `/api/v1/novels/{id}/chapters/{id}/translate/stream` 모두 정상 동작했다(실제 크롤링 결과 수신, 실제 Gemini 키로 번역 스트림 끝까지 수신 및 DB 저장까지 확인).

## 참고

- JDK 11+의 `HttpClient.newHttpClient()`(버전 미지정)는 기본값이 `HTTP_2`이며 "가능하면 HTTP/2로 격상, 안 되면 HTTP/1.1로 폴백"하는 동작을 한다. 상대 서버가 HTTP/1.1만 지원한다는 걸 미리 알고 있다면(uvicorn, 대부분의 경량 서버 등) 처음부터 `Version.HTTP_1_1`로 명시하는 편이 이런 종류의 프로토콜 협상 실패를 원천 차단한다.
- 증상(422, "요청 바디가 이상함")만 보면 직렬화/DTO 문제로 오해하기 쉽지만, 상대 서버 로그의 "Unsupported upgrade request" 같은 저수준 프로토콜 경고가 실마리였다. 클라이언트 쪽 에러 메시지만으로 원인이 안 잡히면 **서버 쪽 로그를 반드시 같이 확인**할 것.
- 앞으로 Core API에 AI API를 호출하는 클라이언트를 추가할 때는 항상 이 패턴(HTTP/1.1 고정)을 적용해야 한다.
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/crawl/HttpCrawlClient.java`, `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/service/TranslateService.java`
