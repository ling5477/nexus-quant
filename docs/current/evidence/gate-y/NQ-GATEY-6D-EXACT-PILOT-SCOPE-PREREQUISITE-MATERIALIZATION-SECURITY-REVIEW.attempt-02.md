# GateY-6D exact pilot scope prerequisite materialization Security Review — attempt-02

## 审查结论

`PASS / GATEY_6D_SECURITY_REVIEW_ACCEPTED / ATTEMPT_01_P1_CLOSED / P0_0 / P1_0 / TRUSTED_OBSERVATION_AUTHORITY_ACCEPTED / PRODUCTION_FAIL_CLOSED_ACCEPTED / POST_APPROVAL_FORGED_REFRESH_DENIED / AUTHORIZATION_REGRESSIONS_PASS / EXACT_PILOT_SCOPE_NOT_MATERIALIZED / EXECUTION_INTENT_0 / OKX_CALL_0 / EXCHANGE_MUTATION_0 / LIVE_DISABLED / READY_TO_COMMIT`（通过 / GateY-6D 安全评审已接受 / attempt-01 P1 已关闭 / 可进入提交前复核）。

本轮独立复核确认 attempt-01 的开放 P1 `UNTRUSTED_PREREQUISITE_OBSERVATION_AUTHORITY` 已彻底关闭，attempt-01 已关闭的 3 个授权 P1 均未回归。接受范围仅为未提交的 GateY-6D control-plane capability；不表示 exact pilot 已物化，不授权真实 provider、worker、第一笔真实订单、micro-live 或 LIVE。

## Baseline 与审查范围

- task：`NQ-GATEY-6D-EXACT-PILOT-SCOPE-PREREQUISITE-MATERIALIZATION-SECURITY-REVIEW`；attempt-02；NQ-only / LIVE control-plane / P1 remediation verification。
- branch=`dev`；`HEAD == origin/dev == bc35edb60370aee367ab40853201e1f249179b83`；baseline CI=`31933158234 / completed / success`；进入本轮 staged=`0`。
- authority before：`GateY-6D / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；next action 为本 Security Review。
- 已读取 attempt-01 Security Review、trusted observation remediation attempt-01、implementation attempt-01 与当前完整 dirty diff；未只审 remediation diff。
- 审查覆盖 API DTO/controller、application command/port、trusted authority、SoR resolver、transaction service、JDBC repository 调用边界、approval/preflight、PostgreSQL V40 integration、ArchUnit、GateY-2/4/6C regression 与全后端测试。
- 不改 migration/schema、frontend、research、scripts、deploy、CI/governance；不访问真实 credential material、生产、真实 OKX 或交易接口。

## Attempt-01 P1 disposition

`UNTRUSTED_PREREQUISITE_OBSERVATION_AUTHORITY=CLOSED`（已关闭）。

- `PilotScopeMaterializationRequest` 与 `PilotScopeMaterializationCommand` 仅保留 account/credential reference、release/risk selection、symbol/capital/window、expected scope hash 与 request metadata；不再携带 balance、fee、clock、instrument item、observedAt、observation/source identity 或 digest/hash。
- API record 的 `@JsonAnySetter` 对未知字段主动抛错；在全局 Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` 下，旧 observation 字段仍逐项产生 `JsonMappingException`，不会被静默接受。
- main source 检索确认 operator authority field 在 request/command 中为 0；`observationSetId` 仅存在于安全 response/result identity，不是 caller input。

## Trusted observation authority 与 production fail-closed

