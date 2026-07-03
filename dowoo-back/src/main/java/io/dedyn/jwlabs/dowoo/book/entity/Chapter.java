package io.dedyn.jwlabs.dowoo.book.entity;

import io.dedyn.jwlabs.dowoo.library.entity.Novel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "chapters")
@Getter
@Setter
@NoArgsConstructor
public class Chapter {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "novel_id", nullable = false)
    private Novel novel;

    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    private String title;

    @Column(name = "original_text", columnDefinition = "text")
    private String originalText;

    @Column(name = "translated_text", columnDefinition = "text")
    private String translatedText;

    @Column(name = "prev_url")
    private String prevUrl;

    @Column(name = "next_url")
    private String nextUrl;

    @Column(name = "chapter_index", nullable = false)
    private Integer chapterIndex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
