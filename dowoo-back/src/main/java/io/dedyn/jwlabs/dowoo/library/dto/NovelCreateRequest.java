package io.dedyn.jwlabs.dowoo.library.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record NovelCreateRequest(
        @NotBlank String sourceUrl,
        @NotBlank String siteName,
        String title,
        String originalTitle,
        String coverUrl,
        UUID promptId
) {
}
