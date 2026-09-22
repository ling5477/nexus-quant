# GateAUDIT Phase7 Final Baseline Plan

任务：`NQ-GATEAUDIT-PHASE7-FINAL-BASELINE-PLAN`。

结论：`PASS / PHASE7_FINAL_BASELINE_PLAN_ACCEPTED / FINAL_ACCEPTANCE_MATRIX_DEFINED / RESIDUAL_DISPOSITION_DEFINED / RELEASE_BLOCKERS_IDENTIFIED / ARCHIVE_CONTRACT_DEFINED / FREEZE_READINESS_CONTRACT_DEFINED / TAG_SEQUENCE_DEFINED / NO_ACCEPTED_PHASE_REOPENED / NO_REAL_TRADING_AUTHORIZED / PHASE7_NOT_STARTED`。

本文只定义 `PLAN / CONTRACT / DISPOSITION`。它不执行 Phase7-A，不改变 [STATUS](../../current/STATUS.md) 的 machine authority，不创建 archive、freeze commit 或 tag，不修改生产数据，也不授权真实交易、发布、commit 或 push。本文中的未来 commit、tree、CI run、tag object 与 repair result 一律在相应步骤发生后从真实事实回填；不得预填占位 SHA 或借用旧 CI。

## 1. 规划基线

规划检查时的 repository facts：

| Fact | 当前值 | 解释 |
| --- | --- | --- |
| branch / upstream | `audit/post-gatey-agent-baseline` / `origin/audit/post-gatey-agent-baseline` | 本地与同名 upstream 为 `0 / 0` ahead/behind |
| HEAD | `0ad12b4a9009c9435cbbf4a9a20a822d1167c1b9` | 当前 authority synchronization commit，不替代各阶段 immutable technical pair |
| HEAD tree | `df65949c7ad862055cc880a77e6fbd94de8088cb` | 只描述该已提交 baseline；工作区既有未提交 L6 evidence 不属于它 |
| upstream head | `0ad12b4a9009c9435cbbf4a9a20a822d1167c1b9` | 与本地 HEAD 一致 |
| remote default / release branch | `origin/HEAD=b76b13130ef0b416cee146ef9c1de7b353ead5a1`；contract branch=`origin/dev=4c19cb775ebb18b4288400a5a1a402145c2fe30a` | `origin/HEAD`、当前 audit branch 与 release branch 是不同身份，禁止混用 |
| current exact-head CI | `35684969759 / NQ CI Baseline / completed / success / 9 of 9` | headSha 精确等于当前 HEAD；它验证当前已提交 baseline，不是未来 freeze commit CI |
| release ancestry | current HEAD 目前不是 `origin/dev` 的 ancestor | 这是未来 release sequencing prerequisite，不是当前代码 P0/P1 |
| index/stage | `0` | 本规划开始时没有 staged path |
| working tree | DIRTY，含用户既有 `AGENTS.md`、agent policy、L6 evidence、storage analyzer 与 raw run/metrics | 全部排除在本规划变更与未来默认 staging 外；不得清理、覆盖或宽泛暂存 |

以上表格保留 planning-time snapshot，不改写为后续 UI V3 或 docs rebind 身份。纳入 tracked baseline 时新增的 accepted input 为 NQ Console Visual System V3，固定 technical pair=`07453f8b16e798bd580070a3727aa9eb7e88a193 / 35720426791`；该 technical head 已在当前候选 ancestry 中，后续 governance/docs commits 均不得替代它。

全部列入 final baseline 的 technical heads 已在当前候选 ancestry 中存在。current authority 为 GateAUDIT=`IN_PROGRESS / NOT_FROZEN`、Phase6=`ACCEPTED / COMPLETE`、UI V3 工作包=`ACCEPTED / CI_GREEN`、Phase7=`NOT_STARTED`；blocking P0/P1=`0/0`。本规划不把流程前置条件虚报为新的 P0/P1。

事实优先级固定为：代码、Git、同一 run object 的 CI 与 accepted evidence，高于 current explanatory docs；current explanatory docs 高于历史计划；旧 prompt 与 memory 不参与接受判定。[STATUS](../../current/STATUS.md) 继续是唯一 machine current authority。

## 2. Final acceptance matrix

“纳入 final baseline”表示保留其已接受能力或已完成审计事实，不表示重新审查，也不表示所有行都有 implementation commit。Phase1–3 是 audit/analysis/disposition facts，不能伪造成 CI-validated implementation。

