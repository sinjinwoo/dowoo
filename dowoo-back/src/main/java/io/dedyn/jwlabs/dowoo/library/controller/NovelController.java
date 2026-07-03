package io.dedyn.jwlabs.dowoo.library.controller;

import io.dedyn.jwlabs.dowoo.common.response.ApiResponse;
import io.dedyn.jwlabs.dowoo.library.dto.LastReadRequest;
import io.dedyn.jwlabs.dowoo.library.dto.NovelCreateRequest;
import io.dedyn.jwlabs.dowoo.library.dto.NovelDetailResponse;
import io.dedyn.jwlabs.dowoo.library.dto.NovelPatchRequest;
import io.dedyn.jwlabs.dowoo.library.dto.NovelSummaryResponse;
import io.dedyn.jwlabs.dowoo.library.dto.ReorderRequest;
import io.dedyn.jwlabs.dowoo.library.service.NovelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/novels")
@RequiredArgsConstructor
public class NovelController {

    private final NovelService novelService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NovelSummaryResponse>>> list(
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(ApiResponse.success(200, novelService.list(keyword), "조회 성공"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NovelDetailResponse>> create(@Valid @RequestBody NovelCreateRequest request) {
        NovelDetailResponse created = novelService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, created, "소설이 등록되었습니다."));
    }

    @GetMapping("/{novelId}")
    public ResponseEntity<ApiResponse<NovelDetailResponse>> detail(@PathVariable UUID novelId) {
        return ResponseEntity.ok(ApiResponse.success(200, novelService.getDetail(novelId), "조회 성공"));
    }

    @PatchMapping("/{novelId}")
    public ResponseEntity<ApiResponse<NovelDetailResponse>> patch(
            @PathVariable UUID novelId, @RequestBody NovelPatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(200, novelService.patch(novelId, request), "수정되었습니다."));
    }

    @DeleteMapping("/{novelId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID novelId) {
        novelService.delete(novelId);
        return ResponseEntity.ok(ApiResponse.<Void>success(200, null, "삭제되었습니다."));
    }

    @PatchMapping("/reorder")
    public ResponseEntity<ApiResponse<Void>> reorder(@Valid @RequestBody ReorderRequest request) {
        novelService.reorder(request);
        return ResponseEntity.ok(ApiResponse.<Void>success(200, null, "순서가 변경되었습니다."));
    }

    @PatchMapping("/{novelId}/last-read")
    public ResponseEntity<ApiResponse<Void>> lastRead(
            @PathVariable UUID novelId, @Valid @RequestBody LastReadRequest request) {
        novelService.updateLastRead(novelId, request);
        return ResponseEntity.ok(ApiResponse.<Void>success(200, null, "저장되었습니다."));
    }

    @GetMapping("/{novelId}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID novelId,
            @RequestParam(required = false, defaultValue = "translated") String lang) {
        NovelService.ExportResult result = novelService.export(novelId, lang);
        byte[] body = result.content().getBytes(StandardCharsets.UTF_8);
        String filename = URLEncoder.encode(result.title() + ".txt", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(body);
    }
}
