package io.dedyn.jwlabs.dowoo.settings.dto;

import java.util.List;
import java.util.UUID;

public record ApiSettingsResponse(
        String model,
        Integer thinkingBudget,
        List<ApiKeyItem> apiKeys
) {
    public record ApiKeyItem(UUID id, String masked, Integer order) {
    }
}
