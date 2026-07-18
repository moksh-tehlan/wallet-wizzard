CREATE TABLE accounts (
    id             UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    name           VARCHAR(255) NOT NULL,
    -- ASSET/LIABILITY/INCOME/EXPENSE/EQUITY
    type           VARCHAR(20)  NOT NULL,
    -- DEBIT (ASSET/EXPENSE) or CREDIT (LIABILITY/INCOME/EQUITY)
    normal_balance VARCHAR(10)  NOT NULL,
    parent_id      UUID,
    is_system      BOOLEAN      NOT NULL DEFAULT false,
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    notes          TEXT,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_accounts              PRIMARY KEY (id),
    CONSTRAINT fk_accounts_user         FOREIGN KEY (user_id)   REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_accounts_parent       FOREIGN KEY (parent_id) REFERENCES accounts(id) ON DELETE SET NULL,
    CONSTRAINT chk_accounts_type        CHECK (type           IN ('ASSET', 'LIABILITY', 'INCOME', 'EXPENSE', 'EQUITY')),
    CONSTRAINT chk_accounts_balance     CHECK (normal_balance IN ('DEBIT', 'CREDIT')),
    -- system accounts cannot be their own parent
    CONSTRAINT chk_accounts_no_self_ref CHECK (id <> parent_id)
);
