# B2 组合候选独立正确性审查

Task classification: HIGH_RISK / INDEPENDENT_CORRECTNESS_REVIEW / NQ-only

Review mode: REVIEW_ONLY。本会话未参与候选实现；在任何测试之前固定候选身份。独立执行与分析现有真实进程 harness，不将实现方日志视为 reviewer 证据。

Starting HEAD: `d1cedb6debfaa3dbeacb0e95ef8599c69e5f9da3`

Branch: `audit/post-gatey-agent-baseline`

Candidate fingerprint start: `f7fc0fe02ede99e1f52a6b52cb6786d41d0a2efb0bd377e93352568e92a14da2`

Candidate fingerprint end: `f7fc0fe02ede99e1f52a6b52cb6786d41d0a2efb0bd377e93352568e92a14da2`

Fingerprint match: YES。算法为所有 tracked + nonignored untracked 文件的排序路径与原始字节 SHA-256 清单（缺失路径标记 MISSING），对 UTF-8 BOM/CRLF 清单再取 SHA-256。清单见 start-manifest.txt / end-manifest.txt；包含既有未提交证据，不包含 ignored build output。

另按实现 manifest 原算法核对 15 个源码 Git canonical blobs：`1262ace8561a134343d32f92b6efeaaeb7bcc22875683c5957e546c044d6e77e`，全部一致。

Terminal convergence: PASS。专用 reconciliation 入口从仓储读取完整 durable proof；校验 identity、正数量与环境。订单行锁后新语句重新证明、以 CANCELLED/status/version 做 CAS；同一事务写状态、审计、事件。

Partial cancel: PASS。original=10，executed=4，CANCELLED/version6，remaining=6。

Full-fill race: PASS。cancel ACK 后本地 CANCELLED/version6，后续 durable unique fills=4+3+3=10，venue FILLED，经 ordinary recovery 成为 FILLED/version7。

Overfill: PASS。10.00000001 拒绝；并发不同 fill 的超量插入被共享订单行锁拒绝；SQL 对等待/并发后的完整事实重新证明，不 clamp。

Remaining quantity: PASS。部分为6，足量为0，精确 decimal 比较。

Environment provenance: PASS。INSERT 的 trade_env 来自同一事务锁定的 canonical orders.trade_env，无调用方 Trade 环境自由参数，无 profile/adapter/Synthetic/default SIM 推断。

SIM: PASS，SIM Order → SIM Trade。

LIVE: PASS，LIVE Order → LIVE Trade。此处 LIVE 是隔离 fixture 的持久化环境字段；真实 LIVE capability、provider、生产交易没有启用。

Mismatch rejection: PASS。SIM/LIVE 双方向损坏 PG fixture，三个 Trade 读取入口均拒绝；reconciliation 在 Ledger replay 前拒绝并审计，terminal proof/CAS 也拒绝。原错误字段及 Order/version 不变，Ledger=0，未自动回填历史数据。

OCC/version: PASS。专用纠正只+1；重复 recovery 业务快照相同；并发 CAS 只有一次纠正事件/审计；事件失败真实事务回滚。

Stale ACK: PASS。真实 Spring/PG stale PLACE、stale CANCEL、ABA、pre-cancel race、CAS conflict 均通过；旧 ACK 不得回退 FILLED。普通生命周期仍禁止 CANCELLED → FILLED。

Trade: PASS。逐 unique fill 唯一，重复 venue 报告不重复写 Trade。

Ledger: PASS。逐 fill 核对 canonical key、delta、currency、direction；重复 reconciliation 无重复 Ledger。

Fees: PASS。三笔费用0/0.01/0.02 USDT，各笔独立核对；不以固定分录条数判定正确性。

Replay: PASS，Order/Trade/Ledger/events/positions/account snapshots 在二次 recovery 后不变。

Restart: PASS。旧 NQ 被杀且退出后 venue 才生成余下 fills，新 NQ PID 使用同一数据库恢复，PLACE 始终1。

C1 interaction: PASS。持久化 OCC/version、stale ACK/ABA/pre-cancel 相关回归通过。

C2 interaction: PASS。相关 PG 共28项（L4 blocker22 + cursor6）。共享总 limit、venue-before-limit、cursor 持久化/公平性、CANCELLED missing-fill discovery 和 Trade/Ledger replay 保持。V48 与 HEAD 原始字节相同，SHA-256=`5147c5b6dddc8b6bd05ce9f10620b2a2f9c9bd1b338d120d1df8da57c983b742`。

Historical LIVE classification: `REACHABLE_BUT_PERSISTED_ENVIRONMENT_NOT_PROVEN`。独立只读核对发布 commit `8e3dd0cf6104eb85f36a0e434ca51ea9d903705a` 的 pilot persistFills → trades.insert 及旧仓储省略 trade_env 路径；归档只记录 Order FILLED/LIVE、Trade1、Ledger4，没有 Trade trade_env 字段读回。未连接生产数据库，未证明历史误绑定，未重开 GateY/重跑 pilot/更改历史。

