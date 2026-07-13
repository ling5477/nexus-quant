# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateV
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatev-freeze
last_frozen_gate_commit=530ce4e2bde416aa61944262cbfbadca556656cb
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553
accepted_batch_acceptance_head=6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553
accepted_batch_ci_run=29230512781
work_batch=GateW-3
work_batch_status=COMMITTED|CI_FAILED|FIX_REQUIRED
work_batch_commit=54c7bdd2caee5602441ce983b33c4cd2466ee263
work_batch_ci_run=29253811976
next_action=NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH
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
- GateW：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；[GateW planning baseline](GATEW_PLAN.md)、GateW-1 capability/guard 与 GateW-2 private read-only diagnostic probe 均已获 exact-HEAD CI 接受。GateW-3 venue-rule facts 为 `COMMITTED / CI FAILED / FIX REQUIRED`（已提交 / CI 已失败 / 必须修复），machine exact status 为 `COMMITTED|CI_FAILED|FIX_REQUIRED`；GateW 尚未冻结。
- GateW-PLAN：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `5661a13e236ce067edad9ae5789c97ae3ae2e7bb`，`NQ CI Baseline` run `29199785253` 为 `completed / success`。
- GateW-1：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `31c8171df26bc1eb9f93da19cf0576c0ac48116b`，`NQ CI Baseline` run `29219687588` 为 `completed / success`。该批次只建立 typed capability matrix、default-deny endpoint guard 与 GateW profile Bean 边界。
- GateW-2：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；implementation/acceptance head 为 `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553`，`NQ CI Baseline` run `29230512781` 为 `completed / success`。该接受只覆盖两个 typed private read-only diagnostic operation；`REAL_SMOKE=NOT_RUN`，不表示远端 permission 已验证、LIVE 或交易授权。
- GateW-3：`COMMITTED / CI FAILED / FIX REQUIRED`（已提交 / CI 已失败 / 必须修复）；venue-rule facts implementation commit `8b54adc6952775dc1a939aad7b0ae849f20f42cf` 与独立 migration conformance review 继续有效。当前 latest committed exact head 仍为 governance hardening commit `54c7bdd2caee5602441ce983b33c4cd2466ee263`，其 `NQ CI Baseline` run `29253811976` 仍为 `completed / failure`。动态 Flyway 校验与 Playwright job/step timeout 60/30 分钟修复已完成独立 review，结论为 `PASS / CI_BLOCKER_FIX_ACCEPTED / READY_TO_COMMIT`（通过 / CI blocker 修复已接受 / 可进入提交前复核），但尚未 commit/push 或取得 fix-commit exact-head CI；因此 batch 尚未 `ACCEPTED|CI_GREEN`。
- GateW-3 dry-run order preview：仍未获实施授权。必须先提交/push 已接受的 CI blocker fix 并取得 fix commit exact-head CI green，再重新执行 security/risk review attempt-02。OKX Spot minimum notional 继续保持 UNKNOWN 或显式 NQ risk rule。

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

治理 authority 中下一动作精确为 `NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH`。本轮 review 已接受工作区的动态 Flyway 与 Playwright timeout 修复，但 fix 尚未 commit/push；提交新的 fix commit 后才可回到 `COMMITTED|CI_PENDING`。当前 committed exact-head CI 仍为 `29253811976 / failure`，GateW-3 尚未 accepted。本轮不授权 order preview、Controller、credential/private endpoint、scheduler、LIVE、交易写侧、GateW-4 或 GateW Freeze。只有 fix commit 的 exact-head CI green 后，GateW-3 才可进入接受同步并重跑 dry-run order preview security/risk review attempt-02。
