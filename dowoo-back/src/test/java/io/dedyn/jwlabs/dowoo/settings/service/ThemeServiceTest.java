package io.dedyn.jwlabs.dowoo.settings.service;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemePresetCreateRequest;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemeSettingsResponse;
import io.dedyn.jwlabs.dowoo.settings.dto.ThemeSettingsUpdateRequest;
import io.dedyn.jwlabs.dowoo.settings.entity.ThemePreset;
import io.dedyn.jwlabs.dowoo.settings.entity.ThemeSetting;
import io.dedyn.jwlabs.dowoo.settings.repository.ThemePresetRepository;
import io.dedyn.jwlabs.dowoo.settings.repository.ThemeSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThemeServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ThemeSettingRepository themeSettingRepository;
    @Mock
    private ThemePresetRepository themePresetRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserRepository userRepository;

    private ThemeService themeService;

    @BeforeEach
    void setUp() {
        themeService = new ThemeService(themeSettingRepository, themePresetRepository, currentUserProvider, userRepository);
    }

    @Test
    void get_noSavedSettings_returnsBuiltInDefault() {
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(themeSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        ThemeSettingsResponse response = themeService.get();

        assertEquals("#ffffff", response.bgColor());
        assertEquals("18", response.fontSize());
    }

    @Test
    void put_newUser_createsSettingLinkedToUser() {
        User userRef = mock(User.class);
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(userRef);
        when(themeSettingRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(themeSettingRepository.save(any(ThemeSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        ThemeSettingsResponse response = themeService.put(
                new ThemeSettingsUpdateRequest("Pretendard", "#000000", "#eef3ec", "20", "500", "1.8", "2"));

        assertEquals("#eef3ec", response.bgColor());
        assertEquals("20", response.fontSize());
    }

    @Test
    void createPreset_duplicateName_throwsConflict() {
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(themePresetRepository.findByUserIdAndName(USER_ID, "다크 모드")).thenReturn(Optional.of(mock(ThemePreset.class)));

        ThemePresetCreateRequest request = new ThemePresetCreateRequest("다크 모드", Map.of("bgColor", "#16171d"));

        ApiException ex = assertThrows(ApiException.class, () -> themeService.createPreset(request));

        assertEquals("DUPLICATE_PRESET_NAME", ex.getErrorCode());
    }

    @Test
    void createPreset_newName_savesAndReturnsPreset() {
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(themePresetRepository.findByUserIdAndName(USER_ID, "오렌지 페이퍼")).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));

        var theme = Map.<String, Object>of("bgColor", "#f6ead7");
        var response = themeService.createPreset(new ThemePresetCreateRequest("오렌지 페이퍼", theme));

        assertEquals("오렌지 페이퍼", response.name());
        assertEquals(theme, response.theme());
    }

    @Test
    void listPresets_returnsInCreatedOrder() {
        ThemePreset preset = mock(ThemePreset.class);
        when(preset.getName()).thenReturn("미디엄 그린");
        when(preset.getTheme()).thenReturn(Map.of("bgColor", "#cbdec8"));
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(themePresetRepository.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(preset));

        var presets = themeService.listPresets();

        assertEquals(1, presets.size());
        assertEquals("미디엄 그린", presets.get(0).name());
    }

    @Test
    void deletePreset_delegatesToRepositoryWithCurrentUser() {
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);

        themeService.deletePreset("삭제할 프리셋");

        verify(themePresetRepository).deleteByUserIdAndName(USER_ID, "삭제할 프리셋");
    }
}
