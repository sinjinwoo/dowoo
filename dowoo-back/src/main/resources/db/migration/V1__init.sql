CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    oauth_provider  VARCHAR(32)  NOT NULL,
    oauth_id        VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    withdrawn_at    TIMESTAMPTZ,
    CONSTRAINT uq_users_oauth UNIQUE (oauth_provider, oauth_id)
);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

CREATE TABLE novels (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title                    VARCHAR(500) NOT NULL,
    original_title           VARCHAR(500),
    cover_url                TEXT,
    source_url               TEXT NOT NULL,
    site_name                VARCHAR(255) NOT NULL,
    last_read_chapter_index  INTEGER,
    last_read_scroll_pos     DOUBLE PRECISION,
    order_index              INTEGER NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_novels_user_source UNIQUE (user_id, source_url)
);

CREATE INDEX idx_novels_user_id ON novels (user_id);

CREATE TABLE novel_prompts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    novel_id          UUID NOT NULL UNIQUE REFERENCES novels (id) ON DELETE CASCADE,
    system_prompt     TEXT,
    translation_note  TEXT,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chapters (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    novel_id         UUID NOT NULL REFERENCES novels (id) ON DELETE CASCADE,
    source_url       TEXT NOT NULL,
    title            VARCHAR(500),
    original_text    TEXT,
    translated_text  TEXT,
    prev_url         TEXT,
    next_url         TEXT,
    chapter_index    INTEGER NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_chapters_novel_source UNIQUE (novel_id, source_url)
);

CREATE INDEX idx_chapters_novel_id ON chapters (novel_id);

CREATE TABLE api_key_settings (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    model            VARCHAR(100),
    thinking_budget  INTEGER,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE api_keys (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    encrypted_key  TEXT NOT NULL,
    key_order      INTEGER NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_keys_user_id ON api_keys (user_id);

CREATE TABLE theme_settings (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    font_family  VARCHAR(255),
    font_color   VARCHAR(32),
    bg_color     VARCHAR(32),
    font_size    VARCHAR(16),
    font_weight  VARCHAR(16),
    line_height  VARCHAR(16),
    text_indent  VARCHAR(16),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE theme_presets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    theme       JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_theme_presets_user_name UNIQUE (user_id, name)
);
