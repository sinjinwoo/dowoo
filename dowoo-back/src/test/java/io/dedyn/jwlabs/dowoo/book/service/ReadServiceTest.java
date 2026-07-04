package io.dedyn.jwlabs.dowoo.book.service;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.book.crawl.CrawlClient;
import io.dedyn.jwlabs.dowoo.book.crawl.CrawlResult;
import io.dedyn.jwlabs.dowoo.book.dto.ReadRequest;
import io.dedyn.jwlabs.dowoo.book.dto.ReadResponse;
import io.dedyn.jwlabs.dowoo.book.entity.Chapter;
import io.dedyn.jwlabs.dowoo.book.repository.ChapterRepository;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.library.entity.Novel;
import io.dedyn.jwlabs.dowoo.library.repository.NovelPromptRepository;
import io.dedyn.jwlabs.dowoo.library.repository.NovelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class ReadServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ChapterRepository chapterRepository;
    @Mock
    private NovelRepository novelRepository;
    @Mock
    private NovelPromptRepository novelPromptRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CrawlClient crawlClient;

    private ReadService readService;

    @BeforeEach
    void setUp() {
        readService = new ReadService(
                chapterRepository, novelRepository, novelPromptRepository, currentUserProvider, userRepository, crawlClient);
    }

    @Test
    void read_neitherSourceUrlNorPastedText_throwsValidationError() {
        ApiException ex = assertThrows(ApiException.class,
                () -> readService.read(new ReadRequest(null, null, false)));

        assertEquals("VALIDATION_ERROR", ex.getErrorCode());
    }

    @Test
    void read_bothSourceUrlAndPastedText_throwsValidationError() {
        ApiException ex = assertThrows(ApiException.class,
                () -> readService.read(new ReadRequest("https://ixdzs8.com/read/1/p1.html", "붙여넣은 텍스트", false)));

        assertEquals("VALIDATION_ERROR", ex.getErrorCode());
    }

    @Test
    void read_cachedChapterExists_returnsWithoutCallingCrawler() {
        String sourceUrl = "https://ixdzs8.com/read/1/p1.html";
        Novel novel = novelWithId(UUID.randomUUID());
        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setNovel(novel);
        chapter.setTranslatedText("이미 번역됨");

        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(chapterRepository.findBySourceUrlAndNovel_UserId(sourceUrl, USER_ID)).thenReturn(Optional.of(chapter));

        ReadResponse response = readService.read(new ReadRequest(sourceUrl, null, false));

        assertEquals(novel.getId(), response.novelId());
        assertEquals(chapter.getId(), response.chapterId());
        assertEquals("이미 번역됨", response.translatedText());
        verify(crawlClient, never()).crawl(any());
    }

    @Test
    void read_newSourceUrl_crawlsAndCreatesNovelAndChapter() {
        String sourceUrl = "https://ixdzs8.com/read/1/p1.html";
        CrawlResult crawled = new CrawlResult("1화", "이세계 이야기", "원문 본문", null, "https://ixdzs8.com/read/1/p2.html", "ixdzs8.com");

        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(chapterRepository.findBySourceUrlAndNovel_UserId(sourceUrl, USER_ID)).thenReturn(Optional.empty());
        when(crawlClient.crawl(sourceUrl)).thenReturn(crawled);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(novelRepository.countByUserId(USER_ID)).thenReturn(0L);
        when(novelRepository.save(any(Novel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> inv.getArgument(0));

        ReadResponse response = readService.read(new ReadRequest(sourceUrl, null, false));

        assertEquals("", response.translatedText());
        verify(novelRepository).save(any(Novel.class));
        verify(chapterRepository).save(any(Chapter.class));
    }

    @Test
    void read_forceRecrawlExistingChapter_updatesChapterButDoesNotCreateNewNovel() {
        String sourceUrl = "https://ixdzs8.com/read/1/p1.html";
        Novel novel = novelWithId(UUID.randomUUID());
        Chapter chapter = new Chapter();
        chapter.setId(UUID.randomUUID());
        chapter.setNovel(novel);
        chapter.setOriginalText("옛날 원문");
        chapter.setTranslatedText("옛날 번역");
        CrawlResult recrawled = new CrawlResult("1화", "이세계 이야기", "새 원문", null, null, "ixdzs8.com");

        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(chapterRepository.findBySourceUrlAndNovel_UserId(sourceUrl, USER_ID)).thenReturn(Optional.of(chapter));
        when(crawlClient.crawl(sourceUrl)).thenReturn(recrawled);

        ReadResponse response = readService.read(new ReadRequest(sourceUrl, null, true));

        assertEquals("새 원문", chapter.getOriginalText());
        assertEquals("옛날 번역", chapter.getTranslatedText()); // 다시 번역은 프론트가 7.1 호출로 덮어씀, 여기선 유지
        assertEquals("옛날 번역", response.translatedText());
        verify(novelRepository, never()).save(any(Novel.class));
    }

    @Test
    void read_pastedText_alwaysCreatesNewNovelTitledSaeDoseo() {
        when(currentUserProvider.currentUserId()).thenReturn(USER_ID);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(mock(User.class));
        when(novelRepository.countByUserId(USER_ID)).thenReturn(1L);
        when(novelRepository.save(any(Novel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> inv.getArgument(0));

        readService.read(new ReadRequest(null, "붙여넣은 원문", false));

        verify(crawlClient, never()).crawl(any());
        verify(novelRepository).save(any(Novel.class));
    }

    private Novel novelWithId(UUID id) {
        Novel novel = new Novel();
        novel.setId(id);
        return novel;
    }
}
