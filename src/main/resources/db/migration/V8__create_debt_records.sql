-- Debt records are metadata only. The live outstanding balance is always
-- computed from the linked account's journal entry lines (balance = 0 → settled).
CREATE TABLE debt_records (
    id              UUID           NOT NULL,
    user_id         UUID           NOT NULL,
    person_id       UUID           NOT NULL,
    -- The ASSET (receivable) or LIABILITY (payable) account created for this debt
    account_id      UUID           NOT NULL,
    direction       VARCHAR(10)    NOT NULL,  -- LENT | BORROWED
    original_amount NUMERIC(18, 2) NOT NULL,
    description     TEXT,
    due_date        DATE,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_debt_records          PRIMARY KEY (id),
    CONSTRAINT fk_dr_user               FOREIGN KEY (user_id)   REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_dr_person             FOREIGN KEY (person_id) REFERENCES people(id),
    CONSTRAINT fk_dr_account            FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT chk_dr_direction         CHECK (direction IN ('LENT', 'BORROWED')),
    CONSTRAINT chk_dr_original_amount   CHECK (original_amount > 0)
);
