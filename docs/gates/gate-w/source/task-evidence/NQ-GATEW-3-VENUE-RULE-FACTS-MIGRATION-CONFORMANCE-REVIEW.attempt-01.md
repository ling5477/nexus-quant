# NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW — Attempt 01

## 1. Review decision

```text
PASS / MIGRATION_CONFORMANCE_ACCEPTED / READY_TO_COMMIT
```

本结论仅接受未提交工作区中的 GateW-3 venue-rule facts migration/domain/repository/public-metadata/freshness 实现，可进入精确范围提交；不表示 exact-HEAD CI green，不恢复或初始化 dry-run order preview，不构成 LIVE、订单提交或交易授权。

下一动作：

```text
NQ-GATEW-3-VENUE-RULE-FACTS-COMMIT-AND-PUSH
```

## 2. Preflight and scope comparison

- branch：`dev`。
- `HEAD == origin/dev == f9aaaa2f84ff76c77b44689ed050d0942735e757`。
- exact-head CI：GitHub Actions `NQ CI Baseline` run `29234071167`，`completed / success`，`headSha=f9aaaa2f84ff76c77b44689ed050d0942735e757`。
- starting staged paths：`0`。
- authority before：`work_batch=GateW-3 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；`next_action=NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW`。
- scope authority：用户指定的 implementation 最终报告精确 `git add --` 范围，预期 `32` paths；按任务要求不修改 Attempt-01、不重复在其中保存 manifest。
- 实际范围统计同时合并 `git diff --name-only` 与 `git ls-files --others --exclude-standard`：`actual=32`、`staged=0`。与预期范围比较：`expected=32 / actual=32 / extra=0 / missing=0`。
- 本 review 只新增本 evidence，并更新原 32 路径中已存在的 current evidence index/authority ledgers；未扩展实现路径。

## 3. Migration and schema review

- 当前 HEAD 最高已提交 migration 为 V33；本轮只新增 `V34__gate_w3_venue_rule_facts.sql`，历史 migration 无 diff。
- V34 仅扩展既有 `instrument_catalog`，未创建第二张 venue-rule/current/history 表；该表仍是唯一 current fact source。
- `tick_size`、`step_size`、`min_quantity` 为 `NUMERIC(38,18)`；新增 `max_limit_quantity`、`max_market_size`、`max_limit_notional_usd`、`max_market_notional_usd` 同为 `NUMERIC(38,18)`。
- 新增 `max_market_size_unit`、`source_schema_version`、`observed_at`、`next_rule_effective_at`、`rule_checksum`；所有新增列 nullable 且无伪默认值。
- 旧行不回填 `0`、`now()`、伪 checksum 或伪 schema version；`synced_at` 继续表示数据库写入时间，`observed_at` 独立表示完整 provider response 的观测时间。
- numeric CHECK 明确为 null 或 `> 0`；market size/unit 明确 both-null 或 both-present + positive + `USDT`；checksum 为空或 lowercase 64 位 hex；schema version 非空时不得 blank；`observed_at <= synced_at`；next effective 非空时要求 observed 非空且严格更晚。约束显式处理 SQL 三值逻辑，未发现错误拒绝合法旧行或接受非法组合。
- 新列和新 CHECK constraint 均有中文 comment；既有 unique constraints/indexes 保留。

## 4. Disposable PostgreSQL and operational risk

- 使用本机 disposable PostgreSQL `16.14` container；无 volume、仅绑定 `127.0.0.1`、未 pull image、未连接生产数据库。
- fresh V1→V34：`PASS`，2-test suite 总体 0 failure/error；独立计时 `32707 ms`，Flyway version `34`，repository insert/update/read lifecycle、precision、constraints、comments 与既有 keys/indexes 通过。
- V33→V34：`PASS`；独立计时 `2498 ms`，Flyway version `34`；legacy row `1`，新增 venue facts 保持 null。
- upgrade 样本 `instrument_catalog` relation size 为 `73,728 bytes`；`pg_relation_filepath` 在 V34 后变化，证明该样本发生 table rewrite。
- 独立 contention observation 显示 reader 持有 `AccessShareLock` 时，V34 `ALTER TABLE ... ALTER COLUMN TYPE` waiter 请求 `AccessExclusiveLock` 且未获授予。该强锁/rewrite 是 P2 operational risk；目标生产/共享部署表规模与维护窗口未在本轮验证，不能外推为生产无锁。
- 本机开发 PostgreSQL 17.7 已处于 Flyway 34；这只说明本地共享开发库已执行 V34。生产或其他共享环境是否执行 V34未验证，本轮未连接或修改这些环境。
- rollback：disposable DB 删除重建；未来环境采用备份恢复或后续 forward migration，不修改 V34、不设计 destructive down migration。

## 5. Domain, official mapping and parser

- `InstrumentCatalogItem`、`OkxVenueRuleContract`、`OkxVenueRuleFact`、`OkxVenueRuleFactsSnapshot` 的金额/数量使用 `BigDecimal`；nullable provider fields 保持 null，无补零。
- source 固定为 `OKX_PUBLIC_INSTRUMENTS`；`NQ_OKX_VENUE_RULE_FACTS_V1` 明确是 NQ parser contract，不冒充 OKX API version。
- LIMIT quantity、MARKET size 与 LIMIT/MARKET USD amount 分字段持久化；未把 minimum notional 伪装为 OKX Public Instruments 官方字段。
- base/quote 读取 `baseCcy/quoteCcy`，不拆 symbol 猜测；non-live state 不过滤并可覆盖旧 live state；records 不包含 raw provider payload。
- reader 只使用固定 `/api/v5/public/instruments?instType=SPOT`，完整校验 `code/data` 后返回；allowlist 限制 1..3 symbols，缺失/重复 symbol、错误 instType、非法/零/负 decimal、unknown/inconsistent unit 整批 fail-closed。
- blank/null optional max fields 保持 null；malformed response 不折叠为空数据；测试 fake 明确断言 exact path/query，不使用真实 OKX。
- `OkxHttpClient` public-only constructor 不装配 signer/credential/private headers，既有 private client constructors/signing 边界未削弱。
- `upcChg` 完整 canonical representation 因不能随 current row 完整持久化而明确后置；`next_rule_effective_at` 当前保持 nullable，未伪造 planned-change 完整性。

## 6. Canonical checksum

- canonical field ordering 固定；null 使用固定 JSON null；decimal 使用 `stripTrailingZeros().toPlainString()`；UTF-8 + SHA-256 + lowercase hex。
- schema version、instrument identity/state、base/quote、tick/lot/min、LIMIT/MARKET quantity/amount、market size unit 与 next effective integrity fact均纳入。
- `observedAt`、`syncedAt`、freshness、request ID、DB ID 排除；相同 facts 与 `1.0/1.00` 生成同 checksum，事实或 schema version 变化生成不同 checksum。
- P2 test gap：测试验证稳定性/变化/decimal 等价，但 expected digest 仍由生产 calculator 生成后比较，没有独立 hard-coded SHA-256 fixed vector。

## 7. Repository, bounded sync and transaction boundary

- API 只接受 1..3 个非空 allowlisted symbols，exchange 固定 `OKX`、market type 固定 `SPOT`；空列表、超过 3 个、未授权 symbol fail-closed。
- 一次 public fetch、一次 bounded `IN` lookup、一次 JDBC batch UPSERT；无全表扫描、无 DB N+1、无循环外部 API。
- 网络 fetch/parse/checksum 在 transaction 外；事务仅覆盖 bounded repository UPSERT。
- 相同 checksum refresh observation/write time；不同 checksum 更新 current facts；non-live 覆盖旧 live；失败发生在 UPSERT 前，不更新 `observed_at`，旧事实自然进入 stale。
- legacy instrument sync update 以 `source_schema_version IS NULL` 保护，不覆盖 GateW facts；UPSERT 不保留双重事实源或 raw payload。
- 唯一键/SQL 异常不吞并，事务 fail-closed；变化日志只含 symbol、old/new checksum、schema version、observed time/result 等摘要。

## 8. Freshness, configuration and no-egress

- evaluator 使用注入 `Clock`；threshold 只允许 `60..86400`，缺失、非数字或越界均 `UNKNOWN/BLOCKED`，不默认 600 秒 FRESH。
- `observedAt` 缺失/未来、source/schema/checksum 缺失或冲突、必要 facts 不完整、non-live 均 fail-closed；过期为 `STALE/BLOCKED`。
- `freshUntil=min(observedAt+threshold,nextRuleEffectiveAt)`；`syncedAt` 不替代 observation time。
- 仅 `gatew-venue-rules-manual` profile 与 `enabled=true` 同时满足时装配 reader/sync；default/test/CI、profile-only、flag-only 都不装配。
- context startup 不 fetch；无 scheduler、runner、startup/background sync、Controller/API/frontend、order preview、order submission 或 LIVE。
- 静态敏感词扫描命中仅限既有 `OkxHttpClient` credential/private 能力与否定性注释；语义追踪确认 GateW-3 venue-rule path 只使用 public-only constructor 和固定 public endpoint。

```text
NO CREDENTIAL
NO PRIVATE ENDPOINT
NO ORDER PREVIEW
NO ORDER SUBMISSION
NO CONTROLLER/API
NO FRONTEND
NO SCHEDULER/RUNNER
NO LIVE
```

## 9. Regression evidence

| Command / check | Result |
| --- | --- |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-adapter-okx,nq-app -am test` | PASS；23/23 reactor modules SUCCESS，`BUILD SUCCESS`，05:55。 |
| `mvn -f backend/pom.xml test` | PASS；23/23 reactor modules SUCCESS，`BUILD SUCCESS`，02:26；`nq-app` 150 tests / 0 failure / 0 error / 6 conditional skips。 |
| forced disposable PostgreSQL integration | PASS；2 tests / 0 failure / 0 error / 0 skip；fresh 与 upgrade 均到 V34。 |
| governance lifecycle regression | PASS。 |
| current-authority next-action regression | PASS。 |
| current authority checker | PASS；`PASS / CURRENT_AUTHORITY_CONSISTENT`。 |
| docs/current link checker | PASS；0 errors，保留 1 个既有 GateJ historical warning。 |
| static security / forbidden scope / `git diff --check` | PASS。 |

