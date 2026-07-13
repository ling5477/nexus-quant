# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要。

## 当前摘要

- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag `nq-gatev-freeze`，durable archive 为 [../gates/gate-v/README.md](../gates/gate-v/README.md)。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；GateW-1、GateW-2 为 `ACCEPTED / CI GREEN`；GateW-3 venue-rule implementation 与 migration conformance review 继续有效，但 latest committed exact-head CI run `29253811976` 失败，当前仍为 `COMMITTED / CI FAILED / FIX REQUIRED`。动态 Flyway 与 Playwright timeout 修复已独立 review accepted，但尚未 commit/push；dry-run order preview 仍未获授权。
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
| GateW active plan | [GATEW_PLAN.md](GATEW_PLAN.md) | 否；定义 OKX Spot planning、GateW-2 冻结安全基线、GateW-3 venue-rule fact model 与 no-side-effect preview review，不决定 current authority |
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
- GateW-3 failed CI 不表示 implementation/review 被撤销，也不表示 batch 已接受；当前唯一动作是 `NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH`。不得把本地验证或 review accepted 解释成 fix commit 已存在、exact-head CI green 或可以交易。
