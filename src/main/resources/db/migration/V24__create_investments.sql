CREATE TABLE investments (
    id                    UUID           NOT NULL,
    user_id               UUID           NOT NULL,
    account_id            UUID           NOT NULL,
    type                  VARCHAR(20)    NOT NULL,
    name                  VARCHAR(255)   NOT NULL,
    invested_amount       NUMERIC(18, 2) NOT NULL,   -- total cash put in (cost basis)
    current_value         NUMERIC(18, 2) NOT NULL,   -- latest computed/fetched market value
    units                 NUMERIC(18, 6),             -- MF: units held
    scheme_code           VARCHAR(20),                -- MF: mfapi.in scheme code
    interest_rate         NUMERIC(7, 4),              -- EPF/FD/RD: annual rate e.g. 0.0825
    monthly_contribution  NUMERIC(18, 2),             -- EPF/RD: monthly installment amount
    start_date            DATE,
    maturity_date         DATE,                       -- FD/RD: when it matures
    last_refreshed_at     DATE,
    notes                 TEXT,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_investments         PRIMARY KEY (id),
    CONSTRAINT fk_investments_user    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_investments_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT chk_investment_type    CHECK (type IN ('MUTUAL_FUND', 'EPF', 'FD', 'RD'))
);

CREATE INDEX idx_investments_user ON investments (user_id);

ALTER TABLE investments ENABLE ROW LEVEL SECURITY;
ALTER TABLE investments FORCE  ROW LEVEL SECURITY;

CREATE POLICY pol_investments ON investments FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));