| Capability / phase | Scope 与当前状态 | Technical acceptance identity | CI identity | Canonical evidence owner | 纳入 GateAUDIT final baseline |
| --- | --- | --- | --- | --- | --- |
| Phase0 | audit bootstrap、authority/doc portability 与治理基线；`ACCEPTED / CI_GREEN / COMPLETE` | `40e1077e1fe735a3d250f094caaa24e437e8ea3f` | `33306024232 / 11 of 11 SUCCESS` | [STATUS](../../current/STATUS.md)、[Audit Bootstrap Charter](../AUDIT_BOOTSTRAP_CHARTER.md) 与 0C accepted evidence | YES；保留为审计入口与治理基础 |
| Phase1 | repository inventory；`COMPLETE` | `NOT_APPLICABLE`：inventory fact，不发明 implementation head | `NOT_APPLICABLE` | [STATUS](../../current/STATUS.md)、[ROADMAP](../../current/ROADMAP.md)、append-only TESTING/WORKLOG | YES；作为完成的 inventory，不升格为技术实现 |
| Phase2 | AS-IS analysis；`COMPLETE` | `NOT_APPLICABLE`：analysis fact | `NOT_APPLICABLE` | 同上 | YES；作为完成的 analysis |
| Phase3 | finding/disposition；`COMPLETE / READY_FOR_PHASE4`，历史 severity=`P0 0 / P1 4 / P2 8 / P3 1` | `NOT_APPLICABLE`：disposition fact；后续 closure 由 Phase4–6 各自证据拥有 | `NOT_APPLICABLE` | [STATUS](../../current/STATUS.md)、[ROADMAP](../../current/ROADMAP.md) | YES；保留原 severity 与后续 disposition，不把历史 finding 改写为从未存在 |
| Phase4 | F-001 L3 causal proof、F-002 forked-JVM restart foundation、F-003 execution identity、F-004 Trade/Ledger convergence，以及 remaining disposition closeout；`COMPLETE / ACCEPTED` | F-001 `95b859ee61a8e7f0a725e29877e7303ea4453b1a`；F-002 `0651a7365d1a6afe453d75c8abd3975d458e0b7a`；F-003 `327c2229e89c076eace60046b79ec02c622a7fe4`；F-004 `18efc06c380d2b411ba7d5f651e7e441247a1b96`；closeout `7ca1fc92f8900e3e9d19184fccd40569f233823f` | `33347091147`；`33387882472`；`33399190770`；`33358364678`；closeout `33405549149`，均 exact-head green | [STATUS](../../current/STATUS.md) 与 [ROADMAP](../../current/ROADMAP.md) Phase4 matrix | YES；不重新技术审查，不把 foundation 冒充 Phase6 L4 |
| Phase5 | canonical CI/supply-chain、deployment/restore、production-config fail-closed、minimum observability、legacy active-asset consolidation、remote required-check enforcement；`ACCEPTED / CLOSED` | Phase5A `d1d20f4087cd337e0b21037b38b377bcbe25499f`；Phase5B `a12ec821fee9dcadaa11428f1db0a065614fb58b`；F008 `614359fc7f25227f736fbb1c11c7d584da1f0627`；F007 `0e2efdeb236c185dbace67bb22f94c6af64a563a`；F009 `dbb8b9c6a2319338f5ca90b566ad494142a55e20`；F001 remote event=`ruleset 22381941 / refs/heads/dev / effective 9/9` | `33505000903`；`33615809848`；`33978394774`；`34009290836`；`34024427455`；remote readback 无伪造 application CI | [F001 Phase5 final reconciliation](GATEAUDIT_PHASE5_F001_POST_REMOTE_AUTHORITY_ACCEPTANCE.md)、[Phase5B acceptance](GATEAUDIT_PHASE5B_POST_CI_AUTHORITY_ACCEPTANCE.md) 与各 F007/F008/F009 accepted evidence | YES；F005 platform attestation 以 deferred 状态随 baseline 继承，绝不写 CLOSED |
| Phase6 | L4 real-process correctness、L5 scale/repeated-fault/accounting、L6-A 60min、L6-B 180min；`ACCEPTED / COMPLETE` | L4 `3d103cea2072b3c2d9d1009cc5841c18a958ee80`；L5 `23548b75093a62d7614e16f8abcaf9ff2ea32ed7`；L6-A `23a0b46b96950aab9f0b8309d3beb8f446c73dce`；L6-B/L6 `dbf9662add09388cd77ca7552de276bb019f0f74` | `34501806297`；`34608208969`；`34922938228`；`35043157675`，均 `9 of 9 SUCCESS` | [Phase6 final acceptance](GATEAUDIT_PHASE6_L5_L6_FINAL_ACCEPTANCE.md) 与其 hash/index | YES；不重跑 L4/L5/L6，不追加 optional soak |
| Frontend localization / Error UX / Error Catalog | zh-CN canonical、en-US secondary、AntD locale、stable `NQ-TRD-1001 / ORDER_VERSION_CONFLICT`、traceId 保留、mutation 不自动重试；`ACCEPTED / CI_GREEN` | `1b4c87129f2a79e13e379aa56501042ddd5bd42f` | `35684433673 / 9 of 9 SUCCESS` | [Error Catalog verification](../../error-catalog/VERIFICATION.md) | YES；不重新技术审查 |
| NQ Console Visual System V3 | AppShell/theme/page scaffold、登录、Dashboard、primary pages、已确认 NQ/交易所视觉素材、响应式修复与 Dashboard unknown-data correctness；`ACCEPTED / CI_GREEN` | `07453f8b16e798bd580070a3727aa9eb7e88a193` | `35720426791 / 9 of 9 SUCCESS` | [UI V3 acceptance evidence](GATEAUDIT_FRONTEND_CONSOLE_VISUAL_SYSTEM_V3_ACCEPTANCE.md) 与 [Frontend design system](../../current/FRONTEND_DESIGN_SYSTEM.md) | YES；P0/P1=`0/0`，AntD deprecation 与 JS bundle-size warning 保持 observation，不重新技术审查 |
| Current authority synchronization | 将规划时已接受事实投影到 current docs；不是新能力 | `0ad12b4a9009c9435cbbf4a9a20a822d1167c1b9`，tree=`df65949c7ad862055cc880a77e6fbd94de8088cb` | `35684969759 / 9 of 9 SUCCESS` | Git、GitHub run 与 [STATUS](../../current/STATUS.md) | 保留为 planning-time baseline；Phase7-A 从 tracked plan 与届时 current Git/STATUS 读取真实 rebind head，不替代任何 technical pair |

