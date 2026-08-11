# NQ-GATEX-5A Admission-Materialization Consistency Contract Review — Attempt 01

> 任务：`NQ-GATEX-5A-ADMISSION-MATERIALIZATION-CONSISTENCY-CONTRACT-REVIEW`
>
> 归属：NQ-only
>
> 结论：`PASS / CONSISTENCY_CONTRACT_REQUIRES_PERSISTED_GUARD / SCHEMA_REVIEW_REQUIRED / NO_CODE_CHANGE`（通过 / 一致性合同要求持久化 guard / 需要 schema 审查 / 无代码修改）
>
> `SELECTED_GUARD_DESIGN=C`
>
> `SCHEMA_REQUIRED=YES`
>
> 日期：2026-08-11
>
> Starting HEAD / `origin/dev`：`7aaf6027644b2ba6cd7dc588536784be50ff1eff`

## 1. Review target 与边界

本轮只冻结 `P1 / ADMISSION_MATERIALIZATION_FACT_TEAR` 的正式一致性合同，不实现代码或 migration。

- 基线：24 个 staged 文件，包含 23 个 GateX-5 implementation 文件和 1 个 rejected review evidence；`unstaged=0`、`untracked=0`。
- Authority：`accepted_batch=GateX-4 / ACCEPTED|CI_GREEN`；`work_batch=GateX-5 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`。
- `LIVE=DISABLED`；Shadow trading=`NOT_ENABLED`。
- 禁止范围：backend production/test、Flyway、V37、frontend、API、Shadow schema、runner、scheduler、trading、LIVE、governance contract。
- 本轮唯一允许写入为本 review evidence；不得删除或覆盖 GateX-5 rejected review evidence。

## 2. P1 root cause

当前调用链为：

```text
StrategyReleaseAdmissionPreviewService.evaluate(...)
  → StrategyReleaseProductionService.verify(...)
  → JdbcStrategyReleaseProvenanceRepository
  → filesystem locator/manifest/artifact verification
  → JdbcStrategyReleaseAdmissionPreviewFactsRepository
  → StrategyValidationOverviewQueryService.evaluateDecision(...)
  → ReleaseToShadowAdmissionService.admit(...)
  → ShadowRunCreationPlan

[read-only transaction ends]

StrategyReleaseShadowRunMaterializationService
  → ShadowRunMaterializationWriter.materialize(...)
  → [new write transaction]
  → shadow_runs INSERT
  → shadow_run_events CREATED append
```

根因不是 plan 字段会被调用方修改，而是 admission 读取、filesystem verification 与 writer transaction 之间没有可验证的 stable generation：

1. `evaluate()` 结束后，publish/evaluation/Paper/Shadow/consistency facts 均可能通过合法写路径变化。
2. 当前 `ShadowRunCreationPlan` 只冻结创建数据，没有 admission fact identity、revision 或 snapshot token。
3. writer 只消费 frozen plan，不重新加载或比较 admission-sensitive facts。
4. `latest Paper/Shadow/consistency` 是排序加 `LIMIT 1` 的动态选择；单独保存旧 row id 不能证明没有更晚 row 出现。
5. release 表只持久化 locator pair，不持久化 release-side artifact digest 或 manifest fingerprint；plan 中 digest 来自当次 filesystem verification。

因此现状可能出现：

```text
facts A → ELIGIBLE
facts A′ commits before writer validation
writer still consumes plan(A)
→ CREATED Shadow Run
```

## 3. Admission fact inventory

### 3.1 实际 admission 输入

