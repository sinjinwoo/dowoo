package io.dedyn.jwlabs.dowoo.auth.dto;

public record AccessTokenResponse(String accessToken, long accessTokenExpiresIn) {
}
