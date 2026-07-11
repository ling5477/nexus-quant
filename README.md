# NexusQuant

NexusQuant 是通用量化交易平台。当前阶段唯一 authority 是 [docs/current/STATUS.md](docs/current/STATUS.md) 的 `nq-current-authority` 机器可读区块；本 README 只提供入口和短摘要。

## 当前摘要

- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- Release tag：`nq-gateu-freeze`。
- Tagged commit：`48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab`。
- GateU archive：[docs/gates/gate-u/README.md](docs/gates/gate-u/README.md)。
- GateV：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；active plan：[docs/current/GATEV_PLAN.md](docs/current/GATEV_PLAN.md)。
- GateV-1、GateV-2、GateV-3A：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateV-3 scheduler：`NOT STARTED`（未开始）。
- LIVE：`DISABLED`（关闭）；Shadow trading：`NOT ENABLED`（未启用）。
- AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。

## Current Authority

- [STATUS.md](docs/current/STATUS.md)：唯一阶段状态 authority。
- [ROADMAP.md](docs/current/ROADMAP.md)：下一允许动作和路线。
- [FACT_SOURCE_INDEX.md](docs/current/FACT_SOURCE_INDEX.md)：authority 分层与历史证据边界。
- [API.md](docs/current/API.md)：已实现 API 能力事实。
- [DB_SCHEMA.md](docs/current/DB_SCHEMA.md)：已落地 schema 事实。
- [ARCHITECTURE.md](docs/current/ARCHITECTURE.md) / [MODULES.md](docs/current/MODULES.md)：架构和模块职责。
- [TESTING.md](docs/current/TESTING.md) / [WORKLOG.md](docs/current/WORKLOG.md)：append-only evidence ledger。

## Boundary

GateU frozen/tagged 与 GateV active 都不代表 LIVE、Shadow trading、AI/DH runtime、Integration runtime、RealClient、real provider、private trading 或真实交易已启用。GateV-1、GateV-2、GateV-3A 已通过 CI acceptance；GateV-3 scheduler 尚未开始，具体下一动作只读取 current authority 与 ROADMAP。
