package io.dedyn.jwlabs.dowoo.library.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PromptResponse(
        UUID id,
        String title,
        String systemPrompt,
        String translationNote,
        boolean isDefault,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
