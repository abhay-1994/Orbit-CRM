-- Phase 2: platform kernel - picklists, record numbering, audit log, search index.

-- ---------------------------------------------------------------------------
-- Tenant-editable picklist values. Records store value_key; labels can be renamed freely.
-- ---------------------------------------------------------------------------
CREATE TABLE picklist_values (
    id           uuid PRIMARY KEY,
    tenant_id    uuid         NOT NULL REFERENCES tenants (id),
    picklist_key varchar(64)  NOT NULL,
    value_key    varchar(64)  NOT NULL,
    label        varchar(120) NOT NULL,
    color        varchar(20),
    position     integer      NOT NULL,
    active       boolean      NOT NULL DEFAULT true,
    is_default   boolean      NOT NULL DEFAULT false,
    system       boolean      NOT NULL DEFAULT false,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_picklist_value UNIQUE (tenant_id, picklist_key, value_key)
);
CREATE INDEX ix_picklist_values_key ON picklist_values (tenant_id, picklist_key, position);

ALTER TABLE picklist_values ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON picklist_values
    USING (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());

-- ---------------------------------------------------------------------------
-- Per-tenant, per-module counters for human-readable record numbers (LEAD-1042).
-- ---------------------------------------------------------------------------
CREATE TABLE record_sequences (
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    module_key varchar(64) NOT NULL,
    next_value bigint      NOT NULL,
    PRIMARY KEY (tenant_id, module_key)
);

ALTER TABLE record_sequences ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON record_sequences
    USING (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());

-- ---------------------------------------------------------------------------
-- Audit log (PRD section 37). Append-only: the application role may only INSERT and SELECT, and a
-- trigger rejects UPDATE/DELETE even for the owner. Partitioned by year so old years can be detached
-- and archived without rewriting the table.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id           uuid        NOT NULL,
    tenant_id    uuid        NOT NULL,
    occurred_at  timestamptz NOT NULL,
    actor_id     uuid,
    actor_name   varchar(200),
    action       varchar(40) NOT NULL,
    module_key   varchar(64),
    record_id    uuid,
    record_label varchar(300),
    changes      jsonb,
    metadata     jsonb,
    ip_address   varchar(64),
    request_id   varchar(64),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE TABLE audit_logs_2026 PARTITION OF audit_logs FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');
CREATE TABLE audit_logs_2027 PARTITION OF audit_logs FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');
CREATE TABLE audit_logs_2028 PARTITION OF audit_logs FOR VALUES FROM ('2028-01-01') TO ('2029-01-01');
CREATE TABLE audit_logs_2029 PARTITION OF audit_logs FOR VALUES FROM ('2029-01-01') TO ('2030-01-01');
CREATE TABLE audit_logs_2030 PARTITION OF audit_logs FOR VALUES FROM ('2030-01-01') TO ('2031-01-01');
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

CREATE INDEX ix_audit_logs_record ON audit_logs (tenant_id, module_key, record_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_tenant_time ON audit_logs (tenant_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_actor ON audit_logs (tenant_id, actor_id, occurred_at DESC);

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_logs
    USING (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());

CREATE FUNCTION forbid_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END
$$;
CREATE TRIGGER audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION forbid_audit_mutation();

DO $$
BEGIN
    EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM %I', '${app_db_user}');
    -- Partitions are only reachable through the parent (whose RLS policy applies).
    EXECUTE format('REVOKE ALL ON audit_logs_2026, audit_logs_2027, audit_logs_2028, audit_logs_2029, '
                   'audit_logs_2030, audit_logs_default FROM %I', '${app_db_user}');
END
$$;

-- ---------------------------------------------------------------------------
-- Global search index (PRD section 32): one denormalised row per record, maintained by the record
-- engine in the same transaction as the change. Trigram indexes make substring search on names,
-- emails and phone digits fast; the table can later be mirrored to OpenSearch without API changes.
-- ---------------------------------------------------------------------------
CREATE TABLE search_index (
    tenant_id     uuid         NOT NULL,
    module_key    varchar(64)  NOT NULL,
    record_id     uuid         NOT NULL,
    owner_id      uuid,
    record_number varchar(40),
    title         varchar(500) NOT NULL,
    subtitle      varchar(500),
    content       text         NOT NULL,
    updated_at    timestamptz  NOT NULL,
    PRIMARY KEY (tenant_id, module_key, record_id)
);
CREATE INDEX ix_search_index_content_trgm ON search_index USING gin (content gin_trgm_ops);
CREATE INDEX ix_search_index_title_trgm ON search_index USING gin (title gin_trgm_ops);

ALTER TABLE search_index ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON search_index
    USING (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());
