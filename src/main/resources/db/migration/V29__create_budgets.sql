CREATE TABLE budgets (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id),
    account_id  UUID        NOT NULL REFERENCES accounts(id),
    amount      NUMERIC(18, 2) NOT NULL,
    period      VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, account_id, period)
);

ALTER TABLE budgets ENABLE ROW LEVEL SECURITY;
CREATE POLICY budgets_user ON budgets
    USING (user_id = (current_setting('app.current_user_id'))::uuid);
