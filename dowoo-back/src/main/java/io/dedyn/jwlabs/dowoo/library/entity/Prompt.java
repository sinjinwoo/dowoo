package io.dedyn.jwlabs.dowoo.library.entity;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 시스템 프롬프트+번역 메모(용어집)를 소설(Novel)과 분리해 독립적으로 관리하는 리소스.
 * 사용자당 {@code isDefault=true}인 행이 정확히 하나 있고(가입 시 시드, DB에도 부분 유니크
 * 인덱스로 보장), 그 프롬프트는 제목을 바꿀 수 없다(내용은 자유롭게 수정 가능) - 서비스
 * 계층에서 강제한다.
 */
@Entity
@Table(name = "prompts")
@Getter
@Setter
@NoArgsConstructor
public class Prompt {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(name = "system_prompt", columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "translation_note", columnDefinition = "text")
    private String translationNote;

    // 필드명을 isDefault로 하면 Lombok이 getter/setter를 isDefault()/setDefault(boolean)로
    // 생성해(필드명이 이미 "is"로 시작하면 접두사를 다시 붙이지 않음) 헷갈리기 쉬워, DB
    // 컬럼명(is_default)과 다르게 defaultPrompt로 지어 isDefaultPrompt()/setDefaultPrompt(boolean)가
    // 나오도록 했다.
    @Column(name = "is_default", nullable = false)
    private boolean defaultPrompt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
