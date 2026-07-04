package io.dedyn.jwlabs.dowoo.auth.service;

import io.dedyn.jwlabs.dowoo.auth.entity.RefreshToken;
import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.RefreshTokenRepository;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    // 로그인 정책(토큰 만료 시간)은 애플리케이션 정책이라 사용자가 배포 시 바꿀 값이 아니므로 고정한다.
    private static final long REFRESH_TOKEN_VALIDITY_DAYS = 14;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public long validityDays() {
        return REFRESH_TOKEN_VALIDITY_DAYS;
    }

    /** @return 쿠키에 담을 원문 토큰 값 (DB에는 해시만 저장됨) */
    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(OffsetDateTime.now().plusDays(REFRESH_TOKEN_VALIDITY_DAYS));
        entity.setCreatedAt(OffsetDateTime.now());
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    /** 기존 토큰을 무효화하고 새 토큰을 발급한다(회전). @return 새 원문 토큰 값 */
    @Transactional
    public String rotate(String currentRawToken) {
        RefreshToken current = validate(currentRawToken);
        User user = current.getUser();
        refreshTokenRepository.delete(current);
        return issue(user);
    }

    @Transactional(readOnly = true)
    public RefreshToken validate(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "리프레시 토큰이 유효하지 않습니다."));
        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(token);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "리프레시 토큰이 만료되었습니다.");
        }
        return token;
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.deleteByTokenHash(hash(rawToken));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
