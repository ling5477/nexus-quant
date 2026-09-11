> 最新正式结果：C1/C2 复用已接受 PASS，C3 新资格运行 PASS；详见末尾“仅 C3 恢复资格接受”。历史 C1 projection FAIL、C3 false-overfill FAIL 及处置记录保留。

# Phase6 L5 concurrent workload 与 backlog：C1失败后停止

Task：`NQ-GATEAUDIT-PHASE6-L5-CONCURRENT-WORKLOAD-AND-BACKLOG`。
Classification：`HIGH_RISK / SCALE_QUALIFICATION / CONCURRENT_RECONCILIATION / BACKLOG_CONVERGENCE / NQ-only`。

**FAIL / PHASE6_L5_CONCURRENT_SCALE_CORRECTNESS_FINDING / PRODUCTION_REMEDIATION_REQUIRED / STOP**。

C1在最终候选下发现持久化持仓丢失；C2/C3未运行，L5仍NOT_ACCEPTED。不得进入REPEATED-FAULT-UNDER-LOAD。下一步是另立生产修复任务，修复并独立审查后重新完成C1–C3；本批不修生产、不另建任务或发布。

## 基线与授权边界

Starting/ending HEAD=`991187fe772ad8b03746a4a9ddfc3ea9010e5896`，branch=`audit/post-gatey-agent-baseline`。L4仍ACCEPTED，technical SHA=`3d103cea2072b3c2d9d1009cc5841c18a958ee80`，既有CI=`34501806297 / 9 of 9 SUCCESS`；没有重开L4或改写current authority。上一批[bounded测量](BOUNDED_WORKLOAD_AND_MEASUREMENT.md)保留为历史通过，不把它用于否定本批失败。

[规划文档](../GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md)起止SHA-256均为 `80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`，byte-for-byte不变。

production/migration/current authority delta=0；stage=0、commit=NONE、push=NONE。无真实交易所、credentials、LIVE、真实资金或主动故障注入。Docker29.7.2的轻量create/inspect/remove/readback通过，本批没有API桥接故障。

## P1：同账户/品种的持仓更新丢失

最终C1实测同一SIM账户BTC-USDT：

| Durable fact | 实测 |
| --- | --- |
| Order / V49 / Venue PLACE | 各120 |
| Order terminal / version | 全部FILLED / version4 |
| Trade / TradeExecuted | 各120，逐身份唯一 |
| BUY成交数量合计 | 12.0 BTC（120×0.1） |
| Ledger entry / Ledger event | 480 / 480；逐Trade四条事实及金额校验通过 |
| 最终Position.qty / available_qty | 11.7 / 11.7 BTC |
| 丢失的持仓增量 | **0.3 BTC** |
| canonical账户查询排序下最新BTC snapshot | 11.6 BTC |
| 最终actionable / unresolved backlog | 0 / 0 |
| 正常reconcile/recovery重放 | 持久化业务事实不变，未补回差额 |

没有SELL或BTC计价手续费可解释差额：所有Trade的Order为BUY，fee_currency为USDT。没有重复Trade、TradeExecuted或Ledger fact，但**整体账务/持仓正确性失败**；不得把重复计数0或backlog归零写成资格PASS。不存在Order丢失，不等于不存在持仓投影工作丢失。

生产路径及机制：

1. [TradeLedgerPostingService.updatePositionProjection](../../../../backend/nq-ledger/src/main/java/com/guidinglight/nexusquant/ledger/service/TradeLedgerPostingService.java)先读取当前Position，在Java中计算`current.qty + qtyChange`，再写入新绝对数量。
2. [JdbcLedgerPostingRepository.findPosition/upsertPosition](../../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerPostingRepository.java)读取不带FOR UPDATE或version，upsert冲突时执行`qty=EXCLUDED.qty`，没有CAS/原子增量或同账户品种串行化。
3. 两个JVM处理不同Order时可读取相同旧Position，各算一次增量，再由后写覆盖先写。这与真实Trade总量12.0而Position11.7的丢更新事实一致。现有每Trade幂等早退不会从完整Trade事实重建已错的Position，因此正常重放不修复。