测试隔离：reader 使用 narrow fake 并断言 exact endpoint；disposable DB 只绑定 loopback；默认 Maven 不运行 real OKX sync；未读取/使用 credential，未调用 OKX public/private API。全量 Maven 的 forced PostgreSQL tests 按既有 guard 条件 skip，真实 disposable 结果来自本轮显式 required=true 独立执行。

## 10. Findings and minimal fixes

- P0：0。
- P1：0。
- P2-1：V34 对三个 numeric columns 的 TYPE 变更发生 table rewrite，并请求 `AccessExclusiveLock`；目标环境表规模/窗口须在部署前单独核验。
- P2-2：checksum test 缺少与生产实现独立的 hard-coded fixed vector；当前稳定性、变化与 decimal 等价行为已有覆盖。
- P2-3：V34 SQL contract 与真实 PostgreSQL tests 覆盖核心正负路径，但没有逐个新增 numeric column、blank schema version、uppercase checksum、market pair 双向缺失等全部组合的独立 negative test。
- P3-1：既有 full Maven Spring test auto-configuration 输出临时 development password 提示；不是仓库/OKX credential，本 evidence 不复制其值，本轮不扩展到全局测试日志治理。
- review 中最小修复：无。未发现 P0/P1，因此按授权不修改生产实现、migration 或测试。

