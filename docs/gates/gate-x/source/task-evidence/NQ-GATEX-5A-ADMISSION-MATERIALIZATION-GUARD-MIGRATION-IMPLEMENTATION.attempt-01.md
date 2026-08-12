# NQ-GATEX-5A Admission Materialization Guard Migration Implementation — Attempt 01

## 结论

`IMPLEMENTED / ADMISSION_GUARD_SCHEMA_COMPLETE / REVISION_MUTATION_PROTOCOL_IMPLEMENTED / RELEASE_CONTENT_IDENTITY_BOUNDARY_COMPLETE / POSTGRESQL_REGRESSION_GREEN / UPSTREAM_P1_NOT_YET_CLOSED / PENDING_INDEPENDENT_MIGRATION_REVIEW`（已实现 / admission guard schema 完成 / revision mutation protocol 已实现 / release content identity 边界完成 / PostgreSQL 回归通过 / 上游 P1 尚未关闭 / 待独立 migration review）。

本结论只说明 GateX-5A V38 consistency infrastructure 已具备独立 migration review 条件。它不关闭 `ADMISSION_MATERIALIZATION_FACT_TEAR`，不实现或签发 GateX-5B guard，不启动 Shadow Run，不授权交易或 LIVE。

## Task classification 与 baseline

- 类型：NQ-only、L 级 `FLYWAY_MIGRATION` / 一致性基础设施实现。
- 主 skill：`db-schema-migration-review`；辅助 skill：`nq-dh-workflow-router`、`nq-docs-writer`。
- 开始分支：`dev`。
- 开始 `HEAD`：`7aaf6027644b2ba6cd7dc588536784be50ff1eff`。
- `origin/dev`：`ac4b1ba10f7ac10f973707e97c52b56a6b5aec6f`。
- 特殊 baseline：既有 26 个 staged remediation-chain 文件的 index 内容与 `origin/dev` 一致；用户确认该状态源于另一台电脑提交、当前电脑撤销提交，可继续。本轮未移动 `HEAD`，未 commit，未 push。
- Machine authority 保持 `accepted_batch=GateX-4 / ACCEPTED|CI_GREEN`、`work_batch=GateX-5 / IMPLEMENTED|PENDING_REVIEW`、`LIVE=DISABLED`、`shadow_trading=NOT_ENABLED`；未修改 governance contract。

## Scope 与禁止范围

实现范围：

- 新增 V38、admission state typed boundary、application mutation coordinator、verified first-binding repository、manifest fingerprinter。
- 将现有 production Publish、evaluation、published backtest、Paper、Shadow、consistency、dataset mutation writer 接入 state-first coordinator。
- 新增/维护真实 PostgreSQL 17、fingerprint、repository fixture 与 stale Flyway baseline 回归。
- 新增本 implementation evidence；`TESTING.md` / `WORKLOG.md` 不追加本轮 ledger。

明确未做：GateX-5B guard issuance/evaluation、`ADMISSION_STALE`、materialization current-fact guard、Shadow start/runner/scheduler、matching/order/risk/ledger/account 写侧、credential/private exchange/LIVE/DH、前端、Python、部署、V1–V37 修改。

## V38 schema 与初始化

新增 `V38__gate_x5a_admission_materialization_guard.sql`；迁移目录最高版本为 38，V38 version count=1，V1–V37 tracked bytes/diff 未变化。

`strategy_release_admission_state`：

- `publish_record_id VARCHAR(128)` 同时为 PK 与 `backtest_publish_records.publish_record_id` FK，`ON UPDATE RESTRICT / ON DELETE RESTRICT`；未新增 `admission_id`、`release_id` 或 `guard_id`。
- `admission_revision BIGINT NOT NULL DEFAULT 0 CHECK >= 0`。
- `guard_schema_version INTEGER NOT NULL DEFAULT 1 CHECK = 1`。
- identity quartet 为 `release_artifact_digest`、`manifest_fingerprint`、`manifest_schema_version`、`identity_bound_at`；CHECK 强制全 NULL 或全 non-NULL。
- digest/fingerprint 仅允许 lowercase 64-hex SHA-256；manifest schema 仅允许 `strategy-release-manifest.v1`；没有 digest/fingerprint UNIQUE。
- 表、全部字段、统一 bump function、fan-out function、immutability function 与 dataset reverse index 均有中文业务注释。

Historical initialization 使用有序 `INSERT ... SELECT` 为已有 publish 创建 `revision=0 / schema=1 / quartet=NULL` state，不扫描 filesystem、不复用 Shadow digest、不重建 manifest、不猜测 fingerprint。Future publish 由 `AFTER INSERT` trigger 幂等初始化 state，publish INSERT 本身不 bump revision。

新增 expression index：

```sql
CREATE INDEX idx_backtest_runs_dataset_snapshot_id
ON backtest_runs ((dataset_snapshot_json ->> 'datasetId'));
```

