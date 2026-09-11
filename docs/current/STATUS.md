# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateY
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatey-freeze
last_frozen_gate_commit=72fbf5e78f217a02b572a54fadb17dea204b594f
active_gate=GateAUDIT
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateAUDIT-PHASE6-L4
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=3d103cea2072b3c2d9d1009cc5841c18a958ee80
accepted_batch_acceptance_head=3d103cea2072b3c2d9d1009cc5841c18a958ee80
accepted_batch_ci_run=34501806297
work_batch=NQ-GATEAUDIT-PHASE6-L5-L6-SCOPE-AND-QUALIFICATION-PLAN
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE6-L5-L6-SCOPE-AND-QUALIFICATION-PLAN
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
- GateAUDIT Phase5A：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`d1d20f4087cd337e0b21037b38b377bcbe25499f / 33505000903`，exact-head CI=`completed / success / 9 of 9`，blocking P0/P1=`0/0`。P5-F001=`ACCEPTED / CLOSED`（local baseline与后续remote enforcement均已接受）；P5-F004与P5-F006=`ACCEPTED / CLOSED`；P5-F005=`INTERNAL_SBOM_PROVENANCE_ACCEPTED`，platform attestation仍为`DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION / id-token NOT_GRANTED`。`IMAGE_DIGEST_RUNTIME_PULL_PENDING_EXACT_HEAD_CI`与`CRITICAL_E2E_ADMISSION_PENDING_FIXTURE_REPAIR`已由该exact-head CI关闭；remote required checks现已由ruleset `22381941`在`refs/heads/dev`生效并接受，effective checks=9/9。
- GateAUDIT Phase5B：`ACCEPTED / CI_GREEN`；immutable technical pair=`a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848`，tree=`40421839abdb44ebd5e934add03fba85d78feab6`，exact-head CI=`9/9 SUCCESS / failed 0 / skipped 0`。Canonical release=`nq-a12ec821fee9-a9a98236663bba0b / COMMITTED_CLEAN / deployable=true / authorizationEligible=true`，build→external admission→verify→install→activate→active verification全部成功；该历史Phase5B pair的PostgreSQL 16.15 schema restore至V46、pending=0、backup integrity、Flyway validate、repository/app-context smoke均通过，PG17 wrong-major提前拒绝。Critical E2E current baseline=`5 specs / 27 cases`（loopback=`25/25`、real-backend=`2/2`），Idempotency-Key fail-closed实际执行并通过。P5-F002与P5-F003=`ACCEPTED / CLOSED`；P5-F008=`ACCEPTED / CLOSED`；P5-F007为`ACCEPTED / CLOSED`，P5-F009为`ACCEPTED / CLOSED`。remote enforcement现为`APPLIED / VERIFIED / ACCEPTED`（ruleset `22381941`，`refs/heads/dev`），platform attestation仍`DEFERRED`。

- GateAUDIT Phase5 F008：`ACCEPTED / CLOSED`（已接受 / 已关闭）；Final Closure Review=`PASS / P0_0 / P1_0`，implementation commit=`716199a7cb836a5eaf43a88b0de6db0f47a75e91`，immutable technical acceptance pair=`614359fc7f25227f736fbb1c11c7d584da1f0627 / 33978394774`，exact-head CI=`completed / success / 9 of 9 / failed 0 / skipped 0`。`614359fc7f25227f736fbb1c11c7d584da1f0627`属于`accepted CI test-harness compatibility remediation`，不构成新的F008 implementation finding。Mandatory Maven与YAML semantic validator通过，mutations=`135 REJECTED / 0 ACCEPTED`，R06/R09/R10均拒绝；本轮只接受已有review/CI证据，不重跑技术测试。详见[post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F008_POST_CI_AUTHORITY_ACCEPTANCE.md)。历史失败与remediation attempts保留在原evidence和append-only ledgers。

- GateAUDIT Phase5 F007：`ACCEPTED / CLOSED`（已接受 / 已关闭）；implementation与accepted technical head均为`0e2efdeb236c185dbace67bb22f94c6af64a563a`，immutable technical acceptance pair=`0e2efdeb236c185dbace67bb22f94c6af64a563a / 34009290836`，exact-head CI=`completed / success / 9 of 9 / failed 0 / skipped 0`，P0=0、P1=0。Scheduler/worker、reconciliation、ledger recovery与critical alert最小观测均已具备；既有observability测试10/10通过，Full Maven=1783 tests、0 failures/errors、53 conditional test skips；53是测试条件跳过，不是CI job skip。高基数metric tags=0、业务副作用语义变化=0；本轮仅接受既有技术与CI证据，不重跑qualification。详见[post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F007_POST_CI_AUTHORITY_ACCEPTANCE.md)。本轮docs-only authority commit不替代该technical pair。

