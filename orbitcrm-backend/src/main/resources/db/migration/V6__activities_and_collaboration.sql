-- Phase 5: activities & calendar, notes, attachments, notifications, and the scheduler lock table.

-- ---------------------------------------------------------------------------
-- Activities (tasks, calls, meetings, events, reminders) - a record module on the generic engine
-- ---------------------------------------------------------------------------
CREATE TABLE activities (
    id               uuid PRIMARY KEY,
    tenant_id        uuid         NOT NULL REFERENCES tenants (id),
    record_number    bigint       NOT NULL,
    owner_id         uuid         NOT NULL,
    created_by       uuid,
    updated_by       uuid,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    deleted_at       timestamptz,
    version          bigint       NOT NULL DEFAULT 0,
    tags             text[],
    custom_fields    jsonb        NOT NULL DEFAULT '{}',
    subject          varchar(255) NOT NULL,
    type             varchar(20)  NOT NULL,
    status           varchar(20)  NOT NULL,
    priority         varchar(64),
    start_at         timestamptz,
    end_at           timestamptz,
    all_day          boolean      DEFAULT false,
    location         varchar(255),
    related_module   varchar(64),
    related_id       uuid,
    participant_ids  uuid[],
    reminder_at      timestamptz,
    reminder_sent_at timestamptz,
    completed_at     timestamptz,
    description      text,
    CONSTRAINT uq_activities_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_activities_number UNIQUE (tenant_id, record_number),
    CONSTRAINT fk_activities_owner FOREIGN KEY (tenant_id, owner_id) REFERENCES users (tenant_id, id),
    CONSTRAINT ck_activities_related CHECK ((related_module IS NULL) = (related_id IS NULL)),
    CONSTRAINT ck_activities_time CHECK (end_at IS NULL OR start_at IS NULL OR end_at >= start_at)
);
CREATE INDEX ix_activities_owner_start ON activities (tenant_id, owner_id, start_at) WHERE deleted_at IS NULL;
CREATE INDEX ix_activities_related ON activities (tenant_id, related_module, related_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_activities_participants ON activities USING gin (participant_ids);
CREATE INDEX ix_activities_created ON activities (tenant_id, created_at DESC);
-- Cross-tenant scan of due reminders by the reminder job.
CREATE INDEX ix_activities_due_reminders ON activities (reminder_at)
    WHERE reminder_sent_at IS NULL AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- Notes on any record
-- ---------------------------------------------------------------------------
CREATE TABLE notes (
    id         uuid PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    module_key varchar(64) NOT NULL,
    record_id  uuid        NOT NULL,
    body       text        NOT NULL,
    pinned     boolean     NOT NULL DEFAULT false,
    created_by uuid        NOT NULL,
    updated_by uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz,
    version    bigint      NOT NULL DEFAULT 0
);
CREATE INDEX ix_notes_record ON notes (tenant_id, module_key, record_id, created_at DESC) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- File attachments (content in object storage, metadata here)
-- ---------------------------------------------------------------------------
CREATE TABLE attachments (
    id           uuid PRIMARY KEY,
    tenant_id    uuid         NOT NULL REFERENCES tenants (id),
    module_key   varchar(64)  NOT NULL,
    record_id    uuid         NOT NULL,
    file_name    varchar(255) NOT NULL,
    content_type varchar(120) NOT NULL,
    size_bytes   bigint       NOT NULL,
    storage_key  varchar(500) NOT NULL,
    sha256       varchar(64)  NOT NULL,
    uploaded_by  uuid         NOT NULL,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    deleted_at   timestamptz,
    version      bigint       NOT NULL DEFAULT 0
);
CREATE INDEX ix_attachments_record ON attachments (tenant_id, module_key, record_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- In-app notifications
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id         uuid PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenants (id),
    user_id    uuid         NOT NULL,
    type       varchar(40)  NOT NULL,
    title      varchar(300) NOT NULL,
    body       text,
    module_key varchar(64),
    record_id  uuid,
    actor_id   uuid,
    actor_name varchar(200),
    read_at    timestamptz,
    created_at timestamptz  NOT NULL
);
CREATE INDEX ix_notifications_user ON notifications (tenant_id, user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (tenant_id, user_id) WHERE read_at IS NULL;

DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['activities', 'notes', 'attachments', 'notifications']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I '
                       'USING (app_rls_bypass() OR tenant_id = app_current_tenant()) '
                       'WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant())', t);
    END LOOP;
END
$$;

-- ---------------------------------------------------------------------------
-- ShedLock: ensures each scheduled job runs on one instance at a time (platform table, no tenant data)
-- ---------------------------------------------------------------------------
CREATE TABLE shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp    NOT NULL,
    locked_at  timestamp    NOT NULL,
    locked_by  varchar(255) NOT NULL
);
