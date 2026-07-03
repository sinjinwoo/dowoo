package io.dedyn.jwlabs.dowoo.library.repository;

import io.dedyn.jwlabs.dowoo.library.entity.NovelPrompt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NovelPromptRepository extends JpaRepository<NovelPrompt, UUID> {

    Optional<NovelPrompt> findByNovelId(UUID novelId);
}