Known limitations：OKX Spot minimum notional 仍为 UNKNOWN；完整 `upcChg` representation 后置；只有 current facts、无 history table；未访问真实 OKX；manual sync 无 HTTP operator entry；生产/共享环境 V34 状态和维护窗口未验证。

## 11. Immutable evidence verification

以下既有 evidence 未修改，review 前后 SHA-256 必须一致：

```text
EB8763E5DF30081FC035559FB181D70F887F5AD0E046C6DB09D11D04A3B7B587  NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-01.md
8633570B41C057597D1A9DF98679CD60462ACFF2D10FCD46CA7333D7D38B9ECA  NQ-GATEW-3-VENUE-RULE-FACTS-SCHEMA-SECURITY-REVIEW.attempt-01.md
7B75FBBAF42881F24AF17C5AAC13441794E2D7830A611AFA6184EC8DEA0A918F  NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION.attempt-01.md
```

## 12. Authority after, rollback and boundary

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-3
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-3-VENUE-RULE-FACTS-COMMIT-AND-PUSH
```

工作区 rollback：仅按本轮精确路径恢复 tracked edits、删除本轮新增 review evidence；不得使用 `git reset --hard`、`git clean` 或覆盖其他用户改动。数据库 rollback 遵循 backup restore / forward migration；不修改历史 migration，不提供 destructive down migration。

边界确认：未 stage、commit、push、PR 或 tag；未修改 frontend/research/scripts/deploy/.github/docs/gates/docs/archive/.agents/pom.xml；未接触 credential、private endpoint、order preview、order submission、LIVE 或真实交易。
