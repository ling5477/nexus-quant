# Phase6 L4 B2 撤单终态成交收敛整改

日期：2026-09-09。任务：`NQ-GATEAUDIT-PHASE6-L4-B2-CANCEL-FILL-TERMINAL-CONVERGENCE-REMEDIATION`。

**IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW / B2_TERMINAL_CONVERGENCE_REMEDIATED / P0_0 / LOCAL_P1_0**

**B2 = NOT_ACCEPTED**。上述 P0/local P1 仅指本轮实现及已运行覆盖，不是独立审查或发布结论。未 commit/push、未触发 CI、未修改 current authority。Full Maven 运行过一次且失败；其四个失败项已在最终定向回归全部关闭，但没有把该全量失败改写为全量绿色。

## 基线、历史与候选身份

- Branch=`audit/post-gatey-agent-baseline`，起始 HEAD 与 origin tracking ref 均为 `d1cedb6debfaa3dbeacb0e95ef8599c69e5f9da3`。开始执行了 status/branch/HEAD/origin/diff --check。
- 初始工作区已有上轮 B0NqProcessMain 修改、B2RealProcessProofTest、B2SyntheticVenueMain 和失败 evidence；本轮承接这些明确已知的未提交文件，不将其误称 clean。
- [原失败 evidence](GATEAUDIT_PHASE6_L4_B2_CANCEL_FILL_RACE_AND_PER_FILL_ACCOUNTING.md) 原样保留，SHA-256=`e6f103d4ef28b8dd2c769fe1dd6634e9ed343c25a608116b7d26448300e508e8`。原 0.1/0.04/0.06 的真实失败、P1 finding 与停止规则不被整改结果覆盖。
- 最终 15 个 Java 源码（7 production、8 tests）的 Git canonical blob 与 9 份最终真实进程 proof 见 [最终 manifest](l4-b2-terminal-remediation-attempt02/manifest.json)。源码 fingerprint=`b61b546b509ed71a22e9ccb33a3945361498aa8cdba5753824ae2484eeac8ba5`。
- Fingerprint 算法：按 manifest 的 sources 顺序，将每行 `path + 空格 + gitBlob` 用 LF 连接（末尾无 LF），计算 UTF-8 SHA-256。它绑定未提交候选，不冒充 HEAD/CI acceptance。
- [早期定向运行 manifest](l4-b2-terminal-remediation-attempt01/manifest.json) 保留 Full Maven 前的候选/运行身份；其源码已被后续修复替代，不是最终候选证据。

## 根因与最小修复

原链路把 `sCode=0` 撤单受理 ACK 持久化成 CANCELLED，随后 CANCELLED 分支仅回补 Trade/Ledger，最后一笔成交虽已补齐却永远保留 CANCELLED。本轮按用户给定语义，以更强的 durable 成交事实纠正该终态；不重写普通状态机、不新增第二 reconciliation、不修改 ACK 的 C1 OCC 实现。

新的 [OrderLifecycleService](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderLifecycleService.java) `reconcileCancelledExecution(orderId, traceId)` 是专用对账入口；参数没有“已完全成交”布尔值、外部累计数量或任意目标状态。它经既有 OrderCommandService 委托到 Spring 事务代理的 [OrderCommandWriteService](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java)。普通 markFilled/applyExternalStatus 仍经过原状态机并拒绝 CANCELLED → FILLED。

[JdbcOrderRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepository.java) 对该订单完整 durable Trades 集合验证 account、symbol、venue、trade_env、external identity、非空唯一 venue fill identity 和正数 qty，再精确累计 BigDecimal / PostgreSQL NUMERIC：

| durable executed 与 original 的关系 | 行为 |
| --- | --- |
| executed < original | 保留 CANCELLED，remaining=original-executed，不变更 version |
| executed = original | 仅专用纠正入口允许 CANCELLED → FILLED，remaining=0 |
| executed > original 或证明损坏 | fail-closed，抛出 reconciliation inconsistency，不 clamp、不推进终态 |

