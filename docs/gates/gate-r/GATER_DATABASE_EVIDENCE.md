# GateR Database Evidence

## Scope

- 说明对象：GateR 的影子运行本地事实模型和数据库边界。
- 与前文约束一致：只读归档，不新增交易写入，仍为 diagnostics local fact。

## Migration and tables

- Migration：`backend/nq-infra/src/main/resources/db/migration/V32__gate_r_shadow_run_fact_model.sql`。
- 本轮新增四表：
  - `shadow_runs`
  - `shadow_run_events`
  - `shadow_run_snapshots`
  - `shadow_consistency_reports`

## Domain evidence

- Domain repository 与实现：
  - `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/ShadowRunFactRepository.java`
  - `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcShadowRunFactRepository.java`
  - `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcShadowRunIllegalTransitionAuditWriter.java`
- 迁移契约测试：
  - `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/ShadowRunFactModelMigrationContractTest.java`
- 状态机与 audit（非法流转）：
  - `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/shadowrun/ShadowRunStateMachine.java`
  - `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/domain/shadowrun/ShadowRunStateMachineTest.java`
  - `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcShadowRunIllegalTransitionAuditWriterTest.java`

## Assertions and smoke boundary

- 本地 fact model 全流程仅用于本地 facts 与 no-side-effect 报告，不包含任何交易/账户/账本写入。
- CI 中 PostgreSQL / Flyway smoke 成功：未见 schema 与迁移失败。
