-- Add INSTALLMENT_PAYMENT and GROUP_SETTLEMENT to the entry_type check constraint.
ALTER TABLE journal_entries DROP CONSTRAINT chk_je_entry_type;

ALTER TABLE journal_entries ADD CONSTRAINT chk_je_entry_type
    CHECK (entry_type IN (
        'EXPENSE', 'INCOME', 'TRANSFER',
        'DEBT_SETTLEMENT', 'LOAN_PAYMENT', 'INSTALLMENT_PAYMENT',
        'GROUP_SETTLEMENT',
        'SUBSCRIPTION', 'OPENING_BALANCE', 'ADJUSTMENT'
    ));
