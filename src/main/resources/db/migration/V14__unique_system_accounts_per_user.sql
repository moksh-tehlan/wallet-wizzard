-- Prevents duplicate system accounts if two first-login requests race.
-- The application-side check (existsByIsSystemTrue) is best-effort;
-- this constraint is the hard guarantee.
CREATE UNIQUE INDEX uq_system_account_name_per_user
    ON accounts (user_id, name)
    WHERE is_system = true;
