package io.dedyn.jwlabs.dowoo.settings.dto;

public record ThemeSettingsResponse(
        String fontFamily,
        String fontColor,
        String bgColor,
        String fontSize,
        String fontWeight,
        String lineHeight,
        String textIndent
) {
}
