# GateY-6F Exact Pilot Binding Implementation — attempt-01

## 任务分类与结论

- Task classification：`CODE_CHANGE / SECURITY_SENSITIVE_IMPLEMENTATION / EXACT_PILOT_BINDING / AUTHORIZATION_BOUNDARY / TESTS`；NQ-only、L 级。
- 主 skill：`java-backend-maintenance`；`nq-dh-workflow-router` 先固定 current Authority 与禁止边界，`nq-java-engineering-standard`、`java-backend-regression-tests` 约束实现和验证，`nq-docs-writer` 只负责本 evidence 与指定 ledger。
- 结论：`IMPLEMENTED / GATEY_6F_EXACT_PILOT_BINDING_COMPLETE / EXACT_RELEASE_BOUND / EXACT_SERVER_BOUND / EXACT_ACCOUNT_BOUND / EXACT_CREDENTIAL_REFERENCE_BOUND / EXACT_INSTRUMENT_BOUND / EXACT_ORDER_ENVELOPE_BOUND / AUTHORITATIVE_FACT_DRIFT_FAIL_CLOSED / ONE_TIME_BINDING_ENFORCED / NO_PROVIDER_IO / NO_ORDER_MUTATION / P0_0 / P1_0 / SELF_REVIEWED / READY_TO_COMMIT`（已实现 / 自审完成 / 可进入提交前复核）。

## Baseline 与 Authority

```text
branch=dev
initial_worktree=clean
HEAD=origin/dev=d278a2e0864df11d5fd8c46fea27f705a119025b
deployment_commit=2cee199081bc338b4dd5c05d2aff867b7a418202
accepted_batch=GateY-6E
work_batch=GateY-6F
work_batch_status=NOT_STARTED
live=DISABLED
kill_switch=ENGAGED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
```

本 implementation evidence 不修改 machine Authority。`binding created/valid/consumed` 均不等于 pilot authorized、PLACE allowed、first real order authorized 或 LIVE enabled。

## Persistence 判断

- 未新增或修改 migration；V1～V41 保持原样。
- V40 `pilot_scope_bindings` 与四类 `pilot_prerequisite_observations` 继续作为 immutable scope/snapshot SoR。
- V39 `live_session_events` 已提供 session 行锁、有序 sequence、append-only、request/trace/idempotency、canonical payload hash 与 8 KiB 脱敏 metadata，因此可承载 `VERIFIED`/`CONSUMED` durable lifecycle，无需第二张表。
- `JdbcExactPilotBindingRepository` 在 session 行锁内保证每 session 只能创建一份 exact binding；消费事件最多一条，第二次使用固定拒绝。
- JDBC adapter 不查询或写入 `execution_intents`、`execution_receipts`、`orders`、ledger 表。

结论：不触发 `BLOCKED / MIGRATION_REVIEW_REQUIRED`。

## 实现合同

### Immutable facts 与 canonical digest

`ExactPilotBinding` 精确绑定：

- binding/session/pilot scope/observation set identity；
- `sourceCommit/releaseId/manifestSha256/serverIdentity/runtimeProfile`；
- `OKX/LIVE/ownerId/exchangeAccountId/credentialReferenceId`；
- 单一 internal instrument、单一 exchange instrument、单一 side、`LIMIT`-only、exact price/quantity/notional；
- instrument/fee/balance/exchange-time 四个现有 observation UUID；
- `riskLimitSetId/riskPolicyVersion/riskPolicyDigest/killSwitchState=ENGAGED`；
- pilot window、request/trace/idempotency、server transaction created/expiry time。

`exact-pilot-binding.v1` 使用固定字段顺序、UTF-8、`BigDecimal(38,8)` plain string、UTC microsecond timestamp 与 lowercase SHA-256。Digest 只由 server-resolved domain facts 计算；API/command 不接受 caller digest，不使用 `Map.toString()`、locale number formatting 或 presentation DTO hash。

### Authority 与 fail-closed

- `StoredFactExactPilotBindingAuthority` 只读取 account/credential summary、release admission、pilot scope、latest complete observation set、valid independent approval、risk/kill、本地 instrument catalog 与 immutable runtime identity。
- credential 只读取 opaque reference summary；没有 material/decrypt 方法调用。
- create/validate/consume 使用 `SERIALIZABLE` 短事务；消费前重新解析 current facts。
- release/manifest/server/profile、account/credential、instrument/order、四类 snapshot、risk、kill、pilot window 任一漂移均返回 `INVALID` 或拒绝消费；不自动重绑。
- exact order 同时受 tick/lot/minimum size、published minimum value、risk caps 与 session capital cap 约束。
- capability 由 `nq.live-control.exact-pilot-binding.enabled=true` 显式启用，且必须提供 server-owned manifest SHA-256 与 server identity；默认不装配，不改变已接受只读 deployment runtime。

### Lifecycle 与非授权边界