真实 PostgreSQL `EXPLAIN` 在 `enable_seqscan=off` 下命中该 index，证明 `datasetId -> backtest run -> publish` reverse mapping 可使用它。

## Revision function 与 trigger coverage

统一单 release function `bump_strategy_release_admission_revision(...)`：校验 ID、`FOR UPDATE NOWAIT` 锁 state、缺行以 `23503` fail-closed、原子执行 `revision + 1` 与 `updated_at` 更新。业务语义只依赖单调变化，不依赖 bulk action 严格 `+1`。

统一 fan-out function 会去空、去重、按 `publish_record_id` 升序执行；硬上限 256，可由事务级 `nexusquant.admission.max_fan_out` 收紧但不可放宽。strategy/dataset resolver 与 trigger 只探测最多 257 个 ID，非法配置以 `22023`、超限以 `54000` fail-closed，且在任何 state lock/source mutation 前终止。

| Source | 覆盖动作 | revision 语义 |
| --- | --- | --- |
| Publish | INSERT | 只初始化 state，不 bump |
| Publish | UPDATE | bump；rekey/delete 被 RESTRICT FK 阻止 |
| Evaluation report | INSERT / UPDATE / DELETE / UPSERT | 经 `backtest_run_id -> publish_record_id` bump old/new affected release |
| Published backtest run | UPDATE / DELETE | 只对已被 publish 引用的 run bump；无 publish 的 research run 不受影响 |
| Paper run | INSERT / UPDATE / DELETE | old/new `publish_id` 均覆盖，包含 NONE→first、latest/reorder/delete |
| Shadow run | INSERT / UPDATE / DELETE | `publish_id != NULL` 时 bump；CREATED INSERT 恰好一次 |
| Shadow event | 任意 | 未安装 trigger；`CREATED` event 零 bump |
| Consistency report | INSERT / UPDATE / DELETE | 经 `shadow_run_id -> shadow_runs.publish_id` bump，覆盖 NONE→first、latest/reorder/delete |
| Strategy version | status UPDATE / DELETE | transition table 汇总、去重、有界、升序 fan-out |
| Dataset | admission-sensitive UPDATE / DELETE | 仅 status/quality/time/bar/gap 等影响 availability/quality 的字段触发 reverse fan-out；无关 metadata 不 bump |

## Application coordinator、fan-out 与锁顺序

`AdmissionMutationCoordinator` / `JdbcAdmissionMutationCoordinator` 实现：publish IDs 去空、去重、升序、最多 256；同一事务内先 `SELECT ... ORDER BY publish_record_id FOR UPDATE` 并精确校验所有 state 存在，再调用 source mutation。coordinator 不直接修改 revision，revision 只由 source trigger 写，避免 double bump。

已接入的 production writer：

- `JdbcBacktestPublishRecordRepository.upsert`
- `JdbcBacktestEvaluationReportRepository.upsert`
- `JdbcBacktestRunRepository.updateExecution`
- `JdbcPaperTradingRunRepository.insert/updateStatus`
- `JdbcShadowRunFactRepository.create/updateStatus/createConsistencyReport`
- `JdbcMarketdataDatasetRepository.updateQuality`

`strategy_versions` 当前 production repository 只有 INSERT，不存在影响已有 publish 的 production UPDATE/DELETE writer；V38 DB trigger 仍覆盖 raw UPDATE/DELETE。全仓 main Java SQL writer 检索未发现第二条未协调的 admission-sensitive production update path。

锁顺序固定为：resolve affected IDs → ascending state locks → source row mutation → trigger bump already-locked state → dependent event/report → commit。raw source-first writer 若与 state-first writer竞争，在 trigger 的 `FOR UPDATE NOWAIT` 处立即 fail-closed，避免 `source row -> wait state` 与 `state -> wait source row` 形成死锁环。

真实并发回归证明：

- state-first TX-B 在 TX-A 持有相同 state 时等待并在线性化后完成；
- 输入 `[A,B]` 与 `[B,A]` 都规范化为 `[A,B]`，无死锁；
- production Shadow status updater 先锁 state、后读取/更新 Shadow row；
- raw source-first writer 遇已锁 state 立即失败，source fact 未提交；
- resolve 后出现的新 publish 仍由 trigger bump 或使事务失败，不存在 committed fact + unchanged revision。

Deadlock assessment：P0=0、P1=0；未发现保留的反向等待路径。

## Release identity first binding 与 immutability

`VerifiedStrategyReleaseIdentity.fromVerifiedRelease(...)` 只接受 server-controlled `StrategyReleaseStatus.VERIFIED` aggregate，并交叉核对 verifier result、manifest 与 release artifact digest。接口不接收 filesystem path、trusted root、raw manifest、HTTP locator 或 client digest。

