# NexusQuant

NexusQuant 是通用量化交易平台。当前阶段唯一 authority 是 [docs/current/STATUS.md](docs/current/STATUS.md) 的 `nq-current-authority` 机器可读区块；本 README 只提供入口和短摘要，不复制完整 machine authority。

## 当前摘要

<!-- nq-current-summary:start -->
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）。
- GateW 当前 Attempt-13=`COMPLETED / ACCEPTED`; production deployment=`STOPPED`；production soak=`COMPLETED`，worker=`STOPPED`。
- 当前唯一动作是 `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`；GateW 仍未冻结，exact transition 以 [STATUS.md](docs/current/STATUS.md) 为准。
- LIVE：`DISABLED`（关闭）；Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Current Authority

- [STATUS.md](docs/current/STATUS.md)：唯一阶段状态 authority。
- [ROADMAP.md](docs/current/ROADMAP.md)：下一允许动作和路线。
- [GATEW_PLAN.md](docs/current/GATEW_PLAN.md)：GateW 的计划与安全边界，不决定 current 状态。
- [FACT_SOURCE_INDEX.md](docs/current/FACT_SOURCE_INDEX.md)：authority 分层与历史证据边界。
- [API.md](docs/current/API.md)：已实现 API 能力事实。
- [DB_SCHEMA.md](docs/current/DB_SCHEMA.md)：已落地 schema 事实。
- [ARCHITECTURE.md](docs/current/ARCHITECTURE.md) / [MODULES.md](docs/current/MODULES.md)：架构和模块职责。
- [TESTING.md](docs/current/TESTING.md) / [WORKLOG.md](docs/current/WORKLOG.md)：append-only evidence ledger。

## Historical Evidence

- Gate archive 统一位于 [docs/gates/](docs/gates/)；最近已冻结 Gate 的精确 tag 与 archive 入口从 [STATUS.md](docs/current/STATUS.md) 获取。
- GateW 的不可覆盖 Attempt 历史从 [evidence index](docs/current/evidence/gate-w/README.md) 访问；历史记录不覆盖 current authority。
- General archive 位于 [docs/archive/](docs/archive/)。

## Boundary

当前计划、历史 archive 与 evidence 均不代表 LIVE、Shadow trading、AI/DH runtime、Integration runtime、RealClient、real provider、private trading 或真实交易已启用。`acknowledge / escalate / resolve / close` 只表示本地人工诊断复核，不构成交易授权。
