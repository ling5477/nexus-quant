# Phase6 L5 聚合资格验收

> 最新正式结论：`PASS / PHASE6_L5_AGGREGATE_QUALIFICATION_ACCEPTED / PHASE6_L5_ACCEPTED`。当前矩阵20行：accepted=1、reused=19、missing=0、invalid=0；cross-batch unresolved=0。L5 qualification已接受，delivery仍未完成。唯一下一动作：`NQ-GATEAUDIT-PHASE6-L5-PRECISE-DELIVERY`。最终依据见末尾“Aggregate Acceptance Resume”；以下原BLOCKED与Kill闭合历史保留。

## 历史：原聚合验收受唯一缺口阻断

Task=`NQ-GATEAUDIT-PHASE6-L5-AGGREGATE-QUALIFICATION-ACCEPTANCE`。
Classification=`HIGH_RISK / AGGREGATE_ACCEPTANCE / EVIDENCE_RECONCILIATION / REVIEW_ONLY / NQ-only`。

**Final decision=`BLOCKED / MISSING_L5_ACCEPTANCE_EVIDENCE`；L5=`NOT_ACCEPTED`。**

三个既有批次的接受事实成立，当前技术候选没有未审漂移；但冻结规划要求的 L5 Kill switch transition 附带证明缺失。不能将进程强杀等同于 Kill switch ENGAGE，也不能把 L4 证明转记为 L5 负载交互证明。本轮没有新动态资格运行，没有修复生产或改写历史。

## 当前身份与审查来源

- Starting/ending HEAD=`991187fe772ad8b03746a4a9ddfc3ea9010e5896`；branch=`audit/post-gatey-agent-baseline`。
- [冻结规划](../GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md) SHA-256=`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`，本轮不变。
- 当前完整入口工作区清单：3907 个 Git tracked/non-ignored untracked 文件，按路径排序，每项记录工作区实际字节 SHA-256；UTF-8 JSON、indent=2、尾随 LF。`backend/nq-app/target/l5-aggregate/entry-inventory.json` SHA-256=`9fa7ac170d1523302cb5b2ba90012969d46cba2b51dc55759fd97cc924b5cb1a`。不含 ignored target；此指纹不冒充 Git tree 或 exact-head CI。
- 当前技术 manifest：`backend/nq-app/target/l5-fault-resume/final-candidate.json`，1838 inputs，SHA-256=`797463469707f83919deb517dcad4efaed329003a972808a7c29a03b1134d126`；主审与独立只读子审查分别计算，mismatch=0。
- [Projection manifest](projection-remediation/candidate-manifest.json) 到 [fill manifest](fill-idempotency-remediation/candidate-manifest.json) 的三项 production 差异为 JdbcTradeRepository、TradeRepository、OkxRestReconcileService，全部由后续 fill 独立审查承接；最终 fill 候选到当前 production/config/POM drift=0。Position/Snapshot、V49/V50/V51、StrategyRun 当前生产输入保持接受身份。后续 harness 差异由 repeated-fault、targeting、resume 批次记录，不能伪称所有历史 run 曾在最终同一字节 harness 上运行。
- Projection 原独立审查：task `01a08f01-ba9a-7af0-866c-a1afeb375a9c`，turn `01a08f01-bd8b-74d0-8b4d-285528319baf`，PASS。原 focused log SHA-256=`9f591ea3dea989aa29dd218c80084b9aa5658fb4677c832e9e008027eab263d2`，保存在系统 Temp 的 `nq-l5-review-h8_rewma`。
- Fill 原独立审查：task `01a08f85-7859-7010-a45f-4a98ac7cb845`，turn `01a08f85-7b68-7721-87a5-a7eb8afe9bdd`，37/0/0/0，明确 C1/C2=`YES / REUSE_ACCEPTED_PROOF`。Temp `nq-l5-fill-independent-review-urhklgly/verification.json` SHA-256=`7f3d2d8c5543c755c17601fe73f991edce0546809c4f1086193d4e2650284f63`；3899 项审查起止一致、copyDrift=[]。
- 本次独立子审查只读检查候选、原任务/原审查输出和 S1/S2/S3 复用；未参与既有实现，无测试/文件修改。它不替代上述原生产审查。

## Canonical L5 matrix

主来源：[bounded](BOUNDED_WORKLOAD_AND_MEASUREMENT.md)、[concurrent](CONCURRENT_WORKLOAD_AND_BACKLOG.md)、[repeated fault](REPEATED_FAULT_UNDER_LOAD.md)、[summary](summary.json)、[projection remediation](CONCURRENT_POSITION_AND_ACCOUNT_PROJECTION_REMEDIATION.md)、[fill remediation](CONCURRENT_FILL_IDEMPOTENCY_REMEDIATION.md)。summary 中旧顶层 decision/l5Accepted 是历史批次状态；按各批次命名对象及追加章节定位，不改旧对象。

