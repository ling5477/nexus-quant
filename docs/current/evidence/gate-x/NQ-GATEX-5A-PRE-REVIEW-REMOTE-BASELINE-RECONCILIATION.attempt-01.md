# NQ-GATEX-5A Pre-Review Remote Baseline Reconciliation — Attempt 01

## 结论

`PASS / REMOTE_BASELINE_RECONCILED / WORKTREE_PRESERVED / V38_REVALIDATED_ON_EXACT_DEV_HEAD / READY_FOR_INDEPENDENT_MIGRATION_REVIEW`（通过 / 远端基线已对齐 / 工作树实现已保全 / V38 已在精确 dev HEAD 上重新验证 / 可进入独立 migration review）。

本结论只证明本地 `dev` 已以 fast-forward 对齐最新 `origin/dev`，原 46-path staged remediation chain 已按“26 个路径进入 HEAD + 22 个 residual 路径重新 staged”保持同一最终内容，并且 V38 在该基线上重新通过 PostgreSQL、后端与 authority 回归。它不关闭上游 `ADMISSION_MATERIALIZATION_FACT_TEAR`，不进入 GateX-5B，不启动 Shadow Run，不授权交易或 LIVE。

## Task classification 与边界

- 归属：NQ-only。
- 类型：L 级 `WORKTREE_PRESERVATION / REMOTE_BASELINE_AUDIT / EXACT_HEAD_RECONCILIATION / CONFLICT_CLASSIFICATION / POST_REAPPLY_REGRESSION`。
- 主 skill：`nq-dh-workflow-router`；本任务不是纯文档任务，未启用 `nq-docs-writer`。
- 禁止项：无新功能、无 migration 重设计、无 commit、无 push、无 GateX-5B、无 Shadow start、无 LIVE/真实交易。
- 前端 diff：0；Playwright=`NOT_RUN`（未运行），因为本任务与 remote commit 均无 frontend 变更。

## Baseline 与完整备份

- 分支：`dev`。
- old local HEAD：`7aaf6027644b2ba6cd7dc588536784be50ff1eff`。
- fetch 后 `origin/dev`：`ac4b1ba10f7ac10f973707e97c52b56a6b5aec6f`。
- 初始状态：46 staged、0 unstaged、0 untracked；`git diff --cached --check` 通过。
- 完整 staged stat：46 files changed，5426 insertions，58 deletions。
- backup path：`C:\Users\Lingyu\AppData\Local\Temp\nq-gatex5a-pre-review-reconcile-1786449422238`。
- 完整 binary patch：`gatex5a-full-staged.patch`，354268 bytes。
- 完整 patch SHA-256：`9546F1001EDDD266474B01DEE6BE363C5E7AFC458376FA3D20E9A4EC5AB8B2CE`。
- 同目录保留 `gatex5a-full-staged.name-status.txt`、`gatex5a-full-staged.stat.txt` 与 46-path 精确清单；清理前再次核对文件存在、hash 一致、当前/捕获路径数均为 46。

## Remote commits 与 changed paths

`REMOTE_COMMITS`：

```text
ac4b1ba1 (origin/dev) feat: 暂时提交
```

`REMOTE_CHANGED_PATHS` 共 26 个：

```text
M README.md
M backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/ApiExceptionHandler.java
A backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/StrategyReleaseShadowRunMaterializationController.java
A backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/StrategyReleaseShadowRunMaterializationResponse.java
M backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/shadowrun/ShadowRunProvenancePostgresIntegrationTest.java
A backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/StrategyReleaseShadowRunMaterializationSecurityWebMvcTest.java
M backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ShadowRunCreationPlan.java
A backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ShadowRunMaterializationActor.java
A backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ShadowRunMaterializationAuthorizationException.java
A backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ShadowRunMaterializationRejectedException.java
A backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ShadowRunMaterializationResult.java
A backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ShadowRunMaterializationWriter.java
M backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseAdmissionPreviewService.java
A backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseShadowRunMaterializationService.java
A backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/ShadowRunMaterializationWriterTest.java
A backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/strategyrelease/application/StrategyReleaseShadowRunMaterializationServiceTest.java
M backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcShadowRunFactRepository.java
M docs/current/README.md
M docs/current/ROADMAP.md
M docs/current/STATUS.md
M docs/current/TESTING.md
M docs/current/WORKLOG.md
A docs/current/evidence/gate-x/NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-IMPLEMENTATION.attempt-01.md
A docs/current/evidence/gate-x/NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-REVIEW.attempt-01.md
A docs/current/evidence/gate-x/NQ-GATEX-5A-ADMISSION-MATERIALIZATION-CONSISTENCY-CONTRACT-REVIEW.attempt-01.md
A docs/current/evidence/gate-x/NQ-GATEX-5A-ADMISSION-MATERIALIZATION-GUARD-SCHEMA-REVIEW.attempt-01.md
```

