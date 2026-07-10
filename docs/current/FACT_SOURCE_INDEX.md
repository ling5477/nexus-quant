# Current Fact Source Index

本文是 NexusQuant 当前事实源索引。GateU-1～GateU-5 已完成，GateU 当前为 `FREEZE READY / NOT TAGGED`（已具备冻结条件 / 尚未打 tag），最小冻结证据入口为 `docs/gates/gate-u/README.md`。`docs/current` 只保留当前状态、路线、验证、工作记录和仍需作为 current 使用的 API / DB / 架构事实。

## 1. 当前事实源优先级

当事实冲突时，按以下顺序解释当前状态：

1. 当前代码和实际验证结果。
2. [STATUS.md](STATUS.md)：当前项目状态。
3. [README.md](README.md)：current 入口和 archive pointer。
4. [ROADMAP.md](ROADMAP.md)：当前路线、GateU freeze release handoff 与 GateV `NOT STARTED` 边界。
5. [TESTING.md](TESTING.md)：当前验证记录和未运行说明。
6. [WORKLOG.md](WORKLOG.md)：当前工作记录。
7. [API.md](API.md)：已实现 HTTP API 当前事实。
8. [DB_SCHEMA.md](DB_SCHEMA.md)：已落地 Flyway schema 当前事实。
9. [../gates/gate-u/README.md](../gates/gate-u/README.md)：GateU-1～GateU-5 evidence matrix、API / frontend / test / CI / safety evidence 与 release prep；当前为 `FREEZE READY / NOT TAGGED`。
10. [NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md](NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md)：archive governance hardening 与 GateR/S/T residual move plan；GateT / GateS / GateR move batch 均已执行。
11. [../gates/gate-t/README.md](../gates/gate-t/README.md)：GateT 历史归档入口。
12. [../gates/gate-t/GATET_FREEZE_CLOSEOUT.md](../gates/gate-t/GATET_FREEZE_CLOSEOUT.md)：GateT freeze closeout authority。
13. [../gates/gate-t/GATET_BATCH_0_6_EVIDENCE_MATRIX.md](../gates/gate-t/GATET_BATCH_0_6_EVIDENCE_MATRIX.md)：GateT-0 到 GateT-6 evidence matrix。
14. [../gates/gate-t/GATET_API_EVIDENCE_SUMMARY.md](../gates/gate-t/GATET_API_EVIDENCE_SUMMARY.md)：GateT GET-only API evidence。
15. [../gates/gate-t/GATET_FRONTEND_EVIDENCE_SUMMARY.md](../gates/gate-t/GATET_FRONTEND_EVIDENCE_SUMMARY.md)：GateT frontend evidence。
16. [../gates/gate-t/GATET_PYTHON_ARTIFACT_BOUNDARY_SUMMARY.md](../gates/gate-t/GATET_PYTHON_ARTIFACT_BOUNDARY_SUMMARY.md)：GateT Python artifact boundary。
17. [../gates/gate-t/GATET_RUNTIME_SCHEDULING_READINESS_SUMMARY.md](../gates/gate-t/GATET_RUNTIME_SCHEDULING_READINESS_SUMMARY.md)：GateT runtime scheduling readiness boundary。
18. [../gates/gate-t/GATET_BOUNDARY_STATEMENT.md](../gates/gate-t/GATET_BOUNDARY_STATEMENT.md)：GateT safety boundary statement。
19. [../gates/gate-t/source/](../gates/gate-t/source/)：GateT process docs source durable copies；historical evidence（历史证据），不作为 current authority。
20. [../gates/gate-s/README.md](../gates/gate-s/README.md)：GateS 历史归档入口。
21. [../gates/gate-s/source/](../gates/gate-s/source/)：GateS process docs source durable copies；historical evidence（历史证据），不作为 current authority。
22. [../gates/gate-r/README.md](../gates/gate-r/README.md)：GateR 历史归档入口。
23. [../gates/gate-r/source/](../gates/gate-r/source/)：GateR process docs source durable copies；historical evidence（历史证据），不作为 current authority。
24. [../gates/gate-q/README.md](../gates/gate-q/README.md)：GateQ 历史归档入口。
25. [../gates/gate-p/README.md](../gates/gate-p/README.md)：GateP 历史归档入口。
26. [../gates/gate-o/README.md](../gates/gate-o/README.md)：GateO 历史归档入口。

`docs/gates/**` 与 `docs/archive/**` 是历史证据或归档引用，不覆盖 `docs/current` 的当前状态摘要。已完成 Gate 的过程型长文档不得再作为 current authority 扩写。

## 2. 当前阶段声明

- GateU：`FREEZE READY / NOT TAGGED`（已具备冻结条件 / 尚未打 tag）；GateU-1～GateU-5 为 `COMPLETED`（已完成）；baseline commit `9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`；current HEAD CI run `29108265105` 为 `completed / success`；`nq-gateu-freeze` 尚不存在。
- GateV：`NOT STARTED`（未开始）。
- GateT：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag `nq-gatet-freeze`。
- GateT-0..6：`COMPLETED`（已完成）。
- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag `nq-gates-freeze`。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag `nq-gater-freeze`。

## 3. GateU Freeze-ready Facts

