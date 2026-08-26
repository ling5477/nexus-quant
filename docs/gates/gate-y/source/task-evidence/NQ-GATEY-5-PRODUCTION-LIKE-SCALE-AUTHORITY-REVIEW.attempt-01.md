# NQ-GATEY-5 Production-like Scale Authority Review — Attempt 01

## 1. Task classification

`INDEPENDENT_CAPACITY_REVIEW / DATABASE_SCALE_REVIEW / V39_LOCK_RELEVANCE_REVIEW / SYNTHETIC_ENVELOPE_REVIEW / GOVERNANCE_EVIDENCE_REVIEW`。

风险等级：L 级独立容量/数据库治理审查。Task ownership：NQ-only。

## 2. Starting baseline

- branch=`dev`。
- `HEAD == origin/dev == 4e4373ecdb88dcfcb0637cc4c74e321c210e1e76`。
- 起始 dirty worktree 精确为 establishment candidate 5 files；mixed/unexpected path=`0`。
- `accepted_batch=GateY-4 / ACCEPTED|CI_GREEN`；`work_batch=GateY-5 / NOT_STARTED / NONE / NOT_RUN`。
- `LIVE=DISABLED`；kill switch=`ENGAGED`；real provider/private trading=`NOT_IMPLEMENTED`。
- production DB、credential、exchange、worker、deployment access=`0`。

## 3. Review object and method

Review object：

- `NQ-GATEY-5-PRODUCTION-LIKE-SCALE-AUTHORITY.manifest.json`；
- `NQ-GATEY-5-PRODUCTION-LIKE-SCALE-AUTHORITY-ESTABLISHMENT.attempt-01.md`。

本 review 不采信 establishment 对 dependency 的摘要，直接回读 V1/V2/V3/V5/V7/V10/V12/V18/V19/V20/V27/V28/V29/V31/V37/V38/V39 SQL，并以 V39 actual DDL、FK、unique、check、trigger 与 current authority 重新判定。

## 4. V39 dependency graph review

实际证据：

- V39 timeouts：`V39__gate_y2_live_session_fact_model.sql:2-3`；
- 六张新表：同文件 `:5`、`:66`、`:129`、`:169`、`:200`、`:271`；
- 既有直接 parent FK：同文件 `:30`、`:89-99`、`:224`；
- lineage roots：V1 `:4-74`、V5 `:8`、V7 `:8-69`、V10 `:8-29`、V12 `:22-114`、V19 `:8-46`、V38 `:6-19`。

逐表 primary classification：

| Table | Primary classification | Review justification |
| --- | --- | --- |
| `users` | `MIGRATION_LOCK_RELEVANT` | V39 risk/session/event/approval 的 direct FK parent；row volume 不需要被 V39 扫描 |
| `exchange_accounts` | `MIGRATION_LOCK_RELEVANT` | `live_sessions` direct FK parent；account writer 是 contention source |
| `exchange_account_credentials` | `MIGRATION_LOCK_RELEVANT` | `live_sessions` direct FK parent；只生成 non-secret synthetic row |
| `strategy_release_admission_state` | `MIGRATION_LOCK_RELEVANT` | `live_sessions` direct FK parent；release writer/active transaction 是 contention source |
| `orders` | `MIGRATION_LOCK_RELEVANT` | `execution_intents` direct FK parent，也是主要并发写 parent；不是 V39 全表扫描 |
| `risk_limit_sets` | `POST_MIGRATION_DRILL_RELEVANT` | V39 新表，首次 migration start 为空；steady state 用于 restore/replay |
| `live_sessions` | `POST_MIGRATION_DRILL_RELEVANT` | V39 新表；首次 DDL 不受 5000 steady rows 影响 |
| `live_session_events` | `POST_MIGRATION_DRILL_RELEVANT` | V39 新 append-only 表；150000 rows 仅 post drill |
| `operator_approvals` | `POST_MIGRATION_DRILL_RELEVANT` | V39 新 append-only 表；15000 rows 仅 post drill |
| `execution_intents` | `POST_MIGRATION_DRILL_RELEVANT` | V39 新表；2000000 rows 仅 worker/replay/restore drill |
| `execution_receipts` | `POST_MIGRATION_DRILL_RELEVANT` | V39 新 append-only 表；6000000 rows 仅 post drill |
| `roles` | `SYNTHETIC_SEED_ONLY` | runtime RBAC catalog；V39 SQL 不直接引用 |
| `user_roles` | `SYNTHETIC_SEED_ONLY` | runtime approver grants；V39 SQL 不直接引用 |
| `accounts` | `SYNTHETIC_SEED_ONLY` | orders/strategy_runs/exchange legacy mapping 的 valid parent |
| `strategy_runs` | `SYNTHETIC_SEED_ONLY` | orders optional lineage parent；不被 V39 DDL 引用 |
| `strategy_definitions` | `SYNTHETIC_SEED_ONLY` | release lineage root |
| `strategy_versions` | `SYNTHETIC_SEED_ONLY` | backtest/publish lineage parent |
| `research_configs` | `SYNTHETIC_SEED_ONLY` | backtest/publish lineage parent |
| `backtest_configs` | `SYNTHETIC_SEED_ONLY` | run/publish lineage parent；dataset FK 可合法为空 |
| `backtest_runs` | `SYNTHETIC_SEED_ONLY` | publish unique parent |
| `backtest_publish_records` | `SYNTHETIC_SEED_ONLY` | V38 admission one-to-one parent |

