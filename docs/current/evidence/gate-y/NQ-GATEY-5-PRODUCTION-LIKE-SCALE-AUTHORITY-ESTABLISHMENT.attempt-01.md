# NQ-GATEY-5 Production-like Scale Authority Establishment — Attempt 01

## 1. Task classification

`CAPACITY_BASELINE / DATABASE_SCALE_AUTHORITY / READ_ONLY_ENVIRONMENT_ASSESSMENT / SYNTHETIC_CAPACITY_ENVELOPE / GOVERNANCE_EVIDENCE`。

风险等级：L 级前置容量/数据库治理任务。Task ownership：NQ-only。

## 2. Starting baseline

- branch=`dev`；起始 worktree clean、staged empty。
- `HEAD == origin/dev == 4e4373ecdb88dcfcb0637cc4c74e321c210e1e76`。
- exact-head `NQ CI Baseline` run=`31682295629 / completed / success`。
- current authority checker=`PASS / CURRENT_AUTHORITY_CONSISTENT`。
- `accepted_batch=GateY-4 / ACCEPTED|CI_GREEN`；`work_batch=GateY-5 / NOT_STARTED / NONE / NOT_RUN`。
- `LIVE=DISABLED`；kill switch=`ENGAGED`；real provider/private trading=`NOT_IMPLEMENTED`。

## 3. Blocker being addressed

上一轮 GateY-5 implementation 在 lock-window 前置审计中发现：仓库只有 2-row disposable migration fixture，且原 evidence 明确禁止外推 production duration/lock window；没有 V39 依赖表的可信 observed statistics 或 planned capacity authority。因此按 hard gate 停止，并记录：

```text
BLOCKED /
PRODUCTION_LIKE_SCALE_NOT_ESTABLISHED /
PRODUCTION_LOCK_WINDOW_NOT_CLOSED /
MICRO_LIVE_NOT_AUTHORIZED /
LIVE_DISABLED
```

本任务只建立后续 measurement 的 candidate capacity authority；不执行 V39 lock-window acceptance，不修改 GateY-5 machine work batch。

## 4. Environment statistics authorization

结论：`TARGET_ENVIRONMENT_STATS=NOT_RUN`。

仓库/current evidence 只有本地、CI、disposable PostgreSQL 连接说明以及 GateW 运行配置引用，没有同时满足以下条件的 authority：

1. 明确、脱敏的目标环境 identity；
2. 明确允许本任务访问的 read-only database；
3. 只读统计连接 procedure；
4. 可引用的 sanitized statistics evidence。

因此本任务没有 SSH、没有连接服务器/生产数据库、没有读取任何数据库环境变量或密码，也没有运行 `COUNT(*)`、`pg_stat_*`、`pg_locks` 或 relation-size SQL。Mode A 不可用，进入 Mode B。

## 5. Scale authority mode and source classes

Authority mode：`REVIEWED_SYNTHETIC_ENVELOPE`（待独立审查的保守 synthetic 容量包络）。

来源分类：

- `OBSERVED`：目标环境真实只读统计；本任务为 0 项。
- `DERIVED`：由 observed 数据计算；本任务为 0 项。
- `POLICY_BOUND`：来自 V39 schema 上限或本 authority 明确冻结的 planned workload/retention bound。
- `SYNTHETIC_CONSERVATIVE`：由 policy bound、retention 与 stress 公式形成的测试目标；不是 production observation。
- `UNAVAILABLE`：环境统计本身不可用，但各 table target scale 不得因此为 UNKNOWN。

manifest 中所有逐表 scale 均标记 `SYNTHETIC_CONSERVATIVE`。业务/schema 上限仅作为计算输入，不冒充 observed rows。

## 6. V39 dependency graph

依赖图从实际 `V39__gate_y2_live_session_fact_model.sql`、被引用表 FK 和 runtime RBAC SQL 重新生成。`strategy_definitions` 到 `backtest_publish_records` 以及 `accounts/strategy_runs` 是生成 valid synthetic FK graph 所需的 transitive seed closure，不表示 V39 直接锁这些表。

