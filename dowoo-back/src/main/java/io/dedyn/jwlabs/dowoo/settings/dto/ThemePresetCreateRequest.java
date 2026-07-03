package io.dedyn.jwlabs.dowoo.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record ThemePresetCreateRequest(
        @NotBlank String name,
        @NotEmpty Map<String, Object> theme
) {
}