本次没有记录每一次丢更新的事务指令级交错，不声称已逐笔定位全部三次覆盖；持久化差额和可达实现机制已足以阻断资格。账户snapshot另有11.6的伴随不一致：[查询实现](../../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/query/JdbcTradingQueryFacade.java)按ts、snapshot_id降序选最新。后续修复须一并核对快照与持仓的关系，不在本批推断它必然只有同一个根因。

Finding=`P1_L5_CONCURRENT_POSITION_LOST_UPDATE`，P0=0、P1=1。既有P2/P3保持OPEN/NON_BLOCKING，没有修复或扩展它们。没有将本批称为SELF_REVIEWED acceptance；这里只完成失败分析与测试工具自查，没有生产修复或Independent Review。

## C1实际竞争、cursor与资源

复用原L5BoundedWorkloadTest、B0Processes/B0Fixture、Synthetic Venue、250ms采样、业务oracle及IdentityMapper；没有第二套load harness。

先正常创建60个未成交旧订单；随后两个独立NQ JVM共享同一PG和Venue，各执行真实reconcileOnce(40)，同时继续产生60个新订单。新订单正常成交，旧前缀保持未成交；生产者结束后才成交旧前缀并等待归零。每JVM一个公平调度slot，只限制qualification投喂，业务调用总并发不超过2；它不提供跨JVM持久化互斥，不替代数据库正确性。

| C1 observation | 实测（不能覆盖P1失败） |
| --- | --- |
| reconciliation actors / 同时活跃NQ JVM | 2 / 2 |
| NQ进程生命周期数 | 3；其中一次在工作完成后正常退出、替换启动，最大同时仍2 |
| producer | 每波2个identity，至少300ms/波；后半程60单耗时13.159s |
| initial / new / peak backlog / final actionable | 60 / 60 / 64 / 0 |
| producer停止后drain及停止actor耗时 | 3.203s；不是SLA |
| 总run耗时（setup/cleanup在内） | 48.426s |
| reservations / wraps / distinct candidates | 48 / 21 / 120 |
| duplicate observations | 1860；不是duplicate business mutation |
| 旧60个仍未成交时，新订单已FILLED | 56 |
| cursor starting / final revision | 无初始行 / 48 |
| public limit | 并发调用40，最后复用baseline静止重放100；每次实际总返回量不超该次limit |
| normal JVM restart | 退出前后DB cursor完全相同，新JVM一次真实预留revision+1 |
| continuous progress / resource samples | 136 / 281 |
| Hikari，每JVM | max10、active peak1、waiting peak0；最终active0、waiting0、idle10 |
| threads | 初始15、峰值20、最终18或19，随后进程全部退出 |
| driver queue | capacity1，peak1、final0 |

cursor观察器位于已有仓储事务advice内，只在真实reserve返回后读取本次cursor revision并记录真实候选；不替换返回、不补写业务、不新增锁或业务屏障。既有cursor事务行锁会覆盖这次额外只读查询，因此可能增加少量观测开销；结果不是性能benchmark。按revision重建循环顺序、回绕及全体identity可达，正常重启证明共享数据库进度而非JVM记忆。

额外Venue executor沿用4 workers/queue16，完整队列、拒绝与连续采样留raw。清理均完成：owned PG=0、NQ=0、Venue=0、PG/Venue端口释放。Venue仅在业务结束后的正常fixture teardown关闭；未将关闭作为故障注入。原始证据按要求留target不算运行资源残留。

C2=NOT_RUN，C3=NOT_RUN；4策略×3窗口、requested/effective mixed execution及4路cursor fairness没有本批运行结论。代码准备不等于验证。

## 失败历史与测量修正

所有日志保留在`backend/nq-app/target`，没有覆盖：

