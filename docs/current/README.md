# Current Docs

`docs/current/` 保存当前控制面。当前状态唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要。

## 当前摘要

<!-- nq-current-summary:start -->
- 最近冻结的 GateAUDIT 已接受并打 tag；固定身份及 CI 以 [STATUS.md](STATUS.md) 为准，历史证据见 [strict archive](../gates/gate-audit/README.md)。
- accepted batch、work batch 与唯一 next action 从 [STATUS.md](STATUS.md) 的机器区块读取；[ROADMAP.md](ROADMAP.md) 解释后续工作。
- GateY pilot 的验收事实见 [STATUS.md](STATUS.md)；该历史验收不授予再次执行权。
- Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Authority Map

| 职责 | 文件 | 是否决定 current Gate |
| --- | --- | --- |
| 唯一阶段状态 | [STATUS.md](STATUS.md) | 是 |
| 下一允许动作 | [ROADMAP.md](ROADMAP.md) | 否 |
| Authority 分层 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) | 否；必须服从 STATUS |
| GateAUDIT strict archive | [../gates/gate-audit/README.md](../gates/gate-audit/README.md) | 否；已冻结 historical evidence |
| API / Schema / 架构 | [API.md](API.md)、[DB_SCHEMA.md](DB_SCHEMA.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[MODULES.md](MODULES.md) | 否 |
| Evidence ledger | [TESTING.md](TESTING.md) / [WORKLOG.md](WORKLOG.md) | 否；append-only |

## Historical Evidence

- Gate archive：[../gates/](../gates/)；GateY pilot 的历史证据见 [GateY strict archive](../gates/gate-y/README.md)。
- General archive：[../archive/](../archive/)。
- Historical evidence 不覆盖 [STATUS.md](STATUS.md)，也不授权新的 runtime、pilot、LIVE、transfer/withdraw、AI 或 DH 操作。

## Current Is Not

- F-002 只接受 Phase4 restart foundation；Phase6 full L4资格已由后续B6聚合接受，L5/L6也已接受，详见[Phase6 final acceptance](../audit/evidence/GATEAUDIT_PHASE6_L5_L6_FINAL_ACCEPTANCE.md)；不得用早期F-002 foundation替代后续资格证据。
- 不是第二 pilot、通用 LIVE、自动策略交易、多订单、多账户、多交易所、合约/杠杆或资金移动已授权。
- 不是 AI/DH runtime 可执行交易。
