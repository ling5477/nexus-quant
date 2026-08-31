# NexusQuant

NexusQuant 是通用量化交易平台。当前阶段唯一 authority 是 [docs/current/STATUS.md](docs/current/STATUS.md) 的 `nq-current-authority` 机器可读区块；本 README 只提供入口和短摘要。

## 当前摘要

<!-- nq-current-summary:start -->
- 最近冻结 Gate 为 GateY：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 为 [docs/gates/gate-y/](docs/gates/gate-y/)，tag=`nq-gatey-freeze`。
- GateAUDIT：`IN PROGRESS / NOT FROZEN`；Phase 0～3 已完成，Phase4 F-001～F-004 均为 `ACCEPTED / CI_GREEN`，remaining disposition closeout=`REVIEW_ACCEPTED / READY_TO_COMMIT`。
- GateY freeze commit=`72fbf5e78f217a02b572a54fadb17dea204b594f`；exact-head CI run=`33037514013 / completed / success / 11 jobs`。
- 当前唯一动作是 `NQ-GATEAUDIT-PHASE4-REMAINING-DISPOSITION-AND-CONSOLIDATION-COMMIT`；完成commit/exact-head CI后进入Phase5。
- Pilot final：PLACE=1、retry=0、CANCEL=0、activeLease=0、LIVE=false、kill=`ENGAGED`、Attempt-02 未创建。
- Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Current Authority

- [STATUS.md](docs/current/STATUS.md)：唯一阶段状态 authority。
- [ROADMAP.md](docs/current/ROADMAP.md)：下一允许动作和路线。
- [FACT_SOURCE_INDEX.md](docs/current/FACT_SOURCE_INDEX.md)：authority 分层与 archive 边界。
- [API.md](docs/current/API.md) / [DB_SCHEMA.md](docs/current/DB_SCHEMA.md)：当前能力事实。
- [ARCHITECTURE.md](docs/current/ARCHITECTURE.md) / [MODULES.md](docs/current/MODULES.md)：架构与模块职责。
- [TESTING.md](docs/current/TESTING.md) / [WORKLOG.md](docs/current/WORKLOG.md)：append-only evidence ledger。

## Historical Evidence

- GateY strict archive：[docs/gates/gate-y/](docs/gates/gate-y/)，包含 plan、work orders、全部 task evidence、失败/remediation 与最小实盘 pilot 证据；它是 historical evidence，不覆盖 current authority。
- 其他 Gate archive：[docs/gates/](docs/gates/)；通用历史归档：[docs/archive/](docs/archive/)。

## Boundary

GateY 只证明单账户、单 credential、OKX Spot BTC-USDT BUY LIMIT、`<= 10 USDT`、人工受控 exactly-one PLACE 与完整 reconciliation。它不授权第二 pilot、通用 LIVE、自动策略实盘、多订单/多账户/多交易所、合约/杠杆、transfer/withdraw 或 AI/DH 交易。
