# Gate B CONTRACTS（命令/事件/Topic 契约）

> 本文件对齐 `nq-contracts` 的实现：
> - 统一 Envelope：`EventEnvelope<T>`
> - 统一 Topic：`TopicNames.*_V1`
>
> Gate B 原则：
> 1) Gate A 已冻结的 Envelope/Topic/基础 DTO 不破坏
> 2) Gate B 只允许新增消息类型（type）或新增 payload 字段（可选 + 默认值）
> 3) 幂等键 `client_order_id` 必须贯穿命令与事件
> 4) `trace_id` 必须贯穿全链路并可落库关联（orders/trades/ledger/audit/risk）

---

## 1. Envelope 规范（EventEnvelope<T>）

统一使用：

- `event_id`：事件唯一 ID（建议 UUID/雪花），用于去重与审计
- `type`：**具体消息类型**（例如 `PlaceOrderCommand`、`TradeExecuted`）
- `version`：消息版本号（从 1 开始；破坏性变更只能升 version）
- `ts`：事件产生时间（UTC）
- `source`：事件来源（如 `nq-gateway` / `nq-scheduler` / `nq-adapter-paper`）
- `trace_id`：链路追踪 ID（必填）
- `key`：分区键/聚合键（推荐使用 `client_order_id` 或 `order_id`）
- `payload`：负载对象（命令或事件 DTO）

> 约束：
> - Gate B 所有命令与事件必须装入 EventEnvelope。
> - trace_id 必填，且必须写入审计与关键业务表的可关联字段（直接存或可反查）。

---

## 2. Topic 规范（TopicNames）

Gate B 只使用以下 Topic 常量，不新增“散装字符串”：

- 命令：
    - `TopicNames.ORDER_COMMAND_V1` = `order.command.v1`
- 事件：
    - `TopicNames.ORDER_EVENT_V1`   = `order.event.v1`
    - `TopicNames.TRADE_EVENT_V1`   = `trade.event.v1`
    - `TopicNames.POSITION_EVENT_V1`= `position.event.v1`
    - `TopicNames.LEDGER_EVENT_V1`  = `ledger.event.v1`
    - `TopicNames.RISK_EVENT_V1`    = `risk.event.v1`
    - `TopicNames.AUDIT_EVENT_V1`   = `audit.event.v1`

> 路由规则：
> - 所有“下单/撤单/改单”等命令：发到 `ORDER_COMMAND_V1`
> - 订单生命周期事件：发到 `ORDER_EVENT_V1`
> - 成交事件：发到 `TRADE_EVENT_V1`
> - 仓位变化事件：发到 `POSITION_EVENT_V1`
> - 记账事件：发到 `LEDGER_EVENT_V1`
> - 风控事件：发到 `RISK_EVENT_V1`
> - 审计事件：发到 `AUDIT_EVENT_V1`

---

## 3. Gate B 消息类型（type）清单

### 3.1 Order Commands（TopicNames.ORDER_COMMAND_V1）

#### type = `PlaceOrderCommand`
payload（建议字段，Gate B 最小实现可先做 MARKET）：
- tenantId / accountId（至少一个）
- strategyId（可选）
- runId（可选，建议写入 strategy_runs 关联）
- clientOrderId（必填，幂等键）
- symbol（必填，如 `BTC/USDT`）
- side（必填：BUY/SELL）
- orderType（必填：MARKET/LIMIT）
- price（LIMIT 必填；MARKET 可为空）
- quantity（必填）
- timeInForce（可选：GTC/IOC/FOK）
- tags（可选 Map）

> key 建议：`clientOrderId`

#### type = `CancelOrderCommand`
payload：
- tenantId / accountId
- clientOrderId（推荐）或 orderId
- reason（可选）

> key 建议：`clientOrderId`（或 orderId）

---

### 3.2 Order Events（TopicNames.ORDER_EVENT_V1）

#### type = `OrderCreated`
- orderId
- clientOrderId
- symbol/side/orderType/price/quantity
- status（可选，或由消费者自己推导）

#### type = `RiskPassed` / `RiskRejected`
- orderId / clientOrderId
- decision（PASS/REJECT）
- ruleHits（可选：命中规则列表）
- reason（可选）

#### type = `OrderSubmitted` / `OrderAccepted` / `OrderRejected`
- orderId / clientOrderId
- adapter（PAPER/OKX/BINANCE）
- adapterOrderId（可选）
- reason（reject 可选）

