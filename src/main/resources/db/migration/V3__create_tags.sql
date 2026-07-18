CREATE TABLE tags (
    id         UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    name       VARCHAR(100) NOT NULL,
    is_system  BOOLEAN      NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_tags              PRIMARY KEY (id),
    CONSTRAINT fk_tags_user         FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_tags_user_name    UNIQUE (user_id, name)
);
