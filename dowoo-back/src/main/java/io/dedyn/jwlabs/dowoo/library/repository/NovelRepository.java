package io.dedyn.jwlabs.dowoo.library.repository;

import io.dedyn.jwlabs.dowoo.library.entity.Novel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NovelRepository extends JpaRepository<Novel, UUID> {

    List<Novel> findByUserIdOrderByOrderIndexAsc(UUID userId);

    Optional<Novel> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndSourceUrl(UUID userId, String sourceUrl);

    long countByUserId(UUID userId);

    @Query("select n from Novel n where n.user.id = :userId and (" +
            ":keyword is null or :keyword = '' " +
            "or lower(n.title) like lower(concat('%', :keyword, '%')) " +
            "or lower(n.originalTitle) like lower(concat('%', :keyword, '%'))" +
            ") order by n.orderIndex asc")
    List<Novel> search(@Param("userId") UUID userId, @Param("keyword") String keyword);
}