| Table | Role | V39 dependency type | FK direction | Lock relevance | Expected writes | Capacity source |
| --- | --- | --- | --- | --- | --- | --- |
| `users` | identity parent | direct FK target/runtime identity | GateY facts → users | V39 创建多个 FK | low | synthetic identity bound |
| `roles` | RBAC parent | runtime RBAC support | user_roles → roles | 无 direct V39 DDL | very low | role catalog bound |
| `user_roles` | RBAC grants | runtime RBAC support | user_roles → users/roles | 无 direct V39 DDL | low | 4 grants/user |
| `accounts` | legacy order parent | FK seed support | orders/strategy_runs → accounts | transitive only | low | 2000 legacy parents |
| `strategy_runs` | order run parent | FK seed support | orders → strategy_runs → accounts | transitive only | medium | 250000 retained runs |
| `exchange_accounts` | GateY account parent | direct FK target | live_sessions → exchange_accounts → users | V39 创建 FK | low | 2000 account rows |
| `exchange_account_credentials` | credential reference parent | direct FK target | live_sessions → credential → account/self | V39 创建 FK | very low | 3 lifecycle rows/account |
| `strategy_definitions` | research strategy parent | release seed support | versions/research configs → strategy definitions | transitive only | low | 2000 definitions |
| `strategy_versions` | release version parent | release seed support | backtest/publish → strategy versions | transitive only | low | 10 versions/definition |
| `research_configs` | research config parent | release seed support | backtest/publish → research configs | transitive only | low | 20000 configs |
| `backtest_configs` | backtest config parent | release seed support | runs/publish → backtest configs | transitive only | medium | 50000 configs |
| `backtest_runs` | publish run parent | release seed support | publish → runs → configs/research/version | transitive only | medium | 500000 runs |
| `backtest_publish_records` | admission state parent | release seed support | admission state → publish | parent of direct target | low | 100000 publishes |
| `strategy_release_admission_state` | GateY release parent | direct FK target | live_sessions → admission → publish | V39 创建 FK | medium | 1 row/publish |
| `orders` | existing order SoR | direct FK target | execution_intents → orders → accounts/runs | high-volume V39 FK target | high | two-year order formula |
| `risk_limit_sets` | immutable risk fact | created by V39 | sessions → risk → users | migration-time empty new table | very low | 1 per 5 sessions |
| `live_sessions` | GateY session fact | created by V39 | session → existing parents | migration-time empty new table | low | two-year session formula |
| `live_session_events` | append-only event | created by V39 | event → session/users | migration-time empty new table | medium | 30/session |
| `operator_approvals` | append-only approval | created by V39 | approval → session/users | migration-time empty new table | low | 3/session |
| `execution_intents` | execution intent | created by V39 | intent → session/orders | migration-time empty new table | high | 400/session |
| `execution_receipts` | append-only receipt | created by V39 | receipt → intent | migration-time empty new table | high | 3/intent |

所有 13 张任务明示表与 8 张 valid-FK seed support 表均已覆盖；duplicate table=0。

## 7. Capacity methodology

Scope 定义：足够保守地覆盖 GateY single-venue micro-live pilot 及其合理两年历史积累，用于 V38→V39 deployment lock behavior；不覆盖 HFT、multi-exchange、百万 TPS、美股/A 股、GateZ 或长期全平台最终容量。

共同方法：

- retention horizon=`730 days`。
- table estimate=`ceil(targetRows × estimatedRowWidth × 1.35 / 8192) × 8192`。
- aggregate index estimate=`ceil(targetRows × estimatedAggregateIndexBytesPerRow × 1.25 / 8192) × 8192`。
- byte estimates 是 planned capacity reservation，不是 `pg_relation_size` observation。
- 所有 round-up 都向保守方向；独立 review 可以增加 target，不能把 synthetic 改写为 observed。
- baseline seed：existing relations 精确等于 manifest 的 `preV39TargetRows`；V39-created relations 在 migration 前精确为 0。
- identity/account/release supporting facts 使用 reviewed absolute policy ceiling；其 growth/stress multiplier 显式为 `1.0/1.0`，headroom 已内含于 ceiling，不伪造 observed baseline 的增长率。
- orders 使用 growth=`1.0`、stress=`1.2`；sessions 使用 growth=`1.0`、stress=`1.5`。
- 其余 GateY facts按 stressed 5000-session parent 做 exact amplification，growth/stress=`1.0/1.0`，避免重复施加 session stress。

