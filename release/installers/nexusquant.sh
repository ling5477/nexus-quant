#!/bin/sh
set -eu
umask 077
action=${1:-start}
[ "$#" -eq 0 ] || shift
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
package_root=${NQ_PACKAGE_ROOT:-$(dirname "$script_dir")}
install_root=${NQ_INSTALL_ROOT:-$HOME/.nexusquant}
project=${NQ_PROJECT_NAME:-nexusquant}
frontend_port=${NQ_FRONTEND_PORT:-18080}
backend_port=${NQ_BACKEND_PORT:-18888}
target_package= trusted_hash= confirm=false purge=false confirm_purge=false backup_path=
docker_group=false lock_owned=false transaction_active=false transaction_dir= update_app_stopped=false
recovery_lock_owned=false bootstrap_dir= bootstrap_mounted=false
manifest_workdir= verified_manifest= package_stage=
selected_backend_image= selected_frontend_image= selected_postgres_image=
update_attempt_active=false attempt_schema=UNKNOWN
fail() { printf '%s\n' "$*" >&2; exit 1; }
meta() { sed -n "s/^$2=//p" "$1"; }
value() { meta "$install_root/config/runtime.env" "$1"; }
file_hash() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d ' ' -f1
    else shasum -a 256 "$1" | cut -d ' ' -f1; fi
}
hex_hash() { printf '%s' "$1" | grep -Eq '^[a-f0-9]{64}$'; }
strict_lf() {
    # 按字节校验，避免 fixture 平台文本模式自动剥除 CR 后误接受非规范元数据。
    [ "$(od -An -tx1 -N3 "$1" | tr -d ' \n')" != efbbbf ] || fail 'UTF-8 BOM is not allowed in metadata'
    [ "$(tail -c 1 "$1" | od -An -tx1 | tr -d ' \n')" = 0a ] || fail 'Metadata must end with an LF newline'
    if od -An -v -tx1 "$1" | grep -Eq '(^| )0(d|0)( |$)'; then fail 'Metadata requires LF only and no NUL bytes'; fi
}
safe_path() {
    case "$1" in /*) ;; *) fail 'Absolute directory required';; esac
    case "$1" in /|"$HOME"|*\'*|*\$*|*\#*|*\"*|*'|'*|*'='*) fail 'Unsafe installation or package directory';; esac
    printf '%s' "$1" | grep -q '[[:cntrl:]]' && fail 'Control characters in path are unsupported'
    # 逐级拒绝链接及点段；删除、恢复和挂载只能作用于明确的安装树。
    rest=${1#/} path=/
    while [ -n "$rest" ]; do
        part=${rest%%/*}
        case "$part" in ''|.|..|' '*|*' ') fail 'Ambiguous directory unsupported';; esac
        path=${path%/}/$part
        [ ! -L "$path" ] || fail 'Symlink paths unsupported'
        case "$rest" in */*) rest=${rest#*/};; *) rest=;; esac
    done
}
safe_path "$install_root"
case "$project" in ''|*[!a-z0-9-]*) fail 'Invalid project identity';; esac
port_valid() { printf '%s' "$1" | grep -Eq '^[0-9]{2,5}$' && [ "$1" -ge 1024 ] && [ "$1" -le 65535 ]; }
port_valid "$frontend_port" && port_valid "$backend_port" || fail 'Ports must be integers between 1024 and 65535'
[ "$frontend_port" != "$backend_port" ] || fail 'Frontend and backend ports must differ'
while [ "$#" -gt 0 ]; do
    case "$1" in
        --package) [ "$#" -ge 2 ] || fail 'Package directory required'; target_package=$2; shift 2;;
        --manifest-sha256) [ "$#" -ge 2 ] || fail 'Independent trusted manifest SHA256 required'; trusted_hash=$2; shift 2;;
        --yes) confirm=true; shift;;
        --purge-data) purge=true; shift;;
        --confirm-purge) confirm_purge=true; shift;;
        *) [ "$action" = restore ] && [ -z "$backup_path" ] || fail 'Unknown option'; backup_path=$1; shift;;
    esac
done
case "$action" in install|start|stop|restart|status|doctor|backup|restore|uninstall|check-update|update|rollback) ;; *) fail 'Unknown action';; esac
[ "$confirm_purge" = false ] || [ "$purge" = true ] || fail '--confirm-purge requires --purge-data'
[ "$purge" = false ] || [ "$action" = uninstall ] || fail 'Purge is only supported by uninstall'
if [ -n "$target_package$trusted_hash" ]; then
    case "$action" in check-update|update|doctor) ;; *) fail 'Package/trust options are only valid for check-update, update or doctor';; esac
