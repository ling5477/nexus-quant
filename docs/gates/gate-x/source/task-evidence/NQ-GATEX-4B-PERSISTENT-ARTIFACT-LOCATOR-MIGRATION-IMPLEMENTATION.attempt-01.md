# NQ-GATEX-4B Persistent Artifact Locator Migration Implementation — Attempt 01

## Task classification

- 归属：NQ-only。
- 类型：`FLYWAY_MIGRATION / RELEASE_ARTIFACT_LOCATOR_PERSISTENCE / PUBLISH_FACT_EVOLUTION / JDBC_MAPPING / IMMUTABILITY_ENFORCEMENT / POSTGRESQL_REGRESSION`。
- 风险：L 级高风险数据库变更；本轮仅允许 `IMPLEMENTED / PENDING_REVIEW`，必须独立 migration review 后才可进入提交动作。

## Execution status

```text
IMPLEMENTED /
PERSISTENT_ARTIFACT_LOCATOR_SCHEMA_COMPLETE /
POSTGRESQL_REGRESSION_GREEN /
PENDING_INDEPENDENT_MIGRATION_REVIEW
```

未 commit、未 push、远端 CI 未运行；GateX-4 API/UI 保持阻断，未初始化 GateX-4C。

## Starting baseline

- branch：`dev`。
- starting HEAD：`5f4824eecaac5cffbbc314fb8f767bd6ba45c29f`。
- `origin/dev` HEAD：`5f4824eecaac5cffbbc314fb8f767bd6ba45c29f`。
- authority before：`GateX-4B / NOT_STARTED / NONE / NOT_RUN`。
- next action before：`NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-IMPLEMENTATION`。
- LIVE before/after：`DISABLED`。

允许并原样保留的 10 个 staged baseline：

1. `README.md`
2. `docs/current/README.md`
3. `docs/current/ROADMAP.md`
4. `docs/current/STATUS.md`
5. `docs/current/TESTING.md`
6. `docs/current/WORKLOG.md`
7. `docs/current/evidence/gate-x/NQ-GATEX-4-MINIMAL-API-UI-CLOSURE-IMPLEMENTATION.attempt-01.md`
8. `docs/current/evidence/gate-x/NQ-GATEX-4-SAFE-ARTIFACT-ROOT-BINDING-IMPLEMENTATION.attempt-01.md`
9. `docs/current/evidence/gate-x/NQ-GATEX-4-PERSISTENT-ARTIFACT-LOCATOR-REQUIRED-BLOCKED.attempt-01.md`
10. `docs/current/evidence/gate-x/NQ-GATEX-4A-PERSISTENT-ARTIFACT-LOCATOR-SCHEMA-REVIEW.attempt-01.md`

## Schema audit and migration

- 实施前最高 Flyway version：V36。
- 新 migration：`V37__gate_x4b_persistent_artifact_locator.sql`。
- V1-V36 未修改。
- 目标表：`backtest_publish_records`。
- 新列：`artifact_storage_key VARCHAR(128) NULL`、`manifest_storage_key VARCHAR(128) NULL`。

### Pair invariant

两个 key 必须同时 `NULL` 或同时 non-NULL。Java value object、record constructor 与数据库 CHECK 使用同一 pair invariant。

### Storage-key syntax

非空 key 必须匹配：

```regex
^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$
```

同时拒绝任意 `..`。slash、backslash、colon、absolute path、URI、空串和 129 字符均被拒绝。key 是 server-owned opaque identifier，不是 filesystem path、trusted root、digest、publish ID 或客户端参数。

### Partial UNIQUE decision

```text
DO NOT ADD UNIQUE
```

4A 的建议在实施前已按任务要求重新判断。当前代码与 provider/storage contract 不能证明“一个 storage key 永远唯一属于一个 publish release”的正式 invariant；强加 UNIQUE 会引入未获业务事实支持的表扫描和写约束。PostgreSQL 回归显式证明当前允许两个不同 publish release 使用相同 pair。未来若 provider contract 冻结全局独占语义，必须另开 forward migration 与独立审查。

### Trigger and immutability decision

使用 database trigger，因为 Java/repository 不能防止直接 SQL 误写：

- 允许唯一一次 `FAILED + NULL/NULL → SUCCEEDED + valid pair`。
- 已绑定 pair 的 rebind 和 clear 均以 SQLSTATE `23514` 拒绝。
- legacy `SUCCEEDED + NULL/NULL` 不能后补绑定，避免把历史猜测提升为事实。
- repository 不提供 `updateArtifactLocator()` / `rebindArtifact()` / PATCH contract。
- JDBC conflict update 对同 pair 幂等，对不同 pair fail-closed，既有值不被覆盖。

