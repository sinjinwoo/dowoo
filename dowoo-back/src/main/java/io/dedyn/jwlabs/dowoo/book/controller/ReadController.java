package io.dedyn.jwlabs.dowoo.book.controller;

import io.dedyn.jwlabs.dowoo.book.dto.ReadRequest;
import io.dedyn.jwlabs.dowoo.book.dto.ReadResponse;
import io.dedyn.jwlabs.dowoo.book.service.ReadService;
import io.dedyn.jwlabs.dowoo.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/read")
@RequiredArgsConstructor
public class ReadController {

    private final ReadService readService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReadResponse>> read(@RequestBody ReadRequest request) {
        return ResponseEntity.ok(ApiResponse.success(200, readService.read(request), "조회/등록 성공"));
    }
}
