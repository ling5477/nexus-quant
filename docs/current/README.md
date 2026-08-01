# Current Docs

`docs/current/` 保存当前控制面。当前阶段唯一 authority 是 [STATUS.md](STATUS.md) 顶部的 `nq-current-authority` 机器可读区块；本文件只作为入口和简要摘要。

## 当前摘要

- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag `nq-gatev-freeze`，durable archive 为 [../gates/gate-v/README.md](../gates/gate-v/README.md)。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；GateW-1 至 GateW-4 均为 `ACCEPTED / CI GREEN`。GateW-4 implementation/acceptance head `07b94f89...` 的 run `29339016784` 已成功；GateW-FREEZE 仅初始化为 `NOT STARTED`。
- GateW Attempt-10 start contract remediation commit `aeacfebd...` 的 exact-head CI run `30697734316` 已 `completed / success / 10 jobs / bad=0`；当前唯一允许动作是受控 `NQ-GATEW-ATTEMPT-11-PREPARATION-AND-START`。
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
- Commit `c16f27c3...c78f` 仍是服务器 current 与 unit links 的 last-known-good immutable release。Attempt-10 attempt-02 的 final release `f06a38f2...` 虽已通过 immutable/root/POSIX verifier 并安装，但启动前安全合同失败后已 canonical 回滚；失败 release 保留且未运行。
- `NQ-GATEW-ATTEMPT-10-PRECREATE-PREREQUISITE-REMEDIATION-DEPLOYMENT-VERIFICATION` 的生产 canonical readback 曾返回 `INTERNAL_SANITIZED_READBACK_FAILURE`，并按 `DEPLOYMENT VERIFICATION FAILED / CODE REMEDIATION REQUIRED`（部署验证失败 / 需要代码整改）回滚到 `c16f27c3...`。旧 fixed RC `5e7a9c4e...` 与整改 RC `ef803568...` 均已被独立 review 拒绝。新 RC `5a7e824e...` 的 JAR full-stream/CRC、Windows/Linux exact build 与 122 JAR 全量读取通过；attempt-03 fixture timestamp P1 已最小修复并三平台重复验证。Review/remediation commit `15ee2ee2...` 的 exact-head CI run `30653141014` 10/10 GREEN，该 review 结果为 `ACCEPTED / CI GREEN / DEPLOYMENT AUTHORIZED`。
- Attempt-10=`FAILED / STOPPED`，production deployment=`STOPPED`。唯一 RunId `gatew-soak-20260801T102353Z-932e26a4` 已 terminalize；worker 实际从未启动，OKX calls=`0`，first heartbeat/hash chain/168h clock 均不存在，current/unit links 已回滚。该 run 禁止修改或复用。
- Attempt-11=`NOT_CREATED / AUTHORIZED`，production deployment=`NOT_STARTED`。只允许从 clean exact commit 构建、验证并部署新 immutable release，创建全新 RunId；LIVE、交易写侧、自动重试与 freeze/archive/tag 继续禁止。
- GateW freeze closeout 当前仍为 `NOT STARTED`（未开始）；只有 168h acceptance 得出 `ACCEPT`（接受）后才能开始。不得把 local soak、restore、incident PASS 或 CI green 解释成真实 permission、余额充分、账户健康、可以交易、已获 LIVE/交易授权或 freeze readiness。
