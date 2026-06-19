# NQ Documentation Governance — G4 CI Evidence Routing

任务：`NQ-DOCS-GOVERNANCE-G4-CI-EVIDENCE-ROUTING`

日期：2026-06-19

状态：**G4 CI evidence routing = IMPLEMENTED / READY FOR REVIEW**

任务类型：DOCUMENTATION_GOVERNANCE_IMPLEMENTATION + CI_EVIDENCE_CONSOLIDATION + COMPATIBILITY_PATH_PRESERVATION

> 本轮只根据 G1 冻结对象 `docs/current/NQ_DOCS_MIGRATION_MAP.md` 中明确标记为 G4 的 CI 条目执行 redirect-first 归位。未从文件名猜测候选，未修改 G1 五份冻结对象，未修改 `docs/gates/**`、`docs/archive/**`、`.agents/**`、`templates/**`、GateJ 17 stub、RUNBOOK、9 份 DIVERGED、workflow、代码、测试、migration、依赖。

---

## 1. 结论

**`NQ-DOCS-GOVERNANCE-G4-CI-EVIDENCE-ROUTING：PASS / READY FOR REVIEW`**

- Migration Map 精确提取 G4 候选：22。
- `REDIRECT_STUB_CREATED`：20。
- `BLOCKED_PER_FILE`：2（`CURRENT_CI_AUTHORITY` pointer，按拒绝条件不转换）。
- Fragment 入链：0 / 22。
- Target conflict：0。
- P0 = 0；P1 = 0；P2 = 0；P3 = 1（line-ending 工作树提示，非内容错误）。

---

## 2. Migration Map 提取规则

只接受同时满足以下条件的 Migration Map 条目：

```text
migration_batch = G4
recommended_action = FUTURE_MOVE_CANDIDATE
target_location 位于 docs/evidence/ci/** 或 docs/baselines/**
```

来源段落：`NQ_DOCS_MIGRATION_MAP.md` §1D `CI 过程 / 基线 / 冻结证据（22 份 NQ_CI_*）→ 未来归位`。该冻结对象只读，未修改。

---

## 3. 候选与预检结果

