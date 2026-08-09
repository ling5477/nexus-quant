# NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION — Attempt 01

## 1. Result

```text
IMPLEMENTED / PENDING_REVIEW
```

本 evidence 只证明未提交工作区中的 venue-rule facts 实现与本地验证完成；不表示 migration/schema conformance 已接受，不表示 exact-HEAD CI green，不解除 dry-run order preview blocker，不构成 LIVE 或交易授权。

下一动作：

```text
NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW
```

## 2. Starting baseline

- branch：`dev`。
- starting `HEAD`：`f9aaaa2f84ff76c77b44689ed050d0942735e757`。
- starting `origin/dev`：`f9aaaa2f84ff76c77b44689ed050d0942735e757`；`git fetch origin` 后仍对齐。
- starting worktree：clean；staged empty。
- exact-HEAD CI：GitHub Actions `NQ CI Baseline` run `29234071167`，`completed / success`。
- schema/security review evidence 已包含于 starting HEAD，结论为 `PASS / VENUE_RULE_SCHEMA_REVIEW_ACCEPTED / IMPLEMENTATION_AUTHORIZED`。
- starting highest Flyway version：V33；未发现 V34 占用、等价 venue-rule migration 或等价实现。
- implementation 当日复核 OKX 官方 API guide：Public Instruments endpoint 无需 authentication；Spot `tickSz/lotSz/minSz/maxLmtSz/maxMktSz/maxLmtAmt/maxMktAmt/state/baseCcy/quoteCcy` 语义与 accepted review baseline 未发生阻断性漂移。

