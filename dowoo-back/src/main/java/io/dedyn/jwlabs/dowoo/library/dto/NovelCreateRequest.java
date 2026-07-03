package io.dedyn.jwlabs.dowoo.library.dto;

import jakarta.validation.constraints.NotBlank;

public record NovelCreateRequest(
        @NotBlank String sourceUrl,
        @NotBlank String siteName,
        String title,
        String originalTitle,
        String coverUrl,
        String systemPrompt,
        String translationNote
) {
}
