# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要。

## 当前摘要

- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag `nq-gatev-freeze`，durable archive 为 [../gates/gate-v/README.md](../gates/gate-v/README.md)。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；GateW-1、GateW-2 为 `ACCEPTED / CI GREEN`；GateW-3 LIMIT-only internal order preview review 继续有效，implementation commit `eff79d7c7ea1b034de4e77c7ec64974c247027f5` 已存在，但 exact-head CI run `29308652349` 为 `completed / failure`。当前为 `COMMITTED / CI FAILED / FIX REQUIRED`，accepted batch 仍为 GateW-2。
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
- GateW-3 preview review accepted 不表示 batch 已接受；E2E runner fix/review 已通过，当前唯一动作是 `NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH`。不得把 failed implementation exact-head CI 或本地 fix validation 写成 GitHub CI green，不得把 venue capability、review PASS 或本地结构 preview 解释成 runtime facts 已存在、可以交易或已获 LIVE/交易授权。
