#!/usr/bin/env bash
set -euo pipefail

# GateJ-FREEZE 7-day health sampling loop.
#
# Why:
# - The freeze acceptance needs stable, append-only evidence every 5 minutes.
# - The loop keeps running until the operator stops it with Ctrl+C, systemd, or
#   a terminal/session manager.

ROOT_DIR="${ROOT_DIR:-/opt/nexus-quant}"
COMPOSE_FILE="${COMPOSE_FILE:-${ROOT_DIR}/docker-compose.freeze.yml}"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env.freeze}"
APP_HEALTH_URL="${APP_HEALTH_URL:-http://127.0.0.1:18888/actuator/health}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-300}"
LOG_FILE="${LOG_FILE:-/opt/nexus-quant/freeze-evidence/health/health-check-7d.log}"

mkdir -p "$(dirname "${LOG_FILE}")"

echo "GateJ-FREEZE health loop started at $(date -Is), interval=${INTERVAL_SECONDS}s" >> "${LOG_FILE}"

while true; do
  {
    echo
    echo "===== sample $(date -Is) ====="
    echo "----- actuator health -----"
    if ! curl -fsS "${APP_HEALTH_URL}"; then
      echo
      echo "ACTUATOR_HEALTH_FAILED"
    fi
    echo
    echo "----- docker ps -----"
    docker ps
    echo "----- free -h -----"
    free -h
    echo "----- df -h -----"
    df -h
  } >> "${LOG_FILE}" 2>&1

  sleep "${INTERVAL_SECONDS}"
done
