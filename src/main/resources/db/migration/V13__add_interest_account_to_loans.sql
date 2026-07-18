-- Stores which expense/income account absorbs interest on each loan.
-- Nullable: pre-existing loans and loans without interest tracking leave it null.
ALTER TABLE loans
    ADD COLUMN interest_account_id UUID REFERENCES accounts(id);
