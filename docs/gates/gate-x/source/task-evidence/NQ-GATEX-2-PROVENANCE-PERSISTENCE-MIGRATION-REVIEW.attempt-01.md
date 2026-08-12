# NQ-GATEX-2 Provenance Persistence Migration Review — Attempt 01

## 1. 审查结论

结论：`PASS / MIGRATION_REVIEW_ACCEPTED / PROVENANCE_INVARIANTS_VERIFIED / POSTGRESQL_COMPATIBILITY_VERIFIED / READY_TO_COMMIT`。

任务分类：NQ-only / L 级 / `FLYWAY_MIGRATION` 独立复核；review-only，只有明确 P0/P1 可做最小修复。

GateX-2 在关闭 1 个 P1 后有条件通过。当前 P0=0、未关闭 P1=0、P2=1、P3=1；P2 为部署规模下的事务级锁持有风险，不阻断本地 review acceptance，但必须进入部署检查清单。本轮未 commit、未 push、未启动 GateX-3。

## 2. 范围

- 已审查：GateX-2 staged migration、domain、JDBC write/read/update、overview/drilldown query、unit/real PostgreSQL regression、current authority 与 implementation evidence。
- 未审查：生产数据库实际行数、关系大小、写入速率、长事务与锁队列；远端 exact-head CI（尚未提交）。
- 明确不涉及：GateX-3 Release-to-Shadow admission、API、scheduler、runner 新行为、frontend、Python、交易状态机、LIVE、credential/private endpoint、真实交易、AI/DH runtime。
- starting branch：`dev`。
- starting HEAD：`2655f5144ba27cc88c2786de7f76633df3df462d`。
- starting origin/dev：`2655f5144ba27cc88c2786de7f76633df3df462d`。
- authority before：`GateX-2 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；next=`NQ-GATEX-2-PROVENANCE-PERSISTENCE-MIGRATION-REVIEW`。

## 3. Findings

### P0

- 无。

### P1

- 已关闭 — `JdbcShadowRunFactRepository.create` 在 `ON CONFLICT DO NOTHING` 后只按 `idempotency_key` 返回旧行，未校验 immutable release anchors。相同 key 携带不同 `publish_id` 或 `artifact_digest` 时会被误报为创建成功，违反 `GATEX_PLAN.md` 的 conflict fail-closed contract，并可能让调用方错误关联 release provenance。
- 最小修复：加载既有行后使用 null-safe equality 比较 `publishId/artifactDigest`；不一致时抛出稳定错误码 `SHADOW_RUN_IDEMPOTENCY_PROVENANCE_CONFLICT`。异常不包含 digest/请求载荷，不修改旧行，不新增 API 映射或 GateX-3 行为。
- 回归：unit 覆盖 publish mismatch 与 digest mismatch；真实 PostgreSQL 覆盖同 key/different digest，随后重新读取原行证明 digest 不变。

### P2

- 未关闭、已接受为部署风险 — V36 在 PostgreSQL 上可事务化；Flyway 默认单事务执行时，前序 `ADD COLUMN` / `ADD CONSTRAINT` 获取的 `AccessExclusiveLock` 保留到整个 migration 提交。即使 `VALIDATE CONSTRAINT` 自身请求 `ShareUpdateExclusiveLock`，历史扫描期间也不能把整个事务描述为弱锁。
- 触发条件：目标 `shadow_runs` 规模变大、存在长事务或持续写入、DDL 等锁排队。
- 最坏结果：migration 等锁或 validation 扫描期间阻塞对表的并发访问，超过部署窗口或触发应用超时。
- 部署控制：变更前观测行数/关系大小、长事务、等待锁与写入速率；按环境设置受控 `lock_timeout` / `statement_timeout`，必要时安排维护窗口。数值不得在 V36 中硬编码，必须由部署环境测量决定。

### P3

- 既有 Maven 全局 settings line 227 存在 unrecognised `profiles` warning；非 GateX-2 引入，不影响本次编译、测试或 migration 结论。

## 4. Migration / Flyway / DDL 审查

- migration 唯一新增：`V36__gate_x2_shadow_run_provenance.sql`；V1-V35 修改数=`0`。
- `artifact_digest VARCHAR(64) NULL`，无 default、无 `NOT NULL`、无 generated expression。
- 两个 CHECK 先 `NOT VALID` 再 `VALIDATE CONSTRAINT`；迁移结束均为 validated。
- DML/backfill=`0`；无 `UPDATE shadow_runs`、无历史 digest 推测、无文件读取或默认值填充。
- unique decision 正确：未新增 `(publish_id, artifact_digest)` unique；同一 release 允许多个 Shadow Run，既有 idempotency unique 保持不变。
- column/constraints 中文 COMMENT 完整，明确不表示 admission、交易批准或 LIVE ready。
- PostgreSQL 17.7 upgrade 小样本中 V36 执行约 `0.012s`；该数据不能外推到生产规模。
- 10,000-row transaction lock probe：完成 add-column/add-check/validate 后查询 `pg_locks`，观察 `AccessExclusiveLock:true` 与 `ShareUpdateExclusiveLock:true`；随后 `ROLLBACK` 并删除 disposable container。

Forward-only / failure handling：

- 源尚未共享，但本机开发库已经执行 V36，因此本 review 保留已验证的 V36 bytes，不以注释调整制造 checksum 漂移。若提交前出现必须改 V36 的 blocker，需先单独确认本地数据库备份/重建方案，再从 disposable fresh/upgrade 重新验证。
- 执行失败：PostgreSQL transactional DDL 使 V36 整体回滚，不接受半迁移状态。
- 一旦 shared/applied：不得修改、删除或重写 V36，避免 Flyway checksum 漂移；任何整改必须新增 V37+ forward migration。
- 本轮未对生产或共享数据库执行 downgrade；本机开发库已是 V36，该本地副作用不作为 acceptance 唯一证据。

## 5. Legacy / constraints / multiplicity

- legacy unbound：`publish_id=NULL/artifact_digest=NULL → LEGACY_UNBOUND`。
- legacy publish-only：`publish_id!=NULL/artifact_digest=NULL → LEGACY_PUBLISH_ONLY`。
- release-bound：两者非空且 digest 为 lowercase 64-hex → `RELEASE_BOUND`。
- 63/65 位、大写、非 hex、空字符串、digest without publish 均由 domain 或 PostgreSQL CHECK fail-closed。
- 同一 `(publish_id, artifact_digest)` 可创建多个 run；没有误加 release-level global unique。
- migration 不回填 legacy rows，升级后 digest 继续为 `NULL`。

## 6. Domain constructors / binding

- `ShadowRun` canonical constructor 接收 nullable `artifactDigest` 并通过 `ShadowRunReleaseBindingMode.derive` 验证组合。
- legacy constructor 保持兼容并显式传入 `artifactDigest=NULL`；现有 runner 不会因此自动绑定 release。
- binding mode 仅由 persisted facts 派生，未新增冗余状态列，未引入 `LIVE_READY`、`TRADING_APPROVED` 或 admission 状态。
- 新异常只表达 create idempotency provenance collision，不承载凭证、digest 或 payload。

## 7. Immutability / update audit

- create 是唯一写入 `publish_id/artifact_digest` 的 repository 路径。
- lifecycle `UPDATE shadow_runs` 只修改 `status/version/updated_at` 与 lifecycle timestamps；不包含 provenance 字段。
- 未发现 `updateArtifactDigest`、`bindRelease`、`rebindRelease`、PATCH 或后置绑定入口。
- 两条 lifecycle sequence（stop、complete）在每次 optimistic-lock transition 后回读，provenance 保持不变。
- idempotency conflict 不再返回错误 provenance 的旧行，且不会改写既有 row。

## 8. JDBC / query audit

- fact repository INSERT/SELECT 的 column order 与 mapper 对齐；nullable PostgreSQL string 使用明确 null mapping。
- overview 与 consistency drilldown SELECT/mapper 同步包含 `artifact_digest`。
- GateX-2 delta 未引入循环 DB/API 调用、N+1、无分页读取、无边界 cache/queue/thread 或外部 IO。
- transaction propagation、optimistic-lock version contract、状态机与模块依赖边界未改变。

## 9. 验证证据

| 验证 | 结果 |
| --- | --- |
| focused binding/migration/JDBC suites | 14 tests；0 failures / 0 errors / 0 skipped |
| explicit PostgreSQL 17.7 fresh `V1→V36` + upgrade `V35→V36` | 2 tests；0 failures / 0 errors / 0 skipped |
| PostgreSQL 10,000-row lock probe | observed AEL + SUEL；transaction rollback；container removed |
| `mvn -f backend/pom.xml test` | 23 modules；1313 tests；0 failures / 0 errors / 17 existing/opt-in skipped；BUILD SUCCESS；2m32s |
| initial authority checker | `AUTHORITY_CHECK errors=0` |

失败轮次与 RCA：

- 首次 focused Maven 未引用 `-Dsurefire.failIfNoSpecifiedTests`，Maven 在 lifecycle 前拒绝参数；引用后通过。
- 首次显式 localhost PostgreSQL 使用通用测试凭据，认证失败且未创建 schema；随后只使用 `--pull=never` 的 disposable PostgreSQL 17.7，未接触生产数据。
- Docker Desktop 首次启动误在仓库生成 `%SystemDrive%` 缓存目录；确认绝对路径、父目录与内容后删除，未进入 staged scope。

## 10. Local DB / GateX-3 boundary

- disposable PostgreSQL containers 与 test data 已删除；lock probe 使用 rollback。
- implementation 全量测试曾按既有 local profile 把本机开发库 `nexus_quant.public` 升到 V36；本轮未修改或降级该库，未访问生产数据库。
- GateX-3 admission / automatic creation / scheduler / runner new behavior=`0`。
- API/frontend/Python/交易状态机/LIVE/credential/private endpoint/真实交易/AI/DH runtime 变更=`0`。

## 11. Authority after / staged scope

```text
work_batch=GateX-2
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-2-COMMIT-AND-PUSH
```

计划 staged scope：21 files，仅包含 19 个原 GateX-2 implementation files、3 个原文件中的 P1 最小修复、1 个新异常类与本 review evidence；因为修复复用了原 staged files，去重后总计 21。不得包含 `%SystemDrive%`、build output、日志、credential 或其他无关文件。

推荐 commit message：`feat(shadow): 持久化并校验发布来源`。

回滚：本轮未执行回滚。若提交前撤销，应先按 exact file list 取消暂存，再恢复 GateX-2 tracked files并删除本批新增文件；不得用 broad reset/clean。本机开发库已经执行 V36，代码回滚不自动回滚数据库，必须先备份并另行确认本地库重建/forward remediation。若 V36 已进入共享环境，只允许 V37+ forward remediation。

唯一下一动作：`NQ-GATEX-2-COMMIT-AND-PUSH`。

## 12. 最终判定

`PASS / MIGRATION_REVIEW_ACCEPTED / PROVENANCE_INVARIANTS_VERIFIED / POSTGRESQL_COMPATIBILITY_VERIFIED / READY_TO_COMMIT`。
