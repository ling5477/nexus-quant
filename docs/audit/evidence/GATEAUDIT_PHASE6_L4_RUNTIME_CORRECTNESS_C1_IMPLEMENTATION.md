# Phase6 L4 C1 implementation preflight evidence

日期：2026-09-07。任务：`NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C1-IMPLEMENTATION`。

结论：`BLOCKED / C1_EXPECTED_STATE_CAS_INSUFFICIENT`。

本文件记录实施前的 ABA 阻断分析；没有生产实现候选，不是 C1 acceptance 或独立 review。依据用户任务书第 6 节，在确认当前写路径存在会使 expected-state CAS 不安全的可达 ABA 后停止实现。未自动增加 version、generation、transition token 或 migration。

## 1. Repository 与 authority

- Repository：`E:\Project\nexus-quant-gateaudit`。
- branch：`audit/post-gatey-agent-baseline`。
- `git fetch origin --prune`：exit 0。
- HEAD 与 fetched origin：`5ea72a0f44bdeb686f50d0e22a3a7f4f53f4de32`。
- 初始 worktree：CLEAN；staged=0；`git diff --check`：exit 0。
- [STATUS](../../current/STATUS.md)：C1 `NOT_STARTED / NONE / NOT_RUN`；next action 仍是 C1 IMPLEMENTATION。
- accepted plan pair：`d79408228ce31c97802afbb674eb2e3d0a2e7bfd / 34071672665`。
- P1-2 OPEN / C1；P1-3 OPEN / C2；canonical P1=2。没有正式 authority transition。
- 分类：Java implementation / HIGH_RISK；实际交付停在 preflight blocker evidence。
- Skill：primary `java-backend-maintenance`；router `nq-dh-workflow-router`；supporting `nq-java-engineering-standard`，触发 `TRANSACTION_CONCURRENCY / TRADING_CORE`。读取 platform profile 与 NQ domain overlay。

## 2. 原写语义与 proposed CAS

当前 [OrderCommandWriteService](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java) 580–590 行：先对旧 `OrderRecord.status` 做状态机校验，再调用 `void OrderRepository.updateStatus(...)`。

当前 [JdbcOrderRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepository.java) 的 SQL：

```sql
UPDATE orders SET status = ?, reason = ?, updated_at = ? WHERE order_id = ?
```

因此状态机检查与 durable freshness 不原子，旧快照仍可覆盖新提交。原 P1-2 PLACE/CANCEL characterization 保持原样；本轮没有将其错误行为标为 correctness PASS。

任务书建议的候选原语（未实施）：

```sql
UPDATE orders SET status = :next, reason = :reason, updated_at = :now
WHERE order_id = :orderId AND status = :expected
```

它能够区分 `SENT/FILLED` 和 `CANCEL_REQUESTED/FILLED`，但不能区分同一订单不同撤单轮次的 `CANCEL_REQUESTED`。affected=1 仅证明状态值相同，不能证明回执仍属于当前轮次；affected=0 时 reload 也无法修正已经误获 affected=1 的旧回执。

## 3. 当前 canonical 路径上的 ABA

以下是源码与既有单元测试支持的确定性交错推导，**不是本轮已执行的 PG16 并发复现**。

| 顺序 | 线程与调用 | durable state / 持有快照 |
| --- | --- | --- |
| 1 | T1 普通 `cancelOrder` 完成本地 prepare，进入 gateway；第一轮明确拒绝回执在 finalize 前迟到 | DB=`CANCEL_REQUESTED`，T1 保存第一轮同名状态快照 |
| 2 | T2 `OkxRestReconcileService.reconcileOnce` 查询到订单仍为 ACCEPTED | 经 `rejectCancel` 提交 `CANCEL_REJECTED`，再经 `applyExternalStatus` 提交 `ACCEPTED` |
| 3 | T3 新的普通 `cancelOrder` 完成本地 prepare，进入第二轮 gateway，回执暂未返回 | DB 再次为 `CANCEL_REQUESTED`，属于第二轮 |
| 4 | T1 第一轮迟到的 `CancelReject` 进入 `finalizeRejectedCancelOrder` | 旧快照语义检查 `CANCEL_REQUESTED → CANCEL_REJECTED` 合法；status-only CAS 条件仍为真，会覆盖第二轮的在途状态 |
| 5 | T3 第二轮实际接受撤单，返回新 ACK | 若直接按 proposed conflict 语义让当前 durable state 胜出，第二轮 ACK 遇到 `CANCEL_REJECTED`；不能由状态值辨认 DB 是被第一轮旧拒绝污染的 |

