package io.dedyn.jwlabs.dowoo.library.entity;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "novels")
@Getter
@Setter
@NoArgsConstructor
public class Novel {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(name = "original_title")
    private String originalTitle;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    @Column(name = "site_name", nullable = false)
    private String siteName;

    @Column(name = "last_read_chapter_index")
    private Integer lastReadChapterIndex;

    @Column(name = "last_read_scroll_pos")
    private Double lastReadScrollPos;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