- GateU archive entry：`docs/gates/gate-u/README.md`；该单一 README 同时承担 batch evidence matrix、API / frontend evidence index、test / CI / safety evidence、known limitations 与 release prep。
- GateU-1～GateU-5 commits：`c276d0ea`、`14f18cba`、`006b8ff9`、`0db719f2`、`9f278583`；五个对应 `NQ CI Baseline` run 均为 `completed / success`。
- Current endpoint：`GET /api/validation-operations/runtime-evidence/overview`；固定聚合 Shadow Validation Workflow、Shadow Runs、Consistency Evidence、Incident / Replay Review、Evaluation Artifact Preview 五来源。
- No-file Evaluation Artifact Preview 保留为第五来源并 fail-closed；只在所有来源 `AVAILABLE / FRESH` 时聚合为 `AVAILABLE / FRESH`。
- `diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`。
- GateU 不新增 migration、写 SQL、scheduler、runner、内部 HTTP、credential、private endpoint 或真实交易；GateV 未启动。

## 4. GateT Frozen Facts

- GateT archive entry：`docs/gates/gate-t/README.md`。
- GateT freeze closeout：`docs/gates/gate-t/GATET_FREEZE_CLOSEOUT.md`。
- GateT evidence matrix：`docs/gates/gate-t/GATET_BATCH_0_6_EVIDENCE_MATRIX.md`。
- GateT API facts：`GET /api/shadow-validation/workflow/overview`、`GET /api/paper-shadow/consistency/evidence/overview`、`GET /api/incidents/replay/review/overview`、`GET /api/strategy-validation/evaluation-artifacts/preview/overview` 均为 GET-only / read-only / no-side-effect / not trading authorization。
- GateT frontend facts：现有 `/strategies/validation` 页面内 Validation Operations Workbench 和各 panel 均为只读诊断与人工复核视图，不是交易操作台。
- GateT Python facts：Evaluation Artifact Preview 是 No-file baseline，不读取 artifact、不执行 Python、不导入 DB；Python ML readiness `NO`，Python live execution readiness `NO`。
- GateT runtime scheduling facts：GateT-6 是 readiness-review only，不启动 scheduler / runner / runtime，不创建任何 run/report/event/record。

## 5. 禁止误写清单

- 不得把 GateU `FREEZE READY / NOT TAGGED` 写成 `TAGGED`、release tag 已推送或 GateV 已启动。
- 不得把 GateU runtime evidence aggregate 写成 runtime execution、Shadow trading、LIVE readiness 或交易授权。
- 不得把 GateU freeze readiness 写成新增 read-model 的继续授权。
- 不得把 GateT GET-only endpoints 写成写接口、runner trigger、scheduler trigger 或交易 endpoint。
- 不得把 frontend Workbench / panels 写成执行按钮、自动处置、真实交易入口或 Shadow trading 已启用。
- 不得把 Strategy Validation 的 `APPROVED`（验证层通过）写成真实交易授权。
- 不得把 Incident / Replay severity 写成真实 incident runtime 或实盘就绪。
- 不得把 Python artifact preview 写成 Python ML readiness 或 Python live execution readiness。
- 不得把 Python artifact checksum valid 写成策略有效、真实收益、交易授权或 live execution ready。
- 不得把 No-file baseline 写成已读取 artifact、已执行 Python 或 Java production binding。
- 不得把 LIVE 写成 enabled。
- 不得把 AI 写成 started。
- 不得把 DH runtime 写成 integrated。
- 不得把 Integration-1 mock/test-support 写成 runtime started。
- 不得把 RealClient、real provider、private trading adapter 或 real permission probe 写成 implemented。

## 6. Allowed residuals

当前无 GateR / GateS / GateT allowed residual。GateT、GateS、GateR process docs 已分别在 `NQ-DOCS-GATET-CURRENT-RESIDUAL-MOVE-BATCH`、`NQ-DOCS-GATES-CURRENT-RESIDUAL-MOVE-BATCH`、`NQ-DOCS-GATER-CURRENT-RESIDUAL-MOVE-BATCH` 中移出 `docs/current`。计划入口：[NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md](NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md)。

## 7. Historical evidence moved from current

GateT process docs 已通过 `git mv` 移入 `docs/gates/gate-t/source/`，作为 historical evidence（历史证据）保留：

- `docs/gates/gate-t/source/GATET_PLAN.md`
- `docs/gates/gate-t/source/GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md`
- `docs/gates/gate-t/source/GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md`
- `docs/gates/gate-t/source/GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md`
- `docs/gates/gate-t/source/GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md`
- `docs/gates/gate-t/source/GATET_5_VALIDATION_OPERATIONS_WORKBENCH_WO.md`
- `docs/gates/gate-t/source/GATET_6_RUNTIME_SCHEDULING_READINESS_WO.md`
- `docs/gates/gate-t/source/GATET_FREEZE_READINESS_REVIEW.md`

这些 source copy 不覆盖 `docs/current/STATUS.md`、`README.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`、`API.md` 或 `DB_SCHEMA.md` 的当前事实。

GateS process docs 已通过 `git mv` 移入 `docs/gates/gate-s/source/`，作为 historical evidence（历史证据）保留：

- `docs/gates/gate-s/source/GATES_0_PLAN.md`
- `docs/gates/gate-s/source/GATES_1_READ_MODEL_WO.md`
- `docs/gates/gate-s/source/GATES_1_FRONTEND_OVERVIEW_WO.md`
- `docs/gates/gate-s/source/GATES_FREEZE_READINESS_REVIEW.md`

这些 source copy 不覆盖 `docs/current/STATUS.md`、`README.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`、`API.md` 或 `DB_SCHEMA.md` 的当前事实，也不表示 GateU started / implemented。

GateR process docs 已通过 `git mv` 移入 `docs/gates/gate-r/source/`，作为 historical evidence（历史证据）保留：

- `docs/gates/gate-r/source/GATER_PLAN.md`
- `docs/gates/gate-r/source/GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md`

这些 source copy 不覆盖 `docs/current/STATUS.md`、`README.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`、`API.md` 或 `DB_SCHEMA.md` 的当前事实，也不表示 GateU started / implemented。
