# Deploying to Zoho Catalyst (AppSail — Container Runtime)

Two apps, both deployed as **OCI container images** to Catalyst AppSail:

| App | Image | From | Serves |
|-----|-------|------|--------|
| Backend | `ksp-agent-be` | `service/Dockerfile` | Spring Boot 4 (Java 25) API on port 8080 |
| UI | `ksp-agent-ui` | `client/Dockerfile` | nginx serving the React SPA + `/api` proxy, port 8080 |

> Catalyst **only accepts `linux/amd64`** images.

There are two ways to deploy, documented below:

- **[A. GitHub Actions + Catalyst console](#a-cicd-github-actions--catalyst-console)** — recommended. CI builds & pushes images to Docker Hub; you deploy from the console. No local Docker/CLI needed.
- **[B. Local CLI](#b-local-cli-deploy)** — build and deploy from your machine with the `catalyst` CLI.

---

# A. CI/CD: GitHub Actions + Catalyst console

**Flow:** push to `main` → GitHub Actions builds both `linux/amd64` images and pushes them to Docker Hub → you deploy/redeploy from the Catalyst console (which pulls from Docker Hub). Workflow file: [`.github/workflows/build-and-push.yml`](.github/workflows/build-and-push.yml).

Catalyst natively pulls from **Docker Hub, AWS ECR, or Google Artifact Registry** — this guide uses Docker Hub.

## A1. One-time: Docker Hub

1. Create a **Docker Hub Personal Access Token** (Docker Hub → Account Settings → Personal access tokens → Read/Write).
2. In GitHub: repo → **Settings → Secrets and variables → Actions → New repository secret**, add:
   - `DOCKERHUB_USERNAME` = your Docker ID
   - `DOCKERHUB_TOKEN` = the PAT

## A2. One-time: connect Docker Hub in the Catalyst console

Catalyst console → **Settings → Integrations** (under General Settings) → **Docker Hub** → **Add Docker Account** → give it a name, enter your Docker ID + the same PAT → **Save**.

## A3. Run the build

Push to `main`, or trigger manually: GitHub → **Actions → Build & Push Images → Run workflow**.
When it's green, these tags exist on Docker Hub:
- `<dockerid>/ksp-agent-be:latest` (and `:<git-sha>`)
- `<dockerid>/ksp-agent-ui:latest` (and `:<git-sha>`)

## A4. Deploy the backend from the console

1. Console → open your project → **Serverless → AppSail** (under Compute) → **Deploy from Console**.
2. Deployment type: **Docker Image**. Name: `ksp-agent-be`.
3. Container Registry Service: **Docker Hub** → select your connection → image `<dockerid>/ksp-agent-be`, tag `latest`.
4. **Port: `8080`**.
5. Add the **backend environment variables** (table in [§ Backend environment variables](#backend-environment-variables)). Set `SPRING_PROFILES_ACTIVE=prod` — required.
6. **Deploy.** Copy the backend endpoint URL → call it `BE_URL`.

## A5. Deploy the UI from the console

1. **Deploy from Console** again → **Docker Image**, name `ksp-agent-ui`.
2. Docker Hub → image `<dockerid>/ksp-agent-ui`, tag `latest`, **Port `8080`**.
3. Environment variable: `BASE_URL` = `BE_URL` from A4.
4. **Deploy.** Copy the UI endpoint URL → `UI_URL`.

## A6. Wire the two together

Set these, then restart each app (Console → the app → Environment Variables → Save/Restart):

1. Backend `CORS_ALLOWED_ORIGINS` = `UI_URL`
2. UI `BASE_URL` = `BE_URL` (already set in A5 — confirm)

Open `UI_URL`.

## A7. Redeploying later

Push to `main` (CI rebuilds `:latest` + `:<sha>`) → in the console open each AppSail app → **Deploy new version** / update the tag → Deploy. Env vars persist. (Prefer pinning the `:<git-sha>` tag for reproducible rollbacks.)

---

# B. Local CLI deploy

## 0. Prerequisites (one-time)

```bash
npm install -g zcatalyst-cli     # Catalyst CLI
catalyst login                   # opens browser — authenticate with your Zoho account
```

Create a Catalyst project in the console (https://catalyst.zoho.com) if you don't have one, and note the **project** and **environment** (Development/Production) you'll deploy into.

The backend creates/updates the crime-DB schema itself on every boot (`fir-schema.sql` →
`entity-resolution.sql` → `official-tables-backfill.sql` → `financial-crime-model.sql`, wired in
`ServiceConfig.crimeDataSourceInitializer` — idempotent, safe against an already-seeded DB). The
**bulk synthetic dataset** is the one piece that is NOT auto-loaded — load it once into your prod
crime database, **before first boot** (the backend's own DDL step will run before it if the app
happens to boot first, which is fine — `financial-crime-model.sql`'s seed step just finds no
accused/case data yet and seeds nothing until you load this and restart):
```bash
psql "<CRIME_DB_URL>" -f scripts/fir-seed.sql
```
See [`documents/SCHEMA_FIDELITY.md`](documents/SCHEMA_FIDELITY.md) for the full schema-to-official-ER
mapping and why the load order matters internally.

---

## 1. Build both images (amd64)

```bash
./scripts/build-images.sh          # builds ksp-agent-be:latest + ksp-agent-ui:latest
```

Confirm they exist and are amd64:
```bash
docker images | grep ksp-agent
docker inspect ksp-agent-be:latest --format '{{.Architecture}}'   # -> amd64
```

---

## 2. Deploy the backend

Standalone deploy from the local Docker registry:

```bash
catalyst deploy appsail \
  --name ksp-agent-be \
  --source docker://localhost/ksp-agent-be:latest \
  --port 8080
```

(Or run `catalyst init` → choose **AppSail** → **Docker Image** → pick `ksp-agent-be:latest`, then `catalyst deploy`.)

The CLI prints the backend **endpoint URL** when done — copy it (call it `BE_URL`).

### Backend environment variables

Set these on the `ksp-agent-be` AppSail app (Console → AppSail → your app → **Environment Variables**, or via the CLI). **`SPRING_PROFILES_ACTIVE=prod` is mandatory** — the image defaults to the `local` profile otherwise.

| Variable | Notes |
|----------|-------|
| `SPRING_PROFILES_ACTIVE` | `prod` — **required** |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | App (primary) Postgres |
| `CRIME_DB_URL` / `CRIME_DB_USERNAME` / `CRIME_DB_PASSWORD` | Crime FIR Postgres |
| `AGENT_ENCRYPTION_KEY` | 64-hex key that encrypts stored secrets — keep stable across deploys |
| `LLM_BASE_URL` | required; plus `LLM_COMPLETIONS_PATH`, `LLM_CATALYST_ORG`, `LLM_MODEL`, `LLM_CLIENT_ID`, `LLM_CLIENT_SECRET`, `LLM_REFRESH_TOKEN`, `LLM_ACCOUNTS_BASE_URL` (or `LLM_API_KEY`) |
| `RAG_BASE_URL`, `RAG_COMPLETIONS_PATH`, `RAG_CATALYST_ORG`, `RAG_API_KEY` | RAG (optional; blank-safe) |
| `STRATUS_BUCKET_URL`, `STRATUS_CATALYST_ORG`, `STRATUS_API_KEY` | Stratus object storage |
| `CRON_SECRET` | scheduled-task auth |
| `JWT_SECRET` | app JWT signing secret |
| `CORS_ALLOWED_ORIGINS` | set to the **UI URL** from step 3 (comma-separated if multiple) |

> Start from [`.env.example`](.env.example) (key names + guidance, no values) and pull the real values from `service/src/main/resources/application-local.yml` (git-ignored) or your secret store.
>
> ⚠️ **`MEMORY_*` are not "set and forget".** If `MEMORY_RECENT_WINDOW` / `MEMORY_SUMMARY_THRESHOLD` are set low (e.g. `1`/`2`), they **override** the sane `application.yaml` defaults of `20`/`40` and truncate the model's view to ~2 messages — multi-turn follow-up questions then lose all context. Keep them at `20`/`40` unless you are deliberately testing summarization. The other tuning vars (`SKILL_UPDATE_*`, `TOOL_SEARCH_*`, `CHAT_TEMPORARY_*`) do have safe defaults.

---

## 3. Deploy the UI

```bash
catalyst deploy appsail \
  --name ksp-agent-ui \
  --source docker://localhost/ksp-agent-ui:latest \
  --port 8080
```

Copy the UI **endpoint URL** (`UI_URL`).

### UI environment variable

| Variable | Value |
|----------|-------|
| `BASE_URL` | the backend `BE_URL` from step 2 |

`docker-defaults.sh` uses `BASE_URL` at container start to (a) point the nginx `/api/` proxy at the backend and (b) set `window.__RUNTIME_CONFIG__.streamApiBase` so the browser streams SSE **directly** from the backend. Do not route SSE through the UI proxy on AppSail — the edge buffers and you get a one-shot answer instead of tokens.

---

## 4. Wire the two together (important — do this after both are up)

There's a two-way dependency, so finish the env wiring once both URLs exist, then restart:

1. Backend `CORS_ALLOWED_ORIGINS` = `UI_URL`   → restart `ksp-agent-be`
2. UI `BASE_URL` = `BE_URL`                     → restart `ksp-agent-ui`

Then open `UI_URL` in a browser.

---

## 5. Redeploying after code changes

```bash
./scripts/build-images.sh
catalyst deploy appsail --name ksp-agent-be --source docker://localhost/ksp-agent-be:latest --port 8080
catalyst deploy appsail --name ksp-agent-ui --source docker://localhost/ksp-agent-ui:latest --port 8080
```

Env vars persist across redeploys.

---

## Troubleshooting

- **App won't start / 502:** check the AppSail app **logs** in the console. If the container listens on a port other than what Catalyst routes to, make the declared `--port` match the container's port (both images use `8080`).
- **Backend boots with `local` profile / can't reach DB:** `SPRING_PROFILES_ACTIVE` isn't set to `prod`.
- **UI loads but chat/SSE fails with CORS errors:** stream is the only browser→BE cross-origin call (other `/api` goes through the UI proxy, so a set `CORS_ALLOWED_ORIGINS` was never exercised by them). Confirm value is exactly `UI_URL` (no trailing slash), `SPRING_PROFILES_ACTIVE=prod`, redeploy BE. Do not add `@CrossOrigin` on controllers — Security CORS alone handles it.
- **Chat answer arrives all at once (no token stream):** SSE is going through the UI `/api` proxy — confirm `streamApiBase` is the BE URL (UI `BASE_URL`) and redeploy UI.
- **UI 5xx on `/api`:** `BASE_URL` on the UI isn't set or points at the wrong backend URL.
- **Chat stream dies after a long wait:** Spring/nginx timeouts are 5h; check AppSail/gateway idle limits if cuts happen earlier.
- **`catalyst deploy` rejects the image:** it isn't `linux/amd64` — rebuild with `./scripts/build-images.sh`.

## References
- [Deploy AppSail as a Custom Runtime from the CLI](https://docs.catalyst.zoho.com/en/serverless/help/appsail/custom-runtimes/deploy-from-cli/)
- [Catalyst AppSail now supports Docker images](https://catalyst.zoho.com/blog/custom-runtime-in-catalyst-appsail.html)
- [Deploy AppSail (CLI reference)](https://docs.catalyst.zoho.com/en/cli/v1/deploy-resources/deploy-appsail/)
- [Java runtime overview](https://docs.catalyst.zoho.com/en/serverless/help/appsail/help-guides/java/overview/)