- `l5c-compile-attempt01.log`：目标编译/driver tests通过。
- `l5c-c1-attempt01.log`：首轮余额oracle拒绝。定位到L5 Venue受控成交沿用下单时间，无法忠实表示“旧订单稍后才成交”；此轮作为fixture前提失败保留，不据其单独声明生产P1。只修L5真实成交转换的uTime，并禁止后续FILL改写已成交事实，余额断言未放宽。
- `l5c-c1-attempt02.log`：修正时间后C1通过；它尚未采用最终每JVM一个业务slot的容量限制，作为被替代候选保留，不能替代最终候选验收。
- `l5c-c1-attempt03.log`：最终受限driver真实运行完成，但业务oracle发现Position丢失，目标Maven BUILD FAILURE，3 tests / 1 error / 0 skipped（driver2 PASS、C1 correctness export ERROR）。随后停止，未运行C2/C3。

cursor导出最初把最后baseline重放的100预算误当成并发40预算；已按每次真实公开limit校验，保留总体预算不变。失败摘要首次读取不存在的Trade.side字段失败，随后改为通过Order关系读取真实side；未改原始事实或oracle。以上是测试/导出问题，不能用它们忽略最终真实持仓差额。

同类测量错误按根因顺序检查Venue事实时间→真实表字段→公开调用预算，未加allowlist或按层级放宽correctness。持仓丢失属于不同生产机制，按任务STOP边界保持未修复；不宣称系统性根因已关闭。

## 证据、验证与后续

[汇总](summary.json)的boundedBatch原样保留上一批summary内容；concurrentBatch记录当前失败、三次attempt、源码/候选及raw/log SHA-256。[C1失败摘要](runs/C1-failure.json)明确标FAIL，含canonical Trade关系、cursor观察、资源及差额。raw/log中L5_ROOT定位原文件；全量raw保持在target，没有用摘要替换原始输入。

最终Java驱动自attempt03未修改；之后仅完成失败导出、离线拒绝测试和证据。规划与生产源码始终不变。8个本批test/tooling文件的最终清单见summary.concurrentBatch.candidate；新增本报告及C1失败摘要，更新summary；上一批报告及S1/S2/S3 run文件未改。

失败复现命令（后续修复任务使用；本批发现P1后不再执行）：

```powershell
$env:MAVEN_OPTS='-Xmx512m'
mvn -o -f backend/pom.xml -pl nq-app -am test '-Dtest=L5BoundedWorkloadTest,L5DriverContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xmx1024m' '-Dnq.l5=true' '-Dnq.l5.level=C1'
```

离线失败核验：`python backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/test_l5_concurrent_oracle.py <attempt03 raw-proof.json>`，4 tests PASS：保留position错误拒绝、8类cursor/concurrency mutation拒绝、旧fill timestamp拒绝、失败导出身份/原始文件不变及secret拒绝。这些PASS表示能识别失败，不表示C1通过。

正常l5_measurement.py导出仍拒绝该raw。显式`--failure <raw-proof.json>`才生成FAIL取证摘要，没有成功资格文件。额外PG cursor suite及Full Maven均NOT_RUN：已用真实PG完成C1，发现生产P1后按合同停止动态资格。

stage-assets、Gitleaks、secret negatives、文档链接及diff最终结果记录于summary.concurrentBatch.validation。完成这些卫生检查不解除业务失败。


## 本轮正式复跑与失败处置

任务：`NQ-GATEAUDIT-PHASE6-L5-CONCURRENT-WORKLOAD-AND-BACKLOG-RESUME`。本节为 2026-09-11 新执行记录；上文原 C1 失败及其原始摘要保留，先前 projection remediation 与独立审查接受事实不撤销。

结论：**FAIL / PHASE6_L5_CONCURRENT_WORKLOAD_AND_BACKLOG_NOT_ACCEPTED / C3_QUALIFICATION_INCOMPLETE / PRODUCTION_REMEDIATION_REQUIRED**。C1、C2 正式通过；C3 发生合法同 fill 并发插入的错误响应并中断，不能接受本批或进入 repeated-fault。L5 仍 NOT_ACCEPTED。

