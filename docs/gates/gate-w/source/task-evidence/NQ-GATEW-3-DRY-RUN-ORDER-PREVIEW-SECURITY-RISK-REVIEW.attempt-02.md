# NQ GateW-3 Dry-run Order Preview Security/Risk Review Attempt-02

> 任务：`NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-02`
> 模式：`ROUND_2_COMBINED`
> 范围：NQ-only；security/risk review 与条件式最小实现前合同冻结
> 日期：2026-07-14

## 1. Authority reconciliation

- 起始 authority：`GateW-3 / COMMITTED|CI_FAILED|FIX_REQUIRED`，commit `54c7bdd2caee5602441ce983b33c4cd2466ee263`，CI run `29253811976`（失败）。
- 修复事实：commit `fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28`，CI run `29260881801`，`completed / success`，exact-head match，10 个实际 jobs 全部成功。
- 治理 enablement：commit `ea58c34e44169e1a459750a0265017c622eea9b6`，CI run `29271620336`，`completed / success`，10 个实际 jobs 全部成功；该 commit 只提供同 batch GREEN continuation 治理能力，不是 preview/venue-rule 实现 commit。
- canonical mode：`POST_FIX_CI_SUCCESS_RECONCILIATION`；`exactHeadMatch=true`；`ciConclusion=success`。
- transition：`COMMITTED|CI_FAILED|FIX_REQUIRED -> COMMITTED|CI_GREEN|CONTINUE_REQUIRED`。
- reconciliation 后仍保持：`accepted_batch=GateW-2`、GateW `IN_PROGRESS|NOT_FROZEN`、GateW-4 未初始化、LIVE disabled、private trading not implemented。

## 2. Attempt-01 immutable baseline

Attempt-01 保持原结论 `BLOCKED / VENUE_RULE_FACTS_UNAVAILABLE`，本轮不修改原文件。该结论不是“所有 facts 均缺失”：当时已有 tick size、step/lot size、minimum quantity、instrument status、source、`syncedAt` 等子集；明确缺少 maximum quantity、maximum market size/amount、minimum notional、rule/schema version、`observedAt/freshUntil`、checksum 和同版本完整 snapshot。

## 3. Venue capability 与 runtime facts

| 判定 | 结果 | 证据与边界 |
|---|---|---|
| `VENUE_RULE_CAPABILITY_READY` | `YES` | V34 已提交；migration conformance 已接受；修复 exact-head CI 为 GREEN；`JdbcInstrumentCatalogRepository.findByExchangeAndSymbols` 提供 parameterized、1..3 symbols 的 bounded local read；`VenueRuleFreshnessEvaluator` 与 `VenueRuleChecksumCalculator` 分别提供 freshness/schema/source/completeness 与 checksum fail-closed；preview 不需要也不得触发 public sync。 |
| `RUNTIME_FACT_PRESENT` | `UNKNOWN` | 本轮未连接 runtime DB；代码能力不等于当前数据库已有 row。 |
| `RUNTIME_FACT_FRESH` | `UNKNOWN` | 未读取 runtime row；实际请求必须按调用方提供的 `evaluationTime` 做 fail-closed freshness 判断。 |

`OkxVenueRuleFactsReader` 与 `OkxVenueRuleFactsSyncService` 仅属于显式 operator-triggered public sync 路径。preview production code 禁止依赖或调用它们。

## 4. 固定 UNKNOWN / NOT_EVALUATED

- `MIN_NOTIONAL=UNKNOWN`：V34/current local facts 没有 venue minimum-notional 字段；禁止由 `minSz * tickSz` 推导，禁止硬编码 5/10 USDT。
- `FEE_RATE=UNKNOWN`、`FEE_AMOUNT=UNKNOWN`：不假设 maker/taker、VIP tier 或 LIMIT 必然 maker。
- `ACCOUNT_PERMISSION=UNKNOWN`：不读取 credential，不假设 API key/permission 有效。
- `BALANCE=NOT_EVALUATED`：不调用 balance endpoint，不给出 fee-inclusive balance 或 final cost。
- `RISK_PIPELINE=NOT_EVALUATED`：不调用含有 duplicate/rate-limit state mutation 的 `PreTradeRiskService`；不调用 paper risk 或 order write path。

