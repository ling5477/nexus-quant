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
work_batch=GateW-FREEZE
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION
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
- GateW-FREEZE：`NOT STARTED`（未开始）。该 work batch 仅完成 governance initialization；GateW 尚未 archive、freeze 或 tag。`NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION` 必须把 Freeze readiness review 作为内部第一道 hard gate，并明确裁决真实 OKX read-only soak `NOT_RUN / CREDENTIAL_REQUIRED` 是否阻断 freeze。
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
- Python ML readiness / Python live execution readiness：`NO`（否）。
- `acknowledge`、`escalate`、`resolve`、`close` 只表示本地人工诊断复核；不构成交易授权、LIVE/Shadow 放行，亦不批准下单、撤单、转账或提现。

## 4. 下一允许动作

治理 authority 中下一动作精确为 `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`。该任务必须先执行 Freeze readiness review，再依据 archive manifest、current authority、doc links、真实 read-only soak residual 与完整 evidence 决定是否可以形成 pre-tag freeze candidate；本状态不表示 GateW 已冻结、归档或打 tag，也不授权 Controller、scheduler、network、credential、LIVE 或交易写侧。
