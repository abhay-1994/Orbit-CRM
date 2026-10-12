-- Phase 1: tenants, identity, access control, sessions.

-- ---------------------------------------------------------------------------
-- Row-level security helpers. The application stamps app.tenant_id on every connection checkout;
-- app.rls_bypass is only switched on for explicit cross-tenant platform jobs.
-- ---------------------------------------------------------------------------
CREATE FUNCTION app_current_tenant() RETURNS uuid
    LANGUAGE sql STABLE AS
$$ SELECT nullif(current_setting('app.tenant_id', true), '')::uuid $$;

CREATE FUNCTION app_rls_bypass() RETURNS boolean
    LANGUAGE sql STABLE AS
$$ SELECT coalesce(current_setting('app.rls_bypass', true), '') = 'on' $$;

-- ---------------------------------------------------------------------------
-- Tenants (no RLS: resolved by slug before authentication; only the current tenant is exposed by the API)
-- ---------------------------------------------------------------------------
CREATE TABLE tenants (
    id                      uuid PRIMARY KEY,
    slug                    varchar(40)  NOT NULL UNIQUE,
    name                    varchar(120) NOT NULL,
    status                  varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    timezone                varchar(64)  NOT NULL,
    currency                varchar(3)   NOT NULL,
    locale                  varchar(35)  NOT NULL,
    fiscal_year_start_month integer      NOT NULL DEFAULT 4 CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    created_at              timestamptz  NOT NULL,
    updated_at              timestamptz  NOT NULL,
    version                 bigint       NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- Roles and permissions
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id            uuid PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenants (id),
    name          varchar(80)  NOT NULL,
    description   varchar(500),
    administrator boolean      NOT NULL DEFAULT false,
    system        boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_roles_tenant_id UNIQUE (tenant_id, id)
);
CREATE UNIQUE INDEX ux_roles_tenant_name ON roles (tenant_id, lower(name));

CREATE TABLE role_permissions (
    role_id  uuid        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    resource varchar(64) NOT NULL,
    action   varchar(20) NOT NULL,
    scope    varchar(10) NOT NULL,
    PRIMARY KEY (role_id, resource, action)
);

-- ---------------------------------------------------------------------------
-- Teams (hierarchical; a parent team acts as a department for TEAM scope)
-- ---------------------------------------------------------------------------
CREATE TABLE teams (
    id          uuid PRIMARY KEY,
    tenant_id   uuid         NOT NULL REFERENCES tenants (id),
    name        varchar(120) NOT NULL,
    description varchar(500),
    parent_id   uuid,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_teams_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_teams_parent FOREIGN KEY (tenant_id, parent_id) REFERENCES teams (tenant_id, id)
);
CREATE UNIQUE INDEX ux_teams_tenant_name ON teams (tenant_id, lower(name));

-- ---------------------------------------------------------------------------
-- Users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                    uuid PRIMARY KEY,
    tenant_id             uuid         NOT NULL REFERENCES tenants (id),
    email                 varchar(254) NOT NULL,
    password_hash         varchar(255),
    first_name            varchar(80)  NOT NULL,
    last_name             varchar(80)  NOT NULL,
    status                varchar(20)  NOT NULL,
    role_id               uuid         NOT NULL,
    team_id               uuid,
    job_title             varchar(120),
    phone                 varchar(40),
    timezone              varchar(64),
    locale                varchar(35),
    mfa_enabled           boolean      NOT NULL DEFAULT false,
    mfa_secret            varchar(255),
    mfa_pending_secret    varchar(255),
    mfa_last_used_step    bigint,
    failed_login_attempts integer      NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    last_login_at         timestamptz,
    password_changed_at   timestamptz,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    version               bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT fk_users_role FOREIGN KEY (tenant_id, role_id) REFERENCES roles (tenant_id, id),
    CONSTRAINT fk_users_team FOREIGN KEY (tenant_id, team_id) REFERENCES teams (tenant_id, id)
);
CREATE INDEX ix_users_team ON users (tenant_id, team_id);
CREATE INDEX ix_users_role ON users (tenant_id, role_id);

-- ---------------------------------------------------------------------------
-- Login sessions and rotating refresh tokens. Not under RLS: they are located by token hash before a
-- tenant is known and are only ever touched by the authentication code with explicit filters.
-- ---------------------------------------------------------------------------
CREATE TABLE auth_sessions (
    id             uuid PRIMARY KEY,
    tenant_id      uuid        NOT NULL REFERENCES tenants (id),
    user_id        uuid        NOT NULL,
    last_used_at   timestamptz NOT NULL,
    expires_at     timestamptz NOT NULL,
    revoked_at     timestamptz,
    revoked_reason varchar(40),
    user_agent     varchar(400),
    ip_address     varchar(64),
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    version        bigint      NOT NULL DEFAULT 0,
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id)
);
CREATE INDEX ix_auth_sessions_user_active ON auth_sessions (tenant_id, user_id) WHERE revoked_at IS NULL;

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY,
    session_id uuid        NOT NULL REFERENCES auth_sessions (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL DEFAULT 0
);
CREATE INDEX ix_refresh_tokens_session ON refresh_tokens (session_id);

-- Single-use invitation and password-reset tokens.
CREATE TABLE user_tokens (
    id         uuid PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    user_id    uuid        NOT NULL,
    purpose    varchar(20) NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_tokens_user FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id)
);
CREATE INDEX ix_user_tokens_user ON user_tokens (tenant_id, user_id, purpose);

-- ---------------------------------------------------------------------------
-- Row-level security
-- ---------------------------------------------------------------------------
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON roles
    USING (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());

-- role_permissions has no tenant column: visibility follows the (RLS-protected) parent role.
ALTER TABLE role_permissions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON role_permissions
    USING (EXISTS (SELECT 1 FROM roles r WHERE r.id = role_id))
    WITH CHECK (EXISTS (SELECT 1 FROM roles r WHERE r.id = role_id));

ALTER TABLE teams ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON teams
    USING (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON users
    USING (app_rls_bypass() OR tenant_id = app_current_tenant())
    WITH CHECK (app_rls_bypass() OR tenant_id = app_current_tenant());
