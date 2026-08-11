# NQ-GATEX-5A Admission Materialization Guard Migration Review（attempt-01）

> Task：`NQ-GATEX-5A-ADMISSION-MATERIALIZATION-GUARD-MIGRATION-REVIEW`
> Classification：NQ-only / `INDEPENDENT_MIGRATION_REVIEW` / L 级高风险数据库审查
> Review date：2026-08-11（Asia/Shanghai）
> 主 skill：`db-schema-migration-review`
> 辅助 skill：`nq-dh-workflow-router`
> Review status：`PASS`（通过）

## 1. 审查边界与结论

本轮仅审查 V38 consistency infrastructure 是否可作为 GateX-5B 修复
`ADMISSION_MATERIALIZATION_FACT_TEAR` 的可靠基础。未实现 GateX-5B，未关闭上游 P1，未修改
current authority、`STATUS.md`、`ROADMAP.md`、`TESTING.md` 或 `WORKLOG.md`，未接触 LIVE、真实交易、
credential、DH、前端、部署、commit 或 push。

最终结论：

```text
PASS /
ADMISSION_GUARD_MIGRATION_REVIEW_ACCEPTED /
REVISION_MUTATION_PROTOCOL_VERIFIED /
RAW_SQL_AND_PHANTOM_GUARDS_VERIFIED /
RELEASE_IDENTITY_IMMUTABILITY_VERIFIED /
UPSTREAM_P1_STILL_OPEN /
READY_FOR_GATEX_5B
```

Finding 汇总：`P0=0 / P1=0 / P2=1 / P3=0`。唯一 P2 为
`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`。

## 2. Baseline、shared-dev 与 forward-only 判定

```text
branch=dev
starting HEAD=ac4b1ba10f7ac10f973707e97c52b56a6b5aec6f
origin/dev=ac4b1ba10f7ac10f973707e97c52b56a6b5aec6f
HEAD==origin/dev=true
starting staged residual=23 paths
starting unstaged=0
starting untracked=0
SHARED_DEV_CONTAINS_P1_OPEN_CODE=true
FORWARD_REMEDIATION_REQUIRED=true
```

禁止重写的 shared commit `ac4b1ba1 feat: 暂时提交` 共含 26 个 GateX-5 / GateX-5A 相关路径：

| 分类 | shared paths |
| --- | --- |
| materialization API | `ApiExceptionHandler.java`、`StrategyReleaseShadowRunMaterializationController.java`、`StrategyReleaseShadowRunMaterializationResponse.java` |
| materialization service | `ShadowRunMaterializationActor.java`、`ShadowRunMaterializationAuthorizationException.java`、`ShadowRunMaterializationRejectedException.java`、`ShadowRunMaterializationResult.java`、`ShadowRunMaterializationWriter.java`、`StrategyReleaseAdmissionPreviewService.java`、`StrategyReleaseShadowRunMaterializationService.java` |
| idempotency / provenance | `ShadowRunCreationPlan.java`、`JdbcShadowRunFactRepository.java` |
| tests | `ShadowRunProvenancePostgresIntegrationTest.java`、`StrategyReleaseShadowRunMaterializationSecurityWebMvcTest.java`、`ShadowRunMaterializationWriterTest.java`、`StrategyReleaseShadowRunMaterializationServiceTest.java` |
| V38 infrastructure | 无；V38 不在 shared HEAD |
| docs / evidence | `docs/current/README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`，以及 GateX-5 implementation/review、GateX-5A consistency contract/schema review 共 4 份 evidence |
| 其他 | root `README.md` |

`git cat-file -e HEAD:backend/nq-infra/src/main/resources/db/migration/V38__gate_x5a_admission_materialization_guard.sql`
返回 128；V38 是 staged 新文件而非 shared migration。因此审查发现 P0/P1 时允许在未提交 V38 上做最小修复，
但禁止 reset/rebase/force 或改写 `ac4b1ba1`。

Machine authority 未把 GateX-5 写成 accepted，未把上游 P1 写成 closed，LIVE 仍为 `DISABLED`，
Shadow trading 仍为 `NOT_ENABLED`。`check-current-authority.ps1` 返回
`PASS / CURRENT_AUTHORITY_CONSISTENT / errors=0`。

## 3. V38 schema、初始化与约束审查

V38 唯一；migration 目录无 V39+；相对 HEAD 的 migration diff 仅 V38，V1–V37 bytes 未改。

`strategy_release_admission_state` 以 `publish_record_id` 为 PK/FK，字段为：

```text
publish_record_id
admission_revision BIGINT NOT NULL DEFAULT 0
guard_schema_version INTEGER NOT NULL DEFAULT 1
release_artifact_digest
manifest_fingerprint
manifest_schema_version
identity_bound_at
created_at
updated_at
```

