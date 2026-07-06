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

    private static final int PASTED_CHAPTER_TITLE_MAX_LENGTH = 10;

    private final ChapterRepository chapterRepository;
    private final NovelRepository novelRepository;
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

        // 같은 책의 다른 회차를 이미 서재에 갖고 있다면(예: 1화만 읽다가 300화 주소로 바로 진입) 새
        // 소설을 만들지 않고 기존 소설에 챕터만 추가한다. 사이트별 책 ID 추출은 각 파서의 책임이라
        // 여기서는 크롤링 결과의 (siteName, sourceBookId)만 보면 된다 - 새 사이트가 추가돼도 이 로직은
        // 그대로다.
        Novel novel = StringUtils.hasText(crawled.sourceBookId())
                ? novelRepository.findByUserIdAndSiteNameAndSourceBookId(userId, crawled.siteName(), crawled.sourceBookId())
                        .orElse(null)
                : null;

        if (novel == null) {
            novel = new Novel();
            novel.setUser(userRef);
            novel.setTitle(resolveNovelTitle(crawled, sourceUrl));
            novel.setSourceUrl(sourceUrl);
            novel.setSiteName(crawled.siteName());
            novel.setSourceBookId(crawled.sourceBookId());
            novel.setOrderIndex((int) novelRepository.countByUserId(userId));
            novel.setCreatedAt(now);
            novel.setUpdatedAt(now);
            // promptId는 비워둔다(=사용자의 기본 프롬프트를 쓴다) - NovelService.selectPrompt로
            // 나중에 특정 프롬프트를 골라 연결할 수 있다.
            novel = novelRepository.save(novel);
        }

        Chapter chapter = new Chapter();
        chapter.setNovel(novel);
        chapter.setSourceUrl(sourceUrl);
        chapter.setTitle(crawled.title());
        chapter.setOriginalText(crawled.content());
        chapter.setTranslatedText("");
        chapter.setPrevUrl(crawled.prevUrl());
        chapter.setNextUrl(crawled.nextUrl());
        chapter.setChapterIndex((int) chapterRepository.countByNovelId(novel.getId()));
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

        Chapter chapter = new Chapter();
        chapter.setNovel(novel);
        chapter.setSourceUrl(pseudoUrl);
        chapter.setTitle(derivePastedTitle(pastedText));
        chapter.setOriginalText(pastedText);
        chapter.setTranslatedText("");
        chapter.setChapterIndex(0);
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);
        chapter = chapterRepository.save(chapter);

        return new ReadResponse(novel.getId(), chapter.getId(), "");
    }

    /** 크롤러가 책 제목(bookTitle)을 못 찾는 사이트도 있어(파서 한계), 챕터 제목 → sourceUrl 순으로 폴백한다. */
    private String resolveNovelTitle(CrawlResult crawled, String sourceUrl) {
        if (StringUtils.hasText(crawled.bookTitle())) {
            return crawled.bookTitle();
        }
        if (StringUtils.hasText(crawled.title())) {
            return crawled.title();
        }
        return sourceUrl;
    }

    /** 붙여넣기는 크롤링과 달리 제목을 알 수 없으니, 본문 앞부분을 잘라 챕터 제목으로 쓴다(전체 본문을 제목에 저장하지 않기 위함). */
    private String derivePastedTitle(String pastedText) {
        String normalized = pastedText.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "새 도서";
        }
        return normalized.length() > PASTED_CHAPTER_TITLE_MAX_LENGTH
                ? normalized.substring(0, PASTED_CHAPTER_TITLE_MAX_LENGTH) + "..."
                : normalized;
    }
}
