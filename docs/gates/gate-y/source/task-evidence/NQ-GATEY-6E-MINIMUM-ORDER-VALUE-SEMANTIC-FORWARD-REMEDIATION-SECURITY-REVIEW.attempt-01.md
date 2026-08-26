# NQ-GATEY-6E-MINIMUM-ORDER-VALUE-SEMANTIC-FORWARD-REMEDIATION-SECURITY-REVIEW — attempt-01

## Task classification

- ownership：NQ-only。
- type：`INDEPENDENT_MIGRATION_SECURITY_REVIEW / DATA_INTEGRITY / CONTRACT_COMPATIBILITY`。
- level：L 级高风险 migration 独立审查；只审查当前未提交 V41 remediation，除关闭 P0/P1 外不扩展功能、不重构、不重设计 schema。
- final result：`PASS / GATEY_6E_V41_SECURITY_REVIEW_ACCEPTED / P0_0 / P1_0 / NO_FAKE_BACKFILL / V1_COMPATIBILITY_ACCEPTED / INSTRUMENT_V2_SEMANTICS_ACCEPTED / JAVA_POSTGRES_PARITY_ACCEPTED / V40_TO_V41_ACCEPTED / V1_TO_V41_ACCEPTED / OKX_CALL_0 / EXCHANGE_MUTATION_0 / FIRST_REAL_ORDER_NOT_AUTHORIZED / LIVE_DISABLED / READY_TO_COMMIT`。

## Starting baseline and reviewed diff

- branch=`dev`；`HEAD == origin/dev == 4fa39a7c5700cabd6b041d6457cb4640eabb4feb`；staged=`0`。
- baseline CI=`31947934533 / NQ CI Baseline / completed / success`，head SHA 精确匹配。首次 `gh run view` 因 GitHub API EOF 失败，第二次只读重试成功。
- machine authority before/after 均为 accepted=`GateY-6D / ACCEPTED|CI_GREEN`，work=`GateY-6E / NOT_STARTED / NONE / NOT_RUN`；LIVE=`DISABLED`，kill switch=`ENGAGED`，`FIRST_REAL_ORDER=NOT_AUTHORIZED`。
- dirty files 逐项核对后严格属于 V41 remediation、回归测试和明确允许的 evidence/ledger/index；V1～V40 diff=`0`，V42=`0`，`STATUS.md` / `ROADMAP.md` diff=`0`。
- reviewed production diff：V41 migration；instrument observation domain/canonical encoder；pilot scope control-plane 与 JDBC repository。
- reviewed tests：migration contract、core canonical、infra control-plane、PostgreSQL upgrade/replay/rollback/constraint/idempotency、GateY-6B/GateY-6D prerequisite regression 与 ArchUnit。
- review 唯一代码修改为补强 `LiveSessionFactModelPostgresIntegrationTest` 的验收证明；production Java 与 V41 SQL 未修改。

## Findings and minimal fixes

### P0

- final open=0。未发现历史事实伪造、未经授权 runtime mutation、credential 暴露、tenant/account 边界破坏或不可回滚数据破坏路径。

### P1

初始发现 2 个 P1 级验证覆盖缺口，均已在 review 内通过最小测试补强关闭；未发现需要修改 production Java 或 V41 SQL 的 P1。final open=0。

1. 原 PostgreSQL 回归只比较最终 hash，未直接断言 Java/PostgreSQL canonical bytes 相等，也未覆盖 DB 侧 `VENUE_PUBLISHED` 双 symbol 排序与 decimal normalization。
   - fix：补充 v1 legacy、v2 `VENUE_NOT_PUBLISHED`、v2 `VENUE_PUBLISHED` exact canonical bytes parity；published fixture 使用逆序输入的两个 symbol 与非规范 decimal，直接比较 Java bytes 和 PostgreSQL bytes。
2. 原 lock-timeout 从 V39 卡在 V40，不能证明 V41 自身 DDL 的 bounded timeout 与 atomic rollback。
   - fix：直接锁定 `pilot_instrument_observation_items` 阻塞 V41 首个 DDL；失败后断言 column、CHECK constraint 与 Flyway V41 history 均不存在。另预建 V41 中途创建的同名函数触发 migration failure，证明此前 DDL 与 Flyway history 原子回滚。

### P2

