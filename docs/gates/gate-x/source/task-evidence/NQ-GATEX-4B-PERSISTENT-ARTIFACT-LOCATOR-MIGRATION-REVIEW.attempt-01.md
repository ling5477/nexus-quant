# NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-REVIEW — attempt-01

## Task classification

- 归属：NQ-only。
- 类型：`INDEPENDENT_MIGRATION_REVIEW`，主分类为 `CODE_ANALYSIS`，包含 PostgreSQL concurrency、immutability、publish lifecycle、lock risk 与 security boundary 审查。
- 等级：高风险独立 migration review。
- 主 skill：`db-schema-migration-review`；`nq-dh-workflow-router` 用于 Gate/边界分类，`nq-docs-writer` 仅用于本 evidence 与 current authority 同步。
- 禁止范围：不启动 GateX-4C，不实现 resolver/API/UI，不接触 LIVE、交易、凭证、生产数据库，不 commit/push。

## Review status

```text
PASS /
MIGRATION_REVIEW_ACCEPTED /
LOCATOR_IMMUTABILITY_VERIFIED /
CONCURRENT_FIRST_BIND_VERIFIED /
POSTGRESQL_COMPATIBILITY_VERIFIED /
READY_TO_COMMIT
```

P0=0，P1=0；保留 2 个 P2 与 1 个 P3，不阻断本批进入 commit 前复核。

## Starting HEAD

```text
5f4824eecaac5cffbbc314fb8f767bd6ba45c29f
```

## origin/dev HEAD

```text
5f4824eecaac5cffbbc314fb8f767bd6ba45c29f
```

分支为 `dev`；进入时 unstaged=0、untracked=0，只有 21 个附件允许的 GateX-4 remediation chain staged 路径。

## Authority before

```text
work_batch=GateX-4B
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-REVIEW
live=DISABLED
```

`scripts/docs/check-current-authority.ps1` 返回 `errors=0 / PASS / CURRENT_AUTHORITY_CONSISTENT`。

## Existing staged remediation chain

- 保留 GateX-4 blocker、safe-root blocker、4A schema review、authority transition 与 4B implementation evidence。
- 保留 V37、domain/service/repository/JDBC/tests 与 current docs。
- 本轮没有回退、覆盖或重写前序 evidence。

## V37 DDL review

- [V37](../../../../backend/nq-infra/src/main/resources/db/migration/V37__gate_x4b_persistent_artifact_locator.sql) 第 6～8 行新增 `artifact_storage_key VARCHAR(128) NULL` 与 `manifest_storage_key VARCHAR(128) NULL`，无 default。
- 第 10～39 行使用 pair CHECK 与两个 syntax CHECK，先 `NOT VALID` 后逐个 `VALIDATE CONSTRAINT`。
- 第 41～72 行建立 immutable function/trigger；第 74～87 行补齐中文业务注释。
- migration 内没有 `UPDATE backtest_publish_records`、backfill、guessed locator、path persistence 或 historical rewrite。
- V1～V36 staged diff 为 0；migration 目录共有 37 个 versioned SQL，最大版本 37，V37 数量 1，无重复版本。

结论：forward-only DDL、版本顺序、nullable rollout 与历史兼容设计通过。

## Pair constraint review

`chk_backtest_publish_artifact_keys_pair` 只允许 `NULL/NULL` 或 `non-NULL/non-NULL`。真实 PostgreSQL 覆盖 partial pair rejection；Java `BacktestPublishArtifactLocator` 构造器也拒绝半对输入，JDBC mapper 使用 `getString` 保留 SQL NULL，不会转成空串。

## Storage-key syntax review

数据库与 Java 共同使用：

```regex
^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$
```

