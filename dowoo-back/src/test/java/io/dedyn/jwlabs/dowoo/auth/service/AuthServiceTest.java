package io.dedyn.jwlabs.dowoo.auth.service;

import io.dedyn.jwlabs.dowoo.auth.dto.LoginRequest;
import io.dedyn.jwlabs.dowoo.auth.dto.SignupRequest;
import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.auth.security.JwtTokenProvider;
import io.dedyn.jwlabs.dowoo.auth.security.UserPrincipal;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.library.repository.PromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PromptRepository promptRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, promptRepository, passwordEncoder, jwtTokenProvider, refreshTokenService, authenticationManager);
    }

    @Test
    void signup_passwordConfirmMismatch_throwsPasswordMismatch() {
        SignupRequest request = new SignupRequest("hero123", "password1", "password2");

        ApiException ex = assertThrows(ApiException.class, () -> authService.signup(request));

        assertEquals("PASSWORD_MISMATCH", ex.getErrorCode());
        verify(userRepository, never()).existsByUsername(anyString());
    }

    @Test
    void signup_usernameAlreadyTaken_throwsDuplicateUsername() {
        SignupRequest request = new SignupRequest("hero123", "password1", "password1");
        when(userRepository.existsByUsername("hero123")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.signup(request));

        assertEquals("DUPLICATE_USERNAME", ex.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void signup_success_encodesPasswordAndIssuesTokens() {
        SignupRequest request = new SignupRequest("hero123", "password1", "password1");
        when(userRepository.existsByUsername("hero123")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("encoded-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("fake.jwt.token");
        when(jwtTokenProvider.accessTokenValiditySeconds()).thenReturn(1800L);
        when(refreshTokenService.issue(any(User.class))).thenReturn("raw-refresh-token");

        AuthService.TokenIssueResult result = authService.signup(request);

        assertEquals("hero123", result.authResponse().user().username());
        assertEquals("fake.jwt.token", result.authResponse().accessToken());
        assertEquals("raw-refresh-token", result.rawRefreshToken());
        verify(passwordEncoder).encode("password1");
    }

    @Test
    void login_usernameNotFound_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest("ghost", "password1");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request));

        assertEquals("INVALID_CREDENTIALS", ex.getErrorCode());
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest("hero123", "wrong-password");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request));

        assertEquals("INVALID_CREDENTIALS", ex.getErrorCode());
    }

    @Test
    void login_withdrawnUser_throwsInvalidCredentials() {
        LoginRequest request = new LoginRequest("hero123", "password1");
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("disabled"));

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request));

        assertEquals("INVALID_CREDENTIALS", ex.getErrorCode());
    }

    @Test
    void login_success_returnsTokens() {
        User user = existingUser();
        LoginRequest request = new LoginRequest("hero123", "password1");
        Authentication successfulAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(user), null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(successfulAuthentication);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("fake.jwt.token");
        when(jwtTokenProvider.accessTokenValiditySeconds()).thenReturn(1800L);
        when(refreshTokenService.issue(user)).thenReturn("raw-refresh-token");

        AuthService.TokenIssueResult result = authService.login(request);

        assertEquals("hero123", result.authResponse().user().username());
        assertEquals("raw-refresh-token", result.rawRefreshToken());
    }

    private User existingUser() {
        User user = new User();
        user.setUsername("hero123");
        user.setPasswordHash("encoded-hash");
        user.setCreatedAt(OffsetDateTime.now());
        return user;
    }
}
