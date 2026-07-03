package io.dedyn.jwlabs.dowoo.book.crawl;

/**
 * §6.1/9.1 크롤링 위임의 Core API 쪽 포트.
 * Phase 3(FastAPI AI API)이 준비되기 전까지는 {@link UnavailableCrawlClient}가 대신 응답하며,
 * Phase 4에서 AI API의 POST /internal/crawl을 호출하는 실제 구현으로 교체된다.
 */
public interface CrawlClient {

    CrawlResult crawl(String url);
}