#### type = `OrderPartiallyFilled` / `OrderFilled`
- orderId / clientOrderId
- filledQty / remainingQty（建议）
- avgPrice（可选）

#### type = `OrderCanceled`
- orderId / clientOrderId
- reason（可选）

> Gate B 最小闭环要求：至少实现 `OrderCreated`、`RiskPassed|RiskRejected`、`OrderSubmitted`、`OrderFilled`（或 PartiallyFilled + Filled）。

---

### 3.3 Trade Events（TopicNames.TRADE_EVENT_V1）

#### type = `TradeExecuted`
- tradeId
- orderId / clientOrderId
- symbol
- price
- quantity
- fee（Gate B 可先为 0，但字段建议保留）
- executedAt（可选；也可用 envelope.ts）

> key 建议：`orderId` 或 `clientOrderId`

---

### 3.4 Ledger Events（TopicNames.LEDGER_EVENT_V1）

#### type = `LedgerPosted`
- ledgerEventId
- orderId/tradeId（至少一个）
- result（POSTED）
- balanceCheck（PASS）

#### type = `LedgerPostFailed`
- ledgerEventId
- orderId/tradeId
- result（FAILED）
- reason（必填）
- balanceCheck（FAIL）

---

### 3.5 Position Events（TopicNames.POSITION_EVENT_V1）

#### type = `PositionUpdated`
- accountId
- symbol
- positionQty
- avgPrice（可选）
- reason（TRADE/ADJUST/INIT）

> Gate B 可选：如果 positions 作为 ledger 投影实现，建议发 PositionUpdated 便于下游订阅。

---

### 3.6 Risk Events（TopicNames.RISK_EVENT_V1）

#### type = `RiskEventRaised`
- category（IDEMPOTENCY/STATE_MACHINE/LEDGER/ADAPTER/DATA）
- severity（INFO/WARN/ERROR）
- message
- ref（orderId/tradeId/runId）

---

### 3.7 Audit Events（TopicNames.AUDIT_EVENT_V1）

#### type = `AuditRecorded`
- action（ORDER_CREATED/RISK_DECISION/TRADE_EXECUTED/LEDGER_POSTED/…）
- subjectType（ORDER/TRADE/LEDGER/RUN）
- subjectId
- outcome（SUCCESS/FAIL）
- detail（可选 JSON）

---

## 4. 兼容性规则（硬约束）

1) 允许新增 `type`（新消息类型），但必须：
    - 明确 Topic 归属（本文件登记）
    - 提供 payload 字段说明与默认值行为
2) 允许 payload 新增字段，但必须可选并保持旧消费者可运行
3) 破坏性变更只能通过 `version` 升级，并保留旧版本处理（或在新 Gate 中迁移）

---

## 5. 示例（对齐 EventEnvelope JSON key）

### 5.1 PlaceOrderCommand（MARKET）

```json
{
  "event_id": "evt_0001",
  "type": "PlaceOrderCommand",
  "version": 1,
  "ts": "2026-02-24T09:00:00Z",
  "source": "nq-scheduler",
  "trace_id": "trc_abc123",
  "key": "coid_0001",
  "payload": {
    "tenantId": "t1",
    "strategyId": "s1",
    "runId": "run_20260224_0001",
    "clientOrderId": "coid_0001",
    "symbol": "BTC/USDT",
    "side": "BUY",
    "orderType": "MARKET",
    "quantity": "0.01"
  }
}
```
### 5.2 TradeExecuted
```json
{
  "event_id": "evt_1001",
  "type": "TradeExecuted",
  "version": 1,
  "ts": "2026-02-24T09:00:02Z",
  "source": "nq-adapter-paper",
  "trace_id": "trc_abc123",
  "key": "coid_0001",
  "payload": {
    "tradeId": "tr_0001",
    "orderId": "ord_0001",
    "clientOrderId": "coid_0001",
    "symbol": "BTC/USDT",
    "price": "52000",
    "quantity": "0.01",
    "fee": "0"
  }
}
```

---

## 额外两点“架构一致性建议”（不强迫，但很值）
1) **`type` 字段建议做成常量集合**（比如 `EventTypes.PLACE_ORDER_COMMAND`），避免手写字符串飘来飘去。
2) **`key` 的统一规则**：命令与订单事件一律用 `clientOrderId`；成交/记账也尽量沿用，能把事件串得非常干净。
