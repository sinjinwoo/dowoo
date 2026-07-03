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
import java.util.Map;

/** api-spec.md §9.1 위임 - AI API(FastAPI)의 POST /internal/crawl을 호출한다. */
@Component
public class HttpCrawlClient implements CrawlClient {

    private final RestClient restClient;

    public HttpCrawlClient(
            @Value("${app.ai-api-base-url}") String aiApiBaseUrl,
            @Value("${app.internal-token}") String internalToken) {
        // JDK HttpClient 기본값은 HTTP/2 cleartext 업그레이드를 시도하는데, uvicorn(HTTP/1.1 전용)이
        // 이를 못 알아듣고 요청 본문을 깨뜨려서 422가 난다. HTTP/1.1로 고정해서 우회한다.
        HttpClient jdkHttpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.restClient = RestClient.builder()
                .baseUrl(aiApiBaseUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .requestFactory(new JdkClientHttpRequestFactory(jdkHttpClient))
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
                (String) data.get("siteName"));
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
