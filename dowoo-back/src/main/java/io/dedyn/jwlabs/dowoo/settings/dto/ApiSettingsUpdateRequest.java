package io.dedyn.jwlabs.dowoo.settings.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** apiKeys를 생략하면(null) 키 목록은 건드리지 않고 model/thinkingBudget만 갱신한다 -
 * GET이 마스킹된 값만 주기 때문에 프론트가 기존 키를 재입력하지 않고는 전체 교체를 할 수 없어서다.
 * model도 비워두면(null/blank) "자동" 모드 - 번역 시 무료 친화적 모델을 순서대로 시도한다.
 * modelOrder도 apiKeys와 같은 이유로 생략 가능(null)하다 - model 드롭다운만 바꾸는 요청이 매번
 * modelOrder까지 같이 보내지 않으면(프론트가 model/thinkingBudget은 항상 현재 값을 재전송하는
 * 관례라 이 필드까지 강제하면 실수로 누락되기 쉽다) 사용자가 커스텀한 순서가 조용히 초기화된다. */
public record ApiSettingsUpdateRequest(
        List<@NotBlank String> apiKeys,
        String model,
        Integer thinkingBudget,
        List<@NotBlank String> modelOrder
) {
}