Real-process scenarios: PASS，12/12。真实 Java21/Spring、PreTradeRiskService、JDBC/transaction/HTTP serialization、PG16.15/V48、独立 Venue/NQ JVM。Controller 通过 reader 查询业务表；初始化只发生在 disposable DB 启动前，运行中角色无 kill switch 写权限。

Targeted tests: PASS，109 tests，0 failures/errors/skips，exit0，2:42。B2 PG13、C1/C2 PG28、B2 JUnit2承载12进程场景、F004/F002恢复及相关 unit tests；逐类统计见 targeted-summary.json。

Full Maven: PASS，精确命令 `mvn -f backend/pom.xml test`，只运行一次。1836 tests，0 failures，0 errors，84 skips，实际执行1752，exit0，1:31。

测试环境：进程白名单；SPRING_PROFILES_ACTIVE、NQ_*、JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/_JAVA_OPTIONS/MAVEN_OPTS 均未继承。初始相关环境 key 列表为空。仅绑定 reviewer 专属 loopback datasource 与公开非空 fixture 密码（不输出秘密）。Full Maven 使用另一个 fresh DB `nq_b2_review_full`；开跑前已读回 V48、Order/Trade/Ledger=0、唯一必要 research account=1。未使用共享数据库。

Conditional skips: 84项逐类原因见下表。Full Maven 本身 B2/C1/C2 opt-in 测试默认关闭，不能称作 Full Maven 实际运行了这些 proof；本轮同一候选的定向测试显式启用四个 mandatory 类并全部执行通过（共43个展开测试，包括12进程场景）。其余未启用的迁移/研究/历史pilot/诊断及平台限定测试不计通过，不扩大审查结论。

Files changed: 0（本轮 candidate 变更）；已有11个 tracked modified及既有 untracked候选原样保留。staged=0；commit/push=NONE。生成日志只在 ignored target 与仓库外 reviewer 目录。

P0: 0

P1: 0

P2: 0（本次范围内新增 finding）

P3: 0（本次范围内新增 finding）

Final decision: PASS / B2_COMBINED_CORRECTNESS_REVIEW_ACCEPTED / TERMINAL_CONVERGENCE_P1_CLOSED / LIVE_TRADE_ENVIRONMENT_P1_CLOSED / READY_TO_RESUME_B2_QUALIFICATION

Commit recommendation: 本轮不 commit/stage/push；正确性审查已通过，但未授予发布权限，也未执行 exact-head CI。

Next action: 返回原 B2 qualification，一次完整完成此前中断的 cancel/fill race matrix、three-fill accounting、remaining quantity、fee variants、restart/replay。**B2 仍 NOT_ACCEPTED**。

当前 STATUS 机器区块仍处于 pre-B0。用户明确授权本次隔离 correctness review；本报告不修改机器 authority、不据此执行真实交易或阶段发布。

## Reviewer 真实进程身份

| 环境 | 场景 | NQ PID | restart PID | Venue PID | 终态/version |
|---|---|---|---|---|---|
| SIM | FINAL_FILL_BEFORE_CANCEL_EFFECT / restart=False | 6880 | — | 42744 | FILLED/7 |
| SIM | FINAL_FILL_BEFORE_CANCEL_EFFECT / restart=False | 12468 | — | 25764 | FILLED/7 |
| SIM | FINAL_FILL_BEFORE_CANCEL_EFFECT / restart=False | 33848 | — | 22684 | FILLED/7 |
| SIM | PARTIAL_FILL_CANCEL / restart=False | 75444 | — | 48900 | CANCELLED/6 |
| SIM | PARTIAL_FILL_CANCEL / restart=False | 3088 | — | 75308 | CANCELLED/6 |
| SIM | PARTIAL_FILL_CANCEL / restart=False | 75016 | — | 42760 | CANCELLED/6 |
| SIM | FINAL_FILL_BEFORE_CANCEL_EFFECT / restart=True | 24280 | 58588 | 884 | FILLED/7 |
| SIM | FINAL_FILL_BEFORE_CANCEL_EFFECT / restart=True | 39628 | 13528 | 3452 | FILLED/7 |
| SIM | FINAL_FILL_BEFORE_CANCEL_EFFECT / restart=True | 68076 | 60976 | 43892 | FILLED/7 |
| LIVE | FINAL_FILL_BEFORE_CANCEL_EFFECT / restart=False | 10604 | — | 58400 | FILLED/7 |
| LIVE | PARTIAL_FILL_CANCEL / restart=False | 68272 | — | 7472 | CANCELLED/6 |
| LIVE | FINAL_FILL_BEFORE_CANCEL_EFFECT / restart=True | 61672 | 20012 | 41172 | FILLED/7 |