### 候选、环境及实际规模

起止 HEAD=`991187fe772ad8b03746a4a9ddfc3ea9010e5896`，branch=`audit/post-gatey-agent-baseline`。入口完整工作区指纹=`3d6c5a9e69daf92514ba3f042a650e2970dc25d67665fdef31c9ed66a7245d4f`，与独立审查结论一致。1,828 个技术输入继续绑定 [projection manifest](projection-remediation/candidate-manifest.json)，SHA-256=`8ac8b3557acf269f04be34106a3e7bf20b148b4a3ca53968e0fdb1a802e52279`；本轮只更新证据，production/migration/harness/plan delta=0。规划起止 SHA-256 仍为 `80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`。

Docker Desktop 29.7.2 的 info、一次无网络 disposable container create/inspect/remove 均成功。三个正式 run 分别使用新建隔离 PostgreSQL 16.15/V51、独立 Synthetic Venue 和真实 Spring/RiskGate/Order/V49/adapter/reconciliation；不访问真实 provider 或生产数据。沿用正式 `L5BoundedWorkloadTest`，依次仅设置 `nq.l5.level=C1/C2/C3`，没有用 remediation driver 替代，也没有重新构建框架。

| 实测 | C1 | C2 | C3 失败截面 |
| --- | --- | --- | --- |
| 结果 | PASS | PASS | FAIL / INCOMPLETE |
| 同时 NQ JVM / reconciliation actors | 2 / 2 | 4 / 4 | 4 / 4 启动，一条 actor 异常终止 |
| 计划候选 | 120 | 240 | 228 ordinary + 12 strategy = 240 |
| 实际 Order / V49 / PLACE | 120 / 120 / 120 | 240 / 240 / 240 | 91 / 91 / 91 |
| Trade / TradeExecuted / Ledger | 120 / 120 / 480 | 240 / 240 / 960 | 28 / 28 / 112 |
| source qty / Position / latest BTC Snapshot | 12.0 / 12.0 / 12.0 | 24.0 / 24.0 / 24.0 | 2.8 / 2.8 / 2.8，仅已成交部分 |
| 初始 / 新增 / 峰值 / 最终观察 actionable | 60 / 60 / 66 / 0 | 60 / 180 / 70 / 0 | 60 / 31 / 66 / 63 |
| cursor reservations / wraps / observed candidates | 46 / 21 / 120 | 135 / 41 / 240 | 246 / 110 / 91，未完成完整契约 |
| 旧 60 候选未成交时新订单已完成 | 58 | 170 | 已观察 28；不冒充完整公平性验收 |
| strategy windows / requested-effective | 不适用 | 不适用 | NOT_REACHED；不能借用前批 proof |
| 收敛后 replay | 原持久事实不变 | 原持久事实不变 | NOT_REACHED |

紧凑结果：[C1](runs/C1-resume.json)、[C2](runs/C2-resume.json)、[C3 failure](runs/C3-resume-failure.json)。各文件绑定新的 raw/log SHA-256、技术 manifest、按账户资产重建的 oracle 和测量；完整随机运行身份、连续采样与调用交错留在其 target locator。C1/C2 的 17 类业务反例、8 类 cursor/concurrency 反例均实际拒绝。新增三个离线 exporter 测试通过；失败 C3 仍被成功导出 oracle 拒绝，没有将失败数据转成 PASS。

数量 expected 独立从 Trade.qty、Order.side、base fee 重建，逐 account/symbol 与 Position、逐 account/asset 与 latest Snapshot 比较；本组实际只有 account=1、BTC base 及 USDT quote。USDT 沿用既有 Ledger SUM(delta)=0 口径，不将 Ledger 成对金额净额冒充 BTC 数量。C1/C2 duplicate PLACE/Trade/TradeExecuted/Ledger、lost work、durable orphan 均为0。C3 已有事实也未发现这些错误，但149个计划候选尚未生成，不能对完整 C3 声明全部为0。