ABA 为 `CANCEL_REQUESTED → CANCEL_REJECTED → ACCEPTED → CANCEL_REQUESTED`。第 4 步会把上一轮拒绝物化成当前轮次的本地迁移与 `ORDER_CANCEL_REJECTED` 事实；即使两个 FILLED mandatory case 被 status-only CAS 保护，共享入口仍存在实际 stale-snapshot freshness 缺口。

这不是依赖 retired compatibility 路径，也不要求实施 C2 的 CANCELLED scan 扩展。T2 使用的是已经存在的 ACCEPTED 对齐分支。第一轮明确拒绝与订单仍 ACCEPTED 相容，不需要假设一个虚构的交易所响应。

### 3.1 源码位置

- [InMemoryOrderStateMachine](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/state/InMemoryOrderStateMachine.java) 34–36、53–62 行：ACCEPTED 可进入 CANCEL_REQUESTED；CANCEL_REQUESTED 可到 CANCEL_REJECTED；CANCEL_REJECTED 可到 ACCEPTED 或再次 CANCEL_REQUESTED。
- [OrderCommandService](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java) 148–190 行：resolve、prepare、gateway、finalize 分离；只对 CANCELLED 作幂等短路，没有覆盖整个外调期间的每订单锁或 attempt generation 校验。
- [OrderCommandWriteService](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java) 402–425、497–543、551–556、580–610 行：prepare 与 finalize 分别是本地事务；拒绝 finalize 使用传入的旧快照，写迁移及拒绝事实；共享入口无轮次身份。
- [OkxRestReconcileService](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java) 143–151、259–292 行：扫描 CANCEL_REQUESTED，遇到 ACCEPTED 先 rejectCancel 再 applyExternalStatus。
- [OrderLifecycleService](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderLifecycleService.java)：rejectCancel / acknowledge / applyExternalStatus 最终复用 ordinary command write owner。
- [OrderRecord](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/OrderRecord.java)：当前快照没有 durable version/generation/transition token。

### 3.2 已有可执行证据的边界

- `OrderCommandServiceTest.shouldAllowRetryCancelAfterCancelRejected`：真实普通 command/write service 加内存端口替身，证明拒绝后可再次 dispatch，断言 cancel invocation=2。
- `OkxRestReconcileServiceTest.shouldRecoverFromCancelRequestedToAcceptedViaCancelRejected`：真实 reconcile service 加 mock lifecycle，证明既有对齐分支调用 rejectCancel 与 ACCEPTED 对齐。
- `InMemoryOrderStateMachineTest`：现有状态机回归。
- 以上证明相关分支行为，结合源码得到交错反例；没有声称既有测试已经覆盖完整 ABA、数据库 CAS row count、实际多线程事务或 PG16。

## 4. 阻断后的设计边界

- 需要能区别同一状态不同轮次的 durable freshness identity，例如 version、generation 或 transition token，或者经证明等价的数据库原子串行化机制。不能仅在 Java re-read 后无条件写。
- 尚未证明必须使用 `orders.version`，也没有选择 schema 方案。仅确认本任务默认的 expected-status-only 条件不足；schema 是否必要需要单独设计与授权。
- `PLAN_DRIFT`：默认 expected-state CAS 足够这一待验证假设在共享撤单路径上不成立。accepted plan evidence、场景清单、13/14 applicability、17 crash points、12 MUST_PROVE、C2/B0/L5/L6 均未改写。
- `OrderCommandWriteService` ordinary write ownership 保持；新增 bypass writer=0。
- CAS API、affected-row 1/0/>1 contract、conflict reload/duplicate/missing/nonterminal contract：均未实施。
- stale external ACK、false local transition event、conflict audit 的新行为：均未实施或验证；不宣称既有缺陷已消失。
- externalOrderId：确认 accepted PLACE 在状态迁移前调用无条件 identity update；未修复或扩大 identity subsystem，未声称 monotonic enrichment 已达成。
- prepareCancel stale snapshot/outbound suppression：未修复，未验证；同一旧快照问题仍需后续方案覆盖。
- P1-2 PLACE/CANCEL：remediation 未实施，CAS affected/final Order/Trade/Ledger/false facts 的 post-fix 证据均 NOT_RUN。
- P1-3：测试断言和 C2 生产代码均未改，本轮未重跑 PG characterization，保持 OPEN，不声称本轮重新复现。

## 5. Validation

实际执行（离线，无真实 provider / 数据库连接）：

