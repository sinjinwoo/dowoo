package io.dedyn.jwlabs.dowoo.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record ApiKeyAppendRequest(
        @NotBlank String apiKey
) {
}
