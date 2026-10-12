-- Phase 4: lead conversion history (PRD section 10: "conversion history must be preserved").
CREATE TABLE lead_conversions (
    id              uuid PRIMARY KEY,
    tenant_id       uuid        NOT NULL REFERENCES tenants (id),
    lead_id         uuid        NOT NULL,
    contact_id      uuid,
    company_id      uuid,
    deal_id         uuid,
    contact_created boolean     NOT NULL,
    company_created boolean     NOT NULL,
    deal_created    boolean     NOT NULL,
    converted_by    uuid        NOT NULL,
    converted_at    timestamptz NOT NULL,
    lead_snapshot   jsonb       NOT NULL,
    CONSTRAINT uq_lead_conversions_lead UNIQUE (tenant_id, lead_id),
    CONSTRAINT fk_lead_conversions_lead FOREIGN KEY (tenant_id, lead_id) REFERENCES leads (tenant_id, id),
    CONSTRAINT fk_lead_conversions_contact FOREIGN KEY (tenant_id, contact_id) REFERENCES contacts (tenant_id, id),
    CONSTRAINT fk_lead_conversions_company FOREIGN KEY (tenant_id, company_id) REFERENCES companies (tenant_id, id),
    CONSTRAINT fk_lead_conversions_deal FOREIGN KEY (tenant_id, deal_id) REFERENCES deals (tenant_id, id)
);

ALTER TABLE lead_conversions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON lead_conversions
    USING (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());
