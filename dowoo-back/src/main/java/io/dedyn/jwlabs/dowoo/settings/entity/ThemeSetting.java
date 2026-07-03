package io.dedyn.jwlabs.dowoo.settings.entity;

import io.dedyn.jwlabs.dowoo.auth.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "theme_settings")
@Getter
@Setter
@NoArgsConstructor
public class ThemeSetting {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "font_family")
    private String fontFamily;

    @Column(name = "font_color")
    private String fontColor;

    @Column(name = "bg_color")
    private String bgColor;

    @Column(name = "font_size")
    private String fontSize;

    @Column(name = "font_weight")
    private String fontWeight;

    @Column(name = "line_height")
    private String lineHeight;

    @Column(name = "text_indent")
    private String textIndent;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
