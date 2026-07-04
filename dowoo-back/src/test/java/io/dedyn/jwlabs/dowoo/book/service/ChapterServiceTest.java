package io.dedyn.jwlabs.dowoo.book.service;

import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.book.dto.ChapterCreateRequest;
import io.dedyn.jwlabs.dowoo.book.entity.Chapter;
import io.dedyn.jwlabs.dowoo.book.repository.ChapterRepository;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.library.entity.Novel;
import io.dedyn.jwlabs.dowoo.library.repository.NovelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChapterServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ChapterRepository chapterRepository;
    @Mock
    private NovelRepository novelRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private ChapterService chapterService;

    @BeforeEach
    void setUp() {
        chapterService = new ChapterService(chapterRepository, novelRepository, currentUserProvider);
    }

    @Test
    void getChapter_novelNotOwned_throwsNotFound() {
        UUID novelId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(novelRepository.findByIdAndUserId(novelId, USER_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> chapterService.getChapter(novelId, chapterId));

        assertEquals("RESOURCE_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void createOrGetCached_sourceUrlAlreadyExists_returnsCachedWithoutCreating() {
        UUID novelId = UUID.randomUUID();
        Novel novel = novelWithId(novelId);
        Chapter existing = existingChapter(novel);
        ChapterCreateRequest request = new ChapterCreateRequest(
                existing.getSourceUrl(), "다른 제목", "다른 본문", null, null);

        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(novelRepository.findByIdAndUserId(novelId, USER_ID)).thenReturn(Optional.of(novel));
        when(chapterRepository.findByNovelIdAndSourceUrl(novelId, request.sourceUrl())).thenReturn(Optional.of(existing));

        ChapterService.ChapterCreateResult result = chapterService.createOrGetCached(novelId, request);

        assertFalse(result.created());
        assertEquals(existing.getTitle(), result.chapter().title());
        verify(chapterRepository, never()).save(any(Chapter.class));
    }

    @Test
    void createOrGetCached_newSourceUrl_assignsChapterIndexFromCurrentCount() {
        UUID novelId = UUID.randomUUID();
        Novel novel = novelWithId(novelId);
        ChapterCreateRequest request = new ChapterCreateRequest(
                "https://ixdzs8.com/read/1/p2.html", "2화", "본문", null, null);

        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(novelRepository.findByIdAndUserId(novelId, USER_ID)).thenReturn(Optional.of(novel));
        when(chapterRepository.findByNovelIdAndSourceUrl(novelId, request.sourceUrl())).thenReturn(Optional.empty());
        when(chapterRepository.countByNovelId(novelId)).thenReturn(3L);
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> inv.getArgument(0));

        ChapterService.ChapterCreateResult result = chapterService.createOrGetCached(novelId, request);

        assertTrue(result.created());
        assertEquals(3, result.chapter().chapterIndex());
        assertEquals("", result.chapter().translatedText());
    }

    private Novel novelWithId(UUID id) {
        Novel novel = new Novel();
        novel.setId(id);
        return novel;
    }

    private Chapter existingChapter(Novel novel) {
        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setNovel(novel);
        chapter.setSourceUrl("https://ixdzs8.com/read/1/p1.html");
        chapter.setTitle("1화");
        chapter.setOriginalText("원문");
        chapter.setTranslatedText("번역됨");
        chapter.setChapterIndex(0);
        return chapter;
    }
}
