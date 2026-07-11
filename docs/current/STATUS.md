# Current Status

<!-- nq-current-authority:start
current_gate=GateU
current_gate_status=FROZEN|ACCEPTED|TAGGED
current_gate_tag=nq-gateu-freeze
next_gate=GateV
next_gate_status=NOT_STARTED
live=DISABLED
shadow_trading=NOT_ENABLED
ai=NOT_STARTED
dh_runtime=NOT_INTEGRATED
integration_runtime=NOT_STARTED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
updated_commit=48ef0cdaa97099ae1ff5a66a8c0caeb07aa11fab
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
- GateV：`PLAN / NOT IMPLEMENTED`（已规划 / 未实现）；唯一 active plan 为 [GATEV_PLAN.md](GATEV_PLAN.md)。

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

本 GateV planning 提交并通过对应 CI 后，下一轮直接执行 `NQ-GATEV-1-DURABLE-REVIEW-FACT-MODEL-MIGRATION-AND-REPOSITORY-IMPLEMENTATION`；不得增加 GateV plan review、plan freeze 或 planning addendum，也不得由本计划自动视为 implementation 已开始。
