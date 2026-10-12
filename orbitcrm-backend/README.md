# OrbitCRM Backend

Multi-tenant CRM platform API built with **Java 21, Spring Boot 4.1, PostgreSQL 18** as a modular monolith.
It implements the MVP from the OrbitCRM PRD: authentication and tenancy, users/teams/roles with OWN/TEAM/ALL
permissions, leads, contacts, companies, deals and pipelines, lead conversion, activities and calendar,
notes, attachments, timeline, notifications, in-app chat with Teams-style presence, global search, saved views,
CSV import/export, dashboard, and an immutable audit log.

## Quick start

```bash
docker compose up -d                                   # PostgreSQL 18 + Mailpit (email UI on :8025)
mvn spring-boot:run -Dspring-boot.run.profiles=local   # API on http://localhost:8080
```

On Windows PowerShell, quote the `-D` argument (PowerShell otherwise splits it at the first dot):

```powershell
docker compose up -d
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

* API explorer: http://localhost:8080/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`)
* Health: http://localhost:8080/actuator/health, metrics: `/actuator/prometheus`
* Everything in a container: `docker compose --profile app up -d --build`

Create a workspace:

```bash
curl -s localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{
  "companyName": "Acme India", "tenantSlug": "acme", "firstName": "Asha", "lastName": "Rao",
  "email": "asha@acme.test", "password": "Str0ng-Passw0rd!"}'
```

The response contains a 15-minute `accessToken` (send as `Authorization: Bearer ...`) and a `refreshToken`,
which is also set as an HttpOnly `orbitcrm_refresh` cookie. Refresh with `POST /api/v1/auth/refresh`, either:

* **Non-browser clients:** send the body `{"refreshToken": "..."}`.
* **Browser apps:** rely on the cookie and send the header `X-Requested-With: fetch`. Don't store the body token
  in JavaScript-accessible storage; the HttpOnly cookie is what keeps it out of reach of XSS.

Every refresh returns a new refresh token, and the old one stops working. `POST /api/v1/auth/logout` accepts
the same two forms.

## Tests

```bash
mvn test        # 80 tests; needs Docker (Testcontainers starts PostgreSQL 18)
```

The suite runs against a real database with the same restricted application role as production, so
row-level security, migrations and SQL are exercised on every run. `ModularityTests` fails the build if a
module reaches into another module's internals or modules form a cycle.

## Architecture

```
React SPA ──HTTPS/JSON──▶ Spring Boot API ──▶ PostgreSQL (RLS)   + object storage (local / S3)
                              │
     identity · tenant · metadata · record engine · audit · search · notification
     companies · contacts · leads · deals · activities · notes · attachments · timeline
     views · import/export · dashboard · chat (SSE + PostgreSQL LISTEN/NOTIFY)
```

**Package structure.** Code is grouped by module first (`com.orbitcrm.<module>`), then by layer inside each
module:

| Package | Contents |
|---|---|
| `controller` | REST controllers |
| `service` | Service interfaces and services, the module's API |
| `service.impl` | Service implementations |
| `repository` | Spring Data / JDBC repositories |
| `entity` | JPA entities and embeddables |
| `dto` | Request, response and view records |
| `model` | Domain enums and value types (e.g. `FilterNode`, `FieldDefinition`, `StageType`) |
| `event` | Domain events |
| `exception` | Exceptions |
| `config` | Spring configuration and module definitions (`ModuleDefinition` beans) |
| `hook` | `RecordHooks` implementations |
| `listener` | Event listeners |
| `job` | Scheduled jobs |
| `mapper` | Assemblers and mappers |
| `security` | Security infrastructure |
| `util` | Helpers |

Cross-cutting building blocks live in `com.orbitcrm.shared` (`dto`, `exception`, `entity`, `config`, `security`,
`tenant`, `service`, `util`, `web`).

A module exposes only the packages marked with `@NamedInterface` in their `package-info.java`, typically
`service`, `dto`, `model` and `event`. Everything else (`entity`, `repository`, `service.impl`, `controller`, ...)
is internal. `ModularityTests` fails the build if another module reaches into those internal packages.

