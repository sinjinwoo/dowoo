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

    // 같은 소설의 다른 회차 URL로 처음 진입해도(예: 1화만 읽다가 300화 주소로 바로 진입) 서재에서
    // 하나로 묶기 위한 사이트별 책 식별자. 파서가 회차 URL 구조에서 뽑아 채워준다(사이트별 정확한
    // 추출 방식은 각 파서가 안다 - ReadService/Novel은 사이트가 늘어나도 이 필드 존재만 알면 된다).
    // 텍스트 붙여넣기로 만든 소설은 대조할 URL이 없어 null로 남는다.
    @Column(name = "source_book_id")
    private String sourceBookId;

    // 이 소설에 연결된 프롬프트(시스템 프롬프트+번역 메모). NULL이면 "사용자의 기본
    // 프롬프트를 쓴다"는 뜻으로 취급한다(TranslateService 참고) - 별도의 "기본값 사용" 플래그가
    // 필요 없고, 선택한 프롬프트가 삭제돼도(ON DELETE SET NULL) 자동으로 기본값으로 돌아온다.
    @Column(name = "prompt_id")
    private UUID promptId;

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
