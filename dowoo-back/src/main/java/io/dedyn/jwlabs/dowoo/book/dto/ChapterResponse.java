package io.dedyn.jwlabs.dowoo.book.dto;

import java.util.UUID;

public record ChapterResponse(
        UUID id,
        UUID novelId,
        String title,
        String sourceUrl,
        String originalText,
        String translatedText,
        String prevUrl,
        String nextUrl,
        Integer chapterIndex
) {
}
