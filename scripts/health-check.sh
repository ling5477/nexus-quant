#!/usr/bin/env bash
set -euo pipefail

# GateJ-FREEZE one-shot health check.
#
# The output is intentionally plain text so it can be redirected into
# freeze-evidence without requiring extra tools on a 2C2G ECS host.

ROOT_DIR="${ROOT_DIR:-/opt/nexus-quant}"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.freeze.yml}"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.freeze}"
APP_HEALTH_URL="${APP_HEALTH_URL:-http://127.0.0.1:18888/actuator/health}"

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

echo "===== timestamp ====="
date -Is

echo "===== docker ps ====="
docker ps

echo "===== free -h ====="
free -h

echo "===== df -h ====="
df -h

echo "===== actuator health ====="
curl -fsS "${APP_HEALTH_URL}" || {
  echo
  echo "ERROR: actuator health check failed: ${APP_HEALTH_URL}" >&2
  exit 1
}
echo

echo "===== nq-app logs last 100 lines ====="
compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" logs --tail=100 nq-app
