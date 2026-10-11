CREATE TABLE net_worth_snapshots (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id             UUID        NOT NULL REFERENCES users(id),
    snapshot_date       DATE        NOT NULL,
    total_assets        NUMERIC(18, 2) NOT NULL,
    total_liabilities   NUMERIC(18, 2) NOT NULL,
    net_worth           NUMERIC(18, 2) NOT NULL,
    liquid_net_worth    NUMERIC(18, 2) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, snapshot_date)
);

ALTER TABLE net_worth_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY net_worth_snapshots_user ON net_worth_snapshots
    USING (user_id = (current_setting('app.current_user_id'))::uuid);
