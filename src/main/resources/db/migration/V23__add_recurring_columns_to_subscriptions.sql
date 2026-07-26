-- Add side (DEBIT/CREDIT) and schedule_type (FIXED_DAY/LAST_DAY/LAST_WEEKDAY)
-- to support both outgoing and incoming recurring transactions.
-- Existing subscriptions default to outgoing (DEBIT) with a fixed day schedule.
ALTER TABLE subscriptions
    ADD COLUMN side          VARCHAR(10)  NOT NULL DEFAULT 'DEBIT',
    ADD COLUMN schedule_type VARCHAR(20)  NOT NULL DEFAULT 'FIXED_DAY';
