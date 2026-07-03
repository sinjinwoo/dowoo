package io.dedyn.jwlabs.dowoo.settings.dto;

import java.util.Map;

public record ThemePresetResponse(
        String name,
        Map<String, Object> theme
) {
}
