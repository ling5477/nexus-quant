# NexusQuant 安装与运维

版本以包内VERSION为准。[v1.0.3](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.3)已于2026-10-31T06:00:01Z正式发布，Release ID429746750，Schema V52；v1.0.0～v1.0.2历史发行保持原字节。本页为发行后开发文档同步，不是重新构建或替换正式包。

**固定发行Source Commit为5f770f60e2b6f11b376535b865a60ef5976db3c1；本次或未来文档提交不替代它。** 正式安装使用该Release的已验证ZIP，不从当前dev重新构包冒充1.0.3。完整来源、Tag/Snapshot及六资产摘要见[STATUS](docs/current/STATUS.md)与Release Notes。开发数据库不是正式升级目标，不使用Flyway repair。

## 取得正式安装资产

Self-Hosted下载NexusQuant-1.0.3-windows-amd64.zip（320966803 bytes），SHA256为fa743eaeb202abfbfef820ff17900a42692318242b6309b4b712a5b493679e61；完整解压后根目录仍为NexusQuant-1.0.3-rc2-windows-amd64，不能只重命名内部文件或使用GitHub自动源码归档代替安装包。在该解压根目录执行installers/install.ps1。

Companion另行下载NexusQuant-Python-Research-Companion-1.0.3-windows-amd64-py311.zip（13107099 bytes），SHA256为bfd099adbe85c82781e8f00fd07ec51feb9b8b71cc8dbcf495b2386952edcb0b。用户自行准备CPython3.11 x64，在独立解压目录按该包根INSTALL创建venv：

~~~powershell
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip --isolated install --no-index --no-cache-dir --only-binary=:all: --find-links .\wheelhouse --require-hashes -r .\requirements.lock
.\.venv\Scripts\python.exe -m pip check
~~~

两份ZIP和sidecar从可信发行渠道取得；同包摘要只检测损坏，不独立证明发行者身份。最终provenance为UNSIGNED_RELEASE_SOURCE_MAPPING，不是数字签名。Companion不需要checkout research/py或editable安装，不进入Java、Compose或安装器生命周期，Python不拥有交易权限。

## v1.0.3正式范围与普通升级

正式已验证Self-Hosted为Windows11 amd64 / 已安装且运行的Docker Desktop Linux Containers / PostgreSQL16.15 / Flyway V52；Companion为Windows amd64 / CPython3.11 x64（实际3.11.9）/ NumPy2.3.5独立离线安装。Linux/macOS/ARM64、其他Python及Public Demo未取得本版正式支持资格，历史support-matrix保持原版本合同。

已验证普通升级仅为 **v1.0.2/V49 → v1.0.3/V52**，由原正式安装器check-update/update执行V50、V51、V52。要求可信独立渠道取得目标Manifest SHA256、版本递增、Schema单调及相同POSTGRES_CONFIG_DIGEST。已有受限nq_runtime / NOLOGIN nq_schema_owner及RLS合同保持；不引入新的桥接。v1.0.0、v1.0.1或其他路径未经本版资格不声称直接支持。

目标1.0.3 Package Manifest可信SHA256为d985d38099f11beca3f3ef98bf4f320e93f4c54d54b5b400473d7e89e8f47734，普通更新必须经原正式check-update/update并保持原PG config digest。新安装、正常升级、备份恢复、主动回滚均已有固定包证据；原故障更新仍exit1，原整轮FAILED和两表raw DIFFERENT保留，自动恢复只按历史数据与稳定控制QUALIFIED，不要求改写原失败或全部raw相同。

### 维护者构包历史参考（不重建已发布1.0.3）

以下只保留构包前提和命令参考，不是本轮操作或用户安装步骤，不授权从新的文档提交重建/覆盖正式1.0.3。

维护者必须从v1.0.2实际官方ZIP、Manifest与镜像TAR核验原PG身份；不能由浮动postgres:16.15或更早版本记录推定兼容。可信v1.0.2 Package Manifest SHA256为 `74406593acccdc452004067a5877a080d7dff596d9417f0af60e54aceb200d5f`，TAR SHA256为 `7177b7019c6c87409b58729f19a3b748303db57794adc8dcbcd95515f524806e`。核验原制品后从新准确提交的已验证不可变导出构包：

