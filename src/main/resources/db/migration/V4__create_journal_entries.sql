CREATE TABLE journal_entries (
    id           UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    date         DATE        NOT NULL,
    description  TEXT        NOT NULL,
    -- EXPENSE | INCOME | TRANSFER | DEBT_SETTLEMENT | LOAN_PAYMENT | SUBSCRIPTION | OPENING_BALANCE | ADJUSTMENT
    entry_type   VARCHAR(30) NOT NULL,
    -- Optional back-reference to the originating domain record (subscription, loan, debt_record)
    reference_id UUID,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_journal_entries      PRIMARY KEY (id),
    CONSTRAINT fk_je_user              FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_je_entry_type       CHECK (entry_type IN (
        'EXPENSE', 'INCOME', 'TRANSFER',
        'DEBT_SETTLEMENT', 'LOAN_PAYMENT', 'SUBSCRIPTION',
        'OPENING_BALANCE', 'ADJUSTMENT'
    ))
);
