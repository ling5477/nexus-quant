# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要。

## 当前摘要

- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag `nq-gatev-freeze`，durable archive 为 [../gates/gate-v/README.md](../gates/gate-v/README.md)。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；GateW-1 至 GateW-4 均为 `ACCEPTED / CI GREEN`。GateW-4 implementation/acceptance head `07b94f89...` 的 run `29339016784` 已成功；GateW-FREEZE 仅初始化为 `NOT STARTED`。
- 最近 accepted batch、当前 work batch 与唯一下一动作均动态读取 [STATUS.md](STATUS.md) 和 [ROADMAP.md](ROADMAP.md)，本入口不复制 batch authority。
- LIVE：`DISABLED`；Shadow trading：`NOT ENABLED`；AI：`NOT STARTED`；DH runtime：`NOT INTEGRATED`。

## Authority Map

| 职责 | 文件 | 是否决定 current Gate |
| --- | --- | --- |
| 唯一阶段状态 | [STATUS.md](STATUS.md) | 是 |
| 下一允许动作 | [ROADMAP.md](ROADMAP.md) | 否 |
| Authority 分层 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) | 否；必须服从 STATUS |
| Gate 治理 workflow | [GOVERNANCE_WORKFLOW.md](GOVERNANCE_WORKFLOW.md) | 否；定义 checker/lifecycle/evidence/release contract |
| Current task evidence | [evidence/gate-w/README.md](evidence/gate-w/README.md) | 否；保存不可覆盖 attempt，不决定阶段 |
| GateW active plan | [GATEW_PLAN.md](GATEW_PLAN.md) | 否；定义 OKX Spot planning、GateW-2 安全基线、GateW-3 diagnostic 边界与 GateW-4 operational safety / Freeze handoff，不决定 current authority |
| GateV historical handoff / GateW planning entry | [GATEV_PLAN.md](GATEV_PLAN.md) | 否；仅保留 GateV historical context 与 GateW planning handoff |
| API / Schema / 架构 | [API.md](API.md)、[DB_SCHEMA.md](DB_SCHEMA.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[MODULES.md](MODULES.md) | 否 |
| Evidence ledger | [TESTING.md](TESTING.md) / [WORKLOG.md](WORKLOG.md) | 否；append-only |

## Historical Evidence

- Gate archive：`docs/gates/**`；GateV 最新入口为 [../gates/gate-v/README.md](../gates/gate-v/README.md)。
- General archive：`docs/archive/**`。
- Historical evidence 不覆盖 `STATUS.md`，也不授权 GateW implementation。
- GateW 前置治理 evidence 已启用；这不表示 GateW planning 或业务实现已开始。

## Current Is Not

- 本入口不判定 accepted/work batch；其精确状态只读取 [STATUS.md](STATUS.md)。
- 不是 LIVE 或 Shadow trading 已启用。
- 不是 AI / DH / Integration runtime 已启动。
- 不是 RealClient、real provider 或 private trading adapter 已实现；GateW-2 仅是默认不装配的 private read-only diagnostic probe，`REAL_SMOKE=NOT_RUN`，不表示远端 permission 或交易授权。
- GateW-4 acceptance 只接受 internal diagnostic/no-side-effect operational safety contract，不表示 GateW frozen 或交易获授权。
- 修复版 Commit A `c16f27c3...c78f` 的 131-artifact immutable release 已完成独立 Linux root/POSIX/ownership/systemd/offline-security/tamper deployment verification；`current` 未切换，units started=`0`，Attempt-10 与新 acceptance clock 均未创建。
- 当前唯一允许动作是 `NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START`；只允许在独立任务中重新执行完整 hard gates 后创建/启动 Attempt-10，不授权恢复 Attempt-09、复用旧 clock、扩大 OKX endpoint、触碰 LIVE/交易写侧或进入 freeze/archive/tag。
- GateW freeze closeout 当前仍为 `NOT STARTED`（未开始）；只有 168h acceptance 得出 `ACCEPT`（接受）后才能开始。不得把 local soak、restore、incident PASS 或 CI green 解释成真实 permission、余额充分、账户健康、可以交易、已获 LIVE/交易授权或 freeze readiness。
