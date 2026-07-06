package io.dedyn.jwlabs.dowoo.library.dto;

import jakarta.validation.constraints.NotBlank;

public record PromptCreateRequest(
        @NotBlank String title,
        String systemPrompt,
        String translationNote
) {
}
