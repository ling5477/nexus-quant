# NQ-GATEW-3-VENUE-RULE-FACTS-SCHEMA-SECURITY-REVIEW — Attempt 01

## 1. Task and decision

- Date：2026-07-13（Asia/Shanghai）。
- Classification：`PRE_IMPLEMENTATION_SCHEMA_REVIEW + VENUE_RULE_FACT_REVIEW + OFFICIAL_PROTOCOL_RECONCILIATION + DATA_FRESHNESS_BOUNDARY + MIGRATION_DECISION + TASK_EVIDENCE`。
- Scope：NQ-only；只审查和冻结 OKX Spot venue-rule 本地事实模型、freshness、ingestion、migration 与安全边界。
- Final decision：`PASS / VENUE_RULE_SCHEMA_REVIEW_ACCEPTED / IMPLEMENTATION_AUTHORIZED`。
- Migration decision：`MIGRATION REQUIRED / PLAN ACCEPTED`。
- Selected schema：方案 A，扩展既有 `instrument_catalog`；不建立第二份 venue-rule 事实表。
- Authorization boundary：只授权后续 `NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION` 按本 evidence 实施 schema/domain/repository/public-only ingestion 与测试；不授权 order preview、Controller、LIVE、private endpoint、credential 或真实交易。

## 2. Baseline and preflight

| Item | Result |
| --- | --- |
| Branch | `dev` |
| Starting HEAD | `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553` |
| `origin/dev` | `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553` |
| Exact-head CI | GitHub Actions run `29230512781`，`completed / success` |
| Staged paths | `0` |
| Existing worktree | 仅用户允许保留的 9 个 GateW-3 blocked review 路径 |
| Existing blocker | `BLOCKED / VENUE_RULE_FACTS_UNAVAILABLE` |
| GateW-2 | commit `6543e096...` 已在 HEAD；CI green；conformance P0=0/P1=0；`REAL_SMOKE=NOT_RUN` |

既有 blocked evidence 为 [NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-01.md](NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-01.md)。本轮开始前该文件 SHA-256 为 `EB8763E5DF30081FC035559FB181D70F887F5AD0E046C6DB09D11D04A3B7B587`；本轮不得修改，结束时再次校验。

首轮预检组合命令因 PowerShell 数组表达式语法错误在解析阶段终止，未执行任何 Git 或文件写操作；修正表达式后完整重跑，以上结果以重跑为准。

## 3. Files and code paths inspected

规范与 current facts：

- `AGENTS.md`、`CLAUDE.md`、root `README.md`。
- `docs/current/GATEW_PLAN.md`、`DB_SCHEMA.md`、`API.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`、`GOVERNANCE_WORKFLOW.md`。
- `backend/nq-infra/src/main/resources/db/migration/**`；当前最高版本为 V33。
- `backend/nq-core/**`、`backend/nq-infra/**`、`backend/nq-adapter-okx/**`、`backend/nq-app/**`、`backend/nq-risk/**`、`backend/nq-backtest/**`。

关键调用链：

```text
InstrumentCatalogController POST /api/instruments/sync
→ AdapterInstrumentCatalogSyncService
→ OkxExchangeAdapter.instrumentsCache().snapshot(traceId)
→ OkxInstrumentsCache
→ GET /api/v5/public/instruments?instType=SPOT
→ map InstrumentCatalogItem
→ InstrumentCatalogRepository.upsertAll
→ JdbcInstrumentCatalogRepository
→ instrument_catalog
```

该链是既有 public instrument synchronization，不属于 order preview。后续实现只能复用 public metadata 能力，不得让 preview 触发该同步，也不得依赖 `OkxExchangeAdapter` 的 mutating trading surface。

## 4. Current schema and code facts

`V15__gateh_pre_instrument_catalog.sql` 建立 `instrument_catalog`：