```
com.orbitcrm.deal
├── config      DealModule, PipelineReferenceResolvers
├── controller  DealController, PipelineController
├── dto         PipelineView, StageView, PipelineBoard, BoardColumn, PipelineRequest, ...
├── entity      Pipeline, PipelineStage
├── hook        DealHooks
├── model       StageType
├── repository  PipelineRepository, PipelineStageRepository
├── service     PipelineService          (exposed)
└── service.impl PipelineServiceImpl     (internal)
```

**Tenant isolation, two layers.** Every tenant-owned table has `tenant_id`. Hibernate's `@TenantId` and
explicit SQL filters scope queries in the application, and PostgreSQL row-level security enforces the same
rule in the database: the API connects as `orbitcrm_app`, which owns nothing and cannot bypass RLS; every
pooled connection is stamped with the caller's tenant (`app.tenant_id`). With no tenant bound, tenant tables
return nothing. Composite foreign keys `(tenant_id, id)` make cross-tenant references impossible.

**Generic record engine.** Leads, contacts, companies, deals and activities are described by
`ModuleDefinition` metadata (fields, types, picklists, lookups). One engine provides CRUD, validation,
optimistic locking (`If-Match`/`version`), soft delete and restore, bulk operations, the filter language,
aggregation, audit entries, search indexing and domain events for every module. Module-specific rules live
in small `RecordHooks` (deal stage → probability/status/close date; converted leads are read-only).
New modules, and later tenant-defined custom fields and modules, reuse the whole stack.

**Filter language** (used by list views, saved views, the board, dashboards and exports):

```json
{"filter": {"and": [
   {"field": "status", "op": "eq", "value": "open"},
   {"field": "ownerId", "op": "in", "value": ["$me"]},
   {"field": "expectedCloseDate", "op": "between", "value": ["$startOfMonth", "$endOfMonth"]},
   {"or": [{"field": "amount", "op": "gte", "value": 1000000},
           {"field": "tags", "op": "contains", "value": ["strategic"]}]}]},
 "sort": [{"field": "amount", "direction": "desc"}], "page": 0, "size": 25}
```

Operators: `eq neq contains not_contains starts_with gt gte lt lte between in not_in contains_all is_empty
is_not_empty within_last_days within_next_days`. `GET /api/v1/metadata/modules/{module}` lists each field's
type, options and supported operators.