Remote stat：26 files changed，2904 insertions，24 deletions。

## Overlap、Flyway 与语义冲突分析

- `OVERLAP_PATHS=26`。
- 分类：`PRODUCT_CODE_OVERLAP + GOVERNANCE_OVERLAP`。
- remote-only paths：0；远端 26 个路径全部来自原 staged chain。
- 24 个重叠路径的 staged final blob 与 `origin/dev` 完全相同。
- 另外 2 个重叠路径 `ShadowRunProvenancePostgresIntegrationTest.java`、`JdbcShadowRunFactRepository.java` 在当前 staged chain 中包含 GateX-5A 后续增量；以 `origin/dev` 为基线后仍属于 residual。
- 相对 `origin/dev` 的 residual：22 paths，2522 insertions，34 deletions。
- Flyway collision：0。远端未改 migration；没有远端 V38/V39+，也未修改 `V38__gate_x5a_admission_materialization_guard.sql`。
- Semantic conflict：0。远端 core admission/Shadow 改动是当前 remediation chain 的 GateX-5 已上游前缀，不是 remote-only 分叉；冻结 contract 与 22-path GateX-5A residual 仍成立。
- Governance/authority conflict：0。远端 `STATUS.md` 与原 staged authority 内容相同，未改变 accepted/work batch、LIVE 或 Shadow trading。

未命中 `FLYWAY_BASELINE_COLLISION`、`REMOTE_SEMANTIC_CONFLICT`、`REMOTE_AUTHORITY_CONFLICT` 或 `FLYWAY_SEQUENCE_CHANGED`。

## Fast-forward 与 patch reapply

清理只使用备份清单内 46 个精确路径：27 个新增文件逐个验证绝对路径位于仓库内后删除，19 个 tracked 文件使用精确 `git restore` 恢复。未执行 `reset --hard`、`clean -fd`、`checkout .`、普通 merge 或 rebase。清理后 `git status --short` 为空。

- fast-forward：`git merge --ff-only origin/dev` 成功；`7aaf6027..ac4b1ba1`，无 merge commit。
- 完整 patch check：`git apply --check --index gatex5a-full-staged.patch` 退出码 1，按预期不能直接应用。失败全部来自 26 个已进入 HEAD 的路径，类别为 tracked hunk 已应用或新增文件 already exists；完整输出保存在 backup 目录的 `gatex5a-full-patch-apply-check.txt`。未 force apply。
- 最小 forward reconciliation：在清理前从 index 生成基于 `origin/dev` 的 `gatex5a-residual-on-origin-dev.patch`。
- residual SHA-256：`54C025C953EAE552526270DFA0888D1D414DAE8D79C8DDA0B3246EA820CE643D`；154934 bytes；22 paths。
- residual `git apply --check --index`：通过。
- residual `git apply --index`：通过。
- post-reapply staged binary diff SHA-256 与 residual backup 完全相同；无手工代码 reconciliation，无远端内容覆盖。

## Migration 与 authority reconciliation

- migration 文件数：38；最高版本：38；V38 count=1；V39+ count=0。
- V1–V37：相对最新 HEAD 与 post-reapply index 的 changed-path count=0；bytes 未变。
- V38：仍为唯一 next migration。
- authority checker：`PASS / CURRENT_AUTHORITY_CONSISTENT / errors=0`。
- authority 保持：`accepted_batch=GateX-4 / ACCEPTED|CI_GREEN`、`work_batch=GateX-5 / IMPLEMENTED|PENDING_REVIEW`、`LIVE=DISABLED`、`shadow_trading=NOT_ENABLED`。

## PostgreSQL 与 backend regression

真实 disposable PostgreSQL：Docker `postgres:17`，实际版本 `17.10`，仅绑定 `127.0.0.1` 随机端口；未连接本机默认库、生产库或真实用户数据。focused 与 final backend 使用独立 fresh 容器；容器最终删除退出码均为 0。

- `AdmissionMaterializationGuardPostgresIntegrationTest`：4 tests / 0 failures / 0 errors / 0 skipped，`BUILD SUCCESS`。
- 覆盖：Fresh V1→V38、V37→V38、Flyway validate、historical/future initialization、revision trigger/raw SQL、Shadow CREATED exact bump、CREATED event no-bump、CREATED→PRECHECKING bump、Paper/Shadow/consistency phantom、fan-out、locking、rollback atomicity、first identity binding。
- targeted `ModuleBoundaryArchTest + PackageBoundaryArchTest`：16 tests / 0 failures / 0 errors / 0 skipped，`BUILD SUCCESS`。
- focused reactor：`mvn -f backend/pom.xml -pl nq-core,nq-research,nq-infra,nq-app -am test`，23/23 reactor modules `SUCCESS`，`BUILD SUCCESS`；`nq-app` 260 tests / 0 failures / 0 errors / 21 skipped。
- full backend：`mvn -f backend/pom.xml test`，23/23 reactor modules `SUCCESS`，`BUILD SUCCESS`；`nq-app` 260 tests / 0 failures / 0 errors / 21 skipped。
- reactor/full 显式把 Spring datasource 指向 disposable DB；只加入一条无 credential、无 exchange account、无 LIVE 能力的 legacy `PAPER/ACTIVE` account fixture，供既有 local happy-path 外键前置使用。

执行 RCA：

1. 第一次 focused 命令因 PowerShell 数组拼接未把 `test` goal 传给 Maven，退出码 1；测试未运行，修正命令后 4/4 通过。
2. 第一次 reactor 命中本机默认 `localhost:5432` 的旧 V38 checksum；Flyway 正确 fail-closed。未执行 repair，改为显式 disposable datasource。
3. 一次 reactor 误把 focused `required=true` 与已迁移 public schema复用，触发既有 `pgcrypto` search-path fixture 冲突；未改 migration/test，最终按“focused 独立运行、reactor/full 默认 skip focused suite”的既有隔离模型通过。

上述失败均未修改 source、migration 或本机默认数据库，不影响最终通过 verdict。

## Git final state 与 diff checks

- current HEAD：`ac4b1ba10f7ac10f973707e97c52b56a6b5aec6f`。
- current `origin/dev`：`ac4b1ba10f7ac10f973707e97c52b56a6b5aec6f`。
- 原 46-path chain 的最终内容保持为：26 remote paths 已在 HEAD，22 residual paths staged；本 evidence 加入后 staged path count 为 23。
- unstaged=0；untracked=0。
- `git diff --check`：通过。
- `git diff --cached --check`：通过。
- 未 commit，未 push。

## Findings

### P0

- 0。工作区实现未丢失；远端有效内容未覆盖；migration history 未破坏；未触达 LIVE/真实交易。

### P1

- 本 reconciliation scope 为 0。无 Flyway collision、remote semantic conflict、authority conflict、patch contract drift 或 V38 regression failure。
- 上游 `ADMISSION_MATERIALIZATION_FACT_TEAR` 仍为 `OPEN / UPSTREAM_P1_NOT_YET_CLOSED`；V38 infrastructure implemented 不等于 P1 closure。

### P2

- 1：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`，沿用 V38 implementation 的既有保留项；本任务仅使用小型 disposable fixture，未测生产 lock window。

### P3

- 0。中间环境/命令 RCA 已关闭，不构成代码 finding。

## 最终决定与下一动作

Final decision：`PASS / P0=0 / RECONCILIATION_P1=0 / P2=1 / P3=0 / HEAD_EQUALS_ORIGIN_DEV / WORKTREE_PRESERVED / V38_REVALIDATED_ON_EXACT_DEV_HEAD / READY_FOR_INDEPENDENT_MIGRATION_REVIEW`。

唯一下一动作：`NQ-GATEX-5A-ADMISSION-MATERIALIZATION-GUARD-MIGRATION-REVIEW`。
