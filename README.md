# Crime Intelligence Assistant — Karnataka State Police

An AI-assisted crime-intelligence platform built for the KSP Datathon 2026 challenge: a
conversational assistant over the state FIR (First Information Report) database, plus a crime
analytics dashboard (trends, hotspots, forecasts, offender risk, financial-crime network, alerts).

## Architecture

```
┌─────────────────────┐        ┌──────────────────────────────────────┐        ┌─────────────────┐
│  React 18 + TS SPA   │──/api─▶│  Spring Boot 4 (Java 25) API         │──────▶│  App Postgres    │
│  (webpack, Zustand)  │  SSE   │  - chat/tool-calling (Spring AI)     │        │  sessions, audit,│
└─────────────────────┘        │  - crime analytics REST layer        │        │  users, config   │
                                │  - role-gated crime tools/SQL guard  │        └─────────────────┘
                                └───────────────┬──────────────────────┘
                                                 │
                                                 ▼
                                        ┌─────────────────┐
                                        │  Crime Postgres  │  FIR schema (see documents/SCHEMA_FIDELITY.md)
                                        │  case/accused/   │  + entity resolution, financial-crime model,
                                        │  victim/...      │  MO/outcome/suspicion-detection views
                                        └─────────────────┘
```

- **Backend**: `service/` — Spring Boot 4, Spring AI (tool-calling chat over a Zoho Catalyst
  QuickML-hosted LLM), two Postgres datasources (app data vs. the read-only crime FIR data).
- **Frontend**: `client/` — React + TypeScript, Zustand state, SSE streaming chat, an analytics
  panel (dashboard/map/network/offenders/financial), Kannada/English UI toggle.
- **Crime schema fidelity**: the crime database is modeled against the organizers' official ER
  diagram wherever possible; every place it diverges (renamed, derived, or added-beyond-spec) is
  documented in [`documents/SCHEMA_FIDELITY.md`](documents/SCHEMA_FIDELITY.md) — read that first if
  you're evaluating correctness against the official schema.

## Prerequisites

- Java 25, Maven (plain `mvn` — no wrapper checked in)
- Node.js (any current LTS) + npm
- Two Postgres databases (app data + crime FIR data) — either your own local Postgres, or the
  shared demo instances (ask a maintainer for `application-local.yml`'s real values)
- Docker, only if you want to load the crime data locally instead of pointing at a shared instance
  (`pgvector/pgvector:pg16` is used throughout this project's own verification — any Postgres 16
  works, `pgvector` isn't actually required by the crime schema)

## Local setup

### 1. Backend

```bash
cd service
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# fill in real datasource URLs/credentials (ask a maintainer, or point at your own local Postgres)
mvn spring-boot:run
```

The `local` Spring profile is active by default (no `SPRING_PROFILES_ACTIVE` needed). On boot, the
backend auto-creates/updates its own app-side schema (`spring.sql.init.mode: always`) AND the crime
DB's schema/views (`ServiceConfig.crimeDataSourceInitializer` — see
[`documents/SCHEMA_FIDELITY.md`](documents/SCHEMA_FIDELITY.md) for exactly which files that runs
and why the order matters). Only the **bulk synthetic FIR dataset** needs a manual one-time load:

```bash
psql "<CRIME_DB_URL>" -f scripts/fir-seed.sql
```

(Regenerate it with `python3 scripts/generate_fir_data.py` if you want a fresh dataset — it's
deterministic/seeded, so re-running without editing the script reproduces the same data.)

The backend listens on `:8080`.

### 2. Frontend

```bash
cd client
npm install
npm run dev
```

Opens on `http://localhost:4000`, proxying `/api` to `http://localhost:8080` (override with
`BASE_URL` if your backend runs elsewhere).

### 3. Log in

Five seeded demo accounts, one per role, shared password `Password@123`:

| Username | Role | Can access |
|---|---|---|
| `admin` | ADMIN | Everything, incl. supervisor session review, prompt/skill editing |
| `supervisor` | SUPERVISOR | Investigative tools/data + any user's chat/tool-call trail (accountability review) |
| `investigator` | INVESTIGATOR | Investigative tools/data: offender risk, network, financial trail |
| `analyst` | ANALYST | Dashboard/map/network aggregates; NOT financial or per-offender risk detail |
| `policymaker` | POLICYMAKER | Dashboard/map aggregates, early warnings, predicted hotspots |

## Demo walkthrough

1. Log in as `investigator`. Open the **Crime Intelligence** assistant and ask a factual question
   ("how many cyber crime cases in Bengaluru City this year?") — the assistant calls
   `run_crime_sql`/`get_crime_schema` and answers from live data, never invented numbers.
2. Ask for a case summary by crime number (`summarize_case` — includes a merged chronological
   investigation timeline) and a repeat-offender lookup (`offender_profile`, backed by the derived
   `accused_identity` entity-resolution layer, not an assumed cross-case key — see
   `documents/SCHEMA_FIDELITY.md`).
3. Ask about gangs/organized groups (`detect_offender_groups`) and a money trail
   (`list_account_transactions` for direct transactions, `trace_money_network` for multi-hop
   layering/cycles) — these five investigative tools are refused for the `analyst`/`policymaker`
   roles, at both the tool-call layer and the SQL-table layer (try asking `analyst` the same
   question — it declines).
4. Switch the UI language toggle to Kannada (ಕ) mid-conversation — the assistant's *reply*, not
   just the chrome, switches language.
5. Open the **Analytics** panel: Dashboard (trends, early warnings, predicted hotspots — Holt-
   Winters forecast with a backtested MAPE/RMSE, not just a curve), Map (hotspots), Network
   (co-offender clusters + victims/locations), Offenders (risk ranking), Financial (money trail,
   suspicious transactions — real rule-evaluated, not a random flag).
6. Log in as `supervisor` and hit `GET /api/sessions/all` to review another user's full chat + tool
   trail — the persisted audit evidence behind every model answer.
7. Export a chat as PDF (toolbar icon) — the export force-expands every tool card so the audit
   trail (Input/Output) is actually in the PDF, not just the prose reply.

## Deployment

See [`DEPLOY.md`](DEPLOY.md) for Zoho Catalyst AppSail deployment (Docker images, CI/CD, env vars).

## Documentation

- [`documents/SCHEMA_FIDELITY.md`](documents/SCHEMA_FIDELITY.md) — official ER diagram vs.
  implementation, entity-resolution methodology + measured accuracy, and every documented
  limitation.
- [`.env.example`](.env.example) — production env-var template (Catalyst deployment).
- [`service/src/main/resources/application-local.yml.example`](service/src/main/resources/application-local.yml.example)
  — local dev config template.
