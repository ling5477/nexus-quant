# V1 发布源码边界

`source-manifest.json` 使用逐文件精确列表，当前候选目标版本为 `1.0.3`，必须与同一提交内的 `VERSION` 一致。清单与源码必须来自同一个完整 40 位 Git commit；没有工作区复制、通配符发现或导出时重写文件。新增文件默认 `REVIEW_REQUIRED`，后续安装器与文档必须经过审查后显式加入清单。Java 主源码/正式资源与 frontend/src 的新增文件若未纳入，覆盖门禁拒绝导出；已审查的开发配置、开发页和未使用素材继续排除。发现范围不会自动将文件加入发行包，也不能通过改标 EXCLUDE_TEST 隐藏正式源码。

```powershell
python scripts/release/release_source.py export --repo E:/Project/nexus-quant --commit <40-hex> --output E:/release-export --audit E:/release-audit.json
python scripts/release/release_source.py verify --repo E:/Project/nexus-quant --commit <40-hex> --output E:/release-export
python scripts/release/release_source.py coverage --repo E:/Project/nexus-quant --commit <40-hex>
python scripts/release/verify_release_boundary.py --repo E:/Project/nexus-quant --commit <40-hex> --output E:/release-export
python scripts/release/scan_release_secrets.py --repo E:/Project/nexus-quant --commit <40-hex> --output E:/release-export --gitleaks <verified-absolute-binary-path>
python -m unittest discover -s scripts/release/tests -v
```

输出目录必须尚不存在且位于仓库外。先验证完整原始文件集合，再在导出树运行 `mvn clean package -Dmaven.test.skip=true`、`npm ci`、`npm run build`。构建生成文件会使严格源码校验失败；请保留另一份未经构建的导出树用于复现校验。Windows 的执行位记入机器清单，POSIX 系统同时校验实际执行位。扫描器失败或未知文件类型不能视为通过。

`.release-source.json` 是唯一生成的元数据文件。文件列表按路径排序；文件 SHA256 使用 Git blob 原始字节，清单 SHA256 使用同一 commit 内清单的原始字节。`releaseTreeSha256` 为下述对象的 SHA256：`{sourceCommit,targetVersion,manifestSha256,files}`，编码采用 UTF-8、键排序、紧凑 JSON 分隔符、不转义 Unicode、不加末尾换行。执行位、blob ID、路径和文件内容 SHA 均纳入算法。两次导出元数据完全一致，算法由固定编码测试约束。

验证器复用同一 commit 的既有阶段语义正则，只允许精确路径、token、行 hash 的业务或持久化兼容标识；历史文档豁免不能用于发布。额外检查空格分隔的历史阶段标签。secret scan 必须扫描实际导出文件，使用默认规则或发布专用规则，不能继承开发历史 findings allowlist。静态闭包检查配合导出树的 Java/TypeScript/Vite 编译；Flyway smoke 只使用新建隔离 PG16，不启动交易运行时。

Java/Spring 的正式源码与 Maven 模块进入发布。正式回测注册表的三份 CSV 按生产依赖例外保留，消费者为 `FixtureMarketdataRegistry` 和 `FixtureHistoricalMarketDataPort`；测试独占 fixture 全部排除。仅保留前端实际引用的品牌和交易所图片。前端测试由开发 CI 显式执行，不再作为 production build 生命周期依赖。

Python research 没有 Java subprocess、运行部署或安装消费方。Java artifact preview 使用 schema ID 表达离线契约，不读取 Python 源文件。因此Python仍从Self-Hosted投影排除；Java `nq-research`等Maven模块属于正式闭包。Python生产能力通过独立Companion资产交付，排除不再表示产品不提供离线计算工具。

`application-ci.yml`、`application-test.yml`、`application-local.yml` 排除。正式运行必须显式选择 `NQ_PROFILE=prod`，该 profile 的数据库和密钥由环境注入并经过既有启动 guard。安装器仅使用正式 profile 并显式注入数据库及随机密钥；源码可构建不授予真实 provider 或部署权限。`paper`、公开行情和受限只读 profile 是明确运行能力，保留源码不代表允许启用它们。

根 README、INSTALL、CHANGELOG、VERSION 与法律文件已逐文件纳入清单；前端 source-adjacent 历史 README 继续排除。许可证已确立为 Apache-2.0，不再存在待选择许可证的阻断。

## 历史部署资产审计（保留当时分类，不是当前交付状态）

本轮不将旧部署控制面原样纳入发布源码，也不提前重写安装器。以下所有资产保留在开发树：

