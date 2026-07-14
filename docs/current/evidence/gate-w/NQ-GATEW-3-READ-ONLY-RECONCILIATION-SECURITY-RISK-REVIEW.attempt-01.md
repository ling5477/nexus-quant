# NQ-GATEW-3 Read-only Reconciliation Security/Risk Review — Attempt 01

## 结论

`PASS / READ_ONLY_RECONCILIATION_REVIEW_ACCEPTED`（通过 / 只读对账安全审查已接受）。

审查日期：`2026-07-14`。Reviewer：`Codex`。本结论只授权 bounded、typed、internal-only、side-effect-free implementation；不授权真实 credential、真实 OKX HTTP、private WebSocket、scheduler、repair、order/trade/ledger/audit/event 写入、LIVE 或交易。

## 官方来源与 permission hard gate

仅使用 OKX 官方来源重新核验：

- [OKX API v5 官方文档](https://www.okx.com/docs-v5/en/)：`API key creation`、`Get order List`、`Get order history (last 7 days)`、`Get transaction details (last 3 days)`。
- [OKX API v5 官方 changelog](https://www.okx.com/docs-v5/log_en/)：复核执行日可见的 order/fill 字段与 enum 变更；未知或新增状态必须 fail-closed。

OKX 官方权限分为 `Read`、`Trade`、`Withdraw`。本轮批准的三个 endpoint 均明确标注 `Permission: Read`；没有 endpoint 要求 `Trade` 或 `Withdraw`，HTTP method 均为 `GET`，因此 permission hard gate 通过。`ACCOUNT_PERMISSION=UNKNOWN`、`API_KEY_VALIDITY=UNKNOWN`、`IP_ALLOWLIST=UNKNOWN`、`REAL_SMOKE=NOT_RUN` 继续保留，本任务未读取真实 credential、未调用真实 endpoint。

## Official fact table

| Operation | Method / exact path | Private | Permission | SPOT | Query / pagination / window / record | Rate-limit dimension | Reconciliation fields / states | Error contract | Official section | Verified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| bounded open-order snapshot | `GET /api/v5/trade/orders-pending` | yes | `Read` | yes | `instType=SPOT`、exact `instId`、`after/before`、`limit`；官方最大/默认 100；仅 `live/partially_filled` | User ID；60 requests / 2s | `ordId/clOrdId/instId/side/ordType/px/sz/accFillSz/state/uTime`；`live/partially_filled` | `code=0` success；`50011` rate limit；`50102` timestamp expiry；其他 non-zero fail-closed | `Order Book Trading / Trade / Get order List` | `2026-07-14 / Codex` |
| bounded order-status/history snapshot | `GET /api/v5/trade/orders-history` | yes | `Read` | yes | `instType=SPOT`、exact `instId`、`after/before`、`begin/end`、`limit`；last 7 days；最大/默认 100；本合同进一步收窄到 24h/1 page | User ID；40 requests / 2s | 同上；terminal `canceled/filled/mmp_canceled`；未成交 canceled 仅保留 2h 是 completeness limitation | 同上；endpoint 未给出完整错误全集，未知 code 统一 fail-closed | `Order Book Trading / Trade / Get order history (last 7 days)` | `2026-07-14 / Codex` |
| bounded recent-fill snapshot | `GET /api/v5/trade/fills` | yes | `Read` | yes | `instType=SPOT`、exact `instId`、`ordId`、`after/before`、`begin/end`、`limit`；last 3 days；最大/默认 100；本合同进一步收窄到 24h/1 page | User ID；60 requests / 2s | `ordId/clOrdId/tradeId/instId/fillPx/fillSz/fillTime` | 同上；任何 malformed/unknown response fail-closed | `Order Book Trading / Trade / Get transaction details (last 3 days)` | `2026-07-14 / Codex` |

官方 changelog 会继续演进字段和 enum；实现不把 changelog 新值 fallback 为 `NEW/FILLED`。所有不在冻结 mapping 的 remote status 返回 `UNMAPPABLE_REMOTE_STATUS`。

## Approved / rejected operations

Approved：上述 `orders-pending`、`orders-history`、`fills` 三个固定 typed `GET` operation；host 固定在既有 GateW-2 global transport，query 由 server-side schema 构造。

Rejected：`GET /api/v5/trade/order`（本轮无需逐单扩展）、`fills-history`/archive history（窗口过宽）、private WebSocket、任意 URL/path/query/body、place/cancel/amend、transfer/withdraw，以及既有 `TradingAdapter` 混合读写接口。

## Existing-code side-effect matrix

| Component | Remote read | Local read | DB/order write | Ledger/event/audit write | Scheduler/background | Credential / network | Safe to reuse | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `OkxRestReconcileService` | yes | yes | yes：link/status/trade insert | yes：ledger/event/audit | invoked by scheduled flows | adapter credential/network | no | 写侧耦合，拒绝 |
| `OkxWsDegradeReconcileCoordinator` | yes | yes | indirect yes | yes | WS listener/background | yes | no | 自动触发与写侧，拒绝 |
| `OkxRecoveryService` | yes | yes | yes：link/transition/cancel | yes | `@EventListener` + `@Scheduled` | yes | no | recovery/write/background，拒绝 |
| `LedgerReconcileScheduler` | no | yes | no order write | audit write | `@Scheduled` | no remote | no | scheduler/audit side effect，拒绝 |
| `OrderRepository` / `JdbcOrderRepository` | no | yes | interface 同时含 insert/update | no | no | no | no | 不注入；新建窄 SELECT port |
| `OrderLifecycleService` | no | yes | status mutation | indirect event/audit paths | no | no | no | write service，拒绝 |
| `TradingAdapter` / existing `OkxExchangeAdapter` | yes | no | place/cancel capability | downstream side effects | no | yes | no | 混合写侧合同，拒绝 |
| GateW-2 guard/signer/scoped credential/GET transport | typed read | credential metadata read only | no | no | no default trigger | only explicit invocation | conditional | 只复用固定 guard、signer、scoped session 与 transport |

## Frozen contract

- `OKX / SPOT` only；account、exchange account、environment 与 1–3 个 server-side allowlisted symbols 显式绑定。
- 每个 symbol、每个 approved operation 最多 `1 page / 100 records`；时间窗 `>0 and <=24h`；无 retry loop、无全账户扫描。
- local snapshot 经新窄 read port：local ref、client/exchange order ID、symbol、side、type、BigDecimal price/original/filled quantity、status、updatedAt。
- remote snapshot 为 immutable normalized model；只保留对账字段、`observedAt`、source operation；raw JSON/header/signature/credential/provider metadata 不进入 domain/result。
- identity priority：`exchangeOrderId`，其次 `clientOrderId`；重复 ID fail-closed；禁止 symbol+price+quantity、近似时间、列表顺序匹配。
- stale threshold 固定 5 分钟；future timestamp、partial/full-page、scope overflow、permission unknown 均 fail-closed。
- status mapping：`live -> ACCEPTED`、`partially_filled -> PARTIALLY_FILLED`、`filled -> FILLED`、`canceled|mmp_canceled -> CANCELLED`。这是诊断映射，不宣称 provider/local 状态机完全等价。
- 数值使用 `BigDecimal.compareTo` canonical equality，无 float/double、无隐式 rounding。

## Taxonomy and result safety

冻结必需分类：`MATCHED / LOCAL_ONLY / REMOTE_ONLY / STATUS_MISMATCH / PRICE_MISMATCH / QUANTITY_MISMATCH / FILLED_QUANTITY_MISMATCH / DUPLICATE_LOCAL_ID / DUPLICATE_REMOTE_ID / UNMATCHED_IDENTITY / UNMAPPABLE_REMOTE_STATUS / STALE_LOCAL_SNAPSHOT / STALE_REMOTE_SNAPSHOT / PARTIAL_REMOTE_SNAPSHOT / REMOTE_PERMISSION_UNKNOWN / REMOTE_NOT_EVALUATED / EXECUTION_NOT_AUTHORIZED`；实现另补 `SYMBOL_MISMATCH / SIDE_MISMATCH / ORDER_TYPE_MISMATCH / CLIENT_ORDER_ID_MISMATCH / SNAPSHOT_SCOPE_MISMATCH`。

结果分离 `matches/differences/blockers/warnings/unknowns/notEvaluated`，并固定：`diagnosticOnly=true`、`readOnly=true`、`noSideEffect=true`、`repairPerformed=false`、`orderSubmitted=false`、`executionReadiness=BLOCKED`。全匹配时仅可输出 `SNAPSHOT_MATCHED_AT_EVALUATION_TIME`。

## Findings

- P0：0。
- P1：0。
- P2：1。root `README.md` 仍保留较早 GateW planning/GateW-1 摘要；它不覆盖 `docs/current/STATUS.md`，且不在本任务 current-doc allowlist，记录为后续入口文档漂移。
- P3：0。

## Limitations / unknowns

- 没有真实 permission、key validity、IP allowlist、rate-limit 或 provider freshness 证据；这正是本轮禁止的 real smoke 边界。
- order history 对未成交 canceled 记录的官方 retention 只有 2 小时；任何缺口只形成 partial/unknown，不能判定本地或远端为真。
- `fills` 是 3-day endpoint，但本合同只取 24h；超过窗口的累计成交依赖 order 的 `accFillSz`，不得用不完整 fill 列表自动 repair。

## Final decision

`PASS / READ_ONLY_RECONCILIATION_REVIEW_ACCEPTED`。
