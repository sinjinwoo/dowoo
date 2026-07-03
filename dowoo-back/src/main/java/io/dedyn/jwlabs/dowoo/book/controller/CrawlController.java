package io.dedyn.jwlabs.dowoo.book.controller;

import io.dedyn.jwlabs.dowoo.book.crawl.CrawlClient;
import io.dedyn.jwlabs.dowoo.book.crawl.CrawlResult;
import io.dedyn.jwlabs.dowoo.book.dto.CrawlUrlRequest;
import io.dedyn.jwlabs.dowoo.common.response.ApiResponse;
import io.dedyn.jwlabs.dowoo.common.util.UrlValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** api-spec.md §6.1. 6.2(/read)가 내부적으로 CrawlClient를 직접 쓰므로, 이 엔드포인트는
 * 프론트가 크롤링 결과만 필요하고 서재 등록은 원하지 않는 드문 경우를 위한 것이다. */
@RestController
@RequestMapping("/api/v1/crawl")
@RequiredArgsConstructor
public class CrawlController {

    private final CrawlClient crawlClient;

    @PostMapping
    public ResponseEntity<ApiResponse<CrawlResult>> crawl(@Valid @RequestBody CrawlUrlRequest request) {
        UrlValidator.requireHttpUrl(request.url());
        return ResponseEntity.ok(ApiResponse.success(200, crawlClient.crawl(request.url()), "크롤링 성공"));
    }
}
