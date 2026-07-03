package io.dedyn.jwlabs.dowoo.settings.repository;

import io.dedyn.jwlabs.dowoo.settings.entity.ThemePreset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThemePresetRepository extends JpaRepository<ThemePreset, UUID> {

    List<ThemePreset> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<ThemePreset> findByUserIdAndName(UUID userId, String name);

    void deleteByUserIdAndName(UUID userId, String name);
}
