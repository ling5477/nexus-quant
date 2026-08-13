# GateY-5 isolated worker / dry-run / rollback / restore / lock-window implementation attempt-01

## Task classification

- 类型：`CODE_CHANGE + BACKEND_IMPLEMENTATION + ISOLATED_WORKER_PROCESS + DETERMINISTIC_FAKE_EXECUTION + PROCESS_RECOVERY + DISPOSABLE_CAPACITY_FIXTURE + V38_TO_V39_LOCK_MEASUREMENT + BACKUP_RESTORE + RELEASE_ROLLBACK + INCIDENT_RECONCILIATION + READ_ONLY_OPERATOR_VISIBILITY + SECURITY_BOUNDARY`。
- ownership：NQ-only；L 级高风险实现。
- 最终状态：`IMPLEMENTED|PENDING_REVIEW`（已实现 / 待独立复核），未 commit、未 push、未 tag、未 stage。
- 下一动作：`NQ-GATEY-5-ISOLATED-WORKER-DRYRUN-ROLLBACK-RESTORE-LOCK-WINDOW-SECURITY-OPERATIONS-REVIEW`。

## Scale authority binding

- 起始 `HEAD == origin/dev == b1ac45601dc8908b8301ff6f48d439d44c52bcd3`，branch=`dev`，起始 worktree/staged clean。
- `NQ CI Baseline` run `31689427116`：`completed / success`，head SHA 精确匹配，bad jobs=`0`。
- frozen authority：`REVIEWED_SYNTHETIC_ENVELOPE`；manifest digest=`bbb67585855ef1c10adf2fbd57ef7cbdd270af702c4a322fe5a38d328037ee81`；manifest bytes SHA-256=`0a872c62e74e2ab807c96ce674ada271ccec982dc32bafa17bd2333a6e3aad2b`。
- PRE=`3,557,032` rows，POST=`11,728,032` rows；accepted reservation 分别约 `7.58 GiB / 17.14 GiB`。这些是 reviewed synthetic capacity envelope，不是 production observation 或 SLA。

## Fixture generator contract

- `scripts/gatey/gatey5-pre-fixture.sql` 在 disposable PostgreSQL 的真实 V38 schema 上生成 PRE clone；固定 seed、exact row count、合法 FK/unique/check 与 logical digest。
- `scripts/gatey/gatey5-post-fixture.sql` 在真实 V39 后生成六张新表 steady-state facts：`1,000 / 5,000 / 150,000 / 15,000 / 2,000,000 / 6,000,000`。
- session 按合法 terminal transition 复用 account，不 bypass trigger/check/unique；receipt/intent ratio=`3`。
- tooling 只允许 `ConfirmDisposable`、loopback random port、固定 `postgres:16-alpine`；production profile fail-closed；临时目录清理前验证其位于 repository `artifacts` 下。

