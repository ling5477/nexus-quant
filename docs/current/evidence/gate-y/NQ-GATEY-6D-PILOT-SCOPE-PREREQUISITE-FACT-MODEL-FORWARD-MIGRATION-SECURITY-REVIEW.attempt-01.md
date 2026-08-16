# NQ-GATEY-6D-PILOT-SCOPE-PREREQUISITE-FACT-MODEL-FORWARD-MIGRATION-SECURITY-REVIEW — attempt-01

## Task classification

- ownership：NQ-only。
- type：`INDEPENDENT_MIGRATION_SECURITY_REVIEW / DATA_INTEGRITY_REVIEW / CONCURRENCY_REVIEW`。
- level：L 级高风险 migration 独立审查；仅允许修复本轮发现的 P0/P1，不重新设计 schema。
- final result：`PASS / GATEY_6D_V40_MIGRATION_SECURITY_REVIEW_ACCEPTED / P0_0 / P1_0 / NO_FAKE_BACKFILL / JAVA_POSTGRES_CANONICAL_PARITY_ACCEPTED / CONCURRENCY_ACCEPTED / APPROVAL_COMPATIBILITY_ACCEPTED / V39_TO_V40_ACCEPTED / V1_TO_V40_ACCEPTED / EXECUTION_INTENT_0 / OKX_CALL_0 / EXCHANGE_MUTATION_0 / LIVE_DISABLED / READY_TO_COMMIT`。

## Starting baseline and reviewed diff

- branch=`dev`；`HEAD == origin/dev == 3544b70e877dde40908e369baaed8f1b312cfa30`。
- machine authority before/after 均为 accepted=`GateY-6C / ACCEPTED|CI_GREEN`，work=`GateY-6D / NOT_STARTED / NONE / NOT_RUN`；LIVE=`DISABLED`，kill switch=`ENGAGED`。
- 任务要求基线 `staged=0`，实际进入独立 review 时已有 10 个 V40 production/migration 文件 staged。该偏差已显式保留；review 未执行 stage/unstage/commit/push，也未覆盖用户 index 状态。
- staged、unstaged 与 untracked dirty files 经逐项核对，均属于 V40 implementation、其回归测试或允许的 review evidence；frontend、research、scripts、deploy、`.github`、`STATUS.md`、`ROADMAP.md` diff=`0`。
- reviewed production diff：V40 migration；pilot scope/observation domain、canonical/freshness/preflight；repository port/JDBC/transaction service；approval domain/JDBC compatibility。
- reviewed test diff：canonical/freshness、migration contract、required PostgreSQL upgrade/replay/rollback/idempotency/concurrency/approval compatibility，以及既有 PostgreSQL fixture 的 V40 target/search-path 兼容。
- reviewed docs diff：implementation evidence、`DB_SCHEMA.md`、`TESTING.md`、`WORKLOG.md` 与 GateY evidence index；review 只新增本文件并最小追加三个 evidence ledger/index 入口。

## Review evidence checked

- authority 与合同：`STATUS.md` machine block、GateY-6D forward-migration work order、implementation evidence。
- migration：V40 全文、V1～V39 Git diff、三表/approval columns、functions/triggers/constraints/comments、timeout 与 Flyway transaction semantics。
- code：Java canonical encoders、freshness/preflight policy、approval validity、JDBC SQL、四类 transaction orchestration 与 architecture boundaries。
- tests：golden parity、逐字段 mutation、excluded-field invariant、negative constraints、no-fake-backfill、V39→V40、V1→V40、failure rollback、idempotency/concurrency、approval matrix、GateY regressions 与 ArchUnit。
- runtime boundary：只扫描本轮新增/修改 backend 行并分类 comment/type/test/production；未读取 credential material，未执行网络、交易所或生产操作。

## Findings and minimal fixes

### P0

- final open=0；未发现 fabricated historical fact、runtime mutation reachability、credential/tenant boundary破坏或不可回滚数据破坏路径。

### P1

Review 初始发现 2 个关联 P1，均已最小修复并完成 focused + full backend 回归；final open=0。

1. `PilotScopeFactTransactionService.preflight()` 未查询 exact valid pilot approval；仅有 fresh complete observations 时可能返回 `eligible=true`。
   - fix：preflight 在同一 `REPEATABLE READ` transaction、同一 DB `decisionAt` 下调用 `findValidPilotApproval()`；缺失或过期时返回 `APPROVAL_MISSING_OR_EXPIRED`，保持 fail-closed。