## 条件性 skips

| 测试类 | skips | 原因 |
|---|---|---|
| TrustedRootStrategyArtifactVerifierTest | 1 | org.opentest4j.TestAbortedException: Assumption failed: SYMLINK_PRIVILEGE_UNAVAILABLE |
| VerifiedOpenStrategyArtifactReaderTest | 9 | org.opentest4j.TestAbortedException: Assumption failed: SUPPORTED_RUNTIME_LINUX_ONLY |
| TrustedRootArtifactVerifierPrototypeTest | 3 | org.opentest4j.TestAbortedException: Assumption failed: SYMLINK_PRIVILEGE_UNAVAILABLE |
| BinanceWsClientLiveDiagnosticTest | 1 | org.opentest4j.TestAbortedException: Assumption failed: assumption is not true |
| JdbcRepositoryPostgresSmokeTest | 1 | org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL repository smoke is disabled without nq.postgres.smoke.* properties |
| PostgresAdvisorySchedulerExecutionLockPostgresIntegrationTest | 1 | org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL advisory lock integration is disabled |
| ServerControlledStrategyArtifactBindingResolverTest | 1 | org.opentest4j.TestAbortedException: Assumption failed: SYMLINK_PRIVILEGE_UNAVAILABLE |
| ValidationReviewRepositoryPostgresIntegrationTest | 1 | org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL validation review integration is disabled |
| AdmissionGuardedMaterializationPostgresIntegrationTest | 3 | org.opentest4j.TestAbortedException: Assumption failed: local disposable PostgreSQL properties are not configured |
| AdmissionMaterializationGuardPostgresIntegrationTest | 5 | org.opentest4j.TestAbortedException: Assumption failed: local disposable PostgreSQL properties are not configured |
| LiveSessionFactModelPostgresIntegrationTest | 6 | org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL F003 identity convergence proof is disabled; org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL GateY V42 to V43 integration is disabled; org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL GateY-2 integration is disabled; org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL GateY-6D integration is disabled; org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL GateY-6E integration is disabled |
| OperatorPilotAuthorityPostgresIntegrationTest | 4 | org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL V44 integration is disabled; org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL V45 integration is disabled; org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL V45 to V46 integration is disabled; org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL V46 regeneration integration is disabled |
| VenueRuleFactsPostgresIntegrationTest | 2 | org.opentest4j.TestAbortedException: Assumption failed: local disposable PostgreSQL properties are not configured |
| BacktestPublishArtifactLocatorPostgresIntegrationTest | 3 | org.opentest4j.TestAbortedException: Assumption failed: local disposable PostgreSQL properties are not configured |
| KillSwitchRestartDurabilityPostgresIntegrationTest | 1 | org.opentest4j.TestAbortedException: Assumption failed: kill-switch PostgreSQL integration is disabled |
| ShadowRunProvenancePostgresIntegrationTest | 4 | org.opentest4j.TestAbortedException: Assumption failed: local disposable PostgreSQL properties are not configured |
| B0RealProcessHarnessTest | 1 | System property [nq.b0] does not exist |
| B1RealProcessProofTest | 1 | System property [nq.b1] does not exist |
| B2RealProcessProofTest | 2 | System property [nq.b2] does not exist |
| B2TerminalCorrectionPostgresIntegrationTest | 11 | System property [nq.b2.pg] does not exist |
| L4PlanBlockerPostgresIntegrationTest | 13 | System property [nq.l4.blockers.enabled] does not exist |
| ManualPublicOutboundSmokeTest | 1 | System property [nq.public-marketdata.manualSmoke.required] does not exist |
| NoOutboundExchangeGuardTest | 1 | org.opentest4j.TestAbortedException: Assumption failed: env absence is enforced only in CI/no-outbound guard mode |
| NqAppContextPostgresSmokeTest | 1 | System property [nq.app.context.smoke.required] does not exist |
| OrderVersionFlywayPostgresIntegrationTest | 1 | System property [nq.l4.blockers.enabled] does not exist |
| ReconciliationCursorPostgresIntegrationTest | 5 | System property [nq.l4.blockers.enabled] does not exist |
| ValidationReviewFlywayPostgresIntegrationTest | 1 | org.opentest4j.TestAbortedException: Assumption failed: PostgreSQL Flyway integration is disabled |

## 独立原始证据

- targeted.log、targeted-reports、targeted-summary.json
- full-maven.log、full-reports、full-summary.json、full-environment.json、prepare.log
- real-process/（复制本轮 proof 与 NQ/Venue 日志）、real-process-summary.json
- start-manifest.txt、end-manifest.txt、source-verification.json
- run-targeted.py、run-full.py：本轮仓库外运行脚本。

本轮专属容器已核对 label/ID 后删除；B0 harness 自有容器清理 remaining=0。所有证据仅对应当前未提交候选，不替代历史或 CI 身份。
