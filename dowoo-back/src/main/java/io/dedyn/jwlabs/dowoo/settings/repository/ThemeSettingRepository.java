package io.dedyn.jwlabs.dowoo.settings.repository;

import io.dedyn.jwlabs.dowoo.settings.entity.ThemeSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ThemeSettingRepository extends JpaRepository<ThemeSetting, UUID> {

    Optional<ThemeSetting> findByUserId(UUID userId);
}
