# Deployment — RK Enterprises on Render

This repo contains a `render.yaml` (Render Blueprint) that defines all three pieces of infrastructure from a single repo: a managed Postgres instance, a Spring Boot backend, and a React/Nginx frontend. One Blueprint launch stands up everything.

---

## Prerequisites

- A [Render](https://render.com) account.
- This repo pushed to GitHub (or GitLab / Bitbucket).
- For logo uploads: an S3-compatible bucket (Backblaze B2 recommended).
- For backups: the AWS CLI (`aws`) installed wherever you run `backup.sh`.

---

## 1 — Connect the repo to Render via Blueprint

### Dashboard (recommended for first-timers)

1. Go to **dashboard.render.com → New → Blueprint**.
2. Connect your GitHub account and select the `rke` repository.
3. Render reads `render.yaml` from the repo root and shows a preview of the three services.
4. Click **Apply**. Render creates `rke-postgres`, `rke-backend`, and `rke-frontend` and begins the first deploys.

### CLI

```bash
render blueprint launch
```

Render detects `render.yaml` in the current directory, prompts you to confirm, and launches all three services.

---

## 2 — Environment variables to set after first deploy

`render.yaml` marks two env vars as `sync: false`, meaning Render will not deploy the affected service until they are given values. Set them in the **Render dashboard → Service → Environment**.

### rke-backend

| Variable | Where to get the value |
|---|---|
| `CORS_ALLOWED_ORIGINS` | The URL of your deployed frontend, e.g. `https://rke-frontend.onrender.com`. Find it in the rke-frontend service page after it deploys. |
| `S3_ENDPOINT` | Your B2/S3 endpoint, e.g. `https://s3.us-west-004.backblazeb2.com`. Leave blank to disable logo upload. |
| `S3_ACCESS_KEY` | B2 keyID or AWS access key. |
| `S3_SECRET_KEY` | B2 applicationKey or AWS secret key. |
| `S3_PUBLIC_URL_BASE` | Public URL prefix for uploaded files, e.g. `https://f004.backblazeb2.com/file/rke-assets`. |

### rke-frontend

| Variable | Where to get the value |
|---|---|
| `VITE_API_BASE_URL` | The URL of the deployed backend, e.g. `https://rke-backend.onrender.com`. Find it in the rke-backend service page. **This value is baked into the static bundle at build time.** After setting it, trigger a manual redeploy of rke-frontend (three-dot menu → Manual Deploy). |

> **Why the two-step?** `VITE_API_BASE_URL` is embedded into the JavaScript bundle during the Docker build (Render passes env vars as Docker build args). The backend must be deployed first so you know its URL before building the frontend.

---

## 3 — Database migrations (Flyway)

Flyway is configured with `baseline-on-migrate: true` and `locations: classpath:db/migration`. Migrations run **automatically at Spring Boot startup**. No manual step is needed.

When `rke-backend` starts:

1. Flyway connects to the Render Postgres instance using the `DB_*` vars wired via `fromDatabase`.
2. It checks the `flyway_schema_history` table and runs any pending migrations in version order.
3. Only after all migrations succeed does the application accept traffic (confirmed by the `/api/health` probe returning `200`).

To verify migrations ran:

```bash
# From Render dashboard → rke-backend → Logs
# Look for lines like:
#   Successfully applied 6 migration(s) to schema "public"
```

Or connect to the DB directly:

```bash
psql "$RENDER_POSTGRES_CONNECTION_STRING" \
  -c "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;"
```

---

## 4 — Health check configuration

The backend exposes `GET /api/health`. This endpoint:

- Issues `SELECT 1` against the connection pool.
- Returns `200 {"status":"UP","db":"reachable"}` when healthy.
- Returns `503 {"status":"DOWN","db":"unreachable"}` when the DB is not reachable.

Render uses this automatically (configured via `healthCheckPath: /api/health` in `render.yaml`). No additional setup is required. The service only receives traffic once the health check passes, which means Flyway must have finished running.

To hit it manually:

```bash
curl https://rke-backend.onrender.com/api/health
```

---

## 5 — Running a manual database backup

The backup script lives at `backend/scripts/backup.sh`. It uses `pg_dump` and the AWS CLI (which handles Backblaze B2 via its S3-compatible API).

### One-time setup (run this once on your machine or a bastion)

```bash
# Install aws CLI if not present
pip install awscli   # or brew install awscli

# Configure credentials (or set env vars — see below)
aws configure set aws_access_key_id     "<B2 keyID or AWS key>"
aws configure set aws_secret_access_key "<B2 applicationKey or AWS secret>"
```

### Run the backup

```bash
export DB_HOST="<host from Render Postgres dashboard>"
export DB_PORT="5432"
export DB_NAME="rke"
export DB_USERNAME="rke"
export DB_PASSWORD="<password from Render Postgres dashboard>"
export BACKUP_BUCKET="rke-backups"
export S3_ENDPOINT="https://s3.us-west-004.backblazeb2.com"   # omit for real AWS S3
export AWS_ACCESS_KEY_ID="<B2 keyID>"
export AWS_SECRET_ACCESS_KEY="<B2 applicationKey>"

bash backend/scripts/backup.sh
```

The script:
1. Runs `pg_dump --format=plain --no-owner --no-acl` and gzips the output.
2. Uploads `rke-<timestamp>.sql.gz` to `s3://<BACKUP_BUCKET>/backups/`.
3. Deletes the local temp file.

### Restore from a backup

```bash
# Download the dump
aws s3 cp s3://rke-backups/backups/rke-<timestamp>.sql.gz ./restore.sql.gz \
    --endpoint-url https://s3.us-west-004.backblazeb2.com

# Restore
gunzip -c restore.sql.gz | psql \
    "postgresql://${DB_USERNAME}:${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
```

> **Retention policy:** Set a [Backblaze B2 lifecycle rule](https://www.backblaze.com/docs/cloud-storage-lifecycle-rules) or an [S3 lifecycle policy](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lifecycle-mgmt.html) on the bucket to auto-expire old backups (e.g. keep 30 days).

---

## 6 — Custom domains

1. Render dashboard → rke-frontend → **Settings → Custom Domain** → add your domain.
2. Update `CORS_ALLOWED_ORIGINS` on rke-backend to include the new domain.
3. Save → Render redeploys the backend automatically.

---

## 7 — Scaling and plan upgrades

| Service | Render plan | Notes |
|---|---|---|
| rke-postgres | `starter` → `standard` | Upgrade for connection pooling (PgBouncer), daily automated backups from Render, and point-in-time recovery. |
| rke-backend | `starter` → `standard` | Upgrade for more RAM, zero-downtime deploys, and persistent disk if needed. |
| rke-frontend | `starter` | Nginx is very lightweight; starter is usually sufficient. |

To change a plan: Render dashboard → Service → **Settings → Instance Type**.

---

## 8 — Local development (unchanged)

```bash
cp .env.example .env   # fill in overrides
docker compose up --build
```

The `docker-compose.yml` is unaffected by the Render setup.