| # | source | target / pointer | current authority | source blob | target blob | 普通入链 | fragment | 状态 |
| --- | --- | --- | --- | --- | --- | ---: | ---: | --- |
| 1 | `docs/current/NQ_CI_BASELINE_PLAN.md` | `docs/baselines/CI_BASELINE_INDEX.md` pointer | true | `1f72a52d77b7b6c38e47676a5e906bbe6b087faf` | N/A | 49 | 0 | BLOCKED_PER_FILE / CURRENT_AUTHORITY |
| 2 | `docs/current/NQ_CI_SECURITY_GUARD_PLAN.md` | `docs/baselines/CI_BASELINE_INDEX.md` pointer | true | `50679045fdd65587980a16c5770f8414a140836d` | N/A | 23 | 0 | BLOCKED_PER_FILE / CURRENT_AUTHORITY |
| 3 | `docs/current/NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` | `docs/evidence/ci/NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` | false | `0d002077076991a2d977ca19a2a83b36738c72d8` | `0d002077076991a2d977ca19a2a83b36738c72d8` | 7 | 0 | REDIRECT_STUB_CREATED |
| 4 | `docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md` | `docs/evidence/ci/NQ_CI_POSTGRES_FLYWAY_PLAN.md` | false | `34575897cd471f1f968834b0ac1f343f58671c16` | `34575897cd471f1f968834b0ac1f343f58671c16` | 34 | 0 | REDIRECT_STUB_CREATED |
| 5 | `docs/current/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md` | `docs/evidence/ci/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md` | false | `b11431ec93346ef47e8ad3ced5154aa2591edadb` | `b11431ec93346ef47e8ad3ced5154aa2591edadb` | 6 | 0 | REDIRECT_STUB_CREATED |
| 6 | `docs/current/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md` | `docs/evidence/ci/NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md` | false | `a703db8e719c529cf9a28d7edbfbe13f29193c8c` | `a703db8e719c529cf9a28d7edbfbe13f29193c8c` | 15 | 0 | REDIRECT_STUB_CREATED |
| 7 | `docs/current/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md` | `docs/evidence/ci/NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md` | false | `48901f69c3d04c39cb17010fb4ebf233bff6586c` | `48901f69c3d04c39cb17010fb4ebf233bff6586c` | 8 | 0 | REDIRECT_STUB_CREATED |
| 8 | `docs/current/NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md` | `docs/evidence/ci/NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md` | false | `ad7d818c67b3263b052efe1436f37b6c318c0b0e` | `ad7d818c67b3263b052efe1436f37b6c318c0b0e` | 5 | 0 | REDIRECT_STUB_CREATED |
| 9 | `docs/current/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md` | `docs/evidence/ci/NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md` | false | `264c6f719c2dd97fe74bd397807d3c8f1a551819` | `264c6f719c2dd97fe74bd397807d3c8f1a551819` | 11 | 0 | REDIRECT_STUB_CREATED |
| 10 | `docs/current/NQ_CI_LOG_REDACTION_PROOF_PLAN.md` | `docs/evidence/ci/NQ_CI_LOG_REDACTION_PROOF_PLAN.md` | false | `668e3ccf7056ca263dd3ab33ae7f1158bf4856b4` | `668e3ccf7056ca263dd3ab33ae7f1158bf4856b4` | 14 | 0 | REDIRECT_STUB_CREATED |
| 11 | `docs/current/NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md` | `docs/evidence/ci/NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md` | false | `afbbc1447b2ae4747fc2f7b542c1fabfc7cce94c` | `afbbc1447b2ae4747fc2f7b542c1fabfc7cce94c` | 7 | 0 | REDIRECT_STUB_CREATED |
| 12 | `docs/current/NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md` | `docs/evidence/ci/NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md` | false | `3966dfbf38918ff736dba385e348bdb71d2496d5` | `3966dfbf38918ff736dba385e348bdb71d2496d5` | 8 | 0 | REDIRECT_STUB_CREATED |
| 13 | `docs/current/NQ_CI_DEPENDENCY_AUDIT_PLAN.md` | `docs/evidence/ci/NQ_CI_DEPENDENCY_AUDIT_PLAN.md` | false | `3f236007c339da8c620767ee9a028534493e60a4` | `3f236007c339da8c620767ee9a028534493e60a4` | 14 | 0 | REDIRECT_STUB_CREATED |
| 14 | `docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md` | `docs/evidence/ci/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md` | false | `d16ef096d937a5f85e0ad862cbb7ec11c9b729d6` | `d16ef096d937a5f85e0ad862cbb7ec11c9b729d6` | 5 | 0 | REDIRECT_STUB_CREATED |
| 15 | `docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md` | `docs/evidence/ci/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md` | false | `457630fcae0b51d7a83ff1a471f7e5b854dc7c5b` | `457630fcae0b51d7a83ff1a471f7e5b854dc7c5b` | 3 | 0 | REDIRECT_STUB_CREATED |
| 16 | `docs/current/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md` | `docs/evidence/ci/NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md` | false | `6c1fb2d9da69595743b551191f2a24285c83fa71` | `6c1fb2d9da69595743b551191f2a24285c83fa71` | 2 | 0 | REDIRECT_STUB_CREATED |
| 17 | `docs/current/NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md` | `docs/evidence/ci/NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md` | false | `b5d3bfa6d58d7b751d24a771004daa3622b00311` | `b5d3bfa6d58d7b751d24a771004daa3622b00311` | 7 | 0 | REDIRECT_STUB_CREATED |
| 18 | `docs/current/NQ_CI_FRONTEND_E2E_PLAN.md` | `docs/evidence/ci/NQ_CI_FRONTEND_E2E_PLAN.md` | false | `81dd8b8336b48469508a1981d15773407974c91d` | `81dd8b8336b48469508a1981d15773407974c91d` | 1 | 0 | REDIRECT_STUB_CREATED |
| 19 | `docs/current/NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md` | `docs/evidence/ci/NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md` | false | `8c57587da63e115092f2197e6d5d35e69ad9355b` | `8c57587da63e115092f2197e6d5d35e69ad9355b` | 2 | 0 | REDIRECT_STUB_CREATED |
| 20 | `docs/current/NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md` | `docs/evidence/ci/NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md` | false | `82c08e3ef59efbc9eb19af78f6fcccb2d814d581` | `82c08e3ef59efbc9eb19af78f6fcccb2d814d581` | 1 | 0 | REDIRECT_STUB_CREATED |
| 21 | `docs/current/NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md` | `docs/evidence/ci/NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md` | false | `5d34d1c40a8affc9b1fa9da51edb404403b24de3` | `5d34d1c40a8affc9b1fa9da51edb404403b24de3` | 0 | 0 | REDIRECT_STUB_CREATED |
| 22 | `docs/current/NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md` | `docs/evidence/ci/NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md` | false | `66733385d8c9a4b65b2298457ee3c11984562693` | `66733385d8c9a4b65b2298457ee3c11984562693` | 1 | 0 | REDIRECT_STUB_CREATED |

