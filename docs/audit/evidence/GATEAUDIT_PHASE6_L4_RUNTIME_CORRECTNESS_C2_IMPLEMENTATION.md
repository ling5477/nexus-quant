# Phase6 L4 C2 CANCELLED fill convergence implementation

本证据属于本地 implementation candidate，不是 authority acceptance、独立 review 或 L4 qualification。
任务：`NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C2-IMPLEMENTATION`。

## 基线与权限

- branch=`audit/post-gatey-agent-baseline`；fetch 后 HEAD=origin=`ff5a26b7bd166b3591b142a20058a8c22e38d75e`。
- 初始 worktree=CLEAN、staged=0；`git fetch origin --prune`、status、branch、两个 rev-parse、cached name-only、diff check 均成功。
- accepted batch=`GateAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C1 / ACCEPTED|CI_GREEN`；implementation=`41c3bbcb210a65bf2b7b5aad9885d6f9e7bdccdd`，acceptance head=`eb9740b7519f48ffc1e32968cbb0950261b871ef`，CI=`34098902705`。
- work batch=`GateAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C2 / NOT_STARTED / NONE / NOT_RUN`；machine next action 仍为本轮 IMPLEMENTATION。P1 before：P1-2 CLOSED、P1-3 OPEN、canonical blocking P1=1、reachability unknown=0。
- 当前 authority 全部保持原样；kill=ENGAGED、LIVE=DISABLED。测试中的 PLACE/CANCEL/kill 设置只作用于专用 PG16 与 synthetic venue。
- 不执行 git add、commit、push、远端写操作、真实 provider、B0 或 L4 qualification。

## 根因与最小生产实现

唯一 production 文件：[OkxRestReconcileService.java](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java)。

候选集从 `SENT / ACCEPTED / PARTIALLY_FILLED / CANCEL_REQUESTED / CANCEL_REJECTED / FILLED` 增加 `CANCELLED`，仍调用 `findOrdersByStatuses(statuses, limit)`。
原盲区是 CANCELLED 未进入扫描，venue fill 无法到达 canonical Trade/Ledger owner。

新分支 `reconcileCancelledOrder` 只处理终态执行事实：

1. 要求 externalOrderId 非 null、非 blank；否则记录既有 `OKX_RECONCILE / UNRESOLVED` 低基数观测并返回 0。
2. 复用现有 `reconcileFills`、`ensureLedgerConvergence`、`TradeLedgerGateway`，没有第二套 Trade/Ledger 实现。
3. 不调用 getOrder、linkExternalOrderId、OrderLifecycleService、PLACE、CANCEL 或其他 provider 写操作。
4. Order=CANCELLED、version 不变；不合成 CANCEL_REQUESTED，不改变状态机拓扑或 C1 OCC。
5. 使用专用 `OKX_CANCELLED_ORDER_FILL_BACKFILL_COMPLETED` audit，字段为 order_id、status、external_order_id、new_trades。
6. 原 FILLED 分支及 `OKX_FILLED_ORDER_FILL_BACKFILL_COMPLETED` 语义、填单身份校验与账本 owner 均未改。

Schema、Flyway、recovery table、queue、scan marker、Binance production、WS production、adapter contract、C1 production diff 均为 0。

### 有界性与失败路径

| 层次 | 保留契约及证明 |
|---|---|
| candidate | 原调用的 limit 原样下传；无 findAllCancelledOrders 或无界查询；本轮确定性 fixture 未观察到 starvation |
| venue | `tradeReports.size() > limit` 仍以 `VENUE_REPORT_LIMIT_EXCEEDED` 拒绝；新增 CANCELLED unit 在任何 Trade/Ledger/event 写入前验证拒绝 |
| durable Trade | `findAllByOrderId(orderId, limit)` 原样保留；repository overflow 仍记录 `DURABLE_TRADE_LIMIT_EXCEEDED` 并抛出 |
| identity | venue/account/client/external order/trade identity mismatch 负例均拒绝；现有 durable owning-order identity PG 回归保留 |
| duplicate report | 重复 exchangeTradeId 仍抛 `DUPLICATE_VENUE_EXCHANGE_TRADE_ID`；原逐项流水线可能已提交首条有效事实，不声称整批原子回滚；第二条不会产生第二笔 Trade/event/ledger posting |
| repeated recovery | Trade 以 exchange+exchangeTradeId 去重；账本沿用 `tradeId:LEDGER:<suffix>` 稳定键并返回 IDEMPOTENT_HIT |

