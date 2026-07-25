CREATE TABLE loan_installments (
    id                UUID           NOT NULL,
    user_id           UUID           NOT NULL,
    loan_id           UUID           NOT NULL,
    installment_no    INTEGER        NOT NULL,
    due_date          DATE           NOT NULL,
    principal_amount  NUMERIC(18, 2) NOT NULL,
    interest_amount   NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_amount      NUMERIC(18, 2) NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'SCHEDULED',
    paid_date         DATE,
    journal_entry_id  UUID,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_loan_installments         PRIMARY KEY (id),
    CONSTRAINT fk_loan_installments_user    FOREIGN KEY (user_id)          REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_loan_installments_loan    FOREIGN KEY (loan_id)          REFERENCES loans(id) ON DELETE CASCADE,
    CONSTRAINT fk_loan_installments_entry   FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id),
    CONSTRAINT uq_loan_installments_no      UNIQUE (loan_id, installment_no),
    CONSTRAINT chk_installment_status       CHECK (status IN ('SCHEDULED', 'DUE', 'PAID')),
    CONSTRAINT chk_installment_amounts      CHECK (principal_amount >= 0 AND interest_amount >= 0 AND total_amount > 0)
);

CREATE INDEX idx_loan_installments_loan   ON loan_installments (loan_id);
CREATE INDEX idx_loan_installments_due    ON loan_installments (due_date) WHERE status IN ('SCHEDULED', 'DUE');
CREATE INDEX idx_loan_installments_user   ON loan_installments (user_id);

ALTER TABLE loan_installments ENABLE ROW LEVEL SECURITY;
ALTER TABLE loan_installments FORCE  ROW LEVEL SECURITY;

CREATE POLICY pol_loan_installments ON loan_installments FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));
