# GateY-5 isolated worker / dry-run / rollback / restore / lock-window 安全与运维审查 attempt-01

## Review target

- 分类：`INDEPENDENT_SECURITY_REVIEW + OPERATIONS_REVIEW + PROCESS_RECOVERY_REVIEW + LOCK_WINDOW_REVIEW + BACKUP_RESTORE_REVIEW + FAKE_PROVIDER_ISOLATION_REVIEW + ARCHITECTURE_REVIEW + FRONTEND_CLOSURE_REVIEW`。
- ownership：NQ-only；L 级独立高风险审查。
- baseline：`dev`；`HEAD == origin/dev == b1ac45601dc8908b8301ff6f48d439d44c52bcd3`；staged=`0`。
- review scope：baseline 加当前全部 GateY-5 未提交 worktree diff；未发现 GateY-5 之外的 mixed worktree。
- authority before：`GateY-4 / ACCEPTED|CI_GREEN`；`GateY-5 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；LIVE=`DISABLED`；kill switch=`ENGAGED`。

## Exact changed-path manifest

```text
README.md
backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/EvaluationController.java
backend/nq-api/src/main/java/com/guidinglight/nexusquant/runtime/api/FakeDryRunOperationsService.java
backend/nq-api/src/main/java/com/guidinglight/nexusquant/runtime/api/OperationalReadinessService.java
backend/nq-api/src/main/java/com/guidinglight/nexusquant/runtime/api/dto/FakeDryRunOperationsResponse.java
backend/nq-api/src/main/java/com/guidinglight/nexusquant/runtime/api/dto/OperationalReadinessResponse.java
backend/nq-api/src/main/java/com/guidinglight/nexusquant/runtime/api/web/OperationalReadinessController.java
backend/nq-api/src/test/java/com/guidinglight/nexusquant/research/api/web/EvaluationControllerTest.java
backend/nq-api/src/test/java/com/guidinglight/nexusquant/runtime/api/web/OperationalReadinessControllerTest.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/livecontrol/executionworker/DisposableFakeVenueLauncher.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/livecontrol/executionworker/DisposableWorkerReleaseVerifier.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/livecontrol/executionworker/IsolatedFakeExecutionWorkerLauncher.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/ModuleBoundaryArchTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/executionworker/DisposableWorkerReleaseVerifierTest.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/LiveSession.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/ExecutionIntentService.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/port/ExecutionAttemptLifecycle.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/port/ExecutionOperationsSnapshot.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/port/ExecutionOperationsSnapshotQuery.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/execution/ExecutionIntentRuntimeTest.java
backend/nq-eval/src/main/java/com/guidinglight/nexusquant/research/application/eval/BacktestEvaluationService.java
backend/nq-eval/src/main/java/com/guidinglight/nexusquant/research/application/eval/api/BacktestRunApiService.java
backend/nq-eval/src/main/java/com/guidinglight/nexusquant/research/domain/eval/port/BacktestEvaluationReportRepository.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/fake/LoopbackFakeExchangeHttpClient.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionOperationsSnapshotQuery.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/eval/jdbc/JdbcBacktestEvaluationReportRepository.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/execution/infra/fake/LoopbackFakeExchangeHttpClientTest.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionOperationsSnapshotQueryTest.java
docs/current/README.md
docs/current/ROADMAP.md
docs/current/STATUS.md
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/evidence/gate-y/README.md
docs/current/evidence/gate-y/NQ-GATEY-5-ISOLATED-WORKER-DRYRUN-ROLLBACK-RESTORE-LOCK-WINDOW-IMPLEMENTATION.attempt-01.md
docs/current/evidence/gate-y/NQ-GATEY-5-ISOLATED-WORKER-DRYRUN-ROLLBACK-RESTORE-LOCK-WINDOW-SECURITY-OPERATIONS-REVIEW.attempt-01.md
frontend/src/pages/runtime/RuntimeReadinessPage.tsx
frontend/src/types/operational-readiness.ts
frontend/tests/e2e/backtest-detail-smoke.spec.ts
frontend/tests/e2e/marketdata-positive-bars-fixture-smoke.spec.ts
frontend/tests/e2e/marketdata-positive-bars-fixture.ts
frontend/tests/e2e/marketdata-real-backend-smoke.spec.ts
frontend/tests/e2e/runtime-operational-readiness-overview-smoke.spec.ts
scripts/gatey/GateY5FlywayLauncher.java
scripts/gatey/gatey5-post-fixture.sql
scripts/gatey/gatey5-pre-fixture.sql
scripts/gatey/gatey5-worker-fixture.sql
scripts/gatey/run-gatey5-isolated-worker-drill.ps1
scripts/gatey/run-gatey5-lock-window-drill.ps1
scripts/gatey/run-gatey5-post-restore-drill.ps1
scripts/gatey/tests/run-gatey5-lock-window-regression.ps1
scripts/gatey/tests/run-gatey5-post-restore-regression.ps1
```

## Independent verification

### Migration, scale and deterministic fixtures

- V1～V39 migration diff=`0`；V40=`NONE`；未发现 runtime DDL、Flyway repair、history manipulation、constraint/trigger bypass。
- reviewed scale source Git commit：`b1ac45601dc8908b8301ff6f48d439d44c52bcd3`；capacity manifest SHA-256：`bbb67585855ef1c10adf2fbd57ef7cbdd270af702c4a322fe5a38d328037ee81`。
- PRE exact rows=`3,557,032`，logical digest=`0be3fc4a207da07188e32aa36ee6acd7fb655293b6b8b1aa9ff58d6789b484fe`。
- POST exact rows=`11,728,032`，logical digest=`78063819fc1c35256f1bc27613fbd0b16f74c6201bd315c7f31eda09e57b690c`。
- generator 使用固定 seed、确定性 ID/时间、合法 FK/UNIQUE/CHECK/状态转换；synthetic credential bytes 由固定 digest 生成，不读取真实 credential；没有 row-count shortcut。

### Lock window and migration failure

- normal V39=`997ms`；120s long read=`NON_BLOCKING / 978ms`。
- order/account/strategy-release/multiple-writer blocked DDL=`5,800/5,757/5,746/5,712ms`，均满足 `lock_timeout=5s + tolerance=2s <=7s`。
- 独立修复后的 harness 记录 `pg_locks`、`pg_stat_activity`、blocking/blocked PID、relation 与 lock mode，并按场景精确释放 blocker。
- statement timeout=`60.148s`，由 PostgreSQL `statement_timeout` 触发；schema atomic、Flyway 未记录 false success；释放 blocker 后 retry 与 validate 通过，未 repair、未手工 DDL 修正、未删除 schema history。
- disposition：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED=CLOSED_FOR_REVIEWED_SYNTHETIC_DISPOSABLE_GATEY_SCALE`。这不是 production observation、SLA 或 deployment authorization。

