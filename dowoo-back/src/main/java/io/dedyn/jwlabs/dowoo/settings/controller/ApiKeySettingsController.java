package io.dedyn.jwlabs.dowoo.settings.controller;

import io.dedyn.jwlabs.dowoo.common.response.ApiResponse;
import io.dedyn.jwlabs.dowoo.settings.dto.ApiKeyAppendRequest;
import io.dedyn.jwlabs.dowoo.settings.dto.ApiSettingsResponse;
import io.dedyn.jwlabs.dowoo.settings.dto.ApiSettingsUpdateRequest;
import io.dedyn.jwlabs.dowoo.settings.service.ApiKeySettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings/api")
@RequiredArgsConstructor
public class ApiKeySettingsController {

    private final ApiKeySettingsService apiKeySettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<ApiSettingsResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success(200, apiKeySettingsService.get(), "조회 성공"));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ApiSettingsResponse>> replace(
            @Valid @RequestBody ApiSettingsUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(200, apiKeySettingsService.replace(request), "저장되었습니다."));
    }

    @PostMapping("/keys")
    public ResponseEntity<ApiResponse<ApiSettingsResponse>> appendKey(@Valid @RequestBody ApiKeyAppendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, apiKeySettingsService.appendKey(request.apiKey()), "키가 추가되었습니다."));
    }

    @DeleteMapping("/keys/{keyId}")
    public ResponseEntity<ApiResponse<Void>> deleteKey(@PathVariable UUID keyId) {
        apiKeySettingsService.deleteKey(keyId);
        return ResponseEntity.ok(ApiResponse.<Void>success(200, null, "삭제되었습니다."));
    }
}
