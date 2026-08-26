# GateY-6D trusted prerequisite observation authority remediation — attempt-01

## 任务与结论

- task：`NQ-GATEY-6D-TRUSTED-PREREQUISITE-OBSERVATION-AUTHORITY-REMEDIATION`。
- 分类：NQ-only / `CODE_CHANGE` / P1 security remediation / trust boundary；风险等级 L。
- 结论：`PASS / GATEY_6D_TRUSTED_OBSERVATION_AUTHORITY_REMEDIATED / OPERATOR_OBSERVATION_AUTHORITY_REMOVED / PRODUCTION_FAIL_CLOSED / POST_APPROVAL_FORGED_REFRESH_DENIED / P1_REMEDIATED_PENDING_REVIEW / EXECUTION_INTENT_0 / OKX_CALL_0 / EXCHANGE_MUTATION_0 / LIVE_DISABLED / READY_FOR_SECURITY_REVIEW_ATTEMPT_02`（通过 / P1 已整改并待独立复审）。
- 本文只记录 remediation evidence，不是 Security Review attempt-02；attempt-01 的拒绝历史保留，只有后续同名 Security Review attempt-02 得出 P0/P1=`0/0` 后才可推进 lifecycle。

## Baseline 与 authority

- repository=`E:\Project\nexus-quant`；branch=`dev`。
- `HEAD == origin/dev == bc35edb60370aee367ab40853201e1f249179b83`。
- 起始 dirty/staged=`26/0`；均属于既有 GateY-6D implementation/review，未 reset、restore、checkout、stage、commit 或 push。
- machine authority before/after 保持：

```text
work_batch=GateY-6D
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-6D-EXACT-PILOT-SCOPE-PREREQUISITE-MATERIALIZATION-SECURITY-REVIEW
LIVE=DISABLED
kill_switch=ENGAGED
```

## Open P1 root cause

Security Review attempt-01 发现 API/operator 可把 instrument、fee、available balance、clock skew、observation identity/digest、source 与 observed time 作为动态 prerequisite facts 提交。旧 control-plane 直接把这些 operator values canonicalize 后写入 V40，approval 后又能通过普通 refresh/materialization replay 追加新的 complete set，导致伪造 balance/skew/fee 可能成为 durable eligible preflight。根因是 operator intent 与 system observation authority 未分离。

本次建立并落实唯一规则：

> Operator 只能声明 pilot scope 意图，不能声明系统 observation 事实。

## Remediation

### API 与 command 收紧

`PilotScopeMaterializationRequest` 与 `PilotScopeMaterializationCommand` 已移除：

- `observationSetId`、`scopeBindings`、`observations`；
- instrument tick/lot/minimum size/minimum value 与动态 item facts；
- `makerFeeRate`、`takerFeeRate`、fee evidence values；
- `availableBalance`、`observedSkewMs`、`observedAt`；
- `observationIdentity`、`observationPayloadHash` 及 caller-supplied source/digest facts。

operator 仅保留 exact account/credential reference、release/risk selection、symbol/capital/window 与 expected scope hash 等 intent。DTO 使用 `@JsonAnySetter` 主动拒绝旧字段；测试证明即使全局 Jackson 配置关闭 `FAIL_ON_UNKNOWN_PROPERTIES`，旧 payload 也不会静默通过。

### Trusted observation port 与 production fail-closed

- core 新增 `PilotPrerequisiteObservationAuthority`，唯一入参为 server 构造的 `LiveSession`、`PilotScopeBinding` 与 `resolvedAt`；caller 没有 observation value 参数。
- `JdbcPilotScopeAuthorityResolver` 从 account/credential/release/risk SoR 与 server-owned runtime properties 重读 exact authority，并返回 immutable `ResolvedScopeBindings`；missing、malformed 或 `latest/current/HEAD` drifting configuration 均 fail closed。
- production 唯一实现为 `UnavailablePilotPrerequisiteObservationAuthority`；在真实 trusted source 尚未实现时固定返回 `TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE`，API 映射 HTTP 503。production 没有 mock、fixture、static balance、hardcoded skew、真实 OKX client 或 credential access。
- test-only nested `DeterministicObservationAuthority` 明确不是 Spring bean，只用于构造可复现的完整 observation set。

### Materialization、refresh 与 same-authority proof

生产链路现在固定为：

```text
authenticated operator intent
→ JdbcPilotScopeAuthorityResolver 重读 SoR + server-owned scope bindings
→ server 构造 immutable LiveSession/PilotScopeBinding
→ PilotPrerequisiteObservationAuthority
→ source/schema/recorder/time/symbol/canonical/freshness 复核
→ PilotScopeFactTransactionService.materialize(...)
```

服务端在 transaction 前复核四类 observation 的 `recordedAt == resolvedAt`、`observedAt <= resolvedAt`、recorder exact worker identity、source identity/schema exact scope、instrument symbols exact session、canonical payload hash、instrument digest，以及 stale/fee class/clock skew/scope mismatch。value、identity、observedAt、source 与 canonical payload hash均来自同一个 trusted authority 返回对象；domain constructor再次重算 canonical hash。