这些 unknown 不阻止纯结构 preview，但永久阻止 venue acceptance guarantee、profitability、`READY_TO_SUBMIT`、`LIVE_READY` 和 `TRADING_AUTHORIZED`。

## 5. Risk reuse matrix

| Check | Pure/stateless? | Needs account state? | Needs position state? | Mutates state? | Writes DB/event/audit? | Safe for preview? | Decision |
|---|---:|---:|---:|---:|---:|---:|---|
| price/quantity precision | 是（使用 local tick/step 对齐函数） | 否 | 否 | 否 | 否 | 是 | 复用 venue facts，使用纯 BigDecimal alignment；不复用全局 scale-only `OrderPrecisionRule` |
| minimum quantity | 是 | 否 | 否 | 否 | 否 | 是 | 使用同一 local facts snapshot 的 `minQuantity` |
| minimum notional | 数学比较可纯，但 venue fact 缺失 | 否 | 否 | 否 | 否 | 否 | `MIN_NOTIONAL_UNKNOWN`；不得用 `MinNotionalRule` 的配置默认值冒充 venue fact |
| maximum amount | 是（facts 与计价单位可比较时） | 否 | 否 | 否 | 否 | 有条件 | 检查 max LIMIT quantity；max USD notional 仅在 quote=USDT 时比较，其他 quote 显式 unknown |
| symbol enablement | 读取 runtime config | 否 | 否 | 否 | 否 | 否 | `NOT_EVALUATED`；catalog `status=LIVE` 仅是 venue instrument state，不等同内部 symbol authorization |
| account enablement | 否 | 是 | 否 | 否 | 否 | 否 | `ACCOUNT_PERMISSION_UNKNOWN` / `NOT_EVALUATED` |
| kill switch | 读取共享可变状态 | 是 | 否 | 否（check 本身） | 否 | 否 | `NOT_EVALUATED`；不得把缺少 runtime account context 写成 PASS |
| duplicate request | 否 | 是 | 否 | 是（消费 in-memory idempotency window） | 否 | 否 | `NOT_EVALUATED` |
| rate limit | 否 | 是 | 否 | 是（消费 in-memory window） | 否 | 否 | `NOT_EVALUATED` |
| balance | 否 | 是 | 否 | 否 | 可能外部调用 | 否 | `BALANCE_NOT_EVALUATED` |
| position | 否 | 是 | 是 | 否 | 可能 DB/外部读取 | 否 | `NOT_EVALUATED` |
| daily loss | 否 | 是 | 是 | 否 | 可能 DB/ledger 读取 | 否 | `NOT_EVALUATED` |
| open orders | 否 | 是 | 是 | 否 | 可能 DB/外部读取 | 否 | `NOT_EVALUATED` |
| `PreTradeRiskService` | 否（registry 混合纯规则与有状态规则） | 是 | 可能 | 是（duplicate/rate limit） | 否 | 否 | 整体禁止调用；不得只依据其 `ALLOW` 输出 readiness |
| paper risk path | 否 | 是 | 是 | 可能 | 是，`JdbcPaperRiskCheckResultRepository.insert` 写 DB | 否 | 禁止调用 |
| order command/write chain | 否 | 是 | 可能 | 是 | 是，order/audit/event/venue write | 否 | 禁止依赖或调用 |

本轮不复制第二套风险规则。允许的校验只基于 existing instrument catalog snapshot 与纯函数；其余全部显式 `UNKNOWN` 或 `NOT_EVALUATED`。

## 6. Frozen preview contract

### Scope decision

- LIMIT：允许 `OKX + SPOT + BUY|SELL + LIMIT` 的 internal application preview。
- MARKET：`BLOCKED / ORDER_TYPE_NOT_SUPPORTED`。
- STOP/TRIGGER/ICEBERG/TWAP/POST_ONLY/IOC/FOK、margin/leverage/futures/swap/options：全部拒绝。
- side-effect boundary：production service 只依赖窄化的 instrument-catalog read port 与 pure freshness/checksum evaluator；不得依赖 `TradingAdapter`、sync/provider、credential、balance、order command/write、ledger、audit 或 event port。

