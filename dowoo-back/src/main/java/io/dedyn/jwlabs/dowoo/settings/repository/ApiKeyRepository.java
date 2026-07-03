package io.dedyn.jwlabs.dowoo.settings.repository;

import io.dedyn.jwlabs.dowoo.settings.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByUserIdOrderByKeyOrderAsc(UUID userId);

    void deleteByUserId(UUID userId);

    void deleteByIdAndUserId(UUID id, UUID userId);
}