| Fact | 当前来源 | 分类 | Admission 影响 |
| --- | --- | --- | --- |
| requested `publishRecordId` | POST path / repository key | `MUTABLE` anchor | 定位 release；row 可被 publish upsert 替换主键或消失 |
| publish row identity/status | `backtest_publish_records` | `MUTABLE` | 必须存在且 `publish_status=SUCCEEDED` |
| publish→backtest/evaluation/strategy binding | `backtest_publish_records` | `MUTABLE` | 决定 release provenance；upsert 可改 strategy/evaluation/status |
| artifact/manifest locator pair | `backtest_publish_records` V37 columns | `IMMUTABLE`（首次绑定后） | 解析 server-owned artifact 与 manifest；V37 trigger 禁止 rebind/clear |
| run strategy version identity | `backtest_runs.strategy_version_id` | `DERIVED_FROM_IMMUTABLE`（当前 application path） | 必须与 publish strategy version 相等 |
| dataset identity | `backtest_runs.dataset_snapshot_json.datasetId` | `DERIVED_FROM_IMMUTABLE`（当前 application path） | release、manifest、Shadow Run dataset provenance |
| backtest window | `backtest_runs.config_snapshot_json.startTime/endTime` | `DERIVED_FROM_IMMUTABLE`（当前 application path） | Shadow Run window |
| dataset existence | `marketdata_datasets` join | `MUTABLE` | 缺失时 release provenance 不完整；Shadow insert 也受 FK 约束 |
| strategy version current status | `strategy_versions.status` | `MUTABLE` by schema | 非 `ACTIVE` 时 validation=`BLOCKED` |
| evaluation id/status | `backtest_eval_reports` | `MUTABLE` | 缺失、非 SUCCEEDED 或失败状态会阻断/拒绝 |
| validation decision | canonical `evaluateDecision(...)` | `DERIVED_FROM_MUTABLE` | `APPROVED` 才能进入 admission eligible |
| latest Paper identity/status/env | `paper_trading_runs` | `MUTABLE` + latest-row | 必须存在、`SIM`、状态为 `RUNNING` 或 `STOPPED` |
| latest Shadow identity/status | `shadow_runs` | `MUTABLE` + latest-row | `BLOCKED/FAILED` 阻断；存在但无 consistency 时 stale |
| latest consistency identity/status | `shadow_consistency_reports` | `MUTABLE` + latest-row | `FAILED` 阻断；diverged/partial/not-comparable 需人工复核 |
| authorization boundary | repository constant `DIAGNOSTIC_ONLY` | `IMMUTABLE`（代码常量） | admission safety hard gate |
| six no-side-effect flags | repository constant | `IMMUTABLE`（代码常量） | 任一 false 都阻断 |
| manifest schema/provenance | bounded filesystem manifest | `FILESYSTEM_EXTERNAL` | schema、strategy、dataset、evaluation 必须与 DB facts 一致 |
| artifact content set | trusted-root filesystem | `FILESYSTEM_EXTERNAL` | 文件集、逐文件 size/SHA-256、aggregate digest 必须通过 verifier |
| release/artifact identity | DB locator + filesystem manifest/content | `DERIVED_FROM_MUTABLE` + `FILESYSTEM_EXTERNAL` | 当前没有 release-side persisted digest/fingerprint |
| `traceId` | server request context | 非 admission business fact | 只用于追踪；不参与 stale proof |
| materialization command identity | `Idempotency-Key` hash | 与 guard 独立 | 只判断 replay/rerun，不证明 admission 仍有效 |

### 3.2 Immutable facts

以下事实有可引用的代码/schema 约束：

1. **已绑定 locator pair**：V37 `trg_backtest_publish_artifact_locator_immutable` 禁止已绑定 pair 被清空或重绑；只允许 `FAILED + NULL/NULL` 首次成对绑定并转为 `SUCCEEDED`。
2. **backtest run 输入 snapshot 的当前 application 写法**：`JdbcBacktestRunRepository.insert` 创建 strategy/dataset/config snapshot；后续 `updateExecution` 只更新执行状态、时间、failure 和 summary，不更新 strategy/dataset/window snapshot。
3. **个体 row 主键在当前 repository API 中稳定**：Paper、Shadow、consistency writer 不提供修改 row identity 的业务方法；这只能证明个体 identity，不证明它仍是 latest。
4. **authorization/safety constants**：admission facts repository 固定返回 `DIAGNOSTIC_ONLY` 与六项全 true no-side-effect policy。

