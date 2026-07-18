-- Adds optimistic locking version columns to entities that were missing them.
-- @Version on the Java side requires a matching column in the DB.
ALTER TABLE debt_records  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE people        ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE subscriptions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
