package io.dedyn.jwlabs.dowoo.settings.repository;

import io.dedyn.jwlabs.dowoo.settings.entity.ApiKeySetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeySettingRepository extends JpaRepository<ApiKeySetting, UUID> {

    Optional<ApiKeySetting> findByUserId(UUID userId);
}
