package io.dedyn.jwlabs.dowoo.book.crawl;

public record CrawlResult(
        String title,
        String content,
        String prevUrl,
        String nextUrl,
        String siteName
) {
}
