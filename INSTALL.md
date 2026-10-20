# NexusQuant 安装与运维

版本以包内 VERSION 为准。**V1.0.0 requires a fresh database. Pre-release development databases are not upgrade targets.** 不使用 Flyway repair，不将开发数据库迁移为正式 V1。

## 系统要求与发行包

V1.0.0 唯一 REQUIRED / CLAIMED host platform 为 **Windows 11 amd64，Docker Desktop 已安装且 daemon 正在运行（Linux containers）**。支持范围现已冻结；预发布资格结论仍须由同一 exact candidate 的真实安装、完整 Continuous 经济链、重启续跑和备份恢复证据验收，不能由本矩阵或镜像构建结果推导 PASS。

| 平台 / 环境 | V1.0.0 合同 |
| --- | --- |
| Windows 11 amd64，Docker Desktop 已安装且运行 | 唯一 REQUIRED / CLAIMED；真实候选资格验收仍须完成 |
| Windows Docker missing | IMPLEMENTED / NOT_QUALIFIED / NOT_CLAIMED |
| Windows daemon stopped | IMPLEMENTED / NOT_QUALIFIED / NOT_CLAIMED |
| Ubuntu amd64 | NOT_QUALIFIED / NOT_CLAIMED |
| Ubuntu arm64 | NOT_QUALIFIED / NOT_CLAIMED |
| macOS arm64 | NOT_QUALIFIED / NOT_CLAIMED |
| macOS amd64 | NOT_CLAIMED |

Docker bootstrap、daemon recovery 和 Linux/macOS 安装器保留为 `AVAILABLE_BUT_UNQUALIFIED`。其他平台 **not officially qualified for v1.0.0**；这仅表示本版本未完成正式资格验证。模拟环境、POSIX fixture 和 multiarch image build 不构成真实宿主资格；arm64 镜像即使可构建也仅为 `BUILD_AVAILABLE`。发布源码中的 `release/support-matrix.json` 绑定相同机器可读合同，发行包中的本表提供完整合同。

最低 2 logical CPU、4 GiB RAM、10 GiB 可用空间（镜像及备份另计）；建议至少 4 CPU、8 GiB RAM。Docker Engine 最低 24.0.0、Compose 最低 2.20.0。Windows/macOS 使用 Docker Desktop Linux containers；Ubuntu 使用 Docker Engine + Compose plugin。macOS/Linux 需要系统 shell、curl 和标准文件工具。

用户无需 Java、Maven、Node、PostgreSQL 或 Python。安装器不编译源码。正式支持范围要求 Docker 已安装并运行，安装器直接复用。已安装但停止时的启动恢复、缺失时的官方渠道安装及 UAC / sudo 提权能力尚未取得本版本正式资格。Docker 版本过低时报告显式升级路径，不静默替换现有版本。安装失败按阶段报告，保留数据，不改变用户 Docker 代理或全局资源配置。

Ubuntu 安装使用 Docker 官方签名 apt 仓库。普通用户加入 docker 组后可运行容器，无需持续 sudo；docker 组具有主机管理权限。macOS/Windows 首次启动 Docker Desktop 可能需要完成操作系统或 Docker Desktop 自身的交互提示。

预构建包包含 VERSION、runtime、installers、产品文档，以及相应架构的 `images-<arch>.tar`、`package-<arch>.env` 和 `.sha256`。manifest 的 18 个字段绑定 exact Git SHA、release-source hash、schema、镜像 archive、每个组件的 OCI native/index ID 与 config digest，以及安装器/runtime 文件摘要。Docker containerd store 与 classic store 使用不同的镜像寻址身份：安装器只尝试这两个已声明的摘要，核对实际 ID、OS 与架构，然后持久化本机可运行的 immutable ID。Compose 直接使用该 `sha256:<ID>`，不会退回浮动 tag。两种摘要与 RepoDigests 分列记录于 `package-<arch>.images.json`，镜像身份不代表平台运行资格。

