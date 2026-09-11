# Phase6 L4 B6 聚合资格验收

日期：2026-09-11。任务：`HIGH_RISK / AGGREGATE_ACCEPTANCE / EVIDENCE_RECONCILIATION / REVIEW_ONLY / NQ-only`。

## Final candidate identity

本次依据用户明确的 B6 聚合验收合同，只读复核当前技术候选、既有独立审查、qualification 与交付链；唯一新增内容是本文件。没有重新执行 correctness implementation、独立生产审查或动态 chaos matrix。

| 身份 | 本轮实际核验 |
| --- | --- |
| Branch | `audit/post-gatey-agent-baseline` |
| Starting HEAD / final delivery SHA | `3d103cea2072b3c2d9d1009cc5841c18a958ee80` |
| Origin tracking / actual remote | 均为上述 SHA；实际 remote 使用 `git ls-remote` 只读核验 |
| Exact-head CI | [34501806297](https://github.com/ling5477/nexus-quant/actions/runs/34501806297)，实时 `gh run view`：headSha 精确相等，completed / success，9/9 jobs SUCCESS |
| CI jobs | Runtime safety and no-outbound、Secret scanning、Repository hygiene and governance、Frontend build and critical E2E、Backend regression、Java architecture guard、Research quality、PostgreSQL and Flyway、Delivery SBOM and provenance |
| Worktree | 验收技术基线 clean；新增本报告前 status 为空。报告产生后仅本文件 untracked，不把最终整个工作区说成 clean |
| Git | stage=0 / commit=NONE / push=NONE；HEAD 未变 |
| Schema inventory | V51；未连接数据库或推断生产 schema |
| V49 SHA256 | `ebea77f85cae76d88e052a99b647573ec3b6ba5ea0464f9083012aac2a883b93` |
| V50 SHA256 | `ca87e2b7b0c739b8fae59d701bf0ff54336ee3d262eb5ae7dcd7292edfc45ae6` |
| V51 SHA256 | `afbc3211b824b8f717912707b584d2382f47fe9f8df86802e7c4a1c8a6cd9942` |
| Stage-assets | 本轮 canonical checker：scanned=1880 / reviewed_exceptions=173 / errors=0 |
| Gitleaks | 最终 exact-head Secret scanning job SUCCESS；[最终交付前扫描事实][stage]为 exit0/findings0，六类秘密负例拒绝。本轮不重跑 scanner |

[STATUS](../../current/STATUS.md) 的 machine authority 仍为 pre-B0，和已交付事实不同；此差异此前各批次已明确记录。本轮按用户明确范围接受技术事实，不修改 authority、不把它解释为 LIVE 或后续执行授权。clean 条件针对用户指定的最终技术候选；按同一任务要求新增报告产生的 untracked 文件是显式列出的唯一输出，不以提交或删除报告伪造最终 clean。

### 候选身份闭合

- 当前文件与[最终已审 Full manifest][full]的 1818 个文件逐项 SHA256 比较：1816 相同，仅 `synthetic_evidence.py`、`test_synthetic_evidence.py` 两项不同，正好是[交付清单][delivery]及[qualification integrity][integrity]明确记录的 exporter/test delta；生产内容没有差异。Full Maven `1898/0/0/121 conditional skips` 是既有执行事实，不是本轮运行或 opt-in proof 的替代。
- [最终 review 原答复][review5]绑定完整候选 fingerprint=`0502bc83ec532c6f8ec26fb7a21ffda03e986cd6d86018d4b0184eeb55032411`。它与交付 SHA 是不同身份；通过逐文件内容和 qualification delta 相连，不把交付 docs commit 冒充 review 起点。当前资格新增测试另有[独立只读 proof 核对][proofreview]。
- [复用索引][reused] 34 份、[新增索引][new] 13 份、[interaction 索引][interactions] 4 份，合计 51 份 canonical proof 的当前 SHA256 全部与索引一致；这些数量不是本轮执行次数或 eligible obligation 数。
- 728 项交付清单中，自身摘要为 null 的交付页由 Git tree 绑定；其余 727 项中 726 项原始摘要相同，1 项为 `reused-review-original.txt` 的已记录 EOF 修正。交付时备份 `backend/nq-app/target/b5-precise-delivery/review-original-before-eof-normalization.txt` 本轮读回 SHA256=`01159bbf438289d9e76e412b3c84109641a6b7d5f728f1f0f0a32a1cf548727f`，符合原 manifest；当前为 `2f32823195a167401bedced72a45018fa4174cff2e968cb264186741f3be5e26`。5835→5833 bytes，去除末尾 CR/LF 后逐字节相等。该目录 `final-precommit.json` 明确记录单文件 candidateDelta、technicalDelta=0；不是正文变化或失效 review。原始备份仍为本地 ignored 证据，本报告保存此核验事实，不复制历史正文。
- B0、B1、B2、B3、B4 delivery commits 全部经 `git merge-base --is-ancestor` 确认为最终 HEAD 的祖先。祖先关系本身不证明能力保持，后续变化由下文直接回归和代码核对承接。

## B0–B5 accepted identity table

| Batch | 实现 / review / qualification 身份 | Delivery / exact-head CI | 聚合状态 |
| --- | --- | --- | --- |
| B0 | [B0][b0] attempt05：9/0/0/0，真实 Spring/PG/Venue，test-only self review；后续 B1 再使用同一基础 | `b172e36ad14b8fdafa621ea9b32c3a59be1eb20c`；[B1][b1]记录后续基线 `2b0eb0cc8d6e43f4c365339743f9fd62c9199e54 / 34236471862` | ACCEPTED_PROOF |
| B1 | [B1][b1] 六次 accepted-timeout/lost-ACK qualification；test-only self review；最终 V51 interaction01–02 再证明受影响入口 | `d1cedb6debfaa3dbeacb0e95ef8599c69e5f9da3 / 34246407667`，见[B3基线][b3] | ACCEPTED_PROOF |
| B2 | [组合独立审查][review2] fingerprint=`f7fc0fe02ede99e1f52a6b52cb6786d41d0a2efb0bd377e93352568e92a14da2`；[qualification][b2] production fingerprint=`7982a8d51e21332e7474be15b24e468249ba0e19a647d651bdd8a5020305910a`，48/48；最终 V51 review 补验 | implementation `68fe95b58785a7534b648eb3dc30db67b93aefbd`；accepted delivery `e927fe107ce5cac4eb828f86b95561d977785b53 / 34327322619` | ACCEPTED_PROOF |
| B3 | [B3][b3] 12/12；production fingerprint=`9a1defa244a7dcd829be7c5c50b95b7902253da3ebce9d4b64e9f09677c44c73`；无生产修改，不虚构额外独立审查 | `e9509e351e6fbc6179e5e081cb03e66c8cb6ad99 / 34334552967`，本轮 CI head/conclusion 实时核验 | ACCEPTED_PROOF |
| B4 | [review provenance][review4] full fingerprint=`1af151b4a4e33bb05d96cf7065c9270f692ea52120959223efa8cceb07f1307c`，production=`a681af6653dd387491012e5eea164f64f89ee2dba74fe7bcef5a1e2992993e6d`；119 direct，Full1839/0/0/90；[qualification][b4] 28新场景+2复用 | `86c8ad84542636364f6c21e78bc292a323cbdff7 / 34359273231`，本轮 CI head/conclusion 实时核验 | ACCEPTED_PROOF |
| B5 | [最终独立 V51 review][review5]上述0502指纹，21/0/0/0；[最终 qualification][b5]17新场景+34复用proof；[stage根因关闭][stage]后technical delta=0 | `3d103cea2072b3c2d9d1009cc5841c18a958ee80 / 34501806297`，9/9 | ACCEPTED_PROOF |

B0 实际提供独立 controller/NQ/Venue PID、强杀后新 NQ PID、真实 transaction proxy/JDBC 与 adapter→Venue HTTP/序列化。checker 只读，fixture 只在启动前初始化输入；没有补写业务终态。真实 credential/exchange/LIVE 操作为0。这里 HTTP 指外部 adapter transport，不冒充 Web Controller 鉴权证明；B2/B4 的 LIVE 是隔离 fixture 的 durable environment 字段。B0 不证明 Venue 自身重启持久性，该限制保留。

## Current eligible matrix

以下按当前语义归并为 **28 个 obligation：13 个当前 canonical plan rows、B0基础1个、后续已接受扩展14个**。这一数字来自逐项列举，不沿用历史27行预算，不把场景重复次数当 obligation。每行状态只有 `ACCEPTED_PROOF`；早期证据在后续变更影响处由最终 reviewed regression 承接，不能独立冒充最终 candidate proof。

| ID | 当前义务 | 状态 | 有效 proof / 最终承接 |
| --- | --- | --- | --- |
| INFRA | B0进程/事务/HTTP与只读oracle | ACCEPTED_PROOF | [B0][b0]及最终13份多JVM proof、[proof review][proofreview] |
| L4-AT-01 | accepted-timeout、query-first、无盲重发 | ACCEPTED_PROOF | [B1][b1]；最终 interaction01 |
| L4-LA-02 | lost ACK、restart convergence | ACCEPTED_PROOF | [B1][b1]；最终 interaction02、R07 |
| L4-CFR-02 | fill赢cancel、真实终态收敛 | ACCEPTED_PROOF | [B2][b2]；最终B2补验、reused25–26和rounded proof |
| L4-CFR-03 | cancel赢、部分/零成交最终性 | ACCEPTED_PROOF | [B2][b2]；reused25 CANCEL_PARTIAL / 26 CANCEL_ZERO |
| L4-CFR-04 | OCC/stale PLACE及cancel ACK不覆盖新事实 | ACCEPTED_PROOF | [B2][b2]；最终review STALE_PLACE双环境补验、V49旧sender proof |
| L4-PFC-01 | per-fill/remaining/fees/Trade/Event/Ledger/replay | ACCEPTED_PROOF | [B2][b2]；最终MULTI_PARTIAL双环境补验、R01–13账务oracle |
| L4-KIF-01 | durable Kill拒绝新mutation | ACCEPTED_PROOF | [B3][b3]；interaction03–04、reused29 |
| L4-ESDB-00 | prepare提交前断连、无外发 | ACCEPTED_PROOF | [B4][b4] PREPARE_CONNECTION_LOSS/ROLLBACK/REJECT；reused16/18/20 |
| L4-ESDB-05 | CANCELLED后Trade前死亡恢复 | ACCEPTED_PROOF | [B4][b4] CANCELLED_TRADE_DEATH；最终cancel finality、原子fan-out与重放 |
| L4-ESDB-06 | Trade后Ledger断连原子性 | ACCEPTED_PROOF | [B4][b4] LEDGER_CONNECTION_LOSS；Ledger writer未变、最终R01–13恢复 |
| L4-ESDB-07 | Ledger提交前进程死亡 | ACCEPTED_PROOF | [B4][b4] LEDGER_DEATH_BEFORE_COMMIT；Ledger writer未变、最终重放 |
| L4-MIL-04 | 已注册只读scheduler PG锁跨JVM释放 | ACCEPTED_PROOF | [B5][b5] resume01 R07–R09；最终qualification核验四个wiring/refresh/lock文件未变 |
| L4-DW-01 | ordinary并发同command唯一执行 | ACCEPTED_PROOF | [B5][b5] resume01 R01–R03；最终R07新PID重放 |
| X01 | Kill in-flight、restart保留ENGAGED、允许query恢复 | ACCEPTED_PROOF | [B3][b3]；interaction03 PRE_ACCEPT / 04 RESTART_PRE_ACK |
| X02 | commit rejection/response-loss/read durable truth/fan-out | ACCEPTED_PROOF | [B4][b4]；最终reused15–20覆盖A/B/C提交前后断连 |
| X03 | V49 one-shot、stale sender、MAY不重新授予 | ACCEPTED_PROOF | [B5][b5]；reused07/08/23/30/31/33及最终V49补验 |
| X04 | canonical TradingVenue防authority bypass | ACCEPTED_PROOF | [venue整改][venue]接受链；最终review manifest及V49补验 |
| X05 | V50同窗口跨JVM single admission | ACCEPTED_PROOF | reused12–14、21；R08–10 admission前死亡 |
| X06 | CREATED owner death/paused owner恢复原run | ACCEPTED_PROOF | R01–06、reused04/27/28 |
| X07 | DISPATCHING恢复、暂停、双successor、原子回滚 | ACCEPTED_PROOF | reused08/22/23/24/27/28/33 |
| X08 | RUNNING确定终态恢复及幂等 | ACCEPTED_PROOF | R11–13、reused01/25/26/34 |
| X09 | immutable work、同run Order≤1、唯一V49 lineage | ACCEPTED_PROOF | 最终[review][review5]、reused04/08/22–24/28及R01–13 |
| X10 | requested保留、effective quantity/price持久、wire相等 | ACCEPTED_PROOF | 最终[review][review5]，R01–13；10.0005→10、100.005→100 |
| X11 | 无效/超精度/规则变化拒绝、不重新规范化已冻结值 | ACCEPTED_PROOF | reused02/03/05/09/10/11及最终review负例 |
| X12 | rounded full-fill无phantom residual/false overfill | ACCEPTED_PROOF | reused01、R11–13，精确executed=Order.qty |
| X13 | recovery/manual scan并发及未来window progress | ACCEPTED_PROOF | R01–06/R08–13、reused01/04/21/32；后续不同dueAt可admit，不宣称第二单成交 |
| X14 | SIM/LIVE durable环境与账务隔离 | ACCEPTED_PROOF | [B2组合review][review2]及最终B2四场景补验；非真实LIVE操作 |

Accepted proof rows=28；Missing=0；Invalid=0。B1–B5 complete matrix / Full Maven / 新chaos / Playwright 本轮均未运行。CI绿色不被用来填补 opt-in 动态证据。

## Cross-batch invariant matrix

| 组合 | 当前代码与证据核对 | 结论 |
| --- | --- | --- |
| B1 ↔ V49/B5 | `OrderCommandService.executePreparedPlaceOrder` 仅在事务arm成功返回后外发；`armOrdinaryPlace`独立事务，异常不继续发送。`finalizeOrdinaryNoOrder` revoke失败保持unresolved；读MAY不授予许可。interaction01–02、reused06/07/30/31证实query-first/no blind retry | RECONCILED |
| B2 ↔ V51 | `StrategyOrderPreparationService`冻结effective、校验已有binding；V51 `nq_strategy_execution_proof`与projection精确比较unique durable fills和Order.qty；trade环境仍取canonical Order。最终review日志 `backend/nq-app/target/b5-independent-review-attempt02-v49-b2.log` 本轮读回：SIM/LIVE各STALE_PLACE、MULTI_PARTIAL全通过，2 JUnit/0/0/0（含V49补验）；reused25–26与13行账务oracle承接新quantity语义 | RECONCILED |
| B3 ↔ V51 | `StrategyRunRecoveryService`只从durable work继续，gateway回到ordinary prepare/RiskGate/V49；已有MAY无第二许可。bookkeeping projection不解除Kill。reused29 KILL_CREATED和interaction03–04证明新请求拒绝、in-flight仍可恢复 | RECONCILED |
| B4 ↔ V49/V50/V51 | run/order锁、immutable binding、admission唯一性、one-shot决策使重复进入读取既有事实；A/B/C before/after-drop六行区分提交/未提交。`RequiredTradeEventStore`和`TradeLedgerPostingService`相对B4 delivery diff为空，原子fan-out/账务owner未换；最终13行死亡/重放保持1 Trade/1 Event/4 Ledger | RECONCILED |
| B5多进程 ↔ B1–B4 | 最终candidate的34复用、17新增场景及独立proof核对覆盖旧actor、successor、唯一identity、金额和重放；生产manifest一致。未知外部事实仍blocking，不猜终态、不re-arm，不把可用性未决当成功 | RECONCILED |

Cross-batch unresolved=0；`MISSING_CROSS_BATCH_PROOF=0`。以上是针对已接受义务的聚合判断，不扩大为全仓库无缺陷或全平台长期运行保证。

## Inactive / future obligations 与历史替代

当前重新搜索所有 production Java：`ExecutionIntentService`只有类/构造器及内部委托，`claimAndExecute`没有外部production caller；`resumeConsumed`唯一调用仍在retained `MinimalLivePilotConfiguration`。V51策略恢复进入ordinary `StrategyOrderExecutionService`，没有激活typed pilot或fake intent worker。已注册 `ValidationEvidenceScheduler`调用只读 `refreshService.refresh`，不能按名称认作交易scheduler。

| 义务 | 唯一聚合状态 | 重新评估触发条件 |
| --- | --- | --- |
| TYPED：AT-02、LA-01、CFR-01、PFC-02、KIF-02、KIF-03、ESDB-01/02/03/04（均加L4前缀） | NOT_CURRENTLY_ELIGIBLE | 10个retired compatibility profile rows；真实canonical reactivation后重新R1–R4 |
| INTENT_WORKER：L4-MIL-01/02/03、L4-DW-02 | NOT_CURRENTLY_ELIGIBLE | 4个DORMANT_NO_CURRENT_ENTRYPOINT rows；真正接入sender/worker后重评 |
| 历史P1-1/PB1 | NOT_CURRENTLY_ELIGIBLE | RETIRED_COMPATIBILITY_ONLY，不以ordinary新proof宣布typed旧缺陷已修 |
| 历史PB2 | NOT_CURRENTLY_ELIGIBLE | DORMANT_NO_CURRENT_ENTRYPOINT，不以V49替代另一个sender协议的证明 |
| 未注册交易scheduler、不存在的lease/leader | FUTURE_OBLIGATION | 新的实际owner/入口出现时建立专属proof |
| Venue自身重启持久性、L5/L6规模/长期/扩展故障 | FUTURE_OBLIGATION | 按后续范围和授权执行；不推翻B0限定基础结论 |

14个历史inactive scenario均不计PASS；以上分组不加入28个eligible分母。没有为覆盖率复活入口。

下列7组旧失败/不足的覆盖关系为 `SUPERSEDED_BY_ACCEPTED_PROOF`，不是删除历史或将红测改标PASS：

| 历史组 | 状态 | 接受替代 |
| --- | --- | --- |
| C2 candidate starvation | SUPERSEDED_BY_ACCEPTED_PROOF | [C2关闭证据](GATEAUDIT_PHASE6_L4_RUNTIME_CORRECTNESS_C2_STARVATION_REMEDIATION_ATTEMPT01.md)，后续B1/B2 cursor regression；最终V51 legacy有界轮转proof |
| B2 cancel/full-fill与durable环境缺陷 | SUPERSEDED_BY_ACCEPTED_PROOF | [组合review][review2]、[B2][b2]及最终补验 |
| B4 TradeExecuted durability | SUPERSEDED_BY_ACCEPTED_PROOF | [B4 review][review4]、[B4 qualification][b4]，保留首次Trade1/Event0失败 |
| B5 stale sender / venue canonicalization | SUPERSEDED_BY_ACCEPTED_PROOF | [B5最终链][b5]、[venue][venue]、最终V49 proof |
| StrategyRun orphan / same-window duplicate | SUPERSEDED_BY_ACCEPTED_PROOF | [B5][b5]原attempt01/02红色记录继续存在；V50/V51最终proof替代 |
| V51 requested/effective mismatch | SUPERSEDED_BY_ACCEPTED_PROOF | [effective整改][effective]及[最终独立review][review5] |
| Gitleaks / stage-assets144等交付阻碍 | SUPERSEDED_BY_ACCEPTED_PROOF | [B2][b2]引用synthetic整改、[B5][b5]原失败记录、[stage根因关闭][stage]及最终exact-headCI |

Superseded rows=7个历史分组，不能与28个当前义务相加计算覆盖率。旧失败Full Maven/fixture错误、失败CI与后续成功结果保留各自身份；不将单项补验改写为原Full绿色。根因经验的 Synthetic Test Identity Policy、Durable Source Fan-out、External Mutation Finality、Durable Intermediate State Recovery、Crash-Window Completeness、Admission Uniqueness、Effective External Mutation Contract、Stage Asset Lifecycle 均已在[regression-delivery](../../../.agents/skills/nq-trading-correctness-proof/references/regression-delivery.md)及[engineering-lessons](../../../.agents/skills/nq-trading-correctness-proof/references/engineering-lessons.md)存在，其规则与最终实现一致。经验内旧阶段措辞是追加历史，不用于覆盖最终review/CI；本轮不扩写。

## Open P2/P3

| 项目 | 状态与L4影响 |
| --- | --- |
| P2 ordinary concurrent INSERT loser | OPEN / NON_BLOCKING；输家可能事务失败，但数据库唯一性与V49使同command Order/外发最多一个；resume01并发与最终R07保持。未发现升级为重复mutation/accounting或P0/P1的证据 |
| P3 wildcard-import residual | OPEN / NON_BLOCKING；代码规范残余，保留，不以FQCN清理或本次验收宣称零wildcard |
| P0 / P1 | 本次L4聚合范围0 / 0；不清零无关历史或全项目finding |

## Final acceptance decision / next phase

```text
PASS /
L4_B6_AGGREGATE_QUALIFICATION_ACCEPTED /
PHASE6_L4_ACCEPTED /
B0_B1_B2_B3_B4_B5_ACCEPTED /
CURRENT_ELIGIBLE_L4_MATRIX_COMPLETE /
CROSS_BATCH_INVARIANTS_RECONCILED /
INACTIVE_OBLIGATIONS_PRESERVED /
NO_MISSING_MANDATORY_PROOF /
P0_0 / P1_0
```

L4技术状态=ACCEPTED。下一阶段为既定L5/L6；本轮不启动其测试，不创建B6 Independent Review、precise-delivery correctness cycle或B1–B5 requalification。authority/status同步及报告提交留给后续明确任务，本轮没有Git发布授权。

本轮轻量验证：git身份/祖先/remote、GitHub CI只读、manifest及51份proof SHA256、migration SHA256、stage-assets、目标代码/原始review日志/历史EOF备份、报告链接与diff/status。动态测试重跑=0；production/migration/test/CI修改=0。Files changed仅本文件。

[b0]: GATEAUDIT_PHASE6_L4_B0_REAL_PROCESS_HARNESS.md
[b1]: GATEAUDIT_PHASE6_L4_B1_ACCEPTED_TIMEOUT_AND_LOST_ACK.md
[b2]: GATEAUDIT_PHASE6_L4_B2_QUALIFICATION_RESUME.md
[b3]: GATEAUDIT_PHASE6_L4_B3_KILL_IN_FLIGHT_QUALIFICATION.md
[b4]: GATEAUDIT_PHASE6_L4_B4_FAILURE_ATOMICITY_AND_PROCESS_DEATH_QUALIFICATION.md
[b5]: GATEAUDIT_PHASE6_L4_B5_DUPLICATE_COMMAND_SCHEDULER_LOCK_MULTIPROCESS_OWNERSHIP_QUALIFICATION.md
[review2]: l4-b2-qualification-resume-attempt01/combined-independent-review.md
[review4]: l4-b4-qualification-resume-attempt01/review-provenance.json
[review5]: l4-b5-final-qualification-resume/reused-review-original.txt
[proofreview]: l4-b5-final-qualification-resume/independent-proof-review.md
[reused]: l4-b5-final-qualification-resume/reused-proof-index.json
[new]: l4-b5-final-qualification-resume/new-run-index.json
[interactions]: l4-b5-final-qualification-resume/interaction-index.json
[integrity]: l4-b5-final-qualification-resume/candidate-integrity.json
[full]: l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json
[delivery]: GATEAUDIT_PHASE6_L4_B5_PRECISE_DELIVERY.md
[stage]: GATEAUDIT_PHASE6_L4_B5_STAGE_ASSETS_ROOT_CAUSE_REMEDIATION.md
[venue]: GATEAUDIT_PHASE6_L4_B5_VENUE_IDENTITY_CANONICALIZATION_REMEDIATION_ATTEMPT01.md
[effective]: GATEAUDIT_PHASE6_L4_B5_EFFECTIVE_EXECUTION_QUANTITY_CONTRACT_REMEDIATION_ATTEMPT01.md
