package io.dedyn.jwlabs.dowoo.settings.service;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.settings.crypto.ApiKeyCipher;
import io.dedyn.jwlabs.dowoo.settings.dto.ApiSettingsResponse;
import io.dedyn.jwlabs.dowoo.settings.dto.ApiSettingsUpdateRequest;
import io.dedyn.jwlabs.dowoo.settings.entity.ApiKey;
import io.dedyn.jwlabs.dowoo.settings.entity.ApiKeySetting;
import io.dedyn.jwlabs.dowoo.settings.repository.ApiKeyRepository;
import io.dedyn.jwlabs.dowoo.settings.repository.ApiKeySettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiKeySettingsServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ApiKeySettingRepository apiKeySettingRepository;
    @Mock
    private ApiKeyRepository apiKeyRepository;
    @Mock
    private ApiKeyCipher apiKeyCipher;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserRepository userRepository;

    private ApiKeySettingsService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeySettingsService(apiKeySettingRepository, apiKeyRepository, apiKeyCipher, currentUserProvider, userRepository);
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(apiKeyCipher.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(apiKeyCipher.decrypt(anyString())).thenReturn("AIzaSyDecryptedKeyValue");
    }

    @Test
    void appendKey_containsNonAsciiPrintableChar_throwsInvalidFormat() {
        ApiException ex = assertThrows(ApiException.class, () -> service.appendKey("key with space"));

        assertEquals("INVALID_API_KEY_FORMAT", ex.getErrorCode());
        verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    void appendKey_success_ordersAfterExistingKeys() {
        when(apiKeyRepository.findByUserIdOrderByKeyOrderAsc(USER_ID))
                .thenReturn(List.of(mock(ApiKey.class), mock(ApiKey.class)))
                .thenReturn(List.of());

        service.appendKey("AIzaSyNewKey");

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getKeyOrder());
    }

    @Test
    void appendKeys_bulk_assignsSequentialOrderStartingFromCurrentCount() {
        when(apiKeyRepository.findByUserIdOrderByKeyOrderAsc(USER_ID))
                .thenReturn(List.of(mock(ApiKey.class)))
                .thenReturn(List.of());

        service.appendKeys(List.of("AIzaSyKeyA", "AIzaSyKeyB", "AIzaSyKeyC"));

        ArgumentCaptor<List<ApiKey>> captor = ArgumentCaptor.forClass(List.class);
        verify(apiKeyRepository).saveAll(captor.capture());
        List<Integer> orders = captor.getValue().stream().map(ApiKey::getKeyOrder).toList();
        assertEquals(List.of(1, 2, 3), orders);
    }

    @Test
    void appendKeys_oneInvalidKeyInBatch_rejectsWholeBatch() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.appendKeys(List.of("AIzaSyValidKey", "invalid key with space")));

        assertEquals("INVALID_API_KEY_FORMAT", ex.getErrorCode());
        verify(apiKeyRepository, never()).saveAll(any());
    }

    @Test
    void replace_invalidKeyInList_throwsAndDoesNotTouchExistingKeys() {
        ApiSettingsUpdateRequest request = new ApiSettingsUpdateRequest(
                List.of("valid-key", "invalid key"), "gemini-2.5-flash", null);

        ApiException ex = assertThrows(ApiException.class, () -> service.replace(request));

        assertEquals("INVALID_API_KEY_FORMAT", ex.getErrorCode());
        verify(apiKeyRepository, never()).deleteByUserId(any());
    }

    @Test
    void replace_nullApiKeys_onlyUpdatesModelAndKeepsExistingKeys() {
        // replace()가 findByUserId -> save -> (get() 안에서) findByUserId를 다시 호출하는 구조라,
        // 단순히 findByUserId를 매번 empty로 고정하면 save한 내용이 반영 안 된 것처럼 보인다(직전 실패 원인).
        // save될 때 그 값을 기억했다가 이후 findByUserId 호출에서 그대로 돌려주는 fake로 대체한다.
        AtomicReference<ApiKeySetting> saved = new AtomicReference<>();
        when(apiKeySettingRepository.findByUserId(USER_ID)).thenAnswer(inv -> Optional.ofNullable(saved.get()));
        when(apiKeySettingRepository.save(any(ApiKeySetting.class))).thenAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(apiKeyRepository.findByUserIdOrderByKeyOrderAsc(USER_ID)).thenReturn(List.of());

        ApiSettingsResponse response = service.replace(new ApiSettingsUpdateRequest(null, "gemini-2.5-pro", 1024));

        assertEquals("gemini-2.5-pro", response.model());
        verify(apiKeyRepository, never()).deleteByUserId(any());
        verify(apiKeyRepository, never()).saveAll(any());
    }
}
