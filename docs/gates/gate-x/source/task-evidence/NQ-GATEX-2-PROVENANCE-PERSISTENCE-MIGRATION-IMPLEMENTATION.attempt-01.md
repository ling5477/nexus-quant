# NQ-GATEX-2 Provenance Persistence Migration Implementation — Attempt 01

## 1. 结论

任务分类：NQ-only / `FLYWAY_MIGRATION` / 高风险数据库变更。

执行结论：

```text
IMPLEMENTED /
PROVENANCE_PERSISTENCE_COMPLETE /
POSTGRESQL_REGRESSION_GREEN /
PENDING_INDEPENDENT_MIGRATION_REVIEW
```

GateX-2 只实现 Shadow Run provenance persistence；未实现 GateX-3 Release-to-Shadow admission，未新增 API、frontend、scheduler 或 runner 行为，未改变交易状态机，未访问生产或开启 LIVE。

## 2. Preflight 与 GateX-1 收口

- branch：`dev`。
- starting worktree：clean；staged empty。
- starting HEAD：`2655f5144ba27cc88c2786de7f76633df3df462d`。
- `origin/dev`：`2655f5144ba27cc88c2786de7f76633df3df462d`。
- GateX-1 implementation commit：`2655f5144ba27cc88c2786de7f76633df3df462d`。
- GateX-1 exact-head CI：`NQ CI Baseline` run `31358676688`，`completed / success`。
- GateX-1 acceptance：`ACCEPTED|CI_GREEN`。
- GateX-2 entering authority：`NOT_STARTED / NONE / NOT_RUN`；next action=`NQ-GATEX-2-PROVENANCE-PERSISTENCE-MIGRATION-IMPLEMENTATION`。
- entering authority checker：`AUTHORITY_CHECK errors=0`。

## 3. Schema audit 与 migration

### Before

- 变更前最高 Flyway version：`V35`。
- `shadow_runs.publish_id`：`VARCHAR(128) NULL`，FK 指向 `backtest_publish_records(publish_record_id)`。
- `shadow_runs` 不存在等价 production `artifact_digest`；命中仅为计划或 prototype，不构成 persistence artifact。
- 现有 lifecycle update 为字段级 optimistic-lock SQL，只更新 `status/version/updated_at` 及 lifecycle timestamps，不是整行覆盖。

### Migration

- 新 migration：`V36__gate_x2_shadow_run_provenance.sql`。
- 历史 migration 修改：`0`。
- 新表 / artifact 表：`0`。

### After

```text
shadow_runs.artifact_digest VARCHAR(64) NULL
```

- `chk_shadow_runs_artifact_digest_sha256`：digest 为 `NULL`，或匹配 `^[0-9a-f]{64}$`。
- `chk_shadow_runs_artifact_requires_publish`：digest 非空时 `publish_id` 必须非空。
- 两项约束以 `NOT VALID` 建立后分别 `VALIDATE CONSTRAINT`；真实 PostgreSQL 验证 `convalidated=true`。
- unique decision：不增加 `UNIQUE(publish_id, artifact_digest)`；同一 release 允许多个 Shadow Run。
- backfill decision：`NO BACKFILL`。migration 无 `UPDATE`，不从文件重算、不使用默认值、不从 `publish_id` 推测 digest。
- 注释：column 与两个 constraints 均有中文 COMMENT，并明确不表示 admission、交易批准或 LIVE ready。

## 4. Domain / repository / JDBC

- `ShadowRun` 新增 nullable `artifactDigest`，构造时校验 lowercase 64-hex 且 digest requires publish。
- 新增派生 enum `ShadowRunReleaseBindingMode`，只由 `publishId + artifactDigest` 决定：
  - `NULL + NULL → LEGACY_UNBOUND`
  - `non-null + NULL → LEGACY_PUBLISH_ONLY`
  - `non-null + non-null → RELEASE_BOUND`
- 未持久化额外 binding-mode 列，未增加 `LIVE_READY / TRADING_APPROVED / ADMISSION_APPROVED`。
- 保留兼容构造路径，现有 runner 未获得自动 release binding，不构成 GateX-3 admission。
- `JdbcShadowRunFactRepository` 的 INSERT/SELECT 增加 nullable `artifact_digest`；PostgreSQL `NULL` 与字符串映射明确。
- overview 与 consistency drilldown SELECT 同步回载 digest。
- `updateStatus` 仍只更新 lifecycle/version/timestamps；不写 `publish_id` 或 `artifact_digest`。
- 未新增 `updateArtifactDigest / bindRelease / rebindRelease / PATCH` 等后置绑定入口。

## 5. PostgreSQL regression

运行环境：本机 disposable schema，PostgreSQL `17.7`，连接仅接受 `localhost/127.0.0.1/::1`。测试创建随机 `gatex2_fresh_*` / `gatex2_upgrade_*` schema，`finally` 精确删除；收尾查询残留数=`0`。

### Fresh database

- 从空 schema 执行 Flyway `V1→V36`。
- 结果：36 migrations applied，current version=`36`；Flyway validate PASS。
- schema、column comment、两个 constraint、`convalidated=true` 均通过。

