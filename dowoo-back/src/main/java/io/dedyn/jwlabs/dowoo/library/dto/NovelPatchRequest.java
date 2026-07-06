package io.dedyn.jwlabs.dowoo.library.dto;

public record NovelPatchRequest(
        String title,
        String originalTitle,
        String coverUrl
) {
}
