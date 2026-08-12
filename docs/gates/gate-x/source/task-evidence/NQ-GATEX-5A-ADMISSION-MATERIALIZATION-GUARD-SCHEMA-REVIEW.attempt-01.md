# NQ-GATEX-5A Admission-Materialization Guard Schema Review — Attempt 01

> 任务：`NQ-GATEX-5A-ADMISSION-MATERIALIZATION-GUARD-SCHEMA-REVIEW`
>
> 归属：NQ-only
>
> 类型：`SCHEMA_DESIGN_REVIEW / MIGRATION_WORK_ORDER`
>
> 结论：`PASS / ADMISSION_GUARD_SCHEMA_SELECTED / REVISION_MUTATION_PROTOCOL_FROZEN / V38_REQUIRED / READY_FOR_MIGRATION_IMPLEMENTATION`（通过 / 已选择 Admission Guard schema / 已冻结 revision mutation 协议 / 需要 V38 / 可进入 migration 实施）
>
> `SELECTED_SCHEMA_DESIGN=B`
>
> `REVISION_BUMP_STRATEGY=HYBRID`
>
> 日期：2026-08-11

## 1. Task classification 与边界

- 主类型：`CODE_ANALYSIS`，子类型为 L 级 PostgreSQL schema/security design review。
- 主 skill：`db-schema-migration-review`；先由 `nq-dh-workflow-router` 完成 NQ/Gate 边界判定，`nq-docs-writer` 仅约束本 evidence 的事实源、中文正文与 anti-churn。
- Starting HEAD：`7aaf6027644b2ba6cd7dc588536784be50ff1eff`。
- `origin/dev` HEAD：`7aaf6027644b2ba6cd7dc588536784be50ff1eff`。
- Authority before：`accepted_batch=GateX-4 / ACCEPTED|CI_GREEN`；`work_batch=GateX-5 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；`LIVE=DISABLED`；Shadow trading=`NOT_ENABLED`。
- Existing staged remediation chain：23 个 GateX-5 implementation 文件、1 个 rejected review evidence、1 个 GateX-5A consistency contract evidence，共 25 个 staged 文件；`unstaged=0`、`untracked=0`。
- 本轮只允许新增本 schema review evidence；不修改 V1–V37、Java production/test、frontend、API、runner、scheduler、Shadow start、trading、LIVE 或 governance contract。
- 保留且不修改：
  - `NQ-GATEX-5-RELEASE-TO-SHADOW-MATERIALIZATION-REVIEW.attempt-01.md`
  - `NQ-GATEX-5A-ADMISSION-MATERIALIZATION-CONSISTENCY-CONTRACT-REVIEW.attempt-01.md`

## 2. Evidence checked

### 2.1 Schema

- 已枚举并审查 V1–V37 migration；与 admission 直接相关的 schema 来源为 V7、V9、V10、V18–V21、V28、V32、V33、V36、V37。
- `backtest_publish_records`：V10 建表，V19 增加 `strategy_version_id`，V37 增加成对 opaque locator 与不可重绑 trigger。
- `backtest_runs`：V7 建表，V18/V20 增加 dataset、strategy、param/config snapshot。
- `backtest_eval_reports`：V9 建表，按 `backtest_run_id` 唯一；V20 增加 traceability metrics。
- `paper_trading_runs`：V21 建表，`publish_id` 直接 FK 到 publish record。
- `shadow_runs`、`shadow_run_events`、`shadow_consistency_reports`：V32 建表；V36 增加 Shadow-side `artifact_digest`。
- `strategy_versions`：V19 建表；schema 允许 `DRAFT / ACTIVE / ARCHIVED`。
- `marketdata_datasets`：V18 建表；run 只在 `dataset_snapshot_json.datasetId` 固化 release-side dataset identity。
- `validation_review_cases/events` 不参与当前 canonical release admission decision；其人工 lifecycle 不构成交易或 Shadow materialization 授权，因此不进入 revision source set。

### 2.2 Current decision/write paths

- Admission query：`JdbcStrategyReleaseAdmissionPreviewFactsRepository`。
- Release provenance：`JdbcStrategyReleaseProvenanceRepository`。
- Canonical validation：`StrategyValidationOverviewQueryService.evaluateDecision`。
- Publish writer：`BacktestPublishService` → `JdbcBacktestPublishRecordRepository.upsert`。
- Evaluation writer：`BacktestEvaluationService` → `JdbcBacktestEvaluationReportRepository.upsert`。
- Backtest run writer：`JdbcBacktestRunRepository.insert/updateExecution`。
- Strategy version writer：`JdbcStrategyVersionRepository.insert`；当前未发现 production status update 方法，但 schema 允许 update，不能据此假定永久不可变。
- Dataset writer：`MarketdataDatasetService` → `JdbcMarketdataDatasetRepository.insert/updateQuality`。
- Paper writer：`PaperTradingRunService` → `JdbcPaperTradingRunRepository.insert/updateStatus`。
- Shadow writer：`ShadowRunMaterializationWriter`、既有 `ShadowRunRunnerService` → `JdbcShadowRunFactRepository.create/updateStatus`。
- Consistency writer：`ShadowConsistencyReportService` → `JdbcShadowRunFactRepository.createConsistencyReport`。
- 非 repository SQL writer：未在 production `src/main` 或仓库非 backend 脚本中发现第二套 admission-sensitive DML；migration 历史 DML 仅为 schema 演进事实，不是 runtime writer。

## 3. Current schema facts

1. `publish_record_id` 已是 canonical release anchor；V10 还保证 one publish row per `backtest_run_id`。
2. V37 只持久化 immutable locator pair，不持久化 release-side `artifact_digest` 或 canonical manifest fingerprint。
3. `shadow_runs.artifact_digest` 是 materialization snapshot，不是 release content source truth。
4. current admission latest selection 为：

```text
Paper: ORDER BY updated_at DESC, paper_run_id DESC LIMIT 1
Shadow: ORDER BY updated_at DESC, created_at DESC, id DESC LIMIT 1
Consistency: ORDER BY generated_at DESC, created_at DESC, id DESC LIMIT 1
```

5. current latest Shadow query未排除 `CREATED`，与上游已冻结的 non-evidence semantics 冲突；GateX-5B 必须修正。
6. current schema 没有可覆盖 empty/latest phantom 的 per-publish row，也没有跨 publish/eval/Paper/Shadow/consistency mutation 的 aggregate generation。
7. `updated_at`、`MAX(child_id)`、Shadow `version` 或已选 child row lock 都不能替代 aggregate revision。

## 4. Design A review — 扩展 `backtest_publish_records`

### 优点

- 少一张表、少一次 PK join；writer 可以直接锁 publish row。
- historical row 无需额外 one-to-one 初始化。
- `publish_record_id` 仍是唯一 release identity。

### 阻断性缺点

1. 现有 publish upsert 会更新 status、evaluation/strategy binding、snapshots、timestamps 和 locator；把高频 `admission_revision` 放入同一 row 会让 publish lifecycle writer 与 admission coordination ownership 重叠。
2. publish list/read 与 mutation bump 共用热 row；Paper/Shadow/consistency 更新会制造与 publish lifecycle 无关的 row write、WAL、vacuum 和 contention。
3. V37 已在该 row 上承担 locator first-bind/immutability；继续叠加 digest/fingerprint/revision 会把 locator、release identity、content identity、admission generation 和 publish workflow 过度耦合。
4. current `ON CONFLICT(backtest_run_id)` 语句包含 `SET publish_record_id = EXCLUDED.publish_record_id`。production service 当前复用 existing id，但 schema ownership 不应依赖这项应用习惯来保护 guard identity。
5. 后续 GateX/Y 增加 guard schema、binding/remediation 或审计字段时会持续污染既有 publish aggregate。

### Design A verdict

`REJECTED`（拒绝）。它不是第二套 identity，但会把 coordination writes 注入既有 publish lifecycle，违反“不污染既有 publish lifecycle”和长期 ownership 清晰优先级。

## 5. Design B review — 独立 per-publish admission state

### Ownership

- 表名冻结为 `strategy_release_admission_state`。
- `publish_record_id` 同时是 PK 与 FK；不生成 admission id、release id 或第二套业务 identity。
- 一行只承载 coordination/security state：revision、guard schema version、immutable release content identities 和时间审计。
- 不持久化 `ELIGIBLE/BLOCKED` decision，不复制 canonical evaluator，不成为第二套 admission decision truth。

### 成本与收益

- one-row-per-publish invariant 由 PK 保证。
- writer 的 `FOR UPDATE` 为单行 PK lookup；额外 join 为常数成本。
- Paper/Shadow/consistency 高频 bump 不改 publish row，不影响 publish `updated_at` 排序。
- historical publish 可安全创建 NULL identity state row，保持 fail-closed。
- mutation trigger 和 application lock protocol拥有单一、清晰目标。

### Design B verdict

`SELECTED_SCHEMA_DESIGN=B`。该表是 publish aggregate 的 coordination/state projection，不是第二套 release aggregate。

## 6. Recommended V38 schema

未来 V38 的最小表结构冻结如下；本轮不创建该 migration：

```sql
CREATE TABLE strategy_release_admission_state (
    publish_record_id VARCHAR(128) PRIMARY KEY,
    admission_revision BIGINT NOT NULL DEFAULT 0,
    guard_schema_version SMALLINT NOT NULL DEFAULT 1,
    release_artifact_digest VARCHAR(64),
    manifest_fingerprint VARCHAR(64),
    manifest_schema_version VARCHAR(64),
    identity_bound_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_strategy_release_admission_state_publish
        FOREIGN KEY (publish_record_id)
        REFERENCES backtest_publish_records (publish_record_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT chk_strategy_release_admission_revision
        CHECK (admission_revision >= 0),
    CONSTRAINT chk_strategy_release_guard_schema_version
        CHECK (guard_schema_version = 1),
    CONSTRAINT chk_strategy_release_identity_completeness CHECK (
        (release_artifact_digest IS NULL
            AND manifest_fingerprint IS NULL
            AND manifest_schema_version IS NULL
            AND identity_bound_at IS NULL)
        OR
        (release_artifact_digest IS NOT NULL
            AND manifest_fingerprint IS NOT NULL
            AND manifest_schema_version IS NOT NULL
            AND identity_bound_at IS NOT NULL)
    ),
    CONSTRAINT chk_strategy_release_artifact_digest_sha256
        CHECK (release_artifact_digest IS NULL
            OR release_artifact_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_strategy_release_manifest_fingerprint_sha256
        CHECK (manifest_fingerprint IS NULL
            OR manifest_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_strategy_release_manifest_schema_version
        CHECK (manifest_schema_version IS NULL
            OR manifest_schema_version = 'strategy-release-manifest.v1'),
    CONSTRAINT chk_strategy_release_admission_timestamps CHECK (
        updated_at >= created_at
        AND (identity_bound_at IS NULL OR identity_bound_at >= created_at)
    )
);
```

同时需要支持 dataset reverse mapping：

```sql
CREATE INDEX idx_backtest_runs_dataset_snapshot_id
    ON backtest_runs ((dataset_snapshot_json ->> 'datasetId'));
```

### PK / FK / UNIQUE / INDEX decision

- PK：`publish_record_id`；同时保证 one-row-per-publish。
- FK：`ON UPDATE RESTRICT / ON DELETE RESTRICT`；guarded publish identity 不允许普通 rekey/delete。
- UNIQUE：除 PK 外不新增。禁止无业务 invariant 的 `UNIQUE(release_artifact_digest)` 或 `UNIQUE(manifest_fingerprint)`；相同 content 可以合法对应不同 publish release。
- Index：state PK 已覆盖 `FOR UPDATE`；不为 digest/fingerprint 增加索引。新增 run dataset snapshot expression index，避免 dataset mutation fan-out 时无边界扫描 `backtest_runs`。
- COMMENT：V38 必须为新表、每个字段、约束、function 和 trigger 提供中文业务注释；明确不保存 path、URL、raw manifest、credential、token、cookie 或交易授权。

## 7. Admission revision contract

```text
field: admission_revision
type: BIGINT
initial: 0
rule: monotonic; never decrement; never reuse
overflow: transaction aborts and fails closed
```

- `0` 精确表示 state row 已初始化，但尚无任何已提交的 post-initialization admission generation；它不表示 content identity 已验证。
- readiness 单独由完整 identity quartet 决定；即使 revision 大于 0，只要 identity 仍为 NULL，仍返回 `ADMISSION_GUARD_UNINITIALIZED`。
- first verified binding 必须在锁定 state row 后写入完整 identity quartet，并将 revision 递增至少 1。
- 每个 admission-sensitive committed mutation 对受影响 publish 的 revision 至少递增 1；bulk statement 可对同一 publish 递增多次，正确性只依赖 monotonic change，不依赖“每事务恰好 +1”。
- 禁止客户端赋值 revision；禁止使用 timestamp、`updated_at`、`MAX(child_id)`、Shadow version 代替。

## 8. Release content identity

### 8.1 Artifact digest ownership

- `strategy_release_admission_state.release_artifact_digest` 是 release-side source truth。
- 格式固定为 64 位 lowercase SHA-256。
- 语义固定为 GateX-1 `TrustedRootStrategyArtifactVerifier.computeArtifactDigest` 的 canonical artifact descriptor set identity。
- `shadow_runs.artifact_digest` 只是一次 materialization 的 provenance snapshot；必须等于 release-side digest，但不能反向回填或覆盖 release truth。

### 8.2 Manifest fingerprint contract

算法/version 固定为 `strategy-release-manifest-fingerprint.v1`，由 `guard_schema_version=1` 选择。canonical input 固定为：

1. domain separator `strategy-release-manifest-fingerprint.v1`；
2. manifest `schemaVersion`；
3. `strategyVersionId`；
4. canonical lowercase UUID `datasetId`；
5. `evaluationId`；
6. `artifactDigest`；
7. `generatedAt` 的 UTC epoch-second 与 nano；
8. `generatorVersion`；
9. artifact count；
10. 按 `logicalName`、`relativePath` 排序后的每个 descriptor：`logicalName / relativePath / sha256 / sizeBytes / mediaType`。

编码要求：每个字段使用 type/name tag、presence byte 和 length-prefixed UTF-8 bytes；整数使用固定宽度 big-endian；禁止拼接歧义；禁止对 raw manifest JSON bytes 直接 hash；禁止依赖 JSON 字段顺序、空白、locale 或时间字符串精度。输出为 lowercase SHA-256。

`manifest_schema_version` 保存 manifest contract version `strategy-release-manifest.v1`，与 fingerprint algorithm version 不混用。改变 fingerprint input/encoding 必须提升 `guard_schema_version`；unknown version 一律 `ADMISSION_STALE`。

## 9. Release binding lifecycle 与 immutability

### First binding

唯一允许的 first binding 时点：

```text
server-owned locator resolution
→ bounded manifest parse
→ manifest contract/provenance verification
→ full artifact verification success
→ lock admission state
→ reload current release facts
→ identities still NULL
→ persist digest/fingerprint/schema/boundAt + revision++
```

绑定前还必须验证 publish `SUCCEEDED`、V37 locator pair 完整、manifest strategy/dataset/evaluation 与 DB current facts一致。

### Immutability enforcement

- V38 必须新增 `BEFORE UPDATE` trigger：identity quartet 从完整值变为任何不同值、NULL、不同 schema version 或不同 timestamp 时以 SQLSTATE `23514` 拒绝。
- 只允许一次完整 `NULL/NULL/NULL/NULL → non-NULL quartet`；不允许 partial bind。
- `guard_schema_version` 与 `publish_record_id` 不允许 ordinary UPDATE。
- repository 不暴露 generic identity update；first binding 使用专用 typed method，并要求 state row lock、server verification result 与 current fact reload。
- DB trigger 负责不可重绑 hard gate；repository contract 负责证明 first binding 来源。两层缺一不可。
- forward remediation 不允许 ordinary UPDATE；内容改变必须创建新 release。若未来确需修复错误 binding，必须单独 security review、专用 audited remediation path 和新 migration，不得复用本表普通 writer。

## 10. Historical compatibility

V38 对所有已有 publish 做 state-row initialization：

```sql
INSERT INTO strategy_release_admission_state (
    publish_record_id,
    admission_revision,
    guard_schema_version,
    release_artifact_digest,
    manifest_fingerprint,
    manifest_schema_version,
    identity_bound_at
)
SELECT publish_record_id, 0, 1, NULL, NULL, NULL, NULL
FROM backtest_publish_records
ORDER BY publish_record_id;
```

- 允许初始化 state row；禁止 backfill artifact/manifest identity。
- 禁止从 `shadow_runs.artifact_digest`、filesystem scan、locator、publish id、manifest regeneration 或历史 JSON 猜测 identity。
- historical row 精确状态为 `LEGACY_RELEASE_IDENTITY_UNBOUND / ADMISSION_GUARD_UNINITIALIZED`。
- first safe verification/binding path与新 row 相同；verification 失败保持 NULL/fail-closed。

## 11. Mutation coverage matrix

| Admission-sensitive fact | Production mutation path | `publishRecordId` resolution | Revision/atomicity contract |
| --- | --- | --- | --- |
| publish row INSERT | `BacktestPublishService` → `JdbcBacktestPublishRecordRepository.upsert` insert branch | `NEW.publish_record_id` | `AFTER INSERT` 仅创建 state row，revision=`0`；不存在旧 guard，不额外 bump |
| publish binding/status/snapshot/locator UPDATE | 同一 upsert conflict branch | existing `publish_record_id`；PK rekey被 state FK RESTRICT | application 先锁 state；DB trigger 同 transaction bump |
| publish DELETE | 当前无 production delete | state PK/FK ownership | `ON DELETE RESTRICT` 拒绝普通 delete；无 committed mutation，无 revision reuse |
| evaluation INSERT/UPSERT/UPDATE | `BacktestEvaluationService` → `JdbcBacktestEvaluationReportRepository.upsert` | `backtest_run_id → backtest_publish_records.backtest_run_id`，并覆盖 OLD/NEW run id | trigger 在同 transaction bump；publish 尚不存在时无需旧 guard，后续 publish 初始化 |
| evaluation DELETE | 当前无 production delete；DB schema允许受 FK 限制 | OLD `backtest_run_id` / `eval_report_id` → publish | BEFORE/AFTER delete trigger；若 FK 拒绝则整个 bump 回滚 |
| strategy version INSERT | `StrategyVersionService` → `JdbcStrategyVersionRepository.insert` | insert 时通常无 publish | 不需 bump；后续 publish 初始化 state |
| strategy version status/content UPDATE/DELETE | 当前无 production update/delete；schema/direct SQL 可发生 | `backtest_publish_records.strategy_version_id` | fan-out IDs 排序锁定；trigger 同 transaction bump；DELETE 若 FK 失败则回滚 |
| dataset INSERT | `MarketdataDatasetService` → repository insert | insert 时无 released run | 不需 bump；后续 publish 初始化 state |
| dataset availability/quality UPDATE | `MarketdataDatasetService.refreshQuality` → `updateQuality` | expression index 查 `backtest_runs.dataset_snapshot_json.datasetId`，再按 `backtest_run_id` join publish | fan-out IDs 排序锁定；trigger 同 transaction bump |
| dataset DELETE | 当前无 production delete | 同上 | trigger bump；existing FK 可能 RESTRICT，失败则 bump 回滚 |
| backtest run immutable input/window facts | `JdbcBacktestRunRepository.insert`；当前 updateExecution 不改 snapshot | `backtest_publish_records.backtest_run_id` | publish 前 insert 无旧 guard；任何已发布 run UPDATE/DELETE 由 trigger保守 bump，覆盖 direct/future writer |
| backtest config current binding/window | `JdbcBacktestConfigRepository` writers | current admission 不读取 config current row，而读取 run snapshot | 不 bump；若 future admission 改读 config，必须提升 guard schema并加入 trigger，不能静默扩展 |
| Paper INSERT | `PaperTradingRunService.create` → repository insert | `NEW.publish_id` | application 先锁 state；trigger bump；覆盖 empty-set phantom |
| Paper status/`updated_at` UPDATE | `PaperTradingRunService.start/stop` → `updateStatus` | OLD/NEW `publish_id` | trigger bump；覆盖 status 与 reorder |
| Paper DELETE/rekey | 当前无 production path | OLD/NEW `publish_id` union | trigger bump；覆盖 latest removal/reassignment |
| Shadow INSERT（含 materialization） | `ShadowRunMaterializationWriter`、既有 runner → repository create | non-null `NEW.publish_id` | `shadow_runs` trigger是唯一 bump owner；CREATED insert恰好 bump一次 |
| Shadow status/version/`updated_at` UPDATE | `JdbcShadowRunFactRepository.updateStatus` | OLD/NEW `publish_id` | trigger bump；首次离开 CREATED 和全部后续 evidence-bearing transition均覆盖 |
| Shadow DELETE/rekey | 当前无 production delete/rekey | OLD/NEW `publish_id` union | trigger bump；相关 FK失败则事务整体回滚 |
| Shadow CREATED event | materialization writer → `appendEvent` | event → shadow run → publish | 不单独 bump；必须与 run insert/event/state bump同一 writer transaction，避免 +2 |
| 其他 Shadow event/snapshot | runner/repository append | 不参与 current latest decision | 不 bump；若 future admission读取，必须提升 guard schema并新增覆盖 |
| consistency report INSERT | `ShadowConsistencyReportService` → `createConsistencyReport` | `report.shadow_run_id → shadow_runs.publish_id` | trigger 同 transaction bump；覆盖 empty consistency set 与 append phantom |
| consistency UPDATE/DELETE | 当前无 production writer | OLD/NEW shadow id → publish union | trigger bump；覆盖 latest replacement/removal |
| release content first binding | future dedicated server binding repository | direct state PK | state row `FOR UPDATE` 下完整 bind + revision++，同一 transaction |
| filesystem replacement under same locator | repository外 filesystem mutation，不是 DB DML | persisted release identity | 不伪造 DB bump；evaluate/writer/未来 runner必须重验 current bytes 与 persisted digest/fingerprint，不匹配 fail-closed |

### Uncovered mutation paths

- production runtime DML：无未映射路径。
- direct/admin SQL：由 DB trigger兜底；不允许把 trigger移除后依赖人工约定。
- migration/backfill SQL：未来任何触及上述表的 migration 必须显式评估 trigger、revision、bulk lock 和 guard schema；migration role 不得长期 disable trigger。
- 当前没有 strategy status、dataset delete、Paper delete、Shadow delete、consistency update/delete 的 production repository method；仍纳入 DB contract，避免 future/raw writer绕过。

## 12. Trigger vs application protocol

### DB trigger review

优点：

- 与 source mutation 同一 PostgreSQL transaction，天然覆盖 repository、raw JDBC 与受控 admin SQL。
- 覆盖 latest empty-set、insert phantom、delete 和未来 writer，不依赖 application event。
- 失败、overflow、state missing 或 identity invariant异常会回滚 source mutation，fail-closed。

风险：

- strategy/dataset 是一对多 fan-out，必须按 `publish_record_id` 排序锁 state rows；禁止 planner-dependent 无序批量锁。
- row trigger在 bulk DML 中可能多次 bump；这是允许的，但测试不得断言“一个 transaction 只 +1”。
- source row 已被锁后再由 trigger锁 state可能与 state-first writer形成 cycle；只靠 trigger不满足统一 lock ordering。
- raw SQL 若绕过 application pre-lock，允许被 deadlock detector/lock timeout终止，但不得静默 commit无 bump。

### Application protocol review

优点：

- production writer可在 source mutation前解析、排序并锁所有 state rows，形成统一 lock order。
- transaction边界、fan-out上限、重试/错误映射与 contract tests更容易表达。

风险：

- 单独使用容易漏 repository、raw JDBC、migration/admin SQL。
- 当前 writers分布在 research/core/infra，不能依赖 review记忆或 best-effort event。

### Selected strategy

```text
REVISION_BUMP_STRATEGY=HYBRID
```

- Application protocol负责 **mutation前 state-first lock**。
- DB trigger/function负责 **mutation后 authoritative bump**；application不得手工再次 bump。
- 同一 shared function按 canonical sorted publish IDs执行 update；state missing必须抛错，不得自动忽略。
- ArchUnit/contract tests约束所有 production repository/service mutation先进入 admission mutation coordinator。
- DB regression直接执行 raw SQL证明 trigger仍会 bump或安全回滚。

## 13. Shadow CREATED 与 evidence lifecycle

- Admission latest Shadow selection 必须增加 `status <> 'CREATED'`；CREATED 不属于 validation evidence。
- materialization `shadow_runs(CREATED)` 成功仍必须推进 revision，使所有并发旧 guard失效。
- 唯一责任方为 `shadow_runs` INSERT trigger；writer不得 manual bump，CREATED event trigger也不得 bump。
- `CREATED → PRECHECKING` 是首次 evidence-bearing transition，`shadow_runs` UPDATE trigger再次 bump。
- PRECHECKING 之后每次 status/version/updated_at变化都 admission-sensitive，因为可能改变 latest排序或 decision。
- same-command replay不插入新 run/event，因此不 bump；different command新建 run会 bump，旧 guard返回 stale，重新 evaluate后才可合法 rerun。

## 14. Latest/empty-set phantom handling

### Paper

- INSERT、DELETE、status update、`updated_at` reorder全部 bump同一 publish state。
- 空 Paper set没有 child row可锁，但 publish state始终存在，因此新 INSERT会等待/推进 revision。

### Shadow

- 只从 non-CREATED rows选 latest evidence。
- evidence-bearing INSERT、status/version/updated_at update、delete/rekey全部 bump。
- CREATED insert虽不参加 selection，仍推进 revision以阻断旧 guard并发穿透。

### Consistency

- report append/update/delete通过 `shadow_run_id → shadow_runs.publish_id` 映射。
- 即使当前没有 report，state row仍提供可锁 aggregate；new report commit必须推进 revision。

### Empty set

- writer不锁 child history，也不依赖存在 child row。
- per-publish state row从 publish创建起始终存在；empty → first child mutation也在该 row串行化。

## 15. Lock ordering 与 deadlock assessment

统一顺序冻结为：

```text
1. resolve affected publishRecordIds without locking unbounded child history
2. sort publishRecordIds ascending
3. SELECT strategy_release_admission_state ... ORDER BY publish_record_id FOR UPDATE
4. mutate/load source fact rows
5. trigger/function bumps already-locked state rows in the same order
6. write dependent Shadow event/snapshot/report rows
7. commit
```

- GateX-5B writer只锁一个 state row；随后 reload facts、做 canonical decision/fingerprint、idempotency compare、run insert、CREATED event。
- Paper/Shadow/consistency single-publish writers锁一个 state row。
- strategy/dataset fan-out writer必须先获得受影响 publish ID 的 bounded/count检查；超过配置化上限时拒绝在线 mutation并转受控 batch，不得无界持锁。
- source mutation与外部 API/filesystem IO不得放在同一长事务；artifact verification在 guard issuance阶段完成，writer只重验必要 identity/fingerprint，不启动 artifact。
- DB trigger兜底 raw SQL若未遵循 pre-lock顺序，可能被 PostgreSQL deadlock detector或 `lock_timeout`中止；安全结果为事务回滚，不允许捕获后继续提交。
- 已识别的主要 cycle是“Shadow status updater先锁 shadow row，再等 state；writer先锁 state，再等同一 idempotency shadow row”。GateX-5A implementation必须把所有 Shadow status/update入口改为先锁 state，关闭该 cycle后方可通过 locking test。
- Review verdict：schema可实施；lock protocol未实现前 upstream P1仍 OPEN。没有发现需要第二个 aggregate lock或锁全部 child rows的理由。

## 16. Migration lock/risk

未来 V38 操作面：

1. `CREATE TABLE` 与 constraints：新表自身风险低；FK会对 `backtest_publish_records`取得引用侧锁。
2. historical state initialization：读取全部 publish rows并向新表插入同等行数；不是 identity backfill。
3. expression index：扫描 `backtest_runs`，普通 `CREATE INDEX`在 Flyway transaction内会阻塞并发写。
4. trigger/function creation：多个 source table需要短时 `SHARE ROW EXCLUSIVE` 类锁，并在单个 Flyway transaction结束前持有。
5. Flyway transaction会让前序锁持续到 commit；不得在未知 row count/长事务条件下直接部署。

实施前必须只读采集：

```sql
SELECT count(*) FROM backtest_publish_records;
SELECT count(*) FROM backtest_runs;
SELECT count(*) FROM strategy_versions;
SELECT count(*) FROM marketdata_datasets;
SELECT count(*) FROM paper_trading_runs;
SELECT count(*) FROM shadow_runs;
SELECT count(*) FROM shadow_consistency_reports;
```

并检查 long-running transaction、目标表写入速率、index size估算和锁等待。V38必须设置保守 `lock_timeout` 与 `statement_timeout`，选择受控窗口；超时即整体回滚。若目标规模不允许 transactional普通 index，必须先回到 migration review决定拆分 non-transactional concurrent index，不能在 V38中临时改语义。

### Rollback / forward-only

- migration未在任何共享环境应用前，可以撤销未提交文件。
- migration一旦应用，禁止修改 V38或执行 destructive down migration；修复使用 V39+ forward remediation。
- Flyway transaction内失败自动回滚 table/index/trigger/data initialization。
- 不删除 historical state，不回填 fake identity，不 disable trigger后继续运行。

## 17. Required V38 functions/triggers

V38 implementation至少提供以下职责；具体 SQL 名称可以遵循现有命名，但语义不得合并丢失：

1. `lock_strategy_release_admission_states(publish_ids)`：排序锁，供 application mutation coordinator使用；不 bump。
2. `bump_strategy_release_admission_states(publish_ids)`：排序、去重、`revision + 1`、更新 `updated_at`；state missing/overflow fail-closed。
3. publish state initializer trigger：new publish创建 revision 0 state；existing publish update bump。
4. release identity immutability trigger：完整 first bind后永不可普通 rebind/clear。
5. evaluation/backtest/strategy/dataset/Paper/Shadow/consistency source triggers。
6. 所有 trigger必须处理 OLD/NEW mapping union；publish id改变若被允许前必须同时锁两侧，本设计由 FK直接 RESTRICT canonical publish rekey。
7. `shadow_run_events`不配置 admission bump trigger。

## 18. GateX-5A migration work order

唯一下一任务：

```text
NQ-GATEX-5A-ADMISSION-MATERIALIZATION-GUARD-MIGRATION-IMPLEMENTATION
```

### Scope

1. 创建 V38 exact schema、constraints、comments、expression index、functions/triggers。
2. historical state-row initialization；identity全部保持 NULL。
3. 实现 typed release identity first-binding repository与 DB immutability guard。
4. 实现 application admission mutation coordinator，接线全部 production mutation paths；不新增业务 API。
5. 保持 `nq-api` 无 SQL、`nq-core` 无 JDBC、trigger/function位于 migration、JDBC实现位于 `nq-infra`。

### Mandatory tests

- Migration contract：latest version=38、table/column/comment/constraint/trigger/function完整。
- Historical initialization：每个 publish恰好一行、revision 0、identity NULL、uninitialized/fail-closed。
- First bind：verified complete bind成功并 bump；partial bind、uppercase digest、unknown schema、rebind/clear失败。
- Mutation coverage：publish/eval/backtest/strategy/dataset/Paper/Shadow/consistency的 INSERT/UPDATE/DELETE 各自真实 PostgreSQL验证 revision变化或受约束拒绝。
- Raw JDBC：绕过 application coordinator仍由 trigger bump/rollback，不能 silent commit。
- Latest phantom：Paper empty insert、old row reorder、Shadow non-CREATED insert/status、consistency append/delete。
- Shadow CREATED：run insert + event只 bump一次；CREATED→PRECHECKING再次 bump。
- Locking：same/different fact mutation与 writer state lock；fan-out sorted order；deadlock test无 cycle，timeout路径全回滚。
- Atomicity：source mutation失败不 bump；bump失败不提交 source；event失败回滚 run/revision/event。
- Performance：dataset reverse mapping使用 expression index；fan-out受配置化上限与批处理策略约束。

### Acceptance

- V38 implementation/review与真实 PostgreSQL regression通过前，不得进入 GateX-5B。
- 不运行 Shadow、不启动 runner/scheduler、不访问 credential/private endpoint、不触达交易/LIVE。

## 19. GateX-5B handoff contract

V38 implementation与独立 review完成后，GateX-5B实施：

```text
evaluate:
  read r0 + persisted identities
  load DB facts
  verify current manifest/artifacts against persisted identities
  canonical decision + AdmissionGuard fingerprint
  read r1
  r0 != r1 => ADMISSION_STALE

writer:
  lock state row FOR UPDATE
  compare guard schema/revision/digest/fingerprint
  reload current facts
  recompute fingerprint + canonical decision
  validate idempotency/conflict
  insert CREATED run
  append exactly one CREATED event
  shadow_runs trigger advances revision once
  commit atomically
```

GateX-5B还必须关闭 rejected review测试矩阵：

- V37 stale assertion repair；
- ADMIN WebMvc allowed；
- different-command true concurrency；
- full provenance conflict matrix与 existing row unchanged；
- run/event/revision rollback；
- POST 400/404/409/422；
- latest Paper/Shadow/consistency phantom races；
- unknown guard schema fail-closed；
- same-command network retry event count=1；
- artifact/manifest replacement mismatch且 runner invocation=0。

## 20. Findings

### P0

- 无。未触达 production、LIVE、真实交易、credential、private endpoint、runner或外部写服务。

### P1

1. 上游 `ADMISSION_MATERIALIZATION_FACT_TEAR` 仍为 OPEN；本 review只选择 schema并冻结 protocol，没有实施 V38/GateX-5B，当前 staged GateX-5不得 accepted/commit。
2. `HYBRID` state-first lock尚未接入现有 publish/eval/dataset/Paper/Shadow/consistency writers；在 implementation与真实 PostgreSQL locking regression前禁止 partial guard implementation。

### P2

1. V38 expression index与多表 trigger在 transactional Flyway中有写阻塞风险；实施前必须采集row count/long transaction并选择受控窗口。
2. dataset reverse mapping来自 JSON snapshot expression；V38必须创建并验证 expression index，后续如提升为正式 relational column需另开 schema review，不得顺手扩表。
3. bulk/fan-out mutation可能多次 bump同一 publish；测试和调用方只能依赖 monotonic change，不能依赖精确 +1，Shadow单行 CREATED例外需精确 +1。

### P3

1. current admission repository注释把部分 application-immutable snapshot描述为不可变；GateX-5B应改为 revision-guard语义。

## 21. Validation

- `git status --short`：任务开始时精确 25 个 staged baseline，`unstaged=0`、`untracked=0`。
- `git rev-parse HEAD` / `origin/dev`：均为 `7aaf6027644b2ba6cd7dc588536784be50ff1eff`。
- `git diff --check`：收口 PASS；`unstaged=0`、`untracked=0`。
- `git diff --cached --check`：baseline 与精确 staging 本 evidence 后均 PASS；收口为 26 个 staged 文件。
- `scripts/docs/check-current-authority.ps1`：`errors=0 / PASS / CURRENT_AUTHORITY_CONSISTENT`。
- `scripts/docs/check-doc-links.ps1 -Roots @('README.md','docs/current')`：`checked=211 / warnings=1 / errors=0 / PASS`；唯一 warning 为既有 `docs/current/TESTING.md → ./GATEJ_TEST_PLAN.md` historical ledger 链接，本轮未引入断链。
- Markdown trailing whitespace：`0`；敏感值模式命中：`0`。
- V1–V37与 production DML静态审计：完成；未读取 generated/credential目录。
- Maven/PostgreSQL/frontend/Python：`NOT RUN`（未运行）。本轮禁止 migration/product/test code，只新增设计 evidence；没有把历史测试写成本轮重跑。

## 22. Authority after、decision 与 next action

- Authority after保持：`accepted_batch=GateX-4 / ACCEPTED|CI_GREEN`；`work_batch=GateX-5 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；`LIVE=DISABLED`；Shadow trading=`NOT_ENABLED`。
- Human evidence：GateX-5 rejected、P1 open、Design C frozen、schema design reviewed、V38 required。
- Files created：仅本 evidence。
- Files changed：无既有文件。
- Commit recommendation：无；当前 25-file rejected/remediation baseline加本 evidence仍不得 commit。
- Rollback：仅从 index/worktree移除本 evidence；不涉及 schema、数据、生产或外部资源。
- 唯一下一动作：`NQ-GATEX-5A-ADMISSION-MATERIALIZATION-GUARD-MIGRATION-IMPLEMENTATION`。

最终决策：

```text
PASS /
ADMISSION_GUARD_SCHEMA_SELECTED /
REVISION_MUTATION_PROTOCOL_FROZEN /
V38_REQUIRED /
READY_FOR_MIGRATION_IMPLEMENTATION
```