`TradeLedgerPostingService.buildEntries` 对本 fixture 的零手续费成交生成两条成对分录；fee>0 的既有额外分录语义未变。本轮 Ledger=2 来源于当前 canonical 实现，不引入新会计模型。

## 真实 PG16 证明

专用 PostgreSQL **16.15**、真实 Spring context/JDBC/Flyway V47、真实 Order/Trade/Ledger/event_store/audit_logs；venue 使用现有 synthetic OKX transport，loopback/no-outbound guard 生效。无 Thread.sleep、概率竞态或真实交易所调用。

[L4PlanBlockerPostgresIntegrationTest.java](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L4PlanBlockerPostgresIntegrationTest.java) 将旧 P1-3 characterization 保留并改名为 `cancelledOrderWithVenueFillBackfillsTradeAndLedgerIdempotently`，移除该方法的 known-defect-reproduction tag。C1 四个测试方法及 R05 方法体与固定 HEAD 逐字比较一致；摘要见本地 `artifacts/20260907-c2-implementation/protected-regressions.json`。

| 场景 | 直接断言的结果 |
|---|---|
| 首轮 PLACE→ACCEPTED→CANCEL→CANCELLED→late fill | fill query delta=1、newTrades=1、Order=CANCELLED、version=5→5、Trade=1、Ledger=2、TradeExecuted=1 |
| 第二轮同一成交 | fill query 再 +1、newTrades=0、Trade=1、Ledger=2、TradeExecuted 仍 1、LedgerPosted 仍 1；重复 Trade/event=0 |
| Order fact 不变量 | 完整 OrderRecord 前后相等；DB version 相等；ORDER_STATUS_TRANSITION、ORDER_ACKED、ORDER_CANCELLED、ORDER_CANCEL_REQUESTED audit 与 OrderAck、CancelAck、order topic event 数量均无 delta |
| audit | dedicated CANCELLED completion 分别包含 new_trades=1 与 0；未产生 FILLED_ORDER completion |
| durable Trade / missing Ledger | 注入一次 ledger failure 后 Trade=1、Ledger=0、TradeExecuted=1；隐藏 venue reports 后重放恢复到 Trade=1、Ledger=2；再重放保持计数与 tradeId 不变 |
| partial fill | ordered=0.2、executed=0.1；两轮后 CANCELLED、version 不变、Trade=1、Ledger=2；kill 维持 ENGAGED |
| no fill | 稳定 externalOrderId + empty reports：newTrades=0、Trade=0、Ledger=0、TradeExecuted=0；Order/version 不变 |
| ordinary positive control | ACCEPTED + venue fill → FILLED、Trade=1、Ledger=2 |
| R05 | kill=ENGAGED 拒绝新 PLACE 为 RISK_REJECTED，既有成交仍完成 Trade/Ledger convergence；kill 不解除 |
| C1 | stale PLACE、stale CANCEL、ABA、pre-cancel 全部通过，stale CAS affected=0，pre-cancel gateway dispatch=0 |
| outbound | 主 C2 场景两轮 recovery 期间 order-query delta=0、PLACE 计数仍 1、CANCEL 计数仍 1；unit 验证零 lifecycle/command mutation |

表中的 CANCELLED/version 数据是在 teardown 前从真实 DB 断言的事实。用例结束后临时 fixture 会被退役为 REJECTED，不能把清理后的数据库状态冒充业务断言时状态。

### 必要的测试隔离调整与失败历史