约束覆盖 revision 非负、guard schema 固定为 1、digest/fingerprint 为 lowercase 64-hex，以及 identity quartet
全 NULL 或全 non-NULL。表中没有第二套 release identity、persisted `ELIGIBLE/BLOCKED`、client-controlled path、
trusted root、raw manifest 或 credential。

Historical initialization 使用 `INSERT ... SELECT` 为每个已有 publish 建且仅建一个 state row：
`admission_revision=0 / guard_schema_version=1 / identity quartet=NULL`。没有 artifact digest backfill、
Shadow digest reuse、filesystem scan 或 fingerprint guessing；historical row 因 quartet NULL 不能伪装成 guard-ready。

Future publish invariant 由 `AFTER INSERT ON backtest_publish_records` trigger 保证；direct raw SQL INSERT 同样自动
产生 revision 0、quartet NULL 的 state，不依赖 application-only initialization。state 缺失时 authoritative bump
以 `23503` fail-closed，不允许 `UPDATE 0 rows` 静默成功。publish re-key 与删除被 FK/trigger contract 拒绝。

Dataset reverse mapping 使用生产 snapshot key `dataset_snapshot_json ->> 'datasetId'`，
`idx_backtest_runs_dataset_snapshot_id` 是唯一等价 expression index；真实 `EXPLAIN` regression 证明 reverse lookup
使用该 index，未发现 key 漂移或重复 index。

## 4. Revision protocol 与本轮 P1 修复

审查发现原 staged V38 只有 `CHECK (admission_revision >= 0)`：direct SQL 可把较大 revision 回写为较小非负值，
从而使旧 guard revision 再次出现。这是 `P1 / ADMISSION_REVISION_ROLLBACK_REWRITE`。

由于 V38 尚未 shared，本轮在 V38 内做最小 forward/uncommitted 修复：

- 新增 `prevent_strategy_release_revision_rewrite()` 与
  `trg_strategy_release_revision_monotonic`；
- 每次 row-level revision UPDATE 只允许严格 `OLD + 1`；
- 回退、同值或跳跃 rewrite 均以 `23514` fail-closed；
- 多个受影响事实可以在同一事务触发多次 +1，因此 bulk action 最终 revision 允许大于 +1；
- 新增 direct SQL regression，覆盖回退、同值、`+2`，失败后 revision 保持不变。

Authoritative `bump_strategy_release_admission_revision(...)` 先 `FOR UPDATE NOWAIT` state，再做 +1；空 ID、
missing state、overflow 或 constraint failure 使整个 source transaction rollback。生产 Java writer 搜索未发现直接设置
`admission_revision` 的路径；application coordinator 只锁 state，不手工 bump，DB trigger 是 sole bump owner。

修复后重新执行完整 PostgreSQL matrix、focused reactor 与 full backend，原 P1 已在本 migration scope 内关闭。

## 5. Mutation / trigger coverage matrix

独立搜索 production JDBC DML 后的覆盖如下：

| Fact | production DML | publish resolution / state-first | DB enforcement | PostgreSQL result |
| --- | --- | --- | --- | --- |
| Publish | INSERT / UPDATE | publish ID；writer 接 coordinator | INSERT 初始化 state；UPDATE bump | PASS |
| Evaluation | INSERT / UPDATE / DELETE / UPSERT | evaluation → backtest → publish；writer 接 coordinator | row trigger I/U/D | PASS |
| Published Backtest Run | UPDATE（当前 production writer） | backtest → publish；writer接 coordinator | U/D trigger | PASS |
| Strategy Version | 当前 production 仅 INSERT；无 U/D writer | strategy version → all publishes | statement-level U/D trigger，limit probe 257 | PASS |
| Dataset | INSERT / UPDATE（当前 production writer） | `datasetId` expression reverse lookup；writer接 coordinator | statement-level U/D trigger，limit probe 257 | PASS |
| Paper | INSERT / UPDATE / DELETE | direct `publish_id`；writer接 coordinator | row trigger I/U/D | PASS |
| Shadow | INSERT / UPDATE；event append不属于 admission fact bump owner | direct `publish_id`；writer接 coordinator | row trigger I/U/D | PASS |
| Consistency | INSERT / UPDATE / DELETE | consistency → Shadow → publish；writer接 coordinator | row trigger I/U/D | PASS |

未发现一条 production admission-sensitive fact 可以 `committed fact + unchanged revision`。无关 Shadow event append
没有 trigger，避免 double bump；涉及 latest/eligibility 的 source fact 写入均受 trigger 保护。