| 路径 | 分类与原因 |
| --- | --- |
| `deploy/canonical/deployment-contract.json` | B6_WILL_REPLACE：既有 canonical artifact 安装契约，需与新源码发布和跨平台安装入口收敛 |
| `deploy/canonical/nq-canonical.service` | B6_WILL_REPLACE：既有 Linux 单机服务模板 |
| `docker-compose.yml` | B6_WILL_REPLACE：PG17 本地开发 Compose，不是 PG16 正式安装 |
| `.env.example`、`frontend/.env.example` | B6_WILL_REPLACE：开发配置示例，正式安装需 SIM、LIVE disabled、kill engaged、private OKX disabled |
| `scripts/deployment/New-NqCanonicalRelease.ps1` | B6_WILL_REPLACE：从仓库和 CI 制品创建既有 artifact，非本轮 source exporter |
| `scripts/deployment/New-NqCanonicalReleaseAdmission.ps1` | B6_WILL_REPLACE：既有 artifact admission |
| `scripts/deployment/Test-NqCanonicalRelease.ps1` | B6_WILL_REPLACE：既有 artifact verifier |
| `scripts/deployment/Test-NqCanonicalReleaseAdmission.ps1` | B6_WILL_REPLACE：既有 artifact admission verifier |
| `scripts/deployment/Install-NqCanonicalRelease.ps1` | B6_WILL_REPLACE：既有 immutable Linux installation/activation |
| `scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1` | B6_WILL_REPLACE：既有仓库依赖的恢复演练 |
| `scripts/deployment/Test-NqCanonicalRestoreProof.ps1` | B6_WILL_REPLACE：既有恢复证据校验 |
| `scripts/deployment/nq-canonical-release.psm1` | B6_WILL_REPLACE：既有 artifact/control 辅助模块 |
| `scripts/deployment/nq-canonical-database.psm1` | B6_WILL_REPLACE：既有安装/恢复数据库辅助模块 |
| `scripts/deployment/NqCanonicalFlywayLauncher.java` | DEV/CI_ONLY：隔离迁移 smoke launcher，不是应用入口 |
| `scripts/deployment/Build-NqCanonicalReleaseCi.ps1` | DEV/CI_ONLY：开发 CI artifact build |
| `scripts/deployment/Install-NqCanonicalReleaseCi.ps1` | DEV/CI_ONLY：开发 CI 安装测试 |
| `scripts/deployment/Invoke-NqCanonicalRestoreCi.ps1` | DEV/CI_ONLY：开发 CI 恢复测试 |
| `scripts/deployment/Test-NqCanonicalReleaseCi.ps1` | DEV/CI_ONLY：开发 CI artifact 验证 |
| `scripts/deployment/tests/**` | DEV/CI_ONLY：部署测试与并发 worker |

原审计时 CURRENT_RELEASE_REQUIRED=0、OBSOLETE=0 仅为历史结果。production Docker/Compose、安装器、运维入口与管理员初始化已实现并显式纳入当前精确清单；不据历史分类删除开发资产，新增文件仍不自动导出。

此前源码整理未创建release分支或tag。当前用户已授权本地release/1.0.0白名单Snapshot；仍不创建tag或部署到现有运行环境。

用户明确授权有界例外：14 个 Java 文件中 15 行历史阶段注释仅移除标签，另授权 Ledger 的一行 RC 历史注释修复；V1 baseline 的两处 SQL COMMENT 文本去除历史/歧义措辞。后者改变 migration blob/checksum，DDL/DML/ACL 不变，结构 schema delta=0、COMMENT delta=2。保留旧 B4 证据，使用新导出在新建隔离 PG16 上重新 migrate/validate，不使用 repair，不访问或改写冻结数据库。CI schema manifest 的两个摘要同步新事实；除还原两个 COMMENT 外，其余 catalog fingerprint 与 B4 完全一致，seed 合同相同。这是直接用户授权的例外，不改变其他已执行/已发布 migration 不可改写规则。

正式路由移除公开开发自检 `/dev/design-system`，对应页面和 CSS 保留在开发树并显式排除；业务账户页面移除 RC 批次 badge。RC 历史说明统一为业务语义，未改订单、撮合、账务或恢复 Java 语句。

默认 gitleaks 对 `BinanceEd25519RequestSigner` 的两条 PEM 头尾字符串产生一项误报。发布扫描仅在规则、完整文件 SHA256、路径、22–23 行位置及两个常量字节全部匹配时识别为 `PROTOCOL_DELIMITER_NOT_KEY_MATERIAL`；文件任何变化或其他 finding 均不能豁免。扫描使用默认规则、无历史 allowlist，输出仅包含规则及位置。工具必须为校验过来源的 gitleaks 8.18.4；本轮使用官方 archive checksum 校验的二进制。

## 当前安装源码边界