Independent review correction 后，manifest 以两个机器级 dataset phase 消除 `targetRows` 语义歧义：

- `PRE_V39_MIGRATION_CLONE` 只读取 `preV39TargetRows`，包含 15 张 V38 既有表、总计 `3,557,032` rows；六张 V39 新表被显式排除，migration start rows=`0`。
- `POST_V39_OPERATIONAL_DRILL` 读取 `targetRows`，包含全部 21 张表、总计 `11,728,032` rows；六张新表的 `8,171,000` steady-state rows 仅服务 backup/restore、replay、reconciliation、restart 与 storage/load drill。

## 8. Identity and account scale

- `users=1000`：operator/service identity reservation。
- `roles=32`：bounded role catalog，包含 `LIVE_APPROVER`，不按用户倍增。
- `user_roles=4000`：最多 4 retained grants/user。
- `accounts=2000` 与 `exchange_accounts=2000`：为既有 account/order FK 和 exchange account history 留出一致父集合；GateY 实际仍只允许 1 pilot account。
- `exchange_account_credentials=6000`：3 synthetic lifecycle rows/account，用于 rotation/revocation FK shape；不读取或复制任何 credential material。

这些是低频表，未夸张扩张为高频事实。

## 9. Strategy-release scale

- `strategy_definitions=2000`。
- `strategy_versions=20000`：10 versions/definition。
- `research_configs=20000`、`backtest_configs=50000`、`backtest_runs=500000`。
- `backtest_publish_records=100000`：每 5 retained runs 预留 1 publish。
- `strategy_release_admission_state=100000`：V38 合同为每个 publish 精确 1 row。

这些表是为 V39 release FK target 生成 valid closure 的 planned capacity，不代表当前 production research volume。

## 10. Orders scale

`orders` 是最重要的既有增长表，不能从 `max_open_orders=20` 推导。target 使用两部分：

```text
existing history reservation
= 4 order-producing account equivalents × 500 orders/day × 730 days
= 1,460,000

GateY retained PLACE history
= 1 pilot × 4 sessions/day × 200 PLACE/session × 730 days
= 584,000

subtotal = 2,044,000
stress    = subtotal × 1.20 = 2,452,800
target    = round up to 2,500,000 rows
```

`4 accounts × 500/day` 是本 authority 的 synthetic policy reservation，不是 observed production rate；它覆盖 existing order ownership、重启/恢复和多 session history，又不扩大到 HFT。

## 11. GateY fact scale

```text
raw sessions = 1 pilot × 4 sessions/day × 730 days = 2,920
stress        = 2,920 × 1.50 = 4,380
target        = 5,000 sessions
```

- `risk_limit_sets=1000`：1 immutable version/5 sessions。
- `live_sessions=5000`。
- `live_session_events=150000`：30/session，覆盖 control、kill、recovery 与 reconciliation event amplification。
- `operator_approvals=15000`：3/session，覆盖 reject/re-approve/expiry history。
- `execution_intents=2000000`：5000 × (`200 PLACE + 200 CANCEL`)。
- `execution_receipts=6000000`：3/intent，覆盖 mutation result、UNKNOWN query 与 reconciliation amplification。

V39 创建这六张表，因此 V38 snapshot 中 `preV39TargetRows=0`；steady-state `targetRows` 只用于后续 restore/worker/容量测试。不得在执行 V39 前伪造这些表存在。

## 12. Production-like table matrix

完整 machine-readable matrix 见同目录 manifest。汇总：

- tables=`21`。
- steady-state target rows=`11,728,032`。
- estimated table bytes=`10,323,410,944`。
- estimated aggregate index bytes=`8,078,557,184`。
- migration-time existing-table rows 与 steady-state rows 通过机器级 `datasetPhases`、`rowSelector`、included/excluded table set 分离；不再依赖人读解释 `targetRows`。
- unknown target rows=`0`；unknown source class=`0`。

## 13. Write-rate envelope

全局 `SYNTHETIC_CONSERVATIVE` envelope：

