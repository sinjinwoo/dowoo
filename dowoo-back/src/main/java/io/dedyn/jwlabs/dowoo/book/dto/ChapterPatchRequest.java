package io.dedyn.jwlabs.dowoo.book.dto;

import jakarta.validation.constraints.NotBlank;

public record ChapterPatchRequest(
        @NotBlank String translatedText
) {
}
