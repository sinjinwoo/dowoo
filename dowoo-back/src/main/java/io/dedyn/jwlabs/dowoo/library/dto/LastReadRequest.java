package io.dedyn.jwlabs.dowoo.library.dto;

import jakarta.validation.constraints.NotNull;

public record LastReadRequest(
        @NotNull Integer lastReadChapterIndex,
        Double lastReadScrollPos
) {
}