- core application port `PilotPrerequisiteObservationAuthority` 只接收 server 构造的 immutable `LiveSession`、`PilotScopeBinding` 与 `resolvedAt`，没有 caller observation payload 参数。
- production main source 仅有一个实现：infra 的 `UnavailablePilotPrerequisiteObservationAuthority`；真实 authority 未实现时固定抛出 `TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE`，API 映射 HTTP 503。
- production 未装配 fixture/mock/static balance/static skew 或真实 OKX client；test-only `DeterministicObservationAuthority` 是测试类私有 nested type，不是 Spring bean。
- `JdbcPilotScopeAuthorityResolver` 只重读 account/credential reference、release、risk 与 server-owned runtime scope bindings；缺失、malformed 或 drifting `latest/current/HEAD` 均 fail closed。
- credential material access=`0`；authenticated OKX call=`0`。

## Source/value 同源与 canonical proof

- source identity、source schema、observation values、observedAt、observation identity 与 payload hash均来自同一个 `PilotObservationSet` trusted authority result；operator 无法混入 value 或 envelope 字段。
- control-plane 在入事务前逐项复核 source/schema、recorder、future/stale time、instrument exact symbols 与 scope freshness；scope digest、fee tier/evidence、signed timestamp source 与 observations exact compare。
- `PilotObservationSet` constructor 对四类 observation 逐项重建 `PilotObservationCanonicalEncoder.digest(...)`，并重建 instrument metadata digest；caller-supplied hash 不存在。
- forged source/schema/recorder/future time/stale balance/wrong symbol均返回 `TRUSTED_PREREQUISITE_OBSERVATION_INVALID`，transaction invocation=`0`。

## Materialization atomicity

- production unavailable path 在 transaction invocation 前失败；真实 PostgreSQL 17.7 integration 对随机 exact IDs 验证 `LiveSession/PilotScope/Observation/Approval=0/0/0/0`。
- trusted success path固定调用 `PilotScopeFactTransactionService.materialize(...)`，在同一个 `TransactionTemplate` 内提交 session、scope 与完整四类 observation set；任一步异常整体回滚。
- V40 deferred complete-set、idempotency/conflict/concurrency 与 migration rollback 回归通过；未修改 V40 或任何历史 migration。

## Forged refresh / replay

- `PilotScopeFactTransactionService.refresh(scope, observations)` arbitrary primitive 已删除；main source 中 `PilotScopeFactTransactionService` 的唯一 caller 为 `PilotScopeControlPlaneService`，ArchUnit/source guard已通过。
- existing-session replay 会重新解析 server authority、重新调用 trusted observation authority，并在 transaction 内重新验证当前 `OPERATOR` role。
- post-approval sequenced attack 对 balance、skew、fee、observation identity/source 的伪造无法通过 trusted-result validation；materialize invocation 保持 1，未追加第二个 durable observation set。
- main source 不存在 `API → arbitrary observation → repository` 调用链。

## Attempt-01 已关闭 P1 regression

### Cross-account preflight IDOR

- preflight 先校验当前 `OPERATOR` role，再按 session ID 读取并校验 `ownerId == actor.userId`。
- 非 owner 与不存在 session均返回 `LIVE_SESSION_NOT_FOUND`，不调用 stored-fact preflight，不泄露资源存在性。

### Materialization replay role revocation

- outer service 与 write transaction 内均校验当前 `OPERATOR` role；PostgreSQL 回归撤销 creator role 后 replay 返回 `LIVE_SESSION_OPERATOR_ROLE_REQUIRED`，observation rows保持原 4 条。

### Approval resource probing

- service 在读取 session/scope 前验证 `LIVE_APPROVER`；transaction 内再次验证当前 role。未授权 caller不能用 approval endpoint探测 protected session/scope。
- creator 自审批、approval ID conflict 与 exact scope/hash mismatch继续 fail closed。

## Approval / preflight

- approval schema固定 `pilot-scope.v1`，绑定 exact `pilotScopeId + pilotScopeHash + releaseDigest + riskLimitSetDigest`；legacy `approval-scope.v1` 无法满足 pilot preflight。
- approver来自认证 actor且必须持有 `LIVE_APPROVER`；`creator != approver`；future、expired、超 execution-window expiry均无效。
- preflight 在 read-only `REPEATABLE READ` transaction 中只读取 durable scope、exact valid approval 与 latest complete observation set；stale/unknown/missing均 `DENY`。
- preflight只返回 eligibility fact；没有 order、intent、provider、worker、network 或其他 execution-side effect。