### 新发现、首因与等级边界

新生产问题：`P2 / CONCURRENT_SAME_FILL_INSERT_FALSE_OVERFILL / OPEN`，本次 qualification 被阻断。与既有普通 Order concurrent INSERT loser P2 是不同位置，后者及 wildcard-import P3 继续 OPEN / NON_BLOCKING。

[Trade writer](../../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/scheduler/infra/jdbc/JdbcTradeRepository.java) 的 insert 在锁父 Order 后先求已提交成交总量，再执行 `executed + candidate.qty > original` 检查；该检查前未在同一锁边界识别已存在的相同 fill。[OKX reconcile](../../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java) 在锁外查不存在后调用 insertWithRequiredEvent，异常直接退出本轮 reconcile。

本次受影响 canonical Order 为 C3 failure 摘要中的 `SYNTH-L4:B5-L5C:R03:ORDER:001`：Order.qty=0.1、Venue accFillSz=0.1、唯一 durable Trade.qty=0.1、唯一同 fill。输家继续插入时把 winner 已提交的相同 effect 再计入数量检查，抛出 `RECONCILIATION_OVERFILL_OR_INVALID_QUANTITY`。没有真实 overfill，数据库唯一性未失守，也没有 Position lost update。

异常首先使一个测试 actor 的 Future 失败；已有 controller 将其传播到命令循环，随后出现 broken-pipe IOException。其余三条 actor 后续达到原80轮预算，这不是首次根因。已完成的28 Trade全部有4条Ledger及1个TradeExecuted，Position/Snapshot均为2.8。剩余63个Order在Venue均为live，其中60个是尚未释放的刻意旧候选，3个新候选亦尚未由后续FILL释放；不能记为63个永久stuck，不能把尚未投喂的工作称为丢单。

P0=0、已证实P1=0。证据证明 production 单轮错误响应及测试 actor 退出，未证明 production 定时调度器永久停机、永久starvation或会计损失；不为得到P1标签夸大结论。尽管如此，正式C3及effective/strategy/replay要求未完成，本批不能接受。未修production、未放宽actor预算、未吞异常后重跑，也未注入下一批故障。

### 资源、清理与交接

C1/C2 连续业务采样分别138/149个、JVM采样285/694个。所有已观察JVM pool max=10、active peak=1、pending peak=0、threads peak=20；command slot/queue沿用capacity=1，Venue executor沿用4 workers/queue16，未观察rejection。订单、Trade、Ledger在producer期间增长，actionable最终下降到0。不是latency SLA或完整性能benchmark。

C3 原失败teardown还有工具缺口：向已关闭stdin发送STOP时抛异常，跳过一个仍有sampler线程的child关闭。已在run结束后核实PID、java executable及本轮argfile身份，单独清理该owned测试进程。该清理不是负载中的故障注入，也不是恢复证明。最终3个qualification容器、15个子进程均无残留，三个DB/Venue端口全部释放；自动清理缺口没有被写成自动PASS。

Full Maven 本轮 NOT_RUN / NOT_REQUIRED：production与migration delta=0，复用已独立接受的最终1909 tests、0 failures/errors、130 conditional skips。正式Maven：C1=3 tests PASS、C2=1 PASS、C3=1 ERROR，原日志保留。历史错误投影继续 `HISTORICAL_PROJECTION_REPAIR_REQUIRED / NON_BLOCKING_FOR_CURRENT_QUALIFICATION`，没有修复或回填历史数据。

