# NQ-V101-RELEASE-SOURCE-AND-FORMAL-UPGRADE-QUALIFICATION

日期：2026-10-24（Asia/Shanghai）。结论：**BLOCKED / PENDING_GIT_DELIVERY**。

本轮在正式源码 coverage 阶段发现发行清单遗漏，已完成最小修复、定向测试和独立静态复核。遵循任务停止条件，不从未提交修复导出正式源码或构建正式包，不执行正式升级。候选静态检查通过不等于不可变源码、发行制品或升级资格通过。

## 1. 基线与保护

| 身份 | 实际值 |
| --- | --- |
| Remote dev / Source Commit | `bf0875842898890de685666ae4dfab93330e02b8` |
| Source Tree | `342e875072b62ed7d48d9112b44ea2ed1d0ca386` |
| Local Branch | `codex/v101-full-stack-security-patch` |
| Local HEAD | `402dcc729b3ecc81c20a740a64fd9e979b6a01d9` |
| Initial Staged Tree | `342e875072b62ed7d48d9112b44ea2ed1d0ca386` |
| Initial Staged Files | 0；tracked 工作区干净 |
| VERSION / Manifest Target | `1.0.1` / `1.0.1` |
| 已发布版本 Tag | `v1.0.0`，未改动 |

执行前运行了 `git fetch origin`、status、HEAD、origin/dev 和 tag 检查；随后通过 `gh api repos/ling5477/nexus-quant/branches/dev --jq .commit.sha` 再次确认远端为上表提交。本地 HEAD 与 dev 提交不同，但 tree 相同；本轮正式检查指定的是远端 dev 的完整提交 SHA。

保留未跟踪的 `docs/current/evidence/NQ_V101_FULL_STACK_SECURITY_PATCH_CLOSEOUT.md`，未暂存、未纳入发行清单。其 SHA-256：`9cd418d744ce0c90257b6bf086c657cd34c282a56be3b98c696f1fcf62fa61e6`。

本轮没有修改业务 Java/前端实现、锁文件、Dockerfile、证书策略文件、历史 migration、已发布 v1.0.0 资产或既有安装数据。没有连接数据库或启动正式安装/更新。

## 2. 原始失败与必要修复

实际执行官方工具（工具与所检查提交的 Git blob 一致）：

```powershell
python scripts/release/release_source.py coverage --repo E:/Project/nexus-quant --commit bf0875842898890de685666ae4dfab93330e02b8
```

退出码 **1**，原始错误为 `RELEASE_SOURCE_FAILED_CLOSED: ValueError: REQUIRED_RUNTIME_NOT_INCLUDED`。遗漏 **76 个必需运行时文件**，包含 Tenant/distribution 核心集成源码、Public Demo 模块源码、前端 distribution 入口及 Demo/platform 源码，以及全部 **V35–V47（13 个）migration**。检查没有被绕过，原始失败保留。

另外从真实构建消费者确认三个明确缺失输入：

| 新增输入 | 必要消费者 / 原因 |
| --- | --- |
| `backend/nq-public-demo/pom.xml` | backend 的 `public-demo` Maven profile 模块与依赖 |
| `backend/nq-app/src/main/distribution/META-INF/nq-distribution.properties` | nq-app Maven filtered resource，提供制品发行身份 |
| `release/docker/verify-distribution.sh` | frontend Dockerfile 的 COPY 与 nginx 启动身份校验 |

修复 `release/source-manifest.json`：仅新增上述 **79 个逐文件路径**，无删除或通配符。所有非路径字段、分类/用途、排除路径、资源例外、法律资产及 forbidden 规则保持不变。新增路径全部来自固定基线已有的普通 Git blob；没有改写任何 migration 内容。候选清单共 **2,180 个 included 文件**，其中现有 coverage 规则要求的运行时文件为 **2,124**，migration 为 **47**。

修复 `release/docker/security/README.md`：其第 11 行引用 `docs/current/`，按既有官方边界规则会产生 `DEV_REFERENCE`。改为自包含的缓解资格说明；保留供应商来源、精确摘要、OpenJDK 关联与 Temurin 支持限制，不新增豁免，不升级为 vendor-patched 声明。

修复 `CHANGELOG.md`：将 What's New、版本历史与目标更新说明的“工作区候选，尚未合并”更正为“已合并，尚未发布；正式包体验待资格验证”。Git 历史及 PR #74 API 均确认 `04b19f444cc5ddb8bfcdbdaa58f00d2ed4e8a69f` 已合并；未修改 1.0.0 历史段落。

## 3. 定向验证与独立复核