| Row | Disposition | 接受依据/边界 |
| --- | --- | --- |
| S1 | REUSED_ACCEPTED_PROOF | 并发1、120候选、Position12、最终backlog0 |
| S2 | REUSED_ACCEPTED_PROOF | 并发2、120候选、Position12、最终backlog0 |
| S3 | REUSED_ACCEPTED_PROOF | 并发4、240普通候选加12策略订单、Position25.2、4策略×3窗口 |
| C1 | REUSED_ACCEPTED_PROOF | 2 JVM、120候选；fill独立审查明确允许复用 |
| C2 | REUSED_ACCEPTED_PROOF | 4 JVM、240候选；fill独立审查明确允许复用 |
| C3 | REUSED_ACCEPTED_PROOF | 4 JVM、228普通+12策略；正式恢复资格通过 |
| F1-R1/R2/R3（3 rows） | REUSED_ACCEPTED_PROOF | 三个独立fresh run；timeout/lost ACK、query-first |
| F2 exact-R1/R2/R3（3 rows） | REUSED_ACCEPTED_PROOF | 三个精确Trade-post-commit边界；R1为targeting稳定完整proof，原driver slot2仅计一次 |
| F3-R1/R2/R3（3 rows） | REUSED_ACCEPTED_PROOF | before COMMIT一次、after COMMIT两次；新DB/进程/工作身份 |
| F4 combined | REUSED_ACCEPTED_PROOF | 主F2-R3及其他完整run支持；后续resume任务明确允许组合复用，不重复计算run |
| Position/Snapshot remediation review | REUSED_ACCEPTED_PROOF | 独立接受；非scale run |
| Fill idempotency remediation review | REUSED_ACCEPTED_PROOF | 独立接受、真overfill仍拒绝；非scale run |
| Deterministic targeting readiness | REUSED_ACCEPTED_PROOF | 精确身份/提交窗口/owner、harness不写业务事实；其完整run仅计F2一次 |
| L5 Kill switch transition under fault/load | MISSING | 规划要求新发送拒绝、既有in-flight/账务收敛，未找到现有L5证明 |

当前 eligible matrix：20 rows；ACCEPTED_PROOF=0、REUSED_ACCEPTED_PROOF=19、MISSING=1、INVALID=0。本轮是复核既有接受，故不把离线检查记作新的动态 ACCEPTED_PROOF。

历史 superseded disposition 另计6组，不计当前PASS：(1) bounded setup/rate/pacing/早期候选；(2) C1 fixture及真实lost-update FAIL；(3) C3 false-overfill FAIL/未完成；(4) repeated-fault targeting/export/input BLOCKED attempts；(5) 旧F2 AFTER_VENUE_ACCEPTED/V51_B不同边界观察；(6) remediation红色复现、编译/cleanup及失败Full Maven。各组=`SUPERSEDED_BY_ACCEPTED_PROOF`，只表示后续相关证明替代其当前用途，原始FAIL/ERROR/NOT_RUN不变。第5组原正确性观察仍有效，但不属于精确Trade-post-commit三次证明。

S1/S2/S3 raw/log哈希全部匹配；独立重算旧ts和新snapshot_id排序均选择同一BTC/USDT行，原数值不被排序修复否定。串行reconciliation下的bounded测量仅在原覆盖范围复用，不能用它反驳后续真实并发失败；当前更高风险由修复后C1/C2/C3和fault proof承接。

## 跨批次事实

| Invariant | 已接受证据的聚合事实 |
| --- | --- |
| Scale ↔ Projection | C1/C2/C3精确重建Position=12/24/24；Snapshot对应当前canonical projection，无lost update |
| Fault ↔ Projection | 9个fault run各240 Trade/240 TradeExecuted/960 Ledger，Position BTC24、latest BTC24/USDT0；restart/replay不增写、不丢投影 |
| Reconciliation ↔ Fill idempotency | C3 33个已成交Order跨actor重叠观察、98对区间，false overfill=0、reconciliation error=0；原真实overfill拒绝证明复用 |
| Scale ↔ V49/V50/V51 | 每logical identity一次PLACE；4策略×3窗口各一run/Order/work，12 SUCCEEDED，durable orphan=0 |
| Fault ↔ Backlog | 各fault run initial60+new180，峰值暂增，恢复后actionable=0、unresolved=0 |