fi
if [ "$confirm" = true ]; then case "$action" in update|rollback) ;; *) fail '--yes is only valid for update or rollback';; esac; fi
# 每个外调有总时限；Docker 子命令继承标准输入以支持原子数据库恢复。
bounded() (
    limit=$1; shift
    if command -v timeout >/dev/null 2>&1; then
        exec timeout --signal=TERM --kill-after=10 "$limit" "$@"
    fi
    "$@" <&0 & child=$!
    (trap 'kill "$sleeper" 2>/dev/null || :; exit' TERM; sleep "$limit" & sleeper=$!; wait "$sleeper"; kill "$child" 2>/dev/null || :; sleep 10; kill -9 "$child" 2>/dev/null || :) >/dev/null 2>&1 & watcher=$!
    result=0; wait "$child" || result=$?
    kill "$watcher" 2>/dev/null || :; wait "$watcher" 2>/dev/null || :
    exit "$result"
)
ubuntu_apt() (
    # sudo 默认清空代理；只向本次 APT 子进程继承三个代理键，不改变调用者或系统配置。
    http_proxy=${http_proxy:-${HTTP_PROXY:-}}
    https_proxy=${https_proxy:-${HTTPS_PROXY:-}}
    no_proxy=${no_proxy:-${NO_PROXY:-}}
    export http_proxy https_proxy no_proxy
    bounded 900 sudo --preserve-env=http_proxy,https_proxy,no_proxy timeout --kill-after=10 850 apt-get -o Acquire::Retries=2 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 "$@"
)
docker_cmd() {
    if [ "$docker_group" = true ]; then
        # sg 只接受命令字符串；逐参数单引号编码，禁止原始路径或用户输入拼接。
        quoted='docker'
        for argument do encoded=$(printf '%s' "$argument" | sed "s/'/'\\\\''/g"); quoted="$quoted '$encoded'"; done
        bounded "${docker_timeout:-600}" sg docker -c "$quoted"
    else bounded "${docker_timeout:-600}" docker "$@"; fi
}
compose_with_env() (
    selected_env=$1; selected_project=$2; selected_compose=$3; shift 3
    # Compose 优先使用父进程环境；必须隔离这些键才能使已验证配置成为唯一挂载及镜像身份。
    unset NQ_HOME NQ_VERSION DB_PASSWORD JWT_SECRET CREDENTIALS_KEY FRONTEND_PORT BACKEND_PORT PROJECT_NAME AUTO_UPDATE RUNTIME_PROFILE BACKEND_MEMORY BACKEND_CPUS POSTGRES_MEMORY POSTGRES_CPUS FRONTEND_MEMORY FRONTEND_CPUS SCHEMA_VERSION SOURCE_SHA RELEASE_SOURCE_HASH BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE PACKAGE_MANIFEST_SHA256
    unset COMPOSE_FILE COMPOSE_PROJECT_NAME COMPOSE_ENV_FILES COMPOSE_PROFILES COMPOSE_DISABLE_ENV_FILE COMPOSE_PATH_SEPARATOR DOCKER_DEFAULT_PLATFORM
    docker_cmd compose --env-file "$selected_env" -p "$selected_project" -f "$selected_compose" "$@"
)
compose() { compose_with_env "$install_root/config/runtime.env" "$project" "$install_root/runtime/compose.yml" "$@"; }
db_query() { compose exec -T postgres psql -v ON_ERROR_STOP=1 -U nexusquant -d nexus_quant -At -c "$1"; }
atomic_copy() {
    cp "$1" "$2.tmp"
    copy_mode=600
    # 静态运行配置不含秘密；bind mount 的后端 UID 不同，必须允许读取实际安装文件。
    [ "$2" != "$install_root/runtime/runtime.yml" ] || copy_mode=644
    chmod "$copy_mode" "$2.tmp"
    mv -f "$2.tmp" "$2"
}
atomic_text() { printf '%s\n' "$1" > "$2.tmp"; chmod 600 "$2.tmp"; mv -f "$2.tmp" "$2"; }
version_valid() { printf '%s' "$1" | grep -Eq '^[0-9]{1,6}\.[0-9]{1,6}\.[0-9]{1,6}$'; }
version_greater() { awk -v a="$1" -v b="$2" 'BEGIN{split(a,x,".");split(b,y,".");for(i=1;i<=3;i++){if(x[i]+0>y[i]+0)exit 0;if(x[i]+0<y[i]+0)exit 1}exit 1}'; }
verify_manifest() {
    [ -f "$1" ] && [ ! -L "$1" ] || fail 'Release package manifest missing or unsafe'
    [ "$(wc -c < "$1" | tr -d ' ')" -le 8192 ] || fail 'Release manifest is too large'
    strict_lf "$1"
    awk -F= 'BEGIN {n=split("FORMAT VERSION SOURCE_SHA RELEASE_SOURCE_HASH SCHEMA_VERSION ARCH ARCHIVE ARCHIVE_SHA256 BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE BACKEND_CONFIG_DIGEST FRONTEND_CONFIG_DIGEST POSTGRES_CONFIG_DIGEST COMPOSE_SHA256 RUNTIME_SHA256 PS_INSTALLER_SHA256 SH_INSTALLER_SHA256",keys," ");for(i=1;i<=n;i++)allowed[keys[i]]=1}
      NF!=2 || !allowed[$1] || seen[$1]++ || $2=="" || $0~/\r/ {exit 1}
      END {for(i=1;i<=n;i++)if(!seen[keys[i]])exit 1}' "$1" || fail 'Invalid release manifest fields (LF, exact allowlist and unique keys required)'
    [ "$(meta "$1" FORMAT)" = 1 ] || fail 'Unsupported release manifest format'
    version_valid "$(meta "$1" VERSION)" || fail 'Invalid package version'
    printf '%s' "$(meta "$1" SOURCE_SHA)" | grep -Eq '^[a-f0-9]{40}$' || fail 'Invalid source SHA'
    printf '%s' "$(meta "$1" SCHEMA_VERSION)" | grep -Eq '^[1-9][0-9]{0,5}$' || fail 'Invalid schema version'
    case "$(meta "$1" ARCH)" in amd64|arm64) ;; *) fail 'Unsupported manifest architecture';; esac
    [ "$(meta "$1" ARCHIVE)" = "images-$(meta "$1" ARCH).tar" ] || fail 'Invalid image archive name'
    for field in RELEASE_SOURCE_HASH ARCHIVE_SHA256 COMPOSE_SHA256 RUNTIME_SHA256 PS_INSTALLER_SHA256 SH_INSTALLER_SHA256; do
        hex_hash "$(meta "$1" "$field")" || fail 'Invalid manifest digest'
    done
    for field in BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE BACKEND_CONFIG_DIGEST FRONTEND_CONFIG_DIGEST POSTGRES_CONFIG_DIGEST; do
        image=$(meta "$1" "$field")
        case "$image" in sha256:*) hex_hash "${image#sha256:}" || fail 'Exact declared immutable image/config digest required';; *) fail 'Exact declared immutable image/config digest required';; esac
    done
}
verify_package() {
    safe_path "$1"
    manifest_source=$1/package-$host_arch.env
    [ -f "$manifest_source" ] && [ ! -L "$manifest_source" ] || fail 'Release manifest source missing or unsafe'
    [ -z "$manifest_workdir" ] || rm -rf -- "$manifest_workdir"
    manifest_workdir=$(mktemp -d "${TMPDIR:-/tmp}/nq-release.XXXXXX")
    # 先冻结受限副本再校验 trust anchor；后续读取同一字节候选，避免替换源 metadata。
    verified_manifest=$manifest_workdir/package.env
    cp "$manifest_source" "$verified_manifest"
    manifest=$verified_manifest
    verify_manifest "$manifest"
    [ "$(meta "$manifest" ARCH)" = "$host_arch" ] || fail 'Package architecture mismatch'
    [ -z "$2" ] || { hex_hash "$2" && [ "$(file_hash "$manifest")" = "$2" ]; } || fail 'Trusted manifest SHA256 mismatch'
    for mapping in 'runtime/compose.yml:COMPOSE_SHA256' 'runtime/runtime.yml:RUNTIME_SHA256' 'installers/nexusquant.ps1:PS_INSTALLER_SHA256' 'installers/nexusquant.sh:SH_INSTALLER_SHA256'; do
        asset=${mapping%:*}; field=${mapping#*:}
        [ -f "$1/$asset" ] && [ ! -L "$1/$asset" ] && [ "$(file_hash "$1/$asset")" = "$(meta "$manifest" "$field")" ] || fail 'Release asset checksum mismatch'
    done
    archive=$1/$(meta "$manifest" ARCHIVE)
    [ -f "$archive" ] && [ ! -L "$archive" ] && [ "$(file_hash "$archive")" = "$(meta "$manifest" ARCHIVE_SHA256)" ] || fail 'Release image archive checksum mismatch'
}
host_identity() {
    host_os=$(uname -s)
    case "$(uname -m)" in x86_64|amd64) host_arch=amd64;; arm64|aarch64) host_arch=arm64;; *) fail 'Unsupported host architecture';; esac
    case "$host_os" in
        Darwin) host_version=$(sw_vers -productVersion);;
        Linux)
            [ -f /etc/os-release ] || fail 'Ubuntu OS identity unavailable'
            os_id=$(sed -n 's/^ID=//p' /etc/os-release | tr -d '"')
            host_version=$(sed -n 's/^VERSION_ID=//p' /etc/os-release | tr -d '"')
            [ "$os_id" = ubuntu ] || fail 'This installer requires Ubuntu 22.04 or 24.04'
            case "$host_version" in 22.04|24.04) ;; *) fail 'This installer requires Ubuntu 22.04 or 24.04';; esac;;
        *) fail 'This entry point requires macOS or Ubuntu';;
    esac
}
bootstrap_docker() {
    if [ -n "${DOCKER_HOST:-}" ]; then case "$DOCKER_HOST" in unix:///*) ;; *) fail 'Remote DOCKER_HOST is unsupported; select a local isolated Docker engine before this operation';; esac; fi
    if [ "$action" = install ] && ! command -v curl >/dev/null 2>&1; then
        [ "$host_os" = Linux ] || fail 'The system curl required by macOS is unavailable'
        printf '%s\n' 'Installing Ubuntu curl and CA prerequisites for official Docker bootstrap and local health checks.'
        ubuntu_apt update >/dev/null 2>&1 || fail 'Ubuntu curl prerequisite package index update failed'
        ubuntu_apt -y install ca-certificates curl >/dev/null 2>&1 || fail 'Ubuntu curl prerequisite installation failed'
    fi
    # Desktop 已存在而 CLI 未加入 PATH 时直接复用，不重复下载或覆盖用户安装。
    if [ "$host_os" = Darwin ] && ! command -v docker >/dev/null 2>&1 && [ -x /Applications/Docker.app/Contents/Resources/bin/docker ]; then
        PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"; export PATH
    fi
    if ! command -v docker >/dev/null 2>&1; then
        [ "$action" = install ] || fail 'Docker is absent; run installer for official bootstrap'
        [ "$(id -u)" != 0 ] || fail 'Run NQ installer as a normal user; sudo is used only for Docker host setup'
        printf '%s\n' 'Docker absent: installing the official Docker distribution (privilege prompt may appear).'
        bootstrap_dir=$(mktemp -d "${TMPDIR:-/tmp}/nq-docker.XXXXXX")
        case "$host_os" in
            Darwin)
                desktop_arch=amd64; [ "$host_arch" != arm64 ] || desktop_arch=arm64
                curl -fsS --connect-timeout 20 --max-time 900 --proto '=https' --proto-redir '=https' "https://desktop.docker.com/mac/main/$desktop_arch/Docker.dmg" -o "$bootstrap_dir/Docker.dmg" || fail 'Official Docker DMG download failed'
                bounded 60 hdiutil attach "$bootstrap_dir/Docker.dmg" -nobrowse -readonly -mountpoint "$bootstrap_dir/mount" >/dev/null 2>&1 || fail 'Docker DMG mount failed'
                bootstrap_mounted=true
                bounded 120 codesign --verify --deep --strict "$bootstrap_dir/mount/Docker.app" >/dev/null 2>&1 || fail 'Docker app signature verification failed'
                bounded 60 codesign -dv --verbose=4 "$bootstrap_dir/mount/Docker.app" 2> "$bootstrap_dir/signature.txt" || fail 'Docker app signing identity unavailable'
                grep -qx 'TeamIdentifier=9BNSXJN65R' "$bootstrap_dir/signature.txt" || fail 'Docker app signer is not Docker Inc'
                [ ! -e /Applications/Docker.app ] || fail 'Existing Docker.app preserved; repair Docker CLI integration explicitly'
                bounded 300 sudo -p 'Docker host setup requires administrator approval: ' ditto "$bootstrap_dir/mount/Docker.app" /Applications/Docker.app >/dev/null 2>&1 || fail 'Docker app installation failed'
                bounded 60 hdiutil detach "$bootstrap_dir/mount" >/dev/null 2>&1 || fail 'Docker DMG detach failed'
                bootstrap_mounted=false
                bounded 60 open /Applications/Docker.app >/dev/null 2>&1 || fail 'Docker Desktop launch failed'
                PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"; export PATH
                ;;
            Linux)
                ubuntu_apt update >/dev/null 2>&1 || fail 'Ubuntu package index update failed'
                ubuntu_apt -y install ca-certificates curl gnupg >/dev/null 2>&1 || fail 'Ubuntu Docker prerequisites installation failed'
                curl -fsS --connect-timeout 20 --max-time 120 --proto '=https' --proto-redir '=https' https://download.docker.com/linux/ubuntu/gpg -o "$bootstrap_dir/docker.asc" || fail 'Official Docker repository key download failed'
                bounded 60 gpg --batch --show-keys --with-colons "$bootstrap_dir/docker.asc" > "$bootstrap_dir/key-info" 2>/dev/null || fail 'Docker repository key validation failed'
                fingerprint=$(awk -F: '$1=="fpr"{print $10;exit}' "$bootstrap_dir/key-info")
                [ "$fingerprint" = 9DC858229FC7DD38854AE2D88D81803C0EBFCD88 ] || fail 'Docker repository key fingerprint mismatch'
                bounded 60 sudo install -m 0755 -d /etc/apt/keyrings >/dev/null 2>&1 || fail 'Docker keyring directory setup failed'
                if [ -f /etc/apt/keyrings/docker.asc ]; then
                    [ "$(file_hash /etc/apt/keyrings/docker.asc)" = "$(file_hash "$bootstrap_dir/docker.asc")" ] || fail 'Existing Docker repository key preserved; explicit repair required'
                else bounded 60 sudo install -m 0644 "$bootstrap_dir/docker.asc" /etc/apt/keyrings/docker.asc >/dev/null 2>&1 || fail 'Docker repository key installation failed'; fi
                codename=$(sed -n 's/^VERSION_CODENAME=//p' /etc/os-release)
                case "$codename" in jammy|noble) ;; *) fail 'Unsupported Ubuntu repository codename';; esac
                printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' "$host_arch" "$codename" > "$bootstrap_dir/docker.list"
                [ ! -e /etc/apt/sources.list.d/nexusquant-docker.list ] || cmp -s "$bootstrap_dir/docker.list" /etc/apt/sources.list.d/nexusquant-docker.list || fail 'Existing repository configuration preserved'
                bounded 60 sudo install -m 0644 "$bootstrap_dir/docker.list" /etc/apt/sources.list.d/nexusquant-docker.list >/dev/null 2>&1 || fail 'Docker repository configuration failed'
                ubuntu_apt update >/dev/null 2>&1 || fail 'Official Docker repository unavailable'
                ubuntu_apt -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin >/dev/null 2>&1 || fail 'Official Docker Engine installation failed'
                bounded 120 sudo systemctl start docker >/dev/null 2>&1 || fail 'Docker daemon start failed'
                normal_user=$(id -un); printf '%s' "$normal_user" | grep -Eq '^[a-zA-Z_][a-zA-Z0-9_.-]*[$]?$' || fail 'Unsupported user identity'
                bounded 60 sudo usermod -aG docker "$normal_user" >/dev/null 2>&1 || fail 'Docker group enrollment failed'
                docker_group=true
                printf '%s\n' 'Docker group grants host administration. New login sessions receive membership; this run uses a bounded sg subprocess.'
                ;;
        esac
        # 临时目录不含凭据；只删除本次 mktemp 返回的子树。
        rm -rf -- "$bootstrap_dir"
        bootstrap_dir=
    fi
    if [ "$host_os" = Darwin ] && ! command -v docker >/dev/null 2>&1 && [ -x /Applications/Docker.app/Contents/Resources/bin/docker ]; then
        PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"; export PATH
    fi
    command -v docker >/dev/null 2>&1 || fail 'Docker CLI unavailable after bootstrap'
    # context inspect 仅查询本地 CLI 配置；在任何 daemon 连接之前拒绝远程端点。
    context=$(docker_timeout=10 docker_cmd context inspect --format '{{(index .Endpoints "docker").Host}}' 2>/dev/null) || fail 'Docker context unavailable'
    case "$context" in unix:///*) ;; *) fail 'Local Docker Unix socket required; remote Docker contexts are unsupported';; esac
    if [ "$host_os" = Linux ] && [ -S /var/run/docker.sock ] && id -nG "$(id -un)" | tr ' ' '\n' | grep -qx docker; then
        if ! docker_timeout=10 docker_cmd info --format '{{.ServerVersion}}' >/dev/null 2>&1; then docker_group=true; fi
    fi
    if ! docker_timeout=10 docker_cmd info --format '{{.ServerVersion}}' >/dev/null 2>&1; then
        [ "$action" = install ] || fail 'Docker daemon unavailable; start Docker before this operation'
        case "$host_os" in
            Darwin) bounded 60 open -a Docker >/dev/null 2>&1 || fail 'Docker Desktop launch failed';;
            Linux)
                if [ -S /var/run/docker.sock ] && id -nG "$(id -un)" | tr ' ' '\n' | grep -qx docker; then docker_group=true; fi
                if ! docker_timeout=10 docker_cmd info --format '{{.ServerVersion}}' >/dev/null 2>&1; then
                    bounded 120 sudo systemctl start docker >/dev/null 2>&1 || fail 'Docker daemon unavailable; check service and docker group access'
                    if id -nG "$(id -un)" | tr ' ' '\n' | grep -qx docker; then docker_group=true; fi
                fi;;
        esac
        deadline=$(($(date +%s) + 300))
        while ! docker_timeout=10 docker_cmd info --format '{{.ServerVersion}}' >/dev/null 2>&1; do
            [ "$(date +%s)" -lt "$deadline" ] || fail 'Docker daemon did not become ready within 300 seconds'
            sleep 5
        done
    fi
    docker_version=$(docker_cmd info --format '{{.ServerVersion}}' 2>/dev/null)
    compose_version=$(docker_cmd compose version --short 2>/dev/null) || fail 'Docker Compose plugin unavailable; install official Compose v2 >= 2.20.0'
    docker_clean=${docker_version%%-*}; compose_clean=${compose_version#v}; compose_clean=${compose_clean%%-*}
    version_valid "$docker_clean" && { [ "$docker_clean" = 24.0.0 ] || version_greater "$docker_clean" 24.0.0; } || fail 'Docker Engine >= 24.0.0 required; use the official supported upgrade path, then retry'
    version_valid "$compose_clean" && { [ "$compose_clean" = 2.20.0 ] || version_greater "$compose_clean" 2.20.0; } || fail 'Docker Compose >= 2.20.0 required; upgrade the official plugin explicitly'
    docker_arch=$(docker_cmd info --format '{{.Architecture}}' 2>/dev/null)
    case "$docker_arch" in x86_64|amd64) docker_arch=amd64;; aarch64|arm64) docker_arch=arm64;; *) fail 'Unsupported Docker architecture';; esac
    [ "$docker_arch" = "$host_arch" ] || fail 'Docker/host architecture mismatch'
}
preflight() {
    case "$host_os" in
        Darwin) host_cpu=$(sysctl -n hw.logicalcpu); host_ram=$(sysctl -n hw.memsize); virtualization=$(sysctl -n kern.hv_support 2>/dev/null || printf UNKNOWN);;
        Linux) host_cpu=$(getconf _NPROCESSORS_ONLN); host_ram=$(awk '/^MemTotal:/{printf "%.0f",$2*1024}' /proc/meminfo); virtualization=UNKNOWN; grep -Eq '(vmx|svm)' /proc/cpuinfo && virtualization=AVAILABLE;;
    esac
    docker_cpu=$(docker_cmd info --format '{{.NCPU}}' 2>/dev/null)
    docker_ram=$(docker_cmd info --format '{{.MemTotal}}' 2>/dev/null)
    for number in "$host_cpu" "$host_ram" "$docker_cpu" "$docker_ram"; do printf '%s' "$number" | grep -Eq '^[0-9]+$' || fail 'Host/Docker capacity unavailable'; done
    effective_cpu=$host_cpu; [ "$docker_cpu" -ge "$host_cpu" ] || effective_cpu=$docker_cpu
    effective_ram=$host_ram; [ "$docker_ram" -ge "$host_ram" ] || effective_ram=$docker_ram
    disk_parent=$install_root; while [ ! -d "$disk_parent" ]; do disk_parent=$(dirname "$disk_parent"); done
    disk_kb=$(df -Pk "$disk_parent" | awk 'END{print $4}')
    printf '%s' "$disk_kb" | grep -Eq '^[0-9]+$' || fail 'Free disk measurement unavailable'
    [ "$effective_cpu" -ge 2 ] && [ "$effective_ram" -ge 4294967296 ] && [ "$disk_kb" -ge 10485760 ] || fail 'Insufficient capacity: require effective 2 CPU / 4 GiB RAM / 10 GiB free disk; adjust Docker resources explicitly'
    if [ "$effective_cpu" -lt 4 ] || [ "$effective_ram" -lt 8589934592 ]; then
        runtime_profile=LIGHT; backend_memory=768m; backend_cpus=1; postgres_memory=512m; postgres_cpus=0.5; frontend_memory=128m; frontend_cpus=0.25
    elif [ "$effective_cpu" -lt 8 ] || [ "$effective_ram" -lt 17179869184 ]; then
        runtime_profile=STANDARD; backend_memory=2048m; backend_cpus=2; postgres_memory=1024m; postgres_cpus=1; frontend_memory=256m; frontend_cpus=0.5
    else runtime_profile=PERFORMANCE; backend_memory=4096m; backend_cpus=4; postgres_memory=2048m; postgres_cpus=2; frontend_memory=256m; frontend_cpus=0.5; fi
    proxy=ABSENT
    [ -z "${HTTP_PROXY:-}${HTTPS_PROXY:-}${NO_PROXY:-}${http_proxy:-}${https_proxy:-}${no_proxy:-}" ] || proxy=PRESENT
    system_proxy=UNKNOWN
    case "$host_os" in
        Darwin) if bounded 10 scutil --proxy 2>/dev/null | grep -Eq '(HTTPEnable|HTTPSEnable|ProxyAutoConfigEnable) : 1'; then system_proxy=PRESENT; else system_proxy=ABSENT; fi;;
        Linux) if [ -f /etc/environment ]; then system_proxy=ABSENT; grep -Eqi '^[[:space:]]*(http_proxy|https_proxy|no_proxy)=' /etc/environment && system_proxy=PRESENT; fi;;
    esac
    printf 'OS=%s VERSION=%s ARCH=%s CPU=%s RAM_BYTES=%s DISK_KIB=%s VIRTUALIZATION=%s DOCKER=%s COMPOSE=%s PROXY=%s SYSTEM_PROXY=%s PROFILE=%s\n' "$host_os" "$host_version" "$host_arch" "$effective_cpu" "$effective_ram" "$disk_kb" "$virtualization" "$docker_version" "$compose_version" "$proxy" "$system_proxy" "$runtime_profile"
}
port_check() {
    for port in "$frontend_port" "$backend_port"; do
        service=frontend; [ "$port" != "$backend_port" ] || service=backend
        if [ -f "$install_root/config/runtime.env" ]; then
            owner_id=$(compose ps -q "$service" 2>/dev/null) || owner_id=
            if [ -n "$owner_id" ]; then
                owner_ports=$(docker_cmd inspect --format '{{range .NetworkSettings.Ports}}{{range .}}{{if eq .HostIp "127.0.0.1"}}{{.HostPort}}{{println}}{{end}}{{end}}{{end}}' "$owner_id" 2>/dev/null) || owner_ports=
                if printf '%s\n' "$owner_ports" | grep -qx "$port"; then continue; fi
            fi
        fi
        case "$host_os" in
            Darwin) if bounded 10 lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then fail 'Port occupied; set NQ_FRONTEND_PORT/NQ_BACKEND_PORT to unused loopback ports and retry'; fi;;
            Linux)
                command -v ss >/dev/null 2>&1 || fail 'Ubuntu ss is required for port preflight'
                listeners=$(bounded 10 ss -H -ltn "sport = :$port" 2>/dev/null) || fail 'Loopback port preflight unavailable'
                [ -z "$listeners" ] || fail 'Port occupied; set NQ_FRONTEND_PORT/NQ_BACKEND_PORT to unused loopback ports and retry';;
        esac
    done
    printf 'PORTS=AVAILABLE FRONTEND=%s BACKEND=%s\n' "$frontend_port" "$backend_port"
}
set_env() {
    awk -v key="$1" -v replacement="$2" 'BEGIN{done=0}index($0,key"=")==1{print key"="replacement;done=1;next}{print}END{if(!done)print key"="replacement}' "$install_root/config/runtime.env" > "$install_root/config/runtime.env.tmp"
    chmod 600 "$install_root/config/runtime.env.tmp"; mv -f "$install_root/config/runtime.env.tmp" "$install_root/config/runtime.env"
}
verify_installed() {
    verify_runtime_env "$install_root/config/runtime.env"
    installed_manifest=$install_root/runtime/package.env
    verify_manifest "$installed_manifest"
    [ "$(meta "$installed_manifest" ARCH)" = "$host_arch" ] || fail 'Installed release architecture does not match host'
    [ "$(file_hash "$installed_manifest")" = "$(value PACKAGE_MANIFEST_SHA256)" ] || fail 'Installed manifest trust anchor mismatch'
    for mapping in 'compose.yml:COMPOSE_SHA256' 'runtime.yml:RUNTIME_SHA256' 'nexusquant.sh:SH_INSTALLER_SHA256'; do
        [ -f "$install_root/runtime/${mapping%:*}" ] && [ ! -L "$install_root/runtime/${mapping%:*}" ] && [ "$(file_hash "$install_root/runtime/${mapping%:*}")" = "$(meta "$installed_manifest" "${mapping#*:}")" ] || fail 'Installed asset checksum mismatch'
    done
    [ "$(meta "$installed_manifest" VERSION)" = "$(value NQ_VERSION)" ] && [ "$(meta "$installed_manifest" SCHEMA_VERSION)" = "$(value SCHEMA_VERSION)" ] || fail 'Installed version/schema identity mismatch'
    [ -f "$install_root/runtime/VERSION" ] && [ ! -L "$install_root/runtime/VERSION" ] && [ "$(cat "$install_root/runtime/VERSION")" = "$(meta "$installed_manifest" VERSION)" ] || fail 'Authoritative installed VERSION mismatch'
    for field in SOURCE_SHA RELEASE_SOURCE_HASH; do
        [ "$(meta "$installed_manifest" "$field")" = "$(value "$field")" ] || fail 'Installed runtime image/source identity mismatch'
    done
    for field in BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE; do image_in_manifest "$installed_manifest" "$field" "$(value "$field")" || fail 'Installed selected image is not a declared immutable identity'; done
    [ "$(value NQ_HOME)" = "$install_root" ] || fail 'Installation location mismatch'
    project=$(value PROJECT_NAME); case "$project" in ''|*[!a-z0-9-]*) fail 'Invalid installed project identity';; esac
    for key in DB_PASSWORD JWT_SECRET CREDENTIALS_KEY; do hex_hash "$(value "$key")" || fail 'Invalid installed secret metadata'; done
    [ "$(value AUTO_UPDATE)" = OFF ] || fail 'Automatic update must remain OFF'
    frontend_port=$(value FRONTEND_PORT); backend_port=$(value BACKEND_PORT)
    port_valid "$frontend_port" && port_valid "$backend_port" && [ "$frontend_port" != "$backend_port" ] || fail 'Invalid installed ports'
}
image_in_manifest() {
    [ "$3" = "$(meta "$1" "$2")" ] || [ "$3" = "$(meta "$1" "${2%_IMAGE}_CONFIG_DIGEST")" ]
}
resolve_image() {
    # 两种 store 的寻址身份不同；只探测包声明的 index/native ID 与 config ID，绝不使用 tag 回退。
    for candidate_id in "$(meta "$1" "$2")" "$(meta "$1" "${2%_IMAGE}_CONFIG_DIGEST")"; do
        observed_identity=$(docker_timeout=30 docker_cmd image inspect "$candidate_id" --format '{{.Id}}|{{.Os}}|{{.Architecture}}' 2>/dev/null) || continue
        [ "$observed_identity" = "$candidate_id|linux|$host_arch" ] || fail 'Addressed image ID/platform differs from its exact declared immutable candidate'
        printf '%s\n' "$candidate_id"; return 0
    done
    fail 'No declared immutable image ID is addressable on this Docker store with the exact required platform'
}
resolve_images() {
    selected_backend_image=$(resolve_image "$1" BACKEND_IMAGE) || fail 'Backend immutable identity resolution failed'
    selected_frontend_image=$(resolve_image "$1" FRONTEND_IMAGE) || fail 'Frontend immutable identity resolution failed'
    selected_postgres_image=$(resolve_image "$1" POSTGRES_IMAGE) || fail 'PostgreSQL immutable identity resolution failed'
}
set_selected_images() {
    set_env BACKEND_IMAGE "$selected_backend_image"
    set_env FRONTEND_IMAGE "$selected_frontend_image"
    set_env POSTGRES_IMAGE "$selected_postgres_image"
}
verify_runtime_env() {
    [ -f "$1" ] && [ ! -L "$1" ] || fail 'Runtime configuration missing or unsafe'
    [ "$(wc -c < "$1" | tr -d ' ')" -le 16384 ] || fail 'Runtime configuration is too large'
    strict_lf "$1"
    awk -F= 'BEGIN{n=split("NQ_HOME NQ_VERSION DB_PASSWORD JWT_SECRET CREDENTIALS_KEY FRONTEND_PORT BACKEND_PORT PROJECT_NAME AUTO_UPDATE RUNTIME_PROFILE BACKEND_MEMORY BACKEND_CPUS POSTGRES_MEMORY POSTGRES_CPUS FRONTEND_MEMORY FRONTEND_CPUS SCHEMA_VERSION SOURCE_SHA RELEASE_SOURCE_HASH BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE PACKAGE_MANIFEST_SHA256",k," ");for(i=1;i<=n;i++)a[k[i]]=1}NF!=2||!a[$1]||seen[$1]++||$2==""||$0~/\r/{exit 1}END{for(i=1;i<=n;i++)if(!seen[k[i]])exit 1}' "$1" || fail 'Runtime configuration requires exact known fields and unique LF keys'
    for field in BACKEND_MEMORY POSTGRES_MEMORY FRONTEND_MEMORY; do printf '%s' "$(meta "$1" "$field")" | grep -Eq '^[1-9][0-9]{1,5}m$' || fail 'Invalid runtime memory allocation'; done
    for field in BACKEND_CPUS POSTGRES_CPUS FRONTEND_CPUS; do printf '%s' "$(meta "$1" "$field")" | grep -Eq '^[0-9]{1,2}(\.[0-9]{1,2})?$' || fail 'Invalid runtime CPU allocation'; done
    case "$(meta "$1" RUNTIME_PROFILE)" in LIGHT|STANDARD|PERFORMANCE) ;; *) fail 'Invalid installed runtime profile';; esac
}
load_images() {
    package_stage=$(mktemp -d "$install_root/runtime/.package-stage.XXXXXX")
    mkdir "$package_stage/runtime" "$package_stage/installers"
    for mapping in 'runtime/compose.yml:COMPOSE_SHA256' 'runtime/runtime.yml:RUNTIME_SHA256' 'installers/nexusquant.ps1:PS_INSTALLER_SHA256' 'installers/nexusquant.sh:SH_INSTALLER_SHA256'; do
        bounded 60 cp "$1/${mapping%:*}" "$package_stage/${mapping%:*}" || fail 'Release asset staging failed'
        [ "$(file_hash "$package_stage/${mapping%:*}")" = "$(meta "$2" "${mapping#*:}")" ] || fail 'Staged release asset checksum mismatch'
    done
    bounded 600 cp "$1/$(meta "$2" ARCHIVE)" "$package_stage/$(meta "$2" ARCHIVE)" || fail 'Release image archive staging failed'
    [ "$(file_hash "$package_stage/$(meta "$2" ARCHIVE)")" = "$(meta "$2" ARCHIVE_SHA256)" ] || fail 'Staged release image archive checksum mismatch'
    docker_cmd load --input "$package_stage/$(meta "$2" ARCHIVE)" >/dev/null 2>&1 || fail 'Image archive load failed before application/DB change'
    resolve_images "$2"
    pg_version=$(docker_cmd run --rm --pull never --platform "linux/$host_arch" --entrypoint postgres "$selected_postgres_image" --version 2>/dev/null)
    printf '%s' "$pg_version" | grep -Eq '^postgres \(PostgreSQL\) 16\.' || fail 'Verified PostgreSQL image must be major 16'
}
switch_package() {
    image_in_manifest "$2" BACKEND_IMAGE "$selected_backend_image" && image_in_manifest "$2" FRONTEND_IMAGE "$selected_frontend_image" && image_in_manifest "$2" POSTGRES_IMAGE "$selected_postgres_image" || fail 'Package switching requires a complete resolved identity from the declared digest pairs'
    atomic_copy "$1/runtime/compose.yml" "$install_root/runtime/compose.yml"
    atomic_copy "$1/runtime/runtime.yml" "$install_root/runtime/runtime.yml"
    atomic_copy "$1/installers/nexusquant.sh" "$install_root/runtime/nexusquant.sh"
    atomic_copy "$2" "$install_root/runtime/package.env"
    for mapping in 'compose.yml:COMPOSE_SHA256' 'runtime.yml:RUNTIME_SHA256' 'nexusquant.sh:SH_INSTALLER_SHA256'; do
        [ "$(file_hash "$install_root/runtime/${mapping%:*}")" = "$(meta "$2" "${mapping#*:}")" ] || fail 'Installed staged release asset checksum mismatch'
    done
    for field in VERSION SCHEMA_VERSION SOURCE_SHA RELEASE_SOURCE_HASH; do
        key=$field; [ "$field" != VERSION ] || key=NQ_VERSION
        set_env "$key" "$(meta "$2" "$field")"
    done
    set_selected_images
    set_env PACKAGE_MANIFEST_SHA256 "$(file_hash "$2")"
}
start_runtime() {
    command -v curl >/dev/null 2>&1 || fail 'Local health checks require the system curl installed by the installer'
    frontend_port=$(value FRONTEND_PORT); backend_port=$(value BACKEND_PORT)
    port_check
    if [ "${1:-false}" = true ]; then
        # 原子替换 runtime.yml 会更换 inode；即使 image ID 相同也必须重建挂载配置的容器。
        compose up -d --no-build --pull never --force-recreate --wait --wait-timeout 300 postgres backend >/dev/null 2>&1 || fail 'PostgreSQL/backend startup or health validation failed'
        compose up -d --no-build --pull never --force-recreate --wait --wait-timeout 120 frontend >/dev/null 2>&1 || fail 'Frontend startup or health validation failed'
    else
        compose up -d --no-build --pull never --wait --wait-timeout 300 postgres backend >/dev/null 2>&1 || fail 'PostgreSQL/backend startup or health validation failed'
        compose up -d --no-build --pull never --wait --wait-timeout 120 frontend >/dev/null 2>&1 || fail 'Frontend startup or health validation failed'
    fi
    curl -fsS --connect-timeout 3 --max-time 10 --noproxy '*' "http://127.0.0.1:$(value BACKEND_PORT)/actuator/health" 2>/dev/null | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || fail 'Backend HTTP health validation failed'
    curl -fsS --connect-timeout 3 --max-time 10 --noproxy '*' "http://127.0.0.1:$(value FRONTEND_PORT)/" >/dev/null 2>&1 || fail 'Frontend HTTP smoke failed'
    code=$(curl -sS --connect-timeout 3 --max-time 10 --noproxy '*' -o /dev/null -w '%{http_code}' "http://127.0.0.1:$(value BACKEND_PORT)/api/auth/me" 2>/dev/null) || fail 'Unauthenticated smoke unavailable'
    case "$code" in 401|403) ;; *) fail 'Unauthenticated business access must be rejected';; esac
    schema=$(db_query 'SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1' 2>/dev/null) || fail 'Installed schema query failed'
    [ "$schema" = "$(value SCHEMA_VERSION)" ] || fail 'Installed schema is not exact target schema'
}
snapshot_assets() {
    mkdir "$1/assets"
    for name in compose.yml runtime.yml nexusquant.sh package.env VERSION; do atomic_copy "$install_root/runtime/$name" "$1/assets/$name"; done
}
make_backup() {
    schema=$(db_query 'SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1' 2>/dev/null) || fail 'Backup schema query failed'
    [ "$schema" = "$(value SCHEMA_VERSION)" ] || fail 'Backup schema does not match installed identity'
    backup_id=$(date -u +%Y%m%dT%H%M%SZ)-$$
    dest=$install_root/backups/$backup_id; mkdir "$dest"
    compose exec -T postgres pg_dump -U nexusquant -d nexus_quant -Fc > "$dest/database.dump.tmp" 2>/dev/null || fail 'Database backup failed'
    mv "$dest/database.dump.tmp" "$dest/database.dump"
    atomic_copy "$install_root/config/runtime.env" "$dest/runtime.env"
    snapshot_assets "$dest"
    cat > "$dest/manifest.env.tmp" <<EOF
FORMAT=1
APP_VERSION=$(value NQ_VERSION)
SCHEMA_VERSION=$schema
POSTGRESQL_MAJOR=16
TIMESTAMP=$backup_id
DATABASE_SHA256=$(file_hash "$dest/database.dump")
CONFIG_SHA256=$(file_hash "$dest/runtime.env")
PACKAGE_MANIFEST_SHA256=$(value PACKAGE_MANIFEST_SHA256)
COMPOSE_SHA256=$(file_hash "$dest/assets/compose.yml")
RUNTIME_SHA256=$(file_hash "$dest/assets/runtime.yml")
SH_INSTALLER_SHA256=$(file_hash "$dest/assets/nexusquant.sh")
VERSION_SHA256=$(file_hash "$dest/assets/VERSION")
EOF
    mv "$dest/manifest.env.tmp" "$dest/manifest.env"
    sync
}
verify_backup() {
    safe_path "$1"
    [ -f "$1/manifest.env" ] && [ ! -L "$1/manifest.env" ] || fail 'Backup manifest missing or unsafe'
    [ "$(wc -c < "$1/manifest.env" | tr -d ' ')" -le 8192 ] || fail 'Backup manifest is too large'
    strict_lf "$1/manifest.env"
    awk -F= 'BEGIN{n=split("FORMAT APP_VERSION SCHEMA_VERSION POSTGRESQL_MAJOR TIMESTAMP DATABASE_SHA256 CONFIG_SHA256 PACKAGE_MANIFEST_SHA256 COMPOSE_SHA256 RUNTIME_SHA256 SH_INSTALLER_SHA256 VERSION_SHA256",k," ");for(i=1;i<=n;i++)a[k[i]]=1}NF!=2||!a[$1]||seen[$1]++||$2==""||$0~/\r/{exit 1}END{for(i=1;i<=n;i++)if(!seen[k[i]])exit 1}' "$1/manifest.env" || fail 'Invalid backup metadata'
    [ "$(meta "$1/manifest.env" FORMAT)" = 1 ] && [ "$(meta "$1/manifest.env" POSTGRESQL_MAJOR)" = 16 ] || fail 'Unsupported backup format'
    for mapping in 'database.dump:DATABASE_SHA256' 'runtime.env:CONFIG_SHA256' 'assets/package.env:PACKAGE_MANIFEST_SHA256' 'assets/compose.yml:COMPOSE_SHA256' 'assets/runtime.yml:RUNTIME_SHA256' 'assets/nexusquant.sh:SH_INSTALLER_SHA256' 'assets/VERSION:VERSION_SHA256'; do
        [ -f "$1/${mapping%:*}" ] && [ ! -L "$1/${mapping%:*}" ] && [ "$(file_hash "$1/${mapping%:*}")" = "$(meta "$1/manifest.env" "${mapping#*:}")" ] || fail 'Backup checksum mismatch'
    done
    verify_manifest "$1/assets/package.env"
    [ "$(meta "$1/assets/package.env" ARCH)" = "$host_arch" ] || fail 'Backup release architecture mismatch'
    verify_runtime_env "$1/runtime.env"
    [ "$(cat "$1/assets/VERSION")" = "$(meta "$1/manifest.env" APP_VERSION)" ] || fail 'Backup authoritative version mismatch'
    [ "$(meta "$1/assets/package.env" VERSION)" = "$(meta "$1/manifest.env" APP_VERSION)" ] && [ "$(meta "$1/assets/package.env" SCHEMA_VERSION)" = "$(meta "$1/manifest.env" SCHEMA_VERSION)" ] || fail 'Backup source identity mismatch'
    [ "$(meta "$1/runtime.env" NQ_VERSION)" = "$(meta "$1/manifest.env" APP_VERSION)" ] && [ "$(meta "$1/runtime.env" AUTO_UPDATE)" = OFF ] || fail 'Backup runtime version/update policy mismatch'
    [ "$(meta "$1/runtime.env" PACKAGE_MANIFEST_SHA256)" = "$(meta "$1/manifest.env" PACKAGE_MANIFEST_SHA256)" ] || fail 'Backup runtime manifest identity mismatch'
    for field in SCHEMA_VERSION SOURCE_SHA RELEASE_SOURCE_HASH; do [ "$(meta "$1/runtime.env" "$field")" = "$(meta "$1/assets/package.env" "$field")" ] || fail 'Backup runtime source/image identity mismatch'; done
    for field in BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE; do image_in_manifest "$1/assets/package.env" "$field" "$(meta "$1/runtime.env" "$field")" || fail 'Backup selected image is not a declared immutable identity'; done
    for field in COMPOSE_SHA256 RUNTIME_SHA256 SH_INSTALLER_SHA256; do [ "$(meta "$1/assets/package.env" "$field")" = "$(meta "$1/manifest.env" "$field")" ] || fail 'Backup release asset mismatch'; done
    for key in DB_PASSWORD JWT_SECRET CREDENTIALS_KEY; do hex_hash "$(meta "$1/runtime.env" "$key")" || fail 'Invalid backup key metadata'; done
}
restore_database() {
    compose up -d --no-build --pull never --wait --wait-timeout 120 postgres >/dev/null 2>&1 || fail 'Restore PostgreSQL startup failed'
    if [ "${2:-false}" = true ]; then
        # 新版迁移可能新增旧 dump 未知的对象；重建独立应用库才能完整去除这些对象。
        compose exec -T postgres psql -v ON_ERROR_STOP=1 -U nexusquant -d postgres -c 'DROP DATABASE IF EXISTS nexus_quant WITH (FORCE)' >/dev/null 2>&1 || fail 'Known-backup rollback database reset failed; application remains stopped'
        compose exec -T postgres psql -v ON_ERROR_STOP=1 -U nexusquant -d postgres -c 'CREATE DATABASE nexus_quant OWNER nexusquant' >/dev/null 2>&1 || fail 'Known-backup rollback database recreation failed; application remains stopped'
        compose exec -T postgres pg_restore -U nexusquant -d nexus_quant --no-owner --exit-on-error --single-transaction < "$1/database.dump" >/dev/null 2>&1 || fail 'Known-backup rollback restore failed; application remains stopped with retryable journal'
    else
        compose exec -T postgres pg_restore -U nexusquant -d nexus_quant --clean --if-exists --no-owner --exit-on-error --single-transaction < "$1/database.dump" >/dev/null 2>&1 || fail 'Transactional database restore failed; applications remain stopped'
    fi
}
confirm_action() {
    [ "$confirm" = false ] || return 0
    [ -t 0 ] || fail 'Explicit user confirmation required; rerun with --yes after reviewing the verified target'
    printf '%s Type YES to proceed: ' "$1"
    IFS= read -r answer; [ "$answer" = YES ] || fail 'Cancelled before mutation'
}
phase_write() { atomic_text "PHASE=$1" "$transaction_dir/phase.env"; sync; }
receipt_write() {
    receipt_result=$1; rollback_result=$2
    cat > "$transaction_dir/receipt.env.tmp" <<EOF
FORMAT=1
FROM_VERSION=$(meta "$transaction_dir/identity.env" FROM_VERSION)
TO_VERSION=$(meta "$transaction_dir/identity.env" TO_VERSION)
STARTED_AT=$(meta "$transaction_dir/identity.env" STARTED_AT)
COMPLETED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
SOURCE_MANIFEST_SHA256=$(meta "$transaction_dir/identity.env" SOURCE_MANIFEST_SHA256)
TARGET_MANIFEST_SHA256=$(meta "$transaction_dir/identity.env" TARGET_MANIFEST_SHA256)
SOURCE_BACKEND_IMAGE=$(meta "$transaction_dir/identity.env" SOURCE_BACKEND_IMAGE)
SOURCE_FRONTEND_IMAGE=$(meta "$transaction_dir/identity.env" SOURCE_FRONTEND_IMAGE)
SOURCE_POSTGRES_IMAGE=$(meta "$transaction_dir/identity.env" SOURCE_POSTGRES_IMAGE)
QUIESCED_AT=$(meta "$transaction_dir/identity.env" QUIESCED_AT)
BACKEND_IMAGE=$(meta "$transaction_dir/identity.env" BACKEND_IMAGE)
FRONTEND_IMAGE=$(meta "$transaction_dir/identity.env" FRONTEND_IMAGE)
POSTGRES_IMAGE=$(meta "$transaction_dir/identity.env" POSTGRES_IMAGE)
BACKUP_ID=$(meta "$transaction_dir/identity.env" BACKUP_ID)
SCHEMA_BEFORE=$(meta "$transaction_dir/identity.env" SCHEMA_BEFORE)
SCHEMA_AFTER=$(meta "$transaction_dir/identity.env" SCHEMA_AFTER)
RESULT=$receipt_result
ROLLBACK_RESULT=$rollback_result
EOF
    receipt_name=${3:-receipt.env}
    # 每次失败及重试都保留独立记录，手动 rollback 不覆盖原来的成功更新收据。
    cp "$transaction_dir/receipt.env.tmp" "$transaction_dir/receipt-$(date -u +%Y%m%dT%H%M%SZ)-$$-$receipt_result-$rollback_result.env"
    mv "$transaction_dir/receipt.env.tmp" "$transaction_dir/$receipt_name"
    sync
}
transaction_verify() {
    transaction_id=$1
    printf '%s' "$transaction_id" | grep -Eq '^[0-9]{8}T[0-9]{6}Z-[0-9]+$' || fail 'Invalid known transaction identity'
    transaction_dir=$install_root/runtime/transactions/$transaction_id
    safe_path "$transaction_dir"
    [ -f "$transaction_dir/identity.env" ] && [ ! -L "$transaction_dir/identity.env" ] || fail 'Known transaction metadata missing'
    [ "$(wc -c < "$transaction_dir/identity.env" | tr -d ' ')" -le 8192 ] || fail 'Transaction metadata is too large'
    strict_lf "$transaction_dir/identity.env"
    awk -F= 'BEGIN{n=split("FORMAT FROM_VERSION TO_VERSION STARTED_AT SOURCE_MANIFEST_SHA256 TARGET_MANIFEST_SHA256 SOURCE_BACKEND_IMAGE SOURCE_FRONTEND_IMAGE SOURCE_POSTGRES_IMAGE BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE QUIESCED_AT BACKUP_ID BACKUP_MANIFEST_SHA256 SCHEMA_BEFORE SCHEMA_AFTER",k," ");for(i=1;i<=n;i++)a[k[i]]=1}NF!=2||!a[$1]||seen[$1]++||$2==""||$0~/\r/{exit 1}END{for(i=1;i<=n;i++)if(!seen[k[i]])exit 1}' "$transaction_dir/identity.env" || fail 'Invalid transaction metadata'
    [ "$(meta "$transaction_dir/identity.env" FORMAT)" = 1 ] || fail 'Unsupported transaction format'
    previous_backup_id=$(meta "$transaction_dir/identity.env" BACKUP_ID)
    printf '%s' "$previous_backup_id" | grep -Eq '^[0-9]{8}T[0-9]{6}Z-[0-9]+$' || fail 'Invalid known backup identity'
    previous_backup=$install_root/backups/$previous_backup_id
    verify_backup "$previous_backup"
    [ "$(meta "$previous_backup/runtime.env" NQ_HOME)" = "$install_root" ] || fail 'Known transaction backup belongs to a different installation'
    [ "$(file_hash "$previous_backup/manifest.env")" = "$(meta "$transaction_dir/identity.env" BACKUP_MANIFEST_SHA256)" ] || fail 'Known backup trust anchor mismatch'
    [ "$(file_hash "$previous_backup/assets/package.env")" = "$(meta "$transaction_dir/identity.env" SOURCE_MANIFEST_SHA256)" ] || fail 'Known previous release mismatch'
    for field in BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE; do [ "$(meta "$previous_backup/runtime.env" "$field")" = "$(meta "$transaction_dir/identity.env" "SOURCE_$field")" ] || fail 'Known previous selected image identity mismatch'; done
    [ "$(meta "$previous_backup/manifest.env" APP_VERSION)" = "$(meta "$transaction_dir/identity.env" FROM_VERSION)" ] && [ "$(meta "$previous_backup/manifest.env" SCHEMA_VERSION)" = "$(meta "$transaction_dir/identity.env" SCHEMA_BEFORE)" ] || fail 'Known previous version/schema mismatch'
    verify_manifest "$transaction_dir/target.env"
    [ "$(file_hash "$transaction_dir/target.env")" = "$(meta "$transaction_dir/identity.env" TARGET_MANIFEST_SHA256)" ] || fail 'Known target release mismatch'
    for field in BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE; do image_in_manifest "$transaction_dir/target.env" "$field" "$(meta "$transaction_dir/identity.env" "$field")" || fail 'Known target selected image is not a declared immutable identity'; done
    [ "$(meta "$transaction_dir/target.env" VERSION)" = "$(meta "$transaction_dir/identity.env" TO_VERSION)" ] && [ "$(meta "$transaction_dir/target.env" SCHEMA_VERSION)" = "$(meta "$transaction_dir/identity.env" SCHEMA_AFTER)" ] || fail 'Known target version/schema mismatch'
}
verify_success_receipt() {
    receipt=$transaction_dir/receipt.env
    [ -f "$receipt" ] && [ ! -L "$receipt" ] && [ "$(wc -c < "$receipt" | tr -d ' ')" -le 8192 ] || fail 'Known successful receipt missing or unsafe'
    strict_lf "$receipt"
    awk -F= 'BEGIN{n=split("FORMAT FROM_VERSION TO_VERSION STARTED_AT COMPLETED_AT SOURCE_MANIFEST_SHA256 TARGET_MANIFEST_SHA256 SOURCE_BACKEND_IMAGE SOURCE_FRONTEND_IMAGE SOURCE_POSTGRES_IMAGE QUIESCED_AT BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE BACKUP_ID SCHEMA_BEFORE SCHEMA_AFTER RESULT ROLLBACK_RESULT",k," ");for(i=1;i<=n;i++)a[k[i]]=1}NF!=2||!a[$1]||seen[$1]++||$2==""{exit 1}END{for(i=1;i<=n;i++)if(!seen[k[i]])exit 1}' "$receipt" || fail 'Invalid known successful receipt fields'
    [ "$(meta "$receipt" FORMAT)" = 1 ] && [ "$(meta "$receipt" RESULT)" = UPDATE_SUCCESS ] && [ "$(meta "$receipt" ROLLBACK_RESULT)" = NOT_REQUIRED ] || fail 'Known successful receipt required'
    for field in FROM_VERSION TO_VERSION STARTED_AT SOURCE_MANIFEST_SHA256 TARGET_MANIFEST_SHA256 SOURCE_BACKEND_IMAGE SOURCE_FRONTEND_IMAGE SOURCE_POSTGRES_IMAGE QUIESCED_AT BACKUP_ID SCHEMA_BEFORE SCHEMA_AFTER; do
        [ "$(meta "$receipt" "$field")" = "$(meta "$transaction_dir/identity.env" "$field")" ] || fail 'Known receipt transaction identity mismatch'
    done
    for field in BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE; do [ "$(meta "$receipt" "$field")" = "$(meta "$transaction_dir/identity.env" "$field")" ] || fail 'Known receipt selected target digest mismatch'; done
    printf '%s' "$(meta "$receipt" COMPLETED_AT)" | grep -Eq '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$' || fail 'Invalid completion timestamp in known receipt'
}
failed_attempt_receipt() {
    safe_path "$install_root/runtime/attempts"; mkdir -p "$install_root/runtime/attempts"
    attempt_file=$install_root/runtime/attempts/$(date -u +%Y%m%dT%H%M%SZ)-$$.env
    attempt_result=UPDATE_FAILED_BEFORE_DB; attempt_backup=NONE; schema_after=$attempt_schema; attempt_rollback=NOT_REQUIRED
    if [ -n "$transaction_dir" ] && [ -f "$transaction_dir/identity.env" ]; then
        attempt_backup=$(meta "$transaction_dir/identity.env" BACKUP_ID)
        case "$(meta "$transaction_dir/phase.env" PHASE)" in DB_MAY_CHANGE|SUCCESS|FAILED_ROLLBACK) attempt_result=UPDATE_FAILED_AFTER_DB_MAY_CHANGE; schema_after=UNKNOWN; attempt_rollback=PENDING;; esac
    fi
    cat > "$attempt_file.tmp" <<EOF
FORMAT=1
FROM_VERSION=$attempt_from_version
TO_VERSION=$(meta "$target_manifest" VERSION)
STARTED_AT=$update_started_at
COMPLETED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
SOURCE_MANIFEST_SHA256=$attempt_source_manifest
TARGET_MANIFEST_SHA256=$trusted_hash
SOURCE_BACKEND_IMAGE=$attempt_source_backend
SOURCE_FRONTEND_IMAGE=$attempt_source_frontend
SOURCE_POSTGRES_IMAGE=$attempt_source_postgres
BACKEND_IMAGE=$(meta "$target_manifest" BACKEND_IMAGE)
FRONTEND_IMAGE=$(meta "$target_manifest" FRONTEND_IMAGE)
POSTGRES_IMAGE=$(meta "$target_manifest" POSTGRES_IMAGE)
BACKUP_ID=$attempt_backup
SCHEMA_BEFORE=$attempt_schema
SCHEMA_AFTER=$schema_after
RESULT=$attempt_result
ROLLBACK_RESULT=$attempt_rollback
EOF
    mv "$attempt_file.tmp" "$attempt_file"; sync
}
recover_transaction() {
    transaction_verify "$transaction_id"
    recovery_phase=$(meta "$transaction_dir/phase.env" PHASE)
    case "$recovery_phase" in PREPARED|DB_MAY_CHANGE|SUCCESS|FAILED_ROLLBACK) ;; *) fail 'Transaction is not eligible for recovery';; esac
    # 使用当前完整配置停应用，再切回已验证的旧快照；数据库风险相位始终恢复备份。
    previous_project=$(meta "$previous_backup/runtime.env" PROJECT_NAME)
    case "$previous_project" in ''|*[!a-z0-9-]*) fail 'Invalid known previous project identity';; esac
    compose_with_env "$previous_backup/runtime.env" "$previous_project" "$previous_backup/assets/compose.yml" stop --timeout 30 frontend backend postgres >/dev/null 2>&1 || fail 'Cannot stop known application services for rollback'
    for name in compose.yml runtime.yml nexusquant.sh package.env VERSION; do atomic_copy "$previous_backup/assets/$name" "$install_root/runtime/$name"; done
    atomic_copy "$previous_backup/runtime.env" "$install_root/config/runtime.env"
    project=$(value PROJECT_NAME)
    resolve_images "$previous_backup/assets/package.env"
    set_selected_images
    if [ "$recovery_phase" != PREPARED ]; then restore_database "$previous_backup" true; fi
    verify_installed
    start_runtime true
    phase_write ROLLED_BACK
    if [ "$recovery_phase" = SUCCESS ]; then receipt_write ROLLBACK_SUCCESS ROLLBACK_SUCCESS rollback-receipt.env
    else receipt_write UPDATE_FAILED ROLLBACK_SUCCESS; fi
    rm -f "$install_root/runtime/pending-update"
    transaction_active=false
    update_app_stopped=false
    printf '%s\n' 'Previous application, configuration and database restored; receipt retained.'
}
cleanup() {
    result=$?
    trap - EXIT HUP INT TERM
    if [ "$update_attempt_active" = true ] && [ "$result" -ne 0 ]; then
        (trap - EXIT HUP INT TERM; set -e; failed_attempt_receipt) & writer_pid=$!
        wait "$writer_pid" || printf '%s\n' 'Failure attempt receipt could not be persisted; required recovery will still run.' >&2
    fi
    if [ "$transaction_active" = true ] && [ "$result" -ne 0 ]; then
        # 恢复在新 shell 子环境中执行，set -e 不受条件调用屏蔽；失败保留 pending 并停机。
        transaction_active=false
        (trap - EXIT HUP INT TERM; set -e; recover_transaction) & recovery_pid=$!
        recovery_result=0; wait "$recovery_pid" || recovery_result=$?
        if [ "$recovery_result" -ne 0 ]; then
            compose stop --timeout 30 frontend backend >/dev/null 2>&1 || :
            (trap - EXIT HUP INT TERM; set -e; phase_write FAILED_ROLLBACK; receipt_write UPDATE_FAILED ROLLBACK_FAILED) & writer_pid=$!
            wait "$writer_pid" || printf '%s\n' 'Rollback failure receipt unavailable; original pending journal remains authoritative.' >&2
            printf '%s\n' 'Rollback failed; application stopped and pending journal retained. Use rollback --yes after resolving the reported stage.' >&2
        fi
    elif [ "$update_app_stopped" = true ] && [ "$result" -ne 0 ]; then
        (trap - EXIT HUP INT TERM; set -e; start_runtime) & recovery_pid=$!
        recovery_result=0; wait "$recovery_pid" || recovery_result=$?
        [ "$recovery_result" -eq 0 ] || printf '%s\n' 'Pre-migration update failed; previous DB/config unchanged, but previous application restart requires attention.' >&2
    fi
    if [ "$lock_owned" = true ]; then rm -f "$install_root/runtime/.operation-lock/pid"; rmdir "$install_root/runtime/.operation-lock" 2>/dev/null || :; fi
    if [ "$recovery_lock_owned" = true ]; then rmdir "$install_root/runtime/.lock-recovery" 2>/dev/null || :; fi
    if [ "$bootstrap_mounted" = true ]; then
        if bounded 60 hdiutil detach "$bootstrap_dir/mount" >/dev/null 2>&1; then bootstrap_mounted=false; fi
    fi
    if [ -n "$bootstrap_dir" ] && [ "$bootstrap_mounted" = false ]; then
        case "$bootstrap_dir" in "${TMPDIR:-/tmp}"/nq-docker.*) rm -rf -- "$bootstrap_dir";; esac
    fi
    if [ -n "$manifest_workdir" ]; then case "$manifest_workdir" in "${TMPDIR:-/tmp}"/nq-release.*) rm -rf -- "$manifest_workdir";; esac; fi
    if [ -n "$package_stage" ]; then case "$package_stage" in "$install_root/runtime"/.package-stage.*) rm -rf -- "$package_stage";; esac; fi
    exit "$result"
}
lock_acquire() {
    trap cleanup EXIT
    trap 'exit 130' INT
    trap 'exit 143' HUP TERM
    if ! mkdir "$install_root/runtime/.operation-lock" 2>/dev/null; then
        # 独立 claim 防止两个调用者同时回收死进程的 lock，误删新进程的身份。
        mkdir "$install_root/runtime/.lock-recovery" 2>/dev/null || fail 'Operation lock recovery in progress; inspect interrupted process if this persists'
        recovery_lock_owned=true
        [ ! -L "$install_root/runtime/.operation-lock" ] || fail 'Unsafe operation lock'
        lock_pid=$(cat "$install_root/runtime/.operation-lock/pid" 2>/dev/null || printf 0)
        printf '%s' "$lock_pid" | grep -Eq '^[1-9][0-9]*$' || fail 'Unresolved operation lock; inspect interrupted installer before retrying'
        kill -0 "$lock_pid" 2>/dev/null && fail 'Another operation is in progress'
        rm -f "$install_root/runtime/.operation-lock/pid"
        rmdir "$install_root/runtime/.operation-lock" || fail 'Unresolved operation lock'
        mkdir "$install_root/runtime/.operation-lock" || fail 'Another operation is in progress'
    fi
    lock_owned=true; printf '%s\n' "$$" > "$install_root/runtime/.operation-lock/pid"
    if [ "$recovery_lock_owned" = true ]; then rmdir "$install_root/runtime/.lock-recovery"; recovery_lock_owned=false; fi
}