限制：除 locator trigger 外，多数“不可变”来自当前 application write surface，不是数据库禁止任意 SQL update。Guard revision contract 必须覆盖任何未来对 admission-sensitive snapshot 字段的合法写能力；不得仅凭注释认定永远不变。

### 3.3 Mutable facts 与真实更新路径

| Mutable fact | 修改者/路径 | 现有 identity/version | 风险 |
| --- | --- | --- | --- |
| publish identity/binding/status | `BacktestPublishService` → `JdbcBacktestPublishRecordRepository.upsert` | PK、`updated_at`；无 revision | `ON CONFLICT(backtest_run_id)` 可改 publish id、strategy/eval/status 和 snapshots |
| evaluation id/status/report | evaluation workflow → `JdbcBacktestEvaluationReportRepository.upsert` | eval id、run unique、`updated_at`；无 version | 同 run upsert 可替换 eval id/status/metrics |
| strategy current status | schema 允许 `DRAFT/ACTIVE/ARCHIVED`；当前 repository 只发现 insert，无 status update API | id、`updated_at`；无 version | schema 未冻结 status；未来/直接合法维护可改变 decision |
| dataset presence/metadata | `JdbcMarketdataDatasetRepository` insert/update | dataset id、`updated_at`；无 admission revision | existence 是 provenance fact；缺失会改变 release validity |
| Paper latest set/status | `JdbcPaperTradingRunRepository.insert/updateStatus` | paper id、`updated_at`；无 version | 新 row 或旧 row status/updated_at 变化都可改变 latest 与 sufficiency |
| Shadow latest set/status | runner/materialization create；`JdbcShadowRunFactRepository.updateStatus` | UUID、optimistic `version`、`updated_at` | 新 row、状态/version 更新时间都可改变 latest 与 decision |
| consistency latest set | `createConsistencyReport` append | UUID、generated/created time；无 aggregate generation | 新 report 使 latest 改变；当前 query 未读取 report id |

当前 `LatestDecisionFact.evidenceUpdatedAt` 是多个来源的 `COALESCE`，不是各 row 的独立 version，也不是覆盖整个 latest 集合的 generation，不能作为 stale token。

## 4. Latest-row semantics

当前选择规则：

```text
latest Paper:
  ORDER BY paper_trading_runs.updated_at DESC, paper_run_id DESC LIMIT 1

latest Shadow:
  ORDER BY shadow_runs.updated_at DESC, created_at DESC, id DESC LIMIT 1

latest consistency for latest Shadow:
  ORDER BY generated_at DESC, created_at DESC, id DESC LIMIT 1
```

只锁住 evaluate 时选中的 row 不足：

- 可以插入新的 qualifying row，形成 phantom。
- 可以更新一个旧 Paper/Shadow row 的 `updated_at`，使其越过已锁 row 成为 latest。
- 可以为 latest Shadow 插入更晚 consistency report。
- 当前查询甚至没有把 consistency report id 带入 `LatestDecisionFact`。
- 锁全部历史 child rows 才能覆盖旧 row 重新排序，但集合可能无界，且仍要解决空集合 phantom；不接受该方案。

正式语义：`latest` 必须由 per-publish monotonic admission revision 保护。任何能改变上述排序结果或 decision 输入的 INSERT/UPDATE/DELETE 都必须在同一数据库事务中推进 revision。

### 4.1 Non-evidence CREATED row

当前 query 会把新 materialization 产生的 `CREATED` run 立即选为 latest，并因缺 consistency 变为 `STALE_EVIDENCE`。这会让同 command 网络重试和 legitimate rerun 在第一次创建后无法重新取得 eligible guard。

冻结以下语义：

- admission 的 latest Shadow evidence 只选择已经离开 `CREATED`、进入 evidence-bearing lifecycle 的 run。
- `CREATED` 是 materialization command fact，不是 validation evidence；不得用它制造第二套 validation truth。
- `CREATED → PRECHECKING/...` 的首次 evidence-bearing transition 必须推进 admission revision。
- 新建 `CREATED` run 仍推进 revision，用于让并发旧 guard fail-closed；重新 evaluate 后，因它不参与 latest evidence 选择，可生成新 revision 下的 current valid guard。

