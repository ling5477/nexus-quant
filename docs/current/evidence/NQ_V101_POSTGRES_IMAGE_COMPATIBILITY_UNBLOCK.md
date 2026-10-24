# NQ-V101-POSTGRES-IMAGE-COMPATIBILITY-UNBLOCK

日期：2026-10-24（Asia/Shanghai）。结论：**BLOCKED / ORIGINAL_INSTALLER_RESTRICTED_RUNTIME_TRANSITION_UNSUPPORTED**。

本轮完成两套正式 PostgreSQL 资产的来源/层/二进制/系统包差异检查、原安装器行为检查和定向回归。发现用户指定的“必须由 v1.0.0 原入口执行升级”还有独立的角色过渡阻断，无法仅改 builder 恢复正常 V34→V47 升级。因此未实现或启用会被误认为完整解锁的旧镜像复用路径；builder、当前/历史安装器、manifest、Compose、业务代码及 migration 均未修改。没有新正式构建或升级。

## 基线与保护

| 身份 | 实际值 |
| --- | --- |
| Branch | `codex/nq-v101-release-manifest-delivery` |
| HEAD | `885b76ae92f0d3f26cb3d31a15bd2d607d799faf` |
| 已合并 dev | `15ffbc58e15adcb11d97ba296dc4ce4b882b0beb`（GitHub API 本轮复核） |
| Starting Tree | `2145e55070afad4fcdfe0227c11e893a81fba19e`；HEAD/dev tree 相同 |
| 初始暂存/已跟踪修改 | 0 |
| v1.0.0 tag peeled commit | `be816342581c8ab09a2c9b70dd8e9d1c88dcec8c` |

仅新增本报告作为本轮仓库证据并精确暂存。最终 staged tree 由外部 `final-candidate.json` 记录，避免报告自引用。原两份未跟踪证据保留原字节：

- `NQ_V101_FULL_STACK_SECURITY_PATCH_CLOSEOUT.md`：`9cd418d744ce0c90257b6bf086c657cd34c282a56be3b98c696f1fcf62fa61e6`。
- `NQ_V101_RELEASE_SOURCE_AND_FORMAL_UPGRADE_QUALIFICATION_RESUMED.md`：`912d48c9c1cacee7cd04e642f3b0b2fec6982c2bb05c2b54cb70c95ca92fcbaf`。

首次 HTTPS git fetch 连接超时；GitHub API 仍取得完整 dev SHA，与本地 origin/dev 对象及 tree 一致。未将 fetch 失败记为成功，也未更改期待身份。

## 根因及正式包来源

原 `release/build-package.ps1` 对 PostgreSQL 执行 `docker pull postgres:16.15`，随后按同一可变标签 inspect/save。标签漂移导致实际归档 PG config 改变，原安装器对 config digest 的严格比较正确拒绝。正式 Compose 使用 manifest 给定的不可变镜像 ID，未自行追踪标签。

从已有资格报告及正式包读取完整身份，而非根据前缀构造：

| 身份 | 官方 v1.0.0 | 既有 v1.0.1 正式来源候选 |
| --- | --- | --- |
| Source Commit | `5231266d366680ed5c6c1e38ab84adb33ffcd644` | `15ffbc58e15adcb11d97ba296dc4ce4b882b0beb` |
| Package Manifest SHA-256 | `c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1` | `7d2e42e45f4a18f7e0b7e16253ca1faee078bb96e92a4bfdbe5df0955a1315b3` |
| Images archive SHA-256 | `11ba0c8c0d6a782dacaccd48a6ec3e4de6dd346435d1aa223db3c2bfe05f1e1d` | `4eadf505823423dda89ac107faf69237b560e8c70db62a54ff7ad1a1355092cc` |
| PostgreSQL native OCI Image | `sha256:ca0bd484cb98bf4b24eb1010e73fb3fcbd6714d240fbc1a10eea5b7dbecb641d` | `sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97` |
| PostgreSQL Config Digest | `sha256:275447c94b11b151decd1f29877965301d5a77032037c46de93d990f739f00a9` | `sha256:066167ff39c2a612402ce99b734ea1cff92a9d1f2ac52f697a5f0384f0d88d6b` |
| 架构 | linux/amd64 | linux/amd64 |
| 实际 PostgreSQL version | 16.15 / Debian 16.15-1.pgdg13+2 | 同左 |
| 镜像创建时间（UTC） | `2026-10-06T01:33:18.890712895Z` | `2026-10-21T00:38:09.198430998Z` |

