CREATE TABLE loans (
    id            UUID           NOT NULL,
    user_id       UUID           NOT NULL,
    -- The LIABILITY (loan taken) or ASSET (loan given) account for this loan
    account_id    UUID           NOT NULL,
    name          VARCHAR(255)   NOT NULL,
    direction     VARCHAR(10)    NOT NULL,       -- TAKEN | GIVEN
    principal     NUMERIC(18, 2) NOT NULL,
    -- Stored as a decimal rate: 0.0875 = 8.75% per annum
    interest_rate NUMERIC(7, 4),
    emi_amount    NUMERIC(18, 2),
    start_date    DATE,
    end_date      DATE,
    notes         TEXT,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_loans                 PRIMARY KEY (id),
    CONSTRAINT fk_loans_user            FOREIGN KEY (user_id)   REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_loans_account         FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT chk_loans_direction      CHECK (direction IN ('TAKEN', 'GIVEN')),
    CONSTRAINT chk_loans_principal      CHECK (principal > 0),
    CONSTRAINT chk_loans_interest_rate  CHECK (interest_rate IS NULL OR (interest_rate >= 0 AND interest_rate <= 1)),
    CONSTRAINT chk_loans_emi            CHECK (emi_amount IS NULL OR emi_amount > 0),
    CONSTRAINT chk_loans_dates          CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);
