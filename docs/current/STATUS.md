# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateV
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatev-freeze
last_frozen_gate_commit=530ce4e2bde416aa61944262cbfbadca556656cb
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateW-1
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=31c8171df26bc1eb9f93da19cf0576c0ac48116b
accepted_batch_acceptance_head=31c8171df26bc1eb9f93da19cf0576c0ac48116b
accepted_batch_ci_run=29219687588
work_batch=GateW-2
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-2-COMMIT-AND-PUSH
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
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；[GateW planning baseline](GATEW_PLAN.md) 与 GateW-1 capability/guard 已获 exact-HEAD CI 接受，GateW-2 security review baseline 和实际 diff conformance review 均已通过，当前为 `REVIEW ACCEPTED / READY TO COMMIT`（复核已接受 / 可进入提交前复核）。
- GateW-PLAN：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `5661a13e236ce067edad9ae5789c97ae3ae2e7bb`，`NQ CI Baseline` run `29199785253` 为 `completed / success`。
- GateW-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `31c8171df26bc1eb9f93da19cf0576c0ac48116b`，`NQ CI Baseline` run `29219687588` 为 `completed / success`。该批次只建立 typed capability matrix、default-deny endpoint guard 与 GateW profile Bean 边界。
- GateW-2：`REVIEW ACCEPTED / READY TO COMMIT`（复核已接受 / 可进入提交前复核）。Pre-implementation security review commit `2c7def771b8779c16b98810f09e5758161242ed6` 的 exact-HEAD CI run `29222532638` 为 `completed / success`；本轮 worktree implementation 已完成实际 diff security conformance review，P0=0、P1=0，但仍未提交、CI 未运行，且不表示 real smoke、LIVE 或交易授权。

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
- RealClient / private trading adapter：`NOT IMPLEMENTED`（未实现）；GateW-2 private read-only diagnostic transport/probe 为 `REVIEW ACCEPTED / READY TO COMMIT`，默认不装配且未做 real smoke。
- Python ML readiness / Python live execution readiness：`NO`（否）。
- `acknowledge`、`escalate`、`resolve`、`close` 只表示本地人工诊断复核；不构成交易授权、LIVE/Shadow 放行，亦不批准下单、撤单、转账或提现。

## 4. 下一允许动作

治理 authority 中下一允许动作精确为 `NQ-GATEW-2-COMMIT-AND-PUSH`；machine contract 将 `REVIEW_ACCEPTED|READY_TO_COMMIT` 映射到 `COMMIT_AND_PUSH`，该 action 受 checker 支持。下一轮只精确提交并 push 已接受的 GateW-2 diff，等待 exact-HEAD CI；不得初始化 GateW-3。