累计不是 `SUM(DISTINCT qty)`：两个不同 fill 即使 qty 相同也必须分别计入。本轮 4+3+3=10 的回归直接覆盖这一点。SQL 完整聚合没有采用当前 venue response 的局部条数或截断后的前缀充当证明。

更新以 expected status=CANCELLED 与 expectedVersion 为前提。仓储先取得订单行锁，再用新 SQL 语句重新验证完整 durable 数量/身份和 version，更新成功只 `version+1`。同一 Spring 事务中写 `ORDER_TERMINAL_EXECUTION_CORRECTED` audit 与现有 `OrderStatusChangedPayload` event；event 失败时状态、version、audit 一并回滚。

CAS 冲突后最多重新读取三次当前 Order 与完整 durable proof：已经 FILLED 则 no-op；仍 CANCELLED 必须重新判断最新数量；其他状态返回当前 canonical 事实。不会给旧 ACK 换一个新 version 重试。重复 reconciliation 不持续增加版本或重复发布纠正事件。

为了关闭“两个响应预检都通过后并发写入超量”的窗口，[JdbcTradeRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/scheduler/infra/jdbc/JdbcTradeRepository.java) 的 canonical insert 使用短 Spring 事务，取得同一订单行锁后重新验证 `durable sum + incoming qty <= original`，否则拒绝。锁内没有 venue I/O；沿用已有 Trade 唯一约束及 Ledger 幂等路径。未增加表、列、migration、游标或预算算法。

[OkxRestReconcileService](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java) 在写入前验证 durable 集合与本轮 reports 的按身份去重并集；相同报告只处理一次，同身份却有数量/价格/fee/fee asset/timestamp 冲突则拒绝。已有 durable Trade 与新报告的 fee magnitude/currency 也必须一致。查询路径本来已有 order snapshot 时保留其状态对齐；原 CANCELLED 路径没有 order query，本轮不增加第二 truth source 或额外 venue 查询。

## C1/C2 与既有恢复契约

- C1 的 stale PLACE ACK、stale CANCEL ACK、ABA、pre-cancel race 继续通过真实 Spring/PostgreSQL 回归；FILLED 提交后的旧 ACK 不能回退状态、version 或 identity。
- V48、reconciliation_scan_cursors、共享总 limit、venue predicate before LIMIT、候选公平性算法均未修改。C2 missing identity 不查询 venue fill、CANCELLED missing-fill discovery、Trade dedupe 与 Ledger replay 均继续验证。
- C2 中“状态与版本不变”的测试明确使用 original=0.2、既有 synthetic fill=0.1 的部分成交夹具；没有删掉状态/版本/扫描公平性断言。原 full-size 夹具在 B2 新语义下应该纠正，故不再拿它证明部分撤单不变。足量终态纠正由 B2 单独验证。
- Full Maven 发现本轮最初将 active-order 状态推进挪到 Ledger 之后，破坏 F004/F002 的“账本失败时 Order 已根据 venue fact 为 FILLED”恢复契约。最终实现改为：数量/身份预检 → 原有 venue 状态对齐 → Trade/Ledger；撤单终态的强成交纠正仍在 durable facts 可用后执行。F004/F002 两项原失败已在最终候选上通过。

## 最终真实进程证明

沿用 B0：真实 Spring JVM、RiskGate、OrderCommandService、AdapterBackedTradingVenueGateway、OkxExchangeAdapter、HTTP/JSON、JDBC；独立 Synthetic Venue JVM；PostgreSQL 16.15 / V48。Controller 使用 reader role 与 REPEATABLE_READ 快照，不写 orders/trades/ledger/receipts；bootstrap 只在 disposable DB 启动前初始化。

