package io.dedyn.jwlabs.dowoo.library.repository;

import io.dedyn.jwlabs.dowoo.library.entity.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromptRepository extends JpaRepository<Prompt, UUID> {

    List<Prompt> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Prompt> findByIdAndUserId(UUID id, UUID userId);

    Optional<Prompt> findByUserIdAndDefaultPromptTrue(UUID userId);

    boolean existsByUserIdAndTitleAndIdNot(UUID userId, String title, UUID id);

    boolean existsByUserIdAndTitle(UUID userId, String title);
}