五项指定交互未发现矛盾（observed unresolved=0）；**全规划覆盖 unresolved=1**，即Kill transition缺口。不能无条件写 CROSS_BATCH unresolved=0。

Venue Fill → Trade → TradeExecuted → Ledger → Position → Account Snapshot：既有关系oracle逐身份验证，duplicate Trade/TradeExecuted/Ledger=0；BTC从Trade signed qty/base fee独立重建，USDT按canonical Ledger SUM(delta)核对，不把成对Ledger净额误作BTC数量。same-fill replay、真实COMMIT ambiguity、新PID recovery与多JVM竞争由原整改审查和L5组合证据相互支持。

C3和fault策略路径实际requested=0.1005、effective=0.100；wire=durable Order/work effective quantity，Trade/accounting绑定实际成交，FILLED且phantom residual=0。不是只借用L4 V51声明。

cursor/fairness：C1/C2/C3 coverage=120/240/240、reservations=46/135/153；旧60未成交时新单仍推进。fault coverage每轮240，cursor持久revision跨正常restart和强杀恢复保留。candidate starvation=0仅指已测候选；ACTIONABLE最终0。CORRECTNESS_REQUIRED_UNRESOLVED单列，既有接受run最终0，不偷偷当已处理。持续采样显示订单/Trade/Ledger增长及恢复后继续推进，已测run无alive-but-stalled；Kill transition期间forward progress仍是missing，不能外推。

F1=3/3、F2 exact=3/3、F3=3/3、F4 combined=PASS。F2 reader确认目标Trade+required Event已提交、目标Ledger=0，六步handshake和exact owner PID后才kill；三组完整业务快照证明控制器本身未改事实。短时gate暂停qualification消费者，随后放行owner；不宣称切断瞬间四actor同时消费。F3明确区分未转发COMMIT与服务端提交但响应被扣留，replacement按durable ACK truth恢复，无重复PLACE。

## 资源、遗留项和阻断

DB pool max10/JVM、app connections<=40、active峰值1/JVM、waiting峰值0；threads峰值S=19、C=20、恢复fault=21。driver queue<=1，Venue workers<=4/queue<=16，无runaway。正常存活JVM末尾active/pending/queue归零，被强杀JVM按实际死亡验收，不能伪造其最后采样为空闲。各接受run清理所属JVM/Venue/PG/proxy及端口，未见跨批次单调连接/线程积累；曾有cleanup缺口及人工回收的失败历史保留，最终fixture修复已有独立证明。

这是短时bounded resource proof；fresh DB重复不等于同一数据库多日稳定，不替代L6 duration/heap/GC/handles/真实timer等验收。

| Open item | 等级与 disposition |
| --- | --- |
| ordinary concurrent INSERT loser | P2 / OPEN / NON_BLOCKING；与已修复same-fill false-overfill是不同问题，未证明使本次接受run失效；不是P0/P1 |
| wildcard-import residual | P3 / OPEN / NON_BLOCKING；样式残余不改变本次运行事实，不是P0/P1 |
| HISTORICAL_PROJECTION_REPAIR_REQUIRED | OPEN / NON_BLOCKING_FOR_L5_ACCEPTANCE；当前资格用clean isolated DB，新候选精确重建通过，旧损坏不污染本批证据；不是当前P0/P1，绝不记CLOSED |
| Kill transition证据缺失 | BLOCKING_EVIDENCE_GAP；不是已证实production P0/P1，不由卫生PASS解除 |

Historical repair必须进入pre-freeze/release baseline verification：停止旧writer，独立核对现有Position/Snapshot与source；有差异再取得数据修复授权，禁止混跑旧新writer或默认replay已修旧数据。

Bounded observations：线程终值高于刚启动、driver/采样开销、fault gate短时暂停、单账户BTC/USDT、USDT既有投影语义均保留为范围限制；未升级为新P0/P1，也未据此宣称普遍性能或长期无泄漏。targeting原P3 qualification blocker已由精确证明关闭；其他失败历史不清零。

Inactive/future：继承L4 B6的14项NOT_CURRENTLY_ELIGIBLE/FUTURE_OBLIGATION，不计PASS；额外 >4 JVM、>240 initial candidates（S3后续12策略不是扩大普通candidate预算）、real exchange、LIVE、multi-day stability均FUTURE_OBLIGATION。L6承担duration/stability，不能把本次mandatory Kill缺口降为future。

