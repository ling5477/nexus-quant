# NexusQuant 安装与运维

版本以包内 VERSION 为准。**V1.0.0 requires a fresh database. Pre-release development databases are not upgrade targets.** 不使用 Flyway repair，不将开发数据库迁移为正式 V1。

## 系统要求与发行包

Windows 10/11、macOS Intel / Apple Silicon、Linux x86_64 / arm64；建议至少 4 CPU、8 GB RAM、10 GB 可用空间（备份另计）。需要运行中的 Docker daemon 与 Compose v2（支持 `up --wait`）。Windows/macOS 使用 Docker Desktop 的 Linux containers；Linux 使用 Docker Engine + Compose plugin。macOS/Linux 使用系统 shell、curl 和标准文件工具。

用户无需 Java、Maven、Node、PostgreSQL 或 Python。安装器不编译源码，不自动安装 Docker。

官方预构建包包含 VERSION、runtime、installers、产品文档，以及 `images-amd64.tar` / `images-arm64.tar` 和对应 `.sha256`。从可信发行渠道取得整个包；摘要用于损坏检测，不能代替发行渠道真实性。安装器按 Docker daemon 架构选择镜像，Compose 不强制 amd64。维护者通过 `release/build-package.ps1 -OutputDirectory <新目录>` 构建两种 Linux 架构并打包；只构建当前架构可传 `-Architectures amd64`。源码导出不是可安装镜像包。

当前 distribution contract 为 **release-package local image load**，尚未发布 registry 镜像。许可证选择与跨平台验收完成前，构建包只用于预发布验证。

## Windows

解压发行包，在 PowerShell 运行：

```powershell
.\installers\install.ps1
```

数据目录 `%USERPROFILE%\.nexusquant`。运行后打开 <http://127.0.0.1:18080>。

## macOS

```sh
sh installers/install-macos.sh
```

## Linux

```sh
sh installers/install-linux.sh
```

macOS/Linux 数据目录 `~/.nexusquant`。安装会检查 Docker/daemon、加载镜像、生成随机内部密码与密钥、启动 PG16 和 backend、等待健康、启动 frontend、验证 bootstrap 并显示地址。重跑同一版本保留配置、角色和密码。

## URLs / ports

UI: <http://127.0.0.1:18080>；backend: <http://127.0.0.1:18888>；健康检查 `/actuator/health`。PostgreSQL 只在 Compose 内网，未发布主机端口。服务默认仅本机可访问，不应直接暴露默认登录到公网。首次安装 Windows 可传 `-FrontendPort` / `-BackendPort`；macOS/Linux 可设置 `NQ_FRONTEND_PORT` / `NQ_BACKEND_PORT`。已有安装以持久化配置为准。

## 首次登录

**Initial username: admin / Initial password: 123456 / Password change required on first login.**

必须修改初始密码。当前密码需正确，新密码至少 8 个字符、最多 72 个 UTF-8 字节，且不同于当前密码和 `123456`。成功后客户端清除旧令牌并返回登录页；请使用新密码重新登录。刷新或直接访问业务 URL 仍进入改密页面，后端同时拒绝业务 API。重启或重跑安装不会恢复默认密码。

## Start / stop / restart

```powershell
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action start
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action stop
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action restart
```

```sh
sh ~/.nexusquant/runtime/nexusquant.sh start
sh ~/.nexusquant/runtime/nexusquant.sh stop
sh ~/.nexusquant/runtime/nexusquant.sh restart
```

## Backup

```powershell
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action backup
```

```sh
sh ~/.nexusquant/runtime/nexusquant.sh backup
```

备份写入 `backups/<UTC timestamp>/`，包含 PostgreSQL custom dump、恢复所需 runtime.env 及 manifest（格式、时间、应用/Schema/PG 版本、摘要）。此目录含内部密钥，权限限制为当前用户；不要提交到 Git 或发送给他人。终端只显示路径。dump 是数据库一致性快照。

## Restore

```powershell
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action restore -BackupPath '<备份目录>'
```

```sh
sh ~/.nexusquant/runtime/nexusquant.sh restore '<备份目录>'
```

仅支持相同 VERSION、V1 schema、PG16。先验证 manifest 与文件摘要，再停止 frontend/backend，单事务恢复 PostgreSQL，恢复 JWT/凭据加密密钥，保持当前安装路径、DB 密码与端口，重启并检查健康。失败时业务容器保持停止，先处理错误再重试；不要启动半恢复的数据。恢复会回到备份时点的用户/密码状态，必要时重新改密。跨版本或历史开发库恢复会拒绝。

## Upgrade policy

本次 V1 必须 fresh DB。正式发布后 V1 baseline 不再修改，后续 schema 只采用 forward-only V2+。没有受支持的跨版本升级包时，请备份并等待明确的升级说明；安装器拒绝版本不一致的重跑与恢复。

## Uninstall

```powershell
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action uninstall
# 明确删除数据及备份
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action uninstall -PurgeData
```

```sh
sh ~/.nexusquant/runtime/nexusquant.sh uninstall
# 明确删除数据及备份
sh ~/.nexusquant/runtime/nexusquant.sh uninstall --purge-data
```

默认删除容器并保留 data / backups / config。purge 仅删除当前安装目录下 data、backups，不删除其他项目数据。删除后再次安装会创建新的默认管理员。

## Data directory

```text
.nexusquant/
  config/runtime.env
  data/postgres/
  logs/
  backups/
  runtime/compose.yml, runtime.yml, VERSION, 运维脚本
```

随机 DB_PASSWORD / JWT_SECRET / CREDENTIALS_KEY 仅保存在本地配置中；不要打印该文件或执行会完整展示 secrets 的 `docker compose config`。Docker 日志按大小轮转。

## 安全默认值

PG16（`postgres:16.15`）、backend、frontend 三个服务。允许受现有 policy 约束的 OKX public marketdata；CI/test 继续 no-outbound。SIM available，LIVE / REAL_EXCHANGE / REAL_PROVIDER / REAL_CLIENT / AI / DH 关闭；private diagnostics、真实 order submission、transfer/withdraw 关闭；baseline 初始化 kill switch=ENGAGED。安装器不打开 LIVE，也不解除 kill switch。

## Troubleshooting

- Docker 不可用：启动 Docker daemon，确认 Linux containers 和 Compose v2 可用。
- 镜像摘要不一致：重新取得完整可信发行包，不跳过校验。
- health 超时：查看 `docker compose` 对应项目的服务状态及脱敏日志，确认端口/内存；保留数据，不用 repair。
- 行情无法连接：检查网络对 `https://www.okx.com` 的可达性，private API 仍保持关闭；网络失败不能当作零行情。
- 启动报告 checksum 不一致：确认是新的 V1 数据目录。旧开发库不是升级目标。
- 忘记修改后的管理员密码：重跑安装不会重置；使用已有备份恢复或等待正式账户恢复能力。
- LICENSE 为 PENDING_USER_DECISION；最终 v1.0.0 发布受阻，跨平台 clean-machine qualification 由后续验收完成。
