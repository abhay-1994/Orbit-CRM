-- Phase 6: saved list views and CSV import jobs.

-- Saved filters/views per module (PRD section 33: "Filters can be saved. Example: My Open Deals").
CREATE TABLE saved_views (
    id            uuid PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenants (id),
    module_key    varchar(64)  NOT NULL,
    name          varchar(120) NOT NULL,
    owner_user_id uuid,
    visibility    varchar(10)  NOT NULL CHECK (visibility IN ('PRIVATE', 'SHARED')),
    system        boolean      NOT NULL DEFAULT false,
    filter        jsonb,
    sort          jsonb,
    columns       jsonb,
    position      integer      NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL DEFAULT 0
);
CREATE INDEX ix_saved_views_module ON saved_views (tenant_id, module_key, position);

-- Import jobs: upload -> map -> preview -> run -> result (PRD section 38).
CREATE TABLE import_jobs (
    id             uuid PRIMARY KEY,
    tenant_id      uuid         NOT NULL REFERENCES tenants (id),
    module_key     varchar(64)  NOT NULL,
    file_name      varchar(255) NOT NULL,
    storage_key    varchar(500) NOT NULL,
    status         varchar(20)  NOT NULL,
    columns        jsonb        NOT NULL,
    total_rows     integer      NOT NULL DEFAULT 0,
    processed_rows integer      NOT NULL DEFAULT 0,
    created_count  integer      NOT NULL DEFAULT 0,
    updated_count  integer      NOT NULL DEFAULT 0,
    skipped_count  integer      NOT NULL DEFAULT 0,
    failed_count   integer      NOT NULL DEFAULT 0,
    mapping        jsonb,
    options        jsonb,
    errors         jsonb,
    created_by     uuid         NOT NULL,
    created_at     timestamptz  NOT NULL,
    started_at     timestamptz,
    finished_at    timestamptz,
    updated_at     timestamptz  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0
);
CREATE INDEX ix_import_jobs_tenant ON import_jobs (tenant_id, created_at DESC);

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['saved_views', 'import_jobs']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I '
                       'USING (app_rls_bypass() OR tenant_id = app_current_tenant()) '
                       'WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant())', t);
    END LOOP;
END
$$;
