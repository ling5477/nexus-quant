# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateX
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatex-freeze
last_frozen_gate_commit=299ab30bd2e243314be2dc609cb244cd5388027b
active_gate=GateY
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateY-6F
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=8e3dd0cf6104eb85f36a0e434ca51ea9d903705a
accepted_batch_acceptance_head=8e3dd0cf6104eb85f36a0e434ca51ea9d903705a
accepted_batch_ci_run=32978280738
work_batch=GateY-FREEZE
work_batch_status=ACCEPTED|CI_GREEN|FREEZE_READY
work_batch_commit=8e3dd0cf6104eb85f36a0e434ca51ea9d903705a
work_batch_ci_run=32978280738
next_action=NQ-GATEY-FREEZE-CLOSEOUT
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

- GateX：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 为 [../gates/gate-x/README.md](../gates/gate-x/README.md)，tag=`nq-gatex-freeze`。
- GateY：`IN PROGRESS / NOT FROZEN`（进行中 / 未冻结）；当前仅等待 freeze closeout、freeze commit exact-head CI 与 annotated tag。
- GateY-6F：`ACCEPTED / CI GREEN / MINIMAL LIVE PILOT VERIFIED`（已接受 / CI 已通过 / 最小实盘 pilot 已验证）；production pilot release=`8e3dd0cf6104eb85f36a0e434ca51ea9d903705a`，CI run=`32978280738 / completed / success / 10 jobs`。
- GateY-FREEZE：`ACCEPTED / CI GREEN / FREEZE READY`（已接受 / CI 已通过 / 冻结准备就绪）；strict archive candidate 为 [../gates/gate-y/README.md](../gates/gate-y/README.md)，tag=`nq-gatey-freeze` 仍为 `TAG PENDING`。
- Final authority/document baseline=`65caaf7fd3038658b0f4f24566efd2960e606d43`，exact-head CI run=`32981327378 / completed / success / 10 jobs / bad=0`。

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
current_gate_tag=nq-gatex-freeze
updated_commit=299ab30bd2e243314be2dc609cb244cd5388027b
```

## 4. 安全与运行边界

- LIVE=`DISABLED`，kill switch=`ENGAGED`，activeLease=0，runtime stopped。
- 禁止再次 pilot、PLACE、CANCEL、第二订单、transfer、withdraw 或重新 DISENGAGE kill。
- 禁止修改生产订单、Trade/Ledger、lease/session/authority、生产数据库业务事实、credential、OKX 权限或重新部署 pilot runtime。
- Shadow trading 未启用；AI、DH runtime 与 Integration runtime 未开始。NQ-only 任务不声明 DH current authority。

## 5. 下一允许动作

- 当前唯一治理动作是 `NQ-GATEY-FREEZE-CLOSEOUT`；只允许 archive/checker/commit/push/exact-head CI/tag/post-tag authority sync，不得执行第二笔真实 pilot。
