package io.dedyn.jwlabs.dowoo.book.service;

import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.book.dto.ChapterCreateRequest;
import io.dedyn.jwlabs.dowoo.book.dto.ChapterPatchRequest;
import io.dedyn.jwlabs.dowoo.book.dto.ChapterResponse;
import io.dedyn.jwlabs.dowoo.book.entity.Chapter;
import io.dedyn.jwlabs.dowoo.book.repository.ChapterRepository;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.library.entity.Novel;
import io.dedyn.jwlabs.dowoo.library.repository.NovelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final NovelRepository novelRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public ChapterResponse getChapter(UUID novelId, UUID chapterId) {
        getOwnedNovel(novelId);
        Chapter chapter = chapterRepository.findByIdAndNovelId(chapterId, novelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "챕터를 찾을 수 없습니다."));
        return toResponse(chapter);
    }

    @Transactional
    public ChapterCreateResult createOrGetCached(UUID novelId, ChapterCreateRequest request) {
        Novel novel = getOwnedNovel(novelId);
        Optional<Chapter> existing = chapterRepository.findByNovelIdAndSourceUrl(novelId, request.sourceUrl());
        if (existing.isPresent()) {
            return new ChapterCreateResult(toResponse(existing.get()), false);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Chapter chapter = new Chapter();
        chapter.setNovel(novel);
        chapter.setSourceUrl(request.sourceUrl());
        chapter.setTitle(request.title());
        chapter.setOriginalText(request.originalText());
        chapter.setTranslatedText("");
        chapter.setPrevUrl(request.prevUrl());
        chapter.setNextUrl(request.nextUrl());
        chapter.setChapterIndex((int) chapterRepository.countByNovelId(novelId));
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);
        chapter = chapterRepository.save(chapter);
        return new ChapterCreateResult(toResponse(chapter), true);
    }

    @Transactional
    public ChapterResponse patchTranslation(UUID novelId, UUID chapterId, ChapterPatchRequest request) {
        getOwnedNovel(novelId);
        Chapter chapter = chapterRepository.findByIdAndNovelId(chapterId, novelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "챕터를 찾을 수 없습니다."));
        chapter.setTranslatedText(request.translatedText());
        chapter.setUpdatedAt(OffsetDateTime.now());
        return toResponse(chapter);
    }

    @Transactional
    public void delete(UUID novelId, UUID chapterId) {
        getOwnedNovel(novelId);
        Chapter chapter = chapterRepository.findByIdAndNovelId(chapterId, novelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "챕터를 찾을 수 없습니다."));
        chapterRepository.delete(chapter);
    }

    private Novel getOwnedNovel(UUID novelId) {
        UUID userId = currentUserProvider.currentUserId();
        return novelRepository.findByIdAndUserId(novelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "소설을 찾을 수 없습니다."));
    }

    private ChapterResponse toResponse(Chapter chapter) {
        return new ChapterResponse(
                chapter.getId(), chapter.getNovel().getId(), chapter.getTitle(), chapter.getSourceUrl(),
                chapter.getOriginalText(), chapter.getTranslatedText(), chapter.getPrevUrl(), chapter.getNextUrl(),
                chapter.getChapterIndex());
    }

    public record ChapterCreateResult(ChapterResponse chapter, boolean created) {
    }
}
