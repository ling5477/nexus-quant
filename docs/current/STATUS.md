# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateY
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatey-freeze
last_frozen_gate_commit=72fbf5e78f217a02b572a54fadb17dea204b594f
active_gate=GateAUDIT
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateAUDIT-PHASE4-REMAINING-DISPOSITION-AND-CONSOLIDATION
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=7ca1fc92f8900e3e9d19184fccd40569f233823f
accepted_batch_acceptance_head=7ca1fc92f8900e3e9d19184fccd40569f233823f
accepted_batch_ci_run=33405549149
work_batch=GateAUDIT-PHASE5A-CANONICAL-CI-AND-SUPPLY-CHAIN
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE5A-CANONICAL-DELIVERY-IMPLEMENTATION
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
- GateAUDIT：`IN PROGRESS / NOT FROZEN`（治理进行中 / 未冻结）；Phase 0=`ACCEPTED / CI_GREEN / COMPLETE`，immutable acceptance pair=`40e1077e1fe735a3d250f094caaa24e437e8ea3f / 33306024232`。Phase 1 inventory、Phase 2 AS-IS analysis 与 Phase 3 disposition 均已完成；这些是 `AUDIT / ANALYSIS / DISPOSITION` facts，不是 CI-validated implementation。
- GateY-6F：`ACCEPTED / CI GREEN / MINIMAL LIVE PILOT VERIFIED`（已接受 / CI 已通过 / 最小实盘 pilot 已验证）；production pilot release=`8e3dd0cf6104eb85f36a0e434ca51ea9d903705a`，CI run=`32978280738 / completed / success / 10 jobs`。
- GateY-FREEZE：`ACCEPTED / CI GREEN / TAGGED`（已接受 / CI 已通过 / 已打 tag）；exact-head CI run=`33037514013 / completed / success / 11 jobs / bad=0`，archive/release post-tag checker errors=0。
- GateAUDIT-0C-R3-DOC-LINK-LINUX-REMEDIATION：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；immutable acceptance pair=`40e1077e1fe735a3d250f094caaa24e437e8ea3f / 33306024232`，blocking jobs=`11/11 SUCCESS`。Linux CI 已关闭 P1-01 authority fixture、P1-02 Java verifier 与 doc-link hidden-root portability finding；P0=0、P1=0。
- GateAUDIT Phase 3：`COMPLETE / READY_FOR_PHASE4`；正式 findings=`P0 0 / P1 4 / P2 8 / P3 1`。Tier A F-001～F-004 均已接受；剩余 capability gaps 已进入 Phase4 closeout disposition，不把 Phase5/Phase6 后置能力写成当前已实现。
- GateAUDIT-PHASE4-L3-PROOF-FOUNDATION：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`95b859ee61a8e7f0a725e29877e7303ea4453b1a / 33347091147`，exact-head CI=`11/11 SUCCESS`。F-001 不在本任务重新 Review。
- GateAUDIT-PHASE4-F004-TRADE-LEDGER-CONVERGENCE：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`18efc06c380d2b411ba7d5f651e7e441247a1b96 / 33358364678`，exact-head CI=`11/11 SUCCESS`。
- GateAUDIT-PHASE4-F002-RESTART-PROOF-FOUNDATION：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`0651a7365d1a6afe453d75c8abd3975d458e0b7a / 33387882472`。R1/R2 forked-JVM proof 与 CI datasource binding remediation 已由 exact-head CI 接受，且不等于 Phase6 full L4 qualification。
- GateAUDIT-PHASE4-F003-ORDER-EXECUTION-IDENTITY-CONVERGENCE：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`327c2229e89c076eace60046b79ec02c622a7fe4 / 33399190770`，exact-head CI=`11/11 SUCCESS`。ordinary Order 是唯一 execution fact，ExecutionIntent 只编排已存在 Order 的外部动作。
- GateAUDIT-PHASE4-REMAINING-DISPOSITION-AND-CONSOLIDATION：`COMPLETE / ACCEPTED / CI_GREEN`；immutable acceptance pair=`7ca1fc92f8900e3e9d19184fccd40569f233823f / 33405549149`，exact-head CI=`11/11 SUCCESS`，blocking P0/P1=`0/0`。该 pair 是 Phase4 capability acceptance authority，不由后续 current-fact synchronization commit/CI替代。
- GateAUDIT Phase5A：`READY_TO_START / NOT_IMPLEMENTED`；workstream=`NQ-GATEAUDIT-PHASE5A-CANONICAL-CI-AND-SUPPLY-CHAIN`。Phase5 inventory seed=`P5-F001～P5-F009 / P1 3 / P2 6`；仅登记缺口，未实施 CI、release、deployment、restore、observability、E2E 或 supply-chain change。Legacy Phase3 IDs F-005/F-011因canonical identity不可恢复而`RETIRED`，Phase5不继承其未知语义。

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

- 当前唯一治理动作是 `NQ-GATEAUDIT-PHASE5A-CANONICAL-DELIVERY-IMPLEMENTATION`，对应 workstream `NQ-GATEAUDIT-PHASE5A-CANONICAL-CI-AND-SUPPLY-CHAIN`。该 action token 避免被现有 matcher 因名称中的 `CI` 误分类为 CI lifecycle action；不得据此宣称 Phase5 已实现，也不得启动 Phase6、再次pilot或扩大LIVE能力。
