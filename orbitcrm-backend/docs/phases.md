# OrbitCRM backend – build phases

The backend was built in small, testable phases. Each phase compiles, passes the full test suite
(real PostgreSQL via Testcontainers) and keeps the Spring Modulith boundary check green.

| Phase | Scope | Key decisions |
|---|---|---|
| 0 | Foundation | Spring Boot 4.1 / Java 21, Flyway, standard response + error envelope, request ids, OpenAPI, Actuator, Modulith verification |
| 1 | Tenancy, identity, access | Shared schema + `tenant_id` + PostgreSQL row-level security; app connects as a non-owner role; JWT access tokens (15 min) + rotating refresh tokens in an HttpOnly cookie with reuse detection; sessions; TOTP MFA; roles with OWN/TEAM/ALL scopes; teams hierarchy |
| 2 | Platform kernel | Field metadata registry, tenant picklists, one filter language (JSON AST → parameterised SQL), generic record engine (CRUD, bulk, soft delete, optimistic locking), append-only partitioned audit log, trigram search index, domain events |
| 3 | Core CRM | Companies, Contacts, Leads, Deals on the engine; pipelines & stages (open/won/lost); stage → probability/status/closedAt; stage history; pipeline board with totals; aggregation API |
| 4 | Lead conversion | All-or-nothing conversion to company/contact/deal (create or link), duplicate suggestions, frozen converted leads, conversion history snapshot |
| 5 | Activities & collaboration | Activities with participants (sharing field), calendar feed, reminder job (ShedLock), notes with @mentions, attachments (magic-byte type checks, object storage), unified timeline, in-app notifications + SSE |
| 6 | Search, views, data | Global search across modules (scope-aware), saved views with `$me`/date tokens, CSV import (upload → map → preview → run → errors), CSV export (EXPORT scope, formula-injection safe) |
| 7 | Dashboard & hardening | Dashboard KPIs/funnel/revenue, API rate limiting, security headers, Dockerfile, docs |
| 8 | In-app chat & presence | Direct messages over SSE on the existing bearer auth; cross-instance fan-out via PostgreSQL LISTEN/NOTIFY (transactional, no broker); per-stream ordered outbox; idempotent sends (`clientId`), edit/delete, read receipts, typing; Teams-style presence computed from heartbeat connection rows, chosen status, idle tabs and meetings in progress; sweep job (ShedLock) |

## What is intentionally not in the MVP (next phases per the PRD)

* **V2:** products, services, price books, quotes (PDF), sales orders, invoices, tickets + SLA, knowledge base,
  campaigns, marketing lists (reuse the filter language), report builder (reuse `RecordService.aggregate`).
* **V3:** workflow engine (consume the `record.event` domain events), approvals, tenant custom fields (`custom_fields` JSONB columns
  already exist on every record table) and custom modules (metadata-driven), webhooks (add the Spring Modulith
  event publication registry as an outbox), integrations.
* **Also deferred:** recurring calendar events, record merge, Excel/JSON import, record-level sharing rules,
  field-level security, SSO/SCIM, multi-currency.
