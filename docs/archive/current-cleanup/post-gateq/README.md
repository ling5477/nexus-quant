# NQ Docs Current Cleanup Post GateQ Archive

本目录保存 `NQ-DOCS-CURRENT-LEANUP-POST-GATEQ-AUDIT-AND-ARCHIVE` 的审计、分类和移动结果。本轮只做 docs-only governance cleanup，不实现功能、不改业务代码、不新增 API、不新增 migration、不改 CI、不新增前端页面、不新增测试。

## 1. Cleanup Decision

- Cleanup status: `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`。含义：`IMPLEMENTED`（已实施）、`SELF-REVIEWED`（已自审）、`READY TO COMMIT`（可进入提交前复核）。
- Current docs count before: 125 tracked Markdown files under `docs/current`.
- Current docs count after: 17 tracked Markdown files under `docs/current`.
- Moved to `docs/archive/current-cleanup/post-gateq/`: 108 files.
- Moved to `docs/gates/**`: 0 files.
- `MERGE_THEN_ARCHIVE`: 0 files.
- `RETAIN_REVIEW`: 0 files.
- Target conflict check: all generated target paths were absent before `git mv`; no archive file was overwritten.
- Historical evidence deletion: none. Current copies were moved; existing `docs/gates/gate-q/**`, `docs/gates/gate-p/**`, and `docs/gates/gate-o/**` evidence was not moved.

结束条件：本轮到 108 个 current copy 移出、`docs/current` 剩余 17 个 tracked Markdown、current 入口改为 archive pointer 后结束；不继续 Round 4 式无限瘦身。

## 2. Boundary Confirmation

