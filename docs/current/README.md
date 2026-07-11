# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要。

## 当前摘要

- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- Release tag：`nq-gateu-freeze`；tagged commit `48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab`。
- GateU durable archive：[../gates/gate-u/README.md](../gates/gate-u/README.md)。
- GateV：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；唯一 active plan：[GATEV_PLAN.md](GATEV_PLAN.md)。
- 最近 accepted batch、当前 work batch 与唯一下一动作均动态读取 [STATUS.md](STATUS.md) 和 [ROADMAP.md](ROADMAP.md)，本入口不复制 batch 状态。
- LIVE：`DISABLED`；Shadow trading：`NOT ENABLED`；AI：`NOT STARTED`；DH runtime：`NOT INTEGRATED`。

## Authority Map

| 职责 | 文件 | 是否决定 current Gate |
| --- | --- | --- |
| 唯一阶段状态 | [STATUS.md](STATUS.md) | 是 |
| 下一允许动作 | [ROADMAP.md](ROADMAP.md) | 否 |
| Authority 分层 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) | 否；必须服从 STATUS |
| API 能力 | [API.md](API.md) | 否 |
| Schema 能力 | [DB_SCHEMA.md](DB_SCHEMA.md) | 否 |
| 架构 / 模块 | [ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md) | 否 |
| 运行手册 | [RUNBOOK.md](RUNBOOK.md) | 否 |
| 前端设计系统 | [FRONTEND_DESIGN_SYSTEM.md](FRONTEND_DESIGN_SYSTEM.md) | 否 |
| Evidence ledger | [TESTING.md](TESTING.md) / [WORKLOG.md](WORKLOG.md) | 否；append-only |
| GateV active plan | [GATEV_PLAN.md](GATEV_PLAN.md) | 否；只定义批次、边界和下一代码切片 |
| Workflow / skills | [CODEX_PROJECT_INSTRUCTIONS.md](CODEX_PROJECT_INSTRUCTIONS.md)、[NQ_DH_CODEX_PLUGIN_WORKFLOW.md](NQ_DH_CODEX_PLUGIN_WORKFLOW.md)、[NQ_DH_WORKFLOW_ROUTER_SKILL.md](NQ_DH_WORKFLOW_ROUTER_SKILL.md)、[NQ_DH_CODEX_TASK_TEMPLATES.md](NQ_DH_CODEX_TASK_TEMPLATES.md) | 否；每轮动态读取 STATUS |

## Historical Evidence

- Gate archive：`docs/gates/**`，当前最新入口为 [GateU archive](../gates/gate-u/README.md)。
- General archive：`docs/archive/**`。
- Historical evidence 不覆盖 `STATUS.md`，也不授权下一 Gate implementation。

## Current Is Not

- 本入口不判定 accepted/work batch；其精确状态只读取 [STATUS.md](STATUS.md)。
- 不是 LIVE 或 Shadow trading 已启用。
- 不是 AI / DH / Integration runtime 已启动。
- 不是 RealClient、real provider、private trading adapter 或 real permission probe 已实现。