旧包 provenance SHA-256 `48eb8cf2df69a983f32dfc04dca7453250c5e89872f91bad474cb45b264d34b9` 与官方发布资产一致；旧包19文件逐项匹配该 provenance。本轮重新校验两套 manifest SHA、完整 archive SHA、native index SHA、amd64 platform manifest SHA、config SHA、Docker/OCI layer顺序及每个 PG layer 的内容 SHA，均通过。没有依赖可变本地标签或手工更改包 manifest。

既有 v1.0.1 候选继续作为历史失败候选保留：Release Source Hash `4965fe5fa6f8573d27391dd4216fb203c6917ede7160515664e3b05e137c6668`，外层 ZIP SHA `bada1788e4c813cff9a9898950028e567342bb3adcb34f99f007e250f9ca2701`。本轮没有把这些摘要用于冒充新候选资格。

## 镜像内容与安全适用性

两份镜像内 `/usr/lib/postgresql/16` 的 **1,087 个文件逐一摘要相同**；postgres 程序 SHA-256 均为 `ea528a9f858bce5eef557522820c945a3d3b9fd260a15d6d0313d6350574229d`。这比相同 version 字符串更强，但不意味着整镜像等价。

dpkg 实际版本差异仅两包：

| Package | 旧 | 新 |
| --- | --- | --- |
| liblzma5:amd64 | 5.8.1-1+deb13u1 | 5.8.1-1+deb13u2 |
| xz-utils | 5.8.1-1+deb13u1 | 5.8.1-1+deb13u2 |

