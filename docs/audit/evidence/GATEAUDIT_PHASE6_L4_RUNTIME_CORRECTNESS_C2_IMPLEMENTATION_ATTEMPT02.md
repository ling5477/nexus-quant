# Phase6 L4 C2 implementation attempt 02 — shared candidate budget

任务：`NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C2-IMPLEMENTATION-ATTEMPT-02`。
本文件记录本地候选，不是独立 review、CI acceptance、delivery 或 L4 qualification。

```text
IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW
```

## 授权、基线与历史

- branch=`audit/post-gatey-agent-baseline`；HEAD 与本地 origin tracking ref=`ff5a26b7bd166b3591b142a20058a8c22e38d75e`。本轮未联网刷新远端。
- 用户本轮确认此前 `C2_SCAN_LANE_SPLIT_VIOLATES_ACCEPTED_LIMIT_CONTRACT` 阻断成立，并指定保留一次共享 bounded scan；不授权双 lane、公开 limit 修改或 fairness 修复。
- current authority 仍为 C1 accepted / C2 NOT_STARTED；STATUS、ROADMAP 和其他 current authority 文件保持不变。本地候选不宣称 P1-3 已正式 accepted/closed。
- Review-01 fingerprint=`9865a0ca169f4f4cdbfb89ddae2cccbbb397e43992e371e0cfe339d8d9f0ab1c`，原结果=`FAIL / P0_0 / P1_1 / NOT_READY_FOR_DELIVERY`，均保留。
- 原 [C2 implementation evidence](GATEAUDIT_PHASE6_L4_RUNTIME_CORRECTNESS_C2_IMPLEMENTATION.md) 字节不变。其中未观察到 starvation 和 canonical P1=0 的早期候选主张已被 Review-01 反例否定，不作为本轮结论。
- Review-01 原件位于本地 `artifacts/20260907-c1-c2-independent-review/REVIEW.md`；本轮没有覆盖任何旧 review、manifest 或日志。

## 最终生产行为与公开契约

唯一 production candidate 文件：[OkxRestReconcileService.java](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java)。相对 Review-01 仅细化 Javadoc：单次扫描的订单候选总上限包含 CANCELLED，所有状态共享一次查询预算；执行逻辑字节不变。

一次 `findOrdersByStatuses(statuses, limit)` 使用：

```text
SENT / ACCEPTED / PARTIALLY_FILLED / CANCEL_REQUESTED / CANCEL_REJECTED / FILLED / CANCELLED
```

查询继续是 `WHERE status IN (:statuses) ORDER BY created_at ASC LIMIT :limit`。返回列表中的订单按状态分派，单次 Order candidate 总数 <= limit，无第二次 CANCELLED lane 查询。每个订单的 venue report / durable Trade 原有上限仍为 limit。

对外证据保持原样：

- [ReconcileRunOnceRequest.java](../../../backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/ReconcileRunOnceRequest.java) 的 OpenAPI 描述仍为“单次扫描上限；为空时默认 100”。
- [TradingVerificationController.java](../../../backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/TradingVerificationController.java) → [TradingMaintenanceService.java](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/maintenance/TradingMaintenanceService.java) → [SchedulerTradingMaintenanceService.java](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/SchedulerTradingMaintenanceService.java) 原样传递 limit；内置 scheduled、OkxRecoveryService 与 OkxWsDegradeReconcileCoordinator 调用也未改。
- [TradingWorkbenchPage.tsx](../../../frontend/src/pages/trading/TradingWorkbenchPage.tsx) 的“扫描上限”、请求参数和默认值不变。

CANCELLED 分支要求稳定 externalOrderId，否则记录 UNRESOLVED 并在任何 fill 查询和 Trade/Ledger 操作前返回。它复用 `reconcileFills` → canonical Trade 去重/TradeExecuted → existing Ledger convergence；不调用 getOrder、status alignment 或 Order lifecycle writer。部分成交后撤单仍为 CANCELLED，完整 OrderRecord/version 不变；recovery 无 PLACE、CANCEL、transfer、withdraw 或 kill switch 解除。

## 必须保留的独立 finding

`P1 / CANCELLED_SCAN_WINDOW_STARVES_ACTIVE_RECOVERY = OPEN / NOT_REMEDIATED_IN_ATTEMPT02`。

