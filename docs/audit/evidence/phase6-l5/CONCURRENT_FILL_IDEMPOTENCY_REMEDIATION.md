# L5 同 fill 并发幂等与 overfill 边界整改

任务：`NQ-GATEAUDIT-PHASE6-L5-CONCURRENT-FILL-IDEMPOTENCY-AND-OVERFILL-BOUNDARY-REMEDIATION`。

结论：**IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW**。本地 P0=0、LOCAL_P1=0、LOCAL_BLOCKING_P2=0；这是实现自查与测试结论，尚未独立接受。L5 保持 `NOT_ACCEPTED`，未运行 C2/C3 qualification。stage=0、commit=NONE、push=NONE。

## 根因与最小修复

根因为 `IDEMPOTENCY_CLASSIFICATION_AFTER_QUANTITY_VALIDATION`。原仓储顺序为 Order 行锁 → SUM(Trade.qty) → executed+incoming.qty 上限检查 → INSERT。锁外预检不能保证 incoming fill 仍是新事实，因此同 fill 输家把赢家已提交数量再算一次。原正式 [C3 失败证据](CONCURRENT_WORKLOAD_AND_BACKLOG.md)及其 raw/summary 均保留。

当前顺序为 Order 行锁 → 按既有 `(exchange, exchange_trade_id)` 分类 → 校验既有 fill 内容或验证新 fill 数量 → 原子 Trade/TradeExecuted。唯一键来自现有 V1/V5，内部 `trade_id` 是赢家事实的 ID；没有新增请求/进程/时间身份或 schema。重复 key 的订单、账户、品种、外部订单、价格、数量、费用、费用币种、成交时间不一致时 fail closed，不吞数据库唯一冲突。

`insertWithRequiredEvent` 保留 void 契约；幂等命中使用赢家 ID 确保必需事件。OKX reconciliation 在写入后回读 durable Trade，用同一 ID 进入 Ledger recovery，只有实际新建 Trade 计入 newTrades。普通严格 `insert` 接口保留既有调用语义；本轮没有扩大到历史 Binance/Paper producer 的重复处理契约。quantity 上限继续使用同事务锁定的 durable effective Order.qty，没有修改 V49/V50/V51 或请求规范化。

## 验证与候选

起止 HEAD=`991187fe772ad8b03746a4a9ddfc3ea9010e5896`，branch=`audit/post-gatey-agent-baseline`。保留入口既有 projection 改动，以完整当前工作区为技术基线；没有改写它们的失败/接受证据、原 C3 raw/summary 或 current authority。[1832 项技术输入 manifest](fill-idempotency-remediation/candidate-manifest.json) SHA-256=`fd1091e5497fe8b80583c99efa19003e5729e4059c92bff705c03f18ebb7f9e1`。目标验证后、Full Maven 副本中及运行后逐文件匹配，drift=0；新旧验证不跨候选借用。

环境为 Windows 11、Java 21.0.9、Maven 3.9.12、Docker Desktop 29.7.2、自有 loopback PostgreSQL 16.15/V51；镜像使用 canonical lock 固定 digest。独立受控 Venue 与真实 Spring/RiskGate/adapter/reconciliation 产生端到端事实，没有真实交易所或 LIVE 执行。仓储负例使用真实 PostgreSQL、事务代理与现有 NQ repository/service；不把 mock 作为核心证明。

