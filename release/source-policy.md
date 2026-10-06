# V1 发布源码边界

`source-manifest.json` 使用逐文件精确列表，目标版本为 `1.0.0`。清单与源码必须来自同一个完整 40 位 Git commit；没有工作区复制、通配符发现或导出时重写文件。新增文件默认 `REVIEW_REQUIRED` 并排除，后续安装器与文档必须经过审查后显式加入清单。

```powershell
python scripts/release/release_source.py export --repo E:/Project/nexus-quant --commit <40-hex> --output E:/release-export --audit E:/release-audit.json
python scripts/release/release_source.py verify --repo E:/Project/nexus-quant --commit <40-hex> --output E:/release-export
python scripts/release/verify_release_boundary.py --repo E:/Project/nexus-quant --commit <40-hex> --output E:/release-export
python scripts/release/scan_release_secrets.py --repo E:/Project/nexus-quant --commit <40-hex> --output E:/release-export --gitleaks <verified-absolute-binary-path>
python -m unittest discover -s scripts/release/tests -v
```

输出目录必须尚不存在且位于仓库外。先验证完整原始文件集合，再在导出树运行 `mvn clean package -Dmaven.test.skip=true`、`npm ci`、`npm run build`。构建生成文件会使严格源码校验失败；请保留另一份未经构建的导出树用于复现校验。Windows 的执行位记入机器清单，POSIX 系统同时校验实际执行位。扫描器失败或未知文件类型不能视为通过。

`.release-source.json` 是唯一生成的元数据文件。文件列表按路径排序；文件 SHA256 使用 Git blob 原始字节，清单 SHA256 使用同一 commit 内清单的原始字节。`releaseTreeSha256` 为下述对象的 SHA256：`{sourceCommit,targetVersion,manifestSha256,files}`，编码采用 UTF-8、键排序、紧凑 JSON 分隔符、不转义 Unicode、不加末尾换行。执行位、blob ID、路径和文件内容 SHA 均纳入算法。两次导出元数据完全一致，算法由固定编码测试约束。

验证器复用同一 commit 的既有阶段语义正则，只允许精确路径、token、行 hash 的业务或持久化兼容标识；历史文档豁免不能用于发布。额外检查空格分隔的历史阶段标签。secret scan 必须扫描实际导出文件，使用默认规则或发布专用规则，不能继承开发历史 findings allowlist。静态闭包检查配合导出树的 Java/TypeScript/Vite 编译；Flyway smoke 只使用新建隔离 PG16，不启动交易运行时。

Java/Spring 的正式源码与 Maven 模块进入发布。正式回测注册表的三份 CSV 按生产依赖例外保留，消费者为 `FixtureMarketdataRegistry` 和 `FixtureHistoricalMarketDataPort`；测试独占 fixture 全部排除。仅保留前端实际引用的品牌和交易所图片。前端测试由开发 CI 显式执行，不再作为 production build 生命周期依赖。

Python research 没有 Java subprocess、运行部署或安装消费方。Java artifact preview 使用 schema ID 表达离线契约，不读取 Python 源文件。因此 Python 全部排除；Java `nq-research` 等 Maven 模块仍属于正式源码闭包。

`application-ci.yml`、`application-test.yml`、`application-local.yml` 排除。正式运行必须显式选择 `NQ_PROFILE=prod`，该 profile 的数据库和密钥由环境注入并经过既有启动 guard。当前基础配置仍有开发 fallback，这是 B6 安装入口必须收敛的 gap；B5 仅证明源码可构建，不授予默认启动、真实 provider 或部署权限。`paper`、公开行情和受限只读 profile 是明确运行能力，保留源码不代表允许启用它们。

现有根 README 引用开发 authority 和历史目录，不能原样发布；前端 source-adjacent 历史 README 同样排除。`README.md`、`INSTALL.md`、`CHANGELOG.md`、`VERSION` 等正式文件待后续完成。当前未选许可证，`LICENSE_REQUIRED_BUT_UNSELECTED` 是最终发布 blocker；本轮不得代选许可证。

## 现有部署资产审计

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

CURRENT_RELEASE_REQUIRED=0，OBSOLETE=0；安装与操作尚未作为 V1 交付，不据此删除既有资产。B6 需要 production Docker/Compose、Windows/macOS/Linux 安装器、start/stop/restart、backup/restore、uninstall，以及 admin bootstrap / forced password change。B6 新文件不会自动导出，必须加入精确清单。正式文档和许可证闭包由 B6/B7 完成。

本轮不创建正式 release 分支或 tag，不部署到任何现有运行环境。

用户明确授权有界例外：14 个 Java 文件中 15 行历史阶段注释仅移除标签，另授权 Ledger 的一行 RC 历史注释修复；V1 baseline 的两处 SQL COMMENT 文本去除历史/歧义措辞。后者改变 migration blob/checksum，DDL/DML/ACL 不变，结构 schema delta=0、COMMENT delta=2。保留旧 B4 证据，使用新导出在新建隔离 PG16 上重新 migrate/validate，不使用 repair，不访问或改写冻结数据库。CI schema manifest 的两个摘要同步新事实；除还原两个 COMMENT 外，其余 catalog fingerprint 与 B4 完全一致，seed 合同相同。这是直接用户授权的例外，不改变其他已执行/已发布 migration 不可改写规则。

正式路由移除公开开发自检 `/dev/design-system`，对应页面和 CSS 保留在开发树并显式排除；业务账户页面移除 RC 批次 badge。RC 历史说明统一为业务语义，未改订单、撮合、账务或恢复 Java 语句。

默认 gitleaks 对 `BinanceEd25519RequestSigner` 的两条 PEM 头尾字符串产生一项误报。发布扫描仅在规则、完整文件 SHA256、路径、22–23 行位置及两个常量字节全部匹配时识别为 `PROTOCOL_DELIMITER_NOT_KEY_MATERIAL`；文件任何变化或其他 finding 均不能豁免。扫描使用默认规则、无历史 allowlist，输出仅包含规则及位置。工具必须为校验过来源的 gitleaks 8.18.4；本轮使用官方 archive checksum 校验的二进制。
