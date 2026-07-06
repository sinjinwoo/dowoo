package io.dedyn.jwlabs.dowoo.library.controller;

import io.dedyn.jwlabs.dowoo.common.response.ApiResponse;
import io.dedyn.jwlabs.dowoo.library.dto.PromptCreateRequest;
import io.dedyn.jwlabs.dowoo.library.dto.PromptPatchRequest;
import io.dedyn.jwlabs.dowoo.library.dto.PromptResponse;
import io.dedyn.jwlabs.dowoo.library.service.PromptService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/prompts")
@RequiredArgsConstructor
public class PromptController {

    private final PromptService promptService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PromptResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(200, promptService.list(), "조회 성공"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PromptResponse>> create(@Valid @RequestBody PromptCreateRequest request) {
        PromptResponse created = promptService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, created, "프롬프트가 등록되었습니다."));
    }

    @PatchMapping("/{promptId}")
    public ResponseEntity<ApiResponse<PromptResponse>> patch(
            @PathVariable UUID promptId, @RequestBody PromptPatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(200, promptService.update(promptId, request), "수정되었습니다."));
    }

    @DeleteMapping("/{promptId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID promptId) {
        promptService.delete(promptId);
        return ResponseEntity.ok(ApiResponse.<Void>success(200, null, "삭제되었습니다."));
    }
}
