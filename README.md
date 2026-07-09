# NexusQuant

NexusQuant 是通用量化交易平台。当前事实入口以 `docs/current/` 为准；已完成 Gate 的冻结卷宗保存在 `docs/gates/`；历史 cleanup 证据保存在 `docs/archive/`。

## 当前状态

- GateT：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateT release tag：`nq-gatet-freeze`。
- GateT archive：`docs/gates/gate-t/README.md`。
- GateT closeout：`docs/gates/gate-t/GATET_FREEZE_CLOSEOUT.md`。
- GateT-0..6：`COMPLETED`（已完成），证据矩阵见 `docs/gates/gate-t/GATET_BATCH_0_6_EVIDENCE_MATRIX.md`。
- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag：`nq-gates-freeze`；archive：`docs/gates/gate-s/README.md`。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag：`nq-gater-freeze`；archive：`docs/gates/gate-r/README.md`。
- GateQ / GateP / GateO 及更早 Gate：历史证据入口为 `docs/gates/**` 或 `docs/archive/**`。
- 下一阶段：GateU `PLAN / NOT STARTED`（规划 / 未开始）。GateU 实现未启动。

## Current Docs

- `docs/current/README.md`：当前事实入口和 GateT archive pointer。
- `docs/current/STATUS.md`：当前状态摘要。
- `docs/current/ROADMAP.md`：当前路线与 GateU `PLAN / NOT STARTED` 边界。
- `docs/current/FACT_SOURCE_INDEX.md`：事实源优先级和 GateT archive 指针。
- `docs/current/TESTING.md`：验证记录；记录 GateT freeze closeout precondition CI 和本轮未复跑本地测试原因。
- `docs/current/WORKLOG.md`：工作记录；记录 GateT freeze closeout、archive、commit/tag 流程和边界。
- `docs/current/API.md`：当前 API 事实；GateT 新增能力均为 GET-only / read-only / no-side-effect / not trading authorization。
- `docs/current/DB_SCHEMA.md`：当前 DB schema 事实。
- `docs/current/ARCHITECTURE.md` / `docs/current/MODULES.md` / `docs/current/RUNBOOK.md`：当前架构、模块和运行手册。

GateT 过程型长文档不再作为 current authority 扩写；GateT-0 plan、GateT-1 到 GateT-6 evidence、freeze readiness review 和 closeout 证据以 `docs/gates/gate-t/` 为归档入口。

## GateT Archive

- `docs/gates/gate-t/README.md`：GateT archive 入口。
- `docs/gates/gate-t/GATET_FREEZE_CLOSEOUT.md`：GateT freeze closeout 归档。
- `docs/gates/gate-t/GATET_FREEZE_READINESS_REVIEW.md`：readiness review 归档索引。
- `docs/gates/gate-t/GATET_0_PLAN.md`：GateT-0 plan 归档索引。
- `docs/gates/gate-t/GATET_BATCH_0_6_EVIDENCE_MATRIX.md`：GateT-0 到 GateT-6 evidence matrix。
- `docs/gates/gate-t/GATET_API_EVIDENCE_SUMMARY.md`：GateT GET-only API 证据摘要。
- `docs/gates/gate-t/GATET_FRONTEND_EVIDENCE_SUMMARY.md`：Validation Operations Workbench 与各前端 panel 证据摘要。
- `docs/gates/gate-t/GATET_PYTHON_ARTIFACT_BOUNDARY_SUMMARY.md`：Evaluation Artifact Preview No-file baseline 与 Python 边界摘要。
- `docs/gates/gate-t/GATET_RUNTIME_SCHEDULING_READINESS_SUMMARY.md`：GateT-6 readiness-only 证据摘要。
- `docs/gates/gate-t/GATET_BOUNDARY_STATEMENT.md`：GateT 不代表什么的边界声明。

## Boundary

GateT freeze closeout 不代表真实交易授权，不开启 LIVE，不接 AI / DH runtime，不实现 RealClient、real provider、private trading adapter 或 real permission probe，也不启动 Shadow trading。GateT 新增 API 均为 GET-only / read-only / no-side-effect / not trading authorization；frontend Workbench 和 panels 均为只读诊断与人工复核视图；GateT-4 Evaluation Artifact Preview 是 No-file baseline，不读取 artifact、不执行 Python、不导入 DB；GateT-6 是 readiness-review only，未启动 scheduler / runner / runtime。

固定边界：

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python ML readiness：`NO`（否）。
- Python live execution readiness：`NO`（否）。
