package io.dedyn.jwlabs.dowoo.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ApiKeyBulkAppendRequest(
        @NotEmpty List<@NotBlank String> apiKeys
) {
}