`JdbcStrategyReleaseAdmissionStateRepository.bindVerifiedReleaseIdentity(...)` 在 state-first transaction 中：

1. 锁唯一 publish state；
2. 重载 publish/backtest/dataset identity facts与 server-owned artifact/manifest storage-key presence；
3. 要求 publish `SUCCEEDED`、persisted facts 与 verified command 一致、现有 quartet 全 NULL；
4. 一次 UPDATE 写入完整 quartet；
5. DB immutability trigger 自动 bump revision；
6. 任一步失败则 quartet 与 revision 一起 rollback。

DB trigger 只允许 `NULL quartet -> complete non-NULL quartet` 一次；之后 digest/fingerprint/schema/boundAt 修改、clear、partial clear、ordinary rebind 均以 `23514` 拒绝。Artifact digest 所有权属于 server verifier 确认的 release artifact set，不属于 HTTP client、path 或历史 Shadow fact。

Producer 状态继续保持 `PERSISTENCE_READY / PRODUCER_NOT_YET_CONNECTED`（持久化已就绪 / producer 尚未接线）；本轮未扫描或批量绑定历史 artifact。

## Manifest fingerprint

`StrategyReleaseManifestFingerprinter` 实现 `strategy-release-manifest-fingerprint.v1`：固定 field/type tag、presence byte、length-prefixed UTF-8、canonical lowercase UUID、Instant epoch-second+nano、固定 field order、完整 descriptor tuple 排序，最后 SHA-256 lowercase 64-hex。

覆盖字段：manifest schemaVersion、strategyVersionId、datasetId、evaluationId、artifactDigest、generatedAt、generatorVersion 与全部 sorted artifact descriptors。实现不 hash raw JSON，不依赖 JSON field order、whitespace、locale 或 ISO string formatting。3 个单测验证 descriptor order/locale 稳定性、每个冻结 identity field 都影响结果，以及 identity 只能从 verified server aggregate 派生。

## PostgreSQL regression

环境：本地 disposable `postgres:17`，实际 PostgreSQL `17.10`，仅使用随机 `gatex5a_*` schema；测试结束 schema 残留/等待锁/超过 30 秒事务均为 0，随后删除容器 `nq-gatex5a-v38-syntax`。未接触生产数据库或真实用户数据。

- 专项：`AdmissionMaterializationGuardPostgresIntegrationTest`，4 tests / 0 failures / 0 errors / 0 skipped。
- Fresh：V1→V38 成功，schema version=v38；Flyway validate 成功（39 migration entries）。
- Upgrade：先迁移至 V37、写入 historical fixture，再只应用 V38；historical state=`revision 0 / quartet NULL`，future publish 同样自动初始化。
- Mutation：Publish、Evaluation INSERT/UPDATE/DELETE/UPSERT、published run、Paper、Shadow、consistency、strategy fan-out、dataset fan-out 均改变相关 revision。
- Fan-out limit：257 个受影响 publish 的 strategy raw UPDATE 在 source mutation 前 fail-closed，strategy status 与首尾 revision 均未变化；application coordinator 同样拒绝 257 IDs 且 mutation callback 调用次数为 0。
- Shadow exactness：CREATED Shadow INSERT 恰好一次 revision change；CREATED event 零 change；CREATED→PRECHECKING change。
- Phantom/latest：Paper NONE→first、Shadow NONE→first evidence-bearing row、Consistency NONE→first，以及 old Paper reorder、Shadow status/version/reorder、new consistency latest 均通过。
- Raw SQL：绕过 coordinator 的 Paper、Shadow、consistency、strategy/dataset fan-out 均得到 revision change 或 transaction fail；无 committed fact + unchanged revision。
- Rollback：强制 trigger/state-lock failure 时 source fact 与 revision 一起 rollback；first-binding failure 保持 quartet NULL 且 revision 不变。
- Identity：first bind 成功；rebind/clear/partial mutation 被 DB 拒绝。
- 既有 PostgreSQL suites：`ShadowRunProvenancePostgresIntegrationTest`、`BacktestPublishArtifactLocatorPostgresIntegrationTest` 与 `JdbcRepositoryPostgresSmokeTest` 相关 6 tests 通过；stale Flyway assertions 已更新至 V38。

## Migration lock / capacity evidence

测试 fixture 快照：

| Label | publish | backtest | Paper | Shadow | consistency | state relation | dataset index | >30s transactions | lock waits |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| upgrade | 2 | 2 | 0 | 0 | 0 | 24,576 bytes | 16,384 bytes | 0 | 0 |
| coverage | 2 | 2 | 0 | 0 | 0 | 24,576 bytes | 16,384 bytes | 0 | 0 |