**Permissions.** Roles grant CREATE/READ/UPDATE/DELETE/EXPORT/IMPORT per module with scope OWN, TEAM
(the user's team and its sub-teams) or ALL. Scopes become SQL predicates on every read path; records a user
cannot see return 404, records they can see but not change return 403. Role changes apply immediately.

**Authentication.** HS256 access tokens (15 min) carry only user, tenant and session ids; permissions are
resolved server-side. Refresh tokens rotate on every use, are stored hashed, and reuse revokes the session.
Login lockout, IP throttling, TOTP two-factor, invitations, password reset, session management.

**In-app chat and presence.** Colleagues send each other direct messages, with Teams-style presence (module
`com.orbitcrm.chat`):

- **Live delivery.** Each browser tab keeps one server-sent event stream (`GET /api/v1/chat/stream`). It goes
  through the normal bearer-token security, so there is no separate authentication path.
- **Multi-instance fan-out.** Events travel between server instances over PostgreSQL `LISTEN/NOTIFY`, so no
  message broker is needed. `pg_notify` is transactional: recipients hear about a message only after it is
  committed.
- **Per-stream outbox.** Every stream has its own ordered outbox, so a slow browser never delays anyone else.
- **Recovery.** After a dropped stream or database blip, browsers reconnect and refetch to catch up.
- **Messages.** Sending is idempotent through a client-generated `clientId`, so a retry can never duplicate a
  message. Authors can edit and delete their own messages. Read receipts, typing indicators and unread counts
  are all included.
- **Presence: chosen or calendar-driven.**
  - Available, Busy, Do not disturb, Be right back, Away and Appear offline can be chosen.
  - "In a meeting" is automatic while a meeting activity you own or attend is in progress.
- **Presence: computed, never stored.** Each open tab has a row in `chat_connections`, kept fresh by a heartbeat.
  You are online while any of your rows is fresh, on any instance.
  - A tab that has seen no input for 5 minutes reports itself idle; when all your tabs are idle, you show as
    Away.
  - Closing a tab announces Offline within seconds. A crashed instance's rows expire after 75 seconds.
  - A ShedLock job sweeps expired rows and announces meetings that start or end.
- **Data model.** One conversation per pair of people. It is built so group chats can be added later. All chat
  tables use row-level security.

**Audit.** `audit_logs` is append-only (the app role has no UPDATE/DELETE; a trigger blocks it for owners),
partitioned by year, and records field-level diffs, logins, exports, imports and admin changes.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `ORBITCRM_DB_URL` | `jdbc:postgresql://localhost:5432/orbitcrm` | Database |
| `ORBITCRM_DB_ADMIN_USER` / `_PASSWORD` | `orbitcrm` / `orbitcrm` | Migration (schema owner) credentials |
| `ORBITCRM_DB_APP_USER` / `_PASSWORD` | `orbitcrm_app` / `orbitcrm_app_dev` | Restricted runtime role (created by migration if missing) |
| `ORBITCRM_JWT_SECRET` | dev value | Base64, ≥ 32 bytes. **Required in prod** |
| `ORBITCRM_ENCRYPTION_KEY` | dev value | Base64 32-byte AES key for secrets at rest. **Required in prod** |
| `ORBITCRM_CORS_ORIGINS` | `http://localhost:5173` | Frontend origin(s) |
| `ORBITCRM_FRONTEND_URL` | `http://localhost:5173` | Base URL for invite / reset links |
| `ORBITCRM_STORAGE_TYPE` | `local` | `local` or `s3` (AWS S3, MinIO, R2 via `ORBITCRM_S3_*`) |
| `SPRING_MAIL_HOST` / `_PORT` | unset | SMTP; without it emails are only logged |
| `ORBITCRM_RATE_LIMIT_USER` / `_IP` | 600 / 120 per minute | API rate limits |

Profiles: `local` (Mailpit, non-secure cookie, debug logs), `prod` (JSON logs, secrets required, Swagger off,
Actuator on port 8081).

## API map

| Area | Endpoints |
|---|---|
| Auth | `POST /api/v1/auth/{register,login,login/mfa,refresh,logout,password/forgot,password/reset,invitations/accept}`, `GET/PATCH /api/v1/auth/me`, MFA, sessions |
| Admin | `/api/v1/users`, `/api/v1/teams`, `/api/v1/roles`, `/api/v1/tenant`, `/api/v1/picklists`, `/api/v1/pipelines`, `/api/v1/audit-logs` |
| Records | `/api/v1/{leads,contacts,companies,deals,activities}`: list, `/search`, CRUD, `/{id}/restore`, `/bulk-update`, `/bulk-delete`, `/{id}/history` |
| CRM | `POST /api/v1/leads/{id}/convert`, `GET /api/v1/leads/{id}/conversion-preview`, `GET/POST /api/v1/deals/board`, `GET /api/v1/deals/{id}/stage-history`, `GET /api/v1/calendar` |
| Collaboration | `/api/v1/{module}/{id}/notes`, `/api/v1/{module}/{id}/attachments`, `/api/v1/{module}/{id}/timeline`, `/api/v1/notifications` (+ SSE `/stream`) |
| Chat | `/api/v1/chat`: SSE `/stream` (+ `DELETE /stream/{id}`), `/conversations`, `/conversations/{id}/messages` (keyset `?before=`), `PATCH/DELETE /messages/{id}`, `/conversations/{id}/{read,typing}`, `/unread-count`, `/people`, `GET/PUT /presence`, `/presence/activity` |
| Data | `GET /api/v1/search`, `/api/v1/views`, `/api/v1/imports`, `POST /api/v1/{module}/export`, `GET /api/v1/dashboard`, `GET /api/v1/metadata/modules` |

Responses use `{"success": true, "data": ..., "message": ...}`; errors use
`{"success": false, "error": {"code", "message", "details": [{field, code, message}]}, "timestamp", "requestId"}`.

See [docs/phases.md](docs/phases.md) for the build phases and what comes next.