## PRE fixture and V38→V39 lock measurements

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gatey/run-gatey5-lock-window-drill.ps1 -ConfirmDisposable -Scale Full
```

结果：`PASS / GATEY5_V38_V39_LOCK_WINDOW_MEASURED`。

| 项目 | 实测 |
| --- | --- |
| PRE exact rows | `3,557,032` |
| PRE logical digest | `0be3fc4a207da07188e32aa36ee6acd7fb655293b6b8b1aa9ff58d6789b484fe` |
| V38 migration | `2,408 ms` |
| normal V39 | `997 ms` |
| order writer | `5,800 ms` |
| account writer | `5,757 ms` |
| strategy-release writer | `5,746 ms` |
| concurrent writers | `5,712 ms` |
| long read age | `120s` fault injection |
| long-read result | `NON_BLOCKING / 978 ms` |
| active transaction preflight | `>30s -> DENIED` |
| statement timeout | `60.148s / schema atomic` |
| retry / Flyway validate | `PASS / PASS` |

四类 genuinely blocked DDL 均在 `lock_timeout=5s + tolerance=2s` 的 `<=7s` 上限内。failure injection 后 V38 schema 与 Flyway history coherent，无 partial V39 acceptance；释放 blocker 后 V39 retry 与 validate 通过。

PRE actual relation/index/total bytes（`pg_relation_size / pg_indexes_size / pg_total_relation_size`）：

| relation | relation bytes | index bytes | total bytes |
| --- | ---: | ---: | ---: |
| `accounts` | 172,032 | 212,992 | 417,792 |
| `backtest_configs` | 7,872,512 | 5,185,536 | 13,099,008 |
| `backtest_publish_records` | 20,692,992 | 14,729,216 | 35,463,168 |
| `backtest_runs` | 107,782,144 | 160,022,528 | 267,870,208 |
| `exchange_account_credentials` | 1,261,568 | 163,840 | 1,466,368 |
| `exchange_accounts` | 237,568 | 344,064 | 614,400 |
| `orders` | 609,247,232 | 1,448,501,248 | 2,057,928,704 |
| `research_configs` | 2,686,976 | 1,794,048 | 4,521,984 |
| `roles` | 8,192 | 32,768 | 40,960 |
| `strategy_definitions` | 270,336 | 352,256 | 663,552 |
| `strategy_release_admission_state` | 9,306,112 | 7,135,232 | 16,474,112 |
| `strategy_runs` | 45,383,680 | 73,711,616 | 119,144,448 |
| `strategy_versions` | 3,637,248 | 3,506,176 | 7,184,384 |
| `user_roles` | 212,992 | 204,800 | 450,560 |
| `users` | 106,496 | 106,496 | 245,760 |
| aggregate | 808,878,080 | 1,716,002,816 | 2,525,585,408 (`2.352 GiB`) |

处置：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED=CLOSED`。该结论只关闭 synthetic disposable measurement blocker，不是 production SLA，也不授权 production migration。推荐 deployment guard：至少 `60 GiB` free space（source+backup+restore 并存）、active transaction age `<=30s`、检查 `pg_stat_activity/pg_locks`、`lock_timeout=5s`、`statement_timeout=60s`；blocked DDL 超过 `7s`、schema/Flyway 不一致或 free space 不足立即 abort，不 repair、不手工 patch，释放 blocker 后才 retry。

