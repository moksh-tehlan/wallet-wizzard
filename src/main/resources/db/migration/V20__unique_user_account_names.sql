-- Deduplicate non-system accounts before adding the unique constraint.
-- Keeps the oldest account per (user_id, name); reassigns all FK references
-- from newer duplicates to the kept one, then deletes the duplicates.

-- 1. Reassign journal_entry_lines from duplicates to the kept account
UPDATE journal_entry_lines jel
SET account_id = kept.id
FROM (
    SELECT DISTINCT ON (user_id, name) id, user_id, name
    FROM accounts
    WHERE is_system = false
    ORDER BY user_id, name, created_at
) AS kept
JOIN accounts dup
    ON dup.user_id = kept.user_id
   AND dup.name    = kept.name
   AND dup.id     != kept.id
   AND dup.is_system = false
WHERE jel.account_id = dup.id;

-- 2. Reassign debt_records from duplicates to the kept account
UPDATE debt_records dr
SET account_id = kept.id
FROM (
    SELECT DISTINCT ON (user_id, name) id, user_id, name
    FROM accounts
    WHERE is_system = false
    ORDER BY user_id, name, created_at
) AS kept
JOIN accounts dup
    ON dup.user_id = kept.user_id
   AND dup.name    = kept.name
   AND dup.id     != kept.id
   AND dup.is_system = false
WHERE dr.account_id = dup.id;

-- 3. Reassign loans.account_id from duplicates to the kept account
UPDATE loans l
SET account_id = kept.id
FROM (
    SELECT DISTINCT ON (user_id, name) id, user_id, name
    FROM accounts
    WHERE is_system = false
    ORDER BY user_id, name, created_at
) AS kept
JOIN accounts dup
    ON dup.user_id = kept.user_id
   AND dup.name    = kept.name
   AND dup.id     != kept.id
   AND dup.is_system = false
WHERE l.account_id = dup.id;

-- 4. Reassign loans.interest_account_id from duplicates to the kept account
UPDATE loans l
SET interest_account_id = kept.id
FROM (
    SELECT DISTINCT ON (user_id, name) id, user_id, name
    FROM accounts
    WHERE is_system = false
    ORDER BY user_id, name, created_at
) AS kept
JOIN accounts dup
    ON dup.user_id = kept.user_id
   AND dup.name    = kept.name
   AND dup.id     != kept.id
   AND dup.is_system = false
WHERE l.interest_account_id = dup.id;

-- 5. Delete the duplicate (newer) accounts
DELETE FROM accounts
WHERE is_system = false
  AND id NOT IN (
      SELECT DISTINCT ON (user_id, name) id
      FROM accounts
      WHERE is_system = false
      ORDER BY user_id, name, created_at
  );

-- 6. Now the data is clean — create the unique index
CREATE UNIQUE INDEX uq_user_account_name_per_user
    ON accounts (user_id, name)
    WHERE is_system = false;
