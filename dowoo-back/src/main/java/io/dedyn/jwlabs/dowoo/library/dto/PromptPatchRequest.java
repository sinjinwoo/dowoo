package io.dedyn.jwlabs.dowoo.library.dto;

/** title은 기본 프롬프트(isDefault=true)에는 적용되지 않는다 - PromptService.update 참고. */
public record PromptPatchRequest(
        String title,
        String systemPrompt,
        String translationNote
) {
}
