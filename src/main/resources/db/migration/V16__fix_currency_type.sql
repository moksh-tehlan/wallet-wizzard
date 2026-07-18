-- CHAR(3) is bpchar in PostgreSQL (Types#CHAR), but Hibernate maps String to VARCHAR.
-- Align the column type so Hibernate schema validation passes.
ALTER TABLE users ALTER COLUMN currency TYPE VARCHAR(3);
