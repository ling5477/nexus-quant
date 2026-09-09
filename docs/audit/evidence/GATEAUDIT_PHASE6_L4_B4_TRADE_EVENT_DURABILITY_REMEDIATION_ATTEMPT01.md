# B4 TradeExecuted durability 与 recovery 整改 attempt01

日期：2026-09-09。任务：NQ-GATEAUDIT-PHASE6-L4-B4-TRADE-EVENT-DURABILITY-AND-RECOVERY-REMEDIATION。

NQ-only / HIGH_RISK / P1_CORRECTNESS_REMEDIATION / TRANSACTION_RECOVERY / RECURRING_ROOT_CAUSE。

本轮为实现与本地验证，不是独立审查、交付或 B4 qualification acceptance。B4 保持 **NOT_ACCEPTED**；stage=0、commit=NONE、push=NONE。

## 起始身份与原失败

branch=`audit/post-gatey-agent-baseline`；HEAD=`e9509e351e6fbc6179e5e081cb03e66c8cb6ad99`，等于本地 origin 引用。起始工作区包含上一轮 B4 test/harness/exporter/FAIL evidence 的未提交改动；本轮保留这些工作，没有 reset、stage 或改写原失败证据。

[原 B4 FAIL 及完整快照](GATEAUDIT_PHASE6_L4_B4_FAILURE_ATOMICITY_AND_PROCESS_DEATH_QUALIFICATION.md)保持原样。其真实故障为 Trade 提交→进程死亡→TradeExecuted 未写入；新 PID 恢复 Ledger 后事件仍为0。原始 production P1 不由前一轮 exporter/测试断言修正消除。

STATUS/ROADMAP 的 pre-B0 文档滞后沿用原记录；本轮按用户明确给定的整改范围工作，不修改 current authority，不取得 LIVE/真实 provider/生产数据库执行权限。

## 根因、事务与 source fan-out

| 阶段 | 原行为 | 本轮行为 |
|---|---|---|
| fill / source | Adapter fill经身份/累计数量验证，JdbcTradeRepository.insert以事务锁定Order并插入Trade | 保留验证和source事实；普通OKX改用insertWithRequiredEvent |
| 新Trade提交 | insert事务先提交，随后一次内存调用publishTradeEvent | Trade与必需TradeExecuted在同一个Spring事务、同一JdbcTemplate datasource/transaction manager内提交 |
| 事件写入 | EventPublisherPort→EventStoreAppender，随机event_id，每次append直接INSERT | RequiredTradeEventStore专门管理普通OKX必需事件，source行锁+旧事件验证+稳定event_id |
| Ledger | 单独TradeLedgerPostingService.postTrade事务，分录/ledger_events/投影/快照/相关event与audit一起提交 | 不合并、不重设计；保持原事务及幂等键 |
| 已有Trade恢复 | ensureLedgerConvergence从durable Trade重放，跳过首次publishTradeEvent | 验证durable身份后ensureRequiredEvent，再调用原Ledger恢复 |
| venue不再返回某fill | 遍历durableTrades，重放Ledger | 同样从durable Trade恢复必需事件及Ledger，不需要重新构造venue报告 |

此前 F004（`18efc06c`）修复了 Trade→Ledger 的持久源重放，而 TradeExecuted 仍只存在于 new-Trade 分支。只要程序在两个提交之间退出，已有Trade去重分支的continue就永久跳过事件。共同根因是只修复 fan-out 的一条分支，将post-commit内存调用当成可靠持久化传播。

Canonical rule：每个 REQUIRED durable derived fact 要么与source原子提交，要么可从source在restart/replay中幂等重建。当前普通OKX新Trade采用A；历史缺口保留B。Ledger继续独立事务。没有扩展到全仓所有事件，也没有修改Paper/Binance producer的业务合同或宣称其资格已证明。

## Event identity、并发与历史兼容

V48 `event_store.event_id VARCHAR(64) PRIMARY KEY` 已提供持久主键。新必需事件使用 `te-` 加 `UUID.nameUUIDFromBytes(UTF8("TradeExecuted:" + durableTradeId))`，每个Trade有独立事件身份，而不是按Order/client错误合并多个fill。

**唯一性不是只靠先SELECT再INSERT**：新写入和恢复均在真实事务中首先 `SELECT trade_id ... FOR UPDATE` 锁定同一source；持锁查询该Trade已有的TradeExecuted。无事件时插入稳定ID，已有一个语义一致事件则no-op，已有多个或语义冲突则拒绝。主键冲突/哈希碰撞不吞掉、不改写既有行，不视为成功。两个独立PG连接的实际锁等待与event=1见下文。