Review result：table count=`21`；missing=`0`；unjustified=`0`；duplicate=`0`。没有把 `marketdata_datasets`、eval、Paper、Shadow 等 nullable/empty support domain 强行加入 capacity set。

## 5. V39 DDL lock relevance

| DDL class | Affected relation | Contention source | Volume sensitivity | Transaction-age sensitivity | Required measurement |
| --- | --- | --- | --- | --- | --- |
| `SET LOCAL` | transaction | none | none | none | 验证实际 timeout 生效值 |
| `CREATE TABLE ... FOREIGN KEY` | 六张新表 + 5 个既有 parents | parent concurrent writers/held relation locks | parent rows 不被新空 child validation 扫描；row count 主要塑造 workload | high | `pg_locks`、wait event、statement timing |
| `CREATE INDEX` | 六张新空表 | catalog/DDL transaction | migration start rows=0，row volume negligible | low | timing，确认无异常 catalog wait |
| `ALTER TABLE ... ADD CHECK` | 两张新空表 | transaction/catalog lock | row volume=0 | low | timing/locks |
| `CREATE FUNCTION` | catalog | catalog concurrency | negligible for this Gate | low | statement timing |
| `CREATE TRIGGER` | 六张新空表 | metadata lock | row volume=0 | low | statement timing |
| `COMMENT` | catalog/new objects | catalog concurrency | negligible for this Gate | low | aggregate timing |

V39 没有 existing-table backfill、existing-table index build、existing-table CHECK validation 或 rewrite。首次 migration 的重点是 parent relation lock acquisition、concurrent writer、active transaction age 和单 transaction 持锁，不得用新表 steady-state size 声称 DDL duration 已被覆盖。

## 6. Dataset phase separation

Independent review 发现初版只有 `preV39TargetRows/targetRows` 与 prose pre/post 描述，没有 top-level machine dataset authority，构成修正前 P1 `CAPACITY_DATASET_PHASE_AMBIGUOUS`。

已最小修正为：

### A. PRE_V39_MIGRATION_CLONE

- purpose：V38→V39 DDL/lock-window measurement；
- row selector=`preV39TargetRows`；
- included existing tables=`15`；excluded V39-created tables=`6`；
- rows=`3,557,032`；
- table/index reservation=`5,606,301,696 / 2,531,016,704 bytes`，合计约 `7.58 GiB`；
- V39-created rows at measurement start=`0`。

### B. POST_V39_OPERATIONAL_DRILL

- purpose：backup/restore、worker replay、reconciliation、restart、steady-state storage/load drill；
- row selector=`targetRows`；included tables=`21`；
- rows=`11,728,032`；
- table/index reservation=`10,323,410,944 / 8,078,557,184 bytes`，合计约 `17.14 GiB`；
- V39-created steady rows=`8,171,000`，其 relation/index reservation约 `9.56 GiB`。

Machine phase coverage problems=`0`。

## 7. Orders scale review

Decision：`ACCEPT_WITH_CONSERVATIVE_LABEL`。

