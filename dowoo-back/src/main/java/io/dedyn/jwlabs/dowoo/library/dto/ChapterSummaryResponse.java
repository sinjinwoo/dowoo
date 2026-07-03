package io.dedyn.jwlabs.dowoo.library.dto;

import java.util.UUID;

/** Novel 상세 조회에 끼워 넣는 챕터 메타(본문 제외) 전용 투영. book.dto.ChapterResponse(전체)와는 별개. */
public record ChapterSummaryResponse(
        UUID id,
        String title,
        String sourceUrl,
        Integer chapterIndex
) {
}
