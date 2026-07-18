CREATE TABLE journal_entry_lines (
    id               UUID           NOT NULL,
    -- user_id is redundant (derivable via journal_entry) but needed for efficient RLS
    user_id          UUID           NOT NULL,
    journal_entry_id UUID           NOT NULL,
    account_id       UUID           NOT NULL,
    amount           NUMERIC(18, 2) NOT NULL,
    side             VARCHAR(10)    NOT NULL,
    memo             TEXT,

    CONSTRAINT pk_journal_entry_lines   PRIMARY KEY (id),
    CONSTRAINT fk_jel_user              FOREIGN KEY (user_id)          REFERENCES users(id)           ON DELETE CASCADE,
    CONSTRAINT fk_jel_journal_entry     FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE CASCADE,
    CONSTRAINT fk_jel_account           FOREIGN KEY (account_id)       REFERENCES accounts(id),
    CONSTRAINT chk_jel_amount           CHECK (amount > 0),
    CONSTRAINT chk_jel_side             CHECK (side IN ('DEBIT', 'CREDIT'))
);

-- ─── Double-entry balance trigger ───────────────────────────────────────────
-- Fires at transaction commit (DEFERRABLE INITIALLY DEFERRED) so all lines for
-- an entry can be inserted within the same transaction before the check runs.

CREATE OR REPLACE FUNCTION fn_check_journal_entry_balanced()
RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_entry_id   UUID;
    v_debit      NUMERIC;
    v_credit     NUMERIC;
BEGIN
    -- NEW is undefined for DELETE triggers; OLD is undefined for INSERT triggers
    IF TG_OP = 'DELETE' THEN
        v_entry_id := OLD.journal_entry_id;
    ELSE
        v_entry_id := NEW.journal_entry_id;
    END IF;

    SELECT
        COALESCE(SUM(amount) FILTER (WHERE side = 'DEBIT'),  0),
        COALESCE(SUM(amount) FILTER (WHERE side = 'CREDIT'), 0)
    INTO v_debit, v_credit
    FROM journal_entry_lines
    WHERE journal_entry_id = v_entry_id;

    IF v_debit <> v_credit THEN
        RAISE EXCEPTION
            'Journal entry % is unbalanced: total debits = %, total credits = %',
            v_entry_id, v_debit, v_credit;
    END IF;

    -- For DELETE triggers the return value is ignored; return OLD to satisfy the signature
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_journal_entry_balanced
    AFTER INSERT OR UPDATE OR DELETE
    ON journal_entry_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION fn_check_journal_entry_balanced();