1. `500/day` 来源：authority 自定义 `POLICY_BOUND` synthetic workload，不是 repository observed/production rate。
2. 4 个 workload unit：四个独立 retained order-producing workload partitions，不等同 observed account/user/venue 数；已在 manifest 机器字段定义。
3. retention=`730 days`：为 GateY single-venue micro-live 前置测量保留两年历史、避免只测 tiny fixture；不是业务数据删除政策。
4. 20% stress：`2,044,000 × 1.2 = 2,452,800`，再向上到 `2,500,000`；最终相对 subtotal headroom=`22.31%`。由于 V39 不扫描 orders，全量 row scale 是 workload/storage realism，writer/tx pressure 才是 lock 关键；本 Gate 无需扩大到 HFT 数量。
5. Paper/Shadow/SIM 生命周期没有重复相加；legacy reservation 明确一次性覆盖已有 order-producing modes。
6. `orders` 是 V39 direct parent 与高写入代表，必须进入 PRE clone；重要性来自 FK lock/contention，不是“表越大 DDL 越慢”的假设。

## 8. Identity/account scale review

- `users=1000`、`roles=32`、`user_roles=4000`：unique pair capacity 为 `32,000`，4000 可实现；role catalog 不按用户无意义倍增。
- `accounts=2000`、`exchange_accounts=2000`：可一对一设置 `legacy_account_id`，owner/alias/external-ref/default partial unique 可通过 seed-derived 值满足。
- `exchange_account_credentials=6000`：V12 允许三种 credential type，正好每 account 三条 active type；V12 partial unique、V29/V31 status/check 均可满足。`encrypted_payload` 只使用固定不可解密 synthetic bytes。
- 低增长 identity rows 不是 migration duration driver；它们用于 FK validity 与 parent writer profile。

## 9. Strategy/research lineage review

Feasible chain：

```text
strategy_definitions(2,000)
  -> strategy_versions(20,000; 10/definition)
  -> research_configs(20,000)
  -> backtest_configs(50,000)
  -> backtest_runs(500,000; 10/config)
  -> backtest_publish_records(100,000; distinct run, 1/5 runs)
  -> strategy_release_admission_state(100,000; V38 exact 1:1)
```

- `backtest_publish_records.backtest_run_id` unique，100000 ≤ 500000。
- dataset/eval/target/artifact/version nullable fields可保持 null；V37 artifact keys 必须成对为空或成对合法。
- config/run 的 research、strategy、version 组合按同一 deterministic parent path 生成，避免数学可填但业务 lineage 不一致。
- V38 后插入 publish 会由 trigger 初始化 admission；generator 不重复直接插入。session 使用的 admission rows 再完成 digest quartet 首次绑定并推进 positive revision。

Static feasibility：`PASS`，不存在 `SYNTHETIC_SCALE_NOT_REALIZABLE`。

## 10. GateY amplification review

Actual ratios：

- risk/session=`0.2`，即 1 risk/5 sessions；scope/version 与 digest 可分别唯一。
- events/session=`30`；`sequence_no=1..30`，并同步 session `next_event_sequence`。
- approvals/session=`3`；`expires_at > approved_at`，approver 与 creator 使用不同 synthetic users。
- intents/session=`400`，即 200 PLACE + 200 CANCEL；`session,sequence` 唯一，PLACE client ID 唯一，字段满足 action CHECK。
- receipts/intent=`3`；attempt=`1..3`，outcome/digest/schema 合法。
- 2000000 intents ≤ 2500000 orders，可为每个 intent 提供 existing `local_order_id`；也可让 CANCEL 指向对应 PLACE order，均不违反 DB unique。

V39 partial unique 只允许每 account 一个 non-terminal session，而 5000 sessions > 2000 accounts。Generator 必须先按 insert guard 创建 `APPROVAL_PENDING`，再按 guard 合法推进旧 session 至 terminal，之后才复用 account。该过程有界且 deterministic，因此 envelope 可实现。

## 11. Byte-estimate review

Formula recomputation mismatch=`0`：

```text
table = ceil(rows × estimatedRowWidth × 1.35 / 8192) × 8192
index = ceil(rows × estimatedAggregateIndexBytesPerRow × 1.25 / 8192) × 8192
```

Decision：`CAPACITY_RESERVATION_ESTIMATE`，绝不是 `EXPECTED_PG_RELATION_SIZE`。

估算没有精确模拟 tuple header、alignment、null bitmap、page fill、visibility map/FSM、TOAST、JSONB/array variance、B-tree split/WAL/temp。现有 row/index widths 与 1.35/1.25 multiplier 可作为 fixture reservation，但 realization 必须记录 actual relation/index bytes。基础 relation+index 为 `17.14 GiB`；生成/migration 主机建议至少预留 `40 GiB` free space，若 source+backup+restore 同时存在则建议至少 `60 GiB`。这些 free-space 数值是 review capacity guard，不是 physical-size observation。

