# Current Stage

`docs/current/` 是 NexusQuant 的当前事实入口，只保留当前控制文档、状态、路线、验证/工作记录、API、DB schema、运行手册、架构/模块摘要和必要的 workflow 入口。GateS 过程型证据已冻结到 `docs/gates/gate-s/`，current 只保留摘要和 archive pointer。

## 当前状态

- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateS release tag：`nq-gates-freeze`。
- GateS archive pointer：[../gates/gate-s/README.md](../gates/gate-s/README.md)。
- GateS closeout：[../gates/gate-s/GATES_FREEZE_CLOSEOUT.md](../gates/gate-s/GATES_FREEZE_CLOSEOUT.md)。
- GateS evidence matrix：[../gates/gate-s/GATES_BATCH_0_6_EVIDENCE_MATRIX.md](../gates/gate-s/GATES_BATCH_0_6_EVIDENCE_MATRIX.md)。
- GateS-0..6：`COMPLETED`（已完成）。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag：`nq-gater-freeze`；archive pointer：[../gates/gate-r/README.md](../gates/gate-r/README.md)。
- GateQ / GateP / GateO 及更早 Gate：只作为历史证据读取，入口为 `docs/gates/**` 或 `docs/archive/**`。
- 当前阶段：GateT-3 Incident / Replay Review Workflow implementation 已进入 `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；GateT 尚未 freeze、accepted 或 tagged。
- GateT-0 planning：[GATET_PLAN.md](GATET_PLAN.md)，状态为 `PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）。
- GateT-1 work order：[GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md](GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md)，状态为 `PLAN READY / READY FOR IMPLEMENTATION`（规划已就绪 / 可实现）。
- GateT-1 implementation：`GET /api/shadow-validation/workflow/overview` 后端 read model 已实现；只派生 derived / deterministic operator items，不持久化、不新增 migration、不启动 runner / scheduler、不调用真实交易所、不读取 credential、不表示交易授权。
- GateT-1 frontend overview：现有 `/strategies/validation` 页面已最小只读消费 `GET /api/shadow-validation/workflow/overview`，只展示 derived operator workflow 诊断信息和固定安全边界，不新增 route、review / acknowledge 写侧或交易动作。
- GateT-2 work order：[GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md](GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md)，定义 consistency evidence refinement 的事实源、DTO、query、repository 和测试计划。
- GateT-2 implementation：`GET /api/paper-shadow/consistency/evidence/overview` 后端 read model 已实现；只派生 deterministic consistency evidence item、severity / freshness summary、metricDelta 摘要、blockers / warnings / nextSteps 和 evidence anchors，不新增 migration、不创建 report、不启动 runner / scheduler、不调用真实交易所、不读取 credential、不表示交易授权。
- GateT-2 frontend overview：现有 `/strategies/validation` 页面已最小只读消费 `GET /api/paper-shadow/consistency/evidence/overview`；只展示 consistency evidence 诊断证据和固定安全边界，不新增 route、review / acknowledge 写侧或交易动作。
- GateT-3 work order：[GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md](GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md)，定义 Incident / Replay Review Workflow 的 read-only derived model、candidate endpoint、review item model、reviewState / reviewDecision 和 no-side-effect 测试计划。
- GateT-3 implementation：`GET /api/incidents/replay/review/overview` 后端 read model 已实现；只派生 deterministic review items，不持久化、不新增 migration、不创建 review / acknowledge / escalation / closeout / incident / alert / replay 记录、不启动 runner / scheduler、不调用真实交易所、不读取 credential、不表示交易授权或真实 incident 已关闭。

## Current Authority

| 用途 | 当前文件 |
| --- | --- |
| GateT planning 入口 | [GATET_PLAN.md](GATET_PLAN.md) |
| GateT-1 work order / frontend overview 边界 | [GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md](GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md), [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md) |
| GateT-2 consistency evidence refinement work order | [GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md](GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md), [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md) |
| GateT-3 Incident / Replay Review Workflow implementation | [GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md](GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md), [API.md](API.md), [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md) |
| 当前状态 | [STATUS.md](STATUS.md) |
| 当前路线 | [ROADMAP.md](ROADMAP.md) |
| 当前事实源优先级 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) |
| 当前验证记录 | [TESTING.md](TESTING.md) |
| 当前工作记录 | [WORKLOG.md](WORKLOG.md) |
| 当前 API 事实 | [API.md](API.md) |
| 当前 DB schema 事实 | [DB_SCHEMA.md](DB_SCHEMA.md) |
| 当前架构/模块摘要 | [ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md) |
| 当前运行手册 | [RUNBOOK.md](RUNBOOK.md) |
| 当前 Codex workflow | [CODEX_PROJECT_INSTRUCTIONS.md](CODEX_PROJECT_INSTRUCTIONS.md), [NQ_DH_CODEX_PLUGIN_WORKFLOW.md](NQ_DH_CODEX_PLUGIN_WORKFLOW.md), [NQ_DH_WORKFLOW_ROUTER_SKILL.md](NQ_DH_WORKFLOW_ROUTER_SKILL.md), [NQ_DH_CODEX_TASK_TEMPLATES.md](NQ_DH_CODEX_TASK_TEMPLATES.md) |
| GateS archive | [../gates/gate-s/README.md](../gates/gate-s/README.md) |

## Historical Evidence

- GateS archive：[../gates/gate-s/README.md](../gates/gate-s/README.md)。
- GateR archive：[../gates/gate-r/README.md](../gates/gate-r/README.md)。
- GateQ archive：[../gates/gate-q/README.md](../gates/gate-q/README.md)。
- GateP archive：[../gates/gate-p/README.md](../gates/gate-p/README.md)。
- GateO archive：[../gates/gate-o/README.md](../gates/gate-o/README.md)。
- GateM / GateN archives：[../gates/gate-m/README.md](../gates/gate-m/README.md), [../gates/gate-n/README.md](../gates/gate-n/README.md)。
- Post-GateQ current cleanup archive：[../archive/current-cleanup/post-gateq/README.md](../archive/current-cleanup/post-gateq/README.md)。

## Current Is Not

- 不是 GateT `FROZEN`（已冻结）、`ACCEPTED`（已接受）或 `TAGGED`（已打 tag）。
- 不是 LIVE enable。
- 不是真实交易授权。
- 不是 Shadow trading enabled。
- 不是 AI runtime started。
- 不是 DH runtime integrated。
- 不是 Integration-1 runtime started。
- 不是 RealClient、real provider、private trading adapter 或 real permission probe implemented。
- 不是 Python ML readiness 或 Python live execution readiness。

## Current Cleanup Rule

GateS 已完成 freeze closeout，GateS-0 plan、GateS-1 到 GateS-6 过程证据、readiness review 和 freeze closeout 证据均以 `docs/gates/gate-s/` 为历史归档入口。`docs/current` 后续只维护当前状态、路线、验证、工作记录和 still-current API / DB / architecture facts；不得把 GateS archive closeout 写成 GateT implementation、LIVE、AI/DH runtime、real provider、private trading 或真实交易路径。
