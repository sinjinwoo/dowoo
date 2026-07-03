package io.dedyn.jwlabs.dowoo.settings.controller;

import io.dedyn.jwlabs.dowoo.common.response.ApiResponse;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemePresetCreateRequest;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemePresetResponse;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemeSettingsResponse;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemeSettingsUpdateRequest;
import io.dedyn.jwlabs.dowoo.settings.service.ThemeService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/theme")
@RequiredArgsConstructor
public class ThemeController {

    private final ThemeService themeService;

    @GetMapping
    public ResponseEntity<ApiResponse<ThemeSettingsResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success(200, themeService.get(), "조회 성공"));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ThemeSettingsResponse>> put(
            @Valid @RequestBody ThemeSettingsUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(200, themeService.put(request), "저장되었습니다."));
    }

    @GetMapping("/presets")
    public ResponseEntity<ApiResponse<List<ThemePresetResponse>>> listPresets() {
        return ResponseEntity.ok(ApiResponse.success(200, themeService.listPresets(), "조회 성공"));
    }

    @PostMapping("/presets")
    public ResponseEntity<ApiResponse<ThemePresetResponse>> createPreset(
            @Valid @RequestBody ThemePresetCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, themeService.createPreset(request), "프리셋이 저장되었습니다."));
    }

    @DeleteMapping("/presets/{name}")
    public ResponseEntity<ApiResponse<Void>> deletePreset(@PathVariable String name) {
        themeService.deletePreset(name);
        return ResponseEntity.ok(ApiResponse.<Void>success(200, null, "삭제되었습니다."));
    }
}