Authority before：

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateW-3
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION
```

## 3. Implementation

### 3.1 V34 migration

- 创建 `V34__gate_w3_venue_rule_facts.sql`；未修改任何历史 migration。
- 扩宽 `tick_size/step_size/min_quantity` 为 `NUMERIC(38,18)`。
- 新增 nullable max quantity/market size/unit/USD amount 与 provenance/freshness columns。
- numeric null-or-positive、market size/unit pair、lowercase SHA-256、observation/write time 与 next-effective time 均有 CHECK。
- 新增字段及关键约束有中文 COMMENT；未新增第二事实表或重复索引。
- legacy rows 不回填 0、当前时间、schema version 或 checksum。

### 3.2 Domain and canonical checksum

- 扩展既有 `InstrumentCatalogItem`；全部金额/数量使用 `BigDecimal`，nullable provider facts 保持 null。
- source 固定 `OKX_PUBLIC_INSTRUMENTS`；schema version 固定为 NQ parser contract `NQ_OKX_VENUE_RULE_FACTS_V1`，不冒充 OKX API version。
- checksum 使用固定 canonical field ordering、JSON null、`stripTrailingZeros().toPlainString()`、UTF-8、SHA-256 与 lowercase hex。
- checksum 包含 official venue facts、NQ schema version 与 `nextRuleEffectiveAt` integrity fact；排除 `observedAt/syncedAt/freshUntil`、request/database id。
- 同一 facts 的 field order/decimal 等价表达产生相同 checksum；schema version、venue fact 或 next-effective fact 变化产生不同 checksum。

### 3.3 Public parser and sync

- 提取 `OkxVenueRuleFactsProvider` 窄端口；reader 只调用固定 `/api/v5/public/instruments?instType=SPOT`。
- 仅接受 `instType=SPOT`，base/quote 使用官方字段，不从 symbol 猜测；non-live state 仍进入 snapshot 和 repository。
- blank nullable max fields 保持 null；非法、零、负 decimal 与缺失 allowlisted symbol 整批 fail-closed。
- `maxMktSz` 仅按 accepted OKX Spot contract 映射 `USDT` unit；未调用 private endpoint。
- `upcChg` 的完整 canonical representation 当前不能随 row 持久化并在读回后重算，因此本轮明确后置；`next_rule_effective_at` 保持 nullable。freshness 对非空 next-effective 的截断行为已独立测试。
- application sync 只允许 server-side allowlist 中 1..3 个 OKX Spot symbols；一次 public fetch + 一次 bounded read + batch UPSERT，无无界扫描。
- fetch/parse/checksum/read 失败发生在 UPSERT 前，旧 snapshot 与 `observed_at` 不变并自然 stale。
- same checksum refresh 更新 observation/write timestamps；different checksum 更新 current facts，并只记录 symbol/old-new checksum/schema version/observedAt/result 等脱敏摘要。
- public `OkxHttpClient` 新增无 signer/credential 的明确构造入口；GateW-3 装配不引用 credential 或 private header 能力。

### 3.4 Freshness and assembly

- `VenueRuleFreshnessEvaluator` 使用注入 `Clock`。
- stale-after 范围 `60..86400`；缺失、非数字或越界返回 `UNKNOWN/BLOCKED`。
- source/version/checksum 缺失或冲突、未来 observation、non-live、必要 facts 缺失均 fail-closed。
- `freshUntil=min(observedAt+threshold,nextRuleEffectiveAt)`；`syncedAt` 和请求时间不替代 observation time。
- 只有 `gatew-venue-rules-manual` profile 与 `enabled=true` 同时满足才装配 reader/sync；默认/test/CI 无 reader、无 startup/background/scheduled sync。
- 本轮无 Controller/API、preview、scheduler/runner、frontend、credential/private transport、order/cancel、LIVE 或真实交易。

## 4. PostgreSQL migration verification

数据库仅为本机 disposable PostgreSQL 16.14 container；无 volume，trust-auth，仅绑定 `127.0.0.1:<ephemeral>`，测试结束后停止并通过 `--rm` 删除。未连接生产数据库。

| Path | Result | Evidence |
| --- | --- | --- |
| fresh V1→V34 | PASS | elapsed `5313 ms`；Flyway version `34`；precision、constraints、comments、existing keys/indexes 与 repository lifecycle 通过。 |
| V33→V34 | PASS | elapsed `319 ms`；Flyway version `34`；legacy row `1`，新增 facts 保持 null。 |

Migration lock/rewrite observation：V33 样本 `instrument_catalog` 为 `73,728 bytes`、1 行；V34 后 `pg_relation_filepath` 变化，说明该 disposable 样本发生 table rewrite。`ALTER COLUMN TYPE` 需要强表锁；本次无并发 contention，不能据此推导生产无锁。独立 conformance review 必须根据目标表规模、事务窗口与 rollback 方案评估停顿风险。

## 5. Verification

| Command / check | Final result |
| --- | --- |
| targeted checksum/freshness/parser/config/sync tests | PASS；core 11、adapter reader 7、app 9，0 failure/error。 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-adapter-okx,nq-app -am test` | PASS；23/23 reactor modules SUCCESS，`BUILD SUCCESS`，最终 run `2:15`。 |
| `mvn -f backend/pom.xml test` | PASS；23/23 reactor modules SUCCESS，`BUILD SUCCESS`，最终 run `2:15`；`nq-core` 250 tests、`nq-app` 150 tests（6 conditional skips），0 failure/error。 |
| disposable PostgreSQL integration | PASS；2 tests，0 failure/error；fresh 与 upgrade 路径均到 V34。 |
| governance lifecycle / next-action / authority | PASS；`IMPLEMENTED|PENDING_REVIEW → NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW` 为 canonical transition。 |
| current doc links | PASS；22 links checked，0 errors，保留 1 个既有 GateJ historical warning。 |
| `git diff --check` | PASS。 |
| forbidden-scope diff | PASS；frontend/research/scripts/deploy/.github/docs/gates/docs/archive/.agents/pom.xml 无 diff。 |

RCA 记录：