最终目标验证：**51 tests / 0 failures / 0 errors / 0 skipped**。可复跑入口：

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am test '-Dtest=L5FillIdempotencyTest,L5FillPostgresTest,L5FixtureCleanupTest,L5ProjectionConcurrencyTest,L5ProjectionRecoveryTest,B4TradeEventPostgresTest,OkxRestReconcileServiceTest,JdbcTradeRepositoryTest,TradeLedgerPostingServiceTest,JdbcLedgerPostingRepositoryTest,JdbcLedgerReconcileRepositoryTest,L5DriverContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l5.fill=true' '-Dnq.l5.projection=true' '-Dnq.b4.pg=true' '-DargLine=-Xmx768m'
```

没有启用 `nq.l5=true`，没有运行 `L5BoundedWorkloadTest` 或任何 C1/C2/C3 scale qualification。`L5DriverContractTest` 仅验证既有命令边界。

| 证明 | 实际结果 |
| --- | --- |
| 原 C3 最小红色复现 | 两个独立 NQ JVM、一个 Order、同一个 fill；两个 actor 都通过锁外预检，PG 实测两个锁等待者；旧生产实现输家抛出原 overfill 错误。 |
| 两 JVM 同 fill | PG 实测 2 个等待者；一个 `RECOVER 1`、一个 `RECOVER 0`，均正常结束；Trade=1、TradeExecuted=1、Ledger=4、Position/latest BTC Snapshot=0.1。 |
| 四路竞争及相邻压力 | PG 实测 4 个等待者；只有一个新 Trade 返回，其他为 benign `RECOVER 0`；随后 5 波四路重放及两次顺序重放，完整持久账务/投影快照不变，无 false overfill 或 aborted transaction 残留。 |
| 不同 fill 正例 | Order=1.0；两个不同 fill 为 0.6、0.4，均接受；总量与 Position/Snapshot=1.0。 |
| 真 overfill | 已有 0.8、新唯一 fill=0.3、Order=1.0，仍以 `RECONCILIATION_OVERFILL_OR_INVALID_QUANTITY` 拒绝；Trade/Event/Ledger/Position/Snapshot 不变。 |
| 冲突事实负例 | 同 fill 改数量、跨订单/账户复用 fill，以及不同 fill 的真实内部主键碰撞均拒绝；拒绝后独立合法事务可以继续提交。 |
| 顺序重放 | 已应用 fill 使用新的临时内部 trade_id 重放三次，均回读赢家 ID；Trade/Event/Ledger/Position/Snapshot 全量快照不变。 |
| COMMIT ambiguity / restart | wire proxy 确认 PG 已提交 Trade+Event 后扣留真实 COMMIT 响应；独立 actor 先从 durable truth 恢复 Ledger。原 JVM 终止后新 PID successor 重放三次，全链事实保持不变，无重复累计。 |
| effective quantity | 新边界测试证明 durable Order=0.1 时，已满额后再来唯一 0.0001 必须拒绝。既有真实 V51 路径同时重跑通过：requested=10.0005、effective/Order/Trade/Position/Snapshot=10.0。 |
| projection/B4 回归 | 1/2/4 JVM 唯一事实、首次初始化、旧 writer 暂停、账户/币种隔离、base fee/逆序时间、owner death、COMMIT 前后恢复以及 Trade/Event 同事务提交拒绝均通过。 |

原始日志、逐场景证明与摘要哈希见 [results.json](fill-idempotency-remediation/results.json)。最终目标日志位于 `backend/nq-app/target/l5-fill-remediation/focused-final01.log`，日志及本次 suite XML 归档为同目录 `focused-final01-surefire.zip`。随机 runtime identities 只留 target，不复制到 tracked evidence。

## Fixture cleanup 单独结论

已修正 `L5BoundedWorkloadTest` 的批量退出路径：STOP 的 closed-pipe 不得跳过当前及其他 owned child；每个 Child 在 finally 强制完成必要终止、等待退出并关闭 writer，逐个失败聚合后再报告。只有已确认进程退出时才记录并忽略失效 stdin 刷新错误；仍存活或终止失败继续拒绝。`awaitReady` setup 失败保留首因及 cleanup suppressed error。`L5QualificationControls` 即使 actor.close 抛异常，也必须 shutdown sampler 并关闭采样 writer。

`L5FixtureCleanupTest` 的四个真实路径全部通过：PASS、assertion FAIL、unexpected command exception/closed stdin、第二阶段 setup failure。每种均断言 owned NQ=0、Venue=0、PG=0；失败 setup 生成的 JVM 也核对已退出。只操作本 session 登记的 Process 和确切容器身份，无全局 Java/Docker 清理。

额外按本轮全部日志登记的 80 个不同 JVM PID、34 个 PostgreSQL container ID 只读核验，remaining=0/0；Full Maven 的额外独立 PG 也已删除。该结果与 production correctness 分开记录。

## Full Maven 与失败保留

最终技术候选稳定后只执行一次完整 `mvn -f backend/pom.xml test`：**1919 tests / 0 failures / 0 errors / 140 conditional skips / exit=0**。本轮要求的新 PG/多 JVM/cleanup 场景均已在目标 run 实际执行，skipped=0；Full Maven 的 140 个条件跳过不记作通过。

Full Maven 在字节一致的干净副本中运行：复制 3897 项工程输入，1832 项冻结技术输入全匹配，不复制旧 target 源码。采用新建 PG 16.15/V51 和当前 `BackendCiLegacyAccountFixture`，验证 PAPER account=1、exchange account=0、credential=0；向测试进程同时绑定 NQ_DB 与 SPRING_DATASOURCE，清除继承 profile/datasource，沿用 CI/no-outbound 设置。fixture/CI 源文件及镜像摘要记录在 results 中，未修改 `.github/**`。

全量日志为 `backend/nq-app/target/l5-fill-remediation/full01/full-maven.log`；`surefire-and-log.zip` 同时保留日志与全部 XML。XML 合计1914，与日志的1919差5，原因仍是 ModuleBoundary/PackageBoundary 同名 Jupiter+ArchUnit suite 覆写 XML；逐 engine 日志和 Maven module totals 保留完整 PASS，不删减测试。归档 SHA-256=`099ca04f6fb0f629c051938bd2ed135f7759be54e204a275fa6fe1f23d2ea1ca`。

失败 attempt 均保留：`red-attempt01` 为旧生产 false overfill；`boundaries-cleanup-attempt01` 为全部资源实际退出后 writer 刷新 closed stream 导致的 fixture ERROR，随后最小修复并由 `cleanup-attempt02` 与最终目标 run 通过。它不是新的 production finding。`green-attempt01` 为早期并发/ambiguity 三场景通过；最终结论绑定 `focused-final01` 与上述唯一 Full Maven，不借早期候选覆盖最终 fixture。

## 状态与限制

`L5_CONCURRENT_FILL_IDEMPOTENCY_REMEDIATED / DUPLICATE_FILL_FALSE_OVERFILL_P2_REMEDIATED / TRUE_OVERFILL_GUARD_PRESERVED / TRADE_ACCOUNTING_EXACTLY_ONCE_PRESERVED / QUALIFICATION_FIXTURE_CLEANUP_REMEDIATED / P0_0 / LOCAL_P1_0`。

本轮只做整改和必要回归，未进行独立审查、qualification、stage/commit/push 或 CI 发布验收。未改写历史失败与 accepted authority，L5 仍 `NOT_ACCEPTED`；其他历史非阻断残余不在本轮清零。工程经验已合入既有 `Idempotency Before Business Validation Rule`，没有新增 Skill。

收尾检查：doc-links=17 checked/0 warning/0 error；stage-assets=1897 scanned/173 reviewed exceptions/0 error；固定 Gitleaks 8.18.4 对本轮16个变更/新增文件扫描为0 finding；git diff --check通过。没有刷新摘要例外或修改 scanner policy；卫生检查不替代独立正确性接受。

## 后续

下一任务为 `NQ-GATEAUDIT-PHASE6-L5-CONCURRENT-FILL-IDEMPOTENCY-INDEPENDENT-CORRECTNESS-REVIEW`。仅安排一次真正独立的候选审查，关注同 fill no-op、真 overfill 拒绝、账务唯一与重放投影不变；不重审 L5 scale matrix。