最终 controller PID=30464；PG container=`SYNTH-L4:B2-EXPORT:R01:CONTAINER:002`，ID=`SYNTH-L4:B2-EXPORT:R01:DOCKERID:001`，loopback port=39337。镜像为 canonical `postgres:16@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`，`--pull=never`。每行各有新 DB、新 venue，数据库 identity、fills、Order/Trade/Ledger 和 audit/event 全快照均在对应 JSON。

| 场景 / repeat | NQ PID | 恢复新 PID | Venue PID | 最终状态/version | executed/remaining | Trade/Ledger |
| --- | --- | --- | --- | --- | --- | --- |
| partial / 1 | 54700 | — | 24764 | CANCELLED/6 | 4/6 | 1/2 |
| partial / 2 | 71396 | — | 73452 | CANCELLED/6 | 4/6 | 1/2 |
| partial / 3 | 37256 | — | 23396 | CANCELLED/6 | 4/6 | 1/2 |
| final fill race / 1 | 42840 | — | 13980 | FILLED/7 | 10/0 | 3/10 |
| final fill race / 2 | 5632 | — | 50892 | FILLED/7 | 10/0 | 3/10 |
| final fill race / 3 | 52052 | — | 44820 | FILLED/7 | 10/0 | 3/10 |
| restart race / 1 | 49840 | 3976 | 41376 | FILLED/7 | 10/0 | 3/10 |
| restart race / 2 | 40884 | 27776 | 62272 | FILLED/7 | 10/0 | 3/10 |
| restart race / 3 | 37188 | 14844 | 22492 | FILLED/7 | 10/0 | 3/10 |

三个 full race 与三个 restart race 都是：original=10 → fill1=4/fee0 durable → CANCEL ACK、本地 CANCELLED/6 → fill2=3/fee0.01 + fill3=3/fee0.02 → cancel effect 发现已完全成交、venue 保留 filled → ordinary RECOVER → FILLED/7。Restart 行在 CANCELLED+fill1 durable 后强杀旧 NQ，确认死亡后 venue 才产生 fill2/fill3；新 PID 使用同一 DB 普通恢复。

Venue 故意在每次 fills response 重复返回全部 fills，完整成交时 6 个 report 对应 3 个 unique fill。每个场景只有 PLACE=1、CANCEL=1；新进程不发第二次 PLACE。再次 RECOVER 返回 0，Order/Trade/Ledger/ledger events/position/account snapshots 与纠正 audit/event 的完整快照均不变。所有 full/restart 行纠正 audit=1、event=1；部分撤单为0。

逐笔 canonical accounting 断言来自实际 fill facts：BUY 100×4=400、100×3=300、100×3=300；fee magnitude 分别 0、0.01、0.02 USDT。按已有 TradeLedgerPostingService 的本金成对分录与非零 fee 成对分录构造期望 key/delta/currency/direction，再逐条匹配实际账本；不固定“每个 Trade 四条分录”。Ledger entries 与 ledger events 数量一致，BTC position 与 executed 一致。上述 2/10 是观察值；既有成对记账及账户投影模型未改变，也不将 quote 分录求和为零解释成真实钱包结算证明。

## PostgreSQL 对抗与永久回归

[B2 PostgreSQL tests](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B2TerminalCorrectionPostgresIntegrationTest.java) 共9次：

- 部分成交不纠正，三笔足量才纠正；普通生命周期不能把 CANCELLED 改为 FILLED。
- 历史/旁路 fixture overfill=10.00000001：服务拒绝，原子 SQL guard 更新0行，不改状态/version。
- 错误 external fill identity 不能形成有效证明。
- Java 已读取满量后，独立连接提交额外 fill：SQL 重新证明拒绝，随后读取识别 overfill，状态/version 不变。
- 两个并发纠正线程在 CAS 前确定性 barrier 相遇，独立重复3次：仅一个更新与一个 audit/event，另一方读取 FILLED 后收敛；version恰好+1。
- 事件存储注入失败：真实 Spring 事务回滚状态、version 与已写审计。
- 两个不同 fill 同时插入会超量时，共用行锁使一方拒绝；durable executed=8，不是12。

