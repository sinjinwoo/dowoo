package io.dedyn.jwlabs.dowoo.auth.controller;

import io.dedyn.jwlabs.dowoo.auth.dto.AccessTokenResponse;
import io.dedyn.jwlabs.dowoo.auth.dto.AuthResponse;
import io.dedyn.jwlabs.dowoo.auth.dto.LoginRequest;
import io.dedyn.jwlabs.dowoo.auth.dto.SignupRequest;
import io.dedyn.jwlabs.dowoo.auth.dto.UsernameAvailabilityResponse;
import io.dedyn.jwlabs.dowoo.auth.service.AuthService;
import io.dedyn.jwlabs.dowoo.auth.service.RefreshTokenService;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    /**
     * refresh/logout는 쿠키만으로 인증되는 요청이라 SameSite=Lax 외에 한 겹 더 CSRF 방어가 필요하다.
     * 이 커스텀 헤더는 단순 폼 기반 CSRF로는 절대 붙일 수 없고, 크로스 오리진 fetch/XHR이 이 헤더를 붙이면
     * CORS preflight를 유발하므로 허용된 오리진(WebConfig)이 아니면 브라우저가 요청 자체를 막는다.
     */
    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_HEADER_VALUE = "XMLHttpRequest";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @GetMapping("/check-username")
    public ResponseEntity<ApiResponse<UsernameAvailabilityResponse>> checkUsername(@RequestParam String username) {
        return ResponseEntity.ok(ApiResponse.success(200, authService.checkUsername(username), "조회 성공"));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        AuthService.TokenIssueResult result = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.rawRefreshToken(), httpRequest).toString())
                .body(ApiResponse.success(201, result.authResponse(), "회원가입에 성공했습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthService.TokenIssueResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.rawRefreshToken(), httpRequest).toString())
                .body(ApiResponse.success(200, result.authResponse(), "로그인에 성공했습니다."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletRequest request) {
        requireCsrfHeader(request);
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.rawRefreshToken(), request).toString())
                .body(ApiResponse.success(200, result.accessTokenResponse(), "토큰이 재발급되었습니다."));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletRequest request) {
        requireCsrfHeader(request);
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie(request).toString())
                .body(ApiResponse.<Void>success(200, null, "로그아웃되었습니다."));
    }

    private void requireCsrfHeader(HttpServletRequest request) {
        if (!CSRF_HEADER_VALUE.equals(request.getHeader(CSRF_HEADER))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CSRF_HEADER_MISSING",
                    "필수 요청 헤더가 누락되었습니다.");
        }
    }

    private ResponseCookie refreshCookie(String value, HttpServletRequest request) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(refreshTokenService.validityDays() * 24 * 60 * 60)
                .build();
    }

    private ResponseCookie expiredRefreshCookie(HttpServletRequest request) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}
