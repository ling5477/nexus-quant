# NexusQuant

NexusQuant 是通用量化交易平台。当前阶段唯一 authority 是 [docs/current/STATUS.md](docs/current/STATUS.md) 的 `nq-current-authority` 机器可读区块；本 README 只提供入口和短摘要，不复制完整 machine authority。

## 当前摘要

<!-- nq-current-summary:start -->
- 最近冻结 Gate 为 GateW：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；archive 为 [docs/gates/gate-w/](docs/gates/gate-w/)，release tag=`nq-gatew-freeze`。
- GateX：`IN PROGRESS / NOT FROZEN`（进入治理容器 / 未冻结）；GateX-0A/0B/0C/0D/1/2/3/4/4B/4C=`ACCEPTED / CI GREEN`（已接受 / CI 已通过），GateX-0E=`AUDITED / IMPLEMENTATION NOT REQUIRED`（已审计 / 无需实施），GateX-5 受控 Shadow materialization=`IMPLEMENTED / PENDING REVIEW`（已实现 / 待独立审查）。
- Attempt-13=`COMPLETED / ACCEPTED`; production deployment=`STOPPED`；production soak=`COMPLETED`，worker=`STOPPED`。
- 当前唯一动作是 `NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-REVIEW`；只允许独立审查受控 Shadow Run 创建，不得启动 Shadow Run 或触达交易写侧，exact transition 以 [STATUS.md](docs/current/STATUS.md) 为准。
- LIVE：`DISABLED`（关闭）；Shadow trading：`NOT ENABLED`（未启用）；AI：`NOT STARTED`（未开始）；DH runtime：`NOT INTEGRATED`（未集成）。
<!-- nq-current-summary:end -->

## Current Authority

- [STATUS.md](docs/current/STATUS.md)：唯一阶段状态 authority。
- [ROADMAP.md](docs/current/ROADMAP.md)：下一允许动作和路线。
- [GATEX_PLAN.md](docs/current/GATEX_PLAN.md)：GateX 实施基线、批次、边界与冻结条件；不代表 capability 已实现。
- [GateW archive](docs/gates/gate-w/README.md)：已冻结 GateW 的 strict archive；历史证据不决定 current 状态。
- [FACT_SOURCE_INDEX.md](docs/current/FACT_SOURCE_INDEX.md)：authority 分层与历史证据边界。
- [API.md](docs/current/API.md)：已实现 API 能力事实。
- [DB_SCHEMA.md](docs/current/DB_SCHEMA.md)：已落地 schema 事实。
- [ARCHITECTURE.md](docs/current/ARCHITECTURE.md) / [MODULES.md](docs/current/MODULES.md)：架构和模块职责。
- [TESTING.md](docs/current/TESTING.md) / [WORKLOG.md](docs/current/WORKLOG.md)：append-only evidence ledger。

## Historical Evidence

- Gate archive 统一位于 [docs/gates/](docs/gates/)；最近已冻结 Gate 的精确 tag 与 archive 入口从 [STATUS.md](docs/current/STATUS.md) 获取。
- GateW 的不可覆盖 Attempt 历史从 [archive evidence index](docs/gates/gate-w/source/task-evidence/README.md) 访问；current evidence index 只保留 post-tag 导航。
- General archive 位于 [docs/archive/](docs/archive/)。

## Boundary

当前计划、历史 archive 与 evidence 均不代表 LIVE、Shadow trading、AI/DH runtime、Integration runtime、RealClient、real provider、private trading 或真实交易已启用。`acknowledge / escalate / resolve / close` 只表示本地人工诊断复核，不构成交易授权。