1. migration 首轮测试发现 SQL three-valued logic 会放过 `max_market_size=NULL + unit='USDT'`；CHECK 改为显式 both-null 或 both-non-null + positive + USDT 后通过。
2. 一次 targeted command 因 PowerShell 未引用 `-D` 参数被 Maven 解析为 lifecycle；引用参数后通过。
3. planned-change helper 清理时误删仍需的 `ArrayList` import，编译复验捕获后最小恢复。
4. 两次 Maven 复验受 Windows native memory/pagefile 资源影响：一次 Surefire JVM native allocation 失败，一次 ByteBuddy attach `CreateProcess error=1455`；未修改 pom/依赖或降低校验，资源释放并把本切片测试改用 narrow port + in-memory fake 后，原始相关 reactor 与 full Maven 均通过。
5. disposable DB 首轮传入空 test-only password property，在测试 precondition 处 fail-closed；改用不参与 trust-auth 的本地占位值后进入 DB。
6. checksum 纳入 `nextRuleEffectiveAt` 后，repository refresh fixture 改变 next-effective 却复用旧 checksum；修正 fixture 保持 same-facts refresh 后，两条 DB 路径通过。
7. authority checker 首轮发现 STATUS 的 GateW-3 display phrase 使用 underscore，未匹配治理合同要求的 `PENDING REVIEW` 展示 pattern；保留 machine exact `IMPLEMENTED|PENDING_REVIEW` 并修正 current body 后，checker `errors=0 / PASS`。
8. doc-link checker 首轮遗漏 mandatory `-Roots` 参数而未执行；按脚本 contract 精确传入本轮 7 个 current/evidence roots 后为 `22 checked / 0 errors / 1 historical warning / PASS`。

预存在且未修改的 warning：全局 Maven `settings.xml` line 227 有 unrecognized `profiles` tag；Mockito dynamic-agent future-JDK warning。二者未导致最终回归失败，本轮不修改用户全局配置或依赖。

## 6. Findings and limitations

- P0：0。
- P1：0。
- P2：0 open；migration rewrite/strong-lock 是待 conformance review 的 operational risk，不是已消除的生产风险。
- P3：1 open；既有 Spring Boot test security auto-configuration 在 full Maven 日志输出临时生成的 development password 提示。该值不是仓库/OKX credential，未复制进本 evidence，且不是本轮变更引入；后续应在独立测试日志卫生任务中抑制。
- known limitations：OKX Spot minimum notional 仍为 UNKNOWN；`upcChg` 完整 canonical representation 后置；只保存 current row、不保存 venue-rule history；未运行 real OKX sync；manual operator trigger 尚无 HTTP API（本轮明确禁止新增 API）。required Maven 的既有 `local` 集成测试还连接了本机 PostgreSQL 17.7；GateW 专项 migration 证据仍仅来自已删除的 disposable PostgreSQL 16.14，未连接生产数据库。

Order-preview blocker：仍为 `BLOCKED / VENUE_RULE_FACTS_UNAVAILABLE` 的既有 attempt-01 结论，不能由本实现自行解除。必须完成 migration/schema conformance review、后续 commit/exact-head CI，再运行 `NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW` attempt-02；本轮未初始化 preview。

## 7. Rollback

- 在未提交状态，回滚仅需按本 evidence 的精确文件清单恢复 tracked edits 并删除本轮新增文件；不得使用 `git reset --hard`、`git clean` 或覆盖用户其他改动。
- 若 V34 已在某个非生产 disposable/test DB 应用，回滚方式为删除并重建该 disposable DB；不提供 destructive down migration。
- 对未来真实环境，先回滚应用到不读取新 columns 的版本；数据库 schema 保持 forward-compatible nullable columns。若必须物理回退，必须另起经审查的 forward migration，不修改 V34。

## 8. Authority after and decision

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-3
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW
```

Final decision：

```text
NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION：
IMPLEMENTED / PENDING_REVIEW
```

Boundary confirmation：无 stage/commit/push/PR/tag；无 credential、private endpoint、scheduler、preview 请求时网络、Controller/API/frontend、LIVE 或真实交易。