该规则同时保留：同 command retry 可 replay；不同 command 可 legitimate rerun；旧 guard 不会永久授权未来创建。

## 5. Filesystem facts

### 5.1 当前保护

- trusted root 来自 server configuration，不来自 HTTP request。
- locator 是 direct-child opaque key；V37 DB trigger 保护 locator pair 不可重绑。
- resolver 使用 bounded manifest read、strict JSON、`NOFOLLOW_LINKS`、real-path containment，以及读取前后 file identity 对比。
- verifier 校验完整 declared file set、拒绝 extra/missing file、逐文件 size/SHA-256、资源上限，并比较验证前后 directory snapshot。
- aggregate `artifactDigest` 是 versioned canonical artifact-file descriptor set 的 SHA-256；它能稳定识别已验证 content set。

### 5.2 当前缺口与冻结结论

- Java NIO verifier 明确不提供 OS 级原子稳定句柄；verification 返回后 filesystem 仍可变化。
- 当前 release DB row 没有 persisted `artifactDigest`；digest 只存在于当次 manifest/result/CreationPlan，之后写入 `shadow_runs.artifact_digest`。
- aggregate artifact digest 不覆盖 raw manifest bytes 的所有 metadata；manifest identity 需要独立 canonical fingerprint。
- locator DB 不变不等于 locator 指向的 filesystem bytes 永远不变。

结论：

1. `artifactDigest` **足以作为 artifact content-set identity**，前提是它在 server-owned release binding 中被持久化、不可变，并且 verifier 将当前 manifest/files与该 persisted digest 比较。
2. `artifactDigest` **不构成 filesystem lease 或 availability proof**；materialization 不得宣称路径在 commit 时仍被锁定。
3. 需要持久化 `manifestFingerprint`，以绑定 schema/provenance/canonical manifest identity；不得把 raw manifest 放入 guard。
4. materialization 只绑定 immutable release content identity，不启动 artifact。未来任何 runner/precheck 使用 artifact 前必须再次验证 persisted digest + manifest fingerprint；变化时 fail-closed，不得执行。
5. locator pair 变化由 V37 fail-closed；同 locator 下 manifest/content replacement 由下一次 verifier 对 persisted identities 的比较 fail-closed。

## 6. Design A review — Existing fact identity compare

**判定：REJECTED（拒绝）。**

可复用事实包括 publish/evaluation/Paper/Shadow row id、status、timestamps、Shadow version 和 artifact digest，但现有字段不足以无歧义覆盖全部 admission truth：

- 没有 release-side persisted artifact digest/manifest fingerprint。
- 没有 consistency report id 进入当前 fact model。
- `evidenceUpdatedAt` 是 `COALESCE`，会丢失其他来源 version。
- latest-row phantom 不能靠锁已选 row 解决。
- 锁全部 Paper/Shadow/report 历史 rows 无界，且破坏最小锁范围。
- absence (`NONE`) 没有可锁 row，无法证明 transaction 内没有新 evidence 出现。

因此 A 不能在现有 schema 下闭合 P1。

## 7. Design B review — Canonical admission fingerprint

**判定：REJECTED AS STANDALONE；保留为 Design C 的组成部分。**

优点：

- 可以把所有 admission-sensitive identity/status 统一成确定性 hash。
- 与当前 idempotency 的 length-prefixed SHA-256 风格一致。
- 不需要持久化 raw manifest、filesystem path 或大量 snapshot payload。

单独使用仍不足：

- writer 重算 fingerprint 前后仍可能出现 latest-row phantom。
- 没有 persisted revision 时，fingerprint 读取本身没有稳定集合边界。
- 新增 admission fact 时可能漏入 canonical input；必须有 schema version 和 fail-closed upgrade policy。
- fingerprint 不能把 filesystem path 变成 DB transaction 可锁资源。