host_identity
if [ "$action" = install ]; then
    safe_path "$package_root"
    for directory in config data data/postgres logs backups runtime runtime/transactions; do
        safe_path "$install_root/$directory"; mkdir -p "$install_root/$directory"
    done
    chmod 700 "$install_root" "$install_root/config" "$install_root/backups" "$install_root/runtime" "$install_root/runtime/transactions"
    lock_acquire
    [ ! -f "$install_root/runtime/pending-update" ] || fail 'Interrupted update pending; run rollback --yes using installed lifecycle script'
    [ ! -f "$install_root/runtime/pending-restore.env" ] || fail 'Interrupted restore pending; retry the original verified restore backup before installing'
    verify_package "$package_root" ''
    bootstrap_docker
    preflight
    manifest=$verified_manifest
    fresh=false; resuming_fresh=false
    if [ -f "$install_root/config/runtime.env" ]; then
        verify_installed
        [ "$(value PACKAGE_MANIFEST_SHA256)" = "$(file_hash "$manifest")" ] || fail 'Rerun requires identical release identity; use explicit verified update'
        if [ -f "$install_root/runtime/first-install-pending" ]; then fresh=true; resuming_fresh=true; fi
        start_runtime
        printf '%s\n' 'Existing password, roles, data and runtime profile preserved.'
    else
        existing_entries=$(ls -A "$install_root/data/postgres" 2>/dev/null) || fail 'Existing PostgreSQL directory is inaccessible; known configuration must be restored before installing'
        [ -z "$existing_entries" ] || fail 'Existing PostgreSQL data without runtime identity; restore known configuration before installing'
        port_check
        load_images "$package_root" "$manifest"
        fresh=true
        atomic_text FRESH "$install_root/runtime/first-install-pending"
        random_secret() { dd if=/dev/urandom bs=32 count=1 2>/dev/null | od -An -tx1 | tr -d ' \n'; }
        cat > "$install_root/config/runtime.env.tmp" <<EOF