若未来发现 direct contradictory evidence，使某 accepted pair 的候选身份、CI 绑定或核心正确性结论失效，只阻断依赖该证据的 Phase7 路径并按 `BLOCKED / ACCEPTANCE_IDENTITY_CONFLICT` 处理；没有这种直接证据时禁止重开 accepted phase。

## 3. Residual and obligation disposition

### 3.1 Mandatory closure matrix

| Object | Current classification | Blocks final-baseline inventory | Blocks GateAUDIT COMPLETE / freeze | Blocks tag / release | Phase7 disposition、owner 与 closure evidence |
| --- | --- | --- | --- | --- | --- |
| `HISTORICAL_PROJECTION_REPAIR_REQUIRED` | `OPEN / NON_BLOCKING_FOR_L5_ACCEPTANCE`；不是已证实当前 P0/P1 | NO | YES，先完成独立 baseline verification；若有差异则 repair 也必须关闭 | YES | `MUST_CLOSE_BEFORE_FREEZE`。Phase7-B owner=`accounting projection integrity owner`。停止旧 writer 后，从独立只读连接按 canonical Trade/Ledger source 重建 expected Position/Snapshot，并与现有投影逐项核对。无差异时以可复验 manifest/hash/计数关闭 obligation；有差异时先保留 mismatch evidence，再取得单独数据修复授权，执行幂等、可回读的 repair 与独立验证。禁止默认 replay、混跑旧新 writer 或把 clean qualification DB 当历史数据正确性证明 |
| P5-F005 platform attestation | internal SBOM/provenance accepted；`DEFERRED / NON_BLOCKING / DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION / id-token NOT_GRANTED` | NO | NO | NO | `ACCEPTED_DEFERRED`。owner=`future platform attestation owner`；仅在显式授权与 token authority 存在时另开任务。final baseline 必须保留 `DEFERRED`，不得写 CLOSED |
| ordinary concurrent INSERT loser | `P2 / OPEN / NON_BLOCKING`；当前无 duplicate mutation/accounting 升级证据 | NO | NO | NO | `NON_BLOCKING_RESIDUAL`。owner=`canonical order/trade persistence owner`；仅在出现重复外发、重复账务、数据丢失或 direct severity escalation 时重新评估 |
| wildcard-import residual | `P3 / OPEN / NON_BLOCKING` | NO | NO | NO | `NON_BLOCKING_RESIDUAL`。owner=`Java hygiene owner`；不为 freeze 做无关 FQCN 清理 |
| GateY `Order.externalOrderId=NULL` | `P2 / ORDER_VENUE_IDENTITY_MODEL_CONSISTENCY_RESIDUAL`；receipt/trade/ledger/reconciliation 完整，current STATUS 明确不阻断 freeze | NO | NO | NO | `NON_BLOCKING_RESIDUAL / HISTORICAL_PRODUCTION_FACT_IMMUTABLE`。不得修改生产订单或为归档清零 |
| pre-B0 F3 restore proof identity | `OPEN / P2 / NON_BLOCKING_FOR_B0` | NO | NO，现有 Phase5B/后续 CI 接受事实不被直接否定 | NO | `NON_BLOCKING_RESIDUAL`。owner=`deployment/restore contract owner`；未来相关 checker/restore 语义变更时处理 |
| pre-B0 F4 SBOM array shape | `OPEN / P2 / NON_BLOCKING_FOR_B0` | NO | NO | NO | `NON_BLOCKING_RESIDUAL`。owner=`SBOM/provenance tooling owner`；未来 normalization 变更时处理 |
| pre-B0 F5 Java shadow committed-change classification | `OPEN / P2 / NON_BLOCKING_FOR_B0` | NO | NO | NO | `NON_BLOCKING_RESIDUAL`。owner=`Java governance owner`；未来 classification 语义变更时处理 |
| pre-B0 F6 manual seed SQL scope | `OPEN / P2 / NON_BLOCKING_FOR_B0`；静态风险，SQL 未执行 | NO | NO | NO | `NON_BLOCKING_RESIDUAL`。owner=`fixture/database tooling owner`；不得在生产或含非-fixture admin 的库执行，未来 seed 使用前必须先收窄 scope |
| retired typed compatibility rows与历史 P1-1/PB1 | `RETIRED_COMPATIBILITY_ONLY / NOT_CURRENTLY_ELIGIBLE` | NO | NO | NO | `RETIRED`。保留历史与 reactivation trigger；只有真实 canonical reactivation 后重新做 R1–R4，不为 coverage 复活入口 |
| dormant intent-worker rows与历史 PB2 | `DORMANT_NO_CURRENT_ENTRYPOINT / NOT_CURRENTLY_ELIGIBLE` | NO | NO | NO | `DORMANT`。真正接入 sender/worker 后重评；当前不计 PASS、SKIP 或 defect closure |
| 14 个历史 inactive scenarios | `NOT_CURRENTLY_ELIGIBLE` | NO | NO | NO | `FUTURE_OBLIGATION`。维持分组与触发条件，不进入当前 eligible 分母 |
| 未注册交易 scheduler、不存在 lease/leader、Venue 自身重启持久性、超出已接受 L5/L6 envelope 的规模/长期/扩展故障 | `FUTURE_OBLIGATION` | NO | NO | NO | `FUTURE_OBLIGATION`。新 owner/入口、范围和授权同时出现后建立专属 proof；不推翻当前 bounded acceptance |
| 历史 FAIL/BLOCKED/remediation、5421ms cause=`UNKNOWN` | `HISTORICAL_ONLY / APPEND_ONLY` | NO | NO | NO | `ARCHIVE_ONLY`。保留原始结果、根因未知与 superseding evidence 的关系；禁止改写为 PASS、CLOSED 或已知根因 |
| legacy Phase3 IDs F-005/F-011 | `LEGACY_FINDING_IDENTITY_UNRECOVERABLE / RETIRED` | NO | NO | NO | `RETIRED / ARCHIVE_ONLY`。禁止猜测其语义或与 P5-F005 混同；只有找到可验证 canonical source 时重新审计 identity |