首次安装必须从可信发行渠道取得整个包；同包 `.sha256` 只检测损坏，不能证明发行者真实性。更新必须额外提供从可信独立渠道取得的 manifest SHA256，不能把目标包自行计算的摘要当作信任授权。维护者从验证过且未生成构建产物的 source export 执行 `release/build-package.ps1 -OutputDirectory <新目录>`；需要 PowerShell 7.4+ 和使用 containerd image store 的本机 Docker。打包器验证 OCI index → platform manifest → config/layers 与 Docker 兼容 manifest 的摘要绑定，只生成可由两种 store 加载的发行归档；此构建要求不要求用户改变安装主机的 image store。只构建某架构可传 `-Architectures amd64`。源码导出不是可安装镜像包；未正式发布的资格 fixture 不作为公开 release。

当前 distribution contract 为 **release-package local image load**，尚未发布 registry 镜像。公开发行状态以最终 Release qualification / current release authority 为准；本版本未声明的平台不阻断技术验收。

## Windows

解压发行包，在 PowerShell 运行：

```powershell
.\installers\install.ps1
```

数据目录 `%USERPROFILE%\.nexusquant`。运行后打开 <http://127.0.0.1:18080>。

## macOS

`AVAILABLE_BUT_UNQUALIFIED`：not officially qualified for v1.0.0。

```sh
sh installers/install-macos.sh
```

## Linux

`AVAILABLE_BUT_UNQUALIFIED`：not officially qualified for v1.0.0。

```sh
sh installers/install-linux.sh
```

macOS/Linux 数据目录 `~/.nexusquant`。安装会检查 Docker/daemon、加载镜像、生成随机内部密码与密钥、启动 PG16 和 backend、等待健康、启动 frontend、验证 bootstrap 并显示地址。重跑同一版本保留配置、角色和密码。

## Host preflight / Runtime Profile

检查 OS/version、架构、CPU、RAM、可用空间、virtualization 可观察状态、Docker/Compose、代理是否配置、目录及端口。代理只报告存在状态，不输出 URL、账号或凭据，不覆盖系统设置。首次安装端口冲突时给出明确错误与端口参数；不会终止占用端口的其他进程。

按主机与 Docker 可用资源上限的较小值确定 profile，同一资源输入产生相同结果。已有安装重跑不自动改变其持久化 profile。

| Profile | 资源条件 | Backend / PostgreSQL / Frontend memory | CPU limits |
| --- | --- | --- | --- |
| LIGHT | 低于 4 CPU 或 8 GiB，且满足最低要求 | 768m / 512m / 128m | 1 / 0.5 / 0.25 |
| STANDARD | 满足 LIGHT 上界，但低于 8 CPU 或 16 GiB | 2048m / 1024m / 256m | 2 / 1 / 0.5 |
| PERFORMANCE | 至少 8 CPU 与 16 GiB | 4096m / 2048m / 256m | 4 / 2 / 0.5 |

JVM MaxRAMPercentage=65、InitialRAMPercentage=20。Profile 仅设置容器资源与 JVM 内存，不修改 Strategy、Risk、Order 或交易行为。实际选择写入本地受限配置。

## URLs / ports

UI: <http://127.0.0.1:18080>；backend: <http://127.0.0.1:18888>；健康检查 `/actuator/health`。PostgreSQL 只在 Compose 内网，未发布主机端口。服务默认仅本机可访问，不应直接暴露默认登录到公网。首次安装 Windows 可传 `-FrontendPort` / `-BackendPort`；macOS/Linux 可设置 `NQ_FRONTEND_PORT` / `NQ_BACKEND_PORT`。已有安装以持久化配置为准。

## 首次登录

**Initial username: admin / Initial password: 123456 / Password change required on first login.**

必须修改初始密码。当前密码需正确，新密码至少 8 个字符、最多 72 个 UTF-8 字节，且不同于当前密码和 `123456`。成功后客户端清除旧令牌并返回登录页；请使用新密码重新登录。刷新或直接访问业务 URL 仍进入改密页面，后端同时拒绝业务 API。重启或重跑安装不会恢复默认密码。

## Start / stop / restart / status / doctor

```powershell
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action start
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action stop
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action restart
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action status
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action doctor
```