- normal order/intents writes=`5/s`。
- peak order/intents writes=`20/s`，burst=`60s`。
- peak receipts=`60/s`，对应 3 receipts/intent amplification。
- concurrent writers=`4`。
- maximum expected transaction duration=`30s`。
- maximum acceptable active transaction age=`30s`；deployment preflight 发现超过该阈值的 active transaction 必须停止。

逐表 normal/peak/burst/concurrent writers 已进入 manifest。该 write pressure 不是 observed throughput。

## 14. Active transaction envelope

| Scenario | Age | Held lock | Relations | Expected measurement behavior |
| --- | ---: | --- | --- | --- |
| `NO_COMPETITION` | 0s | none | none | 测量 bounded normal duration |
| `LONG_READ_TRANSACTION` | 120s | `ACCESS SHARE` | users/orders/release state | 记录实际兼容/阻塞，不预设结论 |
| `ORDER_WRITER_TRANSACTION` | 15s | `ROW EXCLUSIVE` | orders | 冲突时 5s lock timeout fail-closed |
| `ACCOUNT_REFERENCE_WRITER` | 15s | `ROW EXCLUSIVE` | exchange accounts/credentials | 冲突时 fail-closed |
| `STRATEGY_RELEASE_REFERENCE_WRITER` | 15s | `ROW EXCLUSIVE` | admission state | 冲突时 fail-closed |
| `MULTIPLE_CONCURRENT_WRITERS` | 30s | `ROW EXCLUSIVE` | orders/account/release | 不得留下 partial V39；释放后才 retry |
| `TRANSACTION_AGE_LIMIT` | 30s | preflight threshold | `pg_stat_activity` | 任一 active transaction 超限即停止部署 |

本任务不实际获取 lock、不启动事务、不运行 V39。

Scenario class 已冻结为：正常无竞争=`EXPECTED`，30s admission threshold=`LIMIT`，120s long read 与 15s/30s writers=`FAULT_INJECTION`。V39 合同固定 `lock_timeout=5s`、`statement_timeout=60s`；后续 blocked DDL 的 acceptance upper bound 为 `5s + 2s harness observation tolerance = 7s`，当前 `measurementStatus=NOT_MEASURED`。

## 15. Synthetic dataset strategy

本轮不提前实现 generator；现有文档合同足以让下一 implementation task 选择最小 SQL/PowerShell/Java integration fixture，避免 docs task 引入测试代码。

Generator contract：

1. 固定 seed=`gatey-production-like-scale-v1`；所有 table-scoped ID/UUID 从 `SHA-256(seed, table, ordinal)` 派生，timestamp 从固定 UTC epoch 加 bounded ordinal offset 派生，不使用未 seeded randomness 或 wall-clock jitter。
2. 在 disposable PostgreSQL fresh migrate 到 V38。
3. 按 dependency order 精确 seed 所有 existing tables 到 `preV39TargetRows`；PK/FK、unique constraint 和状态值合法。
4. credential row 只使用固定、不可解密、非秘密 synthetic bytes；不复制 production data，不记录 payload。
5. 运行 V39 lock scenarios；此时六张 V39 新表由 migration 创建且为空。
6. lock measurement 完成后，若 rollback/restore/worker drill 需要，再按 `targetRows` seed GateY facts。
7. logical dataset digest 对 table name + ordered non-sensitive PK/FK/business marker 计算 SHA-256；排除 OID、physical size、timestamp jitter 与 credential bytes。
8. same seed 必须得到相同 logical digest，exact row counts 与 FK integrity 必须通过。
9. `backtest_publish_records` 先写入，再核实 V38 trigger 产生精确 1:1 admission row；session 使用的 admission row 完整绑定 digest quartet 并推进 revision。
10. 5000 个 session 必须从 `APPROVAL_PENDING` 插入；复用 account 前，将旧 session 按 V39 guard 合法推进到 terminal。event/intents/receipts 分别使用唯一正序号，receipt attempt 固定 `1..3`。

## 16. Capacity manifest

文件：[NQ-GATEY-5-PRODUCTION-LIKE-SCALE-AUTHORITY.manifest.json](NQ-GATEY-5-PRODUCTION-LIKE-SCALE-AUTHORITY.manifest.json)。