- 初始 PG run：9 tests / 8 failures / 0 errors / 0 skips，日志 `pg16-l4-attempt01.log`。部分成交用例通过后，其 CANCELLED 遗留订单被下一用例扫描，synthetic venue 已 reset，报 `unknown deterministic external order id`。
- 最初尝试 teardown 清空 external_order_id；既有 V5 trigger 会从 exchange_order_id 恢复该值，因此无法退役。最终未引入兼容列访问，改为断言完成后的 fixture-only `status=REJECTED`。
- 同样调整 [TradingChainPostgresIntegrationTest.java](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TradingChainPostgresIntegrationTest.java) 的现有 retireTestOrders：只有 teardown 状态和解释注释变化，所有业务断言不变。该类 8 个测试通过。
- 首次 full Maven 的 2 个失败均为未修改 `OrderVersionFlywayPostgresIntegrationTest:27` 的启动参数拒绝：该 fixture 要求 JVM system property 与精确 `/nq_l4_blocker` 库名；初次 runner 仅传环境变量并使用带后缀的库名。保留 `full-maven-attempt01.log`，重建临时库、修正 runner 后重跑；未削弱该测试或改 schema。
- Windows Docker read-only version probe 未返回；只停止本轮已核对的 probe shell/child，未操作其他 Docker 进程。PG 使用仓库已缓存 WSL runtime，无新增下载。

## Validation

所有 Maven 使用 `-o -f backend/pom.xml`；目标测试加 `-Dsurefire.failIfNoSpecifiedTests=false`，各目标 mandatory tests 的 skips=0。

| 范围 | tests | failures | errors | skips | 结果 |
|---|---:|---:|---:|---:|---|
| OkxRestReconcileServiceTest | 18 | 0 | 0 | 0 | PASS |
| affected scheduler recovery | 17 | 0 | 0 | 0 | PASS |
| dedicated PG16 L4 C1+C2 | 9 | 0 | 0 | 0 | PASS |
| Trade/Ledger + PG causal + architecture | 36 | 0 | 0 | 0 | PASS |
| full backend Maven | 1794 | 0 | 0 | 50 | PASS / exit 0 |

执行顺序和命令：

```powershell
mvn -o -f backend/pom.xml -pl nq-scheduler -am '-Dtest=OkxRestReconcileServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -o -f backend/pom.xml -pl nq-scheduler -am '-Dtest=OkxRecoveryServiceTest,BinanceRestReconcileServiceTest,OkxWsOrderAccelerationServiceTest,BinanceWsOrderAccelerationServiceTest,LedgerReconcileSchedulerTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
# 以下使用本轮专用 PG16 loopback datasource；初次 targeted 运行以 SPRING_DATASOURCE_* 绑定。
mvn -o -f backend/pom.xml -pl nq-app -am '-Dtest=L4PlanBlockerPostgresIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l4.blockers.enabled=true' '-Dlogging.level.org.springframework.boot.autoconfigure.security=ERROR' test
mvn -o -f backend/pom.xml -pl nq-app -am '-Dtest=TradingChainPostgresIntegrationTest,TradeLedgerPostingServiceTest,JdbcLedgerPostingRepositoryTest,PackageBoundaryArchTest,ModuleBoundaryArchTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dlogging.level.org.springframework.boot.autoconfigure.security=ERROR' test
# full runner 同时绑定 SPRING_DATASOURCE_* 和 JVM properties，具体临时端口见 postgres-instance.json。
mvn -o -f backend/pom.xml '-Dnq.l4.blockers.enabled=true' "-Dspring.datasource.url=$env:SPRING_DATASOURCE_URL" '-Dspring.datasource.username=postgres' '-Dspring.datasource.password=disposable-test-only' '-Dlogging.level.org.springframework.boot.autoconfigure.security=ERROR' test
```

本地日志与机器摘要位于 `artifacts/20260907-c2-implementation/`，属于 ignored artifacts，不是已提交 CI 证据。test-results.json 汇总每个 Maven module 的最终计数，避免把每类计数与 module subtotal 重复累计。

全量首次执行为 1794/2/0/50，修正 fixture 启动参数后为 1794/0/0/50，最终耗时 1 分 30 秒。两次运行之间 production/test candidate 未变；未在每个小改动后重复全量测试。50 是 JUnit 条件跳过，不是 CI job skip；XML 逐项原因保存在 conditional-skips.json，涉及显式 opt-in PG/外网诊断和平台文件系统条件等。所有本轮强制 C2、C1 interaction、Trade/Ledger、kill 回归无跳过。新 CI 未运行。

### Governance 与工程约束