Review-01 以真实 Spring/PG16 证明：较早 CANCELLED 无成交、较新 ACCEPTED 有成交时，limit=1 连续三轮均选中 CANCELLED，active Trade/Ledger=0；相同数据库 limit=2 阳性对照可恢复 active。该独立 probe 与失败日志完整保留，本轮未重跑该 starvation probe，也未把它改成期望功能测试。

本轮生产执行逻辑和 repository 排序均不变，因此没有修复该可达 finding。用户明确将 terminal candidate fairness/starvation 排除出实现范围，本轮仅记录；不输出 canonical P1=0、Review-01 P1 closed 或 cluster review PASS。P1-3 coverage 回补候选与该独立 fairness finding 分别陈述，不能把选中订单的成功回补解释为所有排队订单最终收敛保证。

## 定向回归与直接事实

[OkxRestReconcileServiceTest.java](../../../backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileServiceTest.java) 新增 `limitOneUsesExactlyOneSharedCandidateScan`：精确匹配七个状态与 limit=1，verify 唯一调用并 verifyNoMoreInteractions；验证一次 CANCELLED fill query、durable Trade bound=1、无 Order lifecycle/Trade/Ledger mutation。原 null/empty/blank identity、错误 identity、重复 exchangeTradeId、两类 overflow 和幂等测试保持。

[L4PlanBlockerPostgresIntegrationTest.java](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L4PlanBlockerPostgresIntegrationTest.java) 新增四个参数化执行例：

| 场景 | PG16 直接断言 |
|---|---|
| CANCELLED 较早，ACCEPTED 较晚，两者均有 fill，limit=1 | 只查询较早 CANCELLED 的 fill；Trade=1/Ledger=2；ACCEPTED 无 Trade/Ledger，CANCELLED 状态/version不变 |
| ACCEPTED 较早，CANCELLED 较晚，两者均有 fill，limit=1 | 只查询较早 ACCEPTED 的 fill；转为 FILLED/Trade=1/Ledger=2；CANCELLED 无 Trade/Ledger且状态/version不变 |
| CANCELLED，fee=0，limit=1 | 首轮 newTrades=1、Trade=1、Ledger=2，主分录 -10/+10，费用分录=0；第二轮 newTrades=0；隐藏 venue report 后 durable replay 仍不增加事实 |
| CANCELLED，fee=0.02 USDT，limit=1 | 首轮 Trade=1/Ledger=4；主分录 -10/+10，费用分录 -0.02/+0.02；重复及 durable replay 后完整 ledger 行集合不变，TradeExecuted=1/LedgerPosted=1 |

两订单预算例仅在专用 PG fixture 内固定 created_at 为 2020-01-01 / 2020-01-02，日志记录 candidate IDs、状态、时间顺序、fill query 与 Trade/Ledger 计数；不使用 sleep。kill=ENGAGED，recovery 前后的 PLACE/CANCEL 计数不变。fee 参数例在 synthetic adapter report 边界注入费用；Order command、Spring/JDBC、Trade/Ledger/event/audit 均使用真实实现，不声称验证真实交易所费用传输。

原 P1-3 permanent correctness regression、C2 首轮/第二轮 terminal 完整事实、partial fill、existing Trade/missing Ledger、no-fill、kill，以及 C1 stale PLACE/stale CANCEL/ABA/pre-cancel 均重新执行并通过。C1 四个方法和 kill 回归方法体与 Review-01 相等；C1 production 四个 Java 文件及 V47 与 accepted implementation Git 内容相等（只归一化工作区 CRLF）。TradingChain 文件与 Review-01 SHA-256 相等，本轮 teardown delta=0。

## 本轮验证

本地 PostgreSQL 16.15、专用 `nq_l4_blocker`、真实 Spring/JDBC/Flyway V47、synthetic venue/no-outbound。临时 PG 已停止，数据目录已校验路径后删除；详情 `artifacts/20260907-c2-attempt02/postgres-instance.json`。依赖、PG runtime 与 scanner 均使用本地缓存，Maven 使用 offline 模式。

| 验证 | tests | failures | errors | conditional skips |
|---|---:|---:|---:|---:|
| OkxRestReconcileServiceTest | 19 | 0 | 0 | 0 |
| C1+C2 PG16 | 13 | 0 | 0 | 0 |
| affected scheduler/recovery/metrics | 21 | 0 | 0 | 0 |
| Trade/Ledger + PG causal + API + architecture | 51 | 0 | 0 | 0 |
| full backend Maven（本轮仅一次） | 1799 | 0 | 0 | 50 |

