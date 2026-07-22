# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateV
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatev-freeze
last_frozen_gate_commit=530ce4e2bde416aa61944262cbfbadca556656cb
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateW-4
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=07b94f89903b0ee62e3ee9d76d31d1a3d9351a7c
accepted_batch_acceptance_head=07b94f89903b0ee62e3ee9d76d31d1a3d9351a7c
accepted_batch_ci_run=29339016784
work_batch=GateW-OKX-READONLY-SOAK-ATTEMPT-09
work_batch_status=RUNNING|PENDING_168H
work_batch_commit=1b501488076fae79e15b84579a02f5c580fa51b3
work_batch_ci_run=29837563573
next_action=NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-168H-ACCEPTANCE
live=DISABLED
shadow_trading=NOT_ENABLED
ai=NOT_STARTED
dh_runtime=NOT_INTEGRATED
integration_runtime=NOT_STARTED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
nq-current-authority:end -->

`docs/current/STATUS.md` 是 NexusQuant 当前阶段状态的唯一 authority。其他 current 文档只能引用或解释本文件，不得复制独立的 current Gate / next Gate 判定。

## 1. 当前阶段

- GateV：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。release tag 为 `nq-gatev-freeze`；annotated tag object 为 `06d5fea2af1765f143f277b111358b3abd8171ce`；peeled commit 为 `530ce4e2bde416aa61944262cbfbadca556656cb`。
- GateV-FREEZE：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；freeze candidate、implementation commit 与 acceptance head 均为 `7117bb0abc2113c0957ce9c4a0d7c2b57320b1a6`，`NQ CI Baseline` run `29191014596` 为 `completed / success`。
- GateV release closeout exact-HEAD CI：`NQ CI Baseline` run `29191677441`，`completed / success`，`headSha=530ce4e2bde416aa61944262cbfbadca556656cb`。
- GateV durable archive：[../gates/gate-v/README.md](../gates/gate-v/README.md)。它是历史证据，不覆盖本 authority。
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；GateW-4 operational safety implementation/acceptance head `07b94f89903b0ee62e3ee9d76d31d1a3d9351a7c` 的 exact-head run `29339016784` 已 `completed / success`，10 个实际 jobs 全部成功。GateW-4 已整体 `ACCEPTED / CI GREEN`（已接受 / CI 已通过）；GateW 尚未冻结。
- GateW-PLAN：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `5661a13e236ce067edad9ae5789c97ae3ae2e7bb`，`NQ CI Baseline` run `29199785253` 为 `completed / success`。
- GateW-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `31c8171df26bc1eb9f93da19cf0576c0ac48116b`，`NQ CI Baseline` run `29219687588` 为 `completed / success`。该批次只建立 typed capability matrix、default-deny endpoint guard 与 GateW profile Bean 边界。
- GateW-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553`，`NQ CI Baseline` run `29230512781` 为 `completed / success`。该接受只覆盖两个 typed private read-only diagnostic operation；`REAL_SMOKE=NOT_RUN`，不表示远端 permission 已验证、LIVE 或交易授权。
- GateW-3：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。venue-rule facts、LIMIT-only order preview、bounded read-only reconciliation 与 diagnostic risk preflight 的独立 review 均 P0=0/P1=0，四个 acceptance heads 的 exact-head CI 均成功。implementation/acceptance head 为 `178b4951ba1406748170022c9940f84beaa8ab81`，run `29332316101`。
- GateW-4：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）。Blocker-1、operations、persistence/retention、human-review evidence binding、disposable backup/restore、11 场景 incident drill 与 10,000 次 local no-egress soak hard gates 均通过；internal-only assessment 不产生交易授权。Implementation/acceptance head 为 `07b94f89903b0ee62e3ee9d76d31d1a3d9351a7c`，CI run `29339016784` 为 exact-head `completed / success`。
- GateW soak start contract remediation：`PASS / COMMITTED / CI GREEN / SUPERSEDED BY PRECREATE REMEDIATION`（通过 / 已提交 / CI 已通过 / 已由 pre-create remediation 替代）。Implementation commit `0e8e2c128c456542b3f7695c9620e4d170c3f4f6` 的 exact-head CI run `29766800343` 为 `completed / success / 10 of 10`；该 release 保留为已验证回滚点，不再是服务器 current。
- GateW pre-create sanitized prerequisite remediation：`PASS / IMPLEMENTATION COMMITTED / IMPLEMENTATION CI GREEN / SERVER DEPLOYED`（通过 / 实现已提交 / 实现 CI 已通过 / 服务器已部署）。Implementation commit `1b501488076fae79e15b84579a02f5c580fa51b3` 的 exact-head CI run `29837563573` 为 `completed / success / 10 of 10`；服务器 `/opt/nexus-quant/current` 固定到该 `EXACT_COMMIT` immutable release，129 artifacts、manifest/POSIX、root owner/mode、`nqgatewWritable=false`、systemd verify、sanitized pre-create 与完整 final offline acceptance 均通过。
- GateW-OKX-READONLY-SOAK-ATTEMPT-09：`RUNNING / PENDING 168H`（运行中 / 待满 168 小时）。唯一 run `gatew-soak-20260722T111144Z-ac00f878` 使用正式 systemd unit 与 immutable runtime Commit A；首次 OKX config/balance typed GET、fresh SSH same MainPID、heartbeat 推进、hash chain、zero forbidden/raw/secret 与 kill switch `ENGAGED` 均通过。真实 acceptance clock 为 `2026-07-22T11:19:59.5201964Z` 至 `2026-07-29T11:19:59.5201964Z`；当前只表示 soak 正在运行，不表示 168 小时已接受。
- GateW-FREEZE：`NOT STARTED`（未开始）。GateW 尚未 archive、freeze 或 tag；必须等待真实 read-only soak 后续证据，不得从 remediation 的隔离 offline clock 推导真实 168 小时 acceptance 已开始。
- GateW-3 dry-run order preview：只包含 OKX Spot、BUY/SELL、LIMIT、internal application、local persisted facts、read-only diagnostic；minimum notional、fee、远端 permission 与 runtime balance/risk 继续保持显式 UNKNOWN / NOT_EVALUATED，`executionReadiness=BLOCKED`，不得推导交易授权。
- GateW-3 read-only reconciliation：只包含 OKX Spot、最多 3 个 allowlisted symbols、1 page/100 records/24h typed private `Read` snapshot、bounded local SELECT 与 pure comparator；默认不装配，无 real smoke/credential/network/repair/persistence/scheduler，`executionReadiness=BLOCKED`。CI acceptance 只接受该 side-effect-free contract，不证明真实 permission 或账户健康。
- GateW-3 risk preflight：只消费 immutable preview/reconciliation result 与显式 local metadata snapshots；不调用 `PreTradeRiskService`/registry/stateful rules，不构造 `PlaceOrderCommand`，无 DB/network/write。minimum notional、fee、remote permission 保持 UNKNOWN，stateful risk/balance/position 等保持 NOT_EVALUATED，`executionReadiness=BLOCKED`、`tradingAuthorized=false`。

## 2. Archive Compatibility Verification

以下三项只供已冻结 archive checker 校验 GateV tag 事实，不属于 `nq-current-authority` schema，也不将 GateW 写成 tagged：

```text
current_gate_status=FROZEN|ACCEPTED|TAGGED
current_gate_tag=nq-gatev-freeze
updated_commit=530ce4e2bde416aa61944262cbfbadca556656cb
```

## 3. 安全与运行边界

- LIVE：`DISABLED`（关闭）。
- Shadow trading：`NOT ENABLED`（未启用）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration runtime：`NOT STARTED`（未开始）。
- RealClient / private trading adapter：`NOT IMPLEMENTED`（未实现）；GateW-2 private read-only diagnostic transport/probe 为 `ACCEPTED / CI GREEN`，默认不装配且未做 real smoke，不属于交易适配器或交易授权。
- GateW runtime release：`1b501488076fae79e15b84579a02f5c580fa51b3`；该值是服务器当前 immutable runtime，不因本次 docs/evidence Commit B 改变。
- Attempt-09：`RUNNING / PENDING 168H`（运行中 / 待满 168 小时）；正式 unit=`active/running`，worker=`nqgatew`，MainPID=`4074358`，kill switch=`ENGAGED`。真实 acceptance clock 已从 `2026-07-22T11:19:59.5201964Z` 原子启动，planned=`2026-07-29T11:19:59.5201964Z`；仅允许 config/balance 两个 typed GET，LIVE、下单、撤单、转账与提现继续关闭。
- Python ML readiness / Python live execution readiness：`NO`（否）。
- `acknowledge`、`escalate`、`resolve`、`close` 只表示本地人工诊断复核；不构成交易授权、LIVE/Shadow 放行，亦不批准下单、撤单、转账或提现。

## 4. 下一允许动作

治理 authority 中唯一下一动作精确为 `NQ-GATEW-OKX-READONLY-SOAK-ATTEMPT-09-168H-ACCEPTANCE`。在 planned acceptance 时间前只能保持健康只读 soak 与安全监控，不得提前执行最终验收、freeze/archive/tag，也不得因 docs/CI 问题停止健康 unit；该动作不授权 LIVE、订单、撤单、转账、提现、allowlist 修改、credential 输出或任何交易写侧。