| 检查 | 结果 |
|---|---|
| stage asset checker | errors=0，scanned=1802、reviewed exceptions=178 |
| stage guard | Windows 49 tests / 0 failure / 2 既有 symlink 权限条件跳过；Linux 49/49、0 skip |
| F009 graph | 18 contracts、105 members、1559 actual=approved edges；new=0、stale=0、policy diff=0 |
| Java standard | verify-java-engineering-standard PASS |
| Java architecture / Shadow | 两个 ArchUnit 类通过；Shadow NEW_CODE_VIOLATION_COUNT=0，existing baseline=143、ruleset expansion=14；未改 baseline |
| authority PS5.1 / PS7 | PASS，exit 0 |
| next-action / lifecycle / agent workflow | PASS，exit 0 |
| docs links | checked=439、warnings=123、errors=0；非阻断警告保留 |
| Gitleaks | pinned 8.18.4、官方缓存 archive checksum 与 repository lock 一致；tracked safe working tree + 新 evidence 共 3148 files，findings=0、exit 0 |
| final diff / rollback | git diff --check 与反向补丁 git apply --check 通过；staged=0 |

Primary Skill=`java-backend-maintenance`；supporting=`nq-java-engineering-standard`，exact trigger=`TRADING_CORE / TRANSACTION_CONCURRENCY`。已读 platform-profile、common Java standard、NQ domain overlay。

- PASS：既有状态机/OCC、Trade/Ledger owner、事务边界、稳定幂等键、bounded recovery、identity fail-closed、audit、SIM/no-outbound、失败恢复；新增分支没有事务或资源所有权变化。
- NOT_APPLICABLE：新 migration、依赖升级、executor/queue、跨模块架构重构、真实 provider 验证、生产部署。
- 领域 overlay 的历史模式词汇不覆盖 current authority 的 SIM/LIVE；未按过时词汇扩展 runtime。
- 无新增标准豁免、allowlist 或 compatibility caller authorization。

## 候选判定、范围与回滚

最终 candidate 判定：

```text
IMPLEMENTED /
PHASE6_L4_C2_CANCELLED_FILL_TRADE_LEDGER_CONVERGENCE_REMEDIATED /
P0_0 / P1_3_REMEDIATED / CANONICAL_P1_0_CANDIDATE /
PENDING_INDEPENDENT_CORRECTNESS_CLUSTER_REVIEW
```

P1-3=`REMEDIATED_PENDING_INDEPENDENT_CLUSTER_REVIEW`；本轮范围内 P0=0、P1-3 remaining=0 candidate、canonical remaining P1=0 candidate。此为本地候选结论，非正式 P1 closure 或 C2 acceptance；当前 machine authority 未迁移。

剩余边界：独立 cluster review、exact-head CI、B0、L4 qualification、真实 provider/生产行为均未执行；公平调度/规模验证不在本轮。专用 PG16 最终已停止并删除临时 data directory（postgres-instance.json cleanup=PASS）；本轮未留下运行中的 PG fixture。

变更文件：一个 production service、OkxRestReconcileServiceTest、L4PlanBlockerPostgresIntegrationTest、TradingChainPostgresIntegrationTest 的必要 teardown、本文，共五个路径。

回滚产物：`artifacts/20260907-c2-implementation/rollback.patch`，包含四个 tracked files 的反向增量与本文的删除；`git apply --check artifacts/20260907-c2-implementation/rollback.patch` 仅预检，不执行回滚。保留 ignored 日志和 evidence artifacts，避免删除审查依据。

Git：staged=0、commit=NONE、push=NONE；建议后续经 review 后的 commit message：`fix(reconcile): 恢复撤单终态的成交与账本收敛`。

成功后建议动作：`NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C1-C2-CLUSTER-INDEPENDENT-REVIEW`，仓库 matcher 唯一类型=`REVIEW`。只审 C2 correctness、C1/C2 interaction 和 canonical P1=0 candidate，不从零重审已接受 C1，不直接进入 B0。

工具声明：Git、rg、PowerShell 5.1/7、Python、Maven/Java、WSL、PostgreSQL、Gitleaks；functions 执行本地命令与补丁。未使用 connector MCP 或子代理；网络仅 git fetch origin，只读核验固定基线；依赖、PG16、scanner 使用本地缓存。写操作限候选文件、ignored artifacts 与专用临时 PG fixture。生产、真实交易、凭证读取、远端发布均未执行。