已有随机event_id事件保留原始ID/envelope/time/source，不再写第二个稳定ID事件。验证event payload（ts按同一timestamptz时刻比较）、trace/key/schema与durable facts；历史缺省trade_env只通过Trade/Order共同环境锚定，若有字段则必须匹配。该协议要求参与普通OKX写入的进程遵守同一source锁；本轮不授权旧新writer混跑或在线滚动部署。历史重复/冲突事件会fail closed，未对任何历史生产数据进行清洗或补写。

因此本候选不需要V49。migration=0，V48 unchanged。可靠性依赖明确的当前writer协议及数据库锁/PK，不宣称V48原schema单独能拒绝所有任意SQL写入。

## Event semantics

必需事件从已持久化Trade JOIN Order构建，不使用本次网络报告或Spring profile作为恢复source。验证Trade/Order的account、symbol、OKX venue、trace、external order identity及SIM/LIVE一致性。

payload保留TradeExecuted类型及trade/order/client/account、venue/exchange/fill、price/qty/fee/fee currency、fill timestamp；envelope trace/key锚定durable source，时间取source成交时间，使新写入与恢复一致。新事件增加可选`trade_env`，来自Trade与Order共同值。TradeExecuted旧构造器保持兼容，缺省环境字段不序列化；旧事件不回写、不改类型、不生成RecoveryTradeExecuted。

全部10行事件payload已独立对照完整durable snapshot逐字段验证，含decimal值、timestamp等价、trace/key和环境；不是只数event行数。没有修改Trade或历史venue truth。

## 真实进程最小矩阵

Java21.0.9 / Spring Boot3.5.10 / PostgreSQL16.15 / V48。每行新owned loopback DB、独立Synthetic Venue/NQ JVM；fixture为SIM/LIVE持久环境字段测试，real credential/provider/exchange=0、LIVE enable=0。Kill只允许真实canonical ENGAGE，DISENGAGED仍仅启动前封存TEST_PRECONDITION。

`B4LegacyClasspath`从固定失败HEAD的真实OkxRestReconcileService源码编译旧producer，前置classpath仅用于隔离旧NQ。其余依赖为当前候选，raw insert实现本轮未改；没有复制一套测试业务算法、SQL删除事件或Controller补写事实。旧producer在真实Spring/adapter/JDBC路径产生缺口；旧进程全部退出后，候选新PID才恢复同一DB/venue。历史source编译文件、classpath、日志留target。

| 场景 | 真实cut与恢复 | SIM | LIVE |
|---|---|---|---|
| NORMAL | 候选正常Trade=1/Event=1/Ledger=4，之后两次新PID重启 | PASS | PASS |
| LEGACY_TRADE_GAP | 旧producer Trade commit→事件前强杀，cut Trade=1/Event=0/Ledger=0→候选恢复 | PASS | PASS |
| LEGACY_LEDGER_GAP | 上述缺口→旧NQ新PID普通recovery补Ledger、Event仍0→旧NQ退出→候选再恢复事件 | PASS | PASS |
| ATOMIC_DEATH | 候选insertWithRequiredEvent已提交→Ledger调用前强杀，cut Trade=1/Event=1/Ledger=0 | PASS | PASS |
| LEGACY_COMPLETE | 旧producer完整生成随机ID事件及Ledger→候选重启验证旧事件不重复 | PASS | PASS |

10/10 PASS，1个JUnit承载，不冒充完整B4矩阵或每行3次qualification。每行候选recovery前canonical ENGAGE，Kill=ENGAGED时连续RECOVER4次；再换第二个新PID，RECOVER后完整business+TradeExecuted快照相等。故障前NQ由父进程destroyForcibly并确认退出，只有实际事务代理返回后才输出cut，reader独立查询验证committed facts。

每行PLACE requests=1、venue accepted=1、blind retry=0、CANCEL=0；Order最终FILLED/version4，Trade=1，qty10、price100、fee0.01；Ledger entries/events=4/4，逐笔本金/费用key、金额/方向、position10通过既有B2 oracle。成对USDT净额为0，不将该模型冒充真实钱包结算。所有DB、child、owned PG容器已清理。