```text
VERIFIED -> CONSUMED
VERIFIED -> EXPIRED（按当前事务时间计算）
任一 digest/authority drift -> INVALID
```

状态集中不存在 `AUTHORIZED/EXECUTING/FILLED/LIVE`。Validation 与 consumption result 强制 `tradingAuthorized=false`；consumption 同时强制 `exchangeMutation=false`。没有 Controller、PLACE/CANCEL、provider、worker 或 execution DTO 依赖。

## 测试覆盖

新增 11 个 focused tests：

- canonical 字段顺序、UTF-8、numeric/timestamp、tamper、wildcard、short-lived、LIMIT-only 与无 authorization lifecycle；
- happy path、scope expansion、wrong release/manifest/server/profile/account/credential/instrument/side、price/quantity/notional、四类 observation drift、risk id/version/digest、kill disengaged；
- expired、digest tamper、idempotent replay conflict、duplicate consumption/second-use rejection；
- V39 event metadata round-trip、tampered stored canonical rejection、VERIFIED/CONSUMED durable event；
- production Authority 只调用 credential summary，`verifyNoMoreInteractions` 证明 material/decrypt path 未进入。

测试固定断言 binding creation/validation/consumption 的 credential material reads、provider calls、orders 与 ledger delta 为 0；control-plane dependency reflection 不含 execution/provider/order/ledger mutation owner。

## Validation

| Command / check | Result |
| --- | --- |
| focused Maven（core/infra/app） | PASS（通过）；11 tests，failures/errors/skipped=`0/0/0` |
| `mvn -f backend/pom.xml test` | PASS（通过）；23 modules、1563 tests、failures/errors/skipped=`0/0/48`；首次因新 `@Repository final` CGLIB proxy 失败，移除 `final` 后受影响 context 3/3 与全量最终重跑通过 |
| GateY canonical regressions | PASS（通过）；deployment boundary、release 29 cases、runtime deployment 51 cases、GateY5 lock/post-restore |
| GateW frozen regressions | PASS（通过）；soak remediation 37、security 12、release reproducibility 34 cases |
| Java governance | PASS（通过）；platform release 21 / Spring Boot 3.5.10；Shadow=`VIOLATION_FOUND` 仅 existing 144 + ruleset expansion 14，new-code=0 |
| current Authority | PASS（通过）；schema=3，errors=0 |
| migration boundary | PASS（通过）；V40 git-blob contract PASS；migration diff=0，V41 current |
| `git diff --check` / scoped whitespace | PASS（通过）；无 whitespace error |

执行历史如实保留：首次 focused Maven 因 PowerShell 未引用 dotted `-D` 参数而未进入编译；首次 full Maven 暴露 Spring CGLIB proxy RCA 后已最小修复；主 worktree Shadow 因既有无权访问 pip artifact 在 scope filter 前阻断，随后在 detached 临时 worktree 对同一 18 个 Java 变更运行官方 checker并得到 new-code=0，临时 worktree已移除。

## Exact changed files

```text
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ExactPilotBindingConfiguration.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/ExactPilotRuntimeIdentity.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/livecontrol/StoredFactExactPilotBindingAuthority.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/livecontrol/StoredFactExactPilotBindingAuthorityTest.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/ExactPilotBindingAuthority.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/ExactPilotBindingCommand.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/ExactPilotBindingConsumption.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/ExactPilotBindingConsumptionCommand.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/ExactPilotBindingControlPlane.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/ExactPilotBindingValidation.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/ExactPilotBinding.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/ExactPilotBindingCanonicalEncoder.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/port/ExactPilotBindingRepository.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/domain/ExactPilotBindingCanonicalEncoderTest.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/ExactPilotBindingService.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcExactPilotBindingRepository.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/ExactPilotBindingServiceTest.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcExactPilotBindingRepositoryTest.java
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/evidence/gate-y/README.md
docs/current/evidence/gate-y/NQ-GATEY-6F-EXACT-PILOT-BINDING-IMPLEMENTATION.attempt-01.md
```

## Boundary、自审、回滚与下一步

- P0/P1/P2/P3=`0/0/0/0`。
- 未读取 credential material，未调用真实 OKX，未执行 permission probe，未创建 PilotScope/approval/ExecutionIntent/Receipt/order/ledger fact，未 PLACE/CANCEL，未 enable LIVE，未 disengage kill，未访问服务器。
- 未 stage/commit/push/tag/deploy。回滚只需反向应用本 evidence 的 exact changed-file allowlist；V1～V41 与已接受 deployment evidence 无变更。
- 建议 commit message：`feat(gatey): 实现精确pilot binding合同`。
- 下一动作唯一为 `NQ-GATEY-6F-EXACT-PILOT-ATTEMPT-04`；该动作仍需显式 operator-controlled exact values 与独立授权，不能从本 binding implementation 推导交易许可。