```powershell
mvn -o -f backend/pom.xml -pl nq-scheduler -am '-Dtest=InMemoryOrderStateMachineTest,OrderCommandServiceTest,OkxRestReconcileServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

exit 0 / BUILD SUCCESS；19 个 reactor project 构建完成，只有下表 3 个类实际执行测试。不是 full Maven test。

| 类 | tests | failures | errors | skips |
| --- | ---: | ---: | ---: | ---: |
| InMemoryOrderStateMachineTest / core | 3 | 0 | 0 | 0 |
| OrderCommandServiceTest / core | 8 | 0 | 0 | 0 |
| OkxRestReconcileServiceTest / scheduler | 4 | 0 | 0 | 0 |
| Total | 15 | 0 | 0 | 0 |

Surefire XML 位于各 affected module 的 `target/surefire-reports/`。这是 unchanged-baseline 分支回归，不是修复成功证明。编译器 unchecked、Mockito self-attach、SLF4J 无 provider 警告不影响 exit 0，未因此做无关修改。

- normal PLACE accepted、CANCEL accepted/rejected/retry：已有 OrderCommandServiceTest 覆盖并通过；完整任务要求的 normal PLACE rejection、独立 write-service/infra matrix、OrderLifecycle 及 PG16 C1 correctness suite 未执行。
- PG16 concurrency proof、actual JDBC CAS affected rows、Trade/Ledger/event/audit post-fix proof：NOT_RUN，因实施前 ABA blocker 未进入实现及验收阶段。
- Full Maven：NOT_RUN；Java architecture guard / Shadow：NOT_APPLICABLE，本轮 production Java diff=0，未改规则或基线。
- PS5.1 authority 首次执行 exit 1：本机脚本执行策略拒绝 `-File`。使用仅当前子进程的 `-ExecutionPolicy Bypass` 重跑；没有修改持久系统策略。
- PS5.1 / PS7 authority：exit 0，errors=0。
- next-action：exit 0，failed=0。
- lifecycle：exit 0，passed=20 / failed=0。
- agent workflow：exit 0，fixtures=12/12，全部 negative mutation 被拒绝。
- stage checker：新增本文件前后均 exit 0，scanned=1799 / reviewed_exceptions=178 / errors=0；不将此计数解释为新文件已被扫描。
- stage guard tests：exit 0，49 tests / 0 failures / 0 errors / 2 skips；这两个既有条件跳过不计入上表 15 个 Maven tests，也不伪装为全部执行。
- docs links：exit 0，checked=413 / warnings=123 / errors=0；warnings 位于未修改的 TESTING/WORKLOG 历史 ledger。
- 新 evidence 单文件 links：exit 0，checked=10 / warnings=0 / errors=0；单文件 whitespace 检查 PASS；反向补丁 `git apply --check` exit 0。`git diff --no-index --stat -- NUL <evidence>` 显示新增文件，exit 1 是存在 diff 的预期结果。
- 最终 tracked `git diff --check`、范围 diff、`git diff --exit-code -- backend scripts .github .agents docs/current` 及 accepted plan 两文件检查：exit 0。新 evidence 是 untracked，另以文件级检查验证，不将空 tracked diff 当成它的内容校验。
- secret scan：NOT_RUN，`gitleaks` 不在 PATH；未下载工具或改 allowlist。未读取真实凭证；本文件只含代码、测试与本地 Git 事实，仍不把人工内容检查冒充 secret scan PASS。
- 搜索命令曾因 PowerShell 下将 wildcard 直接作为 rg path 而 exit 1；改为目录加 `-g` 后完成检索，无内容变更。

## 6. Scope、回滚与下一动作

- production files changed=0；test source changed=0；C2/Flyway/schema/workflow/compatibility changed=0。
- accepted plan 与 current authority changed=0；LIVE/provider/生产数据库/真实 PLACE/CANCEL/transfer/withdraw=0。
- 唯一 source-controlled 候选新增文件：本 evidence；生成的 target 测试结果不作为 production diff。
- Git：staged=0；git add/commit/push=NONE。
- Findings：本轮 scoped 分析未发现 P0；P1-2 仍 OPEN，canonical P1 仍为 2；ABA 是 C1 方案阻断，不新增一个正式 canonical finding 编号。
- 高风险约束判定：模块/write ownership 与 SIM/LIVE 边界 PASS（无变更）；concurrency freshness VIOLATION（旧无条件写及 status-only 候选的 ABA 缺口）；事务内外调分离 PASS（源码范围）；新 CAS 事务一致性与 post-fix audit proof NOT_APPLICABLE（未实施）；不存在通过注释或单元测试将其提升为 acceptance 的例外。
- 回滚：只有新增 evidence 需要删除型反向补丁；原 repository API、SQL、write owner 和测试均保持 HEAD，无需恢复生产文件。补丁位于 `artifacts/20260907-c1-blocked-rollback-v1.patch`，仅提供并执行 `git apply --check` 验证，不执行回滚。
- 建议 commit message（仅建议，未提交）：`docs(audit): 记录C1状态CAS的ABA阻断证据`。
- 实际下一动作：先处理 C1 freshness identity 方案阻断及其授权范围；不得进入 C2、B0 或 qualification。当前 matcher 仍解析 C1 IMPLEMENTATION；本轮不擅自将 machine status 改为 BLOCKED。
- [governance contract](../../../scripts/docs/governance-workflow-contract.json) 的 highRisk lifecycle 在未来 IMPLEMENTED 后要求 PENDING_REVIEW；当前没有实现候选，不能进入 review acceptance 或 commit。C1→C2→Independent Correctness Review→B0 的 accepted DAG 保持原样。

工具声明：外部工具使用 Git、Maven/Java、PowerShell 5.1/7、Python 与 rg；通过 exec/apply_patch 执行本地命令及 evidence 编辑，未使用外部应用 MCP。Skills 如第 1 节；网络仅 Git origin fetch；写操作限 evidence 与本地测试/checker 生成物；未使用子代理。

---

## Attempt-02 — Versioned OCC

日期：2026-09-07。任务：`NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C1-VERSIONED-OCC-IMPLEMENTATION-ATTEMPT-02`。

本节为用户明确授权 durable version 与 V47 后的独立追加记录。此前 Attempt-01 的 `BLOCKED / C1_EXPECTED_STATE_CAS_INSUFFICIENT` 保持原文，不改写为当时已经完成。Attempt-01 原始文件 SHA256=`d99d4d5121fac4f99a2298cc15ce75a1876ea60d5c040b5fc292ff4de1e6ae96`，备份为 `artifacts/20260907-c1-attempt01-baseline.md`。

### A2.1 基线与候选结论

- branch=`audit/post-gatey-agent-baseline`；HEAD / origin=`5ea72a0f44bdeb686f50d0e22a3a7f4f53f4de32`；初始仅 Attempt-01 evidence 为 untracked，属于本轮明确允许保留的既有内容。
- work batch、accepted plan pair、next-action 和全部 machine safety authority 保持原样；C1 实现结果仅是候选，不作 authority acceptance。
- P1-2 candidate=`REMEDIATED`；ABA、stale PLACE、stale CANCEL、pre-cancel stale preparation 的 PG16 回归全部通过；P1-3=`OPEN`，candidate canonical P1 remaining=1。
- Full backend Maven 最终 `BUILD SUCCESS`，23/23 reactor projects；1777 test executions / failures=0 / errors=0 / skips=50。
- 本节收尾状态与 secret scan、清理限制见 A2.8；不把任何 NOT_RUN 写成 PASS。

### A2.2 版本与 repository 合同

Status-only CAS 无法区分 ABA 前后同名状态的撤单轮次；本轮通过 `orders.version BIGINT NOT NULL DEFAULT 0` 持久化状态代际，并在一个 SQL mutation 内绑定订单 ID、expected status 和 expected version：

```sql
UPDATE orders
SET status = ?, reason = ?, version = version + 1, updated_at = ?
WHERE order_id = ? AND status = ? AND version = ?
```

- `OrderRepository.compareAndSetStatus(orderId, expectedStatus, expectedVersion, status, reason, now)` 返回实际 affected rows；原 canonical `void updateStatus(...)` 已移除，没有生产兼容 fallback。
- `affected=1`：取得该代际迁移所有权，返回 version+1，写一次成功迁移审计，再由相应 finalizer 发布当前代际事件。
- `affected=0`：重新读取 durable Order。version 已前进则保留当前 truth，记录 `ORDER_STATUS_TRANSITION_STALE`，不重试；目标已被其他请求实现时同样只观察，不重复成功事实。
- missing row、version 未前进或倒退、负数或大于 1 的 row count、version exhausted：fail closed。Long.MAX_VALUE 在 mutation 前拒绝；数据库 CHECK 禁止负版本。
- `OrderRecord` 完整构造器承载实际 `long version`；JDBC SELECT/RowMapper 读取真实列；旧构造器只为新建/测试对象默认 0。insert 拒绝非零 version，数据库 default 将新行初始化为 0。
- `withStatus` 为成功迁移构造下一代快照，使用 `Math.incrementExact`；`withExternalOrderId` 原样保留 version；CAS 失败不构造 version+1 快照。
- 唯一生产 `OrderRepository` 实现是 `JdbcOrderRepository`；手写实现替身仅 `OrderCommandServiceTest.InMemoryOrderRepository`，已同步条件写及 identity row-count。其余 OrderRepository mocks 按新增调用合同配置。
- `OrderStateMachine` 图没有变化；`OrderCommandWriteService` 仍是 ordinary lifecycle write owner。Controller、command orchestration、scheduler 和 adapter 未新增 SQL 或旁路 writer。

### A2.3 事件、准备阶段及 identity

- 四个 provider finalizer（PLACE accepted/rejected、CANCEL accepted/rejected）分别检查显式 `TransitionResult.applied`。失败 CAS 不发布 OrderAck / OrderReject / CancelAck / CancelReject，不发布 ORDER_ACKED / ORDER_CANCELLED / ORDER_CANCEL_REJECTED 等当前迁移事实。
- 旧 provider 事实以现有 audit repository 保存为 `STALE_PROVIDER_RESULT_IGNORED`，包含 expected/durable version、expected/durable status、provider_result 类别、request_id、trace context；未建立新 event subsystem。
- deferred PLACE/CANCEL 也读取 durable truth：发现代际变化时只记录旧回执审计并返回当前状态。
- preparePlace 的 NEW→RISK_PASSED→SENT / RISK_REJECTED，以及 prepareCancel 均使用同一条件迁移。未取得迁移所有权会抛出 `IllegalStateException`，由既有 command 调用栈终止，gateway 不执行。准备失败事务回滚，其成功事件和事务内审计均不提交；外部动作尚未发生。
- identity 使用单条条件写 `WHERE order_id=? AND (external_order_id IS NULL OR BTRIM(external_order_id)='')`；不递增 lifecycle version。更新后读取并验证：NULL→A 允许；A→A 幂等；A→B 或 missing row 拒绝。所有 canonical identity 调用均经过该 repository 原语。
- stale PLACE ACK 可以安全填充此前空 identity，但仍使用原 SENT/version 执行状态 CAS，绝不拿 identity reload 的版本刷新旧 ACK；较新 FILLED 保留。已有非空 identity 不被不同 ACK 覆盖。

### A2.4 PostgreSQL 确定性证明

使用本机缓存 digest-pinned PostgreSQL 16 镜像，容器=`nq-c1-occ-20260907`，digest=`sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`，tmpfs data，端口仅 `127.0.0.1:38308`，数据库=`nq_l4_blocker`，trust authentication。fake venue / no-outbound initializer 隔离真实 provider；kill 操作只在该临时 fixture 内执行。

每个 CAS 由真实 JdbcTemplate 执行，观测实际 row count、expected status/version、目标状态及 `transactionActive=true`。Thread.sleep=0；通过 CountDownLatch 和有界 Future 等待控制先后顺序。T2 返回并从另一次数据库读取看到新状态后才释放旧回执。

| 证明 | 顺序与真实结果 | 事实一致性 |
| --- | --- | --- |
| stale PLACE | T1 SENT/v2 → T2 FILLED/v3 commit → T1 ACCEPTED CAS=0 | 返回/DB=FILLED/v3；Trade=1、Ledger=2；OrderAck/ORDER_ACKED/虚假 ACCEPTED 迁移=0；旧回执审计=1 |
| stale CANCEL | T1 CANCEL_REQUESTED/v4 → T2 CANCEL_REJECTED/v5 → FILLED/v6 commit → T1 CANCELLED CAS=0 | 返回/DB=FILLED/v6；Trade=1、Ledger=2；CancelAck/ORDER_CANCELLED/虚假 CANCELLED 迁移=0；旧回执审计=1 |
| full ABA | T1 CANCEL_REQUESTED/v4；T2 reconcile→CANCEL_REJECTED/v5→ACCEPTED/v6；T3 新 cancel→CANCEL_REQUESTED/v7；T1 旧 CancelReject CAS=0 | 新 CANCEL_REQUESTED/v7 保持；旧 CancelReject event/当前拒绝审计/新增成功迁移=0；T3 新 ACK CAS=1→CANCELLED/v8；Trade/Ledger=0/0 |
| pre-cancel | resolve ACCEPTED/v3 暂停 → T2 FILLED/v4 commit → prepare CAS=0 | FILLED/v4 保持，gateway CANCEL calls=0；Trade=1、Ledger=2 |

CANCEL 的 `v4→v6` 是现有状态图的两次合法迁移，不是任务书示意的单步 `N+1`；没有改图、漏增或额外增版本。每条成功 SQL 都只递增一次。

对应 [L4PlanBlockerPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L4PlanBlockerPostgresIntegrationTest.java) 的四个 permanent regression：

- `stalePlaceAckPreservesCommittedFilledThroughRealSpringTransactions`
- `staleCancelAckPreservesCommittedFilledThroughRealSpringTransactions`
- `staleCancelRejectCannotClaimNewCancelGenerationAfterAba`
- `stalePreCancelSnapshotNeverDispatchesGateway`

原 R02/R03 的 known-defect 标签及接受错误结果的断言已反转；R04/P1-3 方法和注释区域与 HEAD canonical text 完全一致，SHA256=`99ef200b66a270b50f80d982e67343d5863bd609ef316f70706d14c1191c93e4`。完整 Maven 再次输出 P1-3：CANCELLED scans=2 / fillQueries=0 / Trade=0 / Ledger=0；ACCEPTED control→FILLED/Trade1/Ledger2。它仍是缺陷复现 PASS，不是 correctness PASS。

### A2.5 Migration 与风险边界

唯一新 migration 为 [V47__order_state_optimistic_concurrency.sql](../../../backend/nq-infra/src/main/resources/db/migration/V47__order_state_optimistic_concurrency.sql)：新增 BIGINT NOT NULL DEFAULT 0、CHECK version>=0、中文 COMMENT；V1–V46 完全不改。

[OrderVersionFlywayPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/OrderVersionFlywayPostgresIntegrationTest.java) 在随机独立 schema 中证明：V46 上插入既有行后升级仅执行 V47，旧行 version=0；空 schema 执行 47 个 migration；两者 validate PASS/current=47/pending=0；非负与非空约束、列 COMMENT、真实 JDBC 六行并发条件矩阵和 identity 0/1 行数均通过。测试最后只清理自己的随机 schema。

- PG16 常量默认值支持 fast default，但 CHECK 验证仍可能扫描既有 orders；ALTER TABLE 需要锁。脚本设置 `lock_timeout=5s`、`statement_timeout=60s`，获取锁或扫描超时则失败回滚。
- 已验证的是临时小数据 schema；生产表规模、锁等待和生产迁移耗时未验证，本轮没有生产执行。
- 不允许旧 unconditional writer 与新 versioned writer 混跑；后续部署必须在独立授权下安排写入静默窗口、migration 与版本一致切换。本地 schema 兼容旧列读取不等于允许旧二进制继续写状态。
- 无新增索引：order_id 现有主键已将条件 mutation 限定到一行；不增加 version 索引或全状态图 SQL。
- migration review 是本轮自检，不冒充独立审查：types/default/check/comment/history PASS；小数据 PG replay PASS；生产规模/部署验证 NOT_RUN。

### A2.6 验证命令、计数与失败记录

依次执行 repository → write/command → lifecycle/reconciliation → PG PLACE → PG CANCEL → PG ABA → PG pre-cancel → Flyway → full Maven。

| 范围 | tests | failures | errors | skips | 结果 |
| --- | ---: | ---: | ---: | ---: | --- |
| JdbcOrderRepositoryTest | 6 | 0 | 0 | 0 | PASS |
| 初轮 write/command/state-machine targeted | 34 | 0 | 0 | 0 | PASS；write test 后增 4 个正常 provider case，最终 full 中为 27 |
| affected lifecycle/reconciliation targeted | 25 | 0 | 0 | 0 | PASS |
| 4 个 PG16 C1 单独回归 | 4 | 0 | 0 | 0 | 每次 1 个，均 PASS |
| PG16 migration upgrade/fresh | 2 | 0 | 0 | 0 | PASS，内部各执行六行 CAS matrix |
| 跨 JVM restart targeted | 2 | 0 | 0 | 0 | PASS |
| 最终 full backend | 1777 | 0 | 0 | 50 | BUILD SUCCESS / 23 reactor projects |

这些是有重叠的运行次数，不能相加宣称唯一测试数。Full 包含 C1 PG 4、P1-3 known defect 1、原 R05 normal control 1、迁移 2、write service 27、command 8、repository 6、跨 JVM restart 2。

Full 计数由 18 个 module summary 与 354 次 suite execution 日志分别求和一致得到。352 份本轮新 XML 只合计 1772：两个 architecture 类分别被 JUnit Jupiter（1+4）及 ArchUnit（9+10）引擎执行，后一次覆盖同名 XML；因此不只按 XML 文件总数漏掉前 5 次执行。50 skips 为既有条件测试跳过（core13、adapter-binance1、infra4、app32），C1 mandatory PG tests skips=0。

日志/计数：`artifacts/20260907-c1-*.log`、`artifacts/20260907-c1-validation-counts.json`、`artifacts/20260907-c1-full-counts.json`、`artifacts/20260907-c1-scope-proof.json`；完整成功日志为 `artifacts/20260907-c1-full-maven-verified.log`。

可复执行命令（仅临时 trust fixture，`x` 为非凭证 sentinel；环境变量只设置于本次 PowerShell 进程）：

```powershell
mvn -o -f backend/pom.xml -pl nq-infra -am '-Dtest=JdbcOrderRepositoryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -o -f backend/pom.xml -pl nq-infra -am '-Dtest=OrderCommandWriteServiceTest,OrderCommandServiceTest,InMemoryOrderStateMachineTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -o -f backend/pom.xml -pl nq-scheduler -am '-Dtest=OkxRestReconcileServiceTest,BinanceRestReconcileServiceTest,OkxWsOrderAccelerationServiceTest,BinanceWsOrderAccelerationServiceTest,OkxRecoveryServiceTest,PaperMatchingServiceTest,InMemoryOrderStateMachineTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:38308/nq_l4_blocker'
$env:SPRING_DATASOURCE_USERNAME='postgres'
$env:SPRING_DATASOURCE_PASSWORD='x'
mvn -o -f backend/pom.xml '-Dnq.l4.blockers.enabled=true' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:38308/nq_l4_blocker' '-Dspring.datasource.username=postgres' '-Dspring.datasource.password=x' '-Dlogging.level.org.springframework.boot.autoconfigure.security=ERROR' test
```

四个 PG 单独运行使用相同 datasource 参数，并加 `-pl nq-app -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=L4PlanBlockerPostgresIntegrationTest#<上述方法名>`；migration 使用 `-Dtest=OrderVersionFlywayPostgresIntegrationTest`。临时容器清理后重跑须重新创建 fixture 并替换实际 loopback 端口，不能套用生产地址。

真实失败及最小处理：

1. 初次 compile exit1：core 没有 Mockito。新增 write-service test 移至已有 Mockito 的 infra 测试目录；依赖/POM 改动=0。repository 重跑 PASS。
2. PG testCompile exit1：`assertEquals` 遇到 TransactionTemplate 泛型数值重载歧义。显式 Integer 后解包；未修改业务。
3. 首次 PG PLACE execution：1 failure，row-count spy 使用展开后的 varargs 导致线程提前失败、barrier 未到。改为 `getRawArguments()[1]`，保留真实 JDBC 调用和 actual affected rows，最终四项 PG PASS。
4. Full #1：1777 / failures0 / errors3 / skips50，空 datasource system property 覆盖合成 prod 测试值，以及两个 forked JVM 测试缺少 SPRING_DATASOURCE 环境变量；只修正执行参数。
5. Full #2：1777 / failures2 / errors0 / skips50，restart fixture 最新 migration 断言仍为46。仅将 [TradingRestartRecoveryPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TradingRestartRecoveryPostgresIntegrationTest.java) 一处 current-version 断言改为47；targeted 2/2 PASS，再执行 Full #3 得到上述完整成功结果。不是删除测试或跳过失败。
6. 证据比对脚本曾用 Windows 默认 GBK 读 UTF-8 而失败；显式 UTF-8 后 P1-3/历史 migration 比对 PASS。

### A2.7 Scope 与 rollback

生产改动仅 4 个 Java 文件加 1 个 migration：

- [OrderRecord](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/OrderRecord.java)
- [OrderRepository](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/port/OrderRepository.java)
- [OrderCommandWriteService](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java)
- [JdbcOrderRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepository.java)
- V47（链接见 A2.5）。

测试改动为 OrderCommandServiceTest、JdbcOrderRepositoryTest、L4PlanBlockerPostgresIntegrationTest、TradingRestartRecoveryPostgresIntegrationTest（一行），新增 OrderCommandWriteServiceTest 与 OrderVersionFlywayPostgresIntegrationTest；文档仅本 evidence 追加。OrderCommandService production coordination 无需修改，prepare 异常自然阻止 outbound。

C2 生产代码、P1-3 assertion、V1–V46、workflow、stage exceptions、Gitleaks allowlist、state graph、accepted plan、current authority 改动均=0。B0/L4 qualification/L5/L6 未实施；LIVE、private provider、生产服务器、生产数据库和真实 PLACE/CANCEL/transfer/withdraw 副作用=0。

本轮独立反向补丁：`artifacts/20260907-c1-attempt02-rollback-v1.patch`。它恢复 HEAD 的 8 个 tracked Java 文件，删除本轮 3 个新增 Java/SQL 文件，并只撤回本文件的 Attempt-02 追加，保留 Attempt-01 原文。仅提供并做 `git apply --check`，不执行。

补丁仅用于当前未提交源码候选；若 V47 将来已经正式应用，不得用删源文件或 DROP COLUMN 冒充部署回滚，应按后续授权执行 forward-only 修正/恢复方案。临时测试 schema/container 的清理不等于生产 migration rollback。

建议 commit message（未执行）：`fix(trading): 使用版本化OCC阻止旧回执覆盖订单状态`。

### A2.8 收尾 admission 与 disposition

- authority PS5.1/PS7：errors=0，exit0；next-action failed=0；agent workflow fixtures=12/12、negative mutation 全拒绝；lifecycle passed=20/failed=0。
- stage asset checker：scanned=1801 / exceptions=178 / errors=0；stage guard tests=49 / failures0 / errors0 / skips2（本机 symlink 条件跳过）。无 exception/approved-caller 变更。
- Java standard guard=`GOVERNANCE_CHECKER_RESULT=PASS`；Shadow=`NEW_CODE_VIOLATION_COUNT=0`，existing=143、ruleset expansion=14；没有改 baseline。架构两类两引擎合计24次执行通过。
- docs links：checked=421 / warnings=123（既有历史 ledger）/ errors=0；exit0。
- secret scan：PASS。复用仓库缓存的 Gitleaks 8.18.4 官方归档，repository `Test-DeliveryToolArchive.ps1` 校验 SHA256=`ba6dbb656933921c775ee5a2d1c13a91046e7952e9d919f9bac4cec61d628e7d`，同时匹配缓存官方 checksum；只提取对应 binary，通过 Ubuntu WSL 执行 version=8.18.4。原样提取当前 CI TOML，不改 allowlist；扫描 tracked safe working tree 加精确新增候选文件共3143个，`--no-git --redact`，exit0/no leaks found。未下载安装全局 binary。报告与逐文件 hash manifest 位于 `artifacts/20260907-c1-secret-scan/`。
- `git diff --check`、源码范围核对、Attempt-01 prefix/原始 SHA256 校验：PASS；反向补丁仅执行 `git apply --check`，不回滚。
- 临时容器清理未验证：所有 Java/PG16 证明完成后，Windows Docker named-pipe API 的 inspect/top/version 调用超时；Ubuntu Docker 未启用 socket integration，docker-desktop distro 明确不支持直接 CLI。只停止本轮两个卡住的 Windows Docker CLI 子进程，没有重启/修改 Docker Desktop 或操作其他容器。不能声称 container residue=0；保留确切清理命令 `docker rm -f nq-c1-occ-20260907`，待 Docker API 恢复后执行。此限制不改写已经完成的 JDBC/Flyway/跨 JVM proof，也不冒充已清理。
- 高风险不变量：版本 freshness、失败路径、write ownership、事件代际、SIM/LIVE 隔离、forward-only、局部事务外 provider 调用 PASS；生产容量/部署、exact-head CI、独立 correctness review 未执行。
- disposition：`IMPLEMENTED / PHASE6_L4_C1_VERSIONED_OCC_ABA_REMEDIATED / P0_0 / P1_2_REMEDIATED / CANONICAL_P1_1_REMAINING / PENDING_DELIVERY_LIFECYCLE` 为本地候选技术结果；不表示 C1 ACCEPTED 或 READY_TO_COMMIT。残余环境事项仅为上条临时容器清理未验证。
- 下一动作：按 highRisk lifecycle 交付独立 correctness review，再进入后续授权的 delivery/authority lifecycle；不自行跳过 review、推进 C2 或改 matcher。staged=0；git add/commit/push=NONE。

工具声明：使用 exec/apply_patch 执行 Git、Maven/Java、PowerShell 5.1/7、Python、rg、Docker；没有外部应用 MCP 或子代理。primary Skill=`java-backend-maintenance`；supporting=`nq-dh-workflow-router`、`nq-java-engineering-standard`、`db-schema-migration-review`（V47 schema/锁/恢复自检）。本轮没有下载依赖/镜像/工具，没有连接真实 provider；数据库网络仅 loopback 临时 fixture。写入限上述候选文件和 artifacts/target、任务专用临时数据库。