并额外拒绝 `..`。真实 PostgreSQL negative coverage 已包含：空串、129 字符、`/`、`\`、`:`、`..`、Windows absolute path、URI、控制字符、前导空白与尾随空白。全部 fail-closed；允许值只能是单段 ASCII opaque identifier，不是路径、URI、digest 或 trusted root。

## Partial UNIQUE review

结论：`NO UNIQUE = ACCEPTED`。

- 全仓 production/provider contract 检索没有找到“storage key globally unique per release”的正式 invariant，也没有已接线 producer/provider。
- 当前 key 可以属于 provider namespace；缺少全局独占事实时增加 partial UNIQUE 会把未冻结的 provider 语义硬编码进数据库。
- 真实 PostgreSQL 已证明两个不同 publish identity 使用相同 pair 当前合法。
- 若未来 provider contract 冻结全局唯一语义，必须使用 V38+ forward migration，并独立评估 index build 与 lock window。

## Publish lifecycle review

1. `FAILED → SUCCEEDED` 是合法事实：`BacktestPublishService.failPublish(...)` 持久化 FAILED；后续 retry 不命中 succeeded-idempotent return，可在成功路径原子 upsert SUCCEEDED。
2. 其他首次产生 locator 的合法路径只有“新 publish 首次成功时 INSERT non-NULL pair”；当前 producer 尚未接线，普通 HTTP publish 明确使用 unbound pair。
3. production 源码中唯一写表路径是 `JdbcBacktestPublishRecordRepository.upsert(...)`；其他命中均为测试 fixture。未发现 locator 旁路 production writer。
4. 状态与 locator 分两次 UPDATE 无法绕过：先把 FAILED 改为 SUCCEEDED 后再绑定会因 OLD status 不是 FAILED 被 trigger 拒绝；先绑定而保持 FAILED 也被拒绝。
5. 既有 unbound retry/recovery 不受阻：locator 未变化时 trigger 不拦截；FAILED/null 可继续变为 SUCCEEDED/null，表达 producer 未接线或 legacy unbound。
6. 相同 locator pair 重试保持幂等：service 对 succeeded+same pair 直接返回；repository 的 `IS NOT DISTINCT FROM` 条件允许同 pair 重放。
7. locator 不变的普通 row/status 写入不触发不可变异常；当前 service 没有已绑定成功记录的额外合法状态迁移。
8. clear/rebind/different pair 被 service、repository WHERE 与 DB trigger 三层拒绝。

## Trigger review

- trigger 只在 locator 列出现在 UPDATE target list 时执行；值未变化时返回 NEW，允许同 pair 重放。
- OLD pair 任一非 NULL 且发生变化时立即抛出 SQLSTATE `23514`。
- 仅 `OLD.status=FAILED`、`NEW.status=SUCCEEDED`、NEW pair 完整时允许 NULL→pair。
- INSERT 的约束由 pair/syntax CHECK 保护，调用 ownership 由唯一 production repository + typed domain/service 边界保护。
- 普通数据库 owner/superuser 能禁用 trigger 不属于应用可绕过路径；本轮未新增 DB-owner API。

结论：未命中 `PUBLISH_LOCATOR_LIFECYCLE_MISMATCH`。

## Immutability review

- 已绑定 pair 的 different-pair rebind、clear 与 partial update 均失败。
- legacy SUCCEEDED/null 的 retroactive binding 失败。
- FAILED/null → SUCCEEDED/pair 首次绑定成功。
- repository 冲突返回通用 `backtest publish artifact locator conflict`，不输出 server path 或 locator 值。

结论：`LOCATOR_IMMUTABILITY_VERIFIED`。

## Concurrent first-bind design

[PostgreSQL integration test](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/research/BacktestPublishArtifactLocatorPostgresIntegrationTest.java) 新增同一 FAILED/null publish row 的两个独立 JDBC auto-commit transaction：

- 两个 worker 使用 start barrier 同时提交；
- 每条 JDBC statement query timeout=5 秒；
- future wait timeout=10 秒；
- JUnit test 总 timeout=90 秒；
- finally 精确删除随机 `gatex4b_concurrency_*` schema。

## Concurrent different-pair result

```text
transaction A: artifact_competing_a / manifest_competing_a
transaction B: artifact_competing_b / manifest_competing_b
result: SUCCESS=1 / CONFLICT=1
final: complete pair A OR complete pair B
mixed pair: 0
```

PostgreSQL `ON CONFLICT` 对唯一 `backtest_run_id` 的冲突行串行化；后到事务重新评估 WHERE 时看到已绑定 pair，不同 pair 返回 updated=0，repository 稳定转换为 conflict。

## Concurrent same-pair result

```text
transaction A: artifact_same_pair / manifest_same_pair
transaction B: artifact_same_pair / manifest_same_pair
result: SUCCESS=2
final: artifact_same_pair / manifest_same_pair
```

两次写入使用同一完整 record 与 locator pair，结果等价且没有冲突或重绑。

## Last-write-wins proof

competing pair 用例断言必须恰好一个 success、一个 conflict，并重新读取最终行断言只能是完整 A 或完整 B；若 B 能覆盖 A、产生 mixed pair 或静默返回成功，测试立即失败。因此 competing locator 不存在 last-write-wins。

## Repository/JDBC review

- INSERT columns、20 个 placeholders 与 20 个 args 顺序一致；两个 locator 位于末尾。
- SELECT 与 mapper 三条读取路径均包含 pair；SQL NULL 保持 Java null。
- `ON CONFLICT ... WHERE` 只允许 existing NULL/NULL 首次绑定或 exact same pair 重放；different pair 的 row count=0 转成通用冲突。
- port 只暴露原子 `upsert`，没有普通 rebind/clear API。
- 全仓 multiline SQL 搜索确认唯一 production INSERT/UPDATE writer 为本 repository。

## Publish service review

- public HTTP DTO 只有 `displayName` 与 `strategyVersionId`，不能提交 storage key、path 或 URI。
- API service 只调用普通 `publish(...)`；该方法固定传入 `BacktestPublishArtifactLocator.unbound()`。
- `publishWithArtifactLocator(...)` 当前只在 service 与 tests 出现，接受经过 Java syntax/pair validation 的 typed locator；没有从 `publishRecordId + suffix`、digest、filesystem path 或客户端值生成 key。
- exception 文案不包含 locator/server path；producer 未接线时不会生成 fake locator。

## Producer status

```text
PERSISTENCE_READY / PRODUCER_NOT_YET_CONNECTED
```

该状态与代码一致；不存在 production storage producer、resolver 或 API wiring。

## Fresh migration

真实 PostgreSQL 17.7：fresh schema 从 V1 迁移到 V37，current version=37，schema/constraint/comment/trigger/duplicate/no-unique 与 JDBC contract 全部通过。

## Upgrade migration

真实 PostgreSQL 17.7：先迁移至 V36，插入 legacy SUCCEEDED/null 与 FAILED/null，再升级 V37。两个历史 row 均保持 NULL/NULL；legacy successful late bind 被拒绝，FAILED→SUCCEEDED first bind 成功。

## Flyway validation

fresh、upgrade 与 concurrency 三个 schema 均在 migrate 后执行 `Flyway.validate()` 并通过；V37 version 唯一，V1～V36 无 staged byte change。

## Constraint regressions

- pair、invalid key、legacy compatibility、same-pair idempotency、different-pair conflict、clear/rebind、duplicate across releases 与 no partial UNIQUE 全部通过。
- focused service 3/3、migration contract 1/1、PostgreSQL integration 3/3；0 failure / 0 error / 0 skip。

## Concurrency regressions

- competing first bind：PASS（通过）。
- same-pair concurrent bind：PASS（通过）。
- timeout/deadlock guard：PASS（通过）。
- disposable schema cleanup：PASS（通过），残留 `gatex4b_*` schema=0。

## Table-size evidence

disposable PostgreSQL 三个 phase 均为小样本：

```text
rows=2
relation_bytes=8192
index_bytes=65536
long_transactions=0
lock_waits=0
```

本地 disposable 规模不等于生产规模，不能外推生产 migration duration 或 lock window。

## Lock/scan assessment

| V37 step | 可能 lock / scan | 写入影响与失败条件 |
| --- | --- | --- |
| `ADD COLUMN ... NULL` | `ACCESS EXCLUSIVE`；无 default，通常 catalog-only，无 table rewrite | 获取锁前受 V37 当前 5 秒 `lock_timeout` 限制；获取后阻塞并发访问 |
| `ADD CHECK ... NOT VALID` | DDL 强锁；不扫描历史行，新写入立即受约束 | 获取锁失败则整个 migration 回滚 |
| 三次 `VALIDATE CONSTRAINT` | 每次扫描全表，命令自身通常使用 `SHARE UPDATE EXCLUSIVE` | Flyway 默认单事务使前序更强 `ACCESS EXCLUSIVE` 一直持有到 commit，因此 validation 期间不能宣称整体弱锁 |
| `CREATE FUNCTION` | catalog metadata，不扫描目标表 | function 创建失败则 migration 原子回滚 |
| `CREATE TRIGGER` | 目标表 metadata lock；不扫描历史行 | 从创建后对 UPDATE 生效；本事务仍受前序最强锁覆盖 |
| COMMENT / RESET | metadata；不扫描数据 | 任一步失败均回滚 V37 |

V37 当前 `SET lock_timeout='5s'` 只限制锁获取等待，不限制 validation 扫描/持锁时长，也不能代表生产适宜值。它在超时时原子失败，不降低约束或造成部分 apply，因此按 P2 保留；本 review 不把 5 秒写成生产 sizing 结论，也不凭空设置 `statement_timeout`。

## Deployment recommendations

- 部署前读取目标环境 row count、relation/index size 与写入速率。
- 确认长事务、idle-in-transaction、lock queue 与 blocker PID。
- 在目标环境变更流程中明确 timeout、停止条件和重试决策；不要从本地 2-row 样本推导固定值。
- validation 预计超出可接受窗口时使用 maintenance window；不要在已提交 V37 上临时改字节。
- rollout 窗口暂停旧 writer，避免继续产生新的 SUCCEEDED/null 行。
- V37 commit/push 后 immutable；任何 schema 调整只允许 V38+ forward remediation。

## GateX-4 security boundary

locator persistence 不等于 trusted-root resolver、artifact verified、admission accepted、Shadow Run created 或 trading authorized。当前继续保持：

```text
GateX-4 API/UI = BLOCKED
WAITING FOR SERVER-CONTROLLED ARTIFACT BINDING
LIVE = DISABLED
Shadow trading = NOT ENABLED
```

本轮 resolver/API/UI/Shadow create/start/交易/credential/private endpoint/AI/DH runtime 变更均为 0。

## Files inspected

- `backend/nq-infra/src/main/resources/db/migration/V37__gate_x4b_persistent_artifact_locator.sql`
- `backend/nq-research/**/BacktestPublishArtifactLocator.java`
- `backend/nq-research/**/BacktestPublishRecord.java`
- `backend/nq-research/**/BacktestPublishService.java`
- `backend/nq-research/**/BacktestPublishRecordRepository.java`
- `backend/nq-infra/**/JdbcBacktestPublishRecordRepository.java`
- locator migration contract/PostgreSQL/service tests
- publish API request/controller/API service 边界
- 所有 non-generated `INSERT/UPDATE backtest_publish_records` 命中
- GateX-4A/4B evidence 与 current authority/docs。

## Files created

- `docs/current/evidence/gate-x/NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-REVIEW.attempt-01.md`

## Files changed

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/research/BacktestPublishArtifactLocatorPostgresIntegrationTest.java`：新增 mandatory concurrency 与完整 invalid-key PostgreSQL regression。
- `README.md`
- `docs/current/README.md`
- `docs/current/STATUS.md`
- `docs/current/ROADMAP.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`

