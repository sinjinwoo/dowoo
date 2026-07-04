package io.dedyn.jwlabs.dowoo.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(UUID id, String username, OffsetDateTime createdAt) {
}