## POST fixture and backup/restore

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gatey/run-gatey5-post-restore-drill.ps1 -ConfirmDisposable -Scale Full
```

结果：`PASS / GATEY5_POST_FIXTURE_BACKUP_RESTORE_VERIFIED`。

- exact rows=`11,728,032`；logical digest=`78063819fc1c35256f1bc27613fbd0b16f74c6201bd315c7f31eda09e57b690c`。
- restored fact subset rows=`10,521,000`；all sessions terminal=`true`；receipt ratio=`3`。
- restored temporal states=`0 SEND_STARTED / 0 UNKNOWN / 0 terminal-in-worker-subset`（本 full-scale fixture 用 terminal facts；process drill另测非终态恢复）。
- source disposable DB 被销毁后恢复到新 disposable DB；production writes=`0`，external egress=`0`。

POST 六张 V39 fact relation actual bytes：

| relation | relation bytes | index bytes | total bytes |
| --- | ---: | ---: | ---: |
| `execution_intents` | 546,045,952 | 900,448,256 | 1,446,682,624 |
| `execution_receipts` | 1,528,250,368 | 1,312,358,400 | 2,841,010,176 |
| `live_session_events` | 49,143,808 | 55,427,072 | 104,620,032 |
| `live_sessions` | 4,694,016 | 1,982,464 | 6,717,440 |
| `operator_approvals` | 5,120,000 | 2,285,568 | 7,446,528 |
| `risk_limit_sets` | 327,680 | 376,832 | 737,280 |
| aggregate | 2,133,581,824 | 2,272,878,592 | 4,407,214,080 (`4.105 GiB`) |

## Worker process architecture and authority

- 正式源码采用稳定 capability/domain naming，不把阶段名写入生产类、package、配置键或运行时文案：`DisposableFakeVenueLauncher`、`IsolatedFakeExecutionWorkerLauncher`、`DisposableWorkerReleaseVerifier`，package=`com.guidinglight.nexusquant.app.livecontrol.executionworker`，properties=`nq.fake-worker.*`。
- `GateY-5` 仅保留在 `scripts/gatey/**`、测试场景、task/evidence 文件名与 current authority。生产 source 定向扫描对 `GateY-5|GateY5|gatey5|gate-y-5` 为 0 命中。
- worker 是独立普通 JVM main，不启动 Spring context；手工装配 JDBC transaction/repository、`ExecutionIntentService` 与 loopback fake port。
- worker 只核验 release/kill、claim approved intent、持久化 `SEND_STARTED`、执行 fake mutation、query-only recovery、写 receipt/reconciliation 与 health；不创建策略决策、风险规则、approval、credential、订单/成交/持仓/ledger 主事实。
- `ExecutionAttemptLifecycle` 在 claim 前、claim 后、durable `SEND_STARTED` 后、fake mutation 前后提供 fail-closed lifecycle hook；DB transaction 在 fake remote mutation 前结束。
- `SEND_STARTED/UNKNOWN` 恢复只调用 `queryByClientOrderId`，不调用 place/cancel，不 blind retry。

## Fake venue architecture and isolation

- `LoopbackFakeExchangeHttpClient` 只接受 `http://127.0.0.1:<port>`；禁止 DNS、redirect、userinfo、query、fragment，限制 connect/request timeout 与 response bytes，无 fake→real fallback。
- fake venue 在独立 JVM 中运行，atomic properties store 位于 repository `artifacts`；以 `clientOrderId` 为稳定 key，独立于 worker 与 NQ execution-intent DB lifecycle。
- 支持 place/cancel/query、partial fill、late fill、cancel race、controlled timeout/error 与 unresolved `UNKNOWN`。
- fake store 仅是 disposable venue observation fixture，不是第二 execution ledger；external egress=`0`。

## Process restart / replay / rollback / restore drill

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gatey/run-gatey5-isolated-worker-drill.ps1 -ConfirmDisposable
```

结果：`PASS / ISOLATED_FAKE_WORKER_PROCESS_RESTORE_INCIDENTS_VERIFIED`。

| 场景 | mutation | remote | query | 结果 |
| --- | ---: | ---: | ---: | --- |
| crash before send | 1 | 1 | 0 | lease 后 controlled reclaim，最终一次 mutation |
| crash after SEND_STARTED before mutation | 0 | 0 | 1 | query-only，`RECONCILED` |
| crash after mutation before receipt | 1 | 1 | 1 | remote observation 恢复 receipt |
| receipt transaction failure | 1 | 1 | 1 | query-only recovery |
| duplicate worker | 1 | 1 | 0 | claim/CAS 阻止重复 mutation |
| kill after SEND_STARTED | 0 | 0 | 1 | revision change fail-closed，query-only |
| rollback Release A→B | 1 | 1 | 1 | stop claims/engage kill/verify prior release/reconcile |
| partial fill | 1 | 1 | 1 | 统一 receipt/state machine |
| late fill | 1 | 1 | 1 | 统一 receipt/state machine |
| cancel race | 1 | 1 | 1 | 统一 receipt/state machine |
| unresolved UNKNOWN | 0 | 0 | 1 | 保持 UNKNOWN，无伪造 terminal receipt |

所有 mutation counters 满足 expected exactly-once bound；`SEND_STARTED/UNKNOWN` mutation=`0`。kill 在 envelope、claim 与 send boundary 重读唯一 durable authority；最终 kill=`ENGAGED`。

process drill 实际销毁并恢复 worker DB。restore temporal tuple 前后均为 `1 SEND_STARTED / 2 UNKNOWN / 11 terminal`；restored `SEND_STARTED` 为 mutation=`0`、query=`1`；terminal receipt history=`IMMUTABLE`。fake venue observation 独立保留，旧 lease 不触发 resend。

## Immutable release and worker health

- release verifier 绑定 exact release/worker identity、实际构建 `nq-app` JAR SHA-256、expected manifest byte SHA-256，并解析同一份已校验 bytes，避免二次读取 TOCTOU。
- manifest 必须 regular/bounded、immutable、age `<=24h`；tampered、wrong release、writable、stale、wrong worker 均 deny。
- 该 verification 始终保持 `tradingAuthorization=false`、`productionStartAuthorization=false`。
- atomic health file 记录 worker instance、release identity、start/last observed、health、claim count、sanitized intent；无 credential/raw payload。poll/lease/execution/reconcile/shutdown 均 bounded。

## Operator visibility and read-only API

- 复用 `GET /api/runtime/operational-readiness` 与 `RuntimeReadinessPage`，新增独立 read-only projection/service：`ExecutionOperationsSnapshotQuery`、`JdbcExecutionOperationsSnapshotQuery`、`FakeDryRunOperationsService`。
- `nq-api` 无 SQL；infra 承载 JDBC；复用 kill/session/approval/risk/intent/receipt facts，不新增表、migration、heartbeat table或写 API。
- 页面显示 `FAKE-ONLY DRY-RUN`、`LIVE DISABLED`、kill/session/approval/risk、worker/release、intent/receipt、UNKNOWN/FAILED/RECONCILIATION_BLOCKED、`tradingAuthorization=false`、`productionStartAuthorization=false`。
- 无 START/PLACE/CANCEL/DISENGAGE/production worker 按钮。没有 durable observation 时返回 `NOT_OBSERVED/NOT_RECORDED`，不伪造在线事实。

## Security scan and architecture hygiene

- P0=`0`，P1=`0`。
- 已检查 command/PowerShell argument injection、temp path containment、symlink/reparse、process identity、PID reuse、stale release、kill bypass、blind retry、fake→real fallback、credential escape、restore replay duplication 与 database cleanup target。
- PowerShell 容器名使用 fixed prefix + GUID allowlist；Docker 端口只映射 loopback；run directory 删除前解析并验证位于 repository `artifacts`。
- core 不依赖 infra；API 无 SQL；infra 拥有 JDBC/fake transport；worker 复用 application contract；ArchUnit 已覆盖新增边界。
- V1～V39 未修改，V40=`NONE`；未新增第二 kill switch、execution ledger、orders/trades/positions/ledger owner。

## Tests

- focused lifecycle/release/fake transport/projection/API/ArchUnit：`PASS`。
- PostgreSQL + real isolated JVM + fake venue persistence + restart/replay + rollback + destructive disposable restore：`PASS`。
- full backend：`mvn -f backend/pom.xml test`，23/23 modules `BUILD SUCCESS`；`nq-app` 276 tests / 0 failures / 0 errors / 27 skipped。
- frontend build：`npm run build`，`PASS`。
- targeted Playwright：`runtime-operational-readiness-overview-smoke.spec.ts`，1 passed。
- full `npm run test:e2e`：`FAILED`（退出码 1）。collection 为 87 tests，`.last-run.json` 保留 32 个 failed ids；失败用例依赖未启动的 local backend，反复出现 `ECONNREFUSED 127.0.0.1:18888`。该结果未写成通过；last-run 文件未保留精确 pass/skip 计数。新增 backend-free targeted smoke 已单独通过。

## No-real / no-egress proof

```text
real credential lookup=0
real exchange HTTP=0
real WebSocket=0
real PLACE=0
real CANCEL=0
transfer=0
withdraw=0
borrow=0
leverage=0
production DB writes=0
production migration=0
production worker start=0
production deployment=0
external egress=0
```

Loopback fake calls 由每个 process scenario counters 实际计量；它们不构成 external egress。LIVE=`DISABLED`，kill switch=`ENGAGED`，micro-live=`NOT_AUTHORIZED`。

## Findings and residuals

- P0：无。
- P1：无。
- P2：完整 frontend E2E 因 local backend `18888` 未启动而失败，需在不触发真实 provider/credential/外联的 local fake-only environment 中复核。
- P3：无。
- synthetic disposable lock measurement 不能称 production SLA；production migration/deployment/worker、first real order、micro-live 与 LIVE 仍未授权。

## Authority transition

Before：

```text
accepted_batch=GateY-4
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-5
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
```

After：

```text
accepted_batch=GateY-4
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-5
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-5-ISOLATED-WORKER-DRYRUN-ROLLBACK-RESTORE-LOCK-WINDOW-SECURITY-OPERATIONS-REVIEW
```

## Final decision

```text
PASS /
GATEY_5_FAKE_ONLY_ISOLATED_WORKER_IMPLEMENTED /
PRODUCTION_LIKE_FIXTURE_REALIZED /
V38_V39_LOCK_WINDOW_MEASURED /
PRODUCTION_LOCK_WINDOW_CLOSED /
PROCESS_RESTART_REPLAY_VERIFIED /
NO_BLIND_RETRY_VERIFIED /
ROLLBACK_DRILL_VERIFIED /
RESTORE_DRILL_VERIFIED /
INCIDENT_RECONCILIATION_VERIFIED /
OPERATOR_VISIBILITY_IMPLEMENTED /
PENDING_INDEPENDENT_REVIEW /
MICRO_LIVE_NOT_AUTHORIZED /
LIVE_DISABLED
```