---

## 4. Compatibility Strategy

- 20 个旧 `docs/current/NQ_CI_*` path 保留为 12 行 compatibility stub。
- 每个 stub 保留原 H1，使用相对链接 `../evidence/ci/<filename>`。
- 每个 canonical target 保留完整原文，target blob 与 pre-move source blob 完全一致。
- `NQ_CI_BASELINE_PLAN.md` 与 `NQ_CI_SECURITY_GUARD_PLAN.md` 保留在 `docs/current/`，由 `docs/baselines/CI_BASELINE_INDEX.md` 作为 current authority pointer 导航，不转换为 stub。

---

## 5. 新增索引

- `docs/evidence/ci/README.md`：canonical CI historical evidence 入口；不取代 `STATUS.md` current-status 权威。
- `docs/baselines/CI_BASELINE_INDEX.md`：CI baseline / security guard / Batch 1~5A evidence 导航索引，只链接、不复制正文，不把 backlog/blocked 项写成 completed。

---

## 6. 冻结对象保护

- G1 五份冻结对象未修改：`NQ_DOCS_GOVERNANCE_PLAN.md`、`NQ_DOCS_AUTHORITY_INDEX.md`、`NQ_DOCS_EVIDENCE_INDEX.md`、`NQ_DOCS_MIGRATION_MAP.md`、`NQ_DOCS_G1_IMPLEMENTATION.md`。
- G2 current-control drift repair 未恢复或削弱。
- G3 GateJ redirect-first consolidation 未修改；17 个 GateJ stub、RUNBOOK、9 份 DIVERGED 未修改。
- `docs/gates/**`、`docs/archive/**`、`.agents/**`、`templates/**` 未修改。
- G5～G6 未开始；Batch 5B-ENV / 5B-SMOKE / 4F-B~4F-F 未启动；LIVE / AI / DH runtime / RealClient / real provider 未开启、未接入、未实现。

---

## 7. Rollback

- 对 20 个 migrated 文件：删除对应 `docs/evidence/ci/<filename>`，再用 target 文件内容恢复 `docs/current/<filename>` 原文，或 revert 本轮 commit。
- 删除 `docs/evidence/ci/README.md`、`docs/baselines/CI_BASELINE_INDEX.md`、本文，并 revert `README.md` / `STATUS.md` / `TESTING.md` / `WORKLOG.md` 的本轮追加段。
- G1/G2/G3 冻结对象与代码不受影响。

---

## 8. Findings

### P0 / P1 / P2

- 无。

### P3

- **P3-1（line-ending，信息性）**：Windows 工作树对本轮新 stub / current-control 记录提示 `LF will be replaced by CRLF the next time Git touches it`。`git diff --check` clean，属于仓库级换行归一化提示，不影响 G4 结论。

---

## 9. 状态结论

```text
G1 authority/evidence index = FROZEN / ACCEPTED
G2 current-control drift repair = FROZEN / ACCEPTED
G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED
G4 CI evidence routing = IMPLEMENTED / READY FOR REVIEW
G5～G6 = NOT STARTED
NQ GateK CI mainline = COMPLETED / ACCEPTED
Batch 5A = FROZEN / ACCEPTED
Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED
Batch 5B-SMOKE = BLOCKED BY 5B-ENV
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
```
