package io.dedyn.jwlabs.dowoo.book.crawl;

import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/** api-spec.md §9.1 위임 - AI API(FastAPI)의 POST /internal/crawl을 호출한다. */
@Component
public class HttpCrawlClient implements CrawlClient {

    // 내부망 커넥션 연결이라 짧게 잡아도 충분하다. 사용자가 배포 시 바꿀 값이 아니므로 고정한다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    // AI API(fetcher.py)의 크롤링 재시도 전체 소요 시간(최대 5회 x 20초 요청 + 403 백오프, 최악의
    // 경우 약 2분)보다 넉넉하게 잡아야 정상적으로 재시도 중인 크롤링을 중간에 끊지 않는다.
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(180);

    private final RestClient restClient;

    public HttpCrawlClient(
            @Value("${app.ai-api-base-url}") String aiApiBaseUrl,
            @Value("${app.internal-token}") String internalToken) {
        // JDK HttpClient 기본값은 HTTP/2 cleartext 업그레이드를 시도하는데, uvicorn(HTTP/1.1 전용)이
        // 이를 못 알아듣고 요청 본문을 깨뜨려서 422가 난다. HTTP/1.1로 고정해서 우회한다.
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(aiApiBaseUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public CrawlResult crawl(String url) {
        Map<String, Object> body;
        try {
            body = restClient.post()
                    .uri("/internal/crawl")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("url", url))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientResponseException e) {
            throw toApiException(e);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_API_UNAVAILABLE",
                    "크롤링 서버에 연결할 수 없습니다.");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = body != null ? (Map<String, Object>) body.get("data") : null;
        if (data == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PARSE_FAILED", "크롤링 결과를 읽을 수 없습니다.");
        }

        return new CrawlResult(
                (String) data.get("title"),
                (String) data.get("bookTitle"),
                (String) data.get("content"),
                (String) data.get("prevUrl"),
                (String) data.get("nextUrl"),
                (String) data.get("siteName"),
                (String) data.get("sourceBookId"));
    }

    @SuppressWarnings("unchecked")
    private ApiException toApiException(RestClientResponseException e) {
        try {
            Map<String, Object> body = e.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() {
            });
            Map<String, Object> error = body != null ? (Map<String, Object>) body.get("error") : null;
            String code = error != null && error.get("code") != null ? (String) error.get("code") : "CRAWL_TARGET_ERROR";
            String message = body != null && body.get("message") != null
                    ? (String) body.get("message")
                    : "크롤링에 실패했습니다.";
            return new ApiException(HttpStatus.valueOf(e.getStatusCode().value()), code, message);
        } catch (Exception parseFailure) {
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_API_UNAVAILABLE",
                    "크롤링 서버 응답을 처리할 수 없습니다.");
        }
    }
}
