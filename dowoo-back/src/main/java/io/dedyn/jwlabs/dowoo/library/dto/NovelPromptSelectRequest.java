package io.dedyn.jwlabs.dowoo.library.dto;

import java.util.UUID;

/** promptId=null은 "기본 프롬프트를 쓴다"는 뜻으로, 이 요청의 유효한 값이다(부분 수정이 아니라 항상 전체 반영). */
public record NovelPromptSelectRequest(UUID promptId) {
}
