package io.dedyn.jwlabs.dowoo.library.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "novel_prompts")
@Getter
@Setter
@NoArgsConstructor
public class NovelPrompt {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "novel_id", nullable = false, unique = true)
    private Novel novel;

    @Column(name = "system_prompt", columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "translation_note", columnDefinition = "text")
    private String translationNote;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
