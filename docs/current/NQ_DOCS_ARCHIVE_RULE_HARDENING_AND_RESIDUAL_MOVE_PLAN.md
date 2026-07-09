# NQ-DOCS-ARCHIVE-RULE-HARDENING-AND-GATET-CURRENT-RESIDUAL-PLAN

Status: `PLAN READY / MOVE NOT STARTED`（计划已就绪 / 迁移未开始）

本文是文档治理加硬与 GateR / GateS / GateT current residual move plan。本轮只定义规则和后续批次，不移动 `docs/current` 文件，不改 `docs/gates/**` 或 `docs/archive/**`，不启动 GateU。

## 1. 当前基线

- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），tag `nq-gater-freeze`。
- GateS：`FROZEN / ACCEPTED / TAGGED`，tag `nq-gates-freeze`。
- GateT：`FROZEN / ACCEPTED / TAGGED`，tag `nq-gatet-freeze`。
- GateU：`PLAN / NOT STARTED`（规划 / 未开始）；本轮不是 GateU planning。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。

## 2. 审查来源

来源：`NQ-DOCS-ARCHIVE-SKILL-AND-CURRENT-AUDIT`。

审查结论为 `CONDITIONAL PASS / FIX RECOMMENDED BEFORE GATEU`（有条件通过 / 建议 GateU 前修复）。主要发现：

- `nq-docs-writer` 归档规则够用但偏薄。
- `docs/current` 仍残留 GateR / GateS / GateT 过程文档。
- GateS / GateT archive 部分文件仍反向依赖 `docs/current` historical copy。
- GateS archive completeness score 约 78/100，GateT archive completeness score 约 84/100，结论为 `ARCHIVE THIN / FIX RECOMMENDED`（归档偏薄 / 建议修复）。

## 3. docs/current residual inventory

### Current authority

- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/FACT_SOURCE_INDEX.md`
- `docs/current/ARCHITECTURE.md`
- `docs/current/MODULES.md`
- `docs/current/RUNBOOK.md`
- `docs/current/FRONTEND_DESIGN_SYSTEM.md`
- `docs/current/CODEX_PROJECT_INSTRUCTIONS.md`
- `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md`
- `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md`
- `docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md`

### Active current plan

- 无 GateU plan 文件。本轮不得新增 GateU 文档。

### Allowed residual

- 本文 `docs/current/NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md`，用于后续 residual move batch 的治理计划。
- GateR / GateS / GateT 过程文档在 move batch 执行前暂列 allowed residual，但不得作为 current authority。

### Should move to docs/gates

- GateR / GateS / GateT 过程文档，详见后续分组。

### Should move to docs/archive

- 本轮暂不指定。若后续发现非 Gate 过程文档且不是 current authority，应在 plan review 中列为 `MOVE_TO_docs/archive/...`。

### Delete candidate

- 无。默认禁止删除历史证据。

## 4. GateR residual list

| File | 当前原因 | Archive coverage | 建议动作 | 风险 |
| --- | --- | --- | --- | --- |
| `docs/current/GATER_PLAN.md` | GateR-0 规划过程文档仍留在 current | `docs/gates/gate-r/README.md` 和 `GATER_EVIDENCE_MATRIX.md` 已覆盖摘要，但未保存完整 source copy | move to `docs/gates/gate-r/source/GATER_PLAN.md` | P2 |
| `docs/current/GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md` | GateR-1 migration plan review 仍留在 current | `GATER_DATABASE_EVIDENCE.md` 覆盖 DB evidence 摘要，但未保存完整 review source copy | move to `docs/gates/gate-r/source/GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md` | P2 |

## 5. GateS residual list

| File | 当前原因 | Archive coverage | 建议动作 | 风险 |
| --- | --- | --- | --- | --- |
| `docs/current/GATES_0_PLAN.md` | GateS-0 plan 过程文档仍留在 current | `docs/gates/gate-s/GATES_0_PLAN.md` 当前是 archive index，仍指向 current copy | move to `docs/gates/gate-s/source/GATES_0_PLAN.md`，并把 archive index 改为 source pointer | P1 |
| `docs/current/GATES_1_READ_MODEL_WO.md` | GateS-1 backend work order 仍留在 current | GateS evidence matrix / API summary 覆盖摘要，但不含完整 WO | move to `docs/gates/gate-s/source/GATES_1_READ_MODEL_WO.md` | P2 |
| `docs/current/GATES_1_FRONTEND_OVERVIEW_WO.md` | GateS-1 frontend work order 仍留在 current | GateS frontend summary 覆盖摘要，但不含完整 WO | move to `docs/gates/gate-s/source/GATES_1_FRONTEND_OVERVIEW_WO.md` | P2 |
| `docs/current/GATES_FREEZE_READINESS_REVIEW.md` | GateS readiness review 仍留在 current | `docs/gates/gate-s/GATES_FREEZE_READINESS_REVIEW.md` 是归档索引并反向依赖 current copy | move to `docs/gates/gate-s/source/GATES_FREEZE_READINESS_REVIEW.md`，并补 archive index | P1 |

## 6. GateT residual list

| File | 当前原因 | Archive coverage | 建议动作 | 风险 |
| --- | --- | --- | --- | --- |
| `docs/current/GATET_PLAN.md` | GateT-0 plan 过程文档仍留在 current | `docs/gates/gate-t/GATET_0_PLAN.md` 当前反向指向 current copy | move to `docs/gates/gate-t/source/GATET_PLAN.md`，并补 plan archive pointer | P1 |
| `docs/current/GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md` | GateT-1 work order 仍留在 current | GateT evidence matrix / API / frontend summary 覆盖摘要，但不含完整 WO | move to `docs/gates/gate-t/source/GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md` | P2 |
| `docs/current/GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md` | GateT-2 work order 仍留在 current | GateT evidence matrix / API / frontend summary 覆盖摘要，但不含完整 WO | move to `docs/gates/gate-t/source/GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md` | P2 |
| `docs/current/GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md` | GateT-3 work order 仍留在 current | GateT evidence matrix / API / frontend summary 覆盖摘要，但不含完整 WO | move to `docs/gates/gate-t/source/GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md` | P2 |
| `docs/current/GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md` | GateT-4 work order 仍留在 current | GateT evidence matrix / API / Python boundary / frontend summary 覆盖摘要，但不含完整 WO | move to `docs/gates/gate-t/source/GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md` | P2 |
| `docs/current/GATET_5_VALIDATION_OPERATIONS_WORKBENCH_WO.md` | GateT-5 work order 仍留在 current | GateT evidence matrix / frontend summary 覆盖摘要，但不含完整 WO | move to `docs/gates/gate-t/source/GATET_5_VALIDATION_OPERATIONS_WORKBENCH_WO.md` | P2 |
| `docs/current/GATET_6_RUNTIME_SCHEDULING_READINESS_WO.md` | GateT-6 work order 仍留在 current | GateT runtime scheduling summary 覆盖摘要，但不含完整 WO | move to `docs/gates/gate-t/source/GATET_6_RUNTIME_SCHEDULING_READINESS_WO.md` | P2 |
| `docs/current/GATET_FREEZE_READINESS_REVIEW.md` | GateT readiness review 仍留在 current | `docs/gates/gate-t/GATET_FREEZE_READINESS_REVIEW.md` 当前反向依赖 current copy | move to `docs/gates/gate-t/source/GATET_FREEZE_READINESS_REVIEW.md`，并补 archive index | P1 |

## 7. 每个 residual 的建议动作

- `keep`：仅 current authority、workflow authority 和本计划文档。
- `archive pointer only`：`README.md`、`STATUS.md`、`FACT_SOURCE_INDEX.md` 只保留 GateR/S/T archive pointer 和 allowed residual 状态，不保留长过程正文。
- `move to docs/gates`：GateR/S/T 过程文档进入各自 `docs/gates/<gate>/source/`。
- `move to docs/archive`：本轮未列出；后续 plan review 如发现非 Gate 历史文档再决定。
- `user decision`：无。
- `delete candidate`：无；历史证据默认不得删除。

## 8. move batch 顺序

- Batch A：GateT residual move plan review。
- Batch B：GateT residual move execution。
- Batch C：GateS residual move plan review。
- Batch D：GateS residual move execution。
- Batch E：GateR residual move plan review；如 GateR 残留较少，可并入 Batch C/D 的 plan review，但执行仍需明确文件清单。
- Batch F：archive quality recheck。

本轮不执行任何 Batch，不移动文件。

## 9. 后续应 move to docs/gates/<gate>/source/ 的文件

- `docs/gates/gate-r/source/GATER_PLAN.md`
- `docs/gates/gate-r/source/GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md`
- `docs/gates/gate-s/source/GATES_0_PLAN.md`
- `docs/gates/gate-s/source/GATES_1_READ_MODEL_WO.md`
- `docs/gates/gate-s/source/GATES_1_FRONTEND_OVERVIEW_WO.md`
- `docs/gates/gate-s/source/GATES_FREEZE_READINESS_REVIEW.md`
- `docs/gates/gate-t/source/GATET_PLAN.md`
- `docs/gates/gate-t/source/GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md`
- `docs/gates/gate-t/source/GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md`
- `docs/gates/gate-t/source/GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md`
- `docs/gates/gate-t/source/GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md`
- `docs/gates/gate-t/source/GATET_5_VALIDATION_OPERATIONS_WORKBENCH_WO.md`
- `docs/gates/gate-t/source/GATET_6_RUNTIME_SCHEDULING_READINESS_WO.md`
- `docs/gates/gate-t/source/GATET_FREEZE_READINESS_REVIEW.md`

## 10. 只需要 archive pointer 的文件

- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/FACT_SOURCE_INDEX.md`
- `README.md`

