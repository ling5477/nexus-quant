#!/bin/sh
set -eu
umask 077
action=${1:-start}
[ "$#" -eq 0 ] || shift
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
package_root=${NQ_PACKAGE_ROOT:-$(dirname "$script_dir")}
install_root=${NQ_INSTALL_ROOT:-$HOME/.nexusquant}
project=${NQ_PROJECT_NAME:-nexusquant}
frontend_port=${NQ_FRONTEND_PORT:-18080}
backend_port=${NQ_BACKEND_PORT:-18888}
fail() { printf '%s\n' "$*" >&2; exit 1; }
case "$install_root" in /*) ;; *) fail 'Absolute installation directory required';; esac
case "$install_root" in /|"$HOME"|*\'*|*\$*|*\#*|*\"*) fail 'Unsafe installation directory';; esac
[ ! -L "$install_root" ] || fail 'Symlink installation directory unsupported'
case "$project" in ''|*[!a-z0-9-]*) fail 'Invalid project identity';; esac
# 所有 Docker 调用有进程超时；watcher 随命令完成立即清理。
docker_cmd() {
    exec 3<&0
    docker "$@" <&3 &
    docker_pid=$!
    (trap 'kill "$sleeper" 2>/dev/null || :; exit' TERM; sleep 600 & sleeper=$!; wait "$sleeper"; kill "$docker_pid" 2>/dev/null || :) >/dev/null 2>&1 &
    watcher=$!
    result=0
    wait "$docker_pid" || result=$?
    kill "$watcher" 2>/dev/null || :
    wait "$watcher" 2>/dev/null || :
    return "$result"
}
compose() { docker_cmd compose --env-file "$install_root/config/runtime.env" -p "$project" -f "$install_root/runtime/compose.yml" "$@"; }
value() { sed -n "s/^$1=//p" "$install_root/config/runtime.env"; }
random_secret() { dd if=/dev/urandom bs=32 count=1 2>/dev/null | od -An -tx1 | tr -d ' \n'; }
file_hash() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d ' ' -f1
    else shasum -a 256 "$1" | cut -d ' ' -f1; fi
}
start_runtime() {
    compose up -d --no-build --pull never --wait --wait-timeout 300 postgres backend >/dev/null
    compose up -d --no-build --pull never --wait --wait-timeout 120 frontend >/dev/null
    curl -fsS --max-time 10 "http://127.0.0.1:$(value BACKEND_PORT)/actuator/health" | grep -q '"status":"UP"'
    curl -fsS --max-time 10 "http://127.0.0.1:$(value FRONTEND_PORT)/" >/dev/null
}
command -v docker >/dev/null 2>&1 || fail 'Install Docker first'
command -v curl >/dev/null 2>&1 || fail 'curl is required (included by supported macOS/Linux distributions)'
docker_cmd info --format '{{.ServerVersion}}' >/dev/null
docker_cmd compose version --short >/dev/null
if [ "$action" = install ]; then
    version_path=$package_root/VERSION
    [ -f "$version_path" ] || version_path=$(dirname "$package_root")/VERSION
    version=$(tr -d '\r\n' < "$version_path")
    printf '%s' "$version" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' || fail 'Invalid VERSION'
    arch=$(docker_cmd info --format '{{.Architecture}}')
    case "$arch" in x86_64|amd64) arch=amd64;; aarch64|arm64) arch=arm64;; *) fail 'Unsupported Docker architecture';; esac
    archive=$package_root/images-$arch.tar
    expected=$(tr -d '\r\n' < "$archive.sha256")
    [ "$(file_hash "$archive")" = "$expected" ] || fail 'Release image checksum mismatch'
    docker_cmd load --input "$archive" >/dev/null
    for dir in config data/postgres logs backups runtime; do
        [ ! -L "$install_root/$dir" ] || fail 'Symlink installation paths unsupported'
        mkdir -p "$install_root/$dir"
    done
    chmod 700 "$install_root" "$install_root/config" "$install_root/backups"
    mkdir "$install_root/runtime/.operation-lock" || fail 'Another operation is in progress'
    trap 'rmdir "$install_root/runtime/.operation-lock"' EXIT HUP INT TERM
    fresh=false
    if [ ! -f "$install_root/config/runtime.env" ]; then
        fresh=true
        cat > "$install_root/config/runtime.env" <<ENV
NQ_HOME=$install_root
NQ_VERSION=$version
DB_PASSWORD=$(random_secret)
JWT_SECRET=$(random_secret)
CREDENTIALS_KEY=$(random_secret)
FRONTEND_PORT=$frontend_port
BACKEND_PORT=$backend_port
PROJECT_NAME=$project
ENV
    else
        [ "$(value NQ_VERSION)" = "$version" ] && [ "$(value PROJECT_NAME)" = "$project" ] || fail 'Rerun requires identical version and project identity'
    fi
    cp "$package_root/runtime/compose.yml" "$install_root/runtime/compose.yml"
    cp "$package_root/runtime/runtime.yml" "$install_root/runtime/runtime.yml"
    printf '%s\n' "$version" > "$install_root/runtime/VERSION"
    if [ "$script_dir" != "$install_root/runtime" ]; then cp "$0" "$install_root/runtime/nexusquant.sh"; fi
    start_runtime
    admin=$(compose exec -T postgres psql -U nexusquant -d nexus_quant -At -c "SELECT count(*) FROM users u WHERE username='admin' AND enabled AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code='ADMIN')")
    [ "$admin" = 1 ] || fail 'Bootstrap admin unavailable; existing users preserved'
    if [ "$fresh" = true ]; then
        curl -fsS --max-time 10 -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' "http://127.0.0.1:$(value BACKEND_PORT)/api/auth/login" | grep -q '"mustChangePassword":true'
        printf '%s\n' 'Initial username: admin' 'Initial password: 123456' 'Password change required on first login.'
    else printf '%s\n' 'Existing password and roles preserved.'; fi
    printf 'NexusQuant %s: http://127.0.0.1:%s\n' "$version" "$(value FRONTEND_PORT)"
    exit 0
fi
project=$(value PROJECT_NAME)
version=$(value NQ_VERSION)
mkdir "$install_root/runtime/.operation-lock" || fail 'Another operation is in progress'
trap 'rmdir "$install_root/runtime/.operation-lock"' EXIT HUP INT TERM
case "$action" in
    start) start_runtime;;
    stop) compose stop --timeout 30 >/dev/null;;
    restart) compose stop --timeout 30 >/dev/null; start_runtime;;
    backup)
        schema=$(compose exec -T postgres psql -U nexusquant -d nexus_quant -At -c 'SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1')
        [ "$schema" = 1 ] || fail 'Only V1 backups supported'
        timestamp=$(date -u +%Y%m%dT%H%M%SZ)
        dest=$install_root/backups/$timestamp
        mkdir "$dest"
        compose exec -T postgres pg_dump -U nexusquant -d nexus_quant -Fc > "$dest/database.dump"
        cp "$install_root/config/runtime.env" "$dest/runtime.env"
        cat > "$dest/manifest.env" <<MANIFEST
FORMAT=1
APP_VERSION=$version
SCHEMA_VERSION=1
POSTGRESQL_MAJOR=16
TIMESTAMP=$timestamp
DATABASE_SHA256=$(file_hash "$dest/database.dump")
CONFIG_SHA256=$(file_hash "$dest/runtime.env")
MANIFEST
        printf 'Backup: %s\n' "$dest"
        ;;
    restore)
        backup=${1:?Backup path required}
        backup_value() { sed -n "s/^$1=//p" "$backup/manifest.env"; }
        [ "$(backup_value FORMAT)" = 1 ] && [ "$(backup_value APP_VERSION)" = "$version" ] && [ "$(backup_value SCHEMA_VERSION)" = 1 ] && [ "$(backup_value POSTGRESQL_MAJOR)" = 16 ] || fail 'Incompatible backup'
        [ "$(file_hash "$backup/database.dump")" = "$(backup_value DATABASE_SHA256)" ] && [ "$(file_hash "$backup/runtime.env")" = "$(backup_value CONFIG_SHA256)" ] || fail 'Backup checksum mismatch'
        for key in JWT_SECRET CREDENTIALS_KEY; do
            restored=$(sed -n "s/^$key=//p" "$backup/runtime.env")
            printf '%s' "$restored" | grep -Eq '^[a-f0-9]{64}$' || fail 'Invalid backup key metadata'
        done
        compose stop --timeout 30 frontend backend >/dev/null
        compose up -d --no-build --pull never --wait --wait-timeout 120 postgres >/dev/null
        compose exec -T postgres pg_restore -U nexusquant -d nexus_quant --clean --if-exists --no-owner --exit-on-error --single-transaction < "$backup/database.dump"
        for key in JWT_SECRET CREDENTIALS_KEY; do
            restored=$(sed -n "s/^$key=//p" "$backup/runtime.env")
            printf '%s' "$restored" | grep -Eq '^[a-f0-9]{64}$' || fail 'Invalid backup key metadata'
            sed "s/^$key=.*/$key=$restored/" "$install_root/config/runtime.env" > "$install_root/config/runtime.env.tmp"
            mv "$install_root/config/runtime.env.tmp" "$install_root/config/runtime.env"
        done
        start_runtime
        ;;
    uninstall)
        compose down --timeout 30 >/dev/null
        case "${1:-}" in '') ;; --purge-data)
            for name in data backups; do
                [ ! -L "$install_root/$name" ] || fail 'Unsafe purge target'
                rm -rf -- "$install_root/$name"
            done;; *) fail 'Unknown uninstall option';; esac
        ;;
    *) fail 'Unknown action';;
esac
