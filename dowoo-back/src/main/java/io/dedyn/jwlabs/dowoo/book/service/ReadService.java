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
import io.dedyn.jwlabs.dowoo.common.util.UrlValidator;
import io.dedyn.jwlabs.dowoo.library.entity.Novel;
import io.dedyn.jwlabs.dowoo.library.entity.NovelPrompt;
import io.dedyn.jwlabs.dowoo.library.repository.NovelPromptRepository;
import io.dedyn.jwlabs.dowoo.library.repository.NovelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * §6.2 POST /api/v1/read 의 구현체.
 * 기본은 캐시 우선(있으면 크롤링/번역 생략), forceRecrawl일 때만 원문을 다시 가져온다.
 * 번역 자체(7.1 호출 여부)는 프론트가 응답의 translatedText를 보고 판단한다.
 */
@Service
@RequiredArgsConstructor
public class ReadService {

    private final ChapterRepository chapterRepository;
    private final NovelRepository novelRepository;
    private final NovelPromptRepository novelPromptRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final CrawlClient crawlClient;

    @Transactional
    public ReadResponse read(ReadRequest request) {
        boolean hasUrl = StringUtils.hasText(request.sourceUrl());
        boolean hasPastedText = StringUtils.hasText(request.pastedText());
        if (hasUrl == hasPastedText) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "sourceUrl과 pastedText 중 정확히 하나만 입력해야 합니다.");
        }

        UUID userId = currentUserProvider.currentUserId();
        return hasPastedText
                ? createFromPastedText(userId, request.pastedText())
                : readFromUrl(userId, request.sourceUrl(), request.isForceRecrawl());
    }

    private ReadResponse readFromUrl(UUID userId, String sourceUrl, boolean forceRecrawl) {
        UrlValidator.requireHttpUrl(sourceUrl);
        Optional<Chapter> existing = chapterRepository.findBySourceUrlAndNovel_UserId(sourceUrl, userId);

        if (existing.isPresent() && !forceRecrawl) {
            Chapter chapter = existing.get();
            return new ReadResponse(chapter.getNovel().getId(), chapter.getId(), chapter.getTranslatedText());
        }

        CrawlResult crawled = crawlClient.crawl(sourceUrl);

        if (existing.isPresent()) {
            Chapter chapter = existing.get();
            chapter.setOriginalText(crawled.content());
            chapter.setPrevUrl(crawled.prevUrl());
            chapter.setNextUrl(crawled.nextUrl());
            chapter.setUpdatedAt(OffsetDateTime.now());
            return new ReadResponse(chapter.getNovel().getId(), chapter.getId(), chapter.getTranslatedText());
        }

        OffsetDateTime now = OffsetDateTime.now();
        User userRef = userRepository.getReferenceById(userId);

        Novel novel = new Novel();
        novel.setUser(userRef);
        novel.setTitle(StringUtils.hasText(crawled.title()) ? crawled.title() : sourceUrl);
        novel.setSourceUrl(sourceUrl);
        novel.setSiteName(crawled.siteName());
        novel.setOrderIndex((int) novelRepository.countByUserId(userId));
        novel.setCreatedAt(now);
        novel.setUpdatedAt(now);
        novel = novelRepository.save(novel);

        NovelPrompt prompt = new NovelPrompt();
        prompt.setNovel(novel);
        prompt.setUpdatedAt(now);
        novelPromptRepository.save(prompt);

        Chapter chapter = new Chapter();
        chapter.setNovel(novel);
        chapter.setSourceUrl(sourceUrl);
        chapter.setTitle(crawled.title());
        chapter.setOriginalText(crawled.content());
        chapter.setTranslatedText("");
        chapter.setPrevUrl(crawled.prevUrl());
        chapter.setNextUrl(crawled.nextUrl());
        chapter.setChapterIndex(0);
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);
        chapter = chapterRepository.save(chapter);

        return new ReadResponse(novel.getId(), chapter.getId(), "");
    }

    private ReadResponse createFromPastedText(UUID userId, String pastedText) {
        OffsetDateTime now = OffsetDateTime.now();
        User userRef = userRepository.getReferenceById(userId);
        String pseudoUrl = "pasted:" + UUID.randomUUID();

        Novel novel = new Novel();
        novel.setUser(userRef);
        novel.setTitle("새 도서");
        novel.setSourceUrl(pseudoUrl);
        novel.setSiteName("pasted");
        novel.setOrderIndex((int) novelRepository.countByUserId(userId));
        novel.setCreatedAt(now);
        novel.setUpdatedAt(now);
        novel = novelRepository.save(novel);

        NovelPrompt prompt = new NovelPrompt();
        prompt.setNovel(novel);
        prompt.setUpdatedAt(now);
        novelPromptRepository.save(prompt);

        Chapter chapter = new Chapter();
        chapter.setNovel(novel);
        chapter.setSourceUrl(pseudoUrl);
        chapter.setTitle("새 도서");
        chapter.setOriginalText(pastedText);
        chapter.setTranslatedText("");
        chapter.setChapterIndex(0);
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);
        chapter = chapterRepository.save(chapter);

        return new ReadResponse(novel.getId(), chapter.getId(), "");
    }
}