### Upgrade database

- 先执行 `V1→V35`，插入 legacy fixtures，再执行 `V36`。
- 结果：1 migration applied，current version=`36`；Flyway validate PASS。
- legacy no-publish row：digest 保持 `NULL`，读取为 `LEGACY_UNBOUND`。
- legacy publish-only row：digest 保持 `NULL`，读取为 `LEGACY_PUBLISH_ONLY`。

### Release-bound / invalid / multiplicity

- release-bound：non-null publish + 64 lowercase hex digest，通过 repository 写入、读取一致并派生 `RELEASE_BOUND`。
- invalid：63/65 chars、uppercase、non-hex、empty、digest without publish 在 domain 或 PostgreSQL CHECK 层拒绝。
- 同一 `(publish_id, artifact_digest)` 可创建多个 run，证明未误加 global unique。

### Immutability

- lifecycle path A：创建后执行 stop-request / stopped。
- lifecycle path B：创建后执行 running / completed。
- 每次 optimistic-lock transition 后均重新读取：`publish_id` 与 `artifact_digest` 完全不变，`version` 按既有规则递增。
- JDBC unit test 同时断言 UPDATE SQL 不包含 provenance 字段。

全量 Maven 的既有 `local` profile 另将本机开发库 `nexus_quant.public` 从 V35 正常迁到 V36，并在后续 context startup 确认 up-to-date。该库是本地开发库，不是生产；本任务未访问生产数据库。

## 6. Test ledger

| Command / Check | Result |
| --- | --- |
| focused binding/migration/JDBC suites | 13 tests，0 failures / 0 errors / 0 skipped |
| post-lock-adjustment migration contract | 1 test，0 failures / 0 errors / 0 skipped |
| explicit PostgreSQL fresh/upgrade/repository suites | 2 tests，0 failures / 0 errors / 0 skipped |
| `mvn -f backend/pom.xml -pl nq-infra -am test` | BUILD SUCCESS；`nq-core` 439；`nq-infra` 100，3 existing opt-in skipped |
| `mvn -f backend/pom.xml test` | BUILD SUCCESS；23 modules；1312 tests，0 failures / 0 errors / 17 existing/opt-in skipped；2m14s |
| `scripts/docs/check-current-authority.ps1` | `AUTHORITY_CHECK errors=0` |

失败轮次未冒充通过：

- focused 命令一次因 PowerShell 未引用 `-Dsurefire.failIfNoSpecifiedTests`，Maven lifecycle 未开始；修正 quoting 后通过。
- PostgreSQL rerun 一次把 test property `.user` 误写为 `.username`，测试在连接前 fail-closed；修正属性名后同一 migration 重跑通过。
- cleanup SQL 一次因 `ESCAPE` quoting 无效；改为 anchored schema regex 后确认残留 `0`。

## 7. Lock、兼容与回滚风险

- nullable `ADD COLUMN` 不含 default/backfill；避免 data rewrite 和历史值推测。
- CHECK 以 `NOT VALID` 建立，再显式 validate，降低历史扫描位于强锁阶段的风险。
- 仍存在目标环境锁等待风险：DDL 需要短暂 table lock，`VALIDATE` 会扫描历史行。本次只在本地样本验证，未测生产规模 duration，记录为 P2，必须由独立 migration review 复核。
- Flyway migration 为 forward-only。未 commit 前可从工作区撤销并删除本地测试 schema；已在环境应用 V36 后，不得修改 V36，若必须回退应新增后续 migration，并先确认不存在 release-bound rows。

## 8. Boundary audit

- API impact：无 endpoint/DTO/contract 变更。
- frontend impact：无；`frontend/**` 变更 0。
- Python/research impact：无；`research/**` 变更 0。
- trading/LIVE impact：无；LIVE 保持 `DISABLED`，kill switch 保持 `ENGAGED`。
- credential/private endpoint：未读取 credential 文件，未调用 private endpoint。
- Strategy Release verifier：核心语义未修改。
- GateX-3 admission / automatic creation / scheduler / runner new behavior：均未实现。
- AI / DH runtime：无变更。

## 9. Findings

- P0：0。
- P1：0。
- P2：1 — 目标规模 `shadow_runs` 的 DDL lock wait / validation scan duration 尚未在部署环境测量，独立 review 必须审查 maintenance window、lock timeout 与 rollback plan。
- P3：1 — 既有 Maven 全局 settings line 227 `profiles` warning；非本批引入，不阻断回归。

## 10. Authority after

```text
work_batch=GateX-2
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-2-PROVENANCE-PERSISTENCE-MIGRATION-REVIEW
```

Authority checker：`PASS / CURRENT_AUTHORITY_CONSISTENT / errors=0`。

Review requirement：必须进行独立 migration review；本轮禁止标记 `SELF_REVIEWED` 或 `READY_TO_COMMIT`。

推荐未来 commit：`feat(shadow): persist strategy release artifact provenance`。

唯一下一动作：`NQ-GATEX-2-PROVENANCE-PERSISTENCE-MIGRATION-REVIEW`。