缺口直接来源：冻结规划§L5 fault（第97行）要求“Kill transition 仅在第2组最后一个 chunk 附带一次：新发送被拒绝、既有 in-flight/账务可收敛”；批次表第107行再次要求Kill附带。现有L5报告、summary、harness及原后续任务中未找到证明或明确豁免。后续F4组合复用授权不等于取消Kill requirement。

## 本轮检查与后续入口

只读哈希检查：38个已登记raw/log locator匹配，另S1/S2/S3六个raw/log匹配，共44项；missing=0、invalid=0（这是文件完整性计数，不覆盖上文语义missing=1）。对15个现有accepted raw执行现有业务/concurrent/fault一致性函数，全部PASS；未生成负载、故障或数据库事实。初次本地检查器错误地禁止projection→fill已审production差异，随后修正继承比较；另一次读取旧S摘要不存在的rawLocator字段失败，改为从原log定位并核对既有hash。两者不是生产或资格失败，未改原证据。

收尾检查：stage-assets=1903 scanned / 173 reviewed exceptions / errors=0；固定Gitleaks 8.18.4的包hash/version及实际CI配置核对通过，全部61个modified/untracked文件findings=0；apiKey/secret/token/password/Authorization/credential六类export后negative全部REJECT、字段未被隐藏；links=9 checked / warnings=0 / errors=0；git diff --check及新增报告尾随空白检查PASS。3907个入口文件再次逐字节核对，drift=0；staged paths=0。

本轮唯一非ignored新增文件为本文；工具结果留`backend/nq-app/target/l5-aggregate/`，不复制raw logs。既有文件字节保持不变，stage=0、commit=NONE、push=NONE。S/C/F动态测试、Full Maven、new fault tests均NOT_RUN。

P0=0、已证实P1=0；不存在用missing=1换取L5 ACCEPTED的条件。下一步先定位并提供该既有Kill proof或明确处置冻结规划与现有范围差异；没有动态重跑授权。不进入precise delivery或L6。补齐后须重新完成本聚合验收；成功后的唯一交付动作才是`NQ-GATEAUDIT-PHASE6-L5-PRECISE-DELIVERY`，取得delivery exact-head CI green后，按冻结规划进入`L6-A ACTIVE-STABILITY`，再`L6-B RESTART-CONTINUITY-AND-AGGREGATE`。本次不改STATUS/current authority，也不把L4 CI当L5交付CI。

## Kill transition under load：唯一缺口闭合（2026-09-11）

Task=`NQ-GATEAUDIT-PHASE6-L5-KILL-TRANSITION-UNDER-LOAD-EVIDENCE-CLOSURE`；classification=`HIGH_RISK / QUALIFICATION_EVIDENCE_CLOSURE / KILL_UNDER_LOAD / NQ-only`；review=`SELF_REVIEWED`。

**PASS / PHASE6_L5_KILL_TRANSITION_UNDER_LOAD_PROVEN / READY_TO_RESUME_L5_AGGREGATE_ACCEPTANCE**。此为新授权的单项资格结论，不覆盖上文聚合失败历史，也不直接授予L5 ACCEPTED。

### 候选与 canonical 路径

Starting HEAD仍为`991187fe772ad8b03746a4a9ddfc3ea9010e5896`，branch未变；入口3908个文件完整fingerprint=`7b464ff76ca8bb32d1dc1e1466ea4cf6bee1ffba0e5f8b0a41704244db0ff975`。1838项上一已接受技术manifest全部匹配后才改harness；本轮production/migration/.github/current authority delta=0，规划hash仍为`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`。

运行前冻结1842项技术manifest=`backend/nq-app/target/l5-kill-closure/candidate-attempt01.json`，SHA-256=`bb90c14d88e52bb5121447f6c8127c0e95cb18ccc9c39f35322ea2af2a3a3b68`。最终manifest=`backend/nq-app/target/l5-kill-closure/final-candidate.json`，SHA-256=`67e6b158af9db95d84356f864cfe3045d264fe67948b76a7c7db4e5aa7b68b04`；两者唯一差异是新增的只读`l5_kill_oracle.py`。Java运行候选、fixture及raw未变，最终oracle在原始完整raw上验证，不把失败Maven写成BUILD SUCCESS。

Kill canonical owner为[KillSwitchService](../../../../backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchService.java)；专用L5测试入口调用`engage(expectedVersion, L5_LOAD_STOP, L5_QUALIFICATION, l5-kill-transition)`，进入[JdbcKillSwitchStateRepository](../../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/risk/infra/jdbc/JdbcKillSwitchStateRepository.java)的真实事务、row lock/version CAS及append-only审计事件。未直接SQL更新运行期Kill、未改bean业务状态、未伪造RiskGate结果。只有fixture在NQ启动前初始化DISENGAGED，沿用B0封存与最小ENGAGE权限。

