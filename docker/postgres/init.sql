-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Set default timezone for this database
ALTER DATABASE walletwizzard SET timezone TO 'UTC';

-- Application role: used by Spring Boot at runtime (RLS is enforced on this role).
-- The owner role (walletwizzard / POSTGRES_USER) is used only by Flyway for migrations
-- and bypasses RLS as the table owner — which is intentional.
CREATE ROLE walletwizzard_app WITH LOGIN PASSWORD 'walletwizzard_app_dev';
GRANT CONNECT ON DATABASE walletwizzard TO walletwizzard_app;
GRANT USAGE ON SCHEMA public TO walletwizzard_app;

-- Grant walletwizzard_app access to all tables and sequences that Flyway creates
-- (Flyway runs as walletwizzard, so we set default privileges for that role)
ALTER DEFAULT PRIVILEGES FOR ROLE walletwizzard IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO walletwizzard_app;

ALTER DEFAULT PRIVILEGES FOR ROLE walletwizzard IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO walletwizzard_app;
