CREATE TABLE subscriptions (
    id                 UUID           NOT NULL,
    user_id            UUID           NOT NULL,
    name               VARCHAR(255)   NOT NULL,
    amount             NUMERIC(18, 2) NOT NULL,
    billing_cycle      VARCHAR(20)    NOT NULL,  -- WEEKLY | MONTHLY | QUARTERLY | YEARLY
    next_billing_date  DATE,
    -- Which bank account / card pays for this subscription
    payment_account_id UUID,
    -- Which expense category it maps to
    expense_account_id UUID,
    status             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    notes              TEXT,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,

    CONSTRAINT pk_subscriptions             PRIMARY KEY (id),
    CONSTRAINT fk_subs_user                 FOREIGN KEY (user_id)            REFERENCES users(id)    ON DELETE CASCADE,
    CONSTRAINT fk_subs_payment_account      FOREIGN KEY (payment_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_subs_expense_account      FOREIGN KEY (expense_account_id) REFERENCES accounts(id),
    CONSTRAINT chk_subs_billing_cycle       CHECK (billing_cycle IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT chk_subs_status              CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED')),
    CONSTRAINT chk_subs_amount              CHECK (amount > 0)
);
