-- ─── users ───────────────────────────────────────────────────────────────────
CREATE INDEX idx_users_email
    ON users (email);

-- ─── accounts ────────────────────────────────────────────────────────────────
CREATE INDEX idx_accounts_user
    ON accounts (user_id);

CREATE INDEX idx_accounts_user_type
    ON accounts (user_id, type);

-- Partial: most accounts have no parent; only index the ones that do
CREATE INDEX idx_accounts_parent
    ON accounts (parent_id)
    WHERE parent_id IS NOT NULL;

-- ─── tags ────────────────────────────────────────────────────────────────────
CREATE INDEX idx_tags_user
    ON tags (user_id);

-- ─── journal_entries ─────────────────────────────────────────────────────────
-- Primary access pattern: "show me entries for user X, newest first"
CREATE INDEX idx_je_user_date
    ON journal_entries (user_id, date DESC);

CREATE INDEX idx_je_user_type
    ON journal_entries (user_id, entry_type);

-- Back-reference lookups (e.g., "all journal entries for loan #X")
CREATE INDEX idx_je_reference
    ON journal_entries (reference_id)
    WHERE reference_id IS NOT NULL;

-- ─── journal_entry_lines ─────────────────────────────────────────────────────
-- Used when loading lines for a given entry (Hibernate join)
CREATE INDEX idx_jel_journal_entry
    ON journal_entry_lines (journal_entry_id);

-- Used for account balance computation: SUM lines WHERE account_id = ?
CREATE INDEX idx_jel_account
    ON journal_entry_lines (account_id);

-- Used for RLS-filtered balance queries: WHERE user_id = ? AND account_id = ?
CREATE INDEX idx_jel_user_account
    ON journal_entry_lines (user_id, account_id);

-- ─── journal_entry_tags ──────────────────────────────────────────────────────
CREATE INDEX idx_jet_tag
    ON journal_entry_tags (tag_id);

-- ─── people ──────────────────────────────────────────────────────────────────
CREATE INDEX idx_people_user
    ON people (user_id);

-- ─── debt_records ────────────────────────────────────────────────────────────
CREATE INDEX idx_dr_user
    ON debt_records (user_id);

CREATE INDEX idx_dr_person
    ON debt_records (person_id);

-- ─── loans ───────────────────────────────────────────────────────────────────
CREATE INDEX idx_loans_user
    ON loans (user_id);

-- ─── subscriptions ───────────────────────────────────────────────────────────
CREATE INDEX idx_subs_user_status
    ON subscriptions (user_id, status);

-- Upcoming bills query: active subs ordered by next_billing_date
CREATE INDEX idx_subs_next_bill
    ON subscriptions (user_id, next_billing_date)
    WHERE status = 'ACTIVE';