| 检查 | 实际结果 | 证据边界 |
| --- | --- | --- |
| 官方 exact-commit coverage | **FAIL，exit 1** | 原始已提交清单遗漏；修复未提交，不能追认原提交 PASS |
| `python -m unittest discover -s scripts/release/tests -p test_release_source.py -v` | **29/29 PASS，exit 0** | exporter 身份隔离、路径拒绝、覆盖失败、边界/法律 fixture；不是产品包测试 |
| 候选输入内存静态检查 | **PASS** | 固定基线 blobs + 明确候选 manifest/两份文档；没有写出源码树或 `.release-source.json` |
| 候选 runtime coverage | **2,124 / 47，遗漏为零** | 复用现有 `runtime_coverage`；只表示候选清单覆盖 |
| Maven module/profile/resource 与 Docker COPY 输入 | **静态检查 PASS** | 不是真实 Maven/npm/Docker 构建闭包证明 |
| 既有语义/开发引用规则 | **候选静态检查 PASS** | 使用基线 checker 的正则和允许分类；原 README 失败单列保留 |
| What's New / version history | **system 源文件均在清单** | 不等于正式包 UX 运行通过 |
| `git diff --cached --check` | **PASS** | 最终暂存检查另在外部收据记录 |

外部候选检查器首轮因解析非 COPY 的多行 Docker RUN 发生 `shlex ValueError: No escaped character`，未形成产品结论。仅修正检查器为解析 COPY 行，第二轮通过；没有修改产品、官方工具或验收规则。失败说明独立保留。

一次独立只读定向审查由 `/root/release_manifest_review` 完成，绑定下表三份 Git blob。审查独立复现原 76 项遗漏，并分别移除 V47、`app/render.tsx`、`TenantId.java` 做内存负例，现有 coverage 均准确拒绝。79 个新增路径存在、未触及 forbidden/exclusions；23 个 Maven 模块路径闭包齐全。结论 **CANDIDATE_STATIC_REVIEW_PASS**，未发现新增 BLOCKING/NON_BLOCKING 问题；正式资格仍待 Git 交付与后续实际运行。

Self-Hosted 静态选择保持：Maven 默认 `NQ_SELF_HOSTED`，Demo 模块/依赖仅在 `public-demo` profile；Vite 默认 Self-Hosted aliases，既有 bundle 校验拒绝 Demo/platform 模块进入最终依赖图。共享源码纳入清单不会自行启用 Demo profile。实际最终 jar、前端 bundle 和运行时 API 的隔离证明本轮 **NOT_RUN**。

JDK 策略及安装器所需文件已在清单内：`blocked.certs`、安装脚本、两份 Dockerfile、Windows/Posix installer、Linux/macOS 入口及 runtime Compose。供应商文件固定 SHA-256 `2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10` 保持不变。角色分离实现未改动；其真实迁移、权限/RLS、失败与恢复效果仍须正式包验证。平台支持没有扩大，Linux/macOS 入口存在不代表本轮获得资格。

## 4. 修复候选身份

三文件实现候选 tree（尚不含本报告）：`b872370435c1f7978b4c12cf894232ddda90711f`。最终四文件 staged tree 在外部 `final-staged-candidate.json` 和最终答复记录，避免报告自引用其自身 tree。

| 文件 | Git Blob | SHA-256（暂存字节） |
| --- | --- | --- |
| `release/source-manifest.json` | `4c9463c145da00a00e080c76e789fda87ed75b40` | `13321160cf0b753fccc9145589d032afba9c9a00b5594d22973a03d840d7a5a5` |
| `release/docker/security/README.md` | `9ec1d1c23e965c870a5843723e68b7794b8e5e07` | `3bc115b3afa3d2084e01c44918a508362060f82f2b8ba3a2f1762f5dadee791b` |
| `CHANGELOG.md` | `59b87e46b07cad8b0f8a22c6b04adaa8e7e4c942` | `bf0a7f67efa1a983c62e2d359b7b34f46c0e6c99674b6340e6381fbcdfab78a2` |

仅精确暂存三份修复及本报告，不执行 `git add .` / `git add -A`。没有新增提交，也没有新候选 CI；旧 PR/merge CI 不能代替本修复的 exact-head CI。

外部原始证据目录：`E:/nq-v101-release-qualification-20261024-bf087584/`。

| 文件 | SHA-256 |
| --- | --- |
| `source-coverage-before.log` | `043deb52beb25fbcc891f35d7fd13dec9ffde72d22a3a374e9827e729ddab965` |
| `release-source-tests.log` | `125ebb4b62493f529db4b708090cb515b4c03faa2ec7ddc87b33de69084ed8d1` |
| `candidate-input-check.json`（完整79路径及base blob/SHA） | `699410320f480990902d9734b2a75b081655974c94d93317cd5a6c2122aa9d42` |
| `check_candidate_inputs.py`（非正式静态检查器） | `816843f3f1437f38e32b937608541d79ebaf2050bef9e4c0aed2c404cc011347` |
| `candidate-input-check-first-failure.txt` | `c6072151f423d61b635e71fabb3f08ea4c785a547e19fe8bab8405a74f2dddf2` |
| `implementation-candidate.json` | `2edb582939e46f687fa4b46e3dde171e6962907ad7f8ba25d273c9b8c397f02d` |

外部检查器不是新增发行入口，也不替代 `release_source.py` / `verify_release_boundary.py` / `scan_release_secrets.py`。正式任务恢复后必须从新的可信已提交身份重新执行这些门禁。

## 5. 正式资格状态与发现

