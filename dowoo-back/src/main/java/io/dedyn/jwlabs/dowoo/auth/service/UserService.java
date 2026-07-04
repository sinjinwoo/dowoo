package io.dedyn.jwlabs.dowoo.auth.service;

import io.dedyn.jwlabs.dowoo.auth.dto.UserResponse;
import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public UserResponse me() {
        User user = userRepository.findById(currentUserProvider.currentUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        return new UserResponse(user.getId(), user.getUsername(), user.getCreatedAt());
    }

    @Transactional
    public void withdraw() {
        User user = userRepository.findById(currentUserProvider.currentUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        user.setWithdrawnAt(OffsetDateTime.now());
    }
}