完整PID、source/event/ledger快照与venue truth见[10行摘要及payload/identity验证](l4-b4-trade-event-remediation-attempt01/summary.json)，同目录每个SIM/LIVE场景JSON保留完整canonical快照。raw source、运行identity与日志仅在target，不提交raw identity或其编码/hash替身。

## PostgreSQL永久回归

`B4TradeEventPostgresTest`：4个JUnit全部PASS，其中并发测试承载SIM/LIVE×missing/legacy四组。

- Event真实commit rejection：PG deferred constraint trigger在COMMIT阶段拒绝event_store写入；真实Spring事务失败后Trade=0/Event=0/Ledger=0。撤除本轮fault trigger后同一canonical写入Trade=1/Event=1。没有mock commit异常。
- Concurrent recovery：两个独立Spring事务上下文/连接同时恢复同一durable Trade。控制连接先锁source，pg_stat_activity实际观察2个Lock waiter，然后释放；两次恢复均完成，事件恰好1。已有随机ID事件在四组中的legacy两组保持完整原快照；重复恢复5次不变。
- Event failure / Ledger已存在：raw source-only repository仅用于该隔离PG边界fixture，canonical Ledger真实提交；事件commit拒绝不会重复或改写Ledger。解除fault后event=1，Ledger重放idempotent hit。真实进程版另用旧producer恢复窗口证明该状态可达。
- Ledger failure / Event已存在：新Trade+Event已提交；PG deferred trigger拒绝ledger_entries的COMMIT，entries/events/positions全部回滚，TradeExecuted原快照保持。随后canonical Ledger恢复、再次重放，事件不重复、账务不变。
- 历史事件payload冲突：通过真实EventStoreAppender写入不一致的测试事件，恢复fail closed，source和已有事件原样保留。没有通过删除事件制造缺口。

## 验证记录与失败历史

| 验证 | 实际结果 |
|---|---|
| unit定向 | JdbcTradeRepository2、OkxRestReconcileService21、OperationalReconciliationMetrics2，共25 PASS |
| 进程矩阵attempt03 | JUnit1 / 10独立场景PASS，failure/error/skip=0/0/0，Maven exit0，3:34 |
| B4 PG attempt02 | 4 PASS，failure/error/skip=0/0/0，exit0，33.218秒 |
| 相关回归 | 89 PASS，failure/error/skip=0/0/0，exit0，1:15；清单如下 |
| Full Maven唯一一次 | 1844 tests、failure0/error1/skip90，exit1，1:48；仅Research测试缺少runner的canonical account fixture，详情如下 |
| 单项缺口补验 | 原样准备canonical CI fixture后，ResearchBacktestHappyPathLocalTest 1/1 PASS，exit0，19.392秒；没有第二次Full Maven |

89项：KillSwitchRiskRule2、KillSwitchService5、OrderCommandService8、TradeLedgerPostingService2、OrderCommandWriteService27、B0FixtureSafety8、B2TerminalCorrectionPG13、B4RealProcessProof1（当前原子cut正例与正常对照）、L4PlanBlockerPG22、TradingChainPG的recoversLedgerAfterDurableTradeAndRemainsIdempotent1。覆盖B1/no blind retry、B2 per-fill/OCC/environment、原Ledger recovery；B3/Kill下必要恢复另由本轮10行真实进程证明。没有完整重跑B1/B2/B3 qualification。

定向命令均为`mvn -o -f backend/pom.xml -pl nq-app -am`加精确`-Dtest`及相应`nq.b4.remediation`/`nq.b4.pg`开关。相关回归runner为target/b4-run-validation.py targeted，记录完整参数与隔离URL于target/b4-remediation-targeted/command.json；环境白名单且显式绑定本轮PG16。不存在真实provider/凭证继承。

保留本轮失败attempt，不当成旧P1复现成功：

1. unit attempt01仍断言旧scheduler的append调用，因事件ownership已移到原子repository而失败。改为断言insertWithRequiredEvent/ensureRequiredEvent，新事务真实性由PG/进程测试证明，不把mock调用当持久化证明。
2. process targeted attempt02在SIM NORMAL发现本轮SQL JSONB减法运算符优先级错误（unknown - unknown），事务拒绝。补齐payload括号后attempt03全部通过；不是生产历史缺陷，不隐瞒修复过程。
3. PG attempt01共4项，3 PASS/1 ERROR：测试自建ObjectMapper默认数字timestamp，与真实Spring ISO配置不符，legacy ts cast拒绝。仅把fixture mapper对齐Spring WRITE_DATES_AS_TIMESTAMPS=false；production未改，PG attempt02四项通过，真实旧Spring事件兼容另已有进程证据。