这些文件只保留当前状态、Gate archive pointer、allowed residual 状态和后续 move batch 指针，不承载 GateR/S/T 完整过程正文。

## 11. 允许暂留的文件

- 上述 GateR/S/T residual 在对应 move execution 完成前允许暂留，但必须被标记为 allowed residual。
- `docs/current/TESTING.md` 与 `docs/current/WORKLOG.md` 作为 append-only current record 暂留，不在本轮拆卷。

## 12. 不得删除的文件

- 所有 GateR/S/T process docs。
- `docs/current/TESTING.md`。
- `docs/current/WORKLOG.md`。
- `docs/gates/**`。
- `docs/archive/**`。

如果后续出现删除候选，必须另起 user decision 任务，且不能把删除作为默认动作。

## 13. GateS / GateT archive 补强建议

### GateS

- 补 `GATES_TESTING_EVIDENCE_SUMMARY.md` 或等价测试证据汇总。
- 补 `GATES_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md`，覆盖 GateS backend read models、DB/migration 影响判断、SELECT-only 证据和测试。
- 补 `GATES_KNOWN_LIMITATIONS_AND_RESIDUALS.md`，列明 no scheduler / no runtime / no write side / no real provider / current residual 状态。
- 将 `GATES_0_PLAN.md` 和 `GATES_FREEZE_READINESS_REVIEW.md` 从“见 current copy”升级为指向 archive `source/` 的 durable copy。

### GateT

- 补 `GATET_TESTING_EVIDENCE_SUMMARY.md` 或等价测试证据汇总。
- 补 `GATET_BACKEND_DB_MIGRATION_EVIDENCE_SUMMARY.md`，明确 GateT backend read models、无 migration / no DB write-side、SELECT-only 证据。
- 补 `GATET_KNOWN_LIMITATIONS_AND_RESIDUALS.md`，列明 No-file baseline、no scheduler、no runner、no review write-side、current residual 状态。
- 将 `GATET_0_PLAN.md` 和 `GATET_FREEZE_READINESS_REVIEW.md` 从“见 current copy”升级为指向 archive `source/` 的 durable copy。
- 在 closeout 或 addendum 中固化 tagged commit hash，而不是只写“以 `git show --stat nq-gatet-freeze` 为准”。

## 14. FACT_SOURCE_INDEX 更新计划

后续每个 move batch 必须同步更新 `docs/current/FACT_SOURCE_INDEX.md`：

1. 将已移动文件从 `Allowed residual` 移到 `Historical evidence`。
2. 保留 current authority 优先级：代码和实际验证结果、`STATUS.md`、`README.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`、`API.md`、`DB_SCHEMA.md`。
3. 明确 `docs/gates/**` 与 `docs/archive/**` 不覆盖 current 状态。
4. 明确 NQ-only / DH-only / NQ-DH Integration 隔离，不把 Integration mock/test-support 写成 runtime integrated。
5. 记录尚未执行的 move batch 和剩余 allowed residual。

## 15. 验收标准

- `nq-docs-writer` 已包含 Gate archive required files template、current cleanup hard gate、evidence matrix minimum fields、thin archive detection、residual taxonomy、FACT_SOURCE_INDEX update rule、cross-line isolation rule、freeze/tag verification rule、docs-only churn prevention rule 和 `No GateU until archive audit passed` rule。
- 本文已列出 GateR / GateS / GateT residual inventory、建议动作和 Batch A-F。
- 本轮未移动 `docs/current` 文件。
- 本轮未修改 `docs/gates/**` 或 `docs/archive/**`。
- 本轮未新增 GateU plan，未写 GateU started / implemented。
- Forbidden-area diff 为空。

## 16. 下一轮任务建议

推荐下一轮任务：

```text
NQ-DOCS-GATET-CURRENT-RESIDUAL-MOVE-PLAN-REVIEW
```

范围：只复核 GateT residual move target、source path、references update、FACT_SOURCE_INDEX update scope 和 rollback plan；不执行 move。通过后再进入 Batch B execution。

## 17. Boundary confirmation

本计划不授权 backend、frontend、research、scripts、deploy、CI、migration、API、Controller、DTO、Repository、SQL、前端页面、Python research、LIVE、AI、DH runtime、RealClient、real provider、private trading adapter、real permission probe 或真实交易行为。