因此 B 只能用于比较同一 revision 下的完整事实，不能替代 persisted version token。

## 8. Design C review — Persisted snapshot/version token

**判定：SELECTED（唯一选择）。**

选择理由：

1. per-publish monotonic revision 能覆盖 empty/latest 集合的 insert/update/delete，而不锁大表或全部历史 child rows。
2. writer 只需锁一个 release admission state row，锁范围有界。
3. persisted artifact digest + manifest fingerprint 将 release truth 从易变 filesystem locator 提升为不可变 content identity。
4. canonical evaluator 仍是唯一 admission truth；persisted state 只证明 facts generation，不保存或复制业务 decision。
5. fingerprint 作为防漏字段比较，revision 作为并发线性化 token，两者职责清晰。
6. 与现有 Shadow provenance/idempotency 正交，不改变 raw command identity 规则。

## 9. Schema necessity gate

```text
SELECTED_GUARD_DESIGN=C
SCHEMA_REQUIRED=YES
```

现有 schema 缺失的最小持久化语义：

1. **Per-publish admission state row**：一个 publish/release 对应唯一、可 `FOR UPDATE` 的 guard state。
2. **Monotonic `admissionRevision`**：所有 admission-sensitive mutation 在同一 transaction 中推进；不得回退或由客户端赋值。
3. **Immutable `releaseArtifactDigest`**：64 位 lowercase SHA-256，来自 server artifact pipeline/verifier，不来自请求或 path 推导。
4. **Immutable `manifestFingerprint`**：versioned canonical manifest identity SHA-256，不保存 raw manifest。
5. **Guard schema version**：区分 canonical fact-set 版本；未知/旧版本默认 stale/fail-closed。
6. **Mutation coverage enforcement**：publish、evaluation、strategy status、backtest input snapshot、dataset existence、Paper、evidence-bearing Shadow 与 consistency 的 admission-sensitive INSERT/UPDATE/DELETE 必须推进对应 publish revision。

具体选择“扩展 `backtest_publish_records`”还是“新增专用 admission state 表”、索引、trigger/function、历史 row 处理与锁风险，必须由独立 schema review 决定。本轮不得创建 V38。

不得仅持久化一次 `ELIGIBLE` decision；那会制造第二套 admission truth。Persisted state 只承载 identity/revision，不替代 `StrategyValidationOverviewQueryService` 与 `ReleaseToShadowAdmissionService`。

## 10. AdmissionGuard contract

未来内部 contract 冻结为以下语义；字段名可以按项目命名规范调整，语义不得删减：

```text
AdmissionGuard {
  guardSchemaVersion
  publishRecordId
  admissionRevision

  releaseArtifactDigest
  manifestFingerprint
  manifestSchemaVersion

  backtestRunId
  strategyVersionId
  datasetId
  evaluationId
  windowStart
  windowEnd

  strategyVersionStatus
  evaluationStatus
  publishStatus

  latestPaperIdentity | NONE
  latestShadowEvidenceIdentity | NONE
  latestConsistencyIdentity | NONE

  authorizationBoundary
  sideEffectPolicyVersion
  sideEffectPolicy

  admissionFingerprint
  evaluatedAt
}
```

Identity 子结构至少包含：

- Paper：`paperRunId/status/tradeEnv/updatedAt`。
- Shadow：`shadowRunId/status/version/updatedAt`；只允许 evidence-bearing selection。
- Consistency：`reportId/comparisonStatus/generatedAt/createdAt`。

约束：

- Guard 是 **authorization snapshot proof**，不是第二个 `ShadowRunCreationPlan`。
- Guard 不包含 filesystem path、trusted root、raw manifest、raw `Idempotency-Key`、credential 或 private payload。
- `traceId` 不进入 admission fingerprint；它只用于审计。
- materialization command identity 不进入 guard；它只进入 idempotency identity。
- Guard 由 server-owned evaluate orchestration 生成，不接受客户端提交或覆盖。

## 11. Canonical fingerprint contract