Fresh V1→V38 disposable schema 的 38 migrations 总 execution time 为约 0.571 秒；该数字不是 V38-only timing，也不得外推生产。V38 内 `CREATE TABLE`、FK validation、historical `INSERT ... SELECT`、non-concurrent expression index 与 trigger/function DDL 均处于 Flyway 单事务，相关 DDL lock 会持有到 commit；migration 设置 session `lock_timeout='5s'` 并在末尾 RESET，用于等待超时 fail-closed，不代表生产窗口已测量。

P2 保留：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`。部署前必须在目标环境只读采集真实 row counts、relation/index sizes、长事务与 lock waits，安排受控窗口和停止条件；测试 fixture 不足以证明生产锁窗口安全。

## Java / architecture / authority / diff validation

- `mvn -f backend/pom.xml -pl 'nq-core,nq-research,nq-infra,nq-app' -am test`：显式指向 disposable PostgreSQL；23/23 reactor modules `SUCCESS`，`BUILD SUCCESS`；`nq-app` 260 tests、0 failures、0 errors、21 skipped。
- `mvn -f backend/pom.xml test`：同一 disposable datasource；23/23 reactor modules `SUCCESS`，`BUILD SUCCESS`；`nq-app` 260 tests、0 failures、0 errors、21 skipped。
- Targeted ArchUnit：`ModuleBoundaryArchTest` + `PackageBoundaryArchTest` 共 16 tests、0 failures、0 errors、0 skipped。
- `StrategyReleaseManifestFingerprinterTest`：3/3 通过。
- `JdbcShadowRunFactRepositoryTest`：11/11 通过。
- Authority checker：`PASS / CURRENT_AUTHORITY_CONSISTENT / errors=0`。
- `git diff --check` 与既有 cached diff check 通过；V38 unique、V1–V37 unchanged。
- 最终 staged scope 共 46 paths：既有 26-file remediation chain 完整保留；相对 `origin/dev` 的本任务 delta 精确为 22 paths（与既有 chain 有 2 个重叠修改路径）；unstaged=0、untracked=0、cached whitespace errors=0。
- frontend staged/unstaged diff 均为 0；Playwright=`NOT_RUN`（未运行）。
- Maven 仅出现既有 SLF4J provider、Mockito dynamic-agent/JDK 与 unchecked/deprecation warning；未改变测试 verdict。

RCA：fan-out regression 首次 test-compile 因漏加 `AdmissionMutationCoordinator` import 失败，补齐后专项 4/4。V38 收紧后首次 focused run 命中常规 local DB 中更早的未提交 V38 checksum，Flyway 正确 fail-closed；未对该 DB 执行 repair。切换空 disposable DB 后首次 run 仅因既有 local happy-path test 需要至少一条 legacy account fixture 而失败；在 disposable DB 写入一条无凭证、无真实交易能力的 TEST/SIM fixture 后，focused 与 full 原命令均通过。

## Findings

### P0

- 0。

### P1

- 本 migration implementation scope 内 0：未发现 admission-sensitive mutation 可提交而 revision 不变、非原子 source/revision、missing-state silent no-op、partial/rebind、phantom bypass、fan-out partial bump 或 deadlock ordering violation。
- 上游 `ADMISSION_MATERIALIZATION_FACT_TEAR` 仍为 `UPSTREAM_P1_NOT_YET_CLOSED`；V38 只是 GateX-5B 所需 consistency substrate，尚无 guard issuance/evaluation 与 materialization current-fact validation，因此不得写成 P1 closure。

### P2

- 1：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`。

### P3

- 0。构建日志中的既有环境 warning 不属于本轮代码 finding。

## Boundary、rollback 与下一动作

- LIVE 保持 `DISABLED`；Shadow trading 保持 `NOT_ENABLED`；未新增 runtime start、scheduler、order、risk、ledger、credential、private exchange、network、DH 或前端路径。
- Migration 为 forward-only；尚未 commit 时可通过移除本轮精确 staged files 恢复工作树，但不得覆盖既有 26-file remediation chain。若 V38 已在环境成功应用，不修改 V38 history，必须使用后续 forward corrective migration；部署前应准备数据库备份/恢复方案并以 5 秒 lock timeout、长事务或锁等待为停止条件。
- Machine authority 不因本 implementation evidence 改写；scoped human workflow 的唯一下一动作是 `NQ-GATEX-5A-ADMISSION-MATERIALIZATION-GUARD-MIGRATION-REVIEW`。
- Independent migration review 必须重新审查 V38 DDL/trigger/lock/fan-out/identity immutability、真实 PostgreSQL evidence 与 staged scope；review 通过前不得关闭 upstream P1、进入 GateX-5B 或宣称 CI acceptance。

Final decision：`IMPLEMENTED / P0=0 / IMPLEMENTATION_P1=0 / P2=1 / P3=0 / UPSTREAM_P1_NOT_YET_CLOSED / PENDING_INDEPENDENT_MIGRATION_REVIEW`。
