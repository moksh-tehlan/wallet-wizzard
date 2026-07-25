CREATE TABLE expense_groups (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users(id),
    name       VARCHAR(200) NOT NULL,
    notes      TEXT,
    is_active  BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE group_members (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users(id),
    group_id   UUID        NOT NULL REFERENCES expense_groups(id),
    person_id  UUID        NOT NULL REFERENCES people(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, person_id)
);

CREATE TABLE group_expenses (
    id                  UUID         PRIMARY KEY,
    user_id             UUID         NOT NULL REFERENCES users(id),
    group_id            UUID         NOT NULL REFERENCES expense_groups(id),
    description         VARCHAR(500) NOT NULL,
    total_amount        NUMERIC(19,4) NOT NULL,
    paid_by_person_id   UUID         REFERENCES people(id),
    expense_account_id  UUID         REFERENCES accounts(id),
    bank_account_id     UUID         REFERENCES accounts(id),
    date                DATE         NOT NULL,
    split_type          VARCHAR(20)  NOT NULL DEFAULT 'EQUAL',
    journal_entry_id    UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE group_expense_splits (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users(id),
    expense_id   UUID          NOT NULL REFERENCES group_expenses(id),
    person_id    UUID          REFERENCES people(id),
    share_amount NUMERIC(19,4) NOT NULL,
    is_settled   BOOLEAN       NOT NULL DEFAULT false,
    settled_date DATE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

ALTER TABLE expense_groups        ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_members         ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_expenses        ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_expense_splits  ENABLE ROW LEVEL SECURITY;

CREATE POLICY expense_groups_rls       ON expense_groups       USING (user_id = current_setting('app.current_user_id')::uuid);
CREATE POLICY group_members_rls        ON group_members        USING (user_id = current_setting('app.current_user_id')::uuid);
CREATE POLICY group_expenses_rls       ON group_expenses       USING (user_id = current_setting('app.current_user_id')::uuid);
CREATE POLICY group_expense_splits_rls ON group_expense_splits USING (user_id = current_setting('app.current_user_id')::uuid);

CREATE INDEX idx_group_members_group    ON group_members(group_id);
CREATE INDEX idx_group_expenses_group   ON group_expenses(group_id);
CREATE INDEX idx_group_splits_expense   ON group_expense_splits(expense_id);
CREATE INDEX idx_group_splits_person    ON group_expense_splits(person_id) WHERE person_id IS NOT NULL;
