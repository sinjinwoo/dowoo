package io.dedyn.jwlabs.dowoo.auth.dto;

public record AuthResponse(String accessToken, long accessTokenExpiresIn, UserResponse user) {
}