## 12. Write-rate review

- normal order/intents=`5/s`；peak=`20/s`；receipt peak=`60/s`；burst=`60s`；concurrent writers=`4`。
- 这是 `contention pressure profile`，不构成业务吞吐、交易或 LIVE 授权。
- 覆盖 order writer、account/credential reference writer、strategy-release writer 和 multiple concurrent writers。
- Receipt writer 主要用于 POST operational drill；首次 V39 migration 前 receipts 表不存在，不把它计为 PRE parent contention。

Decision：coherent，保留 synthetic conservative label。

## 13. Transaction-age and timeout review

- `EXPECTED`：无竞争基线；普通事务 expected duration ≤30s。
- `LIMIT`：30s 为 admission/preflight maximum acceptable active transaction age；超过即停止 deployment attempt。
- `FAULT_INJECTION`：120s long read、15s single writers、30s multiple writers；不是 expected production transaction。
- V39 actual contract：`lock_timeout=5s`、`statement_timeout=60s`。
- Frozen expectation：genuinely blocked DDL 必须在 `5s + 2s harness observation tolerance = 7s` 内失败；60s 是 outer fail-closed bound。
- `MEASURED=NO`。本 review 不声称 timeout、lock mode 或 window 已实测。

## 14. Synthetic generator realizability

- ID/UUID：`SHA-256(seed, table, ordinal)` 派生 table-scoped collision-free identifiers/RFC-4122-compatible bytes；不使用 unseeded randomness。
- Timestamps：固定 UTC epoch + bounded ordinal offsets；不使用 wall-clock jitter。
- Generation order 已机器冻结，先 parent/lineage/orders，V39 前六表为空，V39 后再构造 GateY facts。
- PK/FK/unique/check/status/digest/array/credential rules 有确定性构造路径；static checks=`11/11`。
- Complexity：`O(total rows + index construction)` bounded offline bulk generation。
- Runtime class：`MULTI_MILLION_ROW_OFFLINE_BULK_LOAD`；真实耗时在 realization 前不估算。
- 无 production row copy、无真实 credential、无 raw provider payload。

Decision：`SYNTHETIC_ENVELOPE_REALIZABLE`。

## 15. Manifest integrity and source classification

- schema=`gatey-production-like-scale.v1`；source mode=`REVIEWED_SYNTHETIC_ENVELOPE`。
- authority status=`REVIEW_ACCEPTED_READY_TO_COMMIT`。
- generatedFromCommit=`4e4373ecdb88dcfcb0637cc4c74e321c210e1e76`。
- tables=`21`；required missing=`0`；duplicate=`0`；unknown=`0`。
- derivation rules=`4`；missing/duplicate=`0/0`。
- dataset phases=`2`；phase problems=`0`。
- formula mismatch=`0`；UTF-8 BOM=`false`；CR bytes=`0`。
- allowed semantics：repository baseline=`OBSERVED_REPOSITORY_FACT`；policy inputs=`POLICY_BOUND`；formula outputs=`DERIVED`；table/write targets=`SYNTHETIC_CONSERVATIVE`。
- prohibited semantic inflation scan：没有 `PRODUCTION_OBSERVED`、`REAL_DATABASE_SIZE`、`ACTUAL_WRITE_RATE` 或 `MEASURED_LOCK_WINDOW` positive claim。

Canonical digest：`bbb67585855ef1c10adf2fbd57ef7cbdd270af702c4a322fe5a38d328037ee81`。

## 16. Corrections applied

RCA：初版 human prose 能区分 pre/post，但 manifest top-level 只有 `preV39TargetRows/targetRows`，consumer 仍可能把 V39 新表 steady rows误用于首次 DDL；UUID/timestamp/session transition contract也不够机器明确。

最小 correction：

1. 新增 `datasetPhases`，冻结 PRE/POST purpose、row selector、included/excluded tables、rows 与 bytes。
2. 为 capacity policy/derivation/size 增加 source class，并定义 legacy workload unit 与 lifecycle non-double-count semantics。
3. transaction scenarios 增加 `EXPECTED/LIMIT/FAULT_INJECTION`。
4. 新增 5s lock timeout、2s observation tolerance、7s blocked upper bound、60s statement timeout、`NOT_MEASURED` contract。
5. 新增 seed-derived ID/timestamp、generation order、constraint realizability 与 complexity/runtime class。
6. 重算并更新 manifest digest；同步 establishment evidence。