自查为 SELF_REVIEWED / NO_ACCEPTANCE，未追加Independent Review。最终stage-assets、Gitleaks、secret negatives、links、diff检查记录在 [summary](summary.json) 的 concurrentResumeBatch.validation；卫生检查不解除C3失败。stage=0、commit=NONE、push=NONE。下一动作：独立处置同fill并发插入幂等边界及失败teardown，再按新候选影响重新qualification；当前 `PRODUCTION_REMEDIATION_REQUIRED`，不进入 `NQ-GATEAUDIT-PHASE6-L5-REPEATED-FAULT-UNDER-LOAD`。

## 仅 C3 恢复资格接受

任务：`NQ-GATEAUDIT-PHASE6-L5-CONCURRENT-WORKLOAD-AND-BACKLOG-C3-RESUME`。结论：**PASS / PHASE6_L5_CONCURRENT_WORKLOAD_AND_BACKLOG_ACCEPTED / READY_FOR_L5_REPEATED_FAULT**。C1=`REUSED_ACCEPTED_PROOF / PASS`、C2=`REUSED_ACCEPTED_PROOF / PASS`；C3=`NEW_QUALIFICATION_RUN / PASS`。本轮没有重新运行C1/C2，没有故障注入；L5仍`NOT_ACCEPTED`。

### 候选与边界

HEAD=`991187fe772ad8b03746a4a9ddfc3ea9010e5896`，branch=`audit/post-gatey-agent-baseline`。入口完整工作区fingerprint=`f1b807ed3acbb69f208baae284249d5b9c3887f1961443a705413efadf3c9354`，与上一独立审查接受身份逐字节一致。[最终技术manifest](fill-idempotency-remediation/candidate-manifest.json)1832项匹配，SHA-256=`fd1091e5497fe8b80583c99efa19003e5729e4059c92bff705c03f18ebb7f9e1`；projection、fill整改及V49/V50/V51已审生产候选不变。规划SHA-256=`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`。本轮仅更新本报告、summary新批次及新C3摘要；完整工作区因获准证据更新改变，不误报为production drift。

上一独立审查37 tests / 0 failures / 0 errors / 0 skipped，已接受同fill分类、真overfill拒绝、账务/重放投影和cleanup，并明确C1/C2复用。本节记录已接受交接，不冒充第二次Independent Review。

环境仅一次Docker info与无网络disposable container create/inspect/remove，均PASS；Docker29.7.2、隔离PG16.15/V51、canonical镜像digest。运行前可用内存25504MiB、磁盘216.16GiB。实际同时最多4个NQ、4个actor，另1个Venue与1个PG；收敛后正常停止/重启产生第5个NQ PID，但同时不超过4个。无运行中kill、断连、响应丢失、commit ambiguity或Kill transition。

### 正式 C3 实测

[新C3摘要](runs/C3-resume-accepted.json)绑定新raw/log SHA-256；[summary](summary.json)的`concurrentC3ResumeBatch`记录C1/C2复用来源。历史`boundedBatch`、`concurrentBatch`、`concurrentResumeBatch`原对象保持不变，旧C1/C2与C3失败摘要没有覆盖。

| 实测 | C3 |
| --- | --- |
| 入口 | 原L5BoundedWorkloadTest，仅nq.l5.level=C3 |
| 同时NQ / actor / candidates | 4 / 4 / 240 |
| 普通订单 / 策略订单 | 228 / 12 |
| StrategyRun / windows | 12 SUCCEEDED；4策略×2024/2025/2026三个真实dueAt |
| Order / V49 / PLACE / CANCEL | 240 / 240 / 240 / 0 |
| Trade / TradeExecuted / Ledger / Ledger event | 240 / 240 / 960 / 960 |
| initial / new / peak / processed / final actionable | 60 / 180 / 73 / 240 / 0 |
| correctness-required unresolved / stuck | 0 / 0；无剩余待分类候选 |
| cursor reservations / wraps / coverage | 153 / 46 / 240 |
| persistent旧60未成交时已完成的新候选 | 176 |
| duplicate candidate observations | 5940；不是重复副作用 |
| cursor final | revision=153；末尾identity见规范化摘要 |
| producer / drain / total workload | 25.562s / 3.5696843s / 64.2719551s |
| progress / resource samples / transaction delta | 163 / 748 / 37474 |

