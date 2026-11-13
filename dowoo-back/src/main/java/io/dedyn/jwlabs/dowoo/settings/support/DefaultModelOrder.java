package io.dedyn.jwlabs.dowoo.settings.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "자동" 모드(사용자가 특정 모델을 고르지 않았을 때) 번역 시 시도할 무료 티어 모델과 기본 순서.
 * ApiKeySettingsService(사용자별 순서 저장/검증)와 TranslateService(실제 번역 시 사용)가 이 목록
 * 하나만 참조하게 해서, 예전처럼 두 곳(+프론트 주석)에 따로 하드코딩되어 순서가 드리프트하는 일을 막는다.
 *
 * 유료 전용 모델(Pro/Gemini 4 Carbon·Argon 등)은 여기 넣지 않는다 -
 * 사용자가 명시적으로 model을 지정했을 때만 쓰인다.
 * 2026-11-13: gemini-4-hydrogen(Gemini 4 중 유일하게 무료 티어 제공), gemini-3.8-flash 추가 - 공식 가격 문서
 * (aistudio.google.com/docs/pricing)에서 둘 다 Free Tier "Free of charge" 확인. 실제 API 모델 목록(models.list)에
 * 없어 404가 나는 gemini-3-flash-preview와 지원 종료 예정인 gemini-2.5-flash(2026-12-01부터 3.5-flash-lite로
 * 라우팅)는 제거. gemini-3.5-flash는 문서 목록엔 없지만 API에는 존재해 유지한다(2026-11-13 확인).
 */
public final class DefaultModelOrder {

    public static final List<String> MODELS = List.of(
            "gemini-4-hydrogen", "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.5-flash",
            "gemini-3.1-flash-lite", "gemini-3.6-flash");

    private static final Set<String> MODEL_SET = new HashSet<>(MODELS);
    private static final String DELIMITER = ",";

    /** DB에 저장하기 전, 사용자가 보낸 순서가 정확히 MODELS의 순열(추가/누락/중복 없음)인지 검증한다. */
    public static boolean isValidPermutation(List<String> order) {
        return order != null
                && order.size() == MODELS.size()
                && new HashSet<>(order).equals(MODEL_SET);
    }

    public static String serialize(List<String> order) {
        return String.join(DELIMITER, order);
    }

    /**
     * 저장된 사용자 순서를 현재 MODELS 기준으로 맞춘다 - 모델이 추가/삭제돼도 사용자가 정한 순서는 유지하고,
     * 목록에서 빠진 모델은 버리고, 새로 추가된 모델은 뒤에 붙인다(예전처럼 통째로 기본 순서로 초기화하지 않음).
     * 저장된 값이 없거나 쓸 수 있는 모델이 하나도 없으면 기본 순서로 되돌아간다.
     */
    public static List<String> parseOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return MODELS;
        }
        List<String> merged = new ArrayList<>(
                Arrays.stream(raw.split(DELIMITER)).filter(MODEL_SET::contains).distinct().toList());
        if (merged.isEmpty()) {
            return MODELS;
        }
        MODELS.stream().filter(m -> !merged.contains(m)).forEach(merged::add);
        return List.copyOf(merged);
    }

    private DefaultModelOrder() {
    }
}