## Trading reachability

- production GateY-6D path未依赖 execution/provider/worker/network adapter；静态命中仅为否定性注释、safe response计数与 exact account/credential reference校验。
- task-created `ExecutionIntent/ExecutionReceipt=0/0`；credential material access=`0`；OKX call=`0`。
- `PLACE/CANCEL/TRANSFER/WITHDRAW=0/0/0/0`；worker/real-provider start=`0/0`；LIVE enable/kill disengage=`0/0`。
- `EXACT_PILOT_SCOPE=NOT_MATERIALIZED`；实际 session/scope/observation/approval=`0/0/0/0`；`FIRST_REAL_ORDER=NOT_AUTHORIZED`；`MICRO_LIVE=NOT_AUTHORIZED`；LIVE=`DISABLED`；kill switch=`ENGAGED`。

## Architecture hygiene

- API仅依赖 core application boundary；trusted observation port位于 core application；production NoReal实现与 JDBC/transaction实现位于 infra。
- `nq-api` 无 JDBC/SQL 或 infra implementation 依赖；adapter未直接写库。
- account/release/risk/marketdata/trading SoR未复制；resolver通过正式 repository/port重读 exact facts。
- `PackageBoundaryArchTest` 与 transaction single-main-caller guard通过，未发现明显 trust-boundary bypass。

## Validation

| Command / check | Result | Scope / environment / warnings |
| --- | --- | --- |
| GateY-6D controller/resolver/service focused | PASS（通过） | 20/20 modules；17 tests；failures/errors/skipped=`0/0/0`；含 global Jackson ignore 下 legacy fields rejection、production unavailable、trusted validation、IDOR 与 forged replay |
| `PackageBoundaryArchTest` focused | PASS（通过） | 23/23 modules；14 tests；failures/errors/skipped=`0/0/0`；API/command observation dependency 与 transaction single-main-caller guard通过 |
| required PostgreSQL V40 integration | PASS（通过） | disposable PostgreSQL 17.7；3/3、0 skipped；zero-partial=`0/0/0/0`、V39→V40、V1→V40、role-revoked replay与 lock-timeout rollback通过；expected timeout=`5063ms` |
| GateY-6C focused authorization/permission regression | PASS（通过） | core/infra/scheduler/API/app=`16/8/2/7/10`，共43 tests；failures/errors/skipped=`0/0/0` |
| GateY-4 deployment boundary | PASS（通过） | 6/6：delegate-release/linux-root/identity/no-start/no-secret/no-network |
| GateY-2 regression | PASS（通过） | required PostgreSQL suite 的 GateY-2 fact-model/repository/concurrency path与 full backend均通过 |
| `mvn -f backend/pom.xml test` | PASS（通过） | 23/23 modules；1515 tests；failures/errors/skipped=`0/0/47`；52.737s |
| static trust/reachability/diff | PASS（通过） | staged=`0`；operator observation input=0；production trusted implementation=1且 fixed unavailable；arbitrary refresh=0；migration/frontend/research/scripts/deploy/CI diff=0 |

47 个 skip 为既有 conditional/manual integration；required PostgreSQL 已单独 3/3 实跑。Mockito dynamic-agent、SLF4J NOP、deprecation/unchecked、expected error-path stack trace 与 LF→CRLF 提示为非阻断 warning。一次性 PostgreSQL 容器已停止并自动删除；未连接本机 5432、生产数据库或真实数据。

## Findings

### P0

- 无。

### P1