producer期间实际4路reconcile调用重叠。按actor预留/调用区间、Venue不可变FILL与后续QUERY_FILLS交叉核对，33个已成交Order出现在不同actor的重叠调用中、98对重叠区间；240个fill均多次查询，已成交fill查询总数3425。reconciliation error=0、false overfill=0；unique fills=240、累计qty=24.0。该证据不冒充整改期确定性PG锁等待屏障，本轮是原始clean workload。

真实overfill guard=`PRESERVED / REUSED_ACCEPTED_REAL_POSTGRES_NEGATIVE_PROOF`：复用刚接受审查中0.8+0.3>1.0的拒绝及无副作用证明，相关production bytes一致。本C3不新增非法fill、不重跑37项review matrix，也不把拒绝负例冒充本C3新注入场景。

### 策略与投影

逐strategy/account/schedule/dueAt核对同window一个StrategyRun，每run仅一条Order、V49与work lineage；12个run全部SUCCEEDED，未来window继续推进。12条work均requested=0.1005、effective=0.1；Order.qty、wire sz、Venue accFillSz与Trade.qty均=0.1，phantom residual=0。这些事实来自本次正式C3，不借remediation小场景替代。

独立oracle从Trade.qty、Order.side和base fee重建逐account/symbol及account/base资产：account=1、BTC expected=24.0、Position.qty/available_qty=24.0、latest BTC Snapshot=24.0。USDT按当前canonical Ledger SUM(delta)=0.00，与latest Snapshot=0一致，不把成对Ledger净额冒充BTC数量。480条Snapshot恰为每唯一Trade两币种publication。停止producer、backlog归零后正常reconciliation/project replay，全量Trade/Event/Ledger/Position/Snapshot持久快照不变。

duplicate external mutation、Trade、TradeExecuted、Ledger、projection application均=0；lost work=0、durable orphan=0。19类业务反例和9类concurrency/cursor/effective反例均REJECT；另4个既有离线exporter测试PASS，C1/C2仅作为只读历史输入，未重跑其工作负载。

### 资源、验证与下一动作

每JVM Hikari max=10、active peak=1、pending peak=0，最终active/pending=0、idle=10；threads初始15、峰值20、最终18或19。driver queue capacity=1、peak=1、final=0；Venue queue capacity=16、peak=0、rejection=0。业务与账务持续增长，backlog下降归零；无永久资源耗尽、无界队列或停滞。本结果是有界负载观测，不是长期soak或latency SLA。

正式C3 Maven=`1 test / 0 failures / 0 errors / 0 skipped / exit=0`；日志`backend/nq-app/target/l5-c3-resume-mqdn2uhm/C3-qualification.log`。Full Maven=`NOT_RUN / FINAL_CANDIDATE_RUN_REUSED`，复用1919 / 0 / 0 / 140 conditional skips。production delta=0、migration delta=0、harness delta=0。

登记的5个NQ PID、1个Venue PID和1个PG容器全部退出/删除，PG/Venue端口释放；没有人工补救或全局终止。卫生检查完成：stage-assets=1897 scanned / 173 exceptions / 0 errors；固定 Gitleaks 8.18.4 对本轮3个证据文件 findings=0；6类secret negative fixtures全部REJECT，敏感字段未经exporter隐藏；links=17 checked / 0 warnings / 0 errors；git diff --check PASS。原始结果与输入哈希留本轮target，最终扫描另作只读复核。

P0=0、P1=0、blocking P2=0；既有ordinary concurrent INSERT loser P2、wildcard-import P3与历史projection repair obligation保持原非阻断处置。stage=0、commit=NONE、push=NONE；current authority未修改。下一动作`NQ-GATEAUDIT-PHASE6-L5-REPEATED-FAULT-UNDER-LOAD`，L5整体仍`NOT_ACCEPTED`。