[OrderCommandWriteService.preparePlaceOrder](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java)调用真实RiskGate；[KillSwitchRiskRule](../../../../backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchRiskRule.java) order=10，读取durable snapshot并返回`KILL_SWITCH_TRIGGERED`。拒绝在V49发送资格与Venue调用之前返回。Kill是admission-time gate，不伪造CANCEL，也不拦截必要query/reconciliation或已存在run的bookkeeping。本轮没有重审B3或改Kill语义。

### 一个代表运行及事实链

新建loopback PostgreSQL16.15/V51、独立受控Venue、2个NQ JVM、2个真实reconciliation actor。Windows/Java21.0.9/Maven3.9.12/Docker29.7.2；开始可用内存约27.7GiB，沿用NQ512MiB/JVM、Venue256MiB、PG768MiB/tmpfs256MiB、Maven512MiB/Surefire768MiB有界预算。没有真实provider、LIVE、生产数据访问。

先创建60个正常未成交订单；随后持续2路投喂另外60个普通订单，后台actor对账新成交，同时旧60保持backlog。最后一个真实策略scanner窗口产生第121个已接受订单，策略当时RUNNING；它与旧60个工作在Kill后成交并恢复。没有4策略×3窗口扩展，requested/effective均0.1，未增加非mandatory归一化场景。

| 时点/结果 | 已验证事实 |
| --- | --- |
| Kill前 | 121 Order/Venue PLACE，60 Trade/TradeExecuted、240 Ledger，actionable61、unresolved0；持续业务与对账进度已出现 |
| Admission分类 | 121个工作都已获得ACCEPTED返回且在ENGAGE前的Order/Venue完整快照中存在；其余4个新身份仅在durable ENGAGED readback后提交 |
| Transition | `2026-09-11T21:29:32.512959+08:00`，DISENGAGED version2 → ENGAGED version3；真实OPERATOR_ENGAGE事件1条，原Flyway安全bootstrap事件1条独立保留 |
| 新命令 | logical121–124普通命令跨两个JVM提交，4/4 RISK_REJECTED，reason均KILL_SWITCH_TRIGGERED；与策略client无身份重合 |
| 新命令副作用 | Venue calls=0、PLACE/CANCEL=0；4条V49均NOT_ARMED/decided_at=NULL，MAY_HAVE_ESCAPED=0 |
| Kill后恢复 | 先前61个live订单正常FILL后，逐订单QUERY_FILLS序列晚于成交；真实对账调用在durable ENGAGED后新增Trade，StrategyRun RUNNING→SUCCEEDED |
| 最终Order/V49 | 125 Order=121 FILLED+4 RISK_REJECTED；125 V49=121 MAY_HAVE_ESCAPED+4 NOT_ARMED |
| 最终账务 | Trade121、TradeExecuted121、Ledger484、LedgerEvent484、Snapshot242；无重复或丢失 |
| Projection | 独立从Trade signed qty/base fee重建BTC12.1，Position.qty/available=12.1，latest Snapshot BTC12.1/USDT0；当前snapshot_id排序 |
| StrategyRun | 1个run、1条durable work、1个窗口；SUCCEEDED，无busy/orphan |
| Backlog | initial60 / peak66 / transition61 / final0；correctness-required unresolved始终独立分类、最终0 |
| Forward progress | 130个业务采样、284个JVM资源采样；新mutation停止后，Trade60→121、Ledger240→484、run0→1；drain约2.555s，整run约46.707s，不作为latency SLA |

不存在按“Kill timestamp之后出现任何PLACE”判错的规则。本次全部121个合法PLACE在切换前已确认；允许pre-admitted work按现有契约继续完成，不将其与4个切换后新身份混合。切换后采样及最终readback均ENGAGED version3，未再次解除Kill。

### 验证与失败保留

唯一完整运行raw=`backend/nq-app/target/l5-kill-closure/run-23e7ec18-7850-43b3-bea5-fa56d4e5aa01/raw-proof.json`，SHA-256=`c3015d731bb5745833c01c1a14df72a4dc5e49a6708cf135db34a7c7d32086ad`；紧凑canonical输出见[KILL-UNDER-LOAD.json](runs/KILL-UNDER-LOAD.json)，批次新增于[summary](summary.json)的`killUnderLoadClosureBatch`，所有旧对象值不变。

