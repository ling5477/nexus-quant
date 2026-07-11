# Current Status

<!-- nq-current-authority:start
authority_schema=2
last_frozen_gate=GateU
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gateu-freeze
last_frozen_gate_commit=48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab
active_gate=GateV
active_gate_status=IN_PROGRESS|NOT_FROZEN
active_batch=GateV-1
active_batch_status=ACCEPTED|CI_GREEN
active_batch_implementation_commit=f7d71d5a80241ade049a83fa3f90b3ac6ce46806
active_batch_acceptance_head=b3dd5f74f154d5ed9e2343bc18e451f48770814f
active_batch_ci_run=29144345430
next_action=NQ-GATEV-2-OPERATOR-REVIEW-LIFECYCLE-API-IMPLEMENTATION
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
- GateV-2：`NOT STARTED`（未开始）。

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

下一允许动作：`NQ-GATEV-2-OPERATOR-REVIEW-LIFECYCLE-API-IMPLEMENTATION`。GateV-2 仍为 `NOT STARTED`，只能由独立实现任务启动；不得把 GateV 整体写成 accepted、frozen 或 tagged。
