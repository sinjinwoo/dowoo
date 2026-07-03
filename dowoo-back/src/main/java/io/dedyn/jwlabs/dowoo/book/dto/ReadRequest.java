package io.dedyn.jwlabs.dowoo.book.dto;

public record ReadRequest(
        String sourceUrl,
        String pastedText,
        Boolean forceRecrawl
) {
    public boolean isForceRecrawl() {
        return Boolean.TRUE.equals(forceRecrawl);
    }
}