正式命令为：

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am test '-Dtest=L5KillUnderLoadTest,L5KillContractTest,L5DriverContractTest,L5FixtureCleanupTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xmx768m' '-Dnq.l5.kill=true' '-Dnq.l5.level=K1' '-Dnq.l5.fill=true'
```

该Maven实际`8 tests / 0 failures / 1 exporter ERROR / 0 skips / BUILD FAILURE`：完整动态workload已收敛并清理，但新增oracle错误地要求整个Kill事件表只有一条事件。随后源码/事实核对还发现第二个错误假设：拒绝订单应无V49行；V49现行合同实际要求NOT_ARMED行。已改为分别验证Flyway bootstrap、本次唯一ENGAGE，以及拒绝身份NOT_ARMED/未获得发送权；未放宽mutation、账务或Kill安全约束。

最终只读oracle在原raw通过，18/18业务负例、17/17Kill/时序/拒绝/恢复/敏感字段负例全部REJECT；包含错误bootstrap、额外transition、新命令获发送权、无恢复query和无法证明pre-admission的拒绝。Java及运行输入不变、raw起止哈希相同，没有第二次负载运行。原失败log SHA-256=`e86b5c38ac191f42b73dcd191b2197b872985e6c5c9f26bfeeb51b7b8c381d8c`；`attempt01-surefire-and-log.zip` SHA-256=`f08974cd0cbfba5cb3df8464a061d1e24974539f479bc652ff51b06af42b9c81`保留全部本次XML及失败log。动态完成+最终离线验证构成本次证明，未将Maven失败改记通过。

3个命令合同测试和4个cleanup测试均PASS；cleanup覆盖PASS/assertion/exception/closed-stdin/setup failure，使用新的Kill JVM入口，逐fixture owned NQ/Venue/PG=0。独立末尾核对本命令16个PID均无存活；5个所属PG容器均清理，正式run的PG/Venue端口释放。teardown终止所属进程属于清理，未作运行中fault injection。pool max10/JVM、active峰值1、waiting0、threads峰值20、driver queue<=1、Venue queue<=16；最终池/队列静止，无永久资源stall。资源结论仅限该短run。

既有15份接受raw用当前测量oracle做只读正例回归，全部PASS且原文件哈希不变；这是oracle分支兼容性检查，没有重跑S1–S3/C1–C3/F1–F4/L4 B3。Full Maven=NOT_RUN。生产、migration及安全语义未变，按本次用户review policy仅SELF_REVIEWED，不新增Independent Review。

本轮代码范围为5个既有test/tooling文件及4个新增test/tooling文件；证据仅修改本文、summary并新增一个run摘要。普通INSERT loser P2、wildcard-import P3、historical projection repair及inactive/future义务全部保留原处置。P0=0、P1=0；只读oracle错误不虚报生产P1。

收尾卫生：stage-assets=1907 scanned / 173 reviewed exceptions / errors0；Gitleaks8.18.4固定包、version与当前CI配置核对后扫描全部66个modified/untracked文件，findings0；六类secret negatives全部REJECT且export不隐藏字段；links=15 checked / warnings0 / errors0；git diff --check PASS。最终1842项技术manifest逐字节匹配，所有旧summary对象保持原值；所属16个PID与5个容器的末尾只读查询均remaining0。检查结果保存在`backend/nq-app/target/l5-kill-closure/`。

Final decision=`PASS / PHASE6_L5_KILL_TRANSITION_UNDER_LOAD_PROVEN / POST_KILL_NEW_MUTATION_0 / RECOVERY_UNDER_KILL_CONVERGED / ACTIONABLE_BACKLOG_CONVERGED / ACCOUNTING_CORRECTNESS_PRESERVED / P0_0 / P1_0 / READY_TO_RESUME_L5_AGGREGATE_ACCEPTANCE`。

L5=`NOT_ACCEPTED`。唯一下一动作=`NQ-GATEAUDIT-PHASE6-L5-AGGREGATE-QUALIFICATION-ACCEPTANCE-RESUME`。stage=0、commit=NONE、push=NONE；不进入precise delivery或L6。

## Aggregate Acceptance Resume（2026-09-11）

Task=`NQ-GATEAUDIT-PHASE6-L5-AGGREGATE-QUALIFICATION-ACCEPTANCE-RESUME`；classification=`HIGH_RISK / AGGREGATE_ACCEPTANCE / EVIDENCE_RECONCILIATION / REVIEW_ONLY / NQ-only`。

本轮直接复用原聚合已确认的19个proof rows与6组历史处置，不重新审查生产整改、S/C/F证明或引入Independent Review。唯一矩阵变化为Kill-under-load：`MISSING → ACCEPTED_PROOF`，来源是上一闭合任务已经接受的运行，不是本轮新动态测试。

起止branch=`audit/post-gatey-agent-baseline`、HEAD=`991187fe772ad8b03746a4a9ddfc3ea9010e5896`、stage=0。入口完整3913文件fingerprint=`8212b7280404172342a26255aedb58d1b942b54221a3b36a5c4599aa0802a112`，与上一闭合任务收尾逐字节一致；清单留`backend/nq-app/target/l5-aggregate-resume/entry-inventory.json`。当前技术manifest仍为1842项，SHA-256=`67e6b158af9db95d84356f864cfe3045d264fe67948b76a7c7db4e5aa7b68b04`，mismatch=0；production/migration drift=0。规划SHA-256仍为`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`，未改规划或current authority。

### 当前 canonical L5 matrix

| Row | Disposition | Source |
| --- | --- | --- |
| S1 | REUSED_ACCEPTED_PROOF | 原聚合 / bounded S1 |
| S2 | REUSED_ACCEPTED_PROOF | 原聚合 / bounded S2 |
| S3 | REUSED_ACCEPTED_PROOF | 原聚合 / bounded S3 |
| C1 | REUSED_ACCEPTED_PROOF | 原聚合 / C1-resume；既有独立审查允许复用 |
| C2 | REUSED_ACCEPTED_PROOF | 原聚合 / C2-resume；既有独立审查允许复用 |
| C3 | REUSED_ACCEPTED_PROOF | 原聚合 / C3-resume-accepted |
| F1-R1 | REUSED_ACCEPTED_PROOF | 原聚合 / repeated-fault resume |
| F1-R2 | REUSED_ACCEPTED_PROOF | 同上 |
| F1-R3 | REUSED_ACCEPTED_PROOF | 同上 |
| F2 exact-R1 | REUSED_ACCEPTED_PROOF | 原聚合 / deterministic完整proof，仍只计一次 |
| F2 exact-R2 | REUSED_ACCEPTED_PROOF | 原聚合 / repeated-fault resume |
| F2 exact-R3 | REUSED_ACCEPTED_PROOF | 同上 |
| F3-R1 | REUSED_ACCEPTED_PROOF | 原聚合 / before COMMIT |
| F3-R2 | REUSED_ACCEPTED_PROOF | 原聚合 / after COMMIT |
| F3-R3 | REUSED_ACCEPTED_PROOF | 同上，独立fresh run |
| F4 combined | REUSED_ACCEPTED_PROOF | 原聚合 / 已允许组合复用 |
| Position/Snapshot remediation review | REUSED_ACCEPTED_PROOF | 原聚合已接受审查；不是scale run |
| Fill idempotency remediation review | REUSED_ACCEPTED_PROOF | 原聚合已接受审查；不是scale run |
| Deterministic targeting readiness | REUSED_ACCEPTED_PROOF | 原聚合 / 精确身份与durable边界 |
| KILL_SWITCH_TRANSITION_UNDER_LOAD | ACCEPTED_PROOF | [KILL-UNDER-LOAD.json](runs/KILL-UNDER-LOAD.json)；唯一新纳入的已接受proof |

Eligible=20、ACCEPTED_PROOF=1、REUSED_ACCEPTED_PROOF=19、MISSING=0、INVALID=0。原6个`SUPERSEDED_BY_ACCEPTED_PROOF`历史组仍单列，不计当前PASS，其行状态和原始失败不变。Kill exporter错误亦保留为闭合任务中的工具失败，未用本轮接受擦除。

### Kill 与跨批次一致性

Kill canonical run摘要SHA-256=`e62f334a77870f7f27500ccfcafb31acc7edc9dbe31205e646c86424815fe4d8`；与summary闭合批次引用一致。原raw SHA-256=`c3015d731bb5745833c01c1a14df72a4dc5e49a6708cf135db34a7c7d32086ad`、原失败log SHA-256=`e86b5c38ac191f42b73dcd191b2197b872985e6c5c9f26bfeeb51b7b8c381d8c`及suite归档均重新核对匹配。runtime→final manifest只有已登记只读oracle变化，生产/Java运行候选不变；原log仍为BUILD FAILURE，后续只读oracle已在同一未修改raw通过。接受的是完整运行事实和已接受的oracle-only remediation，不把Maven失败改成成功或归因生产P1。

`DISENGAGED v2 → KillSwitchService.engage → ENGAGED v3`及durable事件匹配；4个新普通身份全部在ENGAGED readback后提交，4个Risk rejects，post-Kill new Venue mutation=0、对应V49 MAY_HAVE_ESCAPED=0。121个pre-admitted身份单列，切换时actionable61；ENGAGED下query/reconcile与账务、投影、StrategyRun恢复继续，final backlog0、Trade121、TradeExecuted121、Ledger484、Position/latest BTC Snapshot12.1，USDT0。duplicate mutation/accounting、lost work、durable orphan均0。

| Cross-batch | 聚合结果 |
| --- | --- |
| Scale ↔ Projection | ACCEPTED；复用原修复后精确重建与无lost update事实 |
| Fault ↔ Projection | ACCEPTED；复用原restart/replay后无重复、无丢失事实 |
| Reconciliation ↔ Fill idempotency | ACCEPTED；same-fill重复观察不产生false overfill或batch abort |
| Scale ↔ V49/V50/V51 | ACCEPTED；one-shot mutation、window唯一、durable lifecycle保持 |
| Fault ↔ Backlog | ACCEPTED；恢复后actionable收敛 |
| Kill-under-load ↔ Recovery | ACCEPTED；新mutation停止，既有工作继续，backlog归零且账务精确 |

Cross-batch unresolved=0。四个批次bounded/concurrent/repeated-fault/Kill-under-load均ACCEPTED；Position/Snapshot=EXACT、actionable backlog=CONVERGED、candidate fairness=PRESERVED、forward progress=PRESERVED、fault recovery=CONVERGED。合法CORRECTNESS_REQUIRED_UNRESOLVED仍独立分类，不伪装处理完成。资源结论为BOUNDED_FOR_L5_SCOPE，短时fresh DB重复不替代L6 duration/stability。

### 保留项与交付边界

`HISTORICAL_PROJECTION_REPAIR_REQUIRED / OPEN / NON_BLOCKING_FOR_L5_ACCEPTANCE`保持不变；clean isolated qualification数据通过不表示历史deployment数据正确。pre-freeze/release baseline verification仍需独立核对source与已有投影，必要repair另行授权，不能标CLOSED。

ordinary concurrent INSERT loser P2、wildcard-import P3与其他既有bounded observations仍按原OPEN/NON_BLOCKING处置；不是P0/P1、不使当前L5证明失效，未清零。原14个inactive/future rows及 >4 JVM、>240 candidates、real exchange、LIVE、multi-day stability、retired/dormant路径继续NOT_CURRENTLY_ELIGIBLE/FUTURE_OBLIGATION，不计PASS。

本轮只更新本文与summary：summary新增`aggregateAcceptanceResumeBatch`，保存旧顶层状态后更新顶层task/decision/l5Accepted，所有旧批次对象原值保留。原BLOCKED缺口和Kill闭合时NOT_ACCEPTED都是历史时点，不覆盖本节最终资格结论。没有运行S/C/F/Kill workload、Full Maven或新chaos；仅运行identity/hash、轻量evidence consistency与卫生检查。

本轮卫生：stage-assets=1907 scanned / 173 reviewed exceptions / errors=0；固定Gitleaks8.18.4包hash/version与实际CI配置核对通过，全部66个modified/untracked文件findings=0；secret negatives=6/6 REJECT且字段未被export隐藏；links=16 checked / warnings0 / errors0 / PASS；git diff --check=PASS。候选/证据核对与卫生输出位于`backend/nq-app/target/l5-aggregate-resume/`。除本文与summary外，入口文件字节不变；technical/production/migration drift=0、staged paths=0。

```text
PASS /
PHASE6_L5_AGGREGATE_QUALIFICATION_ACCEPTED /
PHASE6_L5_ACCEPTED /
BOUNDED_SCALE_ACCEPTED /
CONCURRENT_SCALE_ACCEPTED /
REPEATED_FAULT_UNDER_LOAD_ACCEPTED /
KILL_TRANSITION_UNDER_LOAD_ACCEPTED /
ACCOUNTING_PROJECTION_SCALE_CORRECTNESS_ACCEPTED /
BACKLOG_AND_FAIRNESS_ACCEPTED /
MULTIJVM_RECOVERY_UNDER_LOAD_ACCEPTED /
NO_DUPLICATE_MUTATION /
NO_DUPLICATE_ACCOUNTING /
NO_DURABLE_ORPHAN /
P0_0 /
P1_0
```

L5 correctness/qualification=`ACCEPTED`；delivery=`NOT_DELIVERED / EXACT_HEAD_CI_PENDING`。stage=0、commit=NONE、push=NONE。本轮没有发布授权动作或authority sync；唯一下一动作=`NQ-GATEAUDIT-PHASE6-L5-PRECISE-DELIVERY`，L5 delivery commit取得exact-head CI green后才可进入L6。