- 1 个允许残余：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`。disposable PostgreSQL 的 116ms upgrade 与 5097ms lock-timeout 只证明本地小 fixture 的 bounded behavior，不构成 production SLA、生产数据规模或生产锁窗口测量。

### P3

- 0。

## V40 defect disposition and evidence semantics

- V40 的错误假设是把独立 `minimum_order_value > 0`、currency=`USDT` 当成所有 OKX Spot instrument 都必须发布的 venue fact。当前 typed contract 只证明 `state/tickSz/lotSz/minSz`，不能用固定值、行情推导或经验阈值伪造 minimum order notional。
- V41 引入三类明确证据语义：
  - `VENUE_PUBLISHED`：value 必须 `>0`，currency 必须非空，二者进入 canonical bytes/digest；
  - `VENUE_NOT_PUBLISHED`：value/currency 必须双 `NULL`，不允许人工填值，canonical representation 唯一确定；
  - `LEGACY_V40_REQUIRED`：只允许历史 v1/V40 数据保留旧形态，新 v2 禁止使用。
- `VENUE_NOT_PUBLISHED` 不等于“无最小交易限制”。minimum order size 继续强制；未来 PLACE 若收到 venue 额外 notional business rejection，合同保持 `DEFINITIVELY_REJECTED / NO BLIND RETRY`，不得自动加额重试或降级为 UNKNOWN。本 review 未实现、装配或调用真实 PLACE 路径。

## No fake backfill and compatibility

- V1～V40 文件未修改；V41 SHA-256=`e97f97f2ef79b9628e952a310170f10bba96899e82781656e5dedfac4c95cbc4`，是唯一新增 migration。
- V41 对历史行使用 metadata-safe default=`LEGACY_V40_REQUIRED`，在同一 migration 中立即 `DROP DEFAULT`；无历史 `UPDATE`，不改 value/currency，不把 legacy 值重标为 `VENUE_PUBLISHED`，不生成 placeholder、zero、5 USDT、推导值或 fabricated currency。
- 历史 `instrument-metadata-observation.v1` 可回读且 canonical item bytes/hash 保持不变；v1/v2 可共存。migration 后新 v1 observation 被 DB insert guard 拒绝。
- 新 production instrument observation 固定 `instrument-metadata-observation.v2`；v2 `VENUE_PUBLISHED` / `VENUE_NOT_PUBLISHED` / legacy 禁止条件由 Java 与 DB 双层强制。
- outer `pilot-scope.v1` 未升级；instrument digest 按既有 exact scope contract 进入 scope hash。

## Java / PostgreSQL canonical parity

- v1 legacy：Java exact canonical item bytes == PostgreSQL exact canonical item bytes；既有 digest/payload hash 保持不变。
- v2 `VENUE_NOT_PUBLISHED`：双 NULL 使用唯一、不含 nullable value/currency 的 deterministic bytes；Java/PostgreSQL exact bytes 相等。
- v2 `VENUE_PUBLISHED`：两个 symbol 的 DB canonical 排序、decimal normalization、value/currency 编码均与 Java exact bytes 相等；最终 digest 亦相等。
- parity 判定不再只比较最终 hash；required PostgreSQL 17.7 随机 schema 4/4 通过。

## Database constraints and transaction safety

- CHECK/trigger 强制 v2 published value/currency mandatory、not-published 双 NULL、v2 禁止 legacy、migration 后新 v1 拒绝；不能由 Java 绕过关键 invariant。
- V40 的 append-only/immutable、parent observation/item FK、complete observation-set deferred validation、digest/payload reconstruction 与 identity idempotency/conflict 合同均保留。
- invalid `VENUE_NOT_PUBLISHED` 人工值、空 `VENUE_PUBLISHED`、v2 legacy direct SQL 均以 SQLSTATE 23514 拒绝，不留下非法 item；same identity/same payload 收敛，same identity/different payload fail closed。
- JDBC 仍只位于 infra；未复制 marketdata/adapter SoR，未引入跨 domain application DTO，未新增 provider/runtime wiring。

## Migration replay, failure rollback, and lock window

- environment：repository Compose disposable PostgreSQL 17.7，loopback `127.0.0.1:5432/nexus_quant`，每个测试使用并清理随机 schema；未连接生产数据库。
- populated V40→V41：PASS；historical rows/value/currency/fingerprint/canonical bytes 逐值保持，legacy class 精确为 `LEGACY_V40_REQUIRED`，Flyway validate PASS；review measurement=`116ms`。
- V1→V41 full replay：PASS；latest=`41`，Flyway validate/checksum PASS，v1/v2 coexistence PASS。
- V41 lock-timeout：直接在 V41 首个目标表持有冲突锁，`5097ms` 后 bounded failure；column、CHECK constraint、Flyway V41 history 全部为 0，current version 仍为 40。
- V41 migration failure：预建中途同名函数触发 failure；此前新增 column/constraint 与 Flyway V41 history 均原子回滚，current version 仍为 40；Flyway 日志明确 `Changes successfully rolled back`。
- semantic constraint failure：非法 v2 evidence direct SQL 被拒绝且不留 partial item；deferred complete-set rollback 回归保持通过。
- measurement classification=`DISPOSABLE_POSTGRES_MEASUREMENT`；`PRODUCTION_LOCK_WINDOW=NOT_MEASURED`。

## Validation

| Command / suite | Result |
| --- | --- |
| `mvn -f backend/pom.xml -pl nq-app -am -DskipTests compile` | 23/23 reactor modules `SUCCESS`；`BUILD SUCCESS` |
| required focused Maven suite：GateY-6B provider + core/infra/GateY-6D + ArchUnit + required PostgreSQL | 78 tests；failures/errors/skips=`0/0/0`；provider=`23`、core/infra/GateY-6D=`36`、ArchUnit=`19`；PostgreSQL random schema=`4/4` |
| populated V40→V41 / V1→V41 / V41 failure paths | PASS；no fake backfill、exact bytes parity、constraint、lock-timeout、mid-migration rollback、Flyway validate/checksum 均通过 |
| `mvn -f backend/pom.xml test` | 23/23 reactor modules `SUCCESS`；`BUILD SUCCESS`；`nq-app=289 tests / 0 failures / 0 errors / 30 existing conditional skips`；total=`01:04 min` |
| `git diff --check` / migration and authority guards | whitespace errors=`0`；仅 LF→CRLF 工作区提示；V1～V40 diff=`0`、V42=`0`、staged=`0`、`STATUS.md`/`ROADMAP.md` diff=`0` |

Known warnings：Mockito dynamic-agent、SLF4J NOP、deprecation/unchecked、expected migration failure stack trace 与 LF→CRLF 工作区提示均为非阻断 warning。未运行 frontend/Python/E2E，因为对应 diff=`0`。未运行 production lock measurement、credential access、OKX probe、真实 pilot materialization、真实 approval、worker、deploy 或交易路径。

## Architecture and trading boundary

- 仅修正 instrument observation semantic；`pilot-scope.v1` 未升级，无 V42，无额外 migration，无真实 provider/private trading/runtime binding。
- credential read=`0`；OKX API call=`0`；PilotScope real materialization=`0`；OperatorApproval real creation=`0`；ExecutionIntent/ExecutionReceipt=`0/0`。
- PLACE/CANCEL/TRANSFER/WITHDRAW=`0/0/0/0`；worker start=`0`；provider runtime binding=`0`；exchange mutation=`0`；LIVE enable=`0`；kill disengage=`0`。
- 唯一网络访问是只读 GitHub Actions baseline 查询；未读取 credential material，未调用 OKX，未连接生产数据库，未执行 stage/commit/push/tag/deploy。

## Authority and decision

`STATUS.md` / `ROADMAP.md` 未修改；machine authority 继续：

```text
accepted_batch=GateY-6D
accepted_batch_status=ACCEPTED|CI_GREEN

work_batch=GateY-6E
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN

live=DISABLED
kill_switch=ENGAGED
FIRST_REAL_ORDER=NOT_AUTHORIZED
```

Review decision：`PASS / GATEY_6E_V41_SECURITY_REVIEW_ACCEPTED / P0_0 / P1_0 / NO_FAKE_BACKFILL / V1_COMPATIBILITY_ACCEPTED / INSTRUMENT_V2_SEMANTICS_ACCEPTED / JAVA_POSTGRES_PARITY_ACCEPTED / V40_TO_V41_ACCEPTED / V1_TO_V41_ACCEPTED / OKX_CALL_0 / EXCHANGE_MUTATION_0 / FIRST_REAL_ORDER_NOT_AUTHORIZED / LIVE_DISABLED / READY_TO_COMMIT`。

Next step：仅可提交当前 V41 implementation、review test remediation 与 evidence/ledger/index allowlist，并等待 exact-head CI；不得由本 review 推进 GateY-6E machine authority、访问 credential/OKX、materialize 真实 pilot/approval、装配 provider/worker、执行真实交易或启用 LIVE。

Commit recommendation：`fix(gatey): align OKX minimum order constraint semantics`。