NQ_HOME=$install_root
DB_PASSWORD=$(random_secret)
JWT_SECRET=$(random_secret)
CREDENTIALS_KEY=$(random_secret)
FRONTEND_PORT=$frontend_port
BACKEND_PORT=$backend_port
PROJECT_NAME=$project
AUTO_UPDATE=OFF
RUNTIME_PROFILE=$runtime_profile
BACKEND_MEMORY=$backend_memory
BACKEND_CPUS=$backend_cpus
POSTGRES_MEMORY=$postgres_memory
POSTGRES_CPUS=$postgres_cpus
FRONTEND_MEMORY=$frontend_memory
FRONTEND_CPUS=$frontend_cpus
EOF
        mv "$install_root/config/runtime.env.tmp" "$install_root/config/runtime.env"
        switch_package "$package_stage" "$manifest"
        atomic_text "$(meta "$manifest" VERSION)" "$install_root/runtime/VERSION"
        start_runtime
    fi
    admin=$(db_query "SELECT count(*) FROM users u WHERE username='admin' AND enabled AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code='ADMIN')" 2>/dev/null) || fail 'Bootstrap admin query failed'
    [ "$admin" = 1 ] || fail 'Bootstrap admin unavailable; existing users preserved'
    if [ "$fresh" = true ]; then
        already_changed=false
        if [ "$resuming_fresh" = true ]; then
            changed_state=$(db_query "SELECT must_change_password FROM users WHERE username='admin' AND enabled" 2>/dev/null) || fail 'Interrupted bootstrap authentication state unavailable'
            case "$changed_state" in t) ;; f) already_changed=true;; *) fail 'Interrupted bootstrap authentication state is ambiguous';; esac
        fi
        if [ "$already_changed" = false ]; then
            curl -fsS --connect-timeout 3 --max-time 10 --noproxy '*' -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' "http://127.0.0.1:$(value BACKEND_PORT)/api/auth/login" 2>/dev/null | grep -Eq '"mustChangePassword"[[:space:]]*:[[:space:]]*true' || fail 'Fresh bootstrap must require first password change'
            printf '%s\n' 'Initial username: admin' 'Initial password: 123456' 'Password change required on first login.'
        else printf '%s\n' 'Interrupted initial install resumed; the already changed administrator password and roles were preserved.'; fi
        rm -f "$install_root/runtime/first-install-pending"
    fi
    printf 'NexusQuant %s: http://127.0.0.1:%s\n' "$(value NQ_VERSION)" "$(value FRONTEND_PORT)"
    exit 0
