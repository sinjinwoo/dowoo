package io.dedyn.jwlabs.dowoo.settings.support;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "자동" 모드(사용자가 특정 모델을 고르지 않았을 때) 번역 시 시도할 무료 티어 모델과 기본 순서.
 * ApiKeySettingsService(사용자별 순서 저장/검증)와 TranslateService(실제 번역 시 사용)가 이 목록
 * 하나만 참조하게 해서, 예전처럼 두 곳(+프론트 주석)에 따로 하드코딩되어 순서가 드리프트하는 일을 막는다.
 *
 * "gemini-3-flash"(비-preview 이름)는 아직 API에 존재하지 않아 404가 나므로 반드시
 * "gemini-3-flash-preview"를 써야 한다(2026-07-05 확인). 유료 전용 모델(Pro 계열)은 여기 넣지 않는다 -
 * 사용자가 명시적으로 model을 지정했을 때만 쓰인다.
 */
public final class DefaultModelOrder {

    public static final List<String> MODELS = List.of(
            "gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.1-flash-lite",
            "gemini-3.6-flash", "gemini-3-flash-preview", "gemini-2.5-flash");

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

    /** 저장된 값이 없거나(신규 사용자) 손상되었으면(수동 DB 수정 등) 기본 순서로 안전하게 되돌아간다. */
    public static List<String> parseOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return MODELS;
        }
        List<String> parsed = Arrays.asList(raw.split(DELIMITER));
        return isValidPermutation(parsed) ? List.copyOf(parsed) : MODELS;
    }

    private DefaultModelOrder() {
    }
}