### 3.2 Historical projection repair 的确定性判定

该 obligation 进入 **Phase7 mandatory closure**，不是 post-freeze/pre-release follow-up，理由如下：

1. accepted L5 evidence 明确要求 `pre-freeze/release baseline verification`，clean isolated qualification 不能证明历史 deployment projection 正确；
2. 当前 governance contract 的 freeze/release 转移都直接进入 `FROZEN|ACCEPTED|TAGGED`，不存在可作为 canonical authority 的“已 freeze、尚未 release/tag”中间状态；
3. 因此 Phase7-B 必须先完成核对。若 source 与 projection 一致，关闭的是 verification obligation；若不一致，repair 立即成为 `MUST_CLOSE_BEFORE_FREEZE`，在 repair 及独立 readback 完成前不得通过 freeze readiness、不得创建 tag；
4. repair 是潜在数据 mutation，本文不授权。缺少目标数据源、access authority 或 repair authorization 时，只将 Phase7-B 对应路径标为 `BLOCKED`，其他只读 inventory 可以继续。

## 4. Canonical authority ownership

| Object | 唯一 owner | Relationship / 禁止事项 |
| --- | --- | --- |
| current stage、安全状态、work batch、next action | `docs/current/STATUS.md` machine block | 唯一 machine current authority；archive、plan、ROADMAP 不得覆盖 |
| next allowed Phase7 batch 与 residual disposition explanation | `docs/current/ROADMAP.md` | 解释 STATUS，不得产生第二份 machine state |
| final acceptance matrix 与 evidence locator | Phase7 archive 的 `GATEAUDIT_EVIDENCE_MATRIX.md` | 引用 immutable accepted evidence 与 hashes；不复制大量 raw，不重写历史结果 |
| accepted technical evidence | 现有 `docs/audit/evidence/**` 与 `docs/error-catalog/**` owners | 保持原路径与 append-only 内容；archive 通过链接和受控 manifest 引用 |
| final archive navigation | `docs/gates/gate-audit/README.md` | historical archive 入口；明确 non-runtime authority，链接所有 strict roles |
| freeze readiness decision | `GATEAUDIT_FREEZE_READINESS_REVIEW.md` | 只判断 entry/exit criteria；不预言 future commit/CI/tag object |
| pre-tag closeout candidate | `GATEAUDIT_FREEZE_CLOSEOUT.md` | 记录真实 candidate source/tree、residual、验证与 `TAG PENDING`；不提前写 FROZEN/TAGGED |
| release/tag identity | annotated tag `nq-gateaudit-freeze`、Git tag object、peeled commit、remote ref 与同一 exact-head CI run | Git/GitHub fact；必须由 release checker 验证，文档声明不能替代 |
| post-tag current projection | `STATUS.md` + 最小相关 current docs | 只在 tag/remote/readback 成功后同步 `last_frozen_gate` 与 GateAUDIT frozen 状态；tag target 保持 immutable |

