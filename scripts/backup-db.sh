#!/usr/bin/env bash
set -euo pipefail

# GateJ-FREEZE PostgreSQL backup helper.
#
# Usage:
#   ./scripts/backup-db.sh before-freeze
#   ./scripts/backup-db.sh after-1h
#   ./scripts/backup-db.sh after-24h
#   ./scripts/backup-db.sh after-7d
#
# The script writes SQL dumps under freeze-evidence/db. Those dumps can contain
# sensitive operational data and must never be committed to Git.

ROOT_DIR="${ROOT_DIR:-/opt/nexus-quant}"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.freeze.yml}"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.freeze}"
BACKUP_NAME="${1:-before-freeze}"

cd "${ROOT_DIR}"

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    echo "ERROR: Docker Compose is not available. Install Docker Compose v2 or docker-compose." >&2
    exit 1
  fi
}

read_env_value() {
  local key="$1"
  local default_value="$2"
  local line=""
  line="$(grep -E "^[[:space:]]*${key}=" "${ENV_FILE}" | tail -n 1 || true)"
  if [[ -z "${line}" ]]; then
    printf "%s" "${default_value}"
    return
  fi
  local value="${line#*=}"
  value="${value%$'\r'}"
  value="${value%\"}"
  value="${value#\"}"
  value="${value%\'}"
  value="${value#\'}"
  printf "%s" "${value}"
}

if [[ ! "${BACKUP_NAME}" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "ERROR: backup name may only contain letters, numbers, dot, underscore, and dash." >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "ERROR: missing env file: ${ENV_FILE}" >&2
  exit 1
fi

POSTGRES_DB="$(read_env_value "POSTGRES_DB" "nexus_quant")"
POSTGRES_USER="$(read_env_value "POSTGRES_USER" "nq_freeze")"
NQ_BASE_DIR="$(read_env_value "NQ_BASE_DIR" "/opt/nexus-quant")"
OUTPUT_DIR="${NQ_BASE_DIR}/freeze-evidence/db"
OUTPUT_FILE="${OUTPUT_DIR}/${BACKUP_NAME}.sql"

mkdir -p "${OUTPUT_DIR}"

compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" exec -T postgres \
  pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" --no-owner --no-privileges \
  > "${OUTPUT_FILE}"

chmod 600 "${OUTPUT_FILE}"
echo "DB backup written: ${OUTPUT_FILE}"
