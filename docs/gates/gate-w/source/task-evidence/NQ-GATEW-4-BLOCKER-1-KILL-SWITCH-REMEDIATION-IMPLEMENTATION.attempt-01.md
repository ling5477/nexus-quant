# NQ-GATEW-4 Blocker-1 Kill Switch Remediation Implementation Attempt 01

## Task classification

`SECURITY_REMEDIATION / CODE_CHANGE / DURABLE_KILL_SWITCH / STOP_PROPAGATION / POSTGRESQL_TEST`。

## Original blocker

`KILL_SWITCH_DURABILITY_NOT_PROVEN`：原实现使用默认未熔断的进程内 `AtomicBoolean(false)`，重启丢失，公开 `disable()`，且 GateW private probe 未接入 stop control。

原始 BLOCKED evidence 保留：`NQ-GATEW-4-SECURITY-OPERATIONS-REVIEW.attempt-01.md`；本轮前后 SHA-256 均为 `4601F0C57286CF1B77CC55FED955374EE27BBFBE6B72013213A41D404B4529A2`，没有修改其失败结论。

## Implementation

### State and persistence

- 新增 immutable `KillSwitchState` / `KillSwitchSnapshot`、`KillSwitchStatus`、`KillSwitchScope`、engage-only command 与 repository port。
- V35 新增 `kill_switch_states` 与 `kill_switch_events`；所有表/字段均有中文业务注释，状态/version/非空文本有 CHECK 约束。
- `GLOBAL_TRADING` 默认 seed 为 `ENGAGED / version=1`；seed 同时产生 version 1 event。
- current state 使用 row lock + optimistic version 条件更新；事件以 `UNIQUE(scope,state_version)` 去重，FK `ON DELETE RESTRICT`，无 cascade delete。
- `KillSwitchService` 使用 injected `Clock`；missing、repository/mapping error、非法 status、缺失 timestamp、未来 timestamp 均返回 `UNKNOWN/BLOCKED`。
- production mutation surface 只有 engage；重复 engage 返回同一 state，不新增事件；无 release/disengage command。

### Stop propagation

- `KillSwitchRiskRule` 改读 durable snapshot：ENGAGED/UNKNOWN/error/missing 均 hard reject，DISENGAGED 仅通过该规则。
- `OkxPrivateReadonlyProbeService` 在 account lookup 与 credential executor 前读取 snapshot；ENGAGED 返回 `KILL_SWITCH_ENGAGED`，其他 fail-closed 状态返回 `KILL_SWITCH_STATE_UNKNOWN`。
- spy/fail-on-access fixture 证明 ENGAGED、missing/UNKNOWN、repository failure 时 account access=0、credential callback=0、transport operation=0。
- `TradingRuntimeConfiguration` 只用 `KillSwitchStateRepository + Clock.systemUTC()` 装配 production service；不存在 `new KillSwitchService()` 或 in-memory/noop fallback。

## Files created

- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchScope.java`
- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchStatus.java`
- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchState.java`
- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchSnapshot.java`
- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchEngageCommand.java`
- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchStateRepository.java`
- `backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchVersionConflictException.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/risk/infra/jdbc/JdbcKillSwitchStateRepository.java`
- `backend/nq-infra/src/main/resources/db/migration/V35__gate_w4_durable_kill_switch.sql`
- `backend/nq-risk/src/test/java/com/guidinglight/nexusquant/risk/service/KillSwitchServiceTest.java`
- `backend/nq-risk/src/test/java/com/guidinglight/nexusquant/risk/service/KillSwitchRiskRuleTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/KillSwitchStateMigrationContractTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/risk/KillSwitchRestartDurabilityPostgresIntegrationTest.java`

## Files changed

- `KillSwitchService.java`、`KillSwitchRiskRule.java`。
- `TradingRuntimeConfiguration.java`、`GateWOkxPrivateReadonlyConfiguration.java`。
- `OkxPrivateReadonlyProbeService.java`。
- `PreTradeRiskServiceTest.java`、`OkxPrivateReadonlyProbeServiceTest.java`、`GateWOkxPrivateReadonlyConfigurationTest.java`。
- 本轮 evidence index、`TESTING.md`、`WORKLOG.md`；未改 `STATUS.md` 或 `ROADMAP.md`。

## Validation

- Required targeted：`mvn -f backend/pom.xml -pl nq-risk,nq-core,nq-infra,nq-app -am test`，23/23 modules `SUCCESS`，`BUILD SUCCESS`；`nq-risk 11 tests / 0 failures / 0 errors`。
- Fresh PostgreSQL：Docker 29.6.1 + 本地已有 `postgres:16-alpine`，随机 disposable schema，Flyway V1→V35 共 35 migrations 成功。
- Restart：两个独立 Spring context 使用同一数据库；第二个 context 仍读到 ENGAGED，version/updatedAt/event count 可追溯；1 test / 0 failures / 0 errors / 0 skipped。
- Repository：optimistic lock success、version mismatch、engage idempotency、event append 均通过。
- Full Maven：`mvn -f backend/pom.xml test`，23/23 modules `SUCCESS`，`BUILD SUCCESS`。
- 首次 targeted 暴露 `updatedAt=null` 产生不稳定 NPE；构造器已最小修为稳定 `IllegalArgumentException`，随后 targeted/full/real PostgreSQL 均通过。

Environment：`CI=true / NQ_NO_OUTBOUND=true / NQ_AI_ENABLED=false / NQ_DH_RUNTIME_ENABLED=false / NQ_REAL_EXCHANGE_ENABLED=false`。未访问 OKX、未读取真实 credential。

## Known limitations

- 本任务故意不提供 DISENGAGED/release workflow；未来只能在独立 human-review Gate 中设计。
- `DISENGAGED` 仅表示该 stop control 可继续下一只读检查，不是 `TRADE_AUTHORIZED`、`LIVE_READY` 或 `ORDER_APPROVED`。
- exact-head CI 在 commit/push 前为 `PENDING`，不得在本文写成 CI green。

## Rollback

代码回滚使用后续 revert commit；V35 一旦执行不得删除或改写，安全状态/event 表必须保留。回滚到旧应用会重新暴露原 durability blocker，因此只能作为受控止损并保持 LIVE disabled，不能视为安全恢复完成。

## Result

`IMPLEMENTED / LOCALLY_VALIDATED / PENDING_CONFORMANCE_AND_EXACT_HEAD_CI`（已实现 / 本地验证通过 / 等待符合性复核与 exact-head CI）。
