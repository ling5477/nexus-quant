# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateY
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatey-freeze
last_frozen_gate_commit=72fbf5e78f217a02b572a54fadb17dea204b594f
active_gate=GateAUDIT
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C2
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=612c2f5887a2e6b3a8b3138d9ae9b193c20e298f
accepted_batch_acceptance_head=612c2f5887a2e6b3a8b3138d9ae9b193c20e298f
accepted_batch_ci_run=34183851797
work_batch=GATEAUDIT-PHASE6-PRE-B0-CI-SAFETY-AND-CURRENT-AUTHORITY-REMEDIATION
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE6-PRE-B0-SAFETY-INDEPENDENT-REVIEW
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
- Phase6=`IN_PROGRESS / NOT_FROZEN`；L4 plan 固定接受 pair=`d79408228ce31c97802afbb674eb2e3d0a2e7bfd / 34071672665`，C1 保持 `ACCEPTED / CI_GREEN / CLOSED`。C2=`ACCEPTED / CI_GREEN / CLOSED`，implementation/acceptance head=`612c2f5887a2e6b3a8b3138d9ae9b193c20e298f`，CI=`34183851797`；P1-3 已关闭，本轮不重开 C2。当前 work batch 为 pre-B0 的 F1/F2 guard 整改、F7/F8/F9 文档收口与 engineering discipline completeness 追加整改，状态 `IMPLEMENTED / PENDING_INDEPENDENT_REVIEW`，尚未完成独立审查或 exact-head delivery。B0、L4 qualification 未开始，L5/L6 等待 L4 accepted。

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

- 当前任务：`NQ-GATEAUDIT-PHASE6-PRE-B0-CI-SAFETY-AND-CURRENT-AUTHORITY-REMEDIATION`；work batch=`IMPLEMENTED|PENDING_REVIEW / NONE / NOT_RUN`。范围包括既有 F1/F2 实现、F7/F8/F9 收口与用户追加的九领域 engineering discipline 归属补齐，不修改 C2 或业务代码。
- Machine action=`NQ-GATEAUDIT-PHASE6-PRE-B0-SAFETY-INDEPENDENT-REVIEW`，合同唯一 matcher=`REVIEW`，对应完整任务名 `NQ-GATEAUDIT-PHASE6-PRE-B0-CI-SAFETY-REMEDIATION-INDEPENDENT-REVIEW`。完整标题含 CI/REMEDIATION，故机器动作使用无歧义别名；不修改 matcher 或治理合同。
- C2 immutable acceptance pair=`612c2f5887a2e6b3a8b3138d9ae9b193c20e298f / 34183851797`，`accepted_batch*` 绑定该 pair。本轮同步用户已给定的接受事实，不把本轮本地测试冒充 C2 CI，不生成新的 C2 acceptance。
- C1(P1-2) 与 C2(P1-3) 均已接受关闭；历史 C1 首次失败 delivery=`41c3bbcb210a65bf2b7b5aad9885d6f9e7bdccdd / 34086018265` 及后续接受 pair=`eb9740b7519f48ffc1e32968cbb0950261b871ef / 34098902705` 保留，不重写历史根因。
- 当前依赖：pre-B0 remediation → 同一次 F1/F2 guard、current docs 与 engineering discipline completeness 独立审查 → 精确 delivery/exact-head CI → B0 Harness Foundation → L4 Qualification。当前无 stage/commit/push 授权；B0 与 qualification 不在本轮执行。
- F3 restore proof identity、F4 SBOM array shape、F5 Java shadow committed-change classification、F6 manual seed SQL scope 均为 `OPEN / P2 / NON_BLOCKING_FOR_B0`，触发条件见[本轮 evidence](../audit/evidence/GATEAUDIT_PHASE6_PRE_B0_CI_SAFETY_CURRENT_AUTHORITY_REMEDIATION.md)。不据此宣称 GateAUDIT 已结束。
- 当前 repository schema=`V48`，以 tracked Flyway inventory 为准；历史 V42/V47 验收和 Phase5B V46 pair 保持原样，不推断生产 schema。本轮不运行 PostgreSQL/Flyway 或 C2 回归。
- P1-1/PB1=`RETIRED_COMPATIBILITY_ONLY`，PB2=`DORMANT_NO_CURRENT_ENTRYPOINT`；保留历史观察，对应路径再次 canonical 时才重新运行 R1–R4。27 scenarios 中 13 当前适用、14 future-triggered；L4 qualification 仍 NOT_RUN，不把历史 68/68 reproduction 当成 qualification acceptance。

## 6. F009 acceptance provenance

- 已接受本地review与三项P1 closure，不追加review或技术测试。Reviewed fingerprint=`4f5ff0565f44f6aed7fe1b3288f1273a0284b163185cc6eea78a86fc68fef826`；protected fingerprint=`0c4cb19699c5bf914d8e675f60fc37a4869b15ba4ee18ea3bac65e82c9740099`，均仅为历史precommit证据标识，不冒充当前authority candidate fingerprint。
- Failed delivery pair=`85d11984d0c65b464ffe4858fe7fd1da51885f12 / 34024011663`；根因是precommit ROADMAP authority delta导致stage-asset exception digest stale。最小remediation为`dbb8b9c6a2319338f5ca90b566ad494142a55e20`，只同步当时ROADMAP exception hash。
- 固定technical acceptance pair=`dbb8b9c6a2319338f5ca90b566ad494142a55e20 / 34024427455`；后续governance-only authority commit与此pair分层记录，不能成为新的technical acceptance head。完整记录见[F009 post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F009_POST_CI_AUTHORITY_ACCEPTANCE.md)。

- C1 delivery remediation仅机械同步三项授权protected hashes：contract count=18、member topology=105、approved caller topology=1559 edges均不变；new compatibility caller=0、enforcement semantic change=0。F009仍`ACCEPTED / CLOSED`，不是新F009实现。

- Engineering discipline 追加整改：`ENGINEERING_DISCIPLINE_COMPLETENESS_REMEDIATED / PENDING_INDEPENDENT_REVIEW`；[九领域 ownership matrix 与证据](evidence/instruction-system/NQ-CODEX-ENGINEERING-DISCIPLINE-COMPLETENESS.attempt-01.md)。仍为原 work batch/REVIEW next action，不另建 workflow 或扩大真实操作授权。