上述部署审计保留为前次源码导出证据。当前 VERSION、产品文档、production Docker/Compose、三平台安装和运维入口已逐文件纳入 source-manifest.json；既有 canonical 部署脚本仍为开发资产，不进入安装包。正式运行采用 prod 与 public-marketdata-manual 的精确组合，并由安装配置显式提供数据库及随机密钥，允许公开行情，关闭私有诊断和真实交易。Windows 11 amd64 / 已运行 Docker Desktop 的本地全新安装、备份恢复和重启已有隔离资格；其他平台不声明正式支持，升级仍为 UPGRADE_NOT_VERIFIED。Apache-2.0 许可证已确定。C4 旧候选72小时终态为 NOT_ACCEPTED / EVIDENCE_GAPS，原件不改。用户已明确 C4_OWNER_DECISION=ACCEPT_WITH_EVIDENCE_EXCEPTION、C4_72H_RERUN=WAIVED，例外与原审查分层记录。现有合同未禁止保留原审查并单列用户证据例外；本次已授权的Release Admission将历史UI缺口按ADMITTED_WITH_DOCUMENTED_EXCEPTION准入。新增行为另以39项定向PG/HTTP/跨JVM证明及独立候选审查覆盖；不将owner例外扩大为新增代码免验证。本地资格不等于公开发布。

2026-10-20 文档同步涉及清单内文件，本轮已授权本地提交与新包冻结：使用新完整开发Commit中的清单和Git Blob导出，在新目录构建；旧包与旧摘要保持不变。source-policy 本身为开发说明，按原清单排除，docs/current、历史验收与审计不进入发行，不扩大白名单。

## 独立Release Snapshot来源映射

正式包SOURCE_SHA始终绑定原导出器读取policy与业务Git Blob的完整开发提交。release/1.0.0可采用无父独立Snapshot历史，只含原INCLUDE文件与唯一生成的.release-source.json；其commit message记录完整开发SHA、原dev集成基线、清单及release-source摘要。Snapshot是精确投影身份，不冒充开发提交，不改写metadata sourceCommit，不额外塞入开发导出器或source-manifest以扩大白名单。Snapshot不能单独作为policy导出仓库；重建由声明的完整开发提交导出，构建输入全集必须与Snapshot tree逐字节一致。新SHA远端CI与本地验证分别记录。

## 当前候选资格边界

v1.0.2 候选目标为 NQ_SELF_HOSTED、Windows 11 amd64、Docker Desktop Linux Containers、PostgreSQL 16、Schema V49。`support-matrix.json` 保留 v1.0.0 历史资格，不作为本候选 PASS。候选记录由当前 VERSION、本逐文件清单、产品安装说明及 `docs/current/evidence/NQ_V102_RELEASE_READINESS.md` 共同表达；未提交的准备结果不能生成正式来源身份。必须依次取得已提交源码、验证过的不可变导出、不可变镜像、可信 Package Manifest 和安装包，再执行新安装、v1.0.1 普通更新及恢复资格。历史 Tag、Snapshot、包和证明保持不变。

普通v1.0.1→v1.0.2更新包按INSTALL的准确PG输入构建：使用`-Architectures amd64 -PostgresImage <原包immutable-ID> -ExpectedPostgresConfigDigest <原包config-digest>`。两参数必须成对且单架构；显式复用不拉取公共标签，归档校验不符拒绝Manifest。镜像来源必须先对照可信v1.0.1 Manifest和TAR核实；此检查不替代新候选包资格。

## v1.0.3与独立Python Companion

三阶段业务已接受；Self-Hosted目标Windows11 amd64、PG16、Schema V52，普通更新只声明经资格的v1.0.2/V49→v1.0.3/V52。前文v1.0.2候选/升级说明保留历史，不覆盖本版本；V1～V52原字节和历史发行不改。

Python使用release/python-companion-source-manifest.json精确列表，通过scripts/release/python_companion.py从同一40位SOURCE_SHA读取Git Blob，生成独立.python-companion-source.json和sourceHash。所有research/py/src生产文件必须显式列入，不能自动发现并接纳；新文件遗漏即拒绝。VERSION、Wheel和Companion均1.0.3，因子/Artifact/方法合同不随产品名重写。

先export/verify，再使用投影中的release/build-python-companion.ps1于干净Windows amd64/CPython3.11构建；runtime与构建工具由python-companion-dependencies.json精确锁定，NumPy2.3.5 Windows cp311 Wheel摘要/元数据必须吻合。构建器不从工作树复制业务文件，不联网解析运行依赖。最终独立ZIP包含Wheel、依赖、hash安装锁、来源/版本/摘要manifest、说明与许可证；用户显式以--no-index离线安装和运行，无Java subprocess/Compose/Python守护进程。

两资产独立验证、共享开发SOURCE_SHA；Self-Hosted Snapshot不包含Python，Companion来源另有树摘要。保留未签名provenance的真实边界，不更改严格18字段Java Package Manifest、既有导出器coverage或普通升级门禁。正式候选资格/安装/恢复及最终发行授权分别记录。
