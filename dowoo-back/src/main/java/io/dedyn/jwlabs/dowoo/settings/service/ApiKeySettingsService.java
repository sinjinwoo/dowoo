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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ApiKeySettingsService {

    private static final Pattern ASCII_PRINTABLE = Pattern.compile("^[\\x21-\\x7E]+$");

    private final ApiKeySettingRepository apiKeySettingRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyCipher apiKeyCipher;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ApiSettingsResponse get() {
        UUID userId = currentUserProvider.currentUserId();
        ApiKeySetting setting = apiKeySettingRepository.findByUserId(userId).orElse(null);
        List<ApiSettingsResponse.ApiKeyItem> items = apiKeyRepository.findByUserIdOrderByKeyOrderAsc(userId).stream()
                .map(k -> new ApiSettingsResponse.ApiKeyItem(
                        k.getId(), mask(apiKeyCipher.decrypt(k.getEncryptedKey())), k.getKeyOrder()))
                .toList();
        return new ApiSettingsResponse(
                setting != null ? setting.getModel() : null,
                setting != null ? setting.getThinkingBudget() : null,
                items);
    }

    @Transactional
    public ApiSettingsResponse replace(ApiSettingsUpdateRequest request) {
        if (request.apiKeys() != null) {
            for (String key : request.apiKeys()) {
                if (!ASCII_PRINTABLE.matcher(key).matches()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_API_KEY_FORMAT",
                            "API 키에 사용할 수 없는 문자가 포함되어 있습니다.");
                }
            }
        }

        UUID userId = currentUserProvider.currentUserId();
        User userRef = userRepository.getReferenceById(userId);

        ApiKeySetting setting = apiKeySettingRepository.findByUserId(userId).orElseGet(() -> {
            ApiKeySetting s = new ApiKeySetting();
            s.setUser(userRef);
            return s;
        });
        setting.setModel(request.model());
        setting.setThinkingBudget(request.thinkingBudget());
        setting.setUpdatedAt(OffsetDateTime.now());
        apiKeySettingRepository.save(setting);

        if (request.apiKeys() != null) {
            apiKeyRepository.deleteByUserId(userId);
            apiKeyRepository.flush();

            List<ApiKey> newKeys = new ArrayList<>();
            for (int i = 0; i < request.apiKeys().size(); i++) {
                ApiKey key = new ApiKey();
                key.setUser(userRef);
                key.setEncryptedKey(apiKeyCipher.encrypt(request.apiKeys().get(i)));
                key.setKeyOrder(i);
                key.setCreatedAt(OffsetDateTime.now());
                newKeys.add(key);
            }
            apiKeyRepository.saveAll(newKeys);
        }

        return get();
    }

    @Transactional
    public void deleteKey(UUID keyId) {
        apiKeyRepository.deleteByIdAndUserId(keyId, currentUserProvider.currentUserId());
    }

    /** 기존 키는 마스킹된 값만 조회 가능해 PUT(전체 교체)으로는 재입력 없이 추가할 수 없다 - 한 개만 이어붙이는 용도. */
    @Transactional
    public ApiSettingsResponse appendKey(String rawKey) {
        if (!ASCII_PRINTABLE.matcher(rawKey).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_API_KEY_FORMAT",
                    "API 키에 사용할 수 없는 문자가 포함되어 있습니다.");
        }
        UUID userId = currentUserProvider.currentUserId();
        int nextOrder = apiKeyRepository.findByUserIdOrderByKeyOrderAsc(userId).size();

        ApiKey key = new ApiKey();
        key.setUser(userRepository.getReferenceById(userId));
        key.setEncryptedKey(apiKeyCipher.encrypt(rawKey));
        key.setKeyOrder(nextOrder);
        key.setCreatedAt(OffsetDateTime.now());
        apiKeyRepository.save(key);

        return get();
    }

    /** 한 번에 여러 줄 붙여넣기로 입력된 키를 순서 그대로(로테이션 순번) 이어붙인다. */
    @Transactional
    public ApiSettingsResponse appendKeys(List<String> rawKeys) {
        for (String key : rawKeys) {
            if (!ASCII_PRINTABLE.matcher(key).matches()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_API_KEY_FORMAT",
                        "API 키에 사용할 수 없는 문자가 포함되어 있습니다.");
            }
        }

        UUID userId = currentUserProvider.currentUserId();
        User userRef = userRepository.getReferenceById(userId);
        int nextOrder = apiKeyRepository.findByUserIdOrderByKeyOrderAsc(userId).size();
        OffsetDateTime now = OffsetDateTime.now();

        List<ApiKey> newKeys = new ArrayList<>();
        for (int i = 0; i < rawKeys.size(); i++) {
            ApiKey key = new ApiKey();
            key.setUser(userRef);
            key.setEncryptedKey(apiKeyCipher.encrypt(rawKeys.get(i)));
            key.setKeyOrder(nextOrder + i);
            key.setCreatedAt(now);
            newKeys.add(key);
        }
        apiKeyRepository.saveAll(newKeys);

        return get();
    }

    private String mask(String rawKey) {
        if (rawKey.length() <= 8) {
            return "*".repeat(rawKey.length());
        }
        return rawKey.substring(0, 4) + "*".repeat(rawKey.length() - 8) + rawKey.substring(rawKey.length() - 4);
    }
}
