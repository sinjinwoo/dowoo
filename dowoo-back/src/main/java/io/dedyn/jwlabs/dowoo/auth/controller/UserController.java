package io.dedyn.jwlabs.dowoo.auth.controller;

import io.dedyn.jwlabs.dowoo.auth.dto.UserResponse;
import io.dedyn.jwlabs.dowoo.auth.service.UserService;
import io.dedyn.jwlabs.dowoo.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.success(200, userService.me(), "조회 성공"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw() {
        userService.withdraw();
        return ResponseEntity.ok(ApiResponse.<Void>success(200, null, "회원 탈퇴가 처리되었습니다."));
    }
}
