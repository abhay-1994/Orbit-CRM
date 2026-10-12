-- OrbitCRM baseline: extensions and the restricted application role.
--
-- Migrations run as the schema owner. The application connects as ${app_db_user}, which owns
-- nothing and cannot bypass row-level security, so tenant isolation is enforced by the database
-- even if application code forgets a tenant filter.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_db_user}') THEN
        EXECUTE format(
            'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS',
            '${app_db_user}', '${app_db_password}');
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), '${app_db_user}');
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', '${app_db_user}');
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
                   '${app_db_user}');
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I',
                   '${app_db_user}');
END
$$;
