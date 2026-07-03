package io.dedyn.jwlabs.dowoo.library.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NovelDetailResponse(
        UUID id,
        String title,
        String originalTitle,
        String coverUrl,
        String sourceUrl,
        String siteName,
        String systemPrompt,
        String translationNote,
        Integer lastReadChapterIndex,
        Double lastReadScrollPos,
        Integer order,
        List<ChapterSummaryResponse> chapters,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
