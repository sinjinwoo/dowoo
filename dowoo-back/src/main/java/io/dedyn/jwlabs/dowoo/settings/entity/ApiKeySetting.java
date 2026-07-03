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

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