没有改变 21-table set、orders 2.5M、PRE/POST totals、table/index estimate、write rate或 retention。

## 17. Findings

### P0

- 无。

### P1

- 修正前：`CAPACITY_DATASET_PHASE_AMBIGUOUS`、deterministic constraint contract 过于隐式。
- 修正后：全部关闭；final P1=`0`。

### P2

- `PRODUCTION_LOCK_WINDOW_NOT_MEASURED=OPEN`。
- Byte estimate 尚未由 disposable PostgreSQL actual relation/index size 校准。
- Generator contract 已冻结但实现不存在；不得把 static realizability 当作 generated fixture。

### P3

- 根 `CLAUDE.md` 有非权威旧阶段文字漂移，本任务不修改。

## 18. Authority and boundary

Before：scale authority=`ESTABLISHED_PENDING_INDEPENDENT_REVIEW`；GateY-5 machine work=`NOT_STARTED / NONE / NOT_RUN`。

After：`PRODUCTION_LIKE_SCALE_AUTHORITY=REVIEW_ACCEPTED|READY_TO_COMMIT`；GateY-5 machine work仍为 `NOT_STARTED / NONE / NOT_RUN`。

本 review 只关闭 `PRODUCTION_LIKE_SCALE_NOT_ESTABLISHED`，不关闭 `PRODUCTION_LOCK_WINDOW_NOT_MEASURED`，不表示 GateY-5 implementation、micro-live、LIVE、真实 provider、credential 或交易获授权。

## 19. Security boundary

- production DB access=`0`；credential read=`0`；exchange calls=`0`；real mutation=`0`。
- worker starts=`0`；production deployment=`0`；migration execution=`0`。
- PLACE/CANCEL/transfer/withdraw=`0/0/0/0`。
- LIVE=`DISABLED`；kill switch=`ENGAGED`。

## 20. Validation

Final ledger-inclusive recheck：manifest JSON=`PASS`；authority status=`REVIEW_ACCEPTED_READY_TO_COMMIT`；digest match=`true`；tables missing/extra/duplicate/unknown=`0/0/0/0`；size mismatch=`0`；derivation missing/duplicate=`0/0`；dataset phase problems=`0`；named FK/unique/check/cardinality checks=`11/11`；UTF-8 BOM=`false`；CR bytes=`0`。`check-current-authority.ps1` errors=`0`；`check-doc-links.ps1 -Roots docs/current` checked=`257`、errors=`0`、既有 historical warnings=`14`；changed paths=`6`、unexpected/forbidden=`0/0`；`git diff --check` exit=`0`，仅 tracked Markdown LF→CRLF working-tree warning；sensitive value match=`0`。branch/HEAD/origin=`dev / 4e4373ecdb88dcfcb0637cc4c74e321c210e1e76 / same`。

验证过程有一次 ad-hoc PowerShell constraint array comparison parsing 失败，导致该次 wrapper 输出无效 `0 checks`；RCA 是逗号与无括号比较表达式组合被解释成 object array。改为 named boolean map 后连续复算 `11/11`、failures=`0`；失败调用无文件、DB 或外部副作用。Semantic scan 的唯一 term hit 是本 evidence 对禁止 inflation labels 的否定性列举，不是 positive claim。

产品 backend/frontend/Python tests 不运行：本轮只有 manifest/governance review correction，没有 generator、Java、migration、frontend 或 Python 代码。

## 21. Review decision

Final conclusion：

```text
PASS /
GATEY_5_PRODUCTION_LIKE_SCALE_AUTHORITY_REVIEW_ACCEPTED /
P0_0 /
P1_0 /
PRE_V39_MIGRATION_SCALE_FROZEN /
POST_V39_OPERATIONAL_SCALE_FROZEN /
ALL_V39_DEPENDENCIES_COVERED /
SYNTHETIC_ENVELOPE_REALIZABLE /
CAPACITY_MANIFEST_VERIFIED /
PRODUCTION_LOCK_WINDOW_STILL_OPEN /
GATEY_5_IMPLEMENTATION_NOT_STARTED /
READY_TO_COMMIT
```

Next：`COMMIT_SCALE_AUTHORITY_AND_WAIT_EXACT_HEAD_CI`。

Recommended commit：`docs(gatey): accept production-like scale authority`。
