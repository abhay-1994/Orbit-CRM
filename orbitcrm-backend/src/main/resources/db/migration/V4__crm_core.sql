-- Phase 3: core CRM records - companies, contacts, leads, deals - plus pipelines and stage history.
-- Every record table carries the standard engine columns (id, tenant_id, record_number, owner_id,
-- created_by, updated_by, created_at, updated_at, deleted_at, version, tags, custom_fields).

-- ---------------------------------------------------------------------------
-- Companies (customer organizations; "Organization" is reserved for the tenant concept)
-- ---------------------------------------------------------------------------
CREATE TABLE companies (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid         NOT NULL REFERENCES tenants (id),
    record_number        bigint       NOT NULL,
    owner_id             uuid         NOT NULL,
    created_by           uuid,
    updated_by           uuid,
    created_at           timestamptz  NOT NULL,
    updated_at           timestamptz  NOT NULL,
    deleted_at           timestamptz,
    version              bigint       NOT NULL DEFAULT 0,
    tags                 text[],
    custom_fields        jsonb        NOT NULL DEFAULT '{}',
    name                 varchar(255) NOT NULL,
    industry             varchar(64),
    company_type         varchar(64),
    website              varchar(500),
    phone                varchar(40),
    email                varchar(254),
    employees            bigint,
    annual_revenue       numeric(19, 2),
    parent_company_id    uuid,
    billing_street       varchar(255),
    billing_city         varchar(120),
    billing_state        varchar(120),
    billing_postal_code  varchar(20),
    billing_country      varchar(120),
    shipping_street      varchar(255),
    shipping_city        varchar(120),
    shipping_state       varchar(120),
    shipping_postal_code varchar(20),
    shipping_country     varchar(120),
    description          text,
    CONSTRAINT uq_companies_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_companies_number UNIQUE (tenant_id, record_number),
    CONSTRAINT fk_companies_owner FOREIGN KEY (tenant_id, owner_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_companies_parent FOREIGN KEY (tenant_id, parent_company_id) REFERENCES companies (tenant_id, id)
);
CREATE INDEX ix_companies_owner ON companies (tenant_id, owner_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_companies_created ON companies (tenant_id, created_at DESC);
CREATE INDEX ix_companies_name_trgm ON companies USING gin (name gin_trgm_ops);
CREATE INDEX ix_companies_tags ON companies USING gin (tags);

-- ---------------------------------------------------------------------------
-- Contacts
-- ---------------------------------------------------------------------------
CREATE TABLE contacts (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid         NOT NULL REFERENCES tenants (id),
    record_number       bigint       NOT NULL,
    owner_id            uuid         NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    deleted_at          timestamptz,
    version             bigint       NOT NULL DEFAULT 0,
    tags                text[],
    custom_fields       jsonb        NOT NULL DEFAULT '{}',
    first_name          varchar(80),
    last_name           varchar(80)  NOT NULL,
    email               varchar(254),
    phone               varchar(40),
    mobile              varchar(40),
    job_title           varchar(120),
    department          varchar(120),
    company_id          uuid,
    reports_to_id       uuid,
    lead_source         varchar(64),
    mailing_street      varchar(255),
    mailing_city        varchar(120),
    mailing_state       varchar(120),
    mailing_postal_code varchar(20),
    mailing_country     varchar(120),
    birthday            date,
    email_opt_out       boolean      DEFAULT false,
    description         text,
    CONSTRAINT uq_contacts_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_contacts_number UNIQUE (tenant_id, record_number),
    CONSTRAINT fk_contacts_owner FOREIGN KEY (tenant_id, owner_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_contacts_company FOREIGN KEY (tenant_id, company_id) REFERENCES companies (tenant_id, id),
    CONSTRAINT fk_contacts_reports_to FOREIGN KEY (tenant_id, reports_to_id) REFERENCES contacts (tenant_id, id)
);
CREATE INDEX ix_contacts_owner ON contacts (tenant_id, owner_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_contacts_created ON contacts (tenant_id, created_at DESC);
CREATE INDEX ix_contacts_company ON contacts (tenant_id, company_id);
CREATE INDEX ix_contacts_email ON contacts (tenant_id, lower(email));
CREATE INDEX ix_contacts_last_name_trgm ON contacts USING gin (last_name gin_trgm_ops);
CREATE INDEX ix_contacts_tags ON contacts USING gin (tags);

-- ---------------------------------------------------------------------------
-- Pipelines and stages (tenant configuration, managed through the pipelines API)
-- ---------------------------------------------------------------------------
CREATE TABLE pipelines (
    id         uuid PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenants (id),
    name       varchar(120) NOT NULL,
    is_default boolean      NOT NULL DEFAULT false,
    position   integer      NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_pipelines_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX ux_pipelines_name ON pipelines (tenant_id, lower(name));
CREATE UNIQUE INDEX ux_pipelines_default ON pipelines (tenant_id) WHERE is_default;

CREATE TABLE pipeline_stages (
    id          uuid PRIMARY KEY,
    tenant_id   uuid          NOT NULL REFERENCES tenants (id),
    pipeline_id uuid          NOT NULL,
    name        varchar(120)  NOT NULL,
    position    integer       NOT NULL,
    probability numeric(5, 2) NOT NULL CHECK (probability BETWEEN 0 AND 100),
    stage_type  varchar(10)   NOT NULL CHECK (stage_type IN ('OPEN', 'WON', 'LOST')),
    active      boolean       NOT NULL DEFAULT true,
    created_at  timestamptz   NOT NULL,
    updated_at  timestamptz   NOT NULL,
    version     bigint        NOT NULL DEFAULT 0,
    CONSTRAINT uq_pipeline_stages_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_pipeline_stages_pipeline FOREIGN KEY (tenant_id, pipeline_id) REFERENCES pipelines (tenant_id, id)
);
CREATE INDEX ix_pipeline_stages_pipeline ON pipeline_stages (tenant_id, pipeline_id, position);

-- ---------------------------------------------------------------------------
-- Leads
-- ---------------------------------------------------------------------------
CREATE TABLE leads (
    id                   uuid PRIMARY KEY,
    tenant_id            uuid         NOT NULL REFERENCES tenants (id),
    record_number        bigint       NOT NULL,
    owner_id             uuid         NOT NULL,
    created_by           uuid,
    updated_by           uuid,
    created_at           timestamptz  NOT NULL,
    updated_at           timestamptz  NOT NULL,
    deleted_at           timestamptz,
    version              bigint       NOT NULL DEFAULT 0,
    tags                 text[],
    custom_fields        jsonb        NOT NULL DEFAULT '{}',
    first_name           varchar(80),
    last_name            varchar(80)  NOT NULL,
    company_name         varchar(255),
    email                varchar(254),
    phone                varchar(40),
    mobile               varchar(40),
    website              varchar(500),
    job_title            varchar(120),
    lead_source          varchar(64),
    status               varchar(64)  NOT NULL,
    industry             varchar(64),
    rating               varchar(64),
    street               varchar(255),
    city                 varchar(120),
    state                varchar(120),
    postal_code          varchar(20),
    country              varchar(120),
    email_opt_out        boolean      DEFAULT false,
    description          text,
    converted_at         timestamptz,
    converted_contact_id uuid,
    converted_company_id uuid,
    converted_deal_id    uuid,
    CONSTRAINT uq_leads_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_leads_number UNIQUE (tenant_id, record_number),
    CONSTRAINT fk_leads_owner FOREIGN KEY (tenant_id, owner_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_leads_contact FOREIGN KEY (tenant_id, converted_contact_id) REFERENCES contacts (tenant_id, id),
    CONSTRAINT fk_leads_company FOREIGN KEY (tenant_id, converted_company_id) REFERENCES companies (tenant_id, id)
);
CREATE INDEX ix_leads_owner ON leads (tenant_id, owner_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_leads_created ON leads (tenant_id, created_at DESC);
CREATE INDEX ix_leads_status ON leads (tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX ix_leads_email ON leads (tenant_id, lower(email));
CREATE INDEX ix_leads_last_name_trgm ON leads USING gin (last_name gin_trgm_ops);
CREATE INDEX ix_leads_company_trgm ON leads USING gin (company_name gin_trgm_ops);
CREATE INDEX ix_leads_tags ON leads USING gin (tags);

-- ---------------------------------------------------------------------------
-- Deals
-- ---------------------------------------------------------------------------
CREATE TABLE deals (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenants (id),
    record_number       bigint        NOT NULL,
    owner_id            uuid          NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL,
    deleted_at          timestamptz,
    version             bigint        NOT NULL DEFAULT 0,
    tags                text[],
    custom_fields       jsonb         NOT NULL DEFAULT '{}',
    name                varchar(255)  NOT NULL,
    company_id          uuid,
    contact_id          uuid,
    amount              numeric(19, 2),
    currency            varchar(3)    NOT NULL,
    pipeline_id         uuid          NOT NULL,
    stage_id            uuid          NOT NULL,
    probability         numeric(5, 2),
    weighted_amount     numeric(19, 2) GENERATED ALWAYS AS
                            (round(coalesce(amount, 0) * coalesce(probability, 0) / 100, 2)) STORED,
    expected_close_date date,
    lead_source         varchar(64),
    next_step           varchar(255),
    status              varchar(10)   NOT NULL CHECK (status IN ('open', 'won', 'lost')),
    closed_at           timestamptz,
    lost_reason         varchar(64),
    lead_id             uuid,
    description         text,
    CONSTRAINT uq_deals_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_deals_number UNIQUE (tenant_id, record_number),
    CONSTRAINT fk_deals_owner FOREIGN KEY (tenant_id, owner_id) REFERENCES users (tenant_id, id),
    CONSTRAINT fk_deals_company FOREIGN KEY (tenant_id, company_id) REFERENCES companies (tenant_id, id),
    CONSTRAINT fk_deals_contact FOREIGN KEY (tenant_id, contact_id) REFERENCES contacts (tenant_id, id),
    CONSTRAINT fk_deals_pipeline FOREIGN KEY (tenant_id, pipeline_id) REFERENCES pipelines (tenant_id, id),
    CONSTRAINT fk_deals_stage FOREIGN KEY (tenant_id, stage_id) REFERENCES pipeline_stages (tenant_id, id),
    CONSTRAINT fk_deals_lead FOREIGN KEY (tenant_id, lead_id) REFERENCES leads (tenant_id, id)
);
CREATE INDEX ix_deals_owner ON deals (tenant_id, owner_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_deals_created ON deals (tenant_id, created_at DESC);
CREATE INDEX ix_deals_company ON deals (tenant_id, company_id);
CREATE INDEX ix_deals_contact ON deals (tenant_id, contact_id);
CREATE INDEX ix_deals_stage ON deals (tenant_id, pipeline_id, stage_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_deals_status_close ON deals (tenant_id, status, expected_close_date) WHERE deleted_at IS NULL;
CREATE INDEX ix_deals_name_trgm ON deals USING gin (name gin_trgm_ops);
CREATE INDEX ix_deals_tags ON deals USING gin (tags);

ALTER TABLE leads ADD CONSTRAINT fk_leads_deal
    FOREIGN KEY (tenant_id, converted_deal_id) REFERENCES deals (tenant_id, id);

-- Every stage move is recorded: time-in-stage, velocity and funnel conversion cannot be reconstructed later.
CREATE TABLE deal_stage_history (
    id                     uuid PRIMARY KEY,
    tenant_id              uuid           NOT NULL REFERENCES tenants (id),
    deal_id                uuid           NOT NULL,
    pipeline_id            uuid           NOT NULL,
    from_stage_id          uuid,
    to_stage_id            uuid           NOT NULL,
    amount                 numeric(19, 2),
    changed_by             uuid,
    changed_at             timestamptz    NOT NULL,
    seconds_in_prior_stage bigint,
    CONSTRAINT fk_stage_history_deal FOREIGN KEY (tenant_id, deal_id) REFERENCES deals (tenant_id, id)
);
CREATE INDEX ix_stage_history_deal ON deal_stage_history (tenant_id, deal_id, changed_at);
CREATE INDEX ix_stage_history_stage ON deal_stage_history (tenant_id, to_stage_id, changed_at);

-- ---------------------------------------------------------------------------
-- Row-level security for every new table
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['companies', 'contacts', 'pipelines', 'pipeline_stages', 'leads', 'deals',
                             'deal_stage_history']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I '
                       'USING (app_rls_bypass() OR tenant_id = app_current_tenant()) '
                       'WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant())', t);
    END LOOP;
END
$$;