4. 唯一Full Maven采用新建空PG，runner遗漏`.github/workflows/ci.yml`已有的`BackendCiLegacyAccountFixture`步骤。ResearchBacktestHappyPathLocalTest第59行查询accounts首行时EmptyResultDataAccess，发生在本轮交易代码调用前；该测试与其生产代码均未改。其余Full测试无failure/error。随后在另一个owned PG原样编译/执行`scripts/ci/BackendCiLegacyAccountFixture.java`，再仅补跑这一项，PASS。没有修改CI/fixture/Research源码、没有新增seed workaround；单项补验不将原Full command的exit1改写成exit0。90项为条件性test skips，不是CI job skips；本轮B4必要PG/进程测试已用显式开关独立运行。完整逐类结果见[test summary](l4-b4-trade-event-remediation-attempt01/test-summary.json)。

Full Maven前后五个production文件字节一致，[production candidate SHA-256清单](l4-b4-trade-event-remediation-attempt01/production-candidate.json)绑定本轮源代码；单项fixture补验未修改候选。Full命令为`mvn -f backend/pom.xml test`，只有本轮owned PG datasource环境绑定；完整命令/日志/退出值保留target/b4-remediation-full。补验记录为target/b4-remediation-full-gap，完整的Full run继续标为FAILED，而非宣称新一轮全量绿色。

证据导出复用common exporter：10行完整逆映射等于原始树，raw identity泄漏0，逐字段payload验证10/10。pinned Gitleaks8.18.4 archive/hash及CI配置原样复用，3345个safe files扫描0 findings、6/6 secret negatives REJECT；另对所有34个修改/未跟踪候选文件完整扫描exit0 / findings0，覆盖新infra helper及全部新证据。stage-assets scanned1826 / reviewed_exceptions173 / errors0；git diff --check通过。没有secret allowlist、bound hash或CI修改。最终本轮owned PG容器与所有child无残留。以上验证不构成后续exact-head CI。

## Project lesson、范围与后续

已在现有[engineering-lessons](../../../.agents/skills/nq-trading-correctness-proof/references/engineering-lessons.md)追加简短Durable Source Fan-out Rule：症状/共同根因、F004单分支不足、A/B判断、排查顺序、永久故障/并发回归。没有新建Skill或治理文档；只记录当前普通OKX机制的实现闭合，不扩大其他producer能力声明。

Production变化仅5文件：TradeExecuted合同（兼容可选环境）、TradeRepository两个明确能力入口、JdbcTradeRepository原子/恢复事务、RequiredTradeEventStore、OkxRestReconcileService调用切换。没有修改Order状态机、Ledger实现、migration、.github、AGENTS、Skill topology或current authority。原B4 exporter两类修复作为已有未提交工作保留，未另加本批例外。

Rollback：本轮未发布，没有生产数据库rollback操作。若将来撤回代码，既有Trade/Event/Ledger不可删除；回退到旧producer会重新暴露缺口，必须另行评估停机/写入版本边界，不能以回退为由改写历史事件。Flyway保持forward-only，本轮migration=0。

审查状态：实现者自查；**PENDING_INDEPENDENT_CORRECTNESS_REVIEW**。本轮不自封独立审查，不commit/push，不进入B4 precise delivery；后续独立review核对候选指纹并决定需要复现的关键场景。

Next action：`NQ-GATEAUDIT-PHASE6-L4-B4-TRADE-EVENT-DURABILITY-INDEPENDENT-CORRECTNESS-REVIEW`。B4仍NOT_ACCEPTED，剩余failure-atomicity/process-death qualification尚未恢复。

本地结论：ROOT_CAUSE_IDENTIFIED / ROOT_CAUSE_FIXED / TRADE_EVENT_RECOVERY_PROVEN / LEDGER_RECOVERY_PRESERVED / PERMANENT_REGRESSION_ADDED / PROJECT_LESSON_CAPTURED。P0=0、LOCAL_P1=0，均限本轮普通OKX Trade/event整改范围；不是独立review结论。

**IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW / B4_TRADE_EVENT_DURABILITY_REMEDIATED / RECURRING_DURABLE_FANOUT_ROOT_CAUSE_CLOSED / P0_0 / LOCAL_P1_0**。