`PilotScopeFactTransactionService.refresh(scope, observations)` arbitrary primitive 已删除。main source 中该 transaction service 的唯一 caller 由 source/ArchUnit guard 固定为 `PilotScopeControlPlaneService`。approval 后 replay 仍重新调用 trusted authority；sequenced attack test 在第一次 valid materialization 与独立 approval 后返回 forged balance、skew、fee、identity/source，control-plane 在 transaction 前拒绝，materialize invocation 保持 1，未追加第二个 observation set。

## Atomicity 与 attack regression

- production unavailable unit path：`TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE`，transaction invocation=`0`。
- disposable PostgreSQL 17.7 真实 JDBC path：对 exact random IDs 断言 `LiveSession=0 / PilotScope=0 / Observation=0 / Approval=0`。
- invalid trusted result：source、schema、recorder、future time、stale balance、wrong symbol 全部返回 `TRUSTED_PREREQUISITE_OBSERVATION_INVALID`，transaction invocation=`0`。
- existing IDOR/role/self-approval 边界保持：preflight owner binding、role-before-lookup、materialization replay current-role check 与 exact approval scope tests 均通过。

## Validation

| Command / check | Result | Scope / environment / warnings |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-api,nq-infra -am "-Dtest=PilotScopeControlPlaneControllerTest,JdbcPilotScopeAuthorityResolverTest,PilotScopeControlPlaneServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS（通过） | 20/20 modules；17 tests，failures/errors/skipped=`0/0/0` |
| `PackageBoundaryArchTest` focused | PASS（通过） | 10 tests；DTO/command observation dependency 与 transaction single-main-caller guard通过 |
| `LiveSessionFactModelPostgresIntegrationTest` required PostgreSQL | PASS（通过） | disposable PostgreSQL 17.7；3/3，0 skipped；V39→V40、V1→V40、lock-timeout rollback、zero-partial path通过 |
| GateY-6C focused | PASS（通过） | core/adapter/infra/app=`20/2/8/25`，共55 tests，failures/errors/skipped=`0/0/0` |
| `scripts/gatey/tests/run-gatey4-deployment-boundary-regression.ps1` | PASS（通过） | 6/6：delegate-release/linux-root/identity/no-start/no-secret/no-network |
| `mvn -f backend/pom.xml test` | PASS（通过） | 23/23 modules；1515 tests，failures/errors/skipped=`0/0/47`；51.134s |
| static trust/reachability/diff | PASS（通过） | operator authority field matches=0；production trusted implementation=1；refresh primitive=0；migration/frontend/research/scripts/deploy/CI diff=0；staged=0 |

标准 full backend 的 47 个 skip 为既有 conditional/manual integration；required PostgreSQL 已单独 3/3 实跑。Mockito dynamic-agent、SLF4J NOP、deprecation/unchecked 与 LF→CRLF 提示为既有非阻断 warning。Docker image仅用于本地 disposable PostgreSQL；容器已停止并自动删除，未连接本机既有数据库或生产数据。

## Trading 与安全边界

- 本任务 credential material access=`0`、authenticated OKX call=`0`、exchange mutation=`0`。
- `ExecutionIntent/ExecutionReceipt` creation=`0/0`；PLACE/CANCEL/TRANSFER/WITHDRAW=`0/0/0/0`。
- provider/worker start=`0/0`；LIVE enable/kill disengage=`0/0`。
- `EXACT_PILOT_SCOPE=NOT_MATERIALIZED`；实际 session/scope/observation/approval 行=`0/0/0/0`；preflight=`NOT_RUN_NO_SCOPE`。
- V40 与其他 migration/schema、frontend、research、scripts、deploy、`.github` 均未修改。

## Findings

- P0：0。
- P1：0 个 remediation-self-check open；`UNTRUSTED_PREREQUISITE_OBSERVATION_AUTHORITY` 已整改，状态为 `P1_REMEDIATED_PENDING_REVIEW`，不能替代独立 Security Review attempt-02。
- P2：0。
- P3：0。

## Changed files

### Production

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/ApiExceptionHandler.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/livecontrol/api/PilotScopeMaterializationRequest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotPrerequisiteObservationAuthority.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotScopeAuthorityResolver.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/PilotScopeMaterializationCommand.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/JdbcPilotScopeAuthorityResolver.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/PilotScopeControlPlaneService.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/PilotScopeFactTransactionService.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/UnavailablePilotPrerequisiteObservationAuthority.java`

### Tests / evidence

- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/livecontrol/api/PilotScopeControlPlaneControllerTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/JdbcPilotScopeAuthorityResolverTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/PilotScopeControlPlaneServiceTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/PackageBoundaryArchTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/livecontrol/LiveSessionFactModelPostgresIntegrationTest.java`
- 本 evidence、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、`docs/current/evidence/gate-y/README.md` 与 `docs/current/STATUS.md` 的最小当前事实同步。

## Rollback 与 next action

- rollback：在未提交状态下按上述 remediation 文件逐文件反向应用本次 diff；不得 reset/restore/checkout 覆盖同一 worktree 中既有 GateY-6D implementation/review 变更。
- next：直接重跑原任务 `NQ-GATEY-6D-EXACT-PILOT-SCOPE-PREREQUISITE-MATERIALIZATION-SECURITY-REVIEW`，新增 `...SECURITY-REVIEW.attempt-02.md`，保留 attempt-01；本 remediation 不 stage/commit/push，不进入 GateY-6E。