```sh
sh ~/.nexusquant/runtime/nexusquant.sh start
sh ~/.nexusquant/runtime/nexusquant.sh stop
sh ~/.nexusquant/runtime/nexusquant.sh restart
sh ~/.nexusquant/runtime/nexusquant.sh status
sh ~/.nexusquant/runtime/nexusquant.sh doctor
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

普通 restore 只接受相同 VERSION/schema、PG16。先验证 manifest 与文件摘要，再停止 frontend/backend，重建当前安装的应用数据库并从完整 dump 恢复，恢复 JWT/凭据加密密钥，保持当前安装路径、DB 密码与端口，重启并检查健康。完整数据库恢复确保备份之后新增的对象与事实不会残留。失败时业务容器保持停止，先处理错误再重试；不要启动半恢复的数据。恢复会回到备份时点的用户/密码状态，必要时重新改密。跨版本或历史开发库的普通 restore 会拒绝。

## Check update / update / rollback

`AUTO_UPDATE=OFF`。没有后台、启动时或静默更新。`check-update` 只验证可信目标 metadata 并提示版本，不下载/加载镜像、不修改数据库或安装状态。不存在可信目标 metadata 时，doctor 的更新可用性为 UNKNOWN，不当作“没有更新”。

```powershell
$runtime = "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1"
& $runtime -Action check-update -TargetPackageRoot '<目标包目录>' -TrustedManifestSha256 '<可信渠道提供的64位摘要>'
& $runtime -Action update -TargetPackageRoot '<目标包目录>' -TrustedManifestSha256 '<可信摘要>'
# 无交互脚本的明确选择
& $runtime -Action update -TargetPackageRoot '<目标包目录>' -TrustedManifestSha256 '<可信摘要>' -ConfirmUpdate
& $runtime -Action rollback -ConfirmRollback
```

```sh
sh ~/.nexusquant/runtime/nexusquant.sh check-update --package '<目标包绝对目录>' --manifest-sha256 '<可信摘要>'
sh ~/.nexusquant/runtime/nexusquant.sh update --package '<目标包绝对目录>' --manifest-sha256 '<可信摘要>'
# --yes 表示用户显式确认
sh ~/.nexusquant/runtime/nexusquant.sh update --package '<目标包绝对目录>' --manifest-sha256 '<可信摘要>' --yes
sh ~/.nexusquant/runtime/nexusquant.sh rollback --yes
```

确认后验证并加载目标 assets，停止应用写入，创建更新前完整备份，再切换目标应用、检查 health/schema/smoke。只有成功后才提交本地 VERSION 和 SUCCESS receipt。停止应用后备份消除了备份到停写之间产生事实、回滚时丢失的窗口。开始目标 backend 前先持久化数据库可能变更的 journal，migration 仍由既有 Flyway 路径执行。

DB 变更前的 image/验证失败保持旧数据库；目标启动可能触发 schema 变更后的失败恢复更新前完整数据库与旧应用。不会执行 Flyway repair。进程中断且 journal 未收敛时阻止普通启动，保留恢复信息。rollback 仅接受已知上次成功更新的 manifest、immutable image IDs、备份和 receipt，不接收任意镜像。

receipt 记录 from/to version、时间、manifest、image digest、backup identity、schema before/after、结果与 rollback 结果，不含 secrets。恢复后回到备份时点；不要将恢复过程手动中断或启动未验证的数据。

V1 初次安装必须 fresh DB。正式发布后 V1 baseline 不再修改，后续 schema 只采用 forward-only V2+；开发数据库不是更新目标。

## Uninstall

```powershell
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action uninstall
# 明确删除数据及备份
& "$env:USERPROFILE\.nexusquant\runtime\nexusquant.ps1" -Action uninstall -PurgeData -ConfirmPurge
```

```sh
sh ~/.nexusquant/runtime/nexusquant.sh uninstall
# 明确删除数据及备份
sh ~/.nexusquant/runtime/nexusquant.sh uninstall --purge-data --confirm-purge
```

默认删除容器并保留 data / backups / config。purge 需要显式参数和第二次确认；没有确认参数时要求输入 DELETE。只删除经过路径验证的本安装目录下 data、backups；不可恢复，先保留必要备份。删除后再次安装会创建新的默认管理员。

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

## License and financial risk

License: `Apache-2.0`.
NexusQuant is licensed under the Apache License 2.0.
See [LICENSE](LICENSE) for software licensing terms.
Third-party attribution: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

NexusQuant does not provide investment advice.
See [DISCLAIMER.md](DISCLAIMER.md) for financial and trading risk information before enabling any real-money functionality.