fi
[ -f "$install_root/config/runtime.env" ] || fail 'Installation not found; run the installer first'
safe_path "$install_root/config"; safe_path "$install_root/runtime"; safe_path "$install_root/backups"
lock_acquire
if [ -f "$install_root/runtime/pending-restore.env" ]; then
    [ "$action" = restore ] || fail 'Interrupted restore pending; retry restore with its original verified backup. Other lifecycle actions are blocked.'
    restore_journal=$install_root/runtime/pending-restore.env
    [ ! -L "$restore_journal" ] && [ "$(wc -c < "$restore_journal" | tr -d ' ')" -le 8192 ] || fail 'Unsafe restore journal'
    strict_lf "$restore_journal"
    awk -F= 'BEGIN{a["FORMAT"]=1;a["BACKUP_PATH"]=1;a["BACKUP_MANIFEST_SHA256"]=1}NF!=2||!a[$1]||seen[$1]++||$2==""{exit 1}END{if(!seen["FORMAT"]||!seen["BACKUP_PATH"]||!seen["BACKUP_MANIFEST_SHA256"])exit 1}' "$restore_journal" || fail 'Invalid restore journal'
    [ "$(meta "$restore_journal" FORMAT)" = 1 ] && [ "$backup_path" = "$(meta "$restore_journal" BACKUP_PATH)" ] || fail 'Retry requires the original pending restore backup'
    [ -f "$backup_path/manifest.env" ] && [ "$(file_hash "$backup_path/manifest.env")" = "$(meta "$restore_journal" BACKUP_MANIFEST_SHA256)" ] || fail 'Pending restore backup trust anchor mismatch'