V37、domain、repository、JDBC 与 service 生产实现本轮未修改。

## P0

无。

## P1

无。

## P2

1. `PRODUCTION_LOCK_WINDOW_NOT_MEASURED`：生产行数、关系大小、写入速率、长事务和实际 migration lock duration 未测；Flyway 单事务会把前序强锁持有到 commit。
2. `STORAGE_PROVIDER_GLOBAL_UNIQUENESS_NOT_FROZEN`：当前没有全局唯一 invariant，因此 NO UNIQUE 正确；未来 provider 若要求独占必须 V38+ 独立审查。

## P3

1. `TOOLING_DOWNGRADE_AND_WARNINGS`：`idea-mcp`、`filesystem`、`postgres`/`MCP_DOCKER` MCP 未暴露，按规则降级为 PowerShell、`rg`、Maven、psql 与 Docker CLI；全后端仍有既有 SLF4J NOP、Mockito dynamic-agent/CDS warning 与 opt-in skips。源码/SQL/真实 PostgreSQL事实可信度高，生产规模事实未验证。

## Review fixes

- 没有 P0/P1 production fix。
- 为满足 mandatory concurrency evidence，仅扩展既有 PostgreSQL integration test：competing/same-pair 并发、timeout 与 URI/control/whitespace invalid keys。
- 没有修改 V37、domain、repository、JDBC 或 service。

