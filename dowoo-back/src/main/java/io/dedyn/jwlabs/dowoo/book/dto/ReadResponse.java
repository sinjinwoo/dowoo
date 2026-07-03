package io.dedyn.jwlabs.dowoo.book.dto;

import java.util.UUID;

public record ReadResponse(
        UUID novelId,
        UUID chapterId,
        String translatedText
) {
}
