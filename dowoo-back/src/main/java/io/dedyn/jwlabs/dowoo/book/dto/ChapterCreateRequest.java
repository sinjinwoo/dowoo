package io.dedyn.jwlabs.dowoo.book.dto;

import jakarta.validation.constraints.NotBlank;

public record ChapterCreateRequest(
        @NotBlank String sourceUrl,
        @NotBlank String title,
        @NotBlank String originalText,
        String prevUrl,
        String nextUrl
) {
}
