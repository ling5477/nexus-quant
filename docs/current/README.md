# Current Stage

`docs/current/` 是 NexusQuant 的当前事实入口，只保留当前控制文档、当前事实源、当前状态、当前路线、验证/工作记录、API、DB schema、运行手册、架构/模块摘要和仍需作为 current 使用的 Codex workflow 入口。已冻结 Gate 的过程型证据不再放在 current；历史证据以 `docs/gates/**`、`docs/archive/**` 为准。

## 当前状态

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）；release tag 为 `nq-gateq-freeze`；历史证据入口为 [../gates/gate-q/README.md](../gates/gate-q/README.md)。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）；release tag 为 `nq-gatep-freeze`；历史证据入口为 [../gates/gate-p/README.md](../gates/gate-p/README.md)。
- GateO 及更早 Gate：只作为历史证据读取，入口为 `docs/gates/**` 或 `docs/archive/**`。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；historical planning 和批次证据入口为 [GATER_PLAN.md](GATER_PLAN.md)；release tag：`nq-gater-freeze`；archive pointer： [../gates/gate-r/README.md](../gates/gate-r/README.md)；GateR-8 已完成并 push，最新 GitHub Actions run `28852212136`（`NQ CI Baseline`）为 `success`（成功）。
- GateR-1：`NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW：PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。当前 review 入口为 [GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md](GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md)。
- GateR-2：Shadow Run local fact model / `V32` / repository 已完成并接受；schema 入口为 [DB_SCHEMA.md](DB_SCHEMA.md)，验证入口为 [TESTING.md](TESTING.md)。
- GateR-3：Shadow Run runner skeleton 已完成；它只在调用方显式调用时写本地诊断 facts，不是 scheduler 或后台 runner。
- GateR-4：decision trace / risk snapshot / order intent preview 已完成。
- GateR-5：shadow consistency report service 已完成。
- GateR-6：Shadow Run read-only API 已完成；只提供 GET 查询，没有写接口。
- GateR-7：Shadow Run detail / replay view 已完成；只读展示，没有执行按钮。
- GateR-8：Shadow Run list / entrypoint 已完成；支持列表进入 detail / replay。
- GateS：下一阶段唯一推荐主线，目标为策略验证运营化与 Shadow 诊断闭环阶段。
- GateS-0：`PLAN / NOT IMPLEMENTED`（规划 / 未实现）；current plan review 入口为 [GATES_0_PLAN.md](GATES_0_PLAN.md)。
- GateS-1：`NEXT / NOT IMPLEMENTED`（下一实施候选 / 未实现）；只允许后续独立任务审查 backend read model / frontend page contract，不代表已实现。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。

## Current Authority

| 用途 | 当前文件 |
| --- | --- |
| 当前事实入口 | [README.md](README.md) |
| 当前状态 | [STATUS.md](STATUS.md) |
| 当前路线 | [ROADMAP.md](ROADMAP.md) |
| GateR historical planning / readiness evidence | [GATER_PLAN.md](GATER_PLAN.md) |
| GateR-1 historical data model / migration plan review | [GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md](GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md) |
| GateR-2..8 Shadow Run closed-loop evidence | [STATUS.md](STATUS.md), [GATER_PLAN.md](GATER_PLAN.md), [API.md](API.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md) |
| GateS-0 planning / fact-source reconciliation | [GATES_0_PLAN.md](GATES_0_PLAN.md) |
| 当前事实源优先级 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) |
| 当前验证记录 | [TESTING.md](TESTING.md) |
| 当前工作记录 | [WORKLOG.md](WORKLOG.md) |
| 当前 API 事实 | [API.md](API.md) |
| 当前 DB schema 事实 | [DB_SCHEMA.md](DB_SCHEMA.md) |
| 当前架构/模块摘要 | [ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md) |
| 当前运行手册 | [RUNBOOK.md](RUNBOOK.md) |
| 当前前端设计系统 | [FRONTEND_DESIGN_SYSTEM.md](FRONTEND_DESIGN_SYSTEM.md) / [frontend/ref/nq-design-system/README.md](frontend/ref/nq-design-system/README.md) |
| 当前 Codex workflow | [CODEX_PROJECT_INSTRUCTIONS.md](CODEX_PROJECT_INSTRUCTIONS.md), [NQ_DH_CODEX_PLUGIN_WORKFLOW.md](NQ_DH_CODEX_PLUGIN_WORKFLOW.md), [NQ_DH_WORKFLOW_ROUTER_SKILL.md](NQ_DH_WORKFLOW_ROUTER_SKILL.md), [NQ_DH_CODEX_TASK_TEMPLATES.md](NQ_DH_CODEX_TASK_TEMPLATES.md) |

## Historical Evidence

- GateQ historical archive: [../gates/gate-q/README.md](../gates/gate-q/README.md)。
- GateP historical archive: [../gates/gate-p/README.md](../gates/gate-p/README.md)。
- GateO historical archive: [../gates/gate-o/README.md](../gates/gate-o/README.md)。
- GateM / GateN historical archives: [../gates/gate-m/README.md](../gates/gate-m/README.md), [../gates/gate-n/README.md](../gates/gate-n/README.md)。
- Post-GateQ current cleanup archive: [../archive/current-cleanup/post-gateq/README.md](../archive/current-cleanup/post-gateq/README.md)。

## Current Is Not

- 不是 GateS implemented / frozen / accepted。
- 不是 GateS-1 implementation started。
- 不是 Shadow Run write API implemented。
- 不是 Shadow runner scheduler 或后台任务 started。
- 不是 Shadow Run live execution started。
- 不是实盘就绪。
- 不代表真实 provider 已启用。
- 不是私有交易适配已启用。
- 不是 DH runtime 已集成。
- 不是 AI 已开始。
- 不是 Integration-1 runtime started。
- 不是 Shadow Live runner enabled。
- 不是 trading authorization。

## Current Cleanup Rule

`docs/current` 不再承载 GateO/P/Q、GateK/L/M/N、CI、credential、DB governance、NQ-DH Integration 或旧 docs cleanup 的过程型长文档。后续如需引用历史证据，只链接 `docs/gates/**` 或 `docs/archive/current-cleanup/post-gateq/**`；GateR frozen evidence 以 [../gates/gate-r/README.md](../gates/gate-r/README.md) 作为历史归档入口，GateR-2..8 的 current API / schema / validation 摘要只保留在 [STATUS.md](STATUS.md)、[API.md](API.md)、[DB_SCHEMA.md](DB_SCHEMA.md)、[TESTING.md](TESTING.md) 和 [WORKLOG.md](WORKLOG.md)。下一步推荐进入 GateS-1 work order / implementation plan review；不得新增 GateR-9、GateS implementation、写接口、scheduler、后台 runner、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter、real permission probe 或真实交易路径。