若 STATUS、archive、freeze document 三者冲突，立即 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`；先依据 Git/CI/accepted evidence 做最小 forward correction，不通过修改历史 archive 消除冲突。

## 5. Phase7 batches and gates

### Phase7-A — Final baseline inventory

任务名：`NQ-GATEAUDIT-PHASE7-A-FINAL-BASELINE-INVENTORY`。

- 固定 source HEAD/tree、upstream、release branch、accepted technical heads、CI objects、evidence paths 与 ancestry。
- 生成 final acceptance matrix 和 evidence locator；验证每项 accepted identity 存在且属于最终候选 ancestry。
- 只读核对所有 `OPEN / DEFERRED / NON_BLOCKING / FUTURE_OBLIGATION / RETIRED / DORMANT / HISTORICAL_ONLY` 对象；不得技术重审 accepted phases。
- Entry：本计划已进入 tracked baseline，UI V3 accepted technical pair 已绑定到 current authority，且 current next action=`NQ-GATEAUDIT-PHASE7-A-FINAL-BASELINE-INVENTORY`；Phase7 未启动。
- Exit：matrix 无 missing identity、无 authority conflict、每个 residual 有 owner/trigger/disposition；若发现 direct contradictory evidence，仅依赖路径 BLOCKED。
- Verification：current-authority、目标 doc links、Git object/ancestry、GitHub CI readback、evidence existence/hash/index、diff check。docs-only；不运行 Maven/E2E/L6。

### Phase7-B — Residual disposition and mandatory closure

任务名：`NQ-GATEAUDIT-PHASE7-B-RESIDUAL-DISPOSITION-AND-MANDATORY-CLOSURE`。

- 固化上节 closure matrix；不得把 P2/P3/deferred/future 自动升级为 P1。
- 执行 historical projection source↔Position/Snapshot baseline verification。读取任何非本地历史/生产数据前必须有明确 data-source 与 read authority；数据 repair 必须另有 mutation authorization。
- 若一致：保存独立重建算法、输入身份、row/count/hash、精确比较与 read-only 证明，关闭 `HISTORICAL_PROJECTION_REPAIR_REQUIRED`。
- 若不一致：保存 mismatch，不修改历史 evidence；形成单独 repair work order，完成幂等 repair、失败恢复、readback 和必要独立 review 后才关闭。
- Exit：mandatory closure=0、blocking P0/P1=0/0、projection obligation 已以一致性证明或已授权 repair+readback 关闭；所有 deferred/non-blocking/future 保留原分类。
- Verification：projection 路径使用隔离重建与 source-derived oracle；若实际 repair，按数据库/账务高风险范围执行 targeted PostgreSQL proof 与一次真正独立 review。无 repair 时不运行 Full Maven。

### Phase7-C — Freeze readiness review

任务名：`NQ-GATEAUDIT-PHASE7-C-FREEZE-READINESS-REVIEW`。

- 独立于实现者核对 final matrix、mandatory closure、authority consistency、release branch contract、archive layout、historical preservation 与安全状态。
- Entry：Phase7-A/B exit 全部满足；最终 candidate 无未解释 drift；P0/P1=0/0；release blocker=0。
- Exit：`PASS / GATEAUDIT_FREEZE_READY / PRETAG_ARCHIVE_AUTHORIZED`，或保留明确 blocker。PASS 只授权形成 archive/freeze candidate，不授权 tag。
- Verification：真正独立的 governance/release-control review；不 review-of-review，不重跑已接受技术 qualification。

### Phase7-D — Canonical archive and pre-tag closeout

任务名：`NQ-GATEAUDIT-PHASE7-D-CANONICAL-ARCHIVE-AND-CLOSEOUT`。

- 创建 `docs/gates/gate-audit/**` strict archive；只复制/迁移必要 role 文档，其他 accepted/raw evidence 保持原路径并由 manifest/hash 引用。
- 形成 freeze candidate commit，但 archive 内先写真实 source HEAD/tree 与 `freeze commit=TO_BE_BOUND_BY_GIT` 的非身份措辞；禁止放入虚构 SHA、run ID、tag object。提交产生后，使用不自引用的 delivery receipt/后续事实绑定。
- Entry：readiness PASS；所有 role 内容、source links 与 evidence locator 完整。
- Exit：strict pre-tag archive PASS、current authority consistent、links errors=0、secret/stage checks与 diff check PASS；candidate 已按精确 allowlist review，仍为 `TAG PENDING`。
- Verification：docs-only validators；只有 archive 触及 checker/contract/runtime code 或出现 direct drift 时才运行相应 targeted tests。默认不运行 Full Maven、frontend E2E 或 L6。

### Phase7-E — Freeze candidate delivery and annotated tag

任务名：`NQ-GATEAUDIT-PHASE7-E-FREEZE-CANDIDATE-DELIVERY-AND-TAG`。

- 这是单独 Git/release 授权任务。精确 staging → commit → push → release branch binding → exact-head CI → annotated tag → push tag → remote readback。
- governance contract 的 release branch 是 `dev`。当前 audit HEAD 不是 `origin/dev` ancestor；最终 freeze candidate 必须以同一 commit 身份进入 `dev`。若 branch protection/PR 产生新的 merge commit，则该 merge commit 才是 freeze candidate，必须重新绑定 tree、archive manifest 与 exact-head CI；禁止给 audit pre-merge commit 打 tag 后宣称 dev 已冻结。
- Exit：local/remote annotated tag object 一致，peeled commit 精确等于 freeze commit，freeze commit 位于 live `origin/dev` ancestry，同一 `NQ CI Baseline` run object 的 head/status/conclusion/databaseId 全部匹配且 green；`check-gate-release.ps1` 与 release-mode archive checker PASS。

### Phase7-F — Post-tag authority synchronization

任务名：`NQ-GATEAUDIT-PHASE7-F-POST-TAG-AUTHORITY-SYNCHRONIZATION`。

- 只在 Phase7-E remote tag/readback PASS 后，将 STATUS 同步为 GateAUDIT=`FROZEN / ACCEPTED / TAGGED`，写入真实 tag、freeze commit 与已完成 Phase7 状态；ROADMAP/FACT_SOURCE_INDEX/导航做最小同步。
- 该 post-tag docs commit 不替代 tagged freeze commit、tag object 或 accepted technical pairs。其自身按授权提交并跑 exact-head CI，但 tag 不移动、不 force update。
- Exit：current authority、archive 与 release facts一致；next action 只指向明确的 post-GateAUDIT work，不在本计划预造不存在的未来工作结果。

## 6. Archive layout contract

未来 archive root：`docs/gates/gate-audit/`。本规划任务不创建该目录。

| Future path / role | 内容与来源 | Copy / reference / immutability |
| --- | --- | --- |
| `README.md` / archive-entry | GateAUDIT scope、tag handoff、导航、non-authority 声明 | 新建薄导航但必须满足 strict independent-body contract；tag 后 immutable |
| `GATEAUDIT_FREEZE_CLOSEOUT.md` / freeze-closeout | pre-tag candidate、真实验证、residual、rollback/forward-fix 边界 | 新建；pre-tag 不预言 freeze SHA/CI/tag object |
| `GATEAUDIT_FREEZE_READINESS_REVIEW.md` / freeze-readiness | Phase7-C independent decision | 审查产物；tag 后 immutable |
| `GATEAUDIT_PHASE7_FINAL_BASELINE_PLAN.md` / plan-or-reconstructed-baseline | 本文在 Phase7-D 以 hash-preserving move 或受控 copy 进入 archive | 若 move，更新全部 links；若 copy，记录 source blob/hash 并指定 archive copy 为 frozen role，避免两个 current owner |
| `GATEAUDIT_EVIDENCE_MATRIX.md` / batch-evidence-matrix | Phase0–6 + frontend/error final matrix与 locator | 新建 summary；引用 accepted evidence，不复制 raw |
| `GATEAUDIT_TESTING_AND_CI_SUMMARY.md` / testing-evidence | technical pairs、current/freeze exact-head CI、未重跑说明 | 新建 summary；每个 CI 必须绑定自己的 head，不用当前 CI 覆盖历史 pair |
| `GATEAUDIT_BOUNDARY_STATEMENT.md` / boundary-statement | safety、scope、未授权能力、historical immutability | 新建；保留 LIVE disabled / kill engaged 等 current facts |
| `GATEAUDIT_KNOWN_LIMITATIONS_AND_RESIDUALS.md` / known-limitations | deferred、P2/P3、retired/dormant/future、historical-only | 新建；不得把 deferred/open 写 CLOSED |
| backend/DB、API、frontend、Python、runtime/scheduling evidence roles | 由 strict manifest 的 conditional roles 决定 | 建立简洁独立 summary，引用 canonical current/accepted sources；不搬运 915 项 audit evidence 或 L6 raw ZIP |
| `source/task-evidence/**` | 仅在 archive contract 需要保留无法稳定外链的 task evidence 时使用 | 默认 reference-in-place；如迁移必须 hash-preserving、append-only、manifest 完整且无 broken links |

`docs/current/**` 保持 current authority/navigation；`docs/audit/evidence/**` 保持 accepted execution evidence；`docs/error-catalog/**` 保持 frontend/error package owner；大体积 L6 raw、历史 FAIL/BLOCKED/remediation 不为“整洁”而复制、删除或改写。

## 7. Freeze identity and exact release sequence

Freeze identity 是关系，不是一个提前填写的 SHA：

```text
accepted technical heads + accepted CI objects
        ↓ ancestry / evidence locator
final source commit + source tree
        ↓ Phase7-A/B/C
strict archive manifest + residual closure
        ↓ Phase7-D exact allowlist commit
freeze candidate commit on release branch dev
        ↓ exact-head NQ CI Baseline SUCCESS
annotated tag nq-gateaudit-freeze
        ↓ push + remote object/peeled readback
post-tag STATUS synchronization
```

未来执行顺序必须精确为：

1. 从 clean、无 staged path 的最终 candidate 开始，核对 user-owned changes 并生成 explicit allowlist；禁止 `git add .` / `git add -A`。
2. inspect exact diff，运行 pre-tag archive/current-authority/links/evidence-index/stage/secret/diff gates。
3. 创建 freeze candidate commit 并 push。该 commit 必须是 `dev` 上的实际 candidate；若 promotion 产生新 commit，回到步骤2重新绑定。
4. 等待该 freeze commit 的 `NQ CI Baseline` exact-head run `completed / success`；workflow、head、status、conclusion、databaseId 来自同一 run object。
5. 仅在 CI green 后创建 annotated tag `nq-gateaudit-freeze`，tag target 精确为 freeze commit；不得 lightweight tag、移动既有 tag 或 force push。
6. push tag；核对 local tag object、remote tag object、local peeled、remote peeled、freeze commit 与 live `origin/dev` ancestry。
7. 执行 canonical release checker与 archive release-mode checker；失败即保持未冻结 authority，并以 forward fix/新 candidate 处理，tag 已推送后不得重写。
8. 完成 post-tag authority synchronization，写入真实身份并对该 docs commit 跑自己的 exact-head CI；它不移动 freeze tag。

## 8. State semantics and exit criteria

### GateAUDIT COMPLETE

只有同时满足以下条件才可称 `COMPLETE`：

- Phase0–6 与 frontend/error accepted facts 已纳入 final matrix，identity/evidence 无冲突；
- Phase7-A/B 全部 mandatory closure 完成，包括 historical projection baseline verification，以及发现差异时的 authorized repair+readback；
- residual/deferred/future/retired/dormant 全部有 owner、trigger、disposition，且没有借 Phase7 自动关闭；
- Phase7-C readiness PASS，P0/P1=`0/0`，release blockers=`0`；
- archive candidate 内容完整并通过 pre-tag gates。

`COMPLETE` 是 capability/governance closeout，可以在 tag 创建前作为 Phase7-D 的 pre-tag结论，但 machine `active_gate_status` 仍须保持合同允许的 `IN_PROGRESS|NOT_FROZEN`，不能私造 `COMPLETE|NOT_FROZEN` 状态值。

### GateAUDIT FROZEN

只有 annotated tag 已创建并推送、peeled target=freeze commit、remote object一致、freeze commit 在 live `origin/dev` ancestry、该 commit exact-head CI green、archive/release validators PASS 后，才可在 post-tag authority sync 中称 `FROZEN / ACCEPTED / TAGGED`。Pre-tag archive、readiness PASS、local commit 或 CI green 单独都不等于 FROZEN。

### Tag eligibility

创建 tag 前必须同时满足：GateAUDIT COMPLETE；projection obligation CLOSED；blocking P0/P1=0/0；strict archive pre-tag PASS；freeze candidate已在 release branch；worktree/stage与 allowlist可解释；exact-head CI green；安全状态未被放宽。任何未来 SHA、run ID 或 tag object 未真实存在前均不得写入接受结论。

## 9. Verification strategy

| Batch | Required verification | Explicitly not required by default |
| --- | --- | --- |
| Plan（本文） | current-authority、目标 doc links、accepted identity existence/ancestry、live CI readback、evidence/residual cross-check、`git diff --check` | Full Maven、frontend E2E、L6、Docker fault qualification、real exchange、independent review |
| Phase7-A | docs-only validators、Git/GitHub readback、evidence/hash/index consistency | accepted phase requalification |
| Phase7-B read-only一致 | source-derived projection oracle、targeted PostgreSQL/read-only export、manifest/hash/count、independent result check | full repository tests |
| Phase7-B repair triggered | targeted migration-free repair rehearsal、idempotency/rollback/readback、账务/投影 correctness tests、一次真正独立 review | unrelated Maven/frontend/L6 |
| Phase7-C | independent governance/release-control review；candidate fingerprint start=end、stage=0 | review-of-review |
| Phase7-D | archive pre-tag、authority、links、manifest/index、stage assets、secret、diff checks | Full Maven/E2E/L6，除非 code/checker delta 或新风险触发 |
| Phase7-E/F | exact-head full CI、annotated tag/remote/peeled/branch ancestry、release/archive validators、post-tag authority consistency | production deployment、LIVE、real provider |

本规划的 Jev involvement=`NONE`。所有 disposition、freeze identity 与 release decision 只来自 deterministic repository/Git/CI/evidence contract。

## 10. Safety, stop conditions and planning self-review

安全状态在所有 Phase7 batch 中保持：`LIVE=DISABLED`、kill switch=`ENGAGED`、real provider=`NOT_IMPLEMENTED`、private trading=`NOT_IMPLEMENTED`。不授权 PLACE、CANCEL、transfer、withdraw、credential mutation、真实 exchange、production deployment 或 production database mutation。

仅以下情况阻断依赖路径：current authority 无法解析；accepted identity/CI/evidence 不存在或矛盾；current P0/P1 明确阻止 freeze；projection target/source 或 access authority 无法确定；projection mismatch 需要 repair 但无 mutation authorization；archive owner不唯一；freeze candidate无法以真实身份进入 `dev`；exact-head CI/tag/remote readback失败。P2/P3、F005 deferred、inactive/future、retired/dormant、历史失败本身不是 blocker。

Planning self-review：

- 每个 mandatory Phase7 obligation 均有 owner、entry、exit 与验证；
- 每个 current residual 均有互斥 disposition，不把 optional item 升级为 P1；
- COMPLETE、pre-tag ready、FROZEN/TAGGED 与 post-tag sync 已分离；
- release sequence不循环，current CI不冒充 future freeze CI；
- 没有 future SHA、CI run、tag object 或 repair result；
- accepted phase未重开，historical FAIL/BLOCKED/UNKNOWN未改写；
- archive复用现有 `docs/gates/**`、`docs/audit/evidence/**`、`docs/current/**` ownership，不建立平行 authority；
- 本任务 code/production/archive/tag mutation=`0`，Phase7仍=`NOT_STARTED`。

## 11. 唯一下一动作

```text
NQ-GATEAUDIT-PHASE7-A-FINAL-BASELINE-INVENTORY
```

该动作只在新的明确任务中启动；本文不执行它。
