-- Share tracking for any account (credit cards, liabilities, etc.)
-- Generalizes the LoanParticipant concept to any account type.
CREATE TABLE account_participants (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES users(id),
    account_id         UUID        NOT NULL REFERENCES accounts(id),
    person_id          UUID        NOT NULL REFERENCES people(id),
    share_percent      NUMERIC(5,2) NOT NULL CHECK (share_percent > 0 AND share_percent <= 100),
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE (account_id, person_id)
);

ALTER TABLE account_participants ENABLE ROW LEVEL SECURITY;

CREATE POLICY account_participants_isolation ON account_participants
    USING (user_id::text = current_setting('app.current_user_id', true));