fi
if [ -f "$install_root/runtime/pending-update" ]; then
    [ ! -L "$install_root/runtime/pending-update" ] || fail 'Unsafe interrupted update marker'
    [ "$action" = rollback ] || fail 'Interrupted update pending; ordinary lifecycle operations are blocked. Run rollback --yes to restore the known snapshot.'
    transaction_id=$(cat "$install_root/runtime/pending-update")
    transaction_verify "$transaction_id"
else verify_installed; fi
if [ "$action" = check-update ]; then
    [ -n "$target_package" ] && hex_hash "$trusted_hash" || fail 'Supply --package and independently trusted --manifest-sha256'
    verify_package "$target_package" "$trusted_hash"
    available=$(meta "$verified_manifest" VERSION)
    printf 'Current: %s\nAvailable: %s\nAUTO_UPDATE=OFF\n' "$(cat "$install_root/runtime/VERSION")" "$available"
    if version_greater "$available" "$(cat "$install_root/runtime/VERSION")"; then printf '%s\n' 'Update available; explicit update and user confirmation required.'; else printf '%s\n' 'No newer version available.'; fi
    exit 0
fi
bootstrap_docker
case "$action" in
    start) start_runtime;;
    stop) compose stop --timeout 30 >/dev/null 2>&1 || fail 'Stop failed';;
    restart) compose stop --timeout 30 >/dev/null 2>&1 || fail 'Stop failed'; start_runtime;;
    status|doctor)
        printf 'NQ_VERSION=%s SCHEMA_EXPECTED=%s AUTO_UPDATE=OFF MANIFEST=%s\n' "$(cat "$install_root/runtime/VERSION")" "$(value SCHEMA_VERSION)" "$(value PACKAGE_MANIFEST_SHA256)"
        for service in postgres backend frontend; do
            service_id=$(compose ps -a -q "$service" 2>/dev/null) || service_id=
            if [ -n "$service_id" ]; then
                service_state=$(docker_cmd inspect --format '{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}UNKNOWN{{end}}' "$service_id" 2>/dev/null) || service_state=UNKNOWN
                printf '%s=%s ID=%s\n' "$service" "$service_state" "$service_id"
            else printf '%s=NOT_RUNNING\n' "$service"; fi
        done
        if [ "$action" = doctor ]; then
            preflight
            printf 'INSTALLED_PROFILE=%s FILESYSTEM=AVAILABLE\n' "$(value RUNTIME_PROFILE)"
            schema=$(db_query 'SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1' 2>/dev/null) || schema=UNKNOWN
            postgres=$(db_query 'SELECT version()' 2>/dev/null) || postgres=UNKNOWN
            kill_state=$(db_query "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'" 2>/dev/null) || kill_state=UNKNOWN
            case "$kill_state" in ENGAGED|DISENGAGED) ;; *) kill_state=UNKNOWN;; esac
            printf 'SCHEMA_ACTUAL=%s POSTGRESQL=%s KILL_STATE=%s\n' "$schema" "$postgres" "$kill_state"
            backend_id=$(compose ps -q backend 2>/dev/null) || backend_id=
            safety=
            if [ -n "$backend_id" ]; then safety=$(docker_cmd inspect --format '{{range .Config.Env}}{{if eq . "NQ_CONTINUOUS_SIM_ENABLED=true"}}SIM=ENABLED {{end}}{{if eq . "NQ_LIVE_ENABLED=false"}}LIVE=DISABLED {{end}}{{if eq . "NQ_REAL_EXCHANGE_ENABLED=false"}}REAL_EXCHANGE=DISABLED {{end}}{{if eq . "NQ_REAL_PROVIDER_ENABLED=false"}}REAL_PROVIDER=DISABLED {{end}}{{if eq . "NQ_REAL_CLIENT_ENABLED=false"}}REAL_CLIENT=DISABLED {{end}}{{end}}' "$backend_id" 2>/dev/null) || safety=; fi
            printf 'RUNTIME_SAFETY=%s\n' "${safety:-UNKNOWN}"
            if [ -n "$target_package" ]; then
                hex_hash "$trusted_hash" || fail 'Independent trusted update manifest SHA256 required'
                verify_package "$target_package" "$trusted_hash"
                if version_greater "$(meta "$verified_manifest" VERSION)" "$(cat "$install_root/runtime/VERSION")"; then printf '%s\n' 'UPDATE=AVAILABLE'; else printf '%s\n' 'UPDATE=NONE'; fi
            else printf '%s\n' 'UPDATE=UNKNOWN (supply verified package metadata; automatic update OFF)'; fi
        fi;;
    backup) make_backup; printf 'Backup: %s\n' "$dest";;
    restore)
        [ -n "$backup_path" ] || fail 'Absolute backup path required'
        verify_backup "$backup_path"
        [ "$(meta "$backup_path/manifest.env" APP_VERSION)" = "$(value NQ_VERSION)" ] && [ "$(meta "$backup_path/manifest.env" SCHEMA_VERSION)" = "$(value SCHEMA_VERSION)" ] || fail 'Normal restore requires identical application version and schema'
        compose stop --timeout 30 frontend backend >/dev/null 2>&1 || fail 'Cannot stop application for restore'
        cat > "$install_root/runtime/pending-restore.env.tmp" <<EOF