差异**确实包含 OS 库安全补丁**，不能描述为纯 metadata 重建。[Debian DSA-6549-1](https://security-tracker.debian.org/tracker/DSA-6549-1) 标明旧版本受影响、新版本修复；[XZ 上游 GHSA-5qpq-xqfv-j9pg](https://github.com/tukaani-project/xz/security/advisories/GHSA-5qpq-xqfv-j9pg) 将缺陷列为 High，要求特定 decoder 重复初始化、分配失败后再次复用并匹配 dictionary；`.xz` 和 raw decoder 不受影响。

实际 `ldd postgres` 包含 liblzma（经 libxml2 等依赖），不能声称库完全未使用。NQ Java/SQL/发行脚本没有发现 SQL XML/LZMA 消费入口。独立检查进一步对照 [libxml2 v2.9.14 xzlib.c](https://github.com/GNOME/libxml2/blob/v2.9.14/xzlib.c) 的新建stream/失败返回/关闭释放序列，以及 [PostgreSQL REL_16_15 xml.c](https://github.com/postgres/postgres/blob/REL_16_15/src/backend/utils/adt/xml.c) 的内存解析与外部实体限制。

有界结论：**未证明该差异在当前 NQ 产品路径构成必须修复的可达 High 风险**，不能据 High 标签自动判安全 P1；也不能据该结论给旧图无漏洞、任意 SQL/原生消费者或跨路径的安全背书。旧扫描的1 Critical / 61 High包匹配原样保留，没有关闭或隐藏。两图相同的 libxml2 `[CVE-2026-6653](https://security-tracker.debian.org/tracker/CVE-2026-6653)` 被 Debian 标为 minor/no-dsa，不能把它当成两镜像的差异修复。

本轮未启动全技术栈扫描。旧镜像复用**未启用**，但原因不被伪装成已证明的 `BLOCKED_SECURITY_RISK`；决定性阻断是下面的旧入口过渡合同。

## 原安装器入口：独立的确定性阻断

原正式包安装器与 `v1.0.0` tag 源码逐字节一致：

| Installer | SHA-256 |
| --- | --- |
| nexusquant.ps1 | `14106d4251e654a09b4cb5f474948ef22ac9f3582fea49abe6e7afc43d0bbf29` |
| nexusquant.sh | `7396857ef801f51e632507a8d1c9fecd7851aecd5c3b4a3f722f94fed5ba05f8` |

本轮通过任务自有隔离安装的**原 v1.0.0 PowerShell 安装器**，对未改动的真实 v1.0.1 候选执行 `check-update` 并生成原合同 CHECK 回执：exit0、status=`CHECKED`、resultCode=`UPDATE_INELIGIBLE`。没有用目标安装器替代旧入口，也没有执行 update 或启动数据库。

独立只读复核发现，即便后续保持 PG config 相同，旧入口仍无法启动当前 V47 目标：

1. v1.0.0 `Set-PackageConfig` 不生成 `RUNTIME_DB_PASSWORD` / `DB_ROLE_FORMAT`。
2. 旧 `Start-Runtime` 只启动 postgres/backend/frontend，不执行新 maintenance。
3. 目标 Compose 要求独立运行凭据，runtime.yml 关闭业务 Flyway；角色准备与迁移应由 maintenance 执行。
4. 用原任务 V100 配置和完整校验的目标 Compose 实际执行 `docker compose ... config --quiet`，exit **1**，backend/maintenance均明确报 `RUNTIME_DB_PASSWORD` 缺失。该命令只解析配置，不启动或更改数据库。

原 `check-update` 的 `UPDATE_ELIGIBLE` 只证明版本/schema单调及config相等，不能证明后续角色准备和V34→V47成功。既有7项原 Tag 合成回归覆盖相同config成功、不同config拒绝，但同样不能代替真实V47过渡。POSIX旧入口具有相同的角色过渡缺口。

此发现由 `/root/pg_reuse_review` 独立复现，外部证据 `independent-old-entry-compatibility.json` SHA-256 `ab191b25dbc37e3a151ec326ca6bacb3fd9f9c9b3769ef38740490828c7507e1`，不是实现者切换角色自审。

因此，builder-only 绑定旧镜像最多消除第一个 digest guard，不能恢复本任务所要求的原入口实际升级。需要单独设计和资格验证兼容原入口的 updater/角色过渡合同，或明确允许既有可信目标安装器的过渡入口。不能修改历史安装器、放宽digest校验、用维护/Owner身份运行应用或手动改数据库来补足。

## 定向验证

| 检查 | 实际结果 / 边界 |
| --- | --- |
| 正式两包 manifest→archive→OCI config/layers | PASS，全量PG层摘要匹配 |
| PG版本、架构、程序内容 | PASS，两图1087文件相同；系统包差异单列 |
| 当前 `test_installer.py` | 33 tests，32 PASS / 1既有SKIP，exit0；未新增skip或删除断言 |
| 原v1.0.0 Tag入口复用既有合成夹具 | 7/7 PASS，exit0，明确 SIMULATED |
| 原Windows入口真实check-update | CHECKED / UPDATE_INELIGIBLE；target未改动 |
| 目标Compose与原V100配置 | 实际exit1，缺RUNTIME_DB_PASSWORD |
| 相同config、不同config、错身份、确认、失败恢复 | 原/当前合成合同覆盖，不能升级为真实平台资格 |
| 本轮V34→V47、Roles、RLS、Definer | NOT_RUN；原入口过渡已无效，未执行手工Migration |
| 经济事实 | 本轮无DB命令/业务mutation；原失败资格的32表MATCH仅按原scope保留，不作为本轮升级PASS |
| Rollback | 本轮产品路径NOT_RUN；合成失败恢复案例通过 |
| CI | 未修改运行源码，未触发新exact-head CI，不用旧CI冒充新候选资格 |

Windows下既有POSIX file-mode测试的SKIP如实保留；没有把Git Bash夹具当原生Linux/macOS资格。未重跑历史GateAUDIT或完整Release Qualification。

## 修改选择与交付状态

- Builder Changes：NONE；不交付无法满足旧入口约束的片面解锁。
- Installer Changes：NONE，尤其未改v1.0.0历史字节和POSTGRES_CONFIG_DIGEST严格比较。
- Manifest/Compose/Migration/Order/Ledger/Risk：NONE。
- PostgreSQL Image Reuse：NOT_ENABLED；没有回退已完成的安全补丁或替换包字段。
- 必要仓库变更仅本报告；精确暂存后停止。
- P0：本轮限定检查未发现。
- P1：2项发布阻断——既有PG config身份不兼容、原入口不支持受限运行身份/maintenance过渡；安全扫描告警不机械计入。
- P2：没有本轮已识别的未修复产品P2。原报告、19文件候选、归档及失败全部保留。

原始材料、命令、日志、SHA与源包层证明位于 `E:/nq-v101-pg-compatibility-20261024/`，索引 `evidence-index.json`；正式包与数据库备份仍保留在原目录，不纳入Git。首次材料采集因Docker engine关闭失败，启动引擎后按相同immutable ID采集成功；没有更换镜像。首版外部归档结果把product version误标为postgresVersion，r2仅修正字段名为packageVersion，原版保留；PG实际版本始终来自镜像内命令。

## 最终输出

```text
Task: NQ-V101-POSTGRES-IMAGE-COMPATIBILITY-UNBLOCK

Baseline:
Branch: codex/nq-v101-release-manifest-delivery
HEAD: 885b76ae92f0d3f26cb3d31a15bd2d607d799faf
Starting Tree: 2145e55070afad4fcdfe0227c11e893a81fba19e
Final Tree: EXTERNAL_FINAL_CANDIDATE_RECEIPT

Root Cause:
Source PostgreSQL Version: 16.15 / Debian 16.15-1.pgdg13+2
Target PostgreSQL Version: 16.15 / Debian 16.15-1.pgdg13+2
Source Config Digest: sha256:275447c94b11b151decd1f29877965301d5a77032037c46de93d990f739f00a9
Target Config Digest: sha256:066167ff39c2a612402ce99b734ea1cff92a9d1f2ac52f697a5f0384f0d88d6b
Difference: XZ_OS_SECURITY_UPDATE;PG_PROGRAM_BYTES_IDENTICAL

Remediation:
Selected Approach: NO_SOURCE_CHANGE / ORIGINAL_ENTRY_TRANSITION_BLOCKED
Image Provenance: VERIFIED_MANIFEST_ARCHIVE_OCI_CONFIG_LAYERS
Builder Changes: NONE
Installer Changes: NONE
Manifest Changes: NONE

Verification:
Original Installer: ACTUAL_CHECK_UPDATE_INELIGIBLE / 7_SIMULATED_TESTS_PASS
PostgreSQL Identity: TWO_DISTINCT_IDENTITIES_VERIFIED
Security Applicability: BOUNDED_DELTA_REVIEW / NO_GLOBAL_SAFETY_CLAIM
Upgrade Compatibility: BLOCKED_ORIGINAL_ENTRY_ROLE_TRANSITION
Database Roles: NOT_RUN
RLS: NOT_RUN
Flyway: NOT_RUN
Economic Facts: NO_DB_MUTATION_THIS_TASK
Rollback: SIMULATED_PASS / REAL_PATH_NOT_RUN
CI Compatibility: LOCAL_TARGETED_TESTS_ONLY

Findings:
P0: 0_IDENTIFIED_IN_SCOPE
P1: 2_RELEASE_BLOCKERS
P2: 0_UNRESOLVED_IN_SCOPE

Decision: BLOCKED / ORIGINAL_INSTALLER_RESTRICTED_RUNTIME_TRANSITION_UNSUPPORTED
Formal Upgrade: NOT_RUN
Public Demo: NOT_READY
LIVE: DISABLED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Release: NOT_CREATED
```

完成有界调查和定向验证后停止，不自动开启新的正式发行资格、数据库镜像转换或Git发布。