```powershell
.\release\build-package.ps1 -OutputDirectory <new-directory> -Architectures amd64 `
  -PostgresImage sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97 `
  -ExpectedPostgresConfigDigest sha256:066167ff39c2a612402ce99b734ea1cff92a9d1f2ac52f697a5f0384f0d88d6b
```

成对参数、单架构和OCI/config摘要必须吻合，错误即拒绝，不能放宽普通更新门禁。正式安装器验证可信资产，停写、生成并验证完整备份、持久journal，在DB_MAY_CHANGE前后区分失败；V52迁移后失败应恢复原V49数据库和绑定资产。主动回滚与真实失败自动恢复分别取得证据；恢复成功不把原更新失败改判成功。不能用旧应用直接连接V52，不手工删pending掩盖未收敛事务。备份只保留停写时点。

## Python Research Companion

独立资产 `NexusQuant-Python-Research-Companion-1.0.3-windows-amd64-py311.zip` 与Self-Hosted分开取得和核验。它提供Wheel、NumPy2.3.5依赖wheelhouse、hash锁、来源/依赖manifest、INSTALL和许可证；Wheel实际版本为nexus-quant-research1.0.3。用户自行准备CPython3.11 x64，按Companion内INSTALL创建干净venv并用 `--no-index --no-cache-dir --require-hashes` 从附带文件安装。其他Python/平台只有对应依赖与真实安装运行证据后才能声明支持。

Java安装和运维无需Python。Companion只读本地V2导出并显式生成离线Artifact，用户带回原research页只读预览；不接DB、不自动认证、不启动服务。三个因子与时序统计仅为研究诊断，回放时间是假设，Java摘要校验不证明统计结论。LIVE=DISABLED、Kill Switch=ENGAGED。

## 历史平台入口与通用运维合同

下列V1.0.0平台矩阵保留历史归属；通用安全、备份、身份、资源和运维步骤继续适用。当前1.0.3资格由上述固定发行与准确证据表达；下文历史候选、V1平台矩阵及Bridge文字保留生成时点，不将旧NOT_RUN或SCOPED_PASS追认为本版PASS。

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

当前候选安装策略：最低 2 logical CPU、10 GiB 可用空间（镜像及备份另计）；建议至少 4 CPU。内存建议如下，按主机内存与 Docker 可用内存上限的较小值判断：

| 内存建议 | 配置与行为 |
| --- | --- |
| Recommended Memory | 4 GiB |
| Preferred Memory | 8 GiB |
| Below Recommended | Installation Allowed / Runtime Stability Not Guaranteed；警告并为全新安装选择 LIGHT |

低于 4 GiB 属于资源受限运行场景，可能发生 OOM、启动失败或性能下降；安装器不设置新的最低内存门槛，也不自动启用 Swap 或修改系统内存配置。主机与 Docker 内存分别实际检测，无法取得有效内存信息时明确拒绝，不将未知值当作低内存继续。历史约 1.58 GiB Ubuntu仅有安装/重启/600秒SCOPED_PASS，正式资格与长期稳定性未验证，不能据此声明正式最低配置或 Linux 完整发行资格。以上调整不追溯改变已发布版本的资格记录。

Docker Engine 最低 24.0.0、Compose 最低 2.20.0。Windows/macOS 使用 Docker Desktop Linux containers；Ubuntu 使用 Docker Engine + Compose plugin。macOS/Linux 需要系统 shell、curl 和标准文件工具。

全新安装与常规运维无需宿主 Java、Maven、Node、PostgreSQL 或 Python；v1.0.0 一次性升级桥接另需 Python 3.12 或更新版本，见下文。安装器不编译源码。正式支持范围要求 Docker 已安装并运行，安装器直接复用。已安装但停止时的启动恢复、缺失时的官方渠道安装及 UAC / sudo 提权能力尚未取得本版本正式资格。Docker 版本过低时报告显式升级路径，不静默替换现有版本。安装失败按阶段报告，保留数据，不改变用户 Docker 代理或全局资源配置。

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
| LIGHT | 低于 4 CPU 或 8 GiB，且满足 CPU / 磁盘要求；低于 4 GiB 警告后允许安装 | 768m / 512m / 128m | 1 / 0.5 / 0.25 |
| STANDARD | 满足 LIGHT 上界，但低于 8 CPU 或 16 GiB | 2048m / 1024m / 256m | 2 / 1 / 0.5 |
| PERFORMANCE | 至少 8 CPU 与 16 GiB | 4096m / 2048m / 256m | 4 / 2 / 0.5 |

