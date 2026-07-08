# Current Fact Source Index

本文是 NexusQuant 当前事实源索引。GateS 已完成 freeze closeout，过程型证据以 `docs/gates/gate-s/` 为归档入口；`docs/current` 只保留当前状态、路线、验证、工作记录和仍需作为 current 使用的 API / DB / 架构事实。

## 1. 当前事实源优先级

当事实冲突时，按以下顺序解释当前状态：

1. 当前代码和实际验证结果。
2. [GATET_PLAN.md](GATET_PLAN.md)：GateT Shadow Validation Operations planning 当前入口。
3. [GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md](GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md)：GateT-1 Shadow Validation Workflow read model / operator model work order。
4. [STATUS.md](STATUS.md)：当前项目状态。
5. [README.md](README.md)：current 入口和 archive pointer。
6. [ROADMAP.md](ROADMAP.md)：当前路线与 GateT 边界。
7. [TESTING.md](TESTING.md)：当前验证记录和未运行说明。
8. [WORKLOG.md](WORKLOG.md)：当前工作记录。
9. [API.md](API.md)：已实现 HTTP API 当前事实。
10. [DB_SCHEMA.md](DB_SCHEMA.md)：已落地 Flyway schema 当前事实。
11. [../gates/gate-s/README.md](../gates/gate-s/README.md)：GateS 历史归档入口。
12. [../gates/gate-s/GATES_FREEZE_CLOSEOUT.md](../gates/gate-s/GATES_FREEZE_CLOSEOUT.md)：GateS freeze closeout authority。
13. [../gates/gate-s/GATES_BATCH_0_6_EVIDENCE_MATRIX.md](../gates/gate-s/GATES_BATCH_0_6_EVIDENCE_MATRIX.md)：GateS-0 到 GateS-6 evidence matrix。
14. [../gates/gate-r/README.md](../gates/gate-r/README.md)：GateR 历史归档入口。
15. [../gates/gate-q/README.md](../gates/gate-q/README.md)：GateQ 历史归档入口。
16. [../gates/gate-p/README.md](../gates/gate-p/README.md)：GateP 历史归档入口。
17. [../gates/gate-o/README.md](../gates/gate-o/README.md)：GateO 历史归档入口。
18. [../archive/current-cleanup/post-gateq/README.md](../archive/current-cleanup/post-gateq/README.md)：post-GateQ current cleanup 审计和移动索引。

`docs/gates/**` 与 `docs/archive/**` 是历史证据或归档引用，不覆盖 `docs/current` 的当前状态摘要。已完成 Gate 的过程型长文档不得再作为 current authority 扩写。

## 2. 当前阶段声明

- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateS release tag：`nq-gates-freeze`。
- GateS archive：`docs/gates/gate-s/README.md`。
- GateS-0..6：`COMPLETED`（已完成）。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag `nq-gater-freeze`。
- GateT：`PLAN / NOT STARTED`（规划 / 未开始）。
- GateT-0 planning：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核），入口为 [GATET_PLAN.md](GATET_PLAN.md)。
- GateT-1 work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核），入口为 [GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md](GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md)。

## 3. GateT Planning Facts

- GateT 主线是 Shadow Validation Operations / 策略验证运营闭环规划，基于 GateS 只读诊断 evidence，不启动真实交易。
- GateT 第一批建议先做 backend read model / operator model plan，再做 frontend workbench。
- GateT-1 已选择候选 endpoint `GET /api/shadow-validation/workflow/overview`，但尚未实现；operator item 默认 derived / not persisted。
- GateT 默认不新增 DB migration；review / acknowledge 若需要持久化，必须另起 DB schema review。
- GateT 不接 Python production binding，只允许 Python artifact read-only binding preview。
- GateT 不接 AI runtime，不接 DH runtime，不启动 Integration-1 runtime。

## 4. GateS Current Code Facts

- GateS API facts：`GET /api/shadow-runs/overview`、`GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}`、`GET /api/strategy-validation/overview`、`GET /api/incidents/replay/overview` 均为 GET-only / read-only / no-side-effect / not trading authorization。
- GateS frontend facts：Shadow Run overview summary、Paper vs Shadow drilldown panel、Strategy Validation overview panel、Strategy Validation / Shadow Workbench 和 Incident / Replay overview panel 均为只读诊断展示。
- GateS Python facts：`research/py/src/nq_research/evaluation/artifacts.py` 和 `parameters.py` 只提供 offline evaluation artifact baseline，不表示 Java production binding、API、migration、runner、Python ML readiness、Python live execution readiness 或真实交易授权。

## 5. 禁止误写清单

- 不得把 GateS freeze closeout 写成 GateT implementation。
- 不得把 GateT plan 写成 runtime 已启动。
- 不得把 GateS GET-only endpoints 写成写接口、runner trigger、scheduler trigger 或交易 endpoint。
- 不得把 frontend panels 写成执行按钮、自动处置、真实交易入口或 Shadow trading enabled。
- 不得把 Strategy Validation 的 `APPROVED`（验证层通过）写成真实交易授权。
- 不得把 Incident / Replay severity 写成真实 incident runtime 或实盘就绪。
- 不得把 Python offline artifact 写成 Python ML ready 或 Python live execution ready。
- 不得把 LIVE 写成 enabled。
- 不得把 AI 写成 started。
- 不得把 DH runtime 写成 integrated。
- 不得把 Integration-1 mock/test-support 写成 runtime started。
- 不得把 RealClient、real provider、private trading adapter 或 real permission probe 写成 implemented。