`admissionFingerprint` 使用 versioned canonical SHA-256：

1. 首字段固定为 `strategy-release-admission-guard.v1`。
2. 所有字段按上述 contract 的固定顺序编码。
3. 每个字段使用 type/name tag + presence byte + length-prefixed UTF-8 bytes；`NULL/NONE` 与空字符串必须不同。
4. UUID 使用 lowercase canonical text；enum 使用 canonical uppercase name；boolean 使用 `0/1`。
5. Instant 使用 UTC epoch-second + nano 两个定长整数，不依赖 locale 或字符串精度。
6. digest/fingerprint 使用 64 位 lowercase hex。
7. latest identity 的 `NONE` 是显式 token，不是缺字段。
8. 新增、删除或改变 admission-sensitive fact 必须提升 guard schema version；writer 不认识的版本返回 `ADMISSION_STALE`，不得兼容猜测。

Fingerprint 可以在 application 内 transient 计算，并写入 CREATED audit metadata；它不需要单独成为可更新的数据库 truth。持久化的并发权威是 `admissionRevision` 与 immutable release artifact/manifest identity。

## 12. Guard issuance contract

为避免 evaluate 自身跨多个 statement 读取撕裂，future evaluate 必须执行：

```text
1. read persisted admissionRevision = r0 and immutable release identity
2. load DB admission-sensitive facts
3. resolve/verify filesystem manifest + artifact against persisted identities
4. run canonical validation/admission decision
5. read admissionRevision = r1
6. if r0 != r1 → ADMISSION_STALE / no eligible guard
7. if decision != ELIGIBLE → ADMISSION_BLOCKED / no guard
8. otherwise issue AdmissionGuard(revision=r1, fingerprint=current facts)
```

不使用 sleep、blind retry 或历史 GET preview。Revision 变化时立即 fail-closed；客户端可以发起新的 POST，由服务端重新 evaluate。

## 13. Lock/version strategy

未来 writer 使用 PostgreSQL `READ COMMITTED` 加单行显式锁即可，但前提是 schema review 证明所有 admission-sensitive mutation 都遵守同一 revision protocol。

```text
SELECT ...
FROM <release_admission_state>
WHERE publish_record_id = ?
FOR UPDATE
```

- 锁对象：恰好一个 publish/release admission state row。
- 锁时长：从 writer 开始 validation 到 run/event commit 或 rollback。
- 不锁 trusted root、filesystem、大表或全部 Paper/Shadow history。
- 其他事实 mutation 的 trigger/protocol 必须在同一 transaction 更新该 state row；若 writer 已持锁，mutation 等待。
- mutation 在 writer 获锁前已提交：writer 看到更高 revision，返回 stale。
- mutation 与 writer 重叠但尚未提交：其新事实对 writer 不可见，并在 revision row 上等待；事务等价于 writer 先完成、mutation 后完成。
- writer 自己插入新 `CREATED` run 时，同 transaction 推进 revision；CREATED event 与 revision/run 一起 commit。

若 schema review 无法为全部真实 mutation path建立上述单行 serialization，必须保持 `SCHEMA_REVIEW_REQUIRED`，不得降级为锁全部 child rows。

## 14. Atomic validate-and-insert contract

未来 writer 行为冻结为：

```text
BEGIN WRITE TX

1. SELECT persisted guard state FOR UPDATE
2. compare guard schema version, admissionRevision,
   releaseArtifactDigest and manifestFingerprint
3. mismatch → ADMISSION_STALE; no run; no event
4. load current DB admission-sensitive facts under the held revision lock
5. recompute canonical fingerprint and canonical admission decision
6. mismatch or no longer ELIGIBLE → ADMISSION_STALE/ADMISSION_BLOCKED; no write
7. perform idempotency collision/replay comparison
8. replay + same provenance/guard semantics → return existing run; no second CREATED event
9. different provenance under same command identity → IDEMPOTENCY_CONFLICT; no write
10. insert shadow_runs(CREATED, RELEASE_BOUND, startedAt=NULL, paperRunId=NULL)
11. append exactly one CREATED event with guard revision/fingerprint audit
12. materialization-created row advances admission revision in the same transaction

COMMIT
```