另有 [强证明/OCC unit tests](../../../backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/trading/application/CancelledExecutionCorrectionTest.java) 8项及 scheduler 21项，覆盖 CAS 失败后重新取 proof/版本、already FILLED no-op、精确 decimal、同身份冲突回报在写入前拒绝、当前响应隐藏旧 durable fills 时仍正确识别累计 overfill。

## 验证记录与失败保留

| 验证 | 真实结果 |
| --- | --- |
| 早期 unit-01 | 测试放在无 Mockito 的 nq-core 测试模块导致编译失败；移到已有 Mockito 的 nq-scheduler 测试模块，未增加依赖 |
| unit-02 | 旧 duplicate-report 断言尚未同步为相同内容幂等；修正测试并保留冲突内容拒绝负例 |
| targeted-01 | 实际 full correction 成功，但新 event checker 用错 envelope JSON 路径；修正为 payload.reason |
| pg-01 | C1/C2 28项通过；B2 中5项 fixture record equals 受 NUMERIC scale 影响；改为插入后读取 canonical Order 作为比较基线 |
| targeted-02 | 94 tests、0 failures/errors/skips；真实进程9次通过，但随后 Full Maven 发现 active-order 恢复顺序回归，此候选已替代 |
| Full Maven（仅一次） | 1831 tests、2 failures、2 errors、79 conditional skips；exit=1，总时间1:28，失败事实永久保留 |
| 最终定向回归 | **155 tests、0 failures、0 errors、0 skips，exit=0，总时间2:22**；包含新候选9次真实进程、B2 PG9、C1/C2 PG28、F004/F002恢复和全部原失败类 |

[Full Maven failure summary](l4-b2-terminal-remediation-attempt02/full-maven-failure-summary.txt) 保留四项失败：

1. TradingChainPostgresIntegrationTest：本轮状态推进晚于账本失败；已修生产顺序，最终该类8/8。
2. TradingRestartRecoveryPostgresIntegrationTest：同一根因，R1 A 要求 FILLED；最终跨 JVM 2/2。
3. ProductionConfigurationApplicationContextInitializerTest：本次父进程导出的空 SPRING_DATASOURCE_PASSWORD 遮蔽测试正例的 prod 属性；改用 disposable trust DB 的非空公开测试值，不改 production validator；最终该类50/50。
4. ResearchBacktestHappyPathLocalTest：fresh full-run DB 没有该既有测试要求的 legacy account；最终定向运行使用本轮已有账户夹具的专用数据库，1/1。没有改测试或研究代码规避断言。

没有第二次 Full Maven。因此正确表述是“全量一次失败、原因已修复/前置条件已纠正、最终受影响集合通过”，不是“最终候选 Full Maven green”。79次条件跳过不计通过；B2、C1/C2、restart 的专用最终运行均实际启用，零 skips。不重跑 B1 整个 fault matrix。

最终命令（repo root，使用仅本轮拥有的 PG16/V48 测试库）：

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am '-Dnq.b2=true' '-Dnq.b2.pg=true' '-Dnq.l4.blockers.enabled=true' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:40095/nq_l4_blocker' '-Dspring.datasource.username=postgres' '-Dspring.datasource.password=b2-fixture-not-a-secret' '-Dtest=CancelledExecutionCorrectionTest,OrderCommandServiceTest,InMemoryOrderStateMachineTest,OkxRestReconcileServiceTest,JdbcOrderRepositoryTest,JdbcTradeRepositoryTest,B2RealProcessProofTest,B0FixtureSafetyTest,B2TerminalCorrectionPostgresIntegrationTest,L4PlanBlockerPostgresIntegrationTest,ReconciliationCursorPostgresIntegrationTest,TradingChainPostgresIntegrationTest,TradingRestartRecoveryPostgresIntegrationTest,ProductionConfigurationApplicationContextInitializerTest,ResearchBacktestHappyPathLocalTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

