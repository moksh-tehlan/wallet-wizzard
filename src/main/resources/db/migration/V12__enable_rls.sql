-- ─── Grant table-level permissions to the application role ───────────────────
GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA public
    TO walletwizzard_app;

GRANT USAGE
    ON ALL SEQUENCES IN SCHEMA public
    TO walletwizzard_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO walletwizzard_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE ON SEQUENCES TO walletwizzard_app;

-- ─── Enable RLS ───────────────────────────────────────────────────────────────
ALTER TABLE users               ENABLE ROW LEVEL SECURITY;
ALTER TABLE users               FORCE  ROW LEVEL SECURITY;

ALTER TABLE accounts            ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts            FORCE  ROW LEVEL SECURITY;

ALTER TABLE tags                ENABLE ROW LEVEL SECURITY;
ALTER TABLE tags                FORCE  ROW LEVEL SECURITY;

ALTER TABLE journal_entries     ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_entries     FORCE  ROW LEVEL SECURITY;

ALTER TABLE journal_entry_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_entry_lines FORCE  ROW LEVEL SECURITY;

ALTER TABLE journal_entry_tags  ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_entry_tags  FORCE  ROW LEVEL SECURITY;

ALTER TABLE people              ENABLE ROW LEVEL SECURITY;
ALTER TABLE people              FORCE  ROW LEVEL SECURITY;

ALTER TABLE debt_records        ENABLE ROW LEVEL SECURITY;
ALTER TABLE debt_records        FORCE  ROW LEVEL SECURITY;

ALTER TABLE loans               ENABLE ROW LEVEL SECURITY;
ALTER TABLE loans               FORCE  ROW LEVEL SECURITY;

ALTER TABLE subscriptions       ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions       FORCE  ROW LEVEL SECURITY;

-- ─── RLS Policies ─────────────────────────────────────────────────────────────
-- PostgreSQL CREATE POLICY only supports one command per FOR clause (no commas).
-- SELECT/UPDATE/DELETE use USING; INSERT uses WITH CHECK.

-- users: reads/writes scoped to own id; INSERT unrestricted (first-login provisioning)
CREATE POLICY pol_users_select ON users FOR SELECT TO walletwizzard_app
    USING (id::TEXT = current_setting('app.current_user_id', true));

CREATE POLICY pol_users_update ON users FOR UPDATE TO walletwizzard_app
    USING (id::TEXT = current_setting('app.current_user_id', true));

CREATE POLICY pol_users_delete ON users FOR DELETE TO walletwizzard_app
    USING (id::TEXT = current_setting('app.current_user_id', true));

CREATE POLICY pol_users_insert ON users FOR INSERT TO walletwizzard_app
    WITH CHECK (true);

-- accounts
CREATE POLICY pol_accounts ON accounts FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));

-- tags
CREATE POLICY pol_tags ON tags FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));

-- journal_entries
CREATE POLICY pol_journal_entries ON journal_entries FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));

-- journal_entry_lines
CREATE POLICY pol_journal_entry_lines ON journal_entry_lines FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));

-- journal_entry_tags (no user_id column; filter via parent entry)
CREATE POLICY pol_journal_entry_tags ON journal_entry_tags FOR ALL TO walletwizzard_app
    USING (
        journal_entry_id IN (
            SELECT id FROM journal_entries
            WHERE user_id::TEXT = current_setting('app.current_user_id', true)
        )
    );

-- people
CREATE POLICY pol_people ON people FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));

-- debt_records
CREATE POLICY pol_debt_records ON debt_records FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));

-- loans
CREATE POLICY pol_loans ON loans FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));

-- subscriptions
CREATE POLICY pol_subscriptions ON subscriptions FOR ALL TO walletwizzard_app
    USING     (user_id::TEXT = current_setting('app.current_user_id', true))
    WITH CHECK (user_id::TEXT = current_setting('app.current_user_id', true));
