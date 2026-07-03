package io.dedyn.jwlabs.dowoo.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "oauth_provider", nullable = false)
    private String oauthProvider;

    @Column(name = "oauth_id", nullable = false)
    private String oauthId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;
}
