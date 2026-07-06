package io.dedyn.jwlabs.dowoo.library.service;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.library.dto.PromptCreateRequest;
import io.dedyn.jwlabs.dowoo.library.dto.PromptPatchRequest;
import io.dedyn.jwlabs.dowoo.library.dto.PromptResponse;
import io.dedyn.jwlabs.dowoo.library.entity.Prompt;
import io.dedyn.jwlabs.dowoo.library.repository.PromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private PromptRepository promptRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private PromptService promptService;

    @BeforeEach
    void setUp() {
        promptService = new PromptService(promptRepository, userRepository, currentUserProvider);
    }

    @Test
    void create_duplicateTitle_throwsConflict() {
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(promptRepository.existsByUserIdAndTitle(USER_ID, "무협체")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> promptService.create(new PromptCreateRequest("무협체", "prompt", "note")));

        assertEquals("DUPLICATE_PROMPT_TITLE", ex.getErrorCode());
        verify(promptRepository, never()).save(any(Prompt.class));
    }

    @Test
    void create_uniqueTitle_savesAsNonDefault() {
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(promptRepository.existsByUserIdAndTitle(USER_ID, "무협체")).thenReturn(false);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(promptRepository.save(any(Prompt.class))).thenAnswer(inv -> inv.getArgument(0));

        PromptResponse response = promptService.create(new PromptCreateRequest("무협체", "prompt", "note"));

        assertEquals("무협체", response.title());
        assertEquals(false, response.isDefault());
    }

    @Test
    void update_defaultPrompt_ignoresTitleChangeButAppliesContent() {
        Prompt defaultPrompt = promptWithId(UUID.randomUUID(), "기본 프롬프트", true);
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(promptRepository.findByIdAndUserId(defaultPrompt.getId(), USER_ID)).thenReturn(Optional.of(defaultPrompt));

        PromptResponse response = promptService.update(defaultPrompt.getId(),
                new PromptPatchRequest("바꿔치기 시도", "새 프롬프트 내용", null));

        assertEquals("기본 프롬프트", response.title());
        assertEquals("새 프롬프트 내용", response.systemPrompt());
        verify(promptRepository, never()).existsByUserIdAndTitleAndIdNot(any(), any(), any());
    }

    @Test
    void update_nonDefaultPrompt_duplicateTitle_throwsConflict() {
        Prompt prompt = promptWithId(UUID.randomUUID(), "원제목", false);
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(promptRepository.findByIdAndUserId(prompt.getId(), USER_ID)).thenReturn(Optional.of(prompt));
        when(promptRepository.existsByUserIdAndTitleAndIdNot(USER_ID, "중복제목", prompt.getId())).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> promptService.update(prompt.getId(), new PromptPatchRequest("중복제목", null, null)));

        assertEquals("DUPLICATE_PROMPT_TITLE", ex.getErrorCode());
        assertEquals("원제목", prompt.getTitle());
    }

    @Test
    void update_nonDefaultPrompt_uniqueTitle_renames() {
        Prompt prompt = promptWithId(UUID.randomUUID(), "원제목", false);
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(promptRepository.findByIdAndUserId(prompt.getId(), USER_ID)).thenReturn(Optional.of(prompt));
        when(promptRepository.existsByUserIdAndTitleAndIdNot(USER_ID, "새제목", prompt.getId())).thenReturn(false);

        PromptResponse response = promptService.update(prompt.getId(), new PromptPatchRequest("새제목", null, null));

        assertEquals("새제목", response.title());
    }

    @Test
    void delete_defaultPrompt_throwsBadRequest() {
        Prompt defaultPrompt = promptWithId(UUID.randomUUID(), "기본 프롬프트", true);
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(promptRepository.findByIdAndUserId(defaultPrompt.getId(), USER_ID)).thenReturn(Optional.of(defaultPrompt));

        ApiException ex = assertThrows(ApiException.class, () -> promptService.delete(defaultPrompt.getId()));

        assertEquals("CANNOT_DELETE_DEFAULT_PROMPT", ex.getErrorCode());
        verify(promptRepository, never()).delete(any(Prompt.class));
    }

    @Test
    void delete_nonDefaultPrompt_deletesIt() {
        Prompt prompt = promptWithId(UUID.randomUUID(), "무협체", false);
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(promptRepository.findByIdAndUserId(prompt.getId(), USER_ID)).thenReturn(Optional.of(prompt));

        promptService.delete(prompt.getId());

        verify(promptRepository).delete(prompt);
    }

    @Test
    void update_notOwnedByCurrentUser_throwsNotFound() {
        UUID promptId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(promptRepository.findByIdAndUserId(promptId, USER_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> promptService.update(promptId, new PromptPatchRequest(null, null, null)));

        assertEquals("RESOURCE_NOT_FOUND", ex.getErrorCode());
    }

    private Prompt promptWithId(UUID id, String title, boolean isDefault) {
        User user = new User();
        user.setId(USER_ID);

        Prompt prompt = new Prompt();
        prompt.setId(id);
        prompt.setUser(user);
        prompt.setTitle(title);
        prompt.setSystemPrompt("기존 프롬프트 내용");
        prompt.setDefaultPrompt(isDefault);
        prompt.setCreatedAt(OffsetDateTime.now());
        prompt.setUpdatedAt(OffsetDateTime.now());
        return prompt;
    }
}
