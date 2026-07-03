package io.dedyn.jwlabs.dowoo.book.repository;

import io.dedyn.jwlabs.dowoo.book.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findByNovelIdOrderByChapterIndexAsc(UUID novelId);

    Optional<Chapter> findByNovelIdAndSourceUrl(UUID novelId, String sourceUrl);

    Optional<Chapter> findByIdAndNovelId(UUID id, UUID novelId);

    Optional<Chapter> findBySourceUrlAndNovel_UserId(String sourceUrl, UUID userId);

    Optional<Chapter> findByNovelIdAndChapterIndex(UUID novelId, Integer chapterIndex);

    long countByNovelId(UUID novelId);
}