## Regression after fixes

1. Focused command：23-module reactor `BUILD SUCCESS`；service 3、contract 1、PostgreSQL integration 3，全部通过且 PostgreSQL required=true，无 skip。
2. Full backend：23 个 reactor module 全部 `SUCCESS`；`nq-app` 最终 module summary 为 247 tests、0 failures、0 errors、13 skipped；`BUILD SUCCESS`，52.026 秒。skipped 为既有/opt-in，mandatory locator PostgreSQL suite 3/3 未 skip。
3. 第一次 focused invocation 因 PowerShell 未引用 JDBC `-D` 参数，在 lifecycle 前失败并误尝试解析 Maven plugin；引用参数后重跑通过，未把失败轮写成测试通过。
4. 临时 loopback-only PostgreSQL 17.7 container、全部随机 schema 与容器均已删除；原本机 5432 未连接、未修改。

## Evidence chain

1. `NQ-GATEX-4-MINIMAL-API-UI-CLOSURE-IMPLEMENTATION.attempt-01.md`
2. `NQ-GATEX-4-SAFE-ARTIFACT-ROOT-BINDING-IMPLEMENTATION.attempt-01.md`
3. `NQ-GATEX-4A-PERSISTENT-ARTIFACT-LOCATOR-SCHEMA-REVIEW.attempt-01.md`
4. `NQ-GATEX-4-PERSISTENT-ARTIFACT-LOCATOR-REQUIRED-BLOCKED.attempt-01.md`
5. `NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-IMPLEMENTATION.attempt-01.md`
6. 本 review attempt-01。