### Input

`exchange`、`instrumentId`、`side`、`orderType`、`requestedQuantity`、`requestedLimitPrice`、`evaluationTime`、`traceId`。price/quantity 仅使用 `BigDecimal`；symbol 由 server-side catalog 解析。禁止 credential、arbitrary URL/endpoint、raw provider response、account balance。

### Output

必须分别给出 `structuralStatus`、`venueFactStatus`、`riskStatus`、`accountStatus`、`executionReadiness`，状态集为 `PASS / BLOCKED / UNKNOWN / NOT_EVALUATED`。固定字段：`diagnosticOnly=true`、`noSideEffect=true`、`orderSubmitted=false`、`executionReadiness=BLOCKED`。不得存在单一 `valid/approved/ready/canTrade` 结论。

分类集合固定为 `blockers`、`warnings`、`unknowns`、`notEvaluated`；unknown 永不降级为 PASS。

### Rounding

不静默修改输入。tick 不对齐返回 `INVALID_TICK_ALIGNMENT`；step 不对齐返回 `INVALID_STEP_ALIGNMENT`。本轮不提供会被误当作执行值的 normalized suggestion。禁止 `double` / `float`。

### Taxonomy

至少冻结以下 code：`INSTRUMENT_NOT_FOUND`、`INSTRUMENT_NOT_LIVE`、`VENUE_RULE_FACTS_MISSING`、`VENUE_RULE_FACTS_STALE`、`VENUE_RULE_SCHEMA_UNSUPPORTED`、`VENUE_RULE_CHECKSUM_INVALID`、`INVALID_PRICE`、`INVALID_QUANTITY`、`INVALID_TICK_ALIGNMENT`、`INVALID_STEP_ALIGNMENT`、`BELOW_MIN_QUANTITY`、`ABOVE_MAX_LIMIT_QUANTITY`、`ABOVE_MAX_LIMIT_NOTIONAL`、`MIN_NOTIONAL_UNKNOWN`、`FEE_UNKNOWN`、`ACCOUNT_PERMISSION_UNKNOWN`、`BALANCE_NOT_EVALUATED`、`RISK_PIPELINE_NOT_EVALUATED`、`ORDER_TYPE_NOT_SUPPORTED`、`EXECUTION_NOT_AUTHORIZED`。允许为 input/type/local-read/max-fact unknown 增加更精确、同样 fail-closed 的 code。

## 7. Module 与 forbidden scope

- 允许：`nq-core` preview model/taxonomy/pure validation/internal orchestration/tests；如物理证明只读依赖所必需，新增窄化 read port 并让 existing repository port 继承。
- 不需要：`nq-risk`、`nq-infra`、`nq-app` production 修改；Spring 对 existing repository implementation 的 read-port 注入由现有继承关系提供。
- 禁止：Controller/REST/OpenAPI/frontend/migration/第二张 facts 表/preview persistence/scheduler/runner/network/private OKX/credential/order adapter method/order state change/ledger/audit/event/LIVE/DH/AI。

## 8. Findings

### P0

- 无。

### P1

- 无。

### P2

- Runtime fact presence/freshness 未在本轮连接 DB 验证，保持 `UNKNOWN`；每次 preview 都必须 fail-closed。
- `max_limit_notional_usd` 与 `grossNotional` 只有在 quote asset 为 USDT 时可直接比较；非 USDT instrument 必须显式 unknown，不得隐式汇率换算。

### P3

- Existing repository port 同时暴露 read/write 方法；实现阶段通过窄化 read-only super-port 限制 preview 构造依赖，以便静态证明无写调用。

## 9. Final decision

```text
P0=0
P1=0

VENUE_RULE_CAPABILITY_READY=YES
RUNTIME_FACT_PRESENT=UNKNOWN
RUNTIME_FACT_FRESH=UNKNOWN

PASS /
LIMIT_ONLY_INTERNAL_PREVIEW_REVIEW_ACCEPTED
```

允许进入 PHASE C；该许可只覆盖合同内的 internal、local-facts-only、deterministic、read-only LIMIT preview，不构成下单许可、LIVE readiness 或 trading authorization。