JVM MaxRAMPercentage=65、InitialRAMPercentage=20。Profile 仅设置容器资源与 JVM 内存，不修改 Strategy、Risk、Order 或交易行为。实际选择写入本地受限配置。

## URLs / ports

UI: <http://127.0.0.1:18080>；backend: <http://127.0.0.1:18888>；健康检查 `/actuator/health`。PostgreSQL 只在 Compose 内网，未发布主机端口。服务默认仅本机可访问，不应直接暴露默认登录到公网。首次安装 Windows 可传 `-FrontendPort` / `-BackendPort`；macOS/Linux 可设置 `NQ_FRONTEND_PORT` / `NQ_BACKEND_PORT`。已有安装以持久化配置为准。

## 候选 Schema 35+ 的数据库身份

v1.0.1 候选将 `nexusquant` 保留为 PostgreSQL 初始化、迁移和备份恢复身份，原 `DB_PASSWORD` 不变。业务容器改用独立 `nq_runtime` 和随机 `RUNTIME_DB_PASSWORD`；配置以 `DB_ROLE_FORMAT=2` 区分。`nq_schema_owner` 不可登录、不具备 SUPERUSER/BYPASSRLS；运行角色无 Schema CREATE 或 Owner 成员关系。

安装、启动和重启先停止业务写入，启动 PostgreSQL，再由同一已验证后端镜像的一次性维护入口准备角色、执行并校验原 Flyway、初始化空库管理员、重建 Owner/ACL，通过完整 Tenant 运行检查后才启动业务。业务容器不接收维护密码，也不执行 Flyway 或管理员初始化。任何角色、迁移或权限失败均不启动业务；普通启动丢失运行凭据时拒绝，不重新生成密码。已有用户的密码、角色和 Session 不因安装重试而覆盖。

备份包含受限配置中的运行凭据。恢复使用 `--no-owner --no-privileges`，随后维护入口重建对象归属、RLS 所需权限和受限函数 EXECUTE；数据库 dump 不被当作集群角色备份。`doctor` 另行验证真实运行身份。配置、备份和维护容器环境含秘密，不要打印、分享或提交。

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

### 从 v1.0.0 进入角色分离候选

已发布 v1.0.0 的安装器进程不包含新维护步骤。本次 v1.0.1 目标还改变了 PostgreSQL 镜像身份；旧版和目标包内新版安装器的普通 `update` 均要求 PostgreSQL 配置摘要不变，因此不能直接完成此次转换。`UPDATE_INELIGIBLE` 是应保留的安全拒绝，不得修改 Manifest、配置或历史安装器以绕过。

此路径必须使用下文的一次性 Legacy Upgrade Bridge，并先取得绑定**该安装实例**的可信过渡授权。只有 Bridge 持久提交并完成交接后，才使用新版安装器进行常规启动、重启及备份恢复。失败恢复由 Bridge 的已绑定事务处理，不把旧镜像连接到已升级数据库。

### 普通安装器更新事务（不适用于上述首次桥接）

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

随机 DB_PASSWORD / RUNTIME_DB_PASSWORD（Schema 35+）/ JWT_SECRET / CREDENTIALS_KEY 仅保存在本地配置中；不要打印该文件或执行会完整展示 secrets 的 `docker compose config`。Docker 日志按大小轮转。

## 安全默认值

PG16（`postgres:16.15`）、backend、frontend 三个服务。允许受现有 policy 约束的 OKX public marketdata；CI/test 继续 no-outbound。SIM available，LIVE / REAL_EXCHANGE / REAL_PROVIDER / REAL_CLIENT / AI / DH 关闭；private diagnostics、真实 order submission、transfer/withdraw 关闭；baseline 初始化 kill switch=ENGAGED。安装器不打开 LIVE，也不解除 kill switch。

