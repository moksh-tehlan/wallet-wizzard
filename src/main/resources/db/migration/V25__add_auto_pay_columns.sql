-- Auto-pay configuration for loans and investments
ALTER TABLE loans
    ADD COLUMN default_bank_account_id UUID REFERENCES accounts(id);

ALTER TABLE investments
    ADD COLUMN default_bank_account_id UUID REFERENCES accounts(id),
    ADD COLUMN next_contribution_date   DATE;
