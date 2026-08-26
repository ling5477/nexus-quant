# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要。

## 当前摘要

<!-- nq-current-summary:start -->
- 最近冻结 Gate 为 GateX：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 为 [../gates/gate-x/](../gates/gate-x/)。
- GateY：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；GateY-6F minimal live pilot=`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- GateY freeze archive candidate 位于 [../gates/gate-y/](../gates/gate-y/)；annotated tag=`nq-gatey-freeze` 仍为 `TAG PENDING`（待创建）。
- 当前唯一动作是 `NQ-GATEY-FREEZE-CLOSEOUT`。
- Pilot final：PLACE=1、retry=0、CANCEL=0、activeLease=0、LIVE=false、kill=`ENGAGED`、Attempt-02 未创建。
- Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Authority Map

| 职责 | 文件 | 是否决定 current Gate |
| --- | --- | --- |
| 唯一阶段状态 | [STATUS.md](STATUS.md) | 是 |
| 下一允许动作 | [ROADMAP.md](ROADMAP.md) | 否 |
| Authority 分层 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) | 否；必须服从 STATUS |
| GateY archive candidate | [../gates/gate-y/README.md](../gates/gate-y/README.md) | 否；historical/pre-tag evidence |
| API / Schema / 架构 | [API.md](API.md)、[DB_SCHEMA.md](DB_SCHEMA.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[MODULES.md](MODULES.md) | 否 |
| Evidence ledger | [TESTING.md](TESTING.md) / [WORKLOG.md](WORKLOG.md) | 否；append-only |

## Historical Evidence

- Gate archive：[../gates/](../gates/)；GateY 全部 process/task evidence 已进入 [GateY strict archive candidate](../gates/gate-y/README.md)。
- General archive：[../archive/](../archive/)。
- Historical evidence 不覆盖 [STATUS.md](STATUS.md)，也不授权新的 runtime、pilot、LIVE、transfer/withdraw、AI 或 DH 操作。

## Current Is Not

- 不是 annotated tag 已创建；tag 仍须等待 freeze commit exact-head CI success。
- 不是第二 pilot、通用 LIVE、自动策略交易、多订单、多账户、多交易所、合约/杠杆或资金移动已授权。
- 不是 AI/DH runtime 可执行交易。