FORMAT=1
BACKUP_PATH=$backup_path
BACKUP_MANIFEST_SHA256=$(file_hash "$backup_path/manifest.env")
EOF
        mv "$install_root/runtime/pending-restore.env.tmp" "$install_root/runtime/pending-restore.env"; sync
        restore_database "$backup_path" true
        # 普通恢复保留当前目录、数据库密码、端口及资源配置，只恢复认证加密代际。
        for key in JWT_SECRET CREDENTIALS_KEY; do set_env "$key" "$(meta "$backup_path/runtime.env" "$key")"; done
        start_runtime
        rm -f "$install_root/runtime/pending-restore.env"; sync;;
    uninstall)
        if [ "$purge" = true ] && [ "$confirm_purge" = false ]; then
            [ -t 0 ] || fail 'Purge requires --confirm-purge or interactive exact DELETE confirmation'
            printf 'Delete all NQ data and backups at %s? Type DELETE: ' "$install_root"
            IFS= read -r answer; [ "$answer" = DELETE ] || fail 'Purge cancelled'
        fi
        compose down --timeout 30 >/dev/null 2>&1 || fail 'Uninstall container removal failed'
        if [ "$purge" = true ]; then
            for name in data backups; do
                safe_path "$install_root/$name"
                if ! rm -rf -- "$install_root/$name" 2>/dev/null; then
                    # PostgreSQL bind 数据归容器 UID 所有；仅显式确认的 purge 可临时提权删除这两个固定子树。
                    printf '%s\n' 'Confirmed purge requires temporary administrator approval for container-owned files.'
                    bounded 300 sudo rm -rf -- "$install_root/$name" >/dev/null 2>&1 || fail 'Confirmed purge failed on container-owned files; data removal remains incomplete'
                fi
            done
            atomic_text FRESH "$install_root/runtime/first-install-pending"
            # purge 后配置仍保留；下次安装重新bootstrap用户，但不覆盖现存 secrets/profile。
            printf '%s\n' 'Explicitly confirmed data/backups purge complete.'
        else printf '%s\n' 'Containers removed; data, backups, configuration and receipts preserved.'; fi;;
    update)
        [ -n "$target_package" ] && hex_hash "$trusted_hash" || fail 'Supply --package and independently trusted --manifest-sha256'
        verify_package "$target_package" "$trusted_hash"
        target_manifest=$verified_manifest
        version_greater "$(meta "$target_manifest" VERSION)" "$(cat "$install_root/runtime/VERSION")" || fail 'Update target must have a newer version'
        [ "$(meta "$target_manifest" SCHEMA_VERSION)" -ge "$(value SCHEMA_VERSION)" ] || fail 'Schema downgrade is not an update'
        [ "$(meta "$target_manifest" POSTGRES_CONFIG_DIGEST)" = "$(meta "$install_root/runtime/package.env" POSTGRES_CONFIG_DIGEST)" ] || fail 'PostgreSQL image upgrades require a separate qualified database lifecycle'
        preflight
        printf 'Verified update: %s -> %s (manifest %s).\n' "$(value NQ_VERSION)" "$(meta "$target_manifest" VERSION)" "$trusted_hash"
        confirm_action 'Update changes installed application and may migrate its isolated database.'
        update_started_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
        attempt_from_version=$(value NQ_VERSION); attempt_source_manifest=$(value PACKAGE_MANIFEST_SHA256)
        attempt_source_backend=$(value BACKEND_IMAGE); attempt_source_frontend=$(value FRONTEND_IMAGE); attempt_source_postgres=$(value POSTGRES_IMAGE)
        update_attempt_active=true
        attempt_schema=$(db_query 'SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1' 2>/dev/null) || { attempt_schema=UNKNOWN; fail 'Source schema query failed before update'; }
        [ "$attempt_schema" = "$(value SCHEMA_VERSION)" ] || fail 'Source schema mismatch before update'
        load_images "$target_package" "$target_manifest"
        # 先加载并验证完整镜像，再停写入应用；备份与切换之间不允许业务事实继续写入。
        compose stop --timeout 30 frontend backend >/dev/null 2>&1 || fail 'Cannot stop application before update backup'
        quiesced_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
        update_app_stopped=true
        make_backup
        transaction_id=$(date -u +%Y%m%dT%H%M%SZ)-$$
        transaction_dir=$install_root/runtime/transactions/$transaction_id; mkdir "$transaction_dir"
        atomic_copy "$target_manifest" "$transaction_dir/target.env"
        cat > "$transaction_dir/identity.env.tmp" <<EOF
