# NexusQuant

NexusQuant 是通用量化交易平台。当前阶段唯一 authority 是 [docs/current/STATUS.md](docs/current/STATUS.md) 的 `nq-current-authority` 机器可读区块；本 README 只提供入口和短摘要。

## 当前摘要

- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- Release tag：`nq-gateu-freeze`。
- Tagged commit：`48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab`。
- GateU archive：[docs/gates/gate-u/README.md](docs/gates/gate-u/README.md)。
- GateV：`PLAN / NOT IMPLEMENTED`（已规划 / 未实现）；计划入口：[docs/current/GATEV_PLAN.md](docs/current/GATEV_PLAN.md)。
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

GateU frozen/tagged 不代表 LIVE、Shadow trading、AI/DH runtime、Integration runtime、RealClient、real provider、private trading 或真实交易已启用。GateV 当前只有 planning；本 planning 提交通过 CI 后直接进入 GateV-1 durable review fact model 代码实现，当前不得写成已实现。
