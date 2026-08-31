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
work_batch=GateAUDIT-PHASE4-F003-ORDER-EXECUTION-IDENTITY-CONVERGENCE
work_batch_status=IMPLEMENTED|SELF_REVIEWED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE4-F003-ORDER-EXECUTION-IDENTITY-CONVERGENCE-COMMIT
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
- GateAUDIT Phase 3：`COMPLETE / READY_FOR_PHASE4`；正式 findings=`P0 0 / P1 4 / P2 8 / P3 1`。Tier A 为 F-001～F-004；F-001/F-002/F-004 已接受，F-003 当前按 `PROVE_FIRST` 执行 identity convergence proof。
- GateAUDIT-PHASE4-L3-PROOF-FOUNDATION：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`95b859ee61a8e7f0a725e29877e7303ea4453b1a / 33347091147`，exact-head CI=`11/11 SUCCESS`。F-001 不在本任务重新 Review。
- GateAUDIT-PHASE4-F004-TRADE-LEDGER-CONVERGENCE：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`18efc06c380d2b411ba7d5f651e7e441247a1b96 / 33358364678`，exact-head CI=`11/11 SUCCESS`。
- GateAUDIT-PHASE4-F002-RESTART-PROOF-FOUNDATION：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`0651a7365d1a6afe453d75c8abd3975d458e0b7a / 33387882472`。R1/R2 forked-JVM proof 与 CI datasource binding remediation 已由 exact-head CI 接受，且不等于 Phase6 full L4 qualification。
- GateAUDIT-PHASE4-F003-ORDER-EXECUTION-IDENTITY-CONVERGENCE：`PROVEN / SELF_REVIEWED / READY_TO_COMMIT`；ordinary Order 是唯一 execution fact，ExecutionIntent 是仅由 conditional minimal-pilot gateway 创建的外部动作 orchestration fact，不创建 Order。PostgreSQL 17.7 / Flyway V46 证明 retry、repository restart、4路并发与独立动作均保持 identity convergence；production defect=`0`，production Java/migration/external side effects=`0/0/0`。

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

- 当前唯一治理动作是 `NQ-GATEAUDIT-PHASE4-F003-ORDER-EXECUTION-IDENTITY-CONVERGENCE-COMMIT`。只允许精确提交已通过 self-review 的 test-only proof 与最小 current-doc 同步；不得修改 production Java/migration、启动 Phase6、再次 pilot 或扩大 LIVE 能力。
