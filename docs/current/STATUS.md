# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateY
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatey-freeze
last_frozen_gate_commit=72fbf5e78f217a02b572a54fadb17dea204b594f
active_gate=GateAUDIT
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=a12ec821fee9dcadaa11428f1db0a065614fb58b
accepted_batch_acceptance_head=a12ec821fee9dcadaa11428f1db0a065614fb58b
accepted_batch_ci_run=33615809848
work_batch=GateAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-COMMIT
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
- GateAUDIT Phase5A：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`d1d20f4087cd337e0b21037b38b377bcbe25499f / 33505000903`，exact-head CI=`completed / success / 9 of 9`，blocking P0/P1=`0/0`。P5-F001=`LOCAL_REQUIRED_CHECK_BASELINE_ACCEPTED / REMOTE_ENFORCEMENT_NOT_APPLIED`；P5-F004与P5-F006=`ACCEPTED / CLOSED`；P5-F005=`INTERNAL_SBOM_PROVENANCE_ACCEPTED`，platform attestation仍为`DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION / id-token NOT_GRANTED`。`IMAGE_DIGEST_RUNTIME_PULL_PENDING_EXACT_HEAD_CI`与`CRITICAL_E2E_ADMISSION_PENDING_FIXTURE_REPAIR`已由该exact-head CI关闭；remote required checks仍未应用或验证。
- GateAUDIT Phase5B：`ACCEPTED / CI_GREEN`；immutable technical pair=`a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848`，tree=`40421839abdb44ebd5e934add03fba85d78feab6`，exact-head CI=`9/9 SUCCESS / failed 0 / skipped 0`。Canonical release=`nq-a12ec821fee9-a9a98236663bba0b / COMMITTED_CLEAN / deployable=true / authorizationEligible=true`，build→external admission→verify→install→activate→active verification全部成功；PostgreSQL 16.15 current-schema restore至V46、pending=0、backup integrity、Flyway validate、repository/app-context smoke均通过，PG17 wrong-major提前拒绝。Critical E2E current baseline=`5 specs / 27 cases`（loopback=`25/25`、real-backend=`2/2`），Idempotency-Key fail-closed实际执行并通过。P5-F002与P5-F003=`ACCEPTED / CLOSED`；P5-F008=`REVIEW_ACCEPTED / READY_TO_COMMIT`；P5-F007/P5-F009继续`OPEN / NOT_IMPLEMENTED`。remote enforcement仍`NOT_APPLIED / NOT_VERIFIED`，platform attestation仍`DEFERRED`。

- F008 Remediation Attempt-01：Formal Review的P1-01/P1-02/P1-03/P2-01均为`REMEDIATED_PENDING_INDEPENDENT_REVIEW`；生产profile只允许`{prod}`，JWT/credential master key必须通过effective-property校验，CI直接约束五项prod YAML无fallback。111项目标测试、PG16 full Maven、双smoke和84项CI mutation通过；下一步是Formal Review Attempt-02，尚无commit/CI acceptance。

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

- 下一允许 machine action 是 `NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-COMMIT`：独立 Final Closure Review 已通过，按 reviewed functional fingerprint 精确提交 F008 candidate。人工 work order `NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-COMMIT-AND-EXACT-HEAD-CI` 仍须继续 push 与 exact-head CI；本轮不写 P5-F008 CLOSED，正式 authority acceptance 留给 CI green 后的独立 post-CI task。保持既有安全边界与 P5-F007/P5-F009、Phase6 的后置状态。
