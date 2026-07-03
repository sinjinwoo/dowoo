package io.dedyn.jwlabs.dowoo.library.support;

/** 신규 소설 생성 시 기본으로 채워 넣는 시스템 프롬프트. dowoo/src/data/defaults.ts의 옛 defaultSystemPrompt와 동일한 문구. */
public final class DefaultPrompts {

    public static final String SYSTEM_PROMPT =
            "당신은 전문 웹소설 번역가입니다. 아래 원문을 자연스러운 한국어로 번역하세요. "
                    + "문체와 어조를 원문에 맞게 유지하고, 등장인물의 말투 차이를 살려주세요.\n\n{{memo}}";

    private DefaultPrompts() {
    }
}