- GateQ: `FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateQ release tag: `nq-gateq-freeze`.
- GateQ archive: `docs/gates/gate-q/`.
- GateP: `FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateO and older Gates: historical evidence comes from `docs/gates/**` or `docs/archive/**`.
- GateR: `PLAN / NOT STARTED`（规划 / 未开始）。
- LIVE: `DISABLED`（关闭）。
- AI: `NOT STARTED`（未开始）。
- DH runtime: `NOT INTEGRATED`（未集成）。
- Integration-1: `NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe: `NOT IMPLEMENTED`（未实现）。

## 3. Keep Current

| File | Classification | Reason |
| --- | --- | --- |
| `docs/current/README.md` | `KEEP_CURRENT` | current 事实入口和 archive pointer。 |
| `docs/current/STATUS.md` | `KEEP_CURRENT` | 当前状态事实源。 |
| `docs/current/ROADMAP.md` | `KEEP_CURRENT` | GateR planning-only 路线入口。 |
| `docs/current/TESTING.md` | `KEEP_CURRENT` | 当前验证记录，保留 append-only 语义。 |
| `docs/current/WORKLOG.md` | `KEEP_CURRENT` | 当前工作记录，保留 append-only 语义。 |
| `docs/current/FACT_SOURCE_INDEX.md` | `KEEP_CURRENT` | 当前事实源优先级索引。 |
| `docs/current/API.md` | `KEEP_CURRENT` | 当前已实现 API 事实。 |
| `docs/current/DB_SCHEMA.md` | `KEEP_CURRENT` | 当前已落地 schema 事实。 |
| `docs/current/ARCHITECTURE.md` | `KEEP_CURRENT` | 当前架构与模块边界摘要。 |
| `docs/current/MODULES.md` | `KEEP_CURRENT` | 当前模块职责摘要。 |
| `docs/current/RUNBOOK.md` | `KEEP_CURRENT` | 当前本地运行手册。 |
| `docs/current/FRONTEND_DESIGN_SYSTEM.md` | `KEEP_CURRENT` | 当前前端设计系统入口。 |
| `docs/current/frontend/ref/nq-design-system/README.md` | `KEEP_CURRENT` | 当前设计系统参考实现入口。 |
| `docs/current/CODEX_PROJECT_INSTRUCTIONS.md` | `KEEP_CURRENT` | 当前 Codex 项目指令入口。 |
| `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md` | `KEEP_CURRENT` | 当前 NQ/DH Codex workflow 路由入口。 |
| `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md` | `KEEP_CURRENT` | 当前 router skill 规格入口。 |
| `docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md` | `KEEP_CURRENT` | 当前任务模板入口。 |

## 4. Move To Docs Archive

以下文件通过 `git mv` 从 `docs/current` 移到本目录下的分类子目录。移动理由相同：这些文件已经是已冻结、已关闭、已消费、已归档或非当前阶段的过程型证据；current 只保留摘要和指针。

### 4.1 GateQ Current Copies

Target: `docs/archive/current-cleanup/post-gateq/gateq-current-copies/`

- `GATEQ_FREEZE_CLOSEOUT.md`
- `GATEQ_FREEZE_READINESS_REVIEW.md`
- `GATEQ_PLAN.md`

GateQ 正式历史入口为 `docs/gates/gate-q/README.md`；本目录只保存原 current copy 的移动结果。

### 4.2 GateP Current Copies

Target: `docs/archive/current-cleanup/post-gateq/gatep-current-copies/`

- `GATEP_FREEZE_CLOSEOUT_REVIEW.md`
- `GATEP_FREEZE_READINESS_REVIEW.md`

GateP 正式历史入口为 `docs/gates/gate-p/README.md`。

### 4.3 Legacy Gates

Target: `docs/archive/current-cleanup/post-gateq/legacy-gates/gate-j/`

- `GATEJ_API_PLAN.md`
- `GATEJ_DB_PLAN.md`
- `GATEJ_TEST_PLAN.md`

Target: `docs/archive/current-cleanup/post-gateq/legacy-gates/gate-k/`

- `GATEK_ARCHITECTURE_BASELINE_REVIEW.md`
- `GATEK_PLAN.md`
- `NQ_GATEK_ARCHITECTURE_FREEZE.md`
- `NQ_GATEK_ARCHIVE_AND_HANDOVER.md`
- `NQ_GATEK_CI_SECURITY_CONTRACT.md`
- `NQ_GATEK_CI_SECURITY_FINAL_FREEZE_SPEC.md`
- `NQ_GATEK_PAPER_EXECUTION_INTELLIGENCE_PLAN.md`
- `NQ_GATEK_PAPER_TRADING_PAGE_SPLIT_PLAN.md`
- `NQ_GATEK_POST_FREEZE_HANDOFF_PLAN.md`

Target: `docs/archive/current-cleanup/post-gateq/legacy-gates/gate-l-current-copies/`

- `GATEL_PLAN.md`
- `GATEL_1_EXCHANGE_ADAPTER_CONTRACT_REVIEW.md`
- `GATEL_1_EXCHANGE_ADAPTER_CONTRACT_FREEZE_REVIEW.md`
- `GATEL_1B_NO_REAL_HARDENING_PLAN.md`
- `GATEL_1B_NO_REAL_HARDENING_PLAN_REVIEW.md`
- `GATEL_1B_NO_REAL_HARDENING_PLAN_FREEZE_REVIEW.md`
- `GATEL_1B_A_IMPL_FREEZE_REVIEW.md`
- `GATEL_1B_B_IMPL_FREEZE_REVIEW.md`
- `GATEL_1B_C_IMPL_FREEZE_REVIEW.md`
- `GATEL_1B_D_IMPL_FREEZE_REVIEW.md`
- `GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`
- `GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md`
- `GATEL_1C_CAPABILITY_MATRIX_CONTRACT_REVIEW.md`
- `GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md`
- `GATEL_1D_ERROR_MODEL_CONTRACT.md`
- `GATEL_1D_ERROR_MODEL_CONTRACT_REVIEW.md`
- `GATEL_1D_ERROR_MODEL_CONTRACT_FREEZE_REVIEW.md`
- `GATEL_1E_READINESS_CHECKLIST_REFINEMENT.md`
- `GATEL_1E_READINESS_CHECKLIST_REFINEMENT_REVIEW.md`

Target: `docs/archive/current-cleanup/post-gateq/legacy-gates/gate-n-current-cleanup/`

- `NQ_GATEN_POST_CURRENT_ARCHIVE_INVENTORY.md`
- `NQ_GATEN_ARCHIVE_PLAN_REVIEW.md`
- `NQ_GATEN_ARCHIVE_CLOSEOUT.md`

Target: `docs/archive/current-cleanup/post-gateq/legacy-gates/jkmn-and-next-phase/`

- `NQ_GATES_JKMN_FREEZE_CI_EVIDENCE_RECONCILIATION.md`
- `NQ_NEXT_PHASE_PLAN.md`

### 4.4 CI, Security And No-Outbound History

Target: `docs/archive/current-cleanup/post-gateq/ci-security-and-no-outbound/`

- `NQ_CI_BASELINE_PLAN.md`
- `NQ_CI_FRONTEND_E2E_BACKEND_PLAN.md`
- `NQ_CI_SECURITY_GUARD_PLAN.md`
- `NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md`
- `NQ_CI_SECURITY_BATCH_5B_ENV_PLAN_REVIEW.md`
- `NQ_CI_SECURITY_BATCH_5B_ENV_FIRST_RUN_REVIEW.md`
- `NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md`
- `NQ_CI_SECURITY_BATCH_5B_SMOKE_PREFLIGHT_PLAN.md`
- `NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_PLAN.md`
- `NQ_CI_SECURITY_BATCH_5B_SMOKE_FIRST_RUN_EVIDENCE.md`
- `NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md`
- `NQ_CI_SECURITY_FINAL_BASELINE_REVIEW.md`
- `NQ_CI_SECURITY_FINAL_FREEZE.md`
- `NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_PLAN.md`
- `NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_ADDENDUM.md`
- `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md`
- `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md`

### 4.5 Credential And DB Governance History

Target: `docs/archive/current-cleanup/post-gateq/credential-db-governance/`

- `DB_SCHEMA_GOVERNANCE_PLAN.md`
- `DB_SCHEMA_GOVERNANCE_REVIEW.md`
- `CREDENTIAL_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md`
- `CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md`
- `CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md`
- `CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`
- `CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md`
- `CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`
- `CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md`
- `CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`
- `CREDENTIAL_REVOCATION_GOVERNANCE_REVIEW.md`
- `CREDENTIAL_ROTATE_GOVERNANCE_REVIEW.md`

### 4.6 NQ-DH Integration History

Target: `docs/archive/current-cleanup/post-gateq/nq-dh-integration-history/`

- `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`
- `NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`
- `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`
- `NQ_DH_INTEGRATION0_SECURITY_POLICY.md`
- `NQ_DH_INTEGRATION1_CONTRACT_FIXTURES_PLAN.md`
- `NQ_DH_INTEGRATION1_DRYRUN_CONTRACT_PLAN.md`
- `NQ_DH_INTEGRATION1_DRYRUN_IMPLEMENTATION_READINESS_PLAN.md`
- `NQ_DH_INTEGRATION1_DRYRUN_MOCK_IMPLEMENTATION_WO.md`
- `NQ_DH_INTEGRATION1_DRYRUN_PLAN.md`
- `NQ_DH_INTEGRATION1_DRYRUN_PLAN_REBASEN.md`
- `NQ_DH_INTEGRATION1_JOINT_RUNTIME_DRYRUN_TEST_CLOSE_REVIEW.md`
- `NQ_DH_INTEGRATION1_JOINT_RUNTIME_DRYRUN_TEST_WO.md`
- `NQ_DH_INTEGRATION1_LIMITED_DRYRUN_RUNTIME_PLAN.md`
- `NQ_DH_INTEGRATION1_M0_CONTRACT_GAP_CLOSE_WO.md`
- `NQ_DH_INTEGRATION1_M1_DH_DRYRUN_CONTRACT_ENTRY_MOCK_WO.md`
- `NQ_DH_INTEGRATION1_M2_NQ_DRYRUN_STUB_RECORDER_WO.md`
- `NQ_DH_INTEGRATION1_M3_JOINT_MOCK_FIXTURES_AND_CONTRACT_TESTS_WO.md`
- `NQ_DH_INTEGRATION1_MOCK_RUNTIME_CLOSE_REVIEW.md`
- `NQ_DH_INTEGRATION1_MOCK_RUNTIME_PR_PREP.md`
- `NQ_DH_INTEGRATION1_NQ_CLIENT_CLOSE_REVIEW.md`
- `NQ_DH_INTEGRATION1_NQ_RUNTIME_CLIENT_WO.md`
- `WORK_ORDER.md`

### 4.7 Docs Governance History

Target: `docs/archive/current-cleanup/post-gateq/docs-governance-history/`

- `NQ_DOCS_AUTHORITY_INDEX.md`
- `NQ_DOCS_EVIDENCE_INDEX.md`
- `NQ_DOCS_GOVERNANCE_PLAN.md`
- `NQ_DOCS_G1_IMPLEMENTATION.md`
- `NQ_DOCS_MIGRATION_MAP.md`
- `NQ_DOCS_CURRENT_CLEANUP_R1_IMPLEMENTATION.md`
- `NQ_DOCS_CURRENT_CLEANUP_R2_REVIEW.md`
- `NQ_DOCS_CURRENT_CLEANUP_R3_FINAL_FREEZE.md`
- `NQ_DOCS_POST_GATEM_CURRENT_ARCHIVE_INVENTORY.md`
- `NQ_DOCS_POST_GATEM_GATEM_ARCHIVE_PLAN_REVIEW.md`
- `NQ_DOCS_POST_GATEM_GATEM_ARCHIVE_CLOSEOUT.md`
- `NQ_PROJECT_WORKFLOW_AUTHORITY.md`

### 4.8 Frontend And Backtest History

Target: `docs/archive/current-cleanup/post-gateq/frontend-history/`

- `NQ_DESIGN_TOKENS_V2.md`
- `NQ_FRONTEND_BUILD_MATRIX.md`
- `NQ_FRONTEND_CHART_FOUNDATION_B0_4.md`

Target: `docs/archive/current-cleanup/post-gateq/research-backtest-plans/`

- `BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md`

## 5. Archive Locations

- GateQ formal archive: `docs/gates/gate-q/`.
- GateP formal archive: `docs/gates/gate-p/`.
- GateO formal archive: `docs/gates/gate-o/`.
- Post-GateQ current cleanup archive: `docs/archive/current-cleanup/post-gateq/`.

## 6. Validation Record

Validation is recorded in `docs/current/TESTING.md` and `docs/current/WORKLOG.md`. Code tests were not run because this was a docs-only cleanup and did not modify backend, frontend, research, scripts, deploy, CI, API, migration, pages, or tests.
