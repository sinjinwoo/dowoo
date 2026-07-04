package io.dedyn.jwlabs.dowoo.auth.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    // 로그인 정책(토큰 만료 시간)은 애플리케이션 정책이라 사용자가 배포 시 바꿀 값이 아니므로 고정한다.
    private static final long ACCESS_TOKEN_VALIDITY_SECONDS = 1800;

    private final SecretKey key;

    public JwtTokenProvider(@Value("${app.jwt-secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public long accessTokenValiditySeconds() {
        return ACCESS_TOKEN_VALIDITY_SECONDS;
    }

    public String generateAccessToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ACCESS_TOKEN_VALIDITY_SECONDS)))
                .signWith(key)
                .compact();
    }

    /** @throws JwtException 토큰이 없거나 형식이 올바르지 않거나 만료된 경우 */
    public UUID parseUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return UUID.fromString(subject);
    }
}
