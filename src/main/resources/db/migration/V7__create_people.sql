CREATE TABLE people (
    id         UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    name       VARCHAR(255) NOT NULL,
    phone      VARCHAR(20),
    email      VARCHAR(255),
    notes      TEXT,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_people        PRIMARY KEY (id),
    CONSTRAINT fk_people_user   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
