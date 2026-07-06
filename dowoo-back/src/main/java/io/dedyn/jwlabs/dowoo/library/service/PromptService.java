package io.dedyn.jwlabs.dowoo.library.service;

import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.library.dto.PromptCreateRequest;
import io.dedyn.jwlabs.dowoo.library.dto.PromptPatchRequest;
import io.dedyn.jwlabs.dowoo.library.dto.PromptResponse;
import io.dedyn.jwlabs.dowoo.library.entity.Prompt;
import io.dedyn.jwlabs.dowoo.library.repository.PromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** "내 서재"와 분리된 독립 리소스로서의 프롬프트(시스템 프롬프트+번역 메모) CRUD. */
@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptRepository promptRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<PromptResponse> list() {
        UUID userId = currentUserProvider.currentUserId();
        return promptRepository.findByUserIdOrderByCreatedAtAsc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PromptResponse create(PromptCreateRequest request) {
        UUID userId = currentUserProvider.currentUserId();
        if (promptRepository.existsByUserIdAndTitle(userId, request.title())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_PROMPT_TITLE", "같은 제목의 프롬프트가 이미 있습니다.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Prompt prompt = new Prompt();
        prompt.setUser(userRepository.getReferenceById(userId));
        prompt.setTitle(request.title());
        prompt.setSystemPrompt(request.systemPrompt());
        prompt.setTranslationNote(request.translationNote());
        prompt.setDefaultPrompt(false);
        prompt.setCreatedAt(now);
        prompt.setUpdatedAt(now);
        return toResponse(promptRepository.save(prompt));
    }

    @Transactional
    public PromptResponse update(UUID promptId, PromptPatchRequest request) {
        Prompt prompt = getOwnedPrompt(promptId);

        // 기본 프롬프트는 제목을 바꿀 수 없다 - 요청에 title이 와도 조용히 무시한다(에러로
        // 막으면 프론트에서 매번 "title은 보내지 말아야 하나"를 신경 써야 해서, 그냥 무해한
        // 필드로 취급하는 편이 API 사용성이 낫다).
        if (request.title() != null && !prompt.isDefaultPrompt()) {
            if (!request.title().equals(prompt.getTitle())
                    && promptRepository.existsByUserIdAndTitleAndIdNot(prompt.getUser().getId(), request.title(), promptId)) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_PROMPT_TITLE", "같은 제목의 프롬프트가 이미 있습니다.");
            }
            prompt.setTitle(request.title());
        }
        if (request.systemPrompt() != null) prompt.setSystemPrompt(request.systemPrompt());
        if (request.translationNote() != null) prompt.setTranslationNote(request.translationNote());
        prompt.setUpdatedAt(OffsetDateTime.now());
        return toResponse(prompt);
    }

    @Transactional
    public void delete(UUID promptId) {
        Prompt prompt = getOwnedPrompt(promptId);
        if (prompt.isDefaultPrompt()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DELETE_DEFAULT_PROMPT", "기본 프롬프트는 삭제할 수 없습니다.");
        }
        promptRepository.delete(prompt);
    }

    private Prompt getOwnedPrompt(UUID promptId) {
        UUID userId = currentUserProvider.currentUserId();
        return promptRepository.findByIdAndUserId(promptId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "프롬프트를 찾을 수 없습니다."));
    }

    private PromptResponse toResponse(Prompt prompt) {
        return new PromptResponse(
                prompt.getId(), prompt.getTitle(), prompt.getSystemPrompt(), prompt.getTranslationNote(),
                prompt.isDefaultPrompt(), prompt.getCreatedAt(), prompt.getUpdatedAt());
    }
}
