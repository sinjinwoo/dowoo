package io.dedyn.jwlabs.dowoo.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record ThemeSettingsUpdateRequest(
        @NotBlank String fontFamily,
        @NotBlank String fontColor,
        @NotBlank String bgColor,
        @NotBlank String fontSize,
        @NotBlank String fontWeight,
        @NotBlank String lineHeight,
        @NotBlank String textIndent
) {
}
