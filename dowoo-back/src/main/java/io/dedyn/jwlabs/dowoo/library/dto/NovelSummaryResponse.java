package io.dedyn.jwlabs.dowoo.library.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NovelSummaryResponse(
        UUID id,
        String title,
        String originalTitle,
        String coverUrl,
        String sourceUrl,
        String siteName,
        long chapterCount,
        Integer lastReadChapterIndex,
        Integer order,
        OffsetDateTime updatedAt
) {
}