Trigger/function 是 metadata DDL，但从创建后立即影响 update；实际部署仍需受控窗口和停止条件。

### Backfill and legacy decision

```text
NO BACKFILL
NO UPDATE
NO GUESSED LOCATOR
```

历史 `NULL/NULL` 保持可读并派生为 `LEGACY_ARTIFACT_UNBOUND`。不得从 publish ID、digest、本地目录、cwd、临时目录或测试 layout 猜测 key；后续 resolver/API/admission 必须对该状态返回 `UNAVAILABLE / BLOCKED`，不得 VERIFY。

## Domain, repository and publish write path

- `BacktestPublishArtifactLocator`：封装 nullable pair、格式校验与 bound/unbound 状态。
- `BacktestPublishRecord`：持久化两个 key，并提供 `LEGACY_ARTIFACT_UNBOUND / PERSISTENT_ARTIFACT_BOUND` 派生语义；保留旧构造器兼容既有调用方。
- `BacktestPublishRecordRepository`：只保留原子 `upsert` contract，并在 port 注释中固定首次绑定/幂等/fail-closed 语义。
- `JdbcBacktestPublishRecordRepository`：INSERT/SELECT/mapper 增加两列；conflict update 只允许旧值为 NULL 或与输入相同，返回行数为 0 时抛出冲突，不静默覆盖。
- `BacktestPublishService`：HTTP request 未增加 path/key 字段；普通 `publish(...)` 使用 unbound locator。新增内部 typed `publishWithArtifactLocator(...)`，仅供未来受控 artifact pipeline 调用。

Artifact producer status：

```text
PERSISTENCE_READY / PRODUCER_NOT_YET_CONNECTED
```

本轮未伪造 producer、未从 publish ID/digest/path 推导 production key。

## PostgreSQL regression

测试环境：localhost-only、无持久卷、`--rm` disposable PostgreSQL 17.7；只创建和删除随机 `gatex4b_*` schema。未连接生产数据库。

### Fresh result

- fresh `V1→V37`：PASS（通过）。
- `Flyway.validate`：PASS（通过）。
- valid pair write/read、JDBC idempotent replay、conflicting pair fail-closed：PASS（通过）。
- 不增加 UNIQUE 时跨 release duplicate behavior：PASS（通过，重复 pair 被允许）。

### Upgrade result

- upgrade `V36→V37`：PASS（通过）。
- legacy `SUCCEEDED` 与 `FAILED` rows 在迁移后均保持 `NULL/NULL`：PASS（通过）。
- legacy `SUCCEEDED` late bind 被 trigger 拒绝：PASS（通过）。
- `FAILED + NULL/NULL → SUCCEEDED + valid pair` 首次绑定：PASS（通过）。

### Constraint and immutability regressions

- partial pair：拒绝。
- 空 key、slash、backslash、colon、任意 `..`、absolute-path shape、129 chars：拒绝。
- bound pair rebind：拒绝。
- bound pair clear：拒绝。
- repository existing publish/read：通过。

### Capacity and lock assessment

| Phase | Rows | Relation | Indexes | Long transactions | Lock waits |
| --- | ---: | ---: | ---: | ---: | ---: |
| fresh | 2 | 8,192 bytes | 65,536 bytes | 0 | 0 |
| upgrade | 2 | 8,192 bytes | 65,536 bytes | 0 | 0 |

以上只是 disposable small-sample facts，不外推为生产安全结论。生产表规模、写入速率、长事务与实际 lock duration 未实测；`ADD COLUMN` / `ADD CHECK NOT VALID` / `VALIDATE` / trigger DDL 均需在部署前通过只读容量/锁检查并设置有界停止条件。该 residual 为 P2，不允许在 P1 未关闭时部署。

## Validation

### Focused tests

```powershell
mvn -f backend/pom.xml -pl nq-research,nq-infra -am `
  '-Dtest=BacktestPublishServiceTest,BacktestPublishArtifactLocatorMigrationContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result：service 3/3、migration contract 1/1，`BUILD SUCCESS`。

真实 PostgreSQL focused integration 使用显式 localhost URL、user 与 `nq.artifact-locator.postgres.required=true`；password 未写入命令、文档或仓库。Result：fresh/upgrade 2/2。

### Focused reactor

```powershell
mvn -f backend/pom.xml -pl nq-research,nq-infra,nq-app -am test
```

Result：23 modules `BUILD SUCCESS`；`nq-app` 246 tests、0 failures、0 errors、15 skipped；两个 canonical ArchUnit suites 各 6/6。