以上 Maven exit=0；full Maven 耗时 1:29。50 是 JUnit 条件跳过，不是 mandatory C1/C2 跳过或 CI job skip。完整日志、模块总计及 protected scope 复核见本地 `artifacts/20260907-c2-attempt02/validation-summary.json`；本轮全量结果替代先前 1794 次的旧候选测试结果作为当前本地验证。

可复用命令（PG datasource 只绑定新的专用 loopback fixture，不复用生产参数）：

```powershell
mvn -o -f backend/pom.xml -pl nq-scheduler -am '-Dtest=OkxRestReconcileServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
# 使用 artifacts/20260907-c2-attempt02/pg-fixture.py start 创建新的临时 PG 后绑定 SPRING_DATASOURCE_*。
mvn -o -f backend/pom.xml -pl nq-app -am '-Dtest=L4PlanBlockerPostgresIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l4.blockers.enabled=true' '-Dlogging.level.org.springframework.boot.autoconfigure.security=ERROR' test
```

治理检查：stage checker errors=0；stage guard Windows 49/0 failure/2 symlink 条件跳过，Linux 49/0 failure/0 skip；Java standard PASS，两个 architecture 类 PASS，Shadow NEW_CODE_VIOLATION_COUNT=0（existing baseline=143、ruleset expansion=14，未改 baseline）；authority PS5.1/PS7 PASS，next-action/lifecycle/agent workflow PASS。PS5.1 第一次 exit=1 是默认 execution policy 阻止脚本加载，保留失败日志；仅进程级 `-ExecutionPolicy Bypass` 重试 exit=0，系统策略未修改。

F009：18 contracts、1559 actual=approved edges、new=0、stale=0；guard、allowlist、caller registry 均无修改。最终 docs links、Gitleaks、diff 与 rollback 预检结果记录在同目录的 `completion.json`，未执行项不得从计划推断为 PASS。

工程约束：primary=`java-backend-maintenance`，supporting=`nq-java-engineering-standard`，trigger=`TRADING_CORE`，读取 platform-profile 和 NQ domain overlay。PASS：总预算、稳定身份、terminal 状态/version、canonical owner/幂等、审计、kill/no-outbound；既有事务边界未变。NOT_APPLICABLE：新事务/锁/线程池、schema/依赖/架构升级与真实 provider。无新增标准豁免；fairness finding 保持 OPEN。

## 范围、身份和回滚

相对 Review-01 新增 delta 仅为 service Javadoc、上述两处测试与本文；原 C2 evidence 和 TradingChain 字节保持。整个候选相对 HEAD 仍只涉及 C2 service、两个测试、原有 TradingChain teardown 与两份 C2 evidence；无 API/frontend/schema/state machine/C1/adapter/ledger implementation/repository ordering 改动。

新的确定性 candidate manifest 位于 `artifacts/20260907-c2-attempt02/candidate-manifest.json`，沿用 Review-01 的 path/kind/size/SHA-256 排序与分隔符算法，`REVIEW02_CANDIDATE_FINGERPRINT` 见 `completion.json`。manifest 覆盖全部四个 tracked candidate files 与两份 evidence；不把 ignored 日志或 manifest 自身递归纳入身份。

`artifacts/20260907-c2-attempt02/rollback.patch` 仅反向撤销 Attempt-02 增量、恢复 Review-01 五文件候选并删除本文；保护早已存在的用户改动。`git apply --check artifacts/20260907-c2-attempt02/rollback.patch` 只预检，不执行回滚。精确原始字节备份保留在同目录 `before/`。旧的全候选 rollback.patch 未覆盖。

staged=0、commit=NONE、push=NONE。独立 correctness review、exact-head CI、delivery、B0、L4 qualification 和生产验证均未执行。下一步仅为新候选的独立 correctness review，review 必须保留本轮授权边界和 OPEN starvation finding。

建议经后续授权后提交说明：`fix(reconcile): 在共享扫描上限内回补撤单终态成交`。

工具声明：PowerShell 5.1/7、Git、rg、Python、Maven/Java、WSL、临时 PostgreSQL、Gitleaks；functions 用于本地命令和补丁。未使用 connector MCP、子代理或网络。写操作限本文、三处候选文件、ignored artifacts 与专用临时测试数据库；无生产/真实交易/外部发送/凭证读取/commit/push。
