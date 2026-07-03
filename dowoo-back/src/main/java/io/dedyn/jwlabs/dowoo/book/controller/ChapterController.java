package io.dedyn.jwlabs.dowoo.book.controller;

import io.dedyn.jwlabs.dowoo.book.dto.ChapterCreateRequest;
import io.dedyn.jwlabs.dowoo.book.dto.ChapterPatchRequest;
import io.dedyn.jwlabs.dowoo.book.dto.ChapterResponse;
import io.dedyn.jwlabs.dowoo.book.service.ChapterService;
import io.dedyn.jwlabs.dowoo.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;

    @GetMapping("/{chapterId}")
    public ResponseEntity<ApiResponse<ChapterResponse>> get(
            @PathVariable UUID novelId, @PathVariable UUID chapterId) {
        return ResponseEntity.ok(ApiResponse.success(200, chapterService.getChapter(novelId, chapterId), "조회 성공"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChapterResponse>> create(
            @PathVariable UUID novelId, @Valid @RequestBody ChapterCreateRequest request) {
        ChapterService.ChapterCreateResult result = chapterService.createOrGetCached(novelId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(
                status.value(), result.chapter(), result.created() ? "챕터가 저장되었습니다." : "이미 저장된 챕터입니다."));
    }

    @PatchMapping("/{chapterId}")
    public ResponseEntity<ApiResponse<ChapterResponse>> patch(
            @PathVariable UUID novelId, @PathVariable UUID chapterId,
            @Valid @RequestBody ChapterPatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                200, chapterService.patchTranslation(novelId, chapterId, request), "번역이 저장되었습니다."));
    }

    @DeleteMapping("/{chapterId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID novelId, @PathVariable UUID chapterId) {
        chapterService.delete(novelId, chapterId);
        return ResponseEntity.ok(ApiResponse.<Void>success(200, null, "삭제되었습니다."));
    }
}
