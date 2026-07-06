package io.dedyn.jwlabs.dowoo.library.service;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.book.entity.Chapter;
import io.dedyn.jwlabs.dowoo.book.repository.ChapterRepository;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.library.dto.NovelCreateRequest;
import io.dedyn.jwlabs.dowoo.library.dto.ReorderRequest;
import io.dedyn.jwlabs.dowoo.library.entity.Novel;
import io.dedyn.jwlabs.dowoo.library.repository.NovelRepository;
import io.dedyn.jwlabs.dowoo.library.repository.PromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
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
class NovelServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private NovelRepository novelRepository;
    @Mock
    private PromptRepository promptRepository;
    @Mock
    private ChapterRepository chapterRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserRepository userRepository;

    private NovelService novelService;

    @BeforeEach
    void setUp() {
        novelService = new NovelService(
                novelRepository, promptRepository, chapterRepository, currentUserProvider, userRepository);
    }

    @Test
    void create_duplicateSourceUrl_throwsConflict() {
        NovelCreateRequest request = new NovelCreateRequest(
                "https://ixdzs8.com/read/1/p1.html", "ixdzs8.com", null, null, null, null);
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(novelRepository.existsByUserIdAndSourceUrl(USER_ID, request.sourceUrl())).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> novelService.create(request));

        assertEquals("DUPLICATE_NOVEL", ex.getErrorCode());
        verify(novelRepository, never()).save(any(Novel.class));
    }

    @Test
    void create_blankTitle_fallsBackToSourceUrl() {
        NovelCreateRequest request = new NovelCreateRequest(
                "https://ixdzs8.com/read/1/p1.html", "ixdzs8.com", "  ", null, null, null);
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(novelRepository.existsByUserIdAndSourceUrl(USER_ID, request.sourceUrl())).thenReturn(false);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(novelRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(novelRepository.save(any(Novel.class))).thenAnswer(inv -> inv.getArgument(0));

        var detail = novelService.create(request);

        assertEquals(request.sourceUrl(), detail.title());
    }

    @Test
    void getDetail_notOwnedByCurrentUser_throwsNotFound() {
        UUID novelId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(novelRepository.findByIdAndUserId(novelId, USER_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> novelService.getDetail(novelId));

        assertEquals("RESOURCE_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void reorder_orderedIdsDontMatchOwnedNovels_throwsValidationError() {
        Novel owned = novelWithId(UUID.randomUUID());
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(novelRepository.findByUserIdOrderByOrderIndexAsc(USER_ID)).thenReturn(List.of(owned));

        ReorderRequest request = new ReorderRequest(List.of(UUID.randomUUID()));

        ApiException ex = assertThrows(ApiException.class, () -> novelService.reorder(request));

        assertEquals("VALIDATION_ERROR", ex.getErrorCode());
    }

    @Test
    void reorder_validIds_updatesOrderIndexToMatchRequestedOrder() {
        Novel first = novelWithId(UUID.randomUUID());
        Novel second = novelWithId(UUID.randomUUID());
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(novelRepository.findByUserIdOrderByOrderIndexAsc(USER_ID)).thenReturn(List.of(first, second));

        // 원래 순서(first, second)를 뒤집어서 요청
        novelService.reorder(new ReorderRequest(List.of(second.getId(), first.getId())));

        assertEquals(1, first.getOrderIndex());
        assertEquals(0, second.getOrderIndex());
    }

    @Test
    void export_noChapterHasTranslatedText_throwsBadRequest() {
        UUID novelId = UUID.randomUUID();
        Novel novel = novelWithId(novelId);
        Chapter untranslated = new Chapter();
        untranslated.setTranslatedText("");
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(novelRepository.findByIdAndUserId(novelId, USER_ID)).thenReturn(Optional.of(novel));
        when(chapterRepository.findByNovelIdOrderByChapterIndexAsc(novelId)).thenReturn(List.of(untranslated));

        ApiException ex = assertThrows(ApiException.class, () -> novelService.export(novelId, "translated"));

        assertEquals("NO_TRANSLATED_CHAPTERS", ex.getErrorCode());
    }

    @Test
    void export_bothLang_joinsTranslatedAndOriginalWithSeparator() {
        UUID novelId = UUID.randomUUID();
        Novel novel = novelWithId(novelId);
        Chapter chapter = new Chapter();
        chapter.setTitle("1화");
        chapter.setOriginalText("原文");
        chapter.setTranslatedText("번역문");
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(novelRepository.findByIdAndUserId(novelId, USER_ID)).thenReturn(Optional.of(novel));
        when(chapterRepository.findByNovelIdOrderByChapterIndexAsc(novelId)).thenReturn(List.of(chapter));

        NovelService.ExportResult result = novelService.export(novelId, "both");

        assertEquals(novel.getTitle(), result.title());
        assertEquals(true, result.content().contains("번역문"));
        assertEquals(true, result.content().contains("原文"));
    }

    private Novel novelWithId(UUID id) {
        Novel novel = new Novel();
        novel.setId(id);
        novel.setTitle("테스트 소설");
        novel.setCreatedAt(OffsetDateTime.now());
        novel.setUpdatedAt(OffsetDateTime.now());
        return novel;
    }
}