首次执行的环境失败链：localhost 5432 无 DB；随机端口不满足既有 local profile；缺少 `nexus_quant` DB；fresh DB 缺少既有测试所需 account fixture。仅在 disposable DB 补齐本地测试环境后重跑通过，未为环境问题修改业务代码或既有测试。

### Full backend

```powershell
mvn -f backend/pom.xml test
```

Result：23-module reactor `BUILD SUCCESS`；`nq-app` 246 tests、0 failures、0 errors、15 skipped。

### IDEA build

IDEA `build_project`：`isSuccess=true`。仅有 3 个与本轮无关的既有 Jackson `JsonNode.fields()` deprecation warning。

### Authority and diff

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/docs/check-current-authority.ps1
git diff --check
```

首轮 checker 精确报告 root README、current README 与 ROADMAP 仍指向 implementation；按任务“仅 checker 必须时修改”边界，最小同步三个 staged baseline 的 4B 状态/next action。重跑结果：`AUTHORITY_CHECK errors=0 / PASS / CURRENT_AUTHORITY_CONSISTENT`。`git diff --check`：PASS（通过），仅 LF→CRLF working-copy warning。

## Files created

- `backend/nq-infra/src/main/resources/db/migration/V37__gate_x4b_persistent_artifact_locator.sql`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/BacktestPublishArtifactLocator.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/infra/postgres/BacktestPublishArtifactLocatorMigrationContractTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/research/BacktestPublishArtifactLocatorPostgresIntegrationTest.java`
- `docs/current/evidence/gate-x/NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-IMPLEMENTATION.attempt-01.md`

## Files changed

- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/BacktestPublishRecord.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/domain/port/BacktestPublishRecordRepository.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/BacktestPublishService.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcBacktestPublishRecordRepository.java`
- `backend/nq-research/src/test/java/com/guidinglight/nexusquant/research/application/BacktestPublishServiceTest.java`
- `docs/current/DB_SCHEMA.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

## Boundary confirmation

- GateX-4 API/UI impact：0；原 blocker 未解除。
- trusted-root impact：0；未新增配置或 resolver，未持久化 root/path/URL。
- frontend / Python / scheduler / runner impact：0。
- Shadow Run creation / admission change：0。
- trading state machine / LIVE / credential / private endpoint / real exchange impact：0。
- AI / DH runtime impact：0。

## Findings

### P0

- 无。

### P1

- 无。

### P2

- 生产 `backtest_publish_records` 行数/大小、写入速率、长事务、锁等待和真实 migration lock window 未实测。部署前必须只读核对容量与锁并设置受控窗口、超时/停止条件；disposable metrics 不得外推。

### P3

- IDEA SQL inspection 当前绑定 V36 数据源，对 V37 新列显示 unresolved；真实 Flyway/PostgreSQL/Maven 已验证，非编译或运行时 blocker。
- 既有 SLF4J NOP、Mockito dynamic agent、CDS 与 Maven settings warning 未在本轮扩范围处理。

## Authority after

```text
work_batch=GateX-4B
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-REVIEW
```

GateX-4C 未初始化；GateX-4 API/UI 仍为 `BLOCKED / WAITING_FOR_ARTIFACT_LOCATOR_REMEDIATION`。

## Rollback

- V37 是 forward-only migration；不得删除、修改或执行 down migration。
- 应用回滚时停止新 publish，回滚 Java binary，但保留两列、CHECK、function 与 trigger；旧应用忽略新列。
- 已绑定 locator 不清空、不重绑、不 backfill。发现错误 key 或损坏 artifact 时新建 publish/release，或另开有审批、审计、测试的 forward remediation。
- 若 migration 在部署窗口因 lock timeout 失败，保持应用停止写入，确认 Flyway transaction 已回滚后再评估；不得临时删除约束或绕过 trigger。

## Commit recommendation

```text
feat(research): persist strategy release artifact locators
```

本任务不执行 commit/push。

## Independent migration review requirement

在独立 review 完成前，不得将 4B 标记为 `REVIEW_ACCEPTED`、不得进入 commit/push，也不得恢复 GateX-4 API/UI。review 必须重新检查 V37 lock/trigger/constraint、JDBC conflict semantics、no-backfill、duplicate-without-UNIQUE 决策和 disposable PostgreSQL evidence。

## Next action

唯一下一动作：

```text
NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-REVIEW
```

## Final decision

```text
IMPLEMENTED /
PERSISTENT_ARTIFACT_LOCATOR_SCHEMA_COMPLETE /
POSTGRESQL_REGRESSION_GREEN /
PENDING_INDEPENDENT_MIGRATION_REVIEW
```