- GateAUDIT Phase5 F009：`ACCEPTED / CLOSED`（已接受 / 已关闭），local review=`CLOSED / PASS / P0_0 / P1_0`，P1-1/P1-2/P1-3均CLOSED。首次delivery=`85d11984d0c65b464ffe4858fe7fd1da51885f12`、failed CI=`34024011663`保留为失败历史；remediation/accepted technical head=`dbb8b9c6a2319338f5ca90b566ad494142a55e20`、CI=`34024427455 / completed / success / 9 of 9 / failed 0 / skipped 0 / cancelled 0`。本次authority同步不替换technical pair；详见[F009 post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F009_POST_CI_AUTHORITY_ACCEPTANCE.md)。
- GateAUDIT Phase5 F001：`ACCEPTED / CLOSED`（已接受 / 已关闭）；remote enforcement事件为ruleset `22381941`创建，目标=`refs/heads/dev`，required checks=9；本轮只读重新验证ACTIVE、effective=YES、missing/unexpected/duplicate=0/0/0、GitHub Actions app=15368，P0=0、P1=0。正式authority acceptance与此前remote mutation分层，详见[F001 post-remote acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F001_POST_REMOTE_AUTHORITY_ACCEPTANCE.md)。
- Phase5=`ACCEPTED / CLOSED`：total=9，accepted/closed=8（F001/F002/F003/F004/F006/F007/F008/F009），open/unclosed=1（仅F005 deferred/non-blocking），blocked=0、remaining blocking=0；非延期open=0。F005 internal SBOM/provenance已接受，platform attestation继续`DEFERRED / NON_BLOCKING / DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION`，不伪装成CLOSED。既有ROADMAP闭合条件已满足，machine lifecycle无必须关闭全部deferred项的额外条件。
- GateAUDIT Phase6=`IN_PROGRESS / NOT_FROZEN`；Phase6 L4=`ACCEPTED`，B0–B5=`ACCEPTED`，B6=`AGGREGATE_ACCEPTED / AGGREGATE_QUALIFICATION_ACCEPTED`。最终 technical SHA=`3d103cea2072b3c2d9d1009cc5841c18a958ee80`，exact-head CI=`34501806297 / 9 of 9 SUCCESS`；current eligible matrix=`28/28 ACCEPTED / missing=0 / invalid=0`，L4范围P0=0、P1=0。完整接受事实见[B6 aggregate evidence](../audit/evidence/GATEAUDIT_PHASE6_L4_B6_AGGREGATE_QUALIFICATION_ACCEPTANCE.md)。本次docs-only authority同步不替代该technical pair，也不表示整个Phase6或GateAUDIT已结束。

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

- Phase6 L5/L6=`NEXT / NOT_STARTED`；下一正式任务=`NQ-GATEAUDIT-PHASE6-L5-L6-SCOPE-AND-QUALIFICATION-PLAN`。目标为后续规模、故障与长期运行验证；先从当前仓库与既有Phase6 planning evidence解析真实scope、能力复用与实现缺口、测试边界和exit criteria，不预设L5/L6子阶段。
- L4 immutable technical pair=`3d103cea2072b3c2d9d1009cc5841c18a958ee80 / 34501806297`；accepted_batch绑定该pair，不由本次文档提交或其CI替换。不重开L4，不重跑B0–B6 qualification或新增Independent Review。
- 当前repository schema=`V51`，不推断生产schema；历史migration与接受身份保持不变。
- P2 ordinary concurrent INSERT loser、P3 wildcard-import residual均为`OPEN / NON_BLOCKING`；不以L4 P0/P1=0宣称全部问题清零。其他历史非阻断残余不在本次重评，原记录保留于[pre-B0 evidence](../audit/evidence/GATEAUDIT_PHASE6_PRE_B0_CI_SAFETY_CURRENT_AUTHORITY_REMEDIATION.md)。
- P1-1/PB1=`RETIRED_COMPATIBILITY_ONLY`，PB2=`DORMANT_NO_CURRENT_ENTRYPOINT`；14个历史inactive scenario与其他future obligations不计PASS。仅在路径重新canonical时重新评估reachability，不为覆盖率复活入口，详见[B6 aggregate evidence](../audit/evidence/GATEAUDIT_PHASE6_L4_B6_AGGREGATE_QUALIFICATION_ACCEPTANCE.md)。
- 本次授权仅docs-only同步及精确commit/push；后续L5/L6尚未开始，生产、LIVE、真实provider和资金操作仍无授权。

## 6. F009 acceptance provenance

- 已接受本地review与三项P1 closure，不追加review或技术测试。Reviewed fingerprint=`4f5ff0565f44f6aed7fe1b3288f1273a0284b163185cc6eea78a86fc68fef826`；protected fingerprint=`0c4cb19699c5bf914d8e675f60fc37a4869b15ba4ee18ea3bac65e82c9740099`，均仅为历史precommit证据标识，不冒充当前authority candidate fingerprint。
- Failed delivery pair=`85d11984d0c65b464ffe4858fe7fd1da51885f12 / 34024011663`；根因是precommit ROADMAP authority delta导致stage-asset exception digest stale。最小remediation为`dbb8b9c6a2319338f5ca90b566ad494142a55e20`，只同步当时ROADMAP exception hash。
- 固定technical acceptance pair=`dbb8b9c6a2319338f5ca90b566ad494142a55e20 / 34024427455`；后续governance-only authority commit与此pair分层记录，不能成为新的technical acceptance head。完整记录见[F009 post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F009_POST_CI_AUTHORITY_ACCEPTANCE.md)。

- C1 delivery remediation仅机械同步三项授权protected hashes：contract count=18、member topology=105、approved caller topology=1559 edges均不变；new compatibility caller=0、enforcement semantic change=0。F009仍`ACCEPTED / CLOSED`，不是新F009实现。

- Engineering discipline历史整改与来源保留于[原证据](evidence/instruction-system/NQ-CODEX-ENGINEERING-DISCIPLINE-COMPLETENESS.attempt-01.md)；其历史待审阶段不再作为当前work batch或next action。