| 门禁 | 状态 | 原因 |
| --- | --- | --- |
| IMMUTABLE_SOURCE | BLOCKED | 原提交 coverage 失败，修复待 Git 交付 |
| RELEASE_MANIFEST | BLOCKED | 候选静态修复通过，尚非正式已提交来源 |
| SOURCE_SECRET_SCAN | BLOCKED / NOT_RUN | 无合格正式导出；未下载/核验 Gitleaks 或运行默认扫描，未继承历史 allowlist |
| OFFICIAL_PACKAGE_BUILD | BLOCKED / NOT_RUN | 明确停止条件；没有正式镜像、包 manifest 或 ZIP |
| JDK_MITIGATION_IN_PACKAGE | BLOCKED / NOT_RUN | 既有 VERIFIED_MITIGATION 不能替代本轮正式制品证书行为验证 |
| FORMAL_UPGRADE | BLOCKED / NOT_RUN | 未安装官方旧包、未执行 check-update/update、未做 V34→V47 |
| HISTORICAL_DATA | BLOCKED / NOT_RUN | 未读取/比较本轮32表数据；不复用旧 MATCH 或已解释 DIFFERENT 冒充新证据 |
| DATABASE_ROLE_SPLIT | BLOCKED / NOT_RUN | 无正式包隔离 PG16 升级、非超级用户/RLS/SECURITY DEFINER 实证 |
| RESTORE | BLOCKED / NOT_RUN | 未执行备份、角色/migration/启动故障注入、事务恢复或再次读取经济事实 |
| SELF_HOSTED_RUNTIME | BLOCKED / NOT_RUN | 未启动正式包或执行 HTTP/Canonical SIM 链 |
| RELEASE_UX | BLOCKED / NOT_RUN | 未运行正式版本检测、明确确认、What's New、中英文与版本历史 |

P0：本次限定源码检查未发现；不代表全量安全审计结论。

P1：1 项发行阻断——已提交 manifest 缺少必需运行/构建输入。最小修复已暂存，但须先 Git 交付，再重新执行正式来源及后续门禁。

P2：两项文档问题（开发证据路径泄漏、合并状态过时）已在候选修复，无本轮已识别的未修复 P2。宿主/CPython、递归工具依赖、Gitleaks 工具来源与校验尚未取得本轮资格，保留未验证状态，不机械判定为 P1。没有启动全量依赖扫描、Tenant 审计或历史 GateAUDIT。

## 6. 最终输出

```text
Task:
NQ-V101-RELEASE-SOURCE-AND-FORMAL-UPGRADE-QUALIFICATION

Baseline:
Source Commit: bf0875842898890de685666ae4dfab93330e02b8
Source Tree: 342e875072b62ed7d48d9112b44ea2ed1d0ca386

Release:
Target Version: 1.0.1
Source Manifest: BLOCKED / CANDIDATE_STATIC_PASS / PENDING_GIT_DELIVERY
Source SHA256: NOT_CREATED
Backend Image: NOT_BUILT
Frontend Image: NOT_BUILT
PostgreSQL Image: NOT_QUALIFIED_THIS_RUN
Package Manifest: NOT_CREATED
Package SHA256: NOT_CREATED

Security:
JDK Mitigation: EXISTING_VERIFIED_MITIGATION / IN_PACKAGE_NOT_VERIFIED
Secret Scan: BLOCKED / NOT_RUN
Build Chain: CANDIDATE_INPUT_STATIC_PASS / FORMAL_BUILD_NOT_RUN

Upgrade:
Source Version: 1.0.0
Target Version: 1.0.1
Official Installer: NOT_RUN
Flyway: SOURCE_V1_TO_V47_INCLUDED / UPGRADE_NOT_RUN
Database Roles: NOT_VERIFIED_THIS_RUN
RLS: NOT_VERIFIED_THIS_RUN
Historical Facts: NOT_COMPARED_THIS_RUN
Canonical SIM: NOT_RUN
Update UX: NOT_RUN
Backup: NOT_RUN
Rollback: NOT_RUN

Verification:
Source: BLOCKED / CANDIDATE_STATIC_AND_29_FIXTURE_TESTS_PASS
Package: BLOCKED / NOT_RUN
PostgreSQL: BLOCKED / NOT_RUN
HTTP: BLOCKED / NOT_RUN
Frontend: BLOCKED / NOT_RUN
Recovery: BLOCKED / NOT_RUN

Findings:
P0: 0_IDENTIFIED_IN_SCOPE
P1: 1_PENDING_GIT_DELIVERY
P2: 0_UNRESOLVED_IN_SCOPE

Decision:
BLOCKED / PENDING_GIT_DELIVERY

Public Demo: NOT_READY
Public Email: NOT_QUALIFIED
Public Marketdata License: UNVERIFIED
Public TLS: NOT_VERIFIED
LIVE: DISABLED
Private Trading: DISABLED
Automatic Real Execution: DISABLED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
PR: NOT_CREATED
Merge: NOT_PERFORMED
Tag: NOT_CREATED
Release: NOT_CREATED
Public Deployment: NOT_PERFORMED
```

按用户停止条件结束。未提交修复不能形成正式来源身份，也不能宣称 `RELEASE_CANDIDATE_QUALIFIED`。
