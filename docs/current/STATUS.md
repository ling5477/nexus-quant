# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateU
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gateu-freeze
last_frozen_gate_commit=48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab
active_gate=GateV
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateV-4
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=d7da91a662be1f0fc0bbf64df70ea57318773697
accepted_batch_acceptance_head=fad9b20900b49fbb918288f8d32d09fc60976444
accepted_batch_ci_run=29181214506
work_batch=GateV-FREEZE
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEV-FREEZE-CLOSEOUT-IMPLEMENTATION
live=DISABLED
shadow_trading=NOT_ENABLED
ai=NOT_STARTED
dh_runtime=NOT_INTEGRATED
integration_runtime=NOT_STARTED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
nq-current-authority:end -->

`docs/current/STATUS.md` 是 NexusQuant 当前阶段状态的唯一 authority。其他 current 文档只能引用或解释本文件，不得复制一套独立的 current Gate / next Gate 判定。

## 1. 当前阶段

- GateU：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateU release tag：`nq-gateu-freeze`。
- Annotated tag object：`800806b02c1e5d29da26ab23662f283e75474178`。
- Tagged / peeled commit：`48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab`。
- Tagged commit CI：GitHub Actions run `29138944526`，`NQ CI Baseline`，`completed / success`，`headSha=48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab`。
- GateU implementation baseline：`9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`。
- GateU durable archive：[../gates/gate-u/README.md](../gates/gate-u/README.md)。
- GateV：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；唯一 active plan 为 [GATEV_PLAN.md](GATEV_PLAN.md)。
- GateV-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。Implementation commit：`f7d71d5a80241ade049a83fa3f90b3ac6ce46806`；CI alignment / acceptance head：`b3dd5f74f154d5ed9e2343bc18e451f48770814f`；`NQ CI Baseline` run `29144345430` 为 `completed / success`。
- GateV-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。Implementation commit 与 acceptance head 均为 `99158738ec980f519637af8df75e4153dfa2869f`；`NQ CI Baseline` run `29150549978` 为 `completed / success`，`headSha=99158738ec980f519637af8df75e4153dfa2869f`。
- GateV-2 仅接受本地 durable review lifecycle：3 个 bounded GET 与 acknowledge/escalate/resolve/close 四个有限 POST，含 RBAC、tenant/owner scope、optimistic locking、idempotency 与脱敏 audit；不创建 case，不影响交易或运行事实，也不构成 trading authorization。
- GateV-3A：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。Implementation commit 与 acceptance head 均为 `45c7df9799c0534ddd3ee291dc9347076dec9ddd`；`NQ CI Baseline` run `29152330658` 为 `completed / success`，`headSha=45c7df9799c0534ddd3ee291dc9347076dec9ddd`。
- GateV-3A 仅接受 PostgreSQL transaction-level advisory lock primitive：使用 `pg_try_advisory_xact_lock(int,int)`、稳定 key mapping、`REQUIRES_NEW` read-only transaction 与 Spring composition；无 migration、无 `@Scheduled`、无业务 callback 或业务副作用，不表示 scheduler 已启用。
- GateV-3：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。专项 review 未发现 P0/P1；implementation commit `6cbceba9d0fbc0fca67f43e898c416ec64a6fa33` 是 acceptance head `b209c416e0daf402216140b62785726f5fd116b6` 的 ancestor；`NQ CI Baseline` run `29155396719` 对该 acceptance head 为 `completed / success`。
- GateV-3 scheduler 默认关闭，只聚合本地 read-only evidence，并使用 GateV-3A PostgreSQL transaction-level advisory lock。它不自动创建或流转 review case，不保存 durable execution history，不构成 trading authorization，也未开启 LIVE 或 Shadow trading。
- GateV-4：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。implementation commit `d7da91a662be1f0fc0bbf64df70ea57318773697` 是 acceptance head `fad9b20900b49fbb918288f8d32d09fc60976444` 的 ancestor；该 acceptance head 对应 `NQ CI Baseline` run `29181214506` 为 exact-HEAD `completed / success`。
- GateV-4 frontend review、API contract、权限、幂等与 E2E 已接受；仅复用 GateV-2 已接受的 7 个 endpoint，未修改 backend contract、migration、scheduler 或状态机。所有 UI 状态只表示本地诊断审查，Review Workbench 不构成 trading authorization。
- GateV-FREEZE：`NOT STARTED`（未开始）。Python manifest preview 继续为 No-file residual；scheduler 仍默认关闭。GateV 整体仍为 `IN PROGRESS / NOT FROZEN`，尚未 freeze、archive 或 tag。

## 2. 安全与运行边界

- LIVE：`DISABLED`（关闭）。
- Shadow trading：`NOT ENABLED`（未启用）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration runtime：`NOT STARTED`（未开始）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- Python artifact import：`NOT STARTED`（未开始）。
- GateU runtime evidence 仍为 GET-only / read-only / no-side-effect / not trading authorization。

## 3. 文档职责

- [ROADMAP.md](ROADMAP.md)：只定义下一允许动作与路线，不决定当前 Gate。
- [README.md](README.md) 与 root `README.md`：只提供入口、短摘要和 archive pointer。
- [API.md](API.md)、[DB_SCHEMA.md](DB_SCHEMA.md)、[ARCHITECTURE.md](ARCHITECTURE.md)、[MODULES.md](MODULES.md)：只描述当前能力事实，不决定当前 Gate。
- [TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md)：append-only evidence ledger，不参与当前阶段判定。
- `docs/gates/**` 与 `docs/archive/**`：historical evidence，不覆盖本文件。

## 4. 下一允许动作

下一允许动作精确为 `NQ-GATEV-FREEZE-CLOSEOUT-IMPLEMENTATION`。该动作只允许实施 GateV freeze closeout，不表示本轮已经实现 freeze，也不得把 GateV 整体提前写成 accepted、frozen、archived 或 tagged。