Deterministic contract：UTF-8、LF、固定 key/table/scenario order；digest 为该文件字节在把 `digest` 值替换成 64 个小写 `0` 后的 SHA-256。这样 digest 字段长度不变，避免 self-reference ambiguity。

Manifest digest：`bbb67585855ef1c10adf2fbd57ef7cbdd270af702c4a322fe5a38d328037ee81`。

## 17. Scale adequacy self-check

- all V39 explicitly required tables covered：`PASS / 13 of 13`。
- transitive valid-FK seed closure covered：`PASS / 8 support tables`。
- duplicate tables：`0`。
- unknown target scale/source class：`0 / 0`。
- orders historical scale：已表示。
- GateY event/receipt amplification：已表示。
- write pressure/transaction age：已表示。
- observed/synthetic distinction：已明确。
- credential material access：0。

## 18. Limitations and independent review boundary

- 本 authority 是 planned synthetic envelope，不证明 actual production size、bloat、distribution、cache state 或 write throughput。
- size bytes 是可复算 estimate，后续 disposable PostgreSQL 生成后必须记录实际 table/index bytes 并解释偏差。
- `PRODUCTION_LOCK_WINDOW_NOT_MEASURED=OPEN`；本任务不关闭 lock-window blocker。
- authority 已完成独立 `NQ-GATEY-5-PRODUCTION-LIKE-SCALE-AUTHORITY-REVIEW` 的 static acceptance；仍须提交并等待 exact-head CI green，才可作为重新执行 GateY-5 implementation 的 accepted input。
- GateY-5 machine authority保持 `NOT_STARTED`。

## 19. Architecture and security boundary

- 没有 capacity runtime service、Maven module、production dependency、Python control-plane dependency或 Flyway copy。
- V1～V39 unchanged；V40=0。
- credential material/API key/secret/passphrase/ciphertext read=`0`。
- production DB reads/writes=`0/0`；production migration/backup/restore=`0/0/0`。
- exchange calls/PLACE/CANCEL/transfer/withdraw=`0/0/0/0/0`。
- worker/process start、deployment、micro-live orders=`0/0/0`。
- LIVE=`DISABLED`；kill switch=`ENGAGED`。

## 20. Validation

Independent review correction 后的最终回读验证：JSON parse PASS；canonical digest match=`true`（`bbb67585855ef1c10adf2fbd57ef7cbdd270af702c4a322fe5a38d328037ee81`）；tables=`21`；required missing=`0`；duplicate=`0`；bad source class=`0`；unknown scale=`0`；size formula mismatch=`0`；derivation missing/duplicate=`0/0`；dataset phase problems=`0`；static constraint checks=`11/11`；UTF-8 BOM=`false`；CR bytes=`0`；PRE/POST rows=`3,557,032 / 11,728,032`；target table/index bytes=`10,323,410,944 / 8,078,557,184`。最终 authority/link/Git 检查以独立 review evidence 为准。

## 21. Findings and decision

- P0：0。
- P1：review 发现 `CAPACITY_DATASET_PHASE_AMBIGUOUS` 与 deterministic generator constraint contract 过于隐式；已在当前 allowlist 内通过 machine `datasetPhases`、scenario class、lock expectation 与 seeded realizability contract 最小修正并整体复核，final P1=`0`。
- P2：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED=OPEN`；capacity byte estimate 尚待 future disposable realization 校准。
- P3：根 `CLAUDE.md` 仍有非权威旧阶段文字漂移，本轮不修改。

Candidate conclusion：

```text
PASS /
GATEY_5_PRODUCTION_LIKE_SCALE_AUTHORITY_ESTABLISHED /
ALL_V39_DEPENDENCY_TABLES_COVERED /
SCALE_SOURCE_CLASSIFIED /
CAPACITY_ENVELOPE_FROZEN /
NO_PRODUCTION_WRITE /
GATEY_5_IMPLEMENTATION_NOT_STARTED /
READY_FOR_INDEPENDENT_SCALE_REVIEW
```

Next：`COMMIT_SCALE_AUTHORITY_AND_WAIT_EXACT_HEAD_CI`。
