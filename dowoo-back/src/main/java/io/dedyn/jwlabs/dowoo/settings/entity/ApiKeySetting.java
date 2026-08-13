package io.dedyn.jwlabs.dowoo.settings.entity;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_key_settings")
@Getter
@Setter
@NoArgsConstructor
public class ApiKeySetting {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private String model;

    @Column(name = "thinking_budget")
    private Integer thinkingBudget;

    // 콤마로 구분된 모델 id 목록("자동" 모드에서 시도할 순서). NULL이면 DefaultModelOrder.MODELS를 쓴다.
    @Column(name = "model_order")
    private String modelOrder;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