- 无。attempt-01 `UNTRUSTED_PREREQUISITE_OBSERVATION_AUTHORITY` 已关闭。
- `PREFLIGHT_CROSS_ACCOUNT_IDOR`、`MATERIALIZATION_REPLAY_ROLE_REVOCATION_BYPASS`、`APPROVAL_UNAUTHORIZED_RESOURCE_PROBE` 均保持关闭。

### P2

- 无。

### P3

- 无。

## Authority after、回滚与下一步

```text
work_batch=GateY-6D
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-6D-COMMIT-AND-PUSH

EXACT_PILOT_SCOPE=NOT_MATERIALIZED
FIRST_REAL_ORDER=NOT_AUTHORIZED
MICRO_LIVE=NOT_AUTHORIZED
LIVE=DISABLED
kill_switch=ENGAGED
```

- 本轮未 stage、commit、push、deploy；commit/push 与 exact-head CI 属于下一独立动作。
- rollback：按本 GateY-6D exact changed-file allowlist逐文件反向应用未提交 diff；不得 `reset --hard`、restore/checkout整个 worktree或覆盖用户已有变更。仅回滚本 review 文档时，可删除本 attempt-02 并反向 patch本轮 current authority追加/替换。
- 建议 commit：`feat(gatey): implement exact pilot scope materialization`。

## Exact changed files / git add allowlist

Review通过后的 exact allowlist 为：

```powershell
git add -- `
  README.md `
  backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/ApiExceptionHandler.java `
  backend/nq-api/src/main/java/com/guidinglight/nexusquant/livecontrol/api/PilotScopeApprovalRequest.java `
  backend/nq-api/src/main/java/com/guidinglight/nexusquant/livecontrol/api/PilotScopeControlPlaneController.java `
  backend/nq-api/src/main/java/com/guidinglight/nexusquant/livecontrol/api/PilotScopeMaterializationRequest.java `
  backend/nq-api/src/main/java/com/guidinglight/nexusquant/livecontrol/api/PilotScopeMaterializationResponse.java `
  backend/nq-api/src/test/java/com/guidinglight/nexusquant/livecontrol/api/PilotScopeControlPlaneControllerTest.java `
  backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/PackageBoundaryArchTest.java `
  backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/LiveSessionFactModelPostgresIntegrationTest.java `
  backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotPrerequisiteObservationAuthority.java `
  backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotScopeApprovalCommand.java `
  backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotScopeAuthorityResolver.java `
  backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotScopeControlPlane.java `
  backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotScopeMaterializationCommand.java `
  backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotScopeMaterializationResult.java `
  backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/JdbcPilotScopeAuthorityResolver.java `
  backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/PilotScopeControlPlaneService.java `
  backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/PilotScopeFactTransactionService.java `
  backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/UnavailablePilotPrerequisiteObservationAuthority.java `
  backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/JdbcPilotScopeAuthorityResolverTest.java `
  backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/PilotScopeControlPlaneServiceTest.java `
  docs/current/README.md `
  docs/current/ROADMAP.md `
  docs/current/STATUS.md `
  docs/current/TESTING.md `
  docs/current/WORKLOG.md `
  docs/current/evidence/gate-y/README.md `
  docs/current/evidence/gate-y/NQ-GATEY-6D-EXACT-PILOT-SCOPE-PREREQUISITE-MATERIALIZATION-IMPLEMENTATION.attempt-01.md `
  docs/current/evidence/gate-y/NQ-GATEY-6D-EXACT-PILOT-SCOPE-PREREQUISITE-MATERIALIZATION-SECURITY-REVIEW.attempt-01.md `
  docs/current/evidence/gate-y/NQ-GATEY-6D-EXACT-PILOT-SCOPE-PREREQUISITE-MATERIALIZATION-SECURITY-REVIEW.attempt-02.md `
  docs/current/evidence/gate-y/NQ-GATEY-6D-TRUSTED-PREREQUISITE-OBSERVATION-AUTHORITY-REMEDIATION.attempt-01.md
```
