# OrbitCRM Frontend

Web app for the OrbitCRM backend, built with **React 19, TypeScript, Vite and Ant Design 6**.
Brand colours: navy `#032577`, purple `#bd38fa` and cyan `#1edcfc` (see `src/theme.ts` and `src/styles.css`).

## Run it

You need the backend running first (see `../orbitcrm-backend/README.md`), on port 8080 by default.

```bash
npm install
npm run dev          # http://localhost:5173
```

The dev server forwards `/api` to the backend, so the browser talks to a single origin. To use a backend on
another port, set `BACKEND_URL`; in PowerShell:

```powershell
$env:BACKEND_URL = "http://localhost:8091"; npm run dev
```

Keep port 5173: invitation and password reset emails link to `http://localhost:5173/accept-invite` and
`/reset-password` (backend setting `ORBITCRM_FRONTEND_URL`).

| Script | Purpose |
|---|---|
| `npm run dev` | Development server with hot reload |
| `npm run build` | Type-check and build to `dist/` |
| `npm run lint` | Lint with oxlint |
| `npm run preview` | Serve the built app locally |

For production, serve `dist/` and route `/api` to the backend on the same domain (for example with nginx).
Any unknown path must return `index.html`, because the app uses browser routing.

## What's inside

| Area | Screens |
|---|---|
| Sign-in | Login (with two-step code), create workspace, forgot / reset password, accept invitation |
| Dashboard | KPIs, sales funnel, revenue for 12 months, leads by source, recent leads, open activities, agenda |
| Records | Leads, contacts, companies, deals and activities: list with saved views, search, filters, sorting, bulk assign/delete, CSV export; detail with timeline, notes, files and related records; create/edit form |
| Sales | Lead conversion, pipeline board with drag and drop, clickable stage path, stage history |
| Activities | Month calendar for you and colleagues |
| Chat | Direct messages with live delivery, typing indicators, read receipts, edit and delete, retry for unsent messages; Teams-style presence (Available, Busy, In a meeting, Do not disturb, Be right back, Away, Offline) on avatars; unread badge, toasts and desktop notifications (muted in Do not disturb) |
| Data | Global search, CSV import wizard |
| Settings | Workspace, users, teams, roles and permission matrix, dropdown values, pipelines, audit log |
| Profile | Details, password, two-step login (QR code), signed-in devices |

## How it is organised

```
src/
├── api/          client.ts (fetch wrapper, token refresh, downloads), types.ts (API shapes)
├── auth/         session context, provider, route guard
├── layout/       header, navigation, user menu, global search, notifications
├── chat/         live connection (ChatProvider), stream reader, presence dots and statuses, chat cache helpers
├── components/   metadata-driven field input/display, record form, filters, notes, files, timeline, charts
├── lib/          formatting, hooks, permissions, module list
└── pages/        one file per screen; settings/ and auth/ grouped
```

- **Metadata-driven.** The record screens read field definitions from `GET /api/v1/metadata/modules/{module}`, so one
  list, detail and form implementation serves every module, including fields added on the server later.
- **Sessions.** The access token stays in memory. The refresh token is an HttpOnly cookie that scripts can't read. On
  reload, and whenever a request returns 401, the app quietly calls `/auth/refresh` once and retries.
- **Permissions.** Buttons and menus follow the signed-in user's permissions from `/auth/me`. The server enforces the
  same rules, so hiding a button is a convenience, not the security boundary.
- **Data fetching.** TanStack Query handles caching and loading states. Failed requests show one message from a central
  handler, and forms show server validation errors next to the fields.
- **Pages load on demand.** Each screen is split into its own bundle.

The logo is `src/assets/logo.png` (transparent background, 224×160) and the browser tab icon is
`public/favicon.png` (64×64). Replace those two files to change them; `src/layout/Logo.tsx` places the logo in the
header and on the sign-in pages.