### Worker, fake venue, retry and kill

- worker 为独立普通 JVM main，不启动 Spring control-plane context；entrypoint/classpath/config/PID/health/release identity 均受 bounded 校验。
- worker 不拥有 strategy admission、risk-rule authoring、operator approval、session authorization、credential lifecycle、kill disengagement、arbitrary intent/venue、ledger ownership；只消费已批准 durable intent。
- fake venue 仅接受 `http://127.0.0.1:<port>`；拒绝 DNS/localhost alias/IPv6/userinfo/query/fragment/non-loopback/redirect，且无 real adapter fallback。
- fake remote properties store 独立于 worker memory 与 execution-intent DB；worker crash/restart 后仍可按 `clientOrderId` query remote fact。
- `SEND_STARTED/UNKNOWN` recovery mutation=`0`；crash-after-send、crash-after-mutation、receipt-failure、kill-after-send、rollback-release 的 recovery query 均=`1`。
- duplicate worker 仅一个 claim/CAS 路径可 mutate；crash-before-send、crash-after-mutation、receipt-failure、duplicate-worker、kill-after-claim、rollback-release 均保持 mutation/remote exactly-one bound。
- kill 的 `ENGAGED/UNKNOWN/MISSING/STALE/CONFLICT` 与 revision-change paths 均 fail-closed；最终 kill=`ENGAGED`。

### Release, rollback, restore and incidents

- release verification 覆盖 correct/tampered/wrong/writable/stale release、wrong worker identity、wrong artifact digest；绑定 actual JAR digest、manifest bytes、path containment 与 OS-level readonly fact，不以 writable path 充当 identity。
- rollback `Release A → kill → stop claims → inspect → Release B → verify → restart/reconcile`：intent loss=`0`、duplicate mutation=`0`、receipt/audit rewrite=`0`。
- backup/restore 实际销毁 source disposable DB，并在独立 restored DB 完成 `pg_restore`；不是 list/schema-copy/same-volume restart。
- restore temporal tuple 前后=`1 SEND_STARTED / 2 UNKNOWN / 11 terminal`；restored `SEND_STARTED` mutation=`0`、query=`1`；terminal receipt history=`IMMUTABLE`。
- UNKNOWN、partial/late fill、cancel race 等 remote fact 不足时保持 `UNKNOWN / RECONCILIATION_BLOCKED`，不伪造 FILLED/CANCELLED/REJECTED。

### API, UI, architecture and frontend closure

