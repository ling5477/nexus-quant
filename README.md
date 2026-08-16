# NexusQuant

NexusQuant 是通用量化交易平台。当前阶段唯一 authority 是 [docs/current/STATUS.md](docs/current/STATUS.md) 的 `nq-current-authority` 机器可读区块；本 README 只提供入口和短摘要，不复制完整 machine authority。

## 当前摘要

<!-- nq-current-summary:start -->
- 最近冻结 Gate 为 GateX：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 为 [docs/gates/gate-x/](docs/gates/gate-x/)，release tag=`nq-gatex-freeze`。
- GateY：`IN PROGRESS / NOT FROZEN`（进入治理容器 / 未冻结）；GateY-6D=`ACCEPTED / CI GREEN`（已接受 / CI 已通过），GateY-6E=`REVIEW ACCEPTED / READY TO COMMIT`（审查已接受 / 可进入提交前复核）；GateY 整体未接受、未冻结。
- Attempt-13=`COMPLETED / ACCEPTED`; production deployment=`STOPPED`；production soak=`COMPLETED`，worker=`STOPPED`。
- 当前唯一动作是 `NQ-GATEY-6E-COMMIT-AND-PUSH`；该动作只允许提交已接受的 prerequisite capability 并等待 exact-head CI。real mutation runtime 仍未绑定，exact PilotScope 未物化，当前请求不构成第一笔真实订单授权；任何真实 PLACE 必须在后续再次取得绑定 exact account/credential/order/risk/window/scope hash 的用户显式授权，精确边界以 [STATUS.md](docs/current/STATUS.md) 为准。
- LIVE：`DISABLED`（关闭）；Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Current Authority

- [STATUS.md](docs/current/STATUS.md)：唯一阶段状态 authority。
- [ROADMAP.md](docs/current/ROADMAP.md)：下一允许动作和路线。
- [GATEY_PLAN.md](docs/current/GATEY_PLAN.md)：OKX Spot 单场所微资金受控实盘候选的 hard gate、控制面、批次与验收计划；不构成 LIVE 授权。
- [GATEX_PLAN.md](docs/gates/gate-x/GATEX_PLAN.md)：GateX 已归档实施基线、批次、边界与冻结条件；不决定 current 状态。
- [GateX strict archive](docs/gates/gate-x/README.md)：已冻结 GateX 的 durable evidence；历史证据不决定 current 状态。
- [GateW archive](docs/gates/gate-w/README.md)：已冻结 GateW 的 strict archive；历史证据不决定 current 状态。
- [FACT_SOURCE_INDEX.md](docs/current/FACT_SOURCE_INDEX.md)：authority 分层与历史证据边界。
- [API.md](docs/current/API.md)：已实现 API 能力事实。
- [DB_SCHEMA.md](docs/current/DB_SCHEMA.md)：已落地 schema 事实。
- [ARCHITECTURE.md](docs/current/ARCHITECTURE.md) / [MODULES.md](docs/current/MODULES.md)：架构和模块职责。
- [TESTING.md](docs/current/TESTING.md) / [WORKLOG.md](docs/current/WORKLOG.md)：append-only evidence ledger。

## Historical Evidence

- Gate archive 统一位于 [docs/gates/](docs/gates/)；最近已冻结 Gate 的精确 tag 与 archive 入口从 [STATUS.md](docs/current/STATUS.md) 获取。
- GateX 的不可覆盖 task 历史从 [archive evidence index](docs/gates/gate-x/source/task-evidence/README.md) 访问；GateW archive 继续保留其历史 Attempt 证据。
- General archive 位于 [docs/archive/](docs/archive/)。

## Boundary

当前计划、历史 archive 与 evidence 均不代表 LIVE、Shadow trading、AI/DH runtime、Integration runtime、RealClient、real provider、private trading 或真实交易已启用。`acknowledge / escalate / resolve / close` 只表示本地人工诊断复核，不构成交易授权。
