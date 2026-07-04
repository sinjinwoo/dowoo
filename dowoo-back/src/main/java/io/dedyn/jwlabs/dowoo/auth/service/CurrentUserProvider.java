package io.dedyn.jwlabs.dowoo.auth.service;

import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** JwtAuthenticationFilter가 SecurityContext에 채워 넣은 인증 정보(principal = userId)를 꺼내온다. */
@Component
public class CurrentUserProvider {

    public UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증이 필요합니다.");
        }
        return userId;
    }
}