- `GET /api/runtime/operational-readiness` 只读；Controller 无 SQL，JDBC 位于 infra；响应不含 credential/private payload/internal path。
- UI 明确展示 `FAKE-ONLY DRY-RUN`、`LIVE DISABLED`、kill/worker/release/session/approval/risk/intent/receipt/UNKNOWN/RECONCILIATION_BLOCKED；无 PLACE/CANCEL/START LIVE/DISENGAGE KILL/START PRODUCTION WORKER 操作。
- ArchUnit/backend full regression 通过；core 不依赖 JDBC/infra，fake transport 位于 local/test infra，未新增第二 ledger、reconciliation SoR 或 global runtime authority。
- E2E 独立发现并关闭两个真实缺陷：evaluation query scope 未传递导致跨 backtest config 串读；positive-bars fixture 未隔离 ingestion health facts且混淆 `sourceHealthStatus=FRESH` 与 `sourceHealth=HEALTHY`。
- targeted：evaluation controller=`1/1`、backtest detail=`2/2`、marketdata real backend=`1/1`、positive bars final=`1/1`。
- full frontend E2E：`87 collected / 86 passed / 1 canonical skipped / 0 failed`，exit=`0`。canonical skip 为未配置 order ID 的条件场景。
- frontend build：`tsc -b && vite build`，exit=`0`；保留既有 bundle-size warning。
- full backend：`mvn -f backend/pom.xml test`，23-module reactor `BUILD SUCCESS`，failures=`0`、errors=`0`。

## Security scan and no-real proof

- 检查 command/PowerShell injection、temp/path containment、symlink/reparse、PID reuse、worker impersonation、release substitution、kill bypass、blind retry、fake-to-real fallback、credential leak、restore replay 与 cleanup target。
- GateY-5 lock/post-restore tooling regression 均 PASS；`git diff --check` errors=`0`；migration diff=`0`；staged=`0`。
- current authority checker=`PASS / CURRENT_AUTHORITY_CONSISTENT`；doc links=`281 checked / 14 historical warnings / 0 errors`。链接检查首次遗漏 mandatory `-Roots` 参数，exit=`1` 且未开始扫描；修正为 `-Roots @('README.md','docs/current')` 后通过，不把调用错误写成验证通过。
- credential lookup、real exchange HTTP/WS、real PLACE/CANCEL、transfer/withdraw/borrow/leverage、production DB write/migration/worker/deploy、external egress 均=`0`。loopback fake/E2E traffic 单独计量，不属于 external egress。

## Findings

- P0：无。
- P1：独立 review 中发现的 release binding/readonly、lock graph/精确 blocker cleanup、evaluation query scope、marketdata fixture isolation 与健康枚举断言均已做最小修复并回归；剩余 P1=`0`。
- P2：无。
- P3：既有 React 19 + Ant Design 5 compatibility/deprecated warnings 与 Vite large-chunk warning；不影响本轮 correctness/security hard gates，未扩大范围修复。

## Authority after and decision

```text
accepted_batch=GateY-4
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-5
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-5-ISOLATED-WORKER-DRYRUN-ROLLBACK-RESTORE-LOCK-WINDOW-COMMIT-AND-PUSH
LIVE=DISABLED
kill_switch=ENGAGED
```

最终决定：

```text
PASS /
GATEY_5_SECURITY_OPERATIONS_REVIEW_ACCEPTED /
P0_0 /
P1_0 /
PRODUCTION_LIKE_FIXTURE_VERIFIED /
LOCK_WINDOW_CLOSURE_REVERIFIED /
NO_BLIND_RETRY_REVERIFIED /
FAKE_REMOTE_RECOVERY_VERIFIED /
ROLLBACK_VERIFIED /
RESTORE_REPLAY_SAFE /
FULL_FRONTEND_E2E_GREEN /
FULL_BACKEND_GREEN /
MICRO_LIVE_NOT_AUTHORIZED /
LIVE_DISABLED /
READY_TO_COMMIT
```

推荐提交信息：`feat(gatey): 完成 GateY-5 fake-only worker 与独立安全运维审查`。

唯一下一动作：`NQ-GATEY-5-ISOLATED-WORKER-DRYRUN-ROLLBACK-RESTORE-LOCK-WINDOW-COMMIT-AND-PUSH`。本 review 未 commit、push、tag、deploy，未初始化 GateY-6。

## Post-commit CI secret-scan remediation

- `failed_commit=8d594f1a0000678e4817f3ec80de19ac975da992`
- `failed_ci_run=31727172181`
- `rule_id=generic-api-key`
- `finding_path=docs/current/evidence/gate-y/NQ-GATEY-5-ISOLATED-WORKER-DRYRUN-ROLLBACK-RESTORE-LOCK-WINDOW-SECURITY-OPERATIONS-REVIEW.attempt-01.md`
- `finding_line=73`
- `classification=FALSE_POSITIVE_NON_SECRET_HASH_EVIDENCE`
- `remediation=EVIDENCE_LEXICAL_SANITIZATION`
- `gitleaks_result=PASS`
- `custom_backstop=PASS`

以上结果使用 Gitleaks `8.18.4`、`extend.useDefault=true`、tracked safe working tree、`--no-git` 与 `--redact` 复现；未记录 matched value 或 source fragment，未修改 CI allowlist。
