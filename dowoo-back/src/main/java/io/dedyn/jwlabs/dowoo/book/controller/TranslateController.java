package io.dedyn.jwlabs.dowoo.book.controller;

import io.dedyn.jwlabs.dowoo.book.service.TranslateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/novels/{novelId}/chapters/{chapterId}/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final TranslateService translateService;

    @PostMapping("/stream")
    public SseEmitter translateStream(@PathVariable UUID novelId, @PathVariable UUID chapterId) {
        return translateService.translate(novelId, chapterId);
    }
}
