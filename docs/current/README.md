# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要。

## 当前摘要

<!-- nq-current-summary:start -->
- 最近冻结 Gate 为 GateY：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 为 [../gates/gate-y/](../gates/gate-y/)，tag=`nq-gatey-freeze`。
- GateAUDIT：`IN PROGRESS / NOT FROZEN`（治理进行中 / 未冻结）；Phase 0=`ACCEPTED / CI_GREEN / COMPLETE`，immutable pair=`40e1077e... / 33306024232`；Phase 1 inventory、Phase 2 analysis 与 Phase 3 disposition 已完成。
- GateY freeze commit=`72fbf5e78f217a02b572a54fadb17dea204b594f`；exact-head CI run=`33037514013 / completed / success / 11 jobs`。
- Phase 3 findings=`P0 0 / P1 4 / P2 8 / P3 1`；F-001=`ACCEPTED / CI_GREEN`（`95b859ee... / 33347091147`）；F-004=`ACCEPTED / CI_GREEN`（`18efc06c... / 33358364678`）；F-002 Phase4 restart foundation=`REVIEW_ACCEPTED / EXACT_HEAD_CI_FAILED`（`e1037328... / 33376886158`）。
- CI datasource binding remediation=`REVIEW_ACCEPTED / READY_TO_COMMIT`；当前唯一动作是 `NQ-GATEAUDIT-PHASE4-F002-DATASOURCE-BINDING-COMMIT`，只提交已接受6-path candidate并取得新 exact-head CI，不得启动 F-003 或扩大 runtime 权限。
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

- 不是 F-002 已完全关闭；当前只证明 Phase4 restart foundation，Phase6 full L4 failure matrix 仍未执行。
- 不是第二 pilot、通用 LIVE、自动策略交易、多订单、多账户、多交易所、合约/杠杆或资金移动已授权。
- 不是 AI/DH runtime 可执行交易。