| Current field | Current meaning |
| --- | --- |
| `instrument_id` | `BIGSERIAL` primary key |
| `exchange_code` / `instrument_type` | exchange 与 market type |
| `exchange_symbol` / `internal_symbol` | venue symbol 与 NQ symbol |
| `base_asset` / `quote_asset` | base/quote currency |
| `status` | instrument status |
| `tick_size` | price step |
| `step_size` | quantity step |
| `min_quantity` | minimum order size |
| `source` | fact source label |
| `synced_at` | repository upsert/write time semantic |
| `created_at` / `updated_at` | row timestamps |

现有约束与索引：

- unique `(exchange_code, exchange_symbol)`。
- unique `(exchange_code, internal_symbol)`。
- index `(exchange_code, status, internal_symbol)`。
- V27 将 `instrument_type` 限制为 `SPOT`，并要求 uppercase non-blank `status`。

`InstrumentCatalogItem` 与 `JdbcInstrumentCatalogRepository` 只映射上述字段。repository 当前逐项先查询 ID 再 insert/update；GateW 最多 3 个 symbol 使规模有界，但 implementation 仍应优先使用 PostgreSQL UPSERT/batch，避免形成可扩张的 N+1 模式。已有 HTTP response 不应在本切片扩展；schema/domain 字段不自动成为 API contract。

当前 OKX parser/cache 只保留 `instId/state/tickSz/lotSz/minSz`，并过滤非 `live` instrument。该行为会使远端变为 `suspend/preopen/test` 时本地旧 `LIVE` 行无法被覆盖，是 GateW preview 的 fail-open 风险。`AdapterInstrumentCatalogSyncService` 以请求开始时间作为 `syncedAt`，且通过拆分 symbol 推导 base/quote；两者均不满足本 review 冻结的 `observedAt` 与官方 currency fact 合同。

`OkxBootstrapFallbackFactory` 的硬编码 BTC fallback 不具备完整字段、版本和有效期，只能视为测试/bootstrap fallback，不能作为 GateW 准实盘 venue truth。

## 5. Official OKX protocol review

访问日期：2026-07-13。仅阅读 OKX 官方文档与 changelog，未调用 OKX API。

官方来源：

