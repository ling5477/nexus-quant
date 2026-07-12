# NexusQuant

NexusQuant 是通用量化交易平台。当前阶段唯一 authority 是 [docs/current/STATUS.md](docs/current/STATUS.md) 的 `nq-current-authority` 机器可读区块；本 README 只提供入口和短摘要。

## 当前摘要

- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag `nq-gatev-freeze`，archive 为 [docs/gates/gate-v/README.md](docs/gates/gate-v/README.md)。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；GateW planning baseline 已建立并自审，当前只允许提交/push 计划，GateW-1 尚未初始化。
- 最近 accepted batch、当前 work batch 与唯一下一动作均动态读取 [STATUS.md](docs/current/STATUS.md) 和 [ROADMAP.md](docs/current/ROADMAP.md)，本入口不复制 batch authority。
- LIVE：`DISABLED`（关闭）；Shadow trading：`NOT ENABLED`（未启用）。
- AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。

## Current Authority

- [STATUS.md](docs/current/STATUS.md)：唯一阶段状态 authority。
- [ROADMAP.md](docs/current/ROADMAP.md)：下一允许动作和路线。
- [GATEW_PLAN.md](docs/current/GATEW_PLAN.md)：OKX Spot 单交易所准实盘准备与 Shadow-to-Live 安全门槛计划。
- [FACT_SOURCE_INDEX.md](docs/current/FACT_SOURCE_INDEX.md)：authority 分层与历史证据边界。
- [API.md](docs/current/API.md)：已实现 API 能力事实。
- [DB_SCHEMA.md](docs/current/DB_SCHEMA.md)：已落地 schema 事实。
- [ARCHITECTURE.md](docs/current/ARCHITECTURE.md) / [MODULES.md](docs/current/MODULES.md)：架构和模块职责。
- [TESTING.md](docs/current/TESTING.md) / [WORKLOG.md](docs/current/WORKLOG.md)：append-only evidence ledger。

## Boundary

GateV frozen/tagged 与 GateW planning 均不代表 LIVE、Shadow trading、AI/DH runtime、Integration runtime、RealClient、real provider、private trading 或真实交易已启用。`acknowledge / escalate / resolve / close` 只表示本地人工诊断复核，不构成交易授权。
