package io.dedyn.jwlabs.dowoo.library.service;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import io.dedyn.jwlabs.dowoo.auth.repository.UserRepository;
import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.book.entity.Chapter;
import io.dedyn.jwlabs.dowoo.book.repository.ChapterRepository;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.common.util.UrlValidator;
import io.dedyn.jwlabs.dowoo.library.dto.ChapterSummaryResponse;
import io.dedyn.jwlabs.dowoo.library.dto.LastReadRequest;
import io.dedyn.jwlabs.dowoo.library.dto.NovelCreateRequest;
import io.dedyn.jwlabs.dowoo.library.dto.NovelDetailResponse;
import io.dedyn.jwlabs.dowoo.library.dto.NovelPatchRequest;
import io.dedyn.jwlabs.dowoo.library.dto.NovelSummaryResponse;
import io.dedyn.jwlabs.dowoo.library.dto.ReorderRequest;
import io.dedyn.jwlabs.dowoo.library.entity.Novel;
import io.dedyn.jwlabs.dowoo.library.entity.NovelPrompt;
import io.dedyn.jwlabs.dowoo.library.repository.NovelPromptRepository;
import io.dedyn.jwlabs.dowoo.library.repository.NovelRepository;
import io.dedyn.jwlabs.dowoo.library.support.DefaultPrompts;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NovelService {

    private final NovelRepository novelRepository;
    private final NovelPromptRepository novelPromptRepository;
    private final ChapterRepository chapterRepository;
    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<NovelSummaryResponse> list(String keyword) {
        UUID userId = currentUserProvider.currentUserId();
        return novelRepository.search(userId, keyword).stream().map(this::toSummary).toList();
    }

    @Transactional
    public NovelDetailResponse create(NovelCreateRequest request) {
        UUID userId = currentUserProvider.currentUserId();
        UrlValidator.requireHttpUrl(request.sourceUrl());
        if (novelRepository.existsByUserIdAndSourceUrl(userId, request.sourceUrl())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_NOVEL", "이미 서재에 등록된 소설입니다.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Novel novel = new Novel();
        novel.setUser(userRepository.getReferenceById(userId));
        novel.setTitle(StringUtils.hasText(request.title()) ? request.title() : request.sourceUrl());
        novel.setOriginalTitle(request.originalTitle());
        novel.setCoverUrl(request.coverUrl());
        novel.setSourceUrl(request.sourceUrl());
        novel.setSiteName(request.siteName());
        novel.setOrderIndex((int) novelRepository.countByUserId(userId));
        novel.setCreatedAt(now);
        novel.setUpdatedAt(now);
        novel = novelRepository.save(novel);

        NovelPrompt prompt = new NovelPrompt();
        prompt.setNovel(novel);
        prompt.setSystemPrompt(StringUtils.hasText(request.systemPrompt())
                ? request.systemPrompt() : DefaultPrompts.SYSTEM_PROMPT);
        prompt.setTranslationNote(request.translationNote());
        prompt.setUpdatedAt(now);
        novelPromptRepository.save(prompt);

        return toDetail(novel, prompt, List.of());
    }

    @Transactional(readOnly = true)
    public NovelDetailResponse getDetail(UUID novelId) {
        Novel novel = getOwnedNovel(novelId);
        NovelPrompt prompt = novelPromptRepository.findByNovelId(novel.getId()).orElse(null);
        List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterIndexAsc(novel.getId());
        return toDetail(novel, prompt, chapters);
    }

    @Transactional
    public NovelDetailResponse patch(UUID novelId, NovelPatchRequest request) {
        Novel novel = getOwnedNovel(novelId);
        if (request.title() != null) novel.setTitle(request.title());
        if (request.originalTitle() != null) novel.setOriginalTitle(request.originalTitle());
        if (request.coverUrl() != null) novel.setCoverUrl(request.coverUrl());
        novel.setUpdatedAt(OffsetDateTime.now());

        NovelPrompt prompt = novelPromptRepository.findByNovelId(novel.getId()).orElseGet(() -> {
            NovelPrompt p = new NovelPrompt();
            p.setNovel(novel);
            return p;
        });
        if (request.systemPrompt() != null) prompt.setSystemPrompt(request.systemPrompt());
        if (request.translationNote() != null) prompt.setTranslationNote(request.translationNote());
        prompt.setUpdatedAt(OffsetDateTime.now());
        novelPromptRepository.save(prompt);

        List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterIndexAsc(novel.getId());
        return toDetail(novel, prompt, chapters);
    }

    @Transactional
    public void delete(UUID novelId) {
        novelRepository.delete(getOwnedNovel(novelId));
    }

    @Transactional
    public void reorder(ReorderRequest request) {
        UUID userId = currentUserProvider.currentUserId();
        List<Novel> owned = novelRepository.findByUserIdOrderByOrderIndexAsc(userId);
        Set<UUID> ownedIds = owned.stream().map(Novel::getId).collect(Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(request.orderedIds());
        if (!ownedIds.equals(requestedIds) || ownedIds.size() != request.orderedIds().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "orderedIds가 본인 소유 소설 목록과 일치하지 않습니다.");
        }
        Map<UUID, Novel> byId = owned.stream().collect(Collectors.toMap(Novel::getId, n -> n));
        for (int i = 0; i < request.orderedIds().size(); i++) {
            byId.get(request.orderedIds().get(i)).setOrderIndex(i);
        }
    }

    @Transactional
    public void updateLastRead(UUID novelId, LastReadRequest request) {
        Novel novel = getOwnedNovel(novelId);
        novel.setLastReadChapterIndex(request.lastReadChapterIndex());
        if (request.lastReadScrollPos() != null) {
            novel.setLastReadScrollPos(request.lastReadScrollPos());
        }
        novel.setUpdatedAt(OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public ExportResult export(UUID novelId, String lang) {
        Novel novel = getOwnedNovel(novelId);
        List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterIndexAsc(novel.getId());
        boolean hasTranslated = chapters.stream().anyMatch(c -> StringUtils.hasText(c.getTranslatedText()));
        if (!hasTranslated) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_TRANSLATED_CHAPTERS", "번역된 챕터가 하나도 없습니다.");
        }

        String effectiveLang = StringUtils.hasText(lang) ? lang : "translated";
        StringBuilder sb = new StringBuilder();
        for (Chapter c : chapters) {
            sb.append(StringUtils.hasText(c.getTitle()) ? c.getTitle() : "").append("\n\n");
            switch (effectiveLang) {
                case "original" -> sb.append(nullToEmpty(c.getOriginalText()));
                case "both" -> sb.append(nullToEmpty(c.getTranslatedText()))
                        .append("\n\n---\n\n")
                        .append(nullToEmpty(c.getOriginalText()));
                default -> sb.append(nullToEmpty(c.getTranslatedText()));
            }
            sb.append("\n\n");
        }
        return new ExportResult(novel.getTitle(), sb.toString());
    }

    private Novel getOwnedNovel(UUID novelId) {
        UUID userId = currentUserProvider.currentUserId();
        return novelRepository.findByIdAndUserId(novelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "소설을 찾을 수 없습니다."));
    }

    private NovelSummaryResponse toSummary(Novel novel) {
        long chapterCount = chapterRepository.countByNovelId(novel.getId());
        String lastReadChapterTitle = novel.getLastReadChapterIndex() != null
                ? chapterRepository.findByNovelIdAndChapterIndex(novel.getId(), novel.getLastReadChapterIndex())
                        .map(Chapter::getTitle)
                        .orElse(null)
                : null;
        return new NovelSummaryResponse(
                novel.getId(), novel.getTitle(), novel.getOriginalTitle(), novel.getCoverUrl(),
                novel.getSourceUrl(), novel.getSiteName(), chapterCount,
                novel.getLastReadChapterIndex(), lastReadChapterTitle, novel.getOrderIndex(), novel.getUpdatedAt());
    }

    private NovelDetailResponse toDetail(Novel novel, NovelPrompt prompt, List<Chapter> chapters) {
        List<ChapterSummaryResponse> chapterSummaries = chapters.stream()
                .map(c -> new ChapterSummaryResponse(c.getId(), c.getTitle(), c.getSourceUrl(), c.getChapterIndex()))
                .toList();
        return new NovelDetailResponse(
                novel.getId(), novel.getTitle(), novel.getOriginalTitle(), novel.getCoverUrl(),
                novel.getSourceUrl(), novel.getSiteName(),
                prompt != null ? prompt.getSystemPrompt() : null,
                prompt != null ? prompt.getTranslationNote() : null,
                novel.getLastReadChapterIndex(), novel.getLastReadScrollPos(), novel.getOrderIndex(),
                chapterSummaries, novel.getCreatedAt(), novel.getUpdatedAt());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record ExportResult(String title, String content) {
    }
}