FORMAT=1
FROM_VERSION=$(value NQ_VERSION)
TO_VERSION=$(meta "$target_manifest" VERSION)
STARTED_AT=$update_started_at
SOURCE_MANIFEST_SHA256=$(value PACKAGE_MANIFEST_SHA256)
TARGET_MANIFEST_SHA256=$trusted_hash
SOURCE_BACKEND_IMAGE=$(value BACKEND_IMAGE)
SOURCE_FRONTEND_IMAGE=$(value FRONTEND_IMAGE)
SOURCE_POSTGRES_IMAGE=$(value POSTGRES_IMAGE)
BACKEND_IMAGE=$selected_backend_image
FRONTEND_IMAGE=$selected_frontend_image
POSTGRES_IMAGE=$selected_postgres_image
QUIESCED_AT=$quiesced_at
BACKUP_ID=$backup_id
BACKUP_MANIFEST_SHA256=$(file_hash "$dest/manifest.env")
SCHEMA_BEFORE=$(value SCHEMA_VERSION)
SCHEMA_AFTER=$(meta "$target_manifest" SCHEMA_VERSION)
EOF
        mv "$transaction_dir/identity.env.tmp" "$transaction_dir/identity.env"
        phase_write PREPARED
        transaction_active=true
        atomic_text "$transaction_id" "$install_root/runtime/pending-update"; sync
        compose stop --timeout 30 postgres >/dev/null 2>&1 || fail 'Cannot stop PostgreSQL before verified update switch'
        switch_package "$package_stage" "$target_manifest"
        # Flyway 在 backend 启动时迁移；持久化风险相位必须先于任何目标数据库启动。
        phase_write DB_MAY_CHANGE
        start_runtime true
        receipt_write UPDATE_SUCCESS NOT_REQUIRED
        atomic_text "$(value NQ_VERSION)" "$install_root/runtime/VERSION"
        phase_write SUCCESS
        atomic_text "$transaction_id" "$install_root/runtime/last-update"
        rm -f "$install_root/runtime/pending-update"; sync
        transaction_active=false
        update_app_stopped=false
        update_attempt_active=false
        printf '%s\n' 'UPDATE_SUCCESS; known pre-update backup and receipt retained.';;
    rollback)
        if [ ! -f "$install_root/runtime/pending-update" ]; then
            [ -f "$install_root/runtime/last-update" ] && [ ! -L "$install_root/runtime/last-update" ] || fail 'Known successful update receipt required'
            transaction_id=$(cat "$install_root/runtime/last-update")
            transaction_verify "$transaction_id"
            [ "$(meta "$transaction_dir/phase.env" PHASE)" = SUCCESS ] || fail 'Known successful update phase required'
            verify_success_receipt
            [ "$(value PACKAGE_MANIFEST_SHA256)" = "$(meta "$transaction_dir/identity.env" TARGET_MANIFEST_SHA256)" ] || fail 'Current version is not the known update target'
        fi
        confirm_action 'Rollback restores the known pre-update database; subsequent changes will be discarded.'
        atomic_text "$transaction_id" "$install_root/runtime/pending-update"; sync
        transaction_active=true
        recover_transaction
        printf '%s\n' 'ROLLBACK_SUCCESS';;
esac