## 6. Raw SQL、phantom、latest 与 Shadow exact semantics

专项测试故意绕过 `AdmissionMutationCoordinator`，直接 SQL 修改 Publish、Evaluation、Backtest、Paper、Shadow、
Consistency、Strategy 和 Dataset。结果均为 revision changed 或 source transaction failed；不存在 raw SQL bypass。

Mandatory phantom / latest proof：

- Paper `NONE -> first`：revision changed；old row `updated_at` reorder：changed；
- Shadow `NONE -> first evidence-bearing`：changed；status/version/update reorder：changed；
- Consistency `NONE -> first`：changed；new report / update reorder / delete：changed；
- Strategy/Dataset fan-out：所有关联 publish changed，或超过上限时 source 与 revisions 全部 rollback。

Shadow exact behavior：

```text
Shadow CREATED INSERT          -> revision exactly +1
CREATED event append           -> revision unchanged
CREATED -> PRECHECKING         -> revision +1
```

未发现 `shadow_runs trigger + application manual bump` 双 ownership。当前 latest Shadow business query 仍未排除
`CREATED`；V38 不依赖该错误 query，因此不是本 migration 的 P1，但 GateX-5B mandatory handoff 必须加入
`status <> CREATED`。

## 7. Coordinator、fan-out 与 locking

`AdmissionMutationCoordinator` / `JdbcAdmissionMutationCoordinator` contract：publish IDs 去重、升序、最大 256，
依次锁 `strategy_release_admission_state FOR UPDATE`，然后才执行 source mutation；不手工 bump revision。

257 probe 同时覆盖 DB trigger 与 application coordinator：

```text
affected publishes=257
source mutation=not committed
mutation callback calls=0
first/last revisions=unchanged
partial bump=0
```

真实并发结果：

- state-first TX-B 遇 TX-A state lock 会等待，TX-A commit 后线性化完成；
- raw source-first writer 遇已锁 state 通过 trigger `NOWAIT` 快速失败，source 与 revision 均不变；
- 两个 coordinator 分别输入 `[A,B]` 与 `[B,A]`，均规范化为 `[A,B]`，5 秒内完成；
- `deadlock=0 / partial mutation=0`；
- trigger/state 缺失强制失败时 source mutation 与 revision 一起 rollback。

统一 lock order 为：

```text
resolve publish IDs
-> ascending sort
-> lock admission states
-> lock/mutate source
-> trigger bump
-> dependent rows/events
-> commit
```

## 8. Release identity first binding 与 fingerprint

`VerifiedStrategyReleaseIdentity.fromVerifiedRelease(...)` 只接受 server-controlled `VERIFIED` aggregate，并核对
verifier result、manifest 与 artifact digest；不接受 HTTP/client digest、filesystem path、trusted root 或 raw manifest。

`JdbcStrategyReleaseAdmissionStateRepository` 在 state-first transaction 内锁 state、确认 quartet 全 NULL、重载并核对
publish/backtest/dataset/release facts，一次写入完整 quartet；DB trigger 同步 +1。mismatched first-bind 使 identity 保持
NULL、revision 不变。

Direct SQL immutability attacks 覆盖：digest-only、fingerprint-only、partial bind、digest/fingerprint/schema/boundAt
rewrite、complete clear、partial clear。全部以 DB constraint/trigger 失败；成功 first-bind 后 quartet 与 revision 保持原值。

`StrategyReleaseManifestFingerprinter` 使用 `strategy-release-manifest-fingerprint.v1`，固定 type/field tag、presence、
length-prefixed UTF-8、canonical UUID、Instant epoch second+nano、固定字段顺序和 sorted descriptors，再 SHA-256。
覆盖 manifest schema、strategyVersionId、datasetId、evaluationId、artifactDigest、generatedAt、generatorVersion 与完整
descriptor tuples；不 hash raw JSON，不依赖 Map iteration、locale、timezone、whitespace 或 descriptor input order。
3 个单测验证 ordering/locale 稳定性及任意冻结语义字段变化都会改变 fingerprint。

## 9. Migration、capacity 与 lock evidence

真实 disposable PostgreSQL：官方 `postgres:17`，实际 `17.10`，仅绑定 `127.0.0.1` 随机端口、无 volume；
测试 schema/容器均已删除，未连接默认本地库、生产库或真实数据。

- Fresh random schema：V1→V38，成功应用 38 个 versioned migrations，current=v38；Flyway validate 报
  39 个 records（包含 schema-history marker）；
