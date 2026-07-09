# NexusQuant

NexusQuant 是通用量化交易平台。当前事实入口以 `docs/current/` 为准；已完成 Gate 的冻结卷宗保存在 `docs/gates/`；历史 cleanup 证据保存在 `docs/archive/`。

## 当前状态

- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateS release tag：`nq-gates-freeze`。
- GateS archive：`docs/gates/gate-s/README.md`。
- GateS closeout：`docs/gates/gate-s/GATES_FREEZE_CLOSEOUT.md`。
- GateS-0..6：`COMPLETED`（已完成），证据矩阵见 `docs/gates/gate-s/GATES_BATCH_0_6_EVIDENCE_MATRIX.md`。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag：`nq-gater-freeze`；归档入口：`docs/gates/gate-r/README.md`。
- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）；release tag：`nq-gateq-freeze`；归档入口：`docs/gates/gate-q/README.md`。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）；release tag：`nq-gatep-freeze`；归档入口：`docs/gates/gate-p/README.md`。
- GateO 及更早 Gate：以 `docs/gates/**` 或 `docs/archive/**` 作为历史证据。
- 当前阶段：GateT-3 Incident / Replay Review Workflow implementation 已进入 `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）。GateT 尚未 freeze、accepted 或 tagged。
- GateT-0 planning：`docs/current/GATET_PLAN.md`，状态为 `PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）。
- GateT-1 work order：`docs/current/GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md`，状态为 `PLAN READY / READY FOR IMPLEMENTATION`（规划已就绪 / 可实现）。
- GateT-1 implementation：`GET /api/shadow-validation/workflow/overview` 后端 read model 已实现；只派生 derived / deterministic operator items，不持久化、不新增 migration、不启动 runner / scheduler、不调用真实交易所、不读取 credential、不表示交易授权。
- GateT-1 frontend overview：现有 `/strategies/validation` 页面已最小只读消费 `GET /api/shadow-validation/workflow/overview`，展示 operator item counts、latest operator item、blockers / warnings / nextSteps、evidence anchors、traceId 和固定安全边界 badges；不新增 route 或交易动作。
- GateT-2 work order：`docs/current/GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md`，定义 Paper vs Shadow consistency evidence refinement 的 read-only plan。
- GateT-2 implementation：`GET /api/paper-shadow/consistency/evidence/overview` 后端 read model 已实现；只派生 deterministic consistency evidence item 和 evidence summary，不持久化、不新增 migration、不创建 consistency report、不启动 runner / scheduler、不调用真实交易所、不读取 credential、不表示交易授权。
- GateT-2 frontend overview：现有 `/strategies/validation` 页面已最小只读消费 `GET /api/paper-shadow/consistency/evidence/overview`，展示 evidence counts、latestEvidenceItem、severityBuckets、freshnessSummary、metricDeltaSummary、blockers / warnings / nextSteps、evidenceAnchors、traceId 和固定安全边界 badges；不新增 route、写侧 client 或交易入口。
- GateT-3 implementation：`GET /api/incidents/replay/review/overview` 后端 read model 已实现；只派生 deterministic review items，不持久化、不新增 migration、不创建 review / acknowledge / escalation / closeout / incident / alert / replay 记录、不启动 runner / scheduler、不调用真实交易所、不读取 credential、不表示交易授权或真实 incident 已关闭。

## Current Docs

- `docs/current/README.md`：当前事实入口和 GateS archive pointer。
- `docs/current/GATET_PLAN.md`：GateT Shadow Validation Operations planning 入口；只定义批次、边界、候选 workflow 和 freeze 条件，不代表实现已启动。
- `docs/current/GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md`：GateT-1 Shadow Validation Workflow read model / operator model work order；定义 endpoint / DTO / query / repository / testing plan。
- `docs/current/GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md`：GateT-2 Consistency Evidence Refinement work order；定义 candidate endpoint、DTO、query、repository、freshness / severity / metric semantics 和 no-side-effect testing plan。
- `docs/current/GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md`：GateT-3 Incident / Replay Review Workflow work order；定义 endpoint、review item model、reviewState / reviewDecision、query / repository、DB / migration decision 和 no-side-effect testing plan。
- `docs/current/STATUS.md`：当前状态摘要。
- `docs/current/ROADMAP.md`：当前路线与 GateT 边界。
- `docs/current/FACT_SOURCE_INDEX.md`：事实源优先级和 GateS 归档指针。
- `docs/current/TESTING.md`：验证记录；记录 GateT-3 backend Maven 验证、GateT-2 backend Maven 验证、GateT-2 frontend build / targeted smoke、GateT-1 backend / frontend build 与 targeted smoke 等当前验证事实。
- `docs/current/WORKLOG.md`：工作记录；记录 GateT-3 Incident / Replay Review Workflow implementation、GateT-2 consistency evidence overview 实现范围、边界和下一步。
- `docs/current/API.md`：当前 API 事实；GateS、GateT-1、GateT-2 与 GateT-3 新增能力均为 GET-only / read-only / no-side-effect。
- `docs/current/DB_SCHEMA.md`：当前 DB schema 事实。
- `docs/current/ARCHITECTURE.md` / `docs/current/MODULES.md` / `docs/current/RUNBOOK.md`：当前架构、模块和运行手册。
- `docs/current/GATES_FREEZE_READINESS_REVIEW.md`：GateS readiness review 的 current pointer；完整 freeze closeout 以 `docs/gates/gate-s/` 为准。

## GateS Archive

- `docs/gates/gate-s/README.md`：GateS archive 入口。
- `docs/gates/gate-s/GATES_FREEZE_CLOSEOUT.md`：GateS freeze closeout 归档。
- `docs/gates/gate-s/GATES_FREEZE_READINESS_REVIEW.md`：readiness review 归档索引。
- `docs/gates/gate-s/GATES_0_PLAN.md`：GateS-0 plan 归档索引。
- `docs/gates/gate-s/GATES_BATCH_0_6_EVIDENCE_MATRIX.md`：GateS-0 到 GateS-6 evidence matrix。
- `docs/gates/gate-s/GATES_API_EVIDENCE_SUMMARY.md`：四个 GET-only API 证据摘要。
- `docs/gates/gate-s/GATES_FRONTEND_EVIDENCE_SUMMARY.md`：frontend overview / drilldown / workbench / incident panel 证据摘要。
- `docs/gates/gate-s/GATES_PYTHON_RESEARCH_EVIDENCE_SUMMARY.md`：Python offline evaluation artifact 证据摘要。
- `docs/gates/gate-s/GATES_BOUNDARY_STATEMENT.md`：GateS 不代表什么的边界声明。

## Boundary

GateS freeze closeout 不代表真实交易授权，不开启 LIVE，不接 AI / DH runtime，不实现 RealClient、real provider、private trading adapter 或 real permission probe，也不启动 Shadow trading。GateS 新增 API 均为 GET-only / read-only / no-side-effect / not trading authorization；frontend panels 均为只读诊断展示；Python artifact 只属于 offline research diagnostic baseline。

固定边界：

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。
