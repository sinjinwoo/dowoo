package io.dedyn.jwlabs.dowoo.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Phase 6(OAuth2 로그인)이 붙기 전까지 모든 요청을 고정된 로컬 사용자로 취급한다.
 * 로그인이 도입되면 SecurityContext에서 실제 userId를 꺼내는 구현으로 교체한다.
 */
@Component
public class CurrentUserProvider {

    private final UUID localUserId;

    public CurrentUserProvider(@Value("${app.local-user-id}") String localUserId) {
        this.localUserId = UUID.fromString(localUserId);
    }

    public UUID currentUserId() {
        return localUserId;
    }
}