`validate + idempotency compare + insert + CREATED event + revision advance` 必须处于同一真实 PostgreSQL transaction。不得先 validate commit，再开另一个 insert transaction。

## 15. Stale-admission behavior 与 error contract

稳定区分：

| Error | 语义 | 写入 | Retry |
| --- | --- | --- | --- |
| `ADMISSION_BLOCKED` | 当前 canonical facts 明确不允许创建 | run=0/event=0 | 只有 facts 合法变化后重新 evaluate |
| `ADMISSION_STALE` | guard revision/identity/fingerprint 已不是 current | run=0/event=0 | 安全；必须 server-side 重新 evaluate，不复用旧 ELIGIBLE |
| `IDEMPOTENCY_CONFLICT` | 同 command identity 已绑定不同 provenance/guard semantics | run unchanged/event unchanged | 更换 key 不能掩盖业务冲突；需人工检查 |

HTTP 设计建议：`ADMISSION_BLOCKED=422`、`ADMISSION_STALE=409`、`IDEMPOTENCY_CONFLICT=409`，以稳定业务错误码区分；本轮不实现 API mapping。

不得返回 current raw facts、SQL、filesystem location、manifest、guard canonical bytes 或内部 exception message。

## 16. Admission-to-write race closure proof

在 Design C contract 下：

1. evaluate 只有在 `r0 == r1` 且 filesystem verification 与 persisted release identities 相符时签发 guard。
2. writer 首先锁定同一 publish 的 persisted state row。
3. writer 比较 revision + immutable release identity，再加载 current facts并重算 fingerprint/decision。
4. writer 持锁期间，所有能改变 admission facts/latest selection 的 mutation 都不能提交新的 revision。
5. mismatch 在任何 insert 前返回 `ADMISSION_STALE`。
6. match 后，run insert、CREATED event 与 revision advance 原子提交。

所以成功创建可线性化在 guard-state row lock 持有期间：写入使用的 canonical facts 与验证的 facts 是同一 revision；不存在 facts A admission 后以 facts A′ 写入的路径。

Filesystem 不由 PostgreSQL 锁定。Race closure 的边界是：guard 绑定 immutable persisted content identities，而不是 live path；materialization 不启动 artifact。任何后续执行必须重新验证 persisted digest/fingerprint，否则 fail-closed。

## 17. Legitimate rerun 与 idempotency interaction

```text
AdmissionGuard != materialization command identity
```

- Guard 回答“当前 admission facts 是否仍有效”。
- Idempotency 回答“这次命令是否为同一请求的 replay”。
- 相同 command + current valid guard：返回同一 run，不追加第二个 CREATED event。
- 不同 command + current valid guard：允许创建另一个 legitimate run。
- 并发不同 command：只有持有 current revision 的第一个 writer直接成功；另一个旧 guard返回 `ADMISSION_STALE`。重新 evaluate 取得新 revision 后可合法创建第二个 run。
- 每次成功创建都推进 revision；旧 guard 永久失效，不能授权未来 rerun。
- `CREATED` row 不作为 validation evidence，但仍推进 revision，因此既保留 replay/rerun，又防止两个旧 guard并发穿透。

## 18. GateX-5B acceptance checklist

本轮不修以下项目；统一进入后续 implementation/acceptance：

