-- Test-only module exercising every field type of the record engine.
CREATE TABLE widgets (
    id            uuid PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenants (id),
    record_number bigint       NOT NULL,
    owner_id      uuid         NOT NULL,
    created_by    uuid,
    updated_by    uuid,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    deleted_at    timestamptz,
    version       bigint       NOT NULL DEFAULT 0,
    tags          text[],
    custom_fields jsonb        NOT NULL DEFAULT '{}',
    name          varchar(255) NOT NULL,
    notes         text,
    email         varchar(254),
    phone         varchar(40),
    website       varchar(500),
    quantity      bigint,
    weight        numeric(19, 4),
    price         numeric(19, 2),
    discount      numeric(5, 2),
    launch_date   date,
    last_seen_at  timestamptz,
    active        boolean,
    status        varchar(64),
    colors        text[],
    reviewer_ids  uuid[],
    parent_id     uuid,
    CONSTRAINT uq_widgets_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_widgets_owner FOREIGN KEY (tenant_id, owner_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_widgets_parent FOREIGN KEY (tenant_id, parent_id) REFERENCES widgets (tenant_id, id)
);

ALTER TABLE widgets ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON widgets
    USING (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());
