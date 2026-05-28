#!/usr/bin/env bash
set -euo pipefail

# GateJ-FREEZE deployment helper.
#
# Why:
# - The ECS host must run the release package exactly as built locally.
# - This script verifies required Docker images are already present, avoiding
#   implicit dependency downloads on the server.
# - It creates only runtime directories under NQ_BASE_DIR and starts Compose.

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

if [[ ! -f "${ENV_FILE}" ]]; then
  if [[ -f "${ROOT_DIR}/.env.freeze.example" ]]; then
    cp "${ROOT_DIR}/.env.freeze.example" "${ENV_FILE}"
  fi
  echo "ERROR: ${ENV_FILE} is not configured. Edit it before deployment." >&2
  exit 1
fi

NQ_BASE_DIR="$(read_env_value "NQ_BASE_DIR" "/opt/nexus-quant")"
required_images=("postgres:16" "eclipse-temurin:21-jre" "nginx:alpine")

for image in "${required_images[@]}"; do
  if ! docker image inspect "${image}" >/dev/null 2>&1; then
    echo "ERROR: required Docker image is missing locally: ${image}" >&2
    echo "Load images before running this script; GateJ-FREEZE must not download dependencies on the server." >&2
    exit 1
  fi
done

mkdir -p \
  "${NQ_BASE_DIR}/data/postgres" \
  "${NQ_BASE_DIR}/logs/postgres" \
  "${NQ_BASE_DIR}/logs/nq-app" \
  "${NQ_BASE_DIR}/logs/nginx" \
  "${NQ_BASE_DIR}/freeze-evidence/health" \
  "${NQ_BASE_DIR}/freeze-evidence/db" \
  "${NQ_BASE_DIR}/backups"

compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d
compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps
