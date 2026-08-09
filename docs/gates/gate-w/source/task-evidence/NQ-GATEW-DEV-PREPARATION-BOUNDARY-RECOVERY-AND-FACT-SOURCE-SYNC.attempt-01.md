# NQ-GATEW-DEV-PREPARATION-BOUNDARY-RECOVERY-AND-FACT-SOURCE-SYNC — Attempt 01

## 1. 任务与初始基线

- Task ID：`NQ-GATEW-DEV-PREPARATION-BOUNDARY-RECOVERY-AND-FACT-SOURCE-SYNC`
- Attempt：`01`
- Task classification：
  `BRANCH_INTEGRITY_AUDIT / CONDITIONAL_FORWARD_REVERT / FACT_SOURCE_SYNC / EXACT_HEAD_CI_ACCEPTANCE / PREPARATION_BRANCH_RECONSTRUCTION / SELF_REVIEW`
- Starting HEAD：`cd1f42b7b02ec68d6300938543e7393c3dca44c9`
- 原 preparation commit：`cd1f42b7b02ec68d6300938543e7393c3dca44c9`
- 起始分支与远端：`dev`；`HEAD == origin/dev == cd1f42b7b02ec68d6300938543e7393c3dca44c9`；tracked/staged clean。
- 起始 authority：GateW=`IN_PROGRESS|NOT_FROZEN`；Attempt-09=`RUNNING|PENDING_168H`；GateW-FREEZE=`NOT_STARTED`；GateX=`NOT_STARTED`；LIVE=`DISABLED`。

## 2. 分支完整性审计

- `prepCommitContainedInDev=true`
- `prepCommitContainedInOriginDev=true`
- 选择路径：`PATH_A / PREPARATION_COMMIT_IN_DEV_CONFIRMED`
- `git show --name-status --format=` 证明该 commit 只包含以下 10 个 `A / Added` 文件，无额外路径或既有文件修改：
  1. `docs/drafts/pre-gatex/README.md`
  2. `docs/drafts/pre-gatex/RESEARCH_TO_SHADOW_CONTRACT_PREPARATION.md`
  3. `docs/drafts/pre-gatex/STRATEGY_RELEASE_SCHEMA_PROPOSAL.sql`
  4. `docs/drafts/pre-gatex/PYTHON_DEPENDENCY_ASSESSMENT.md`
  5. `docs/drafts/pre-gatex/NQ-PRE-GATEX-RESEARCH-TO-SHADOW-CONTRACT-PREPARATION-ATTEMPT-02.md`
  6. `backend/nq-core/src/test/resources/gatex/strategy-release-manifest.schema.json`
  7. `backend/nq-core/src/test/resources/gatex/strategy-release-manifest.golden.json`
  8. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/StrategyReleaseManifestPrototypeTest.java`
  9. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/StrategyReleaseLifecyclePrototypeTest.java`
  10. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategyrelease/preparation/SensitiveFieldPolicyPrototypeTest.java`

## 3. Forward revert

- 执行：`git revert --no-commit cd1f42b7b02ec68d6300938543e7393c3dca44c9`
- 结果：`PASS / EXACT_SCOPE`（通过 / 范围精确）。
- staged diff 只包含上述 10 个文件的删除，共 `1887 deletions`；未触及其他路径。
- 本操作是 forward correction，不改写已推送 `dev` 历史。

## 4. Current fact-source 修正

- 漂移位置：
  - `docs/current/README.md`：错误地把 `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION` 写成当前唯一动作。
  - `docs/current/FACT_SOURCE_INDEX.md`：错误地把 `GateW-FREEZE / NOT_STARTED` 写成 current work batch，并把 freeze closeout 写成唯一下一动作。
- 权威事实源：`docs/current/STATUS.md` 顶部 `nq-current-authority` 区块。
- 修正后 canonical next action：`NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-168H-ACCEPTANCE`。
- 执行条件：达到 `plannedAcceptanceAt=2026-07-29T11:19:59.5201964Z` 后执行。
- GateW freeze closeout：`NOT_STARTED`；只有 168h acceptance 得出 `ACCEPT` 后才能开始。
- `STATUS.md` machine authority 未修改。
- `TESTING.md` 与 `WORKLOG.md` 的旧 freeze-action 命中属于 append-only 历史记录，不参与 current authority；未发现其他 current drift。

## 5. Staged 文件

- 上述 10 个 preparation 文件：`D / Deleted`
- `docs/current/README.md`：`M / Modified`
- `docs/current/FACT_SOURCE_INDEX.md`：`M / Modified`
- `docs/current/evidence/gate-w/NQ-GATEW-DEV-PREPARATION-BOUNDARY-RECOVERY-AND-FACT-SOURCE-SYNC.attempt-01.md`：`A / Added`
- `docs/current/evidence/gate-w/README.md`：`M / Modified`

## 6. 验证与 post-commit gates

- 修改前 authority checker：`PASS / CURRENT_AUTHORITY_CONSISTENT / errors=0`。
- 修改后 authority checker：`PASS / CURRENT_AUTHORITY_CONSISTENT / errors=0`。
- `git diff --cached --check`：exit `0`；暂存 allowlist 精确匹配 14 个预期路径。
- Dev correction commit：`PENDING_COMMIT`。
- Exact-head `NQ CI Baseline`：`PENDING_POST_COMMIT`；不得预写为通过。
- Preparation branch 旧 local/remote SHA：`PENDING_POST_COMMIT`。
- Preparation reconstruction：`PENDING_POST_COMMIT`；只有 dev exact-head CI green 后，才允许创建备份引用并采用 `reset --hard origin/dev` + `cherry-pick cd1f42b7...` 重建。
- Preparation 新 SHA、两组 Maven 测试与远端 SHA：`PENDING_POST_COMMIT`；实际结果由本轮最终执行报告记录。

## 7. Findings

- P0：无。
- P1：`cd1f42b7...` 的 10 个 Pre-GateX preparation 文件误入 `dev`；两个 current summary 与 `STATUS.md` 冲突。
- P2：`check-current-authority.ps1` 在上述两个 current summary 漂移存在时仍返回 `errors=0`；本轮禁止修改 checker。
- P3：无。

## 8. 边界、影响与下一动作

- GateW runtime impact：无。未操作服务器、systemd、release、symlink、soak unit、credential、权限、IP allowlist、交易所或交易写侧。
- GateW 保持 `IN_PROGRESS / NOT_FROZEN`；Attempt-09 保持 `RUNNING / PENDING_168H`；GateX 保持 `NOT_STARTED`；LIVE 保持 `DISABLED`。
- 治理 authority 唯一下一动作保持 `NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-168H-ACCEPTANCE`。
- 本任务全部 post-commit gates 成功后的 preparation-lane 后续任务：`NQ-PRE-GATEX-ARTIFACT-VERIFICATION-SECURITY-PROTOTYPE-ATTEMPT-01`；本轮不执行该任务，且该任务名不改变 current authority。
- 当前决策：`DEV_CORRECTION_READY / POST_COMMIT_GATES_PENDING`。