- V37→V38：先建 historical fixture，再应用 V38；historical state revision 0/quartet NULL，future publish 自动初始化；
- pre-existing `public` bootstrap：真实 `flyway_schema_history` 为 38 个 versioned rows，不含 schema creation marker；
- V38 unique，V1–V37 unchanged，无 V39+；
- `version.*36/37`、`Flyway.*36/37`、`latest.*migration` 搜索零命中；测试中的
  `MigrationVersion.fromVersion("37")` 是 upgrade 起点，不是 stale current-version assertion。

Fixture capacity snapshot：state relation 24,576 bytes，dataset expression index 16,384 bytes；超过 30 秒事务=0，
未授予锁=0。Fresh 38 migrations execution time 约 0.586 秒，只是小 fixture 全链耗时，不代表 V38-only 或生产耗时。

V38 的 `CREATE TABLE`、historical `INSERT SELECT`、FK validation、non-concurrent expression index、functions 与
multi-table triggers 位于 Flyway 单事务，DDL lock 可能持有到 commit。`lock_timeout='5s'` 只用于等待 fail-closed，
不能证明生产 deploy window。

保留 `P2 / PRODUCTION_LOCK_WINDOW_NOT_MEASURED`：部署前必须只读采集目标环境 table/index sizes、row counts、
write rate、long transactions、lock queue，并安排受控 maintenance window、timeout policy、数据库备份/恢复方案与
停止条件；不得把当前 fixture 外推为生产 capacity 证据。

## 10. Validation

| Validation | Result |
| --- | --- |
| `AdmissionMaterializationGuardPostgresIntegrationTest` | 5 tests / 0 failures / 0 errors / 0 skipped；Fresh、upgrade、raw SQL、phantom/latest、fan-out、locking/deadlock、identity、revision rewrite、rollback 全覆盖 |
| focused reactor：`mvn -f backend/pom.xml -pl nq-core,nq-research,nq-infra,nq-app -am test` | 23/23 modules `SUCCESS`，`BUILD SUCCESS`，exit 0 |
| full backend：`mvn -f backend/pom.xml test` | 23/23 modules `SUCCESS`，`BUILD SUCCESS`，exit 0；287 suite XML / 1382 tests / 0 failures / 0 errors / 31 skipped |
| `ModuleBoundaryArchTest,PackageBoundaryArchTest` | 16 tests / 0 failures / 0 errors / 0 skipped |
| authority checker | `PASS / CURRENT_AUTHORITY_CONSISTENT / errors=0` |
| diff checks | working/cached `git diff --check` 均通过；LF/CRLF 仅为 Git 提示，无 whitespace error |
| frontend | staged=0、unstaged=0；Playwright=`NOT_RUN`（未运行，因无前端 diff） |

命令纠正记录：首次 PostgreSQL focused reactor 因缺少
`-Dsurefire.failIfNoSpecifiedTests=false` 在无目标 test 的 `nq-common` 停止，exit 1，目标测试未执行；补参数后
5/5 通过。首次回归容器 bootstrap 仅因 PowerShell `docker port` 的 `-split` 优先级错误在 Maven 前停止；修正后
bootstrap、focused、full 均通过。两次失败都没有被写成验证通过，临时容器最终全部删除。

## 11. Findings 与 forward-remediation requirements

### P0

0。未发现 history rewrite、shared migration mutation、release identity mutable、LIVE/trading boundary violation。

### P1

0（本 migration scope 修复并全量重验后）。原
`ADMISSION_REVISION_ROLLBACK_REWRITE` 已由 monotonic row trigger 与 direct SQL regression 修复。

上游 `ADMISSION_MATERIALIZATION_FACT_TEAR` 仍为 `OPEN`，不是本节的 closed P1；V38 尚未提供：

```text
AdmissionGuard issuance
r0/r1
writer FOR UPDATE guard validation
current fact reload
fingerprint recompute
ADMISSION_STALE
atomic guarded materialization
```

这些全部属于 GateX-5B。

### P2

1：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`。

### P3

0。既有 SLF4J provider、Mockito dynamic-agent/JDK warning 不属于本轮 migration finding。

## 12. Authority、rollback 与下一动作

Authority after：machine authority 保持合法且未改；LIVE=`DISABLED`，Shadow trading=`NOT_ENABLED`，
GateX-5 未被本 review 宣称 accepted，上游 P1 未被宣称 closed。

本轮修复仍是未提交 V38，可通过精确反向应用本 review 的两个文件增量并移除本 evidence 回滚；不得覆盖原有
23-path staged chain。V38 一旦在任何 shared 环境成功应用，禁止再修改 V38，后续缺陷必须用 V39+ forward migration。

唯一下一动作：

```text
NQ-GATEX-5B-ADMISSION-MATERIALIZATION-FACT-TEAR-REMEDIATION-IMPLEMENTATION
```
