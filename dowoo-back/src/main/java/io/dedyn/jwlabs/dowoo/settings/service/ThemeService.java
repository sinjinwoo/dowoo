package io.dedyn.jwlabs.dowoo.settings.service;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemePresetCreateRequest;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemePresetResponse;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemeSettingsResponse;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemeSettingsUpdateRequest;
import io.dedyn.jwlabs.dowoo.settings.entity.ThemePreset;
import io.dedyn.jwlabs.dowoo.settings.entity.ThemeSetting;
import io.dedyn.jwlabs.dowoo.settings.repository.ThemePresetRepository;
import io.dedyn.jwlabs.dowoo.settings.repository.ThemeSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ThemeService {

    private static final ThemeSettingsResponse DEFAULT_THEME = new ThemeSettingsResponse(
            "\"Pretendard\", system-ui, sans-serif", "#08060d", "#ffffff", "18", "400", "1.7", "1");

    private final ThemeSettingRepository themeSettingRepository;
    private final ThemePresetRepository themePresetRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ThemeSettingsResponse get() {
        UUID userId = currentUserProvider.currentUserId();
        return themeSettingRepository.findByUserId(userId).map(this::toResponse).orElse(DEFAULT_THEME);
    }

    @Transactional
    public ThemeSettingsResponse put(ThemeSettingsUpdateRequest request) {
        UUID userId = currentUserProvider.currentUserId();
        User userRef = userRepository.getReferenceById(userId);
        ThemeSetting setting = themeSettingRepository.findByUserId(userId).orElseGet(() -> {
            ThemeSetting s = new ThemeSetting();
            s.setUser(userRef);
            return s;
        });
        setting.setFontFamily(request.fontFamily());
        setting.setFontColor(request.fontColor());
        setting.setBgColor(request.bgColor());
        setting.setFontSize(request.fontSize());
        setting.setFontWeight(request.fontWeight());
        setting.setLineHeight(request.lineHeight());
        setting.setTextIndent(request.textIndent());
        setting.setUpdatedAt(OffsetDateTime.now());
        themeSettingRepository.save(setting);
        return toResponse(setting);
    }

    @Transactional(readOnly = true)
    public List<ThemePresetResponse> listPresets() {
        UUID userId = currentUserProvider.currentUserId();
        return themePresetRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(p -> new ThemePresetResponse(p.getName(), p.getTheme()))
                .toList();
    }

    @Transactional
    public ThemePresetResponse createPreset(ThemePresetCreateRequest request) {
        UUID userId = currentUserProvider.currentUserId();
        if (themePresetRepository.findByUserIdAndName(userId, request.name()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_PRESET_NAME", "이미 같은 이름의 프리셋이 있습니다.");
        }
        ThemePreset preset = new ThemePreset();
        preset.setUser(userRepository.getReferenceById(userId));
        preset.setName(request.name());
        preset.setTheme(request.theme());
        preset.setCreatedAt(OffsetDateTime.now());
        themePresetRepository.save(preset);
        return new ThemePresetResponse(preset.getName(), preset.getTheme());
    }

    @Transactional
    public void deletePreset(String name) {
        themePresetRepository.deleteByUserIdAndName(currentUserProvider.currentUserId(), name);
    }

    private ThemeSettingsResponse toResponse(ThemeSetting s) {
        return new ThemeSettingsResponse(
                s.getFontFamily(), s.getFontColor(), s.getBgColor(),
                s.getFontSize(), s.getFontWeight(), s.getLineHeight(), s.getTextIndent());
    }
}