## v1.0.0 一次性升级桥接

原 v1.0.0 安装器对数据库镜像摘要变化仍应报告 `UPDATE_INELIGIBLE`。跨数据库镜像及运行身份协议的转换使用独立 `installers/legacy_upgrade_bridge.py`，普通更新继续使用正式安装器。不修改历史包或其严格 Manifest 合同。需要 Python 3.12 或更新版本、Docker Linux containers 和 Compose v2。

桥接器与32表比较工具的摘要、桥接源码 Commit、原安装配置摘要、项目身份、原/目标包 Manifest 摘要、目标镜像归档 `images-<arch>.tar` 摘要、架构及目标源码 Commit 必须由独立可信渠道提供的 `NQ_LEGACY_BRIDGE_V1` 过渡清单绑定。`targetArchiveSha256` 对应目标 Manifest 的 `ARCHIVE_SHA256`，不是外层发行 ZIP 的摘要。该清单与桥接脚本在执行前须使用操作系统的 SHA-256 工具核验，不能从待验证目录的自带摘要建立信任。清单固定字段为 `format`、`bridgeSourceCommit`、`bridgeSha256`、`baselineToolSha256`、`sourceManifestSha256`、`targetManifestSha256`、`targetArchiveSha256`、`targetSourceCommit`、`architecture`、`installationConfigSha256`、`projectName`；所有摘要为64位小写十六进制，Commit 为完整40位小写十六进制。

已有隔离实例的签名授权不自动适用于其他安装。其他用户须向维护者申请绑定自身原配置摘要、项目、架构及准确原/目标发行身份的完整过渡清单，并独立验证签名身份、签名主体、完整授权字段及本地配置摘要后再执行。不要上传原始 `runtime.env`、口令或密钥；若尚未取得这种实例授权，应停止升级。通用发行物签名不能替代实例级升级授权，也不授予 LIVE 或产品发布权限。

例如 Windows 使用 `Get-FileHash -Algorithm SHA256`，POSIX 使用 `sha256sum` 或 `shasum -a 256`，与独立取得的摘要逐一比较后才运行 Python。`--trusted-transition-sha256` 也必须使用该独立来源给出的值，不能现场对未知清单计算后直接当作可信值。

```text
python installers/legacy_upgrade_bridge.py preflight --install-root <绝对安装路径> --transition <可信过渡清单> --trusted-transition-sha256 <独立清单摘要> --baseline-tool installers/upgrade_business_baseline.py --source-package <原正式包> --target-package <目标正式包> --target-archive <目标正式包内images-amd64.tar>
```

`preflight` 只读取身份及本地 Docker 状态，不下载、加载镜像、创建目录或改库；两包声明的镜像须已通过正式包校验并加载。`execute` 使用相同参数，另需 `--confirm "BRIDGE 1.0.0 TO 1.0.1"`。桥接器先停写，生成原合同逻辑备份和停库完整集群副本，在隔离恢复副本中验证原/新 PG16 镜像、locale/collation、扩展、索引、32表及认证事实，随后才切换镜像并调用既有 maintenance。原维护口令不变，独立运行口令仅生成一次并持久保存；应用只获得受限凭据。

事务保存在安装目录 `runtime/bridge-transactions/<id>`，与原操作锁互斥。数据库变化前建立原安装器的两个待恢复标记，阻止旧/新安装器接管；不要手工删除标记。`status --transaction <id>` 使用同一可信清单及工具，只读输出事务阶段。失败保持应用停止，`recover --transaction <id> --confirm "RECOVER 1.0.0"` 从已绑定事务恢复原完整集群、角色、配置和应用；未知身份、备份漂移或恢复失败继续停机。已经持久提交的事务只允许完成回执交接，不允许将旧镜像连接到已升级数据库。

尚未提交的桥接代码只能在任务隔离验证中使用 `bridgeSourceCommit=UNCOMMITTED` 和显式 `--local-candidate`，结果不构成正式发行来源或正式升级资格。桥接、打包工具及清单修改须先完成 Git 交付，再从同一新 Commit 导出、校验、扫描和构建正式候选；历史包不能冒充修复后的正式包。

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
