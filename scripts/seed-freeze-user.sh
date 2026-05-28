#!/usr/bin/env bash
set -euo pipefail

# GateJ-FREEZE one-shot auth seed helper.
#
# Why:
# - The ECS host must not install Python, Node, Maven, or any standalone bcrypt
#   tool just to repair a freeze login user.
# - PostgreSQL already ships with pgcrypto in the official image, so the seed
#   can generate a BCrypt-compatible hash inside the database container and
#   write only the hash to users.password_hash.
# - The plaintext password is read from `.env.freeze` or the process
#   environment, is never echoed, and must never be committed.

ROOT_DIR="${ROOT_DIR:-/opt/nexus-quant}"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.freeze.yml}"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.freeze}"

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

require_env_value() {
  local key="$1"
  local value="${!key:-}"
  if [[ -z "${value}" ]]; then
    value="$(read_env_value "${key}" "")"
  fi
  if [[ -z "${value}" || "${value}" == CHANGE_ME* ]]; then
    echo "ERROR: ${key} must be set in ${ENV_FILE} or the process environment." >&2
    exit 1
  fi
  printf "%s" "${value}"
}

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "ERROR: missing env file: ${ENV_FILE}" >&2
  exit 1
fi

POSTGRES_DB="$(read_env_value "POSTGRES_DB" "nexus_quant")"
POSTGRES_USER="$(read_env_value "POSTGRES_USER" "nq_freeze")"
FREEZE_USERNAME="$(require_env_value "NQ_FREEZE_ADMIN_USERNAME")"
FREEZE_PASSWORD="$(require_env_value "NQ_FREEZE_ADMIN_PASSWORD")"

if [[ ${#FREEZE_PASSWORD} -lt 12 ]]; then
  echo "ERROR: NQ_FREEZE_ADMIN_PASSWORD must be at least 12 characters." >&2
  exit 1
fi

compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" exec -T postgres \
  psql -v ON_ERROR_STOP=1 \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    -v freeze_username="${FREEZE_USERNAME}" \
    -v freeze_password="${FREEZE_PASSWORD}" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO roles (role_code, description, created_at)
VALUES
  ('ADMIN', 'System administrator', NOW()),
  ('OPERATOR', 'Operations user', NOW()),
  ('VIEWER', 'Read-only user', NOW())
ON CONFLICT (role_code) DO NOTHING;

CREATE TEMP TABLE nq_freeze_seed_user_id ON COMMIT DROP AS
WITH input AS (
  SELECT
    :'freeze_username'::text AS username,
    crypt(:'freeze_password'::text, gen_salt('bf', 10)) AS password_hash
),
upserted AS (
  INSERT INTO users (username, password_hash, enabled, created_at, updated_at)
  SELECT username, password_hash, TRUE, NOW(), NOW()
  FROM input
  ON CONFLICT (username) DO UPDATE
  SET password_hash = EXCLUDED.password_hash,
      enabled = TRUE,
      updated_at = NOW()
  RETURNING id
)
SELECT id FROM upserted;

DELETE FROM user_roles
WHERE user_id = (SELECT id FROM nq_freeze_seed_user_id);

INSERT INTO user_roles (user_id, role_id, granted_at)
SELECT seeded.id, roles.id, NOW()
FROM nq_freeze_seed_user_id seeded
CROSS JOIN roles
WHERE roles.role_code IN ('ADMIN', 'OPERATOR', 'VIEWER')
ON CONFLICT (user_id, role_id) DO NOTHING;

SELECT COUNT(*) AS freeze_user_bcrypt_ok
FROM users
WHERE username = :'freeze_username'::text
  AND enabled = TRUE
  AND password_hash ~ '^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$'
  AND password_hash = crypt(:'freeze_password'::text, password_hash);
\gset

\if :freeze_user_bcrypt_ok != 1
  \echo ERROR: freeze user seed did not produce a verifiable BCrypt password hash.
  \quit 1
\endif
SQL

echo "Freeze user seeded with a verifiable BCrypt password hash: ${FREEZE_USERNAME}"
