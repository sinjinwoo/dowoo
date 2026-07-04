package io.dedyn.jwlabs.dowoo.auth.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-jwt-secret-must-be-at-least-32-bytes-long";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET);
    }

    @Test
    void parseUserId_returnsSameIdThatWasSigned() {
        UUID userId = UUID.randomUUID();

        String token = jwtTokenProvider.generateAccessToken(userId);

        assertNotNull(token);
        assertEquals(userId, jwtTokenProvider.parseUserId(token));
    }

    @Test
    void parseUserId_withGarbageToken_throwsJwtException() {
        assertThrows(JwtException.class, () -> jwtTokenProvider.parseUserId("not-a-real-jwt"));
    }

    @Test
    void parseUserId_tokenSignedWithDifferentSecret_throwsJwtException() {
        JwtTokenProvider otherProvider = new JwtTokenProvider("a-completely-different-secret-that-is-also-32-bytes");
        String token = otherProvider.generateAccessToken(UUID.randomUUID());

        assertThrows(JwtException.class, () -> jwtTokenProvider.parseUserId(token));
    }
}
