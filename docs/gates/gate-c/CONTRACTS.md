# docs/gates/gate-c/CONTRACTS.md
# Gate C CONTRACTS（CEX 接入契约：OKX REST/WS + Binance Spot）

> 对齐仓库事实（必须）：
> - EventEnvelope<T>：event_id / type / version / ts / source / trace_id / key / payload
> - TopicNames：order.command.v1 / order.event.v1 / trade.event.v1 / position.event.v1 / ledger.event.v1 / risk.event.v1 / audit.event.v1
> - event_store：payload_json(JSONB) 建议存 envelope 全量 JSON（JDBC 写入需 CAST(? AS jsonb)）
>
> GateC 原则：**只增不改**；新增字段必须向后兼容 GateB。

---

## 0. GateC-0 强制契约（先于 OKX 接入）

### 0.1 adapter-api 三分法（必须）
- TradingAdapter：placeOrder / cancelOrder / getOrder / listOpenOrders
- MarketDataAdapter：subscribeBars / subscribeTrades / subscribeOrderBook（GateC-1 可先 stub）
- AccountAdapter：getBalances / getPositions / getAccountSnapshot（GateC-1 可先 REST 拉取）

### 0.2 AdapterRouter（必须）
- route(accountId, venue) -> adapter
- core 只依赖 adapter-api，不允许直接依赖 okx/binance 实现类。

### 0.3 external_order_id（必须）
- placeOrder 成功回执必须带 external_order_id（OKX ordId / Binance orderId）
- 本地 orders 必须落库 external_order_id，用于 reconcile/WS 关联/恢复。

---

## 1. 内部统一语义（adapter-api 约定）

### 1.1 TradingAdapter.placeOrder（统一语义）
输入（内部）至少包含：
- accountId（对应 orders.account_id）
- venue（OKX/BINANCE/PAPER）
- clientOrderId（对应 orders.client_order_id，幂等键）
- symbol（内部建议直接采用 instId / symbol）
- side（BUY/SELL）
- type（MARKET/LIMIT）
- price（LIMIT 必填）
- qty
- traceId
- optional: strategyRunId

输出（内部 ack）：
- accepted（true/false）
- venue（OKX/BINANCE/PAPER）
- externalOrderId（OKX ordId / Binance orderId；失败可为空）
- rejectCode/rejectReason（失败时）
- traceId

### 1.2 TradingAdapter.cancelOrder（统一语义）
输入：
- accountId
- venue
- symbol
- externalOrderId 或 clientOrderId（二选一；建议优先 externalOrderId）
- traceId

输出：
- accepted（true/false）
- externalOrderId（如有）
- rejectCode/rejectReason
- traceId

### 1.3 TradingAdapter.getOrder / listOpenOrders（统一语义）
- getOrder：按 externalOrderId 或 clientOrderId 查询订单状态（用于 query-confirm）
- listOpenOrders：拉取未完成订单列表（恢复用）

---

## 2. GateC 事件契约（必须写入 event_store）

### 2.1 order.command.v1
#### PlaceOrderCommand（必须）
payload 最小字段：
- account_id
- venue
- client_order_id
- symbol
- side
- type
- qty
- price（limit 必填）
- ts
- optional: strategy_run_id

#### CancelOrderCommand（必须）
payload 最小字段：
- account_id
- venue
- client_order_id（或 external_order_id）
- symbol
- ts

### 2.2 order.event.v1
#### OrderAck（必须）
- account_id
- venue
- client_order_id
- external_order_id（必须）
- status（ACCEPTED）
- ts

#### OrderReject（必须）
- account_id
- venue
- client_order_id
- reject_code
- reject_reason
- ts

#### CancelAck（必须）
- account_id
- venue
- client_order_id（或 external_order_id）
- status（CANCELED）
- ts

#### CancelReject（必须）
- account_id
- venue
- client_order_id（或 external_order_id）
- reject_code
- reject_reason
- ts
- 内部状态语义：收到 CancelReject 后，订单从 `CANCEL_REQUESTED` 推进到 `CANCEL_REJECTED`；
  后续由 REST reconcile 按交易所事实继续对齐到 `ACCEPTED/PARTIALLY_FILLED/FILLED/CANCELLED/REJECTED`

### 2.3 trade.event.v1
#### TradeExecuted（必须）
- account_id
- venue
- symbol
- external_order_id
- exchange_trade_id（必须，用于去重）
- price
- qty
- fee
- fee_currency
- ts

### 2.4 ledger.event.v1 / position.event.v1
沿用现有 ledger/position 事件结构即可，但必须带：
- account_id
- symbol
- venue
- trace_id

---

## 3. 外部字段映射要点（OKX / Binance）

### 3.1 OKX（Spot）
- client_order_id -> clOrdId（OKX 明确支持 user-defined unique ID，并可用于查询/撤单）
- external_order_id -> ordId
- fills：写入 exchange_trade_id（OKX 成交明细返回的成交 ID 字段；以实际响应字段为准）

### 3.2 Binance（Spot）
- client_order_id -> clientOrderId（REST 新订单返回/查询中包含 clientOrderId）
- external_order_id -> orderId
- WS：订单推送事件语义以 executionReport 为准（同一事件里包含 clientOrderId、orderId、tradeId 等字段）

---

## 4. 参考依据（权威来源）
- GateC 中 OKX/Binance 的 endpoint、WS 事件语义依据统一收敛在：
  - `docs/gates/gate-c/SOURCES.md`