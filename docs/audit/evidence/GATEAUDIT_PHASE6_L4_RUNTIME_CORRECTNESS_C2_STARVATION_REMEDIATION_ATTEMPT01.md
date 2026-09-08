# Phase6 L4 C2 starvation remediation — Attempt 01

任务：`NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C2-STARVATION-REMEDIATION`，NQ-only。
执行跨 2026-09-07 / 2026-09-08；本文件是本地实现和验证证据，不是独立审查、authority acceptance、delivery 或 L4 qualification。

```text
IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW
```

## 基线与原 P1

- Task classification：HIGH_RISK / P1_TARGETED_REMEDIATION / DURABLE_FAIR_SCAN / POSTGRESQL_CONCURRENCY / ADDITIVE_FLYWAY。
- Starting HEAD：`ff5a26b7bd166b3591b142a20058a8c22e38d75e`。
- Branch/upstream：`audit/post-gatey-agent-baseline / origin/audit/post-gatey-agent-baseline`；本地 tracking ref 与 HEAD 相同，未联网 fetch。
- Review-01 后的六文件 raw SHA-256 全部相同，fingerprint=`725fc81da643564546ce2d529b5359891bdf682df2e68c2ec5b8e56a36645d93`，无未授权起始漂移、staged=0。
- Original P1：`P1_RECONCILIATION_CANDIDATE_STARVATION`。已接受的独立结果仍保留为 `FAIL / C2_NOT_ACCEPTED / CORRECTNESS_CLUSTER_OPEN`。
- Root cause：`ORDER BY created_at ASC LIMIT :limit` 每次选择相同旧前缀；FILLED/CANCELLED 的成功、无 fill、身份缺失或账本已收敛不会移除其 eligibility。原 venue 过滤在 Java 中，发生于数据库 LIMIT 之后。
- current authority 保持 C1 ACCEPTED/CI_GREEN、C2 NOT_STARTED；本轮用户显式授权本地 starvation remediation，不更改 STATUS/ROADMAP，也不把本地 PASS 写成 P1 正式关闭。

原 [C2 implementation](GATEAUDIT_PHASE6_L4_RUNTIME_CORRECTNESS_C2_IMPLEMENTATION.md)、[Attempt-02](GATEAUDIT_PHASE6_L4_RUNTIME_CORRECTNESS_C2_IMPLEMENTATION_ATTEMPT02.md) 字节不变。两次既有 review 的原件、40 轮失败复现及阳性对照均保持原路径与原内容。起始 manifest、原始字节快照及本轮日志位于本地 ignored `artifacts/20260907-c2-starvation-remediation/`，不能将本轮结论倒写进旧 evidence。

## 算法与架构

Selected remediation：在现有 OrderRepository 增加专用 reservation port 方法，OrderCommandService 只转发，JdbcOrderRepository 复用既有 OrderRecord mapping；scheduler 用 `reserveReconciliationCandidates("OKX", statuses, limit)` 取得订单快照，再执行既有 reconciliation。

定向审计 core/infra 的扫描、cursor、progress 及 migration：未发现真正通用、非 Gate-specific 的 durable scan-progress primitive。Shadow run cursor 是其他领域运行状态，GateY pilot leases 不是通用扫描进度，均不复用。不建立第二套 Order repository、lease framework、order claim state machine 或分布式调度框架；模块依赖未新增。

| 合同 | 实现与证据 |
|---|---|
| Candidate selection | SENT / ACCEPTED / PARTIALLY_FILLED / CANCEL_REQUESTED / CANCEL_REJECTED / FILLED / CANCELLED，共享一次查询预算 |
| Venue predicate | SQL `WHERE venue=:venue AND status IN (:statuses)`，先过滤再排序/LIMIT；Java 防御过滤仍保留 |
| Ordering key | `(created_at, order_id)`，使用 PostgreSQL 一致比较与排序；order_id 主键形成同时间的全序 |
| Cursor semantics | 每 venue 一条，记录上次成功非空 reservation 最后一条 candidate 的扫描键 |
| Wrap semantics | CASE 将 cursor 后键放前、其余键放后，各组按全序升序；到末尾后在同一条 SELECT 中回到开头 |
| Per-run max | 一条 LIMIT，总 selected <= limit；没有第二 lane 或额外 wrap 预算，同轮无重复订单 |
| Empty/invalid | 空结果不推进 revision；非正 limit、空 venue、空/无效状态集合 fail closed |
| Offset used | NO |
| Random/time-based ordering used | NO；没有 JVM cursor、调用计数器或 audit-log cursor |
| Order facts | 不用 status/version/updated_at 保存扫描进度；OrderRecord 无新增字段 |

Why durable / restart-safe：进度在 PostgreSQL，scanner/context 无任何跨调用内存游标；独立 Spring context 销毁再创建后从数据库继续。稳定有限集合 N、正预算 L 下，每轮选至多 L 条不同候选，最多 ceil(N/L) 次成功预留覆盖一圈；动态插入与 eligibility 变化在循环中重新可达。该界限针对稳定有限集合，不声称无限持续流入或持续进程失败时仍有固定 wall-clock SLA。

Why public limit unchanged：API/DTO/controller/maintenance/frontend 默认值与传递方式无变化；默认仍 100，所有状态合计最多 limit 个 Order。每订单既有 fill/Trade 上限也不变。

## Migration 与事务

- Migration：新增 [V48__reconciliation_scan_cursor.sql](../../../backend/nq-infra/src/main/resources/db/migration/V48__reconciliation_scan_cursor.sql)。执行时已确认原最大版本 V47。
- Schema added：`reconciliation_scan_cursors`，五列 `venue / cursor_created_at / cursor_order_id / revision / updated_at`；venue 主键，一条 PK index，键成对可空、非空 venue、非负 revision 约束，表及五列均有 COMMENT。
- Historical migrations changed：NONE；V1–V47 共 47 文件 raw SHA-256 不变。fresh/upgrade Flyway V48 checksum 均为 `1369061222`，重复 migrate=0，validate PASS，pending=0。
- 对已存在 V47：仅新增空表及其 index/constraints/comments，没有 orders ALTER、订单回填或旧表索引建设；V47 75 张表 → V48 76 张表。无订单外键，cursor 指向订单删除后仍可循环。
- Reservation transaction：Spring `REQUIRES_NEW / READ_COMMITTED`，创建 cursor（ON CONFLICT DO NOTHING）→ SELECT cursor FOR UPDATE → 单条 circular SELECT → 写最后扫描键并 revision+1 → commit。
- `nq.reconciliation.scan-transaction-timeout-seconds` 默认 10 秒，由 transaction annotation 读取；用于数据库预留事务等待，不改变 venue timeout/retry 配置。仓储直接绕过 Spring 事务调用时拒绝执行。
- Network under lock：NO。真实 adapter 调用边界断言无 active transaction，并以另一个 PG 连接 `FOR UPDATE NOWAIT` 立即取得 cursor 锁；外层 rollback 也不撤销已提交 reservation。
- Crash-after-reservation behavior：该批可以在本圈延后处理，下一圈仍被选中；进度更新事务失败则全回滚，不跳过未成功预留批次。另有真实 reconciliation 读异常后的下一候选与再绕回原候选回归。
- 查询成本：返回/映射内存 O(limit)，CASE 排序仍需检查 venue/status 的 eligible 集合；本轮没有大规模性能 qualification，也没有把 LIMIT 解释为数据库只检查 limit 行。

## 永久 PostgreSQL 回归

[L4PlanBlockerPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L4PlanBlockerPostgresIntegrationTest.java) 的 `terminalPrefixCannotStarveEligibleVictim` 保留原八种 scenario、前缀数量及预算：

| 原反例 | budget | 新永久断言 |
|---|---:|---|
| CANCELLED 无 fill | 1 | 第一轮 victim=0，第二轮 Trade=1/Ledger=2 |
| CANCELLED 已完全收敛 | 1 | 第二轮到达 victim，旧完整 OrderRecord 不变 |
| FILLED 已完全收敛 | 1 | 同上 |
| CANCELLED NULL identity | 1 | 旧候选不查 fills，第二轮恢复 victim |
| CANCELLED blank identity | 1 | 同上 |
| 两个永久 CANCELLED | 2 | 第二轮恢复 victim，无额外预算 |
| CANCELLED 前缀、ACCEPTED victim | 1 | 第二轮恢复，victim→FILLED |
| 100 个永久 CANCELLED、第 101 单缺 fill | 默认 100 | scheduledReconcile 第二轮恢复第 101 单 |

八场景各五轮（40 轮），第二轮起 Trade/Ledger 保持 1/2，后续重放无重复；fill 查询增量每轮 <= budget。旧 SQL 同场景重复五次仍只选旧前缀，作为原排序反例的直接数据库对照。

另用起始快照中的旧 service，仅重命名 class/constructor 为隔离测试类，结合原 Review probe 原断言，在 PG16/V48 additive schema 上重跑：`8 scenarios / 40 starvation rounds / 8 positive controls / 3 missing-identity cases / 11 tests / 0 failures/errors/skips`。此 PASS 表示旧缺陷再次被复现，不是旧候选正确性通过。源码与日志保存在本轮 artifacts，未替换任何 production class；五个临时 class 已按路径校验后从 target 清理。

[ReconciliationCursorPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/ReconciliationCursorPostgresIntegrationTest.java) 证明：

- 相同 created_at 的稳定排序；wrap 中无重复、总预算不变；更早 BINANCE 订单不消耗 OKX budget，两个 venue 各有独立 revision。
- cursor 前插入新候选、后方状态变为 eligible、前后 ineligible/eligible 变化，以及 cursor 指向订单删除后仍可达。
- 先销毁第一个 Spring scanner/context，再创建第二实例：`a → destroy → b → a`，数据库 revision=3；不是 static 或 JVM-memory cursor。该测试是同 JVM 的独立 application context 重建，原 recovery 两 JVM proof 另外执行，不将其冒充新的 cursor 进程崩溃 qualification。
- 两个独立 Spring context 同时预留：20 轮 barrier、40 次并发 reservation；每次 size=1，集合 40 个不同候选，revision 每轮增 2，随后到第 41 单并 wrap，revision=42。
- 预留返回后另一个连接立即可锁；外层 rollback 不影响进度；触发器注入 cursor update 失败后 revision 与下一扫描不丢失；空集合、非法输入、绕过事务代理均按合同处理。
- fresh V1→V48、existing V47→V48、validate、history checksum stability、表/列/index/comment counts 与约束拒绝。

## C2/C1 与完整验证

本轮全部使用离线 Maven、本地缓存 PG16.15、显式专用 loopback datasource、synthetic/no-outbound venue。无真实交易、生产调用或 credential 读取。

| 最终 Full Maven 中的验证 | tests | failures | errors | skips |
|---|---:|---:|---:|---:|
| C2 unit | 19 | 0 | 0 | 0 |
| C1+C2 PostgreSQL（含新增公平性/网络边界） | 22 | 0 | 0 | 0 |
| Cursor + fresh/upgrade Flyway | 6 | 0 | 0 | 0 |
| C1 V47 migration/OCC | 2 | 0 | 0 | 0 |
| TradingChain causal/recovery | 8 | 0 | 0 | 0 |
| 原两 JVM recovery | 2 | 0 | 0 | 0 |
| 完整 scheduler module（含 recovery/metrics/C2） | 67 | 0 | 0 | 0 |
| Full Maven 总计 | 1814 | 0 | 0 | 50 |

分组为 Full Maven 的重叠子集，不相加。Full Maven exit=0，耗时 1:50，实际执行通过 1764；50 是条件测试跳过，不是 mandatory C1/C2 或 CI job skip。逐 test 的实际 skip reason 在 `full-maven-summary.json`；包括额外 PG opt-in、Linux-only、Windows symlink 权限及手工/网络/CI opt-in。两个 ArchUnit 类、Ledger 和 API 测试均在最终 full reactor 中通过。

C2 invariants：共享总预算、CANCELLED 完整 OrderRecord/status/version、identity missing 不查 fills、Trade exchangeTradeId 唯一/身份校验、Ledger durable replay/幂等、fee=0 两条分录与 fee>0 四条分录均 PASS。没有引入 PLACE/CANCEL；测试准备的 synthetic PLACE/CANCEL 计数在 reconciliation 前后不变，kill 保持 ENGAGED。

C1 invariants：stale PLACE、stale CANCEL、ABA、pre-cancel 均 PASS。OrderCommandWriteService、OrderLifecycleService、V47 字节保持；JdbcOrderRepository 的 CAS、external identity writer 与原 findByStatuses 方法体不变；四个 C1 核心 PG 方法体与起始快照相等。该 repository 只新增扫描事务，不改 OCC SQL。

## 失败执行与最小 fixture 适配

下列失败测试日志均保留，不当作 PASS；准备命令失败另见本任务工具输出：

1. 前置快照遇 Windows 长路径：改为按 SHA-256 命名存储原始字节；六文件指纹校验此前已成功。首次 initdb 拒绝 root，改用普通 WSL 用户；未使用生产 PG。
2. `targeted-first.log` exit=1：驱动拒绝 `Timestamp + TIMESTAMP_WITH_TIMEZONE` 非空 cursor 绑定。使用 Spring TIMESTAMP/setTimestamp 绑定并保留 SQL timestamptz cast，随后所有真实扫描回归通过。
3. `targeted-second/third.log` exit=1：新增 schema 测试错误地把 Flyway schema-marker 当作 version migration，并误数五列为六列。按实际 Flyway version 行/五列合同修正断言，DDL 未因此放宽。
4. `full-maven.log` exit=1，六个 TradingChain 失败：旧测试把准备订单放在外层未提交事务，独立 reservation 正确看不到它们。六个测试在扫描前显式提交准备事实；原业务断言未删改。
5. `fixture-remediation.log` 与 `full-maven-retry.log` exit=1：提交后原本由 rollback 隔离的其他测试事实影响全库 ledger 检查。最终采用 TradingChain 每 context 独立随机 schema，在 ContextClosed 清理其自身 schema，不修改生产 ledger 语义或把全库断言改成容忍差异。
6. `fixture-isolation.log` exit=1：隔离 schema 的 search_path 缺少 public 中的 pgcrypto 函数。测试 URL 增加 public 搜索路径，未改历史 migration。`fixture-isolation-retry.log` 因继续任务时 PG 已停止而连接拒绝；恢复隔离实例后 `fixture-isolation-after-resume.log` 8/8 PASS。
7. `full-maven-final.log` 是最终候选全量成功证据；`original-baseline-reproduction.log` 是单独旧实现对照，二者不可混计。

[TradingRestartRecoveryPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TradingRestartRecoveryPostgresIntegrationTest.java) 仅将“迁移到 latest”的预期版本从 47 改为 48；C1 的显式 V47 历史 migration 测试保留 V47。
原两 JVM recovery 还分别创建全新数据库并从零执行 Flyway 至 V48，因此 fresh proof 同时具有空 schema 的专项断言与空数据库的运行证据。

## 变更范围、治理与回滚

本轮 production：

- [OrderRepository](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/domain/port/OrderRepository.java)：增加扫描 reservation 合同。
- [OrderCommandService](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java)：转发专用 reservation。
- [JdbcOrderRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepository.java)：短事务、row lock、循环查询和持久化推进。
- [OkxRestReconcileService](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java)：调用新的 OKX reservation。
- V48 additive migration。

测试：L4PlanBlocker、ReconciliationCursor（新增）、TradingChain、TradingRestartRecovery、OrderCommandServiceTest 的 in-memory fixture 显式拒绝 durable scan、OkxRestReconcileServiceTest 与 OperationalReconciliationMetricsTest 的 venue/reservation mock 合同。再加本文，总计本轮 13 个文件；原两份 C2 evidence 保留，整个候选相对 HEAD 有 15 个文件。

适用标准：primary=`java-backend-maintenance`；supporting=`nq-java-engineering-standard`（TRADING_CORE/TRANSACTION_CONCURRENCY/ARCHITECTURE）和 `db-schema-migration-review`（新增 Flyway）。platform-profile、NQ domain 与 architecture overlay 已读取。PASS：模块方向、端口 owner、短事务/锁/异常回滚、幂等、状态/version、SIM/no-outbound、forward migration。NOT_APPLICABLE：新依赖、Java/Spring 升级、新线程模型与真实 provider；无新增标准豁免。独立 review 未执行。

已执行 authority checker PS5.1/PS7、next-action、lifecycle、agent-workflow、stage-assets、Java-standard：均 exit=0；当前 authority 未写入。末次 doc links、范围 diff、`git diff --check`、staged=0、candidate manifest 与 rollback apply-check 记录于本轮 `completion.json`。任何后续文件变化都必须重新生成 manifest，不能沿用本次验证冒充 exact-head CI。

Rollback：本轮 artifacts 的 `rollback.patch` 仅反向撤销本轮增量，恢复起始 C2 Attempt-02 候选，原始字节另存 snapshots。先运行 `git apply --check artifacts/20260907-c2-starvation-remediation/rollback.patch`；预检通过后才在另行授权的回滚动作中 apply。本轮只预检，不实际回滚。若 V48 已应用，回退代码后 cursor 表作为 inert additive schema 留存，不自动 DROP、不做生产 destructive downgrade；测试的随机 schema 清理不是生产回滚。

## 最终 disposition

```text
P0: 0 observed in this implementation scope
P1: starvation candidate remediated; formal closure PENDING independent correctness review
P2: 0 new implementation findings in scope; scale qualification NOT_RUN
P3: 0 new implementation findings in scope
Final decision: IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW
Commit recommendation: NO_COMMIT / NO_DELIVERY
Next action: NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C2-INDEPENDENT-REVIEW-ATTEMPT-02
staged=0; commit=NONE; push=NONE; PR=NONE; tag=NONE; CI delivery=NONE
```

经未来独立审查与显式提交授权后，可用提交说明：`fix(reconcile): 以持久化循环游标消除候选饥饿`。
B5 distributed-worker qualification、B0、L4 acceptance、exact-head CI、生产迁移/性能验证均未执行；本轮不直接进入 delivery。

工具声明：PowerShell 5.1/7、Git、rg、Python、Java/Maven、WSL/PostgreSQL，用于本地实现、fixture 和验证；functions 用于本地执行与补丁。connector MCP、子代理与互联网访问未使用；网络仅本机 loopback PG。Skills 如上。写操作限上述候选文件、ignored artifacts/Maven 输出及专用临时 PG；无 credential、生产、真实交易、外部发送、暂存或发布操作。