2. approval validity 未拒绝 `approved_at > decisionAt`；future-dated approval 可在批准时刻之前被当成有效。
   - fix：legacy/pilot domain `validFor` 增加 `approvedAt <= now`；legacy/pilot JDBC query 同步增加 `approved_at <= decisionAt`；新增 future-dated pilot approval 回归。

关联测试夹具在新增 future-dated 持久事实后，将 pilot approval count 从 1 修正为 2；有效 approval lookup 仍精确命中当前 valid row，future row 在 decision time 前不可用。

### P2

- 1 个允许残余：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`。disposable PostgreSQL 的 V39→V40 小 fixture 与约 5 秒 lock-timeout failure path 只证明 bounded local behavior，不构成 production SLA 或目标规模测量。

### P3

- 0。

## V40 schema and historical compatibility

- 三表存在且职责清晰：`pilot_scope_bindings` immutable；`pilot_prerequisite_observations` append-only typed facts；`pilot_instrument_observation_items` append-only exact instrument set。
- PK、FK、exact composite FK、unique、variant/range/digest/schema CHECK、UPDATE/DELETE deny、deferred complete-set/item/digest/payload validation、table/column comments 齐全。
- `operator_approvals.scope_schema_version` 与 `pilot_scope_id` forward-additive；历史行确定性保留 `approval-scope.v1 + NULL`，无 legacy/NULL/schema-downgrade/scopeHash-only fallback。
- approval exact binding 为 `(session_id, pilot_scope_id, scope_hash)`；creator≠approver、role、release/risk digest、expiry≤execution window、decision time 与 scope change 均 fail-closed。
- V1～V39 diff=`0`；V40 SHA-256=`1c0e486db0f3db4cdf250cb99ab0ed1e289f42d1ed522981272ee8b4c4da25e3`。

## No fake backfill

- V39→V40 后历史 `live_sessions` 不生成 scope；observations/items 均为 0。
- 不生成 fake digest、placeholder source、fabricated `observedAt` 或 synthetic approval。
- 历史 approval row count 与旧字段 fingerprint 逐值不变；新增 compatibility fields 精确为 `approval-scope.v1 / NULL`。
- decision：`PASS / NO_FAKE_BACKFILL`。

## Canonical hash and observation integrity

- Java/PostgreSQL 均固定 `pilot-scope.v1`、字段顺序、UTF-8、UTC microsecond instant、decimal、uppercase symbol 与 lowercase SHA-256；不依赖 serializer/Map/jsonb 默认顺序。
- golden payload/hash byte-for-byte parity、DB reconstruction、supplied hash mismatch rejection、所有 immutable 字段 mutation 改 hash、fresh observation excluded fields 不改 scope hash均通过。
- incomplete/duplicate observation type、missing/extra/mismatched symbol、instrument/observation digest mismatch、type-specific column混用、future/stale、insufficient balance、clock skew、malformed schema/source identity 均 fail-closed。
- immutable constraint change 不可覆盖旧 scope；必须使用新 session/scope/approval。fresh observation 只 append，selection 使用单一 DB decision time 与 deterministic ordering。

## Idempotency, concurrency, and transaction safety

- scope same session+same payload 收敛同一事实；different payload 返回 `PILOT_SCOPE_MATERIALIZATION_CONFLICT`；并发由 unique/row locking 产生单一 winner。
- observation same stable identity+same payload 收敛原事实；different payload 返回 `PREREQUISITE_OBSERVATION_IDENTITY_CONFLICT`；完整 set 原子提交，未吞 unique violation 创建第二 identity。
- materialization/approval/refresh/preflight 四类短事务边界明确；deferred constraints 在 commit 前执行，失败不留 partial rows。
- preflight 使用 `REPEATABLE READ`、单一 DB `decisionAt`、exact valid approval 与 deterministic latest complete observation set；approval/scope race fail-closed。
- transaction path 无 HTTP、credential、provider、worker 或 exchange IO。

## Migration upgrade, replay, and rollback

- environment：disposable PostgreSQL 17.7，loopback port `55440`，database=`nq_gatey6d_review`；未连接生产数据库。
- V39→V40：PASS；review run 小 fixture约 `177ms`；historical rows/fingerprint不变，new fact rows=0。
- V1→V40 full replay + Flyway validate：PASS；40 migrations applied，current version=`40`。
- failure rollback：持有 `operator_approvals` 冲突锁时，V40 在 final focused run约 `5088ms` 触发 bounded lock timeout；Flyway transaction rollback，不留 partial tables/columns/history。
- migration checksum 与 implementation baseline 一致；未修改 V40 SQL。
- lock measurement classification=`DISPOSABLE_POSTGRES_MEASUREMENT`；`PRODUCTION_LOCK_WINDOW`=`NOT_MEASURED`。

## Architecture and trading boundary

- livecontrol owns scope；source domains保持各自 SoR；account/credential、release、runtime risk、order/trade/position/ledger ownership未复制。
- JDBC只位于 infra；未新增非法跨-domain application DTO依赖；ArchUnit通过。
- added-line/exact-path classification：credential access、OKX call、ExecutionIntent creation、ExecutionReceipt creation、PLACE、CANCEL、TRANSFER、WITHDRAW、worker start、real-provider wiring、LIVE enable、kill disengage均=`0`。
- code/test中 `credentialReference` 是 immutable session identity/fake fixture；migration与service中的 ExecutionIntent/credential/provider文字是明确否定性 comment；无 runtime call 或 capability 扩张。

## Validation

| Command / suite | Result |
| --- | --- |
| focused GateY-6D/GateY-2/3/4/6C + ArchUnit + required PostgreSQL | final `BUILD SUCCESS`；nq-core=`44/0/0/0`、nq-infra=`23/0/0/0`、nq-app=`23/0/0/0` |
| `ValidationReviewFlywayPostgresIntegrationTest` database preparation | `1/0/0/0`；将 disposable public schema V1→V40，未触达生产 |
| final full backend required PostgreSQL Maven | 23/23 modules `BUILD SUCCESS`；`nq-app=284 tests / 0 failures / 0 errors / 24 existing conditional skips`；全 reactor failures/errors=`0/0` |
| V1～V39 / forbidden areas / trading reachability scan | migration diff=`0`；frontend/research/scripts/deploy/`.github`=`0`；runtime mutation reachability=`0` |

Known non-final failures/warnings均保留：

- P1 修复后的前两次 focused 编译分别发现 JDBC SQL 多余右括号、测试缺少 `assertFalse` import；修正后进入测试。
- 第三次 focused 仅因新增 future approval 后旧 count 断言仍为 1 而失败；断言按两条持久 approval事实修正后最终通过。
- 首次 full Maven 在 disposable public schema尚未预迁移时，两个既有 infra smoke 查询表得到 `null`；运行仓库既有 Flyway integration 将 public schema V1→V40 后，以相同 full command复跑通过。这是测试环境准备 RCA，不是产品代码修复。
- 既有 Mockito dynamic-agent、SLF4J NOP、deprecation/unchecked 与 conditional skips 为非阻断 warning；未把 skipped/failed 写成 passed。

未运行 frontend/Python/E2E：对应 diff=`0`，不在本 migration review 范围。未运行 production lock measurement、credential access、OKX probe、真实 pilot materialization、真实 approval、worker、deploy 或任何交易路径。

## Authority and decision

`STATUS.md` / `ROADMAP.md` 未修改；machine authority继续：

```text
accepted_batch=GateY-6C
accepted_batch_status=ACCEPTED|CI_GREEN

work_batch=GateY-6D
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN

live=DISABLED
kill_switch=ENGAGED
```

Review decision：`PASS / GATEY_6D_V40_MIGRATION_SECURITY_REVIEW_ACCEPTED / P0_0 / P1_0 / NO_FAKE_BACKFILL / JAVA_POSTGRES_CANONICAL_PARITY_ACCEPTED / CONCURRENCY_ACCEPTED / APPROVAL_COMPATIBILITY_ACCEPTED / V39_TO_V40_ACCEPTED / V1_TO_V40_ACCEPTED / EXECUTION_INTENT_0 / OKX_CALL_0 / EXCHANGE_MUTATION_0 / LIVE_DISABLED / READY_TO_COMMIT`。

Next step：仅可提交当前 V40 implementation + review evidence，并等待 exact-head CI；不得由本 review 推进 GateY-6D machine authority、materialize真实 pilot、创建真实 approval/ExecutionIntent、启动 provider/worker、访问 credential/OKX 或启用 LIVE。

Commit recommendation：`feat(gatey): add pilot scope prerequisite fact model`。
