package io.dedyn.jwlabs.dowoo.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ApiSettingsUpdateRequest(
        @NotEmpty List<@NotBlank String> apiKeys,
        @NotBlank String model,
        Integer thinkingBudget
) {
}