1. V36→V37 stale migration assertions。
2. ADMIN WebMvc allowed。
3. Different-command PostgreSQL concurrency：验证一个旧 guard成功、另一个 stale，重新 evaluate 后第二个合法创建。
4. Full provenance conflict matrix 与 existing row unchanged。
5. Forced event failure 后 run row=`0` 且 event row=`0` 的显式断言。
6. POST 400/404/409/422 WebMvc matrix，并新增稳定 `ADMISSION_STALE` contract。
7. Frontend legitimate-rerun UX。
8. Revision mutation coverage：publish/eval/strategy/backtest/dataset/Paper/Shadow/consistency 全部真实 PostgreSQL tests。
9. Latest absence/phantom、旧 row 更新时间越位、新 consistency insert 三类 race regression。
10. Guard issuance `r0 != r1` fail-closed。
11. Unknown guard schema version fail-closed。
12. Artifact/manifest replacement后 persisted identity mismatch fail-closed；不得启动 runner。
13. Same-command network retry 在已有 CREATED row 后仍返回同一 run、CREATED event=`1`。
14. `validate + insert + event + revision` transaction rollback 原子性。
15. `ModuleBoundaryArchTest` 与 `PackageBoundaryArchTest`。
16. focused reactor、full backend 与真实 PostgreSQL全量回归。

## 19. Findings

### P0

- 无。本轮没有 LIVE、交易、credential/private exchange、runner/scheduler 或外部写操作。

### P1

1. `ADMISSION_MATERIALIZATION_FACT_TEAR` 仍为 OPEN；本轮冻结关闭它所需的 contract，但没有实现 schema/code，GateX-5 仍不得 accepted/commit。
2. 现有 schema 缺少 persisted release digest/manifest fingerprint 与 per-publish admission revision；不得直接进入 GateX-5B code implementation而跳过 schema review。

### P2

1. GateX-5 rejected review 中列出的测试矩阵缺口全部转入 GateX-5B acceptance checklist，本轮按要求不修。
2. 当前 latest Shadow query 把 non-evidence `CREATED` row 当作 validation evidence；后续必须按本 contract 修正 selection 语义并回归 retry/rerun。

### P3

- 部分代码注释把同一查询中的 window 或 release facts称为“不可变”，但除了 V37 locator 外，多数没有 DB immutable constraint。后续实现应更新注释为准确的 revision-guard 语义。

## 20. Validation

- `git status --short`：进入任务时 24 个 staged baseline，`unstaged=0`、`untracked=0`；写入后只新增本 evidence。
- `git diff --cached --check`：基线 PASS；精确 staging 本 evidence 后收口 PASS。
- `git diff --check`：收口 PASS。
- `scripts/docs/check-current-authority.ps1`：`errors=0 / PASS / CURRENT_AUTHORITY_CONSISTENT`。
- `scripts/docs/check-doc-links.ps1 -Roots 'README.md','docs/current'`：`errors=0 / PASS`；保留 1 个既有 `TESTING.md → GATEJ_TEST_PLAN.md` historical ledger warning，本轮未引入新断链。
- Markdown trailing whitespace：`0`；敏感值模式命中：`0`。
- Maven/PostgreSQL/frontend tests：`NOT RUN`（未运行）。本轮是 review-only contract evidence，禁止修改 code/test/schema；沿用 GateX-5 rejected review 的动态证据作为问题输入，不把历史运行写成本轮重跑。

## 21. Authority、变更与下一动作

- Authority after 保持合法现状：`GateX-5 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`。
- Human evidence 记录：GateX-5 review rejected、P1 open、consistency contract review completed、schema review required。
- 当前 governance contract 无法合法映射未提交 GateX-5 rejection/remediation action；不修改 governance contract，不伪造 commit/CI，不同步 machine next action。
- Files created：仅本 evidence。
- Files changed：无 production/test/migration/frontend/current authority 变更。
- Migration requirement：`YES`，但本轮 migration 变更=`0`。
- 下一 work order：`NQ-GATEX-5A-ADMISSION-MATERIALIZATION-GUARD-SCHEMA-REVIEW`。
- Commit recommendation：无；当前 24-file rejected baseline 加本 contract evidence 仍不得提交。
- Rollback：删除/撤销本 evidence 即可；不涉及 schema、数据或外部资源回滚。

最终决策：

```text
PASS /
CONSISTENCY_CONTRACT_REQUIRES_PERSISTED_GUARD /
SCHEMA_REVIEW_REQUIRED /
NO_CODE_CHANGE
```
