# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要。

## 当前摘要

<!-- nq-current-summary:start -->
- 最近冻结 Gate 为 GateY：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 为 [../gates/gate-y/](../gates/gate-y/)，tag=`nq-gatey-freeze`。
- GateAUDIT：`IN PROGRESS / NOT FROZEN`（治理进行中 / 未冻结）；Phase 0=`ACCEPTED / CI_GREEN / COMPLETE`，immutable pair=`40e1077e... / 33306024232`；Phase 1 inventory、Phase 2 analysis 与 Phase 3 disposition 已完成。
- GateY freeze commit=`72fbf5e78f217a02b572a54fadb17dea204b594f`；exact-head CI run=`33037514013 / completed / success / 11 jobs`。
- Phase 3 findings=`P0 0 / P1 4 / P2 8 / P3 1`；F-001=`95b859ee... / 33347091147`、F-002=`0651a736... / 33387882472`、F-003=`327c2229... / 33399190770`、F-004=`18efc06c... / 33358364678`，均为 `ACCEPTED / CI_GREEN`。
- Phase4 remaining disposition closeout=`COMPLETE / ACCEPTED / CI_GREEN`，immutable pair=`7ca1fc92... / 33405549149`，blocking P0/P1=`0/0`。
- Phase5A=`READY_TO_START / NOT_IMPLEMENTED`；inventory seed=`P5-F001～P5-F009 / P1 3 / P2 6`。Legacy Phase3 IDs F-005/F-011因canonical identity不可恢复而退休，不向Phase5传递未知语义。
- 当前唯一动作是 `NQ-GATEAUDIT-PHASE5A-CANONICAL-DELIVERY-IMPLEMENTATION`，对应 workstream `NQ-GATEAUDIT-PHASE5A-CANONICAL-CI-AND-SUPPLY-CHAIN`。
- Pilot final：PLACE=1、retry=0、CANCEL=0、activeLease=0、LIVE=false、kill=`ENGAGED`、Attempt-02 未创建。
- Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Authority Map

| 职责 | 文件 | 是否决定 current Gate |
| --- | --- | --- |
| 唯一阶段状态 | [STATUS.md](STATUS.md) | 是 |
| 下一允许动作 | [ROADMAP.md](ROADMAP.md) | 否 |
| Authority 分层 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) | 否；必须服从 STATUS |
| GateY strict archive | [../gates/gate-y/README.md](../gates/gate-y/README.md) | 否；已冻结 historical evidence |
| API / Schema / 架构 | [API.md](API.md)、[DB_SCHEMA.md](DB_SCHEMA.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[MODULES.md](MODULES.md) | 否 |
| Evidence ledger | [TESTING.md](TESTING.md) / [WORKLOG.md](WORKLOG.md) | 否；append-only |

## Historical Evidence

- Gate archive：[../gates/](../gates/)；GateY 全部 process/task evidence 已进入 [GateY strict archive](../gates/gate-y/README.md)。
- General archive：[../archive/](../archive/)。
- Historical evidence 不覆盖 [STATUS.md](STATUS.md)，也不授权新的 runtime、pilot、LIVE、transfer/withdraw、AI 或 DH 操作。

## Current Is Not

- F-002 只接受 Phase4 restart foundation；Phase6 full L4 failure matrix仍未执行，不得解释为完整故障资格已完成。
- 不是第二 pilot、通用 LIVE、自动策略交易、多订单、多账户、多交易所、合约/杠杆或资金移动已授权。
- 不是 AI/DH runtime 可执行交易。
