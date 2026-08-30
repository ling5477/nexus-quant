# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateY
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatey-freeze
last_frozen_gate_commit=72fbf5e78f217a02b572a54fadb17dea204b594f
active_gate=GateAUDIT
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateY-6F
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=8e3dd0cf6104eb85f36a0e434ca51ea9d903705a
accepted_batch_acceptance_head=8e3dd0cf6104eb85f36a0e434ca51ea9d903705a
accepted_batch_ci_run=32978280738
work_batch=GateAUDIT-0C-R3-DOC-LINK-LINUX-REMEDIATION
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-0C-R3-COMMIT
production_soak=COMPLETED
kill_switch=ENGAGED
live=DISABLED
shadow_trading=NOT_ENABLED
ai=NOT_STARTED
dh_runtime=NOT_INTEGRATED
integration_runtime=NOT_STARTED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
nq-current-authority:end -->

`docs/current/STATUS.md` 是 NexusQuant 当前阶段状态的唯一 authority。其他 current 文档只能引用或解释本文件。

## 1. 当前阶段

- GateY：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 为 [../gates/gate-y/README.md](../gates/gate-y/README.md)，freeze commit=`72fbf5e78f217a02b572a54fadb17dea204b594f`，annotated tag=`nq-gatey-freeze`，tag object=`c84f412e1da652e85158c5478997945d3065e575`，peeled commit 与 freeze commit 一致。
- GateAUDIT：`IN PROGRESS / NOT FROZEN`（治理进行中 / 未冻结）；R3 cross-platform remediation commit=`99c976306fb4c645251847c35ecf8c09f194b05d` 的 exact-head CI run=`33164682651 / completed / failure`。Linux CI 已关闭此前 authority fixture 与 Java verifier 两个 P1，并暴露 doc-link checker 无法读取 hidden `.agents` root；该 doc-link remediation 的独立复核现已接受，状态=`REVIEW_ACCEPTED / READY_TO_COMMIT`，新 commit=`NONE`、CI=`NOT_RUN`；Phase 0 尚未完成，Phase 1 全仓 audit 尚未开始。
- GateY-6F：`ACCEPTED / CI GREEN / MINIMAL LIVE PILOT VERIFIED`（已接受 / CI 已通过 / 最小实盘 pilot 已验证）；production pilot release=`8e3dd0cf6104eb85f36a0e434ca51ea9d903705a`，CI run=`32978280738 / completed / success / 10 jobs`。
- GateY-FREEZE：`ACCEPTED / CI GREEN / TAGGED`（已接受 / CI 已通过 / 已打 tag）；exact-head CI run=`33037514013 / completed / success / 11 jobs / bad=0`，archive/release post-tag checker errors=0。
- GateAUDIT-0C-R3-DOC-LINK-LINUX-REMEDIATION：`REVIEW_ACCEPTED / READY_TO_COMMIT`（独立复核已接受 / 可以进入提交准备）；decision=`PASS / REVIEW_ACCEPTED / READY_TO_COMMIT`，P0=0、P1=0，candidate modified by review=`NO`，independence violation=0；root cause=`POWERSHELL_PROVIDER_HIDDEN_ITEM_PORTABILITY`，显式 root 与递归枚举均使用 `-Force`，默认 `.agents` blocking root、missing-root fail-closed 与历史 warning policy 保持不变；commit=`NONE`，CI=`NOT_RUN`。

## 2. Accepted pilot facts

- Scope：单账户、单 credential、OKX Spot、BTC-USDT、BUY LIMIT、pilot cap `<= 10 USDT`、人工受控。
- Execution：PLACE=1、PLACE retry=0、CANCEL=0、Attempt-02=`NOT_CREATED`、second PLACE=`NOT_EXECUTED`。
- Reconciliation：Order=`FILLED/LIVE`、Intent=`RECONCILED`、Receipt=`QUERY_CONFIRMED`、Trade=1、Ledger entries=4。
- Terminal：Lease=`CLOSED`、activeLease=0、Session=`LIVE_RECONCILED`、Authority=`CLOSED`、kill=`ENGAGED`、LIVE=false、runtime stopped、Transfer=0、Withdraw=0。
- Residual：`P2 / ORDER_VENUE_IDENTITY_MODEL_CONSISTENCY_RESIDUAL`；`Order.externalOrderId=NULL`，不阻断 freeze，本轮不得修改生产事实或代码清零。

## 3. Archive Compatibility Verification

以下字段只供最近已冻结 Gate 的 archive checker 校验，不属于 `nq-current-authority` schema：

```text
current_gate_status=FROZEN|ACCEPTED|TAGGED
current_gate_tag=nq-gatey-freeze
updated_commit=72fbf5e78f217a02b572a54fadb17dea204b594f
```

## 4. 安全与运行边界

- LIVE=`DISABLED`，kill switch=`ENGAGED`，activeLease=0，runtime stopped。
- 禁止再次 pilot、PLACE、CANCEL、第二订单、transfer、withdraw 或重新 DISENGAGE kill。
- 禁止修改生产订单、Trade/Ledger、lease/session/authority、生产数据库业务事实、credential、OKX 权限或重新部署 pilot runtime。
- Shadow trading 未启用；AI、DH runtime 与 Integration runtime 未开始。NQ-only 任务不声明 DH current authority。

## 5. 下一允许动作

- 当前唯一治理动作是 `NQ-GATEAUDIT-0C-R3-COMMIT`。不得再次启动 review 或修改 remediation candidate；后续 remediation commit 的 exact-head CI green 前不得进入 Phase 1、改写 GateY frozen evidence 或扩大真实交易能力。
