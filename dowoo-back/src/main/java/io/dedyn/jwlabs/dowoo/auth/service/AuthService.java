package io.dedyn.jwlabs.dowoo.auth.service;

import io.dedyn.jwlabs.dowoo.auth.dto.AccessTokenResponse;
import io.dedyn.jwlabs.dowoo.auth.dto.AuthResponse;
import io.dedyn.jwlabs.dowoo.auth.dto.LoginRequest;
import io.dedyn.jwlabs.dowoo.auth.dto.SignupRequest;
import io.dedyn.jwlabs.dowoo.auth.dto.UserResponse;
import io.dedyn.jwlabs.dowoo.auth.dto.UsernameAvailabilityResponse;
import io.dedyn.jwlabs.dowoo.auth.entity.RefreshToken;
import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.auth.security.JwtTokenProvider;
import io.dedyn.jwlabs.dowoo.auth.security.UserPrincipal;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    @Transactional(readOnly = true)
    public UsernameAvailabilityResponse checkUsername(String username) {
        return new UsernameAvailabilityResponse(username, !userRepository.existsByUsername(username));
    }

    @Transactional
    public TokenIssueResult signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_USERNAME", "이미 사용 중인 아이디입니다.");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreatedAt(OffsetDateTime.now());
        user = userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public TokenIssueResult login(LoginRequest request) {
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
            User user = ((UserPrincipal) authentication.getPrincipal()).getUser();
            return issueTokens(user);
        } catch (AuthenticationException e) {
            // 아이디 없음/비밀번호 불일치/탈퇴 계정을 구분 없이 같은 메시지로 응답한다(계정 존재 여부 노출 방지).
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
    }

    @Transactional
    public RefreshResult refresh(String refreshTokenCookieValue) {
        if (refreshTokenCookieValue == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "리프레시 토큰이 없습니다.");
        }
        RefreshToken current = refreshTokenService.validate(refreshTokenCookieValue);
        User user = current.getUser();
        String newRawRefreshToken = refreshTokenService.rotate(refreshTokenCookieValue);
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        return new RefreshResult(
                new AccessTokenResponse(accessToken, jwtTokenProvider.accessTokenValiditySeconds()),
                newRawRefreshToken);
    }

    @Transactional
    public void logout(String refreshTokenCookieValue) {
        if (refreshTokenCookieValue != null) {
            refreshTokenService.revoke(refreshTokenCookieValue);
        }
    }

    private TokenIssueResult issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String rawRefreshToken = refreshTokenService.issue(user);
        UserResponse userResponse = new UserResponse(user.getId(), user.getUsername(), user.getCreatedAt());
        AuthResponse authResponse = new AuthResponse(accessToken, jwtTokenProvider.accessTokenValiditySeconds(), userResponse);
        return new TokenIssueResult(authResponse, rawRefreshToken);
    }

    public record TokenIssueResult(AuthResponse authResponse, String rawRefreshToken) {
    }

    public record RefreshResult(AccessTokenResponse accessTokenResponse, String rawRefreshToken) {
    }
}