- [OKX API guide — Public Data / Get instruments](https://tr.okx.com/docs-v5/en/)：public data 无需认证；Spot instruments 使用 `GET /api/v5/public/instruments?instType=SPOT`；字段以字符串返回，不适用字段可能为空字符串。
- [OKX API changelog](https://www.okx.com/docs-v5/log_en/)：public/private instruments 字段会演进；2026-06-30 仍有新增字段记录，不能把当前 parser shape 当成永久协议版本。
- [OKX API best practices — instrument configuration](https://www.okx.com/docs-v5/trick_en/)：REST 可取得初始 instrument configuration；后续 tick/new-listing 更新可经 public WebSocket。GateW-3 本轮不实现 WebSocket。

逐项结论：

| Official field | GateW interpretation | Nullability/type decision |
| --- | --- | --- |
| `instId` | venue instrument ID；映射 `exchange_symbol` | official `String`；blank/missing fail-closed |
| `instType` | `SPOT` market type | official `String`；非 `SPOT` 拒绝 |
| `baseCcy` / `quoteCcy` | Spot base/quote currency facts | official `String`；Spot blank/missing fail-closed |
| `tickSz` | 官方价格步长 | decimal string；必须 `> 0` |
| `lotSz` | 官方数量步长；Spot 单位为 base currency | decimal string；必须 `> 0` |
| `minSz` | minimum order size；Spot 单位为 base currency | decimal string；必须 `> 0`；不是 minimum notional |
| `maxLmtSz` | 单笔 LIMIT 最大数量；Spot 单位为 base currency | decimal string；blank 视为 UNKNOWN/blocker |
| `maxMktSz` | 单笔 MARKET 最大数量；Spot 文档单位为 USDT | decimal string；可为空；必须与显式 unit 一起保存 |
| `maxLmtAmt` | 单笔 LIMIT 最大 USD amount | decimal string；可为空；与数量上限分别保存 |
| `maxMktAmt` | 单笔 MARKET 最大 USD amount | decimal string；可为空；与数量上限分别保存 |
| `state` | `live/suspend/preopen/test` 等 instrument state | 只有精确 `live` 可用于 GateW；其他、blank、unknown 全部 disabled/block |
| `upcChg` | 计划中的配置变化，含 `paramName/newValue/effTime` | 只对相关规则解析；结构未知或冲突 fail-closed |

官方 Public Instruments 参数中没有可直接作为 OKX Spot venue fact 的 minimum-notional 字段。不得借用 Binance `MIN_NOTIONAL`，不得从历史成交或“常见规则”推断。若 NQ 以后设置 minimum notional，它只能是显式 NQ risk rule；如果缺失，venue minimum notional 为 `UNKNOWN`。

OKX response 不提供可依赖的 provider schema version，也不提供本次成功获取时间。`source_schema_version` 与 `observed_at` 必须由 NQ 明确定义，且不得伪装成 OKX 官方版本/时间。

## 6. Field classification

### A. Official venue facts

- `instId`、`instType`、`state`、`baseCcy`、`quoteCcy`。
- `tickSz`、`lotSz`、`minSz`。
- `maxLmtSz`、`maxMktSz`、`maxLmtAmt`、`maxMktAmt`。
- 与上述规则相关且可严格解析的 `upcChg` effective change。

### B. NQ derived facts

- `internal_symbol` / normalized symbol：uppercase canonical `instId`；同时校验 official base/quote 与 symbol mapping 一致，不以拆字符串代替官方事实。
- `price_precision = max(0, tickSize.stripTrailingZeros().scale())`。
- `quantity_precision = max(0, lotSize.stripTrailingZeros().scale())`。precision 只供显示；合法性仍以 value modulo tick/lot 等于 0 判定。
- `source_schema_version`：NQ parser/schema contract，例如 `OKX_PUBLIC_INSTRUMENTS_V5_2026-07-13`；不是 provider version。
- `rule_checksum`、`next_rule_effective_at`、`fresh_until`、`availability`、`freshness_status`。

### C. NQ risk rules

- server-side symbol allowlist（1..3 个 OKX Spot symbols）。
- `maximumSingleOrderNotional`、`maximumPreviewQuantity`、daily-loss/drawdown blocker、position/exposure limit。
- 如产品确需 minimum notional，必须作为显式、可配置、可追踪的 NQ risk rule，不得命名或展示为 OKX venue minimum notional。
- OKX `maxLmtAmt/maxMktAmt` 是 venue 上限；NQ `maximumSingleOrderNotional` 是内部风险上限。两者都已知时取更严格者，但语义和来源必须分别返回。

### D. Unknown or unsupported

- OKX Spot minimum notional：`UNKNOWN`。
- account-specific fee：本地无可靠 schedule 时 `UNKNOWN`，不补零。
- provider schema version / provider observation timestamp：官方 response 不提供。
- `maxMktSz`、`maxLmtAmt`、`maxMktAmt` 等 blank 时：对应能力 `UNKNOWN/BLOCKED`。
- MARKET preview：在 market 上限、`tgtCcy`、reference price、slippage 等未单独冻结前继续不支持。

## 7. Schema options

### Option A — extend `instrument_catalog`

优势：既有表已经是 exchange instrument 当前事实源；当前 unique key 可保证同一 exchange/symbol 仅一行；无需 join、双写和 active-version 仲裁；可使用跨 venue 的通用字段名，且本 Gate 仍只实现 OKX Spot。

代价：需要 nullable columns 与 numeric widening；旧行必须保持 unavailable，不能伪 backfill。repository/domain 需要同步扩展，但既有 API 可以不变。

### Option B — new `exchange_instrument_rules`

可表达独立 FK/version/history，但会与 `instrument_catalog` 重复保存 status/tick/lot/min facts，产生双写、join、active row 选择和冲突裁决。GateW 最多 3 个 symbol，不足以证明增加第二事实源的复杂度合理。

### Option C — immutable fixture

仅允许 unit test、fake server 或显式 sandbox。fixture 必须来源可追踪、版本不可变、有效期明确且过期 fail-closed；即便满足这些条件，也不得作为 GateW 准实盘 production venue truth，不得用来规避 migration。

### Selected option

选择 A。它是当前范围内最小、单一事实源、可审计、可刷新且可 fail-closed 的方案。B 和 C 均不选。

## 8. Accepted migration plan

当前最高 Flyway 版本为 V33；下一候选版本：

```text
V34__gate_w3_venue_rule_facts.sql
```

implementation 开始前必须重新扫描版本号；若 V34 已被占用，停止并重新评审，不得抢号或修改历史 migration。

### 8.1 Existing-column changes

将 `tick_size`、`step_size`、`min_quantity` 从 `NUMERIC(24,12)` 扩为 `NUMERIC(38,18)`。理由：OKX 返回 decimal string，当前 12 位 scale 可能丢失官方 step precision；所有解析、repository 和 preview 计算继续使用 `BigDecimal`，禁止 `double`。

### 8.2 New nullable columns

| Column | Type | Source/meaning |
| --- | --- | --- |
| `max_limit_quantity` | `NUMERIC(38,18)` | official `maxLmtSz`，Spot base currency |
| `max_market_size` | `NUMERIC(38,18)` | official `maxMktSz` |
| `max_market_size_unit` | `VARCHAR(16)` | GateW OKX Spot 仅允许 `USDT` |
| `max_limit_notional_usd` | `NUMERIC(38,18)` | official `maxLmtAmt` |
| `max_market_notional_usd` | `NUMERIC(38,18)` | official `maxMktAmt` |
| `source_schema_version` | `VARCHAR(64)` | NQ parser/schema contract |
| `observed_at` | `TIMESTAMPTZ` | full public response 成功获取并完成解析/校验后的本地时刻 |
| `next_rule_effective_at` | `TIMESTAMPTZ` | 相关 `upcChg` 的最早 future `effTime` |
| `rule_checksum` | `CHAR(64)` | canonical rule document 的 lowercase SHA-256 |

全部新增列允许 null，且不设置默认值。旧行不回填 0、当前时间、伪 version 或 checksum；任一关键字段为 null 时 GateW venue availability 为 unavailable/unknown，直到一次成功 public-only refresh。

### 8.3 Constraints, indexes and comments

- 所有 numeric rule 列：`IS NULL OR > 0`。
- `max_market_size` 与 `max_market_size_unit` 必须同时 null，或 size `> 0` 且 unit=`USDT`。
- `source_schema_version`：null 或 trim 后 non-blank。
- `rule_checksum`：null 或 lowercase `[0-9a-f]{64}`。
- `observed_at IS NULL OR observed_at <= synced_at`。
- `next_rule_effective_at IS NULL OR (observed_at IS NOT NULL AND next_rule_effective_at > observed_at)`。
- 保留既有两个 unique constraints 与 status lookup index；不新增索引。`(exchange_code, exchange_symbol)` 已满足最多 3 个 symbol 的 exact lookup 和单 active row。
- 新列、约束和变更字段均添加中文 `COMMENT ON COLUMN`；migration 不创建 order/account/ledger/audit/credential 表。

数字类型 ALTER 可能对大表产生 rewrite/lock；当前未查询数据库行数，也未操作数据库。implementation 必须在 fresh PostgreSQL 与 V33 upgrade fixture 上测量 migration，避免在未知环境直接应用。

## 9. Source, checksum and freshness contract

### Source/version

- `source`：固定受控枚举语义 `OKX_PUBLIC_INSTRUMENTS`；不得接收客户端自定义 source。
- `source_schema_version`：随 NQ parser contract 显式升级；version mismatch fail-closed。
- OKX changelog 出现相关字段或 `upcChg` 语义变化时，必须 review parser/schema version；不能仅依赖运行成功判定兼容。

### Checksum

使用 UTF-8 canonical JSON：key 固定排序；decimal 使用 `stripTrailingZeros().toPlainString()`；missing 使用 JSON null；包含所有 official venue facts、NQ `source_schema_version` 与相关 `upcChg` canonical representation；排除 `observed_at/synced_at/fresh_until` 等时间字段。对 canonical bytes 计算 SHA-256，保存 lowercase hex。

相同 checksum 的 refresh 只更新 observation/write timestamps，不产生虚假 rule-change；checksum 变化必须记录 sanitized transition audit。stored checksum 与重算结果不一致、同一 symbol 出现冲突内容、unexpected schema version 均 `CONFLICT/BLOCKED`。

### ObservedAt and freshness

- `observed_at` 在一次完整 public response 成功获取、解析并验证之后、DB write 之前由 injected `Clock` 记录；不是 Controller 时间、请求开始时间、HTTP Date 或 provider generation time。
- `synced_at` 保留为 repository 写入时间；不得与 `observed_at` 混用。
- `staleAfterSeconds` 不落表，来自显式配置 `nq.gatew.venue-rules.stale-after`。建议安全默认 `600s`，允许范围 `60..86400s`；当前既有 OKX cache refresh 为 300s，600s 给一次刷新失败窗口。配置缺失或非法时为 `UNKNOWN`，不得判为 FRESH。
- `freshUntil = min(observedAt + staleAfter, nextRuleEffectiveAt)`；没有 `nextRuleEffectiveAt` 时只使用前者。
- `generatedAt > freshUntil` 时 `STALE/BLOCKED`。相关 planned change 已生效但尚未取得新 snapshot 时同样 blocked。

派生状态只允许：

- `FRESH`：字段完整、source/version expected、checksum valid、state=`LIVE`、当前时刻未超过 `freshUntil`。
- `DISABLED`：state 不是 `LIVE`。
- `STALE`：超过 `freshUntil`。
- `UNKNOWN`：threshold/time/version 等判定事实缺失。
- `UNAVAILABLE`：关键官方字段缺失或 blank。
- `CONFLICT`：重复/冲突事实、checksum mismatch、unexpected schema/source。

除 `FRESH` 外全部阻断 GateW preview。

## 10. Ingestion, idempotency and audit boundary

后续实现优先复用现有 public instrument metadata parser/cache 概念，但提取窄的 public-only reader/port；不得让 GateW application service 依赖具有 mutating capability 的 `OkxExchangeAdapter`。

冻结流程：

```text
explicit operator-triggered OKX venue-rule sync
→ server-side allowlist validation (1..3 SPOT symbols)
→ public-only instruments metadata fetch
→ parse all allowlisted states, including non-live
→ validate complete response and relevant upcChg
→ capture observedAt
→ canonicalize + checksum
→ bounded idempotent UPSERT into instrument_catalog
```

边界：

- 仅 public endpoint；无 API Key、credential、private transport 或 GateW-2 executor。
- 不由 order preview 触发；preview 请求线程永不联网。
- 不新增 scheduler、runner、startup refresh 或 background polling。
- sync 必须显式指定 OKX；禁止既有“exchange 为空则同步全部 venue”的入口用于 GateW。
- 只处理 server-side allowlisted 1..3 symbols；请求不得提交任意 exchange/raw endpoint/provider DTO。
- 必须保留并持久化 non-live state；禁止 parser 预先过滤后使旧 LIVE 行残留。
- 整批解析/校验失败不写 DB；保留上一版本且不改变 `observed_at`，其后自然进入 STALE/UNKNOWN。
- 同一 `(exchange_code, exchange_symbol)` 保持一行；相同 checksum refresh 幂等。
- tests/CI 默认 no-egress，使用 fake server/fixture；fixture 不是 production truth。
- structured audit/log 只记录 `traceId/requestId/exchange/symbol/oldChecksum/newChecksum/sourceSchemaVersion/observedAt/result`；不记录 raw payload、headers、credential 或签名。GateW-3 不引入第二张历史表，当前行保存 current snapshot；durable history 如未来需要须另起 review。
- 该同步不得修改 LIVE、账户、订单、成交、持仓、ledger、risk allowance 或 trading authorization。

## 11. Implementation and test requirements

### Migration tests

1. `VenueRuleFactsMigrationContractTest` 静态确认 V34 只扩展 `instrument_catalog`，字段/约束/comment 精确，且无 credential/order/trade/position/ledger/account/event 表写法。
2. Fresh PostgreSQL：空库从 V1 完整迁移到候选 V34。
3. Upgrade PostgreSQL：V33 → V34，既有 OKX/Binance rows 保留；新增字段全部 null，不产生假 readiness。
4. constraint tests：zero/negative numeric、bad checksum、market-size/unit 不配对、错误 timestamp order 全部拒绝。
5. 记录 numeric ALTER 的时长与锁影响；不得修改历史 migration。

### Repository/domain/ingestion tests

1. insert/update/read 新字段，精确保持 `BigDecimal` scale/value，无 `double`。
2. 相同 checksum 幂等 refresh；不同 checksum 更新并产生一条 sanitized rule-change observation。
3. legacy null row 返回 `UNAVAILABLE/UNKNOWN`，不补零/时间/version。
4. `suspend/preopen/test/unknown` 会被保存并阻断，不被 parser 丢弃。
5. threshold missing/invalid、expired、planned change effective、checksum mismatch、version conflict 均 fail-closed。
6. sync failure 保留旧 row、`observed_at` 不变并最终 stale。
7. 仅 OKX Spot、server-side allowlisted 1..3 symbols；第 4 个、非 SPOT、未知 symbol 拒绝。
8. preview layer zero network、zero credential、zero private/mutating port；sync 不是 preview side effect。
9. API response contract 在本 schema slice 不扩展；无 Controller、scheduler、runner。
10. full Maven regression 与 fresh PostgreSQL migration/repository integration 均通过后，再进入 schema conformance review。

## 12. Fee, risk, permission and preview boundary

- Fee：缺失即 `feeEstimateStatus=UNKNOWN`；不补零，不读取账户费率，不调用 private fee endpoint。
- Minimum notional：不是已核验的 OKX Spot public instrument fact。缺失时 `UNKNOWN`；如 NQ 配置内部阈值，只能标为 risk rule。
- Risk：stateful duplicate/rate-limit/daily-loss/position rules 不得在 preview 中消费或变更状态；只读事实缺失时 UNKNOWN/BLOCKED。
- Permission：GateW-2 `REAL_SMOKE=NOT_RUN`，远端 permission readiness 仍为 UNKNOWN/BLOCKED。
- Network/credential：本 review `NO OKX API CALL / NO API KEY / NO CREDENTIAL ACCESS / NO PRIVATE ENDPOINT`。
- Order preview：venue-rule blocker 只在 migration/domain/repository/public ingestion 完成、schema conformance accepted、exact-head CI green 后关闭；届时重新执行 `NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW` attempt-02。本文不恢复 order preview implementation。

## 13. Findings

### P0

- 无。

### P1

- 无未关闭项。本 review 通过 schema/freshness/ingestion fail-closed 方案关闭“venue facts 无可靠本地模型”的设计阻断；关闭只表示实施方案可接受，不表示能力已实现。

### P2

- 现有 OKX cache 丢弃 non-live instruments，可能保留旧 LIVE DB row；implementation 必须保存官方 state 并让非 LIVE 阻断。
- 现有 sync 以请求开始时间充当 `syncedAt`，没有独立 `observedAt`；implementation 必须按本合同纠正。
- `NUMERIC(24,12) → NUMERIC(38,18)` 可能产生表 rewrite/lock；真实行数和时长本 review 未通过数据库核验，implementation 必须在 disposable PostgreSQL 测量。
- 当前 repository 是逐项 lookup + write；虽然 GateW 硬上限 3 条，仍应以 bounded UPSERT/batch 收口，避免通用入口扩张为 N+1。

### P3

- 当前行只保存最新 checksum，不保存完整 venue-rule history；GateW-3 无需第二事实源。若后续审计要求 durable history，应另起 schema review，不得在本 migration 顺带增加。

## 14. Rollback

本 review 只有 docs/evidence 变更，rollback 为按本任务实际文件逐一恢复或删除新 evidence；禁止回退/覆盖既有 blocked attempt。

未来 migration 采用 forward-only rollback：不得删除历史 V34 或 `git checkout` migration。若实施后发现问题，停止 ingestion/preview、保持 fail-closed，并新增下一版本 migration 修正约束/字段；legacy nullable rows 可安全保持 unavailable。应用代码回滚不得把缺失 facts 解释为 FRESH。

## 15. Authority after and next action

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN

active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN

work_batch=GateW-3
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN

next_action=NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION
```

governance contract 支持 `IMPLEMENTATION` action 与 `NOT_STARTED` status；不修改治理 contract。后续顺序固定为：venue-rule implementation → migration/schema conformance review + exact-head CI → dry-run order preview security/risk review attempt-02。不得初始化 GateW-4。

## 16. Boundary confirmation

- 未修改 Java、SQL migration、API、frontend、CI、scripts、deploy、archive 或 root README。
- 未调用 OKX public/private API；只阅读官方文档。
- 未读取、选择、解密或输出 credential；不需要 API Key。
- 未连接数据库，未执行 migration，未运行 Maven/frontend/Python tests。
- 未创建 order、trade、position、ledger、account、event、audit business record；未提交/撤销订单。
- LIVE 保持 `DISABLED`；schema readiness、public metadata readiness 与 preview 均不构成 trading authorization。

最终结论：

```text
NQ-GATEW-3-VENUE-RULE-FACTS-SCHEMA-SECURITY-REVIEW：
PASS / VENUE_RULE_SCHEMA_REVIEW_ACCEPTED / IMPLEMENTATION_AUTHORIZED
```

## 17. Final validation

| Validation | Result |
| --- | --- |
| `git diff --check` | PASS；无 whitespace error；仅 Git 提示工作区 LF 将在后续 Git 写入时按配置转为 CRLF |
| governance lifecycle regression | PASS；ordinary/high-risk/freeze、evidence path、authority/release fixtures 全部通过 |
| current-authority next-action regression | PASS；`NOT_STARTED → IMPLEMENTATION` 受 canonical contract 支持 |
| authority checker | PASS / `CURRENT_AUTHORITY_CONSISTENT`；首次检查发现 STATUS 正文缺少 checker 要求的 `GateW-3 ... NOT STARTED` 空格形式，最小修正文案后重跑通过；未修改 contract |
| doc link checker | PASS / `DOC_LINKS_VALID`；70 links、0 errors；保留 `TESTING.md` 既有 GateJ historical warning 1 条 |
| worktree allowlist | PASS；10 paths，正好为允许保留的 9 paths + 本轮新 evidence；extra=0、missing=0、staged=0 |
| prior attempt immutability | PASS；结束 SHA-256 仍为 `EB8763E5DF30081FC035559FB181D70F887F5AD0E046C6DB09D11D04A3B7B587` |
| forbidden scope | PASS；`backend/frontend/research/scripts/deploy/.github/migration/docs/gates/docs/archive/.agents/root README/pom/package/lock` 无 diff |

未运行 Maven、frontend 或 Python tests；原因是本轮无代码、migration、API 或 frontend 实现，且任务明确禁止这些实现并指定不运行对应测试。