父进程 SPRING_DATASOURCE_URL/USERNAME/PASSWORD 绑定同一 disposable endpoint，仅用于 forked restart tests；上面的公开字符串不是凭证，trust fixture 没有真实密码。重现时必须重新创建并核验本轮独占容器/端口，不能盲用历史端口。B0 子进程仍通过 cleanEnvironment 只传 OS 必需字段和受限 NQ_B0_* 配置。

全量命令确实为 `mvn -f backend/pom.xml test`，在同一拥有容器内另建 `nq_b2_full` fresh DB，通过进程局部 SPRING_DATASOURCE_* 指向该库。[最终 observations](l4-b2-terminal-remediation-attempt02/observations.txt) 保留进程命令、PID、容器、清理和测试摘要；本地详细日志为 `artifacts/b2-remediation-*.log`，未改写失败日志。

## 范围、安全与回滚

Production 修改仅7个 Java 文件；V48/Flyway、cursor SQL、public limit、.github、AGENTS、Skills、STATUS/ROADMAP/WORKLOG 未修改。LIVE=0、真实 provider=0、真实交易调用=0；没有交易凭证获取或真实 PLACE/CANCEL/transfer/withdraw。本轮可运行能力只在 disposable SIM fixtures，测试中的 kill 初始化不授权生产解除 kill。

本轮 integration/full-run PG 容器=`nq-b2-remediation-20260909`，ID=`c8d7f8b18d37fe3279d93f98180f85b53b32f0b8e33fe888d72a6c1e63fbc04c`，loopback port=40095。清理前核对 label/port 所有权及 schema=48，再删除该唯一拥有容器。B0 自有容器由 harness 清理。最终两个专用 label 查询均无容器残留；原真实进程均被 wait/close 确认退出。未停用共享 Docker、未删除缓存镜像或上轮 socket 备份。

[Production rollback patch](l4-b2-terminal-remediation-attempt02/production-remediation.patch) 只包含7个 production 文件；已执行 `git apply --reverse --check` 通过，未执行回滚。若未来确需撤销本地实现，先重新核对候选 fingerprint/已有改动，再反向应用该补丁；不丢弃用户测试、历史 evidence 或其他改动。回滚会恢复本轮原 P1，不能同时宣称 B2 已关闭。没有 schema 回滚；禁止为了代码回滚把已纠正的 durable FILLED/version 改回旧事实。生产从未部署本候选。

本轮只自查，不冒充独立 reviewer。下一动作：`NQ-GATEAUDIT-PHASE6-L4-B2-TERMINAL-CONVERGENCE-INDEPENDENT-CORRECTNESS-REVIEW`。`stage=0 / commit=NONE / push=NONE`，B2 继续 NOT_ACCEPTED。

## 最终交付读回

最终核验9份 proof 的 LF-normalized SHA-256、15个源码 Git canonical blob、候选 fingerprint 与 observations hash，全部匹配 manifest。原失败 evidence SHA-256 未变。补充文件采用同一 LF-normalized SHA-256 约定：production-remediation.patch=`8588c3f51de0441a3bee551c8664931c75e9ad419e8179ffa45b459c0cab0d88`；full-maven-failure-summary.txt=`099b162a0a393419298902779f340fdba81699a7f5aeafc1265c21cc7ee5a2ff`。

文档链接检查 `checked=13 / warnings=0 / errors=0`。完成 status、diff --check、diff --stat、diff --name-only、diff 及 untracked 文件清单读回；改动范围符合本任务，index 为空，HEAD 与 origin tracking ref 保持上述基线。反向补丁 check 再次通过，未执行回滚。两个专用 Docker label 无容器残留，上表21个 NQ/Venue PID 均已不存在。
