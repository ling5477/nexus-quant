# NexusQuant

NexusQuant 是通用量化交易平台。当前状态唯一 authority 是 [docs/current/STATUS.md](docs/current/STATUS.md) 的 `nq-current-authority` 机器可读区块；本 README 只提供入口和短摘要。

## 当前摘要

<!-- nq-current-summary:start -->
- 最近冻结的业务阶段已接受并打 tag；固定身份及 CI 以 [STATUS.md](docs/current/STATUS.md) 为准，13-role [strict archive](docs/gates/gate-z/README.md) 保留冻结前历史快照。
- V1 Closure 计划已接受；当前唯一下一动作见 [STATUS.md](docs/current/STATUS.md)，产品顺序见 [ROADMAP.md](docs/current/ROADMAP.md)。
- accepted batch、work batch 与唯一下一允许动作以 [STATUS.md](docs/current/STATUS.md) 为准；[ROADMAP.md](docs/current/ROADMAP.md) 解释后续工作。
- 已完成的 GateY pilot 事实见 [STATUS.md](docs/current/STATUS.md)；该历史验收不授予再次执行权。
- Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Current Authority

- [NQ V1 产品收敛计划](docs/current/NQ_V1_CLOSURE_PLAN.md)：基于当前代码的主链、事实归属、阻塞项与交付顺序；接受状态仍以 STATUS 为准。
- [STATUS.md](docs/current/STATUS.md)：唯一阶段状态 authority。
- [ROADMAP.md](docs/current/ROADMAP.md)：下一允许动作和路线。
- [FACT_SOURCE_INDEX.md](docs/current/FACT_SOURCE_INDEX.md)：authority 分层与 archive 边界。
- [API.md](docs/current/API.md) / [DB_SCHEMA.md](docs/current/DB_SCHEMA.md)：当前能力事实。
- [ARCHITECTURE.md](docs/current/ARCHITECTURE.md) / [MODULES.md](docs/current/MODULES.md)：架构与模块职责。
- [TESTING.md](docs/current/TESTING.md) / [WORKLOG.md](docs/current/WORKLOG.md)：append-only evidence ledger。

## Historical Evidence

以下一行是冻结前的摘要原文，仅作为历史记录；当前冻结身份以上方摘要和 STATUS 为准。

- 最近冻结的 GateAUDIT 已接受并打 tag；固定身份及 CI 以 [STATUS.md](docs/current/STATUS.md) 为准，历史证据见 [strict archive](docs/gates/gate-audit/README.md)。

- GateAUDIT strict archive：[docs/gates/gate-audit/](docs/gates/gate-audit/)；GateY pilot 历史证据见 [GateY strict archive](docs/gates/gate-y/)。这些历史证据不覆盖 current authority。
- 其他 Gate archive：[docs/gates/](docs/gates/)；通用历史归档：[docs/archive/](docs/archive/)。

## Boundary

GateY 只证明单账户、单 credential、OKX Spot BTC-USDT BUY LIMIT、`<= 10 USDT`、人工受控 exactly-one PLACE 与完整 reconciliation。它不授权第二 pilot、通用 LIVE、自动策略实盘、多订单/多账户/多交易所、合约/杠杆、transfer/withdraw 或 AI/DH 交易。
