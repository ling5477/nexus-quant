# Current Stage

`docs/current/` 是 NexusQuant 的当前事实入口，只保留当前控制文档、当前事实源、当前状态、当前路线、验证/工作记录、API、DB schema、运行手册、架构/模块摘要和仍需作为 current 使用的 Codex workflow 入口。已冻结 Gate 的过程型证据不再放在 current；历史证据以 `docs/gates/**`、`docs/archive/**` 为准。

## 当前状态

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）；release tag 为 `nq-gateq-freeze`；历史证据入口为 [../gates/gate-q/README.md](../gates/gate-q/README.md)。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）；release tag 为 `nq-gatep-freeze`；历史证据入口为 [../gates/gate-p/README.md](../gates/gate-p/README.md)。
- GateO 及更早 Gate：只作为历史证据读取，入口为 `docs/gates/**` 或 `docs/archive/**`。
- GateR：`NQ-GATER-PLAN-SHADOW-RUN-OPERATIONALIZATION：PLAN READY / NOT IMPLEMENTED`（计划已就绪 / 未实现）。当前 planning 入口为 [GATER_PLAN.md](GATER_PLAN.md)。
- GateR-1：`NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW：PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。当前 review 入口为 [GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md](GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md)；GateR implementation、Shadow Run local fact、API、migration、页面、测试和 CI 均未开始。
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
| GateR planning | [GATER_PLAN.md](GATER_PLAN.md) |
| GateR-1 data model / migration plan review | [GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md](GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md) |
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

- 不是 GateR implementation started。
- 不是 Shadow Run local fact implemented。
- 不是 Shadow Run migration implemented。
- 不是 Shadow Run table created。
- 不是 Shadow Run record created。
- 不是实盘就绪。
- 不代表真实 provider 已启用。
- 不是私有交易适配已启用。
- 不是 DH runtime 已集成。
- 不是 AI 已开始。
- 不是 Integration-1 runtime started。
- 不是 Shadow Live runner enabled。

## Current Cleanup Rule

`docs/current` 不再承载 GateO/P/Q、GateK/L/M/N、CI、credential、DB governance、NQ-DH Integration 或旧 docs cleanup 的过程型长文档。后续如需引用历史证据，只链接 `docs/gates/**` 或 `docs/archive/current-cleanup/post-gateq/**`；GateR planning 以 [GATER_PLAN.md](GATER_PLAN.md) 作为当前计划入口，GateR-1 schema review 以 [GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md](GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md) 作为当前 review 入口。后续 GateR-2 implementation 必须另起任务并明确 allowed files、forbidden scope 和验证命令。