## Authority after

```text
work_batch=GateX-4B
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4B-COMMIT-AND-PUSH
live=DISABLED
```

## Staged scope

最终只允许原 21 个 GateX-4 remediation chain 路径，加本 review evidence，共 22 个 staged 路径；不得混入 generated output、container state、credential、frontend、Python、deploy 或 GateX-4C 文件。

## Review decision

```text
MIGRATION_REVIEW_ACCEPTED
LOCATOR_IMMUTABILITY_VERIFIED
CONCURRENT_FIRST_BIND_VERIFIED
POSTGRESQL_COMPATIBILITY_VERIFIED
```

## Commit recommendation

```text
feat(research): persist strategy release artifact locators
```

本任务不执行 commit/push。

## Rollback

- 当前尚未 commit/deploy；回滚本 review 应使用精确 inverse patch 删除本 evidence、并只撤销本轮新增的 concurrency/current-authority hunks。
- 不得对整个 staged 文件执行粗粒度 restore，因为同一文件包含前序 GateX-4 remediation chain。
- 数据库未部署，无 production schema rollback；V37 应用后保留 nullable columns/constraints/trigger，应用回滚忽略新列，后续只允许 V38+ forward remediation。

## Next action

唯一下一动作：

```text
NQ-GATEX-4B-COMMIT-AND-PUSH
```

不得启动 GateX-4C。

## Final decision

```text
PASS /
MIGRATION_REVIEW_ACCEPTED /
LOCATOR_IMMUTABILITY_VERIFIED /
CONCURRENT_FIRST_BIND_VERIFIED /
POSTGRESQL_COMPATIBILITY_VERIFIED /
READY_TO_COMMIT
```
