#!/usr/bin/env bash
# =============================================================================
# backup.sh — pg_dump the RKE database and upload to S3-compatible storage.
#
# Run manually or via a cron / Render cron job.
#
# Required environment variables:
#   DB_HOST       — Postgres hostname
#   DB_PORT       — Postgres port (default: 5432)
#   DB_NAME       — database name
#   DB_USERNAME   — database user
#   DB_PASSWORD   — database password
#
#   BACKUP_BUCKET — S3 / B2 bucket name (e.g. rke-backups)
#
# Optional:
#   S3_ENDPOINT      — S3-compatible endpoint URL (required for Backblaze B2
#                      or any non-AWS provider; omit for real AWS S3)
#   AWS_ACCESS_KEY_ID     — S3 / B2 access key (falls through to ~/.aws/credentials)
#   AWS_SECRET_ACCESS_KEY — S3 / B2 secret key
#   BACKUP_PREFIX    — key prefix inside the bucket (default: backups)
#   BACKUP_KEEP_DAYS — local temp file is always deleted; remote retention
#                      must be managed via bucket lifecycle rules
#
# Backblaze B2 example:
#   S3_ENDPOINT=https://s3.us-west-004.backblazeb2.com
#   AWS_ACCESS_KEY_ID=<B2 keyID>
#   AWS_SECRET_ACCESS_KEY=<B2 applicationKey>
#   BACKUP_BUCKET=rke-backups
#
# AWS S3 example (no S3_ENDPOINT needed):
#   AWS_ACCESS_KEY_ID=<key>
#   AWS_SECRET_ACCESS_KEY=<secret>
#   BACKUP_BUCKET=rke-backups
# =============================================================================

set -euo pipefail

# ── Validate required vars ───────────────────────────────────────────────────
: "${DB_HOST:?DB_HOST is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${BACKUP_BUCKET:?BACKUP_BUCKET is required}"

DB_PORT="${DB_PORT:-5432}"
BACKUP_PREFIX="${BACKUP_PREFIX:-backups}"
TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
DUMP_FILENAME="rke-${TIMESTAMP}.sql.gz"
DUMP_FILE="/tmp/${DUMP_FILENAME}"

# ── Dump ─────────────────────────────────────────────────────────────────────
echo "[backup] Dumping ${DB_NAME} @ ${DB_HOST}:${DB_PORT} ..."

PGPASSWORD="${DB_PASSWORD}" pg_dump \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --username="${DB_USERNAME}" \
    --dbname="${DB_NAME}" \
    --no-password \
    --format=plain \
    --no-owner \
    --no-acl \
  | gzip > "${DUMP_FILE}"

DUMP_SIZE=$(du -sh "${DUMP_FILE}" | cut -f1)
echo "[backup] Dump complete: ${DUMP_FILE} (${DUMP_SIZE})"

# ── Upload ────────────────────────────────────────────────────────────────────
S3_KEY="${BACKUP_PREFIX}/${DUMP_FILENAME}"

if command -v aws &>/dev/null; then
    echo "[backup] Uploading via aws CLI → s3://${BACKUP_BUCKET}/${S3_KEY} ..."

    ENDPOINT_ARG=""
    if [[ -n "${S3_ENDPOINT:-}" ]]; then
        ENDPOINT_ARG="--endpoint-url ${S3_ENDPOINT}"
    fi

    # shellcheck disable=SC2086
    aws s3 cp "${DUMP_FILE}" "s3://${BACKUP_BUCKET}/${S3_KEY}" \
        ${ENDPOINT_ARG} \
        --no-progress

    echo "[backup] Upload complete."
else
    echo "[backup] ERROR: aws CLI not found. Install it or add it to PATH."
    echo "[backup] Dump is at ${DUMP_FILE} — upload manually."
    exit 1
fi

# ── Cleanup ───────────────────────────────────────────────────────────────────
rm -f "${DUMP_FILE}"
echo "[backup] Done: ${DUMP_FILENAME} → s3://${BACKUP_BUCKET}/${S3_KEY}"
