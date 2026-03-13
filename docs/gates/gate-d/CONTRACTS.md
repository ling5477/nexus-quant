# GateD CONTRACTS
# GateD 契约说明

## 1. 目标

GateD 契约文档用于冻结执行域的统一输入、输出与同步模型，避免 core、adapter、scheduler、ledger 各自定义一套近似但不相等的概念。

---

## 2. 核心写路径契约

### 2.1 SubmitOrderRequest
必备字段：
- `requestId`
- `traceId`
- `accountId`
- `venue`
- `symbol`
- `side`
- `orderType`
- `timeInForce`（若适用）
- `price`（限价必填）
- `quantity`
- `clientOrderId`
- `idempotencyKey`
- `source`（strategy / manual / recovery / reconcile 等）

### 2.2 SubmitOrderResponse
必备字段：
- `accepted`（是否被执行通道接受）
- `clientOrderId`
- `externalOrderId`（若已有）
- `localStatus`
- `externalStatus`
- `reasonCode`
- `reasonMessage`
- `traceId`

### 2.3 CancelOrderRequest
必备字段：
- `requestId`
- `traceId`
- `accountId`
- `venue`
- `symbol`
- `clientOrderId` 或 `externalOrderId`
- `reason`

### 2.4 CancelOrderResponse
必备字段：
- `accepted`
- `clientOrderId`
- `externalOrderId`
- `localStatus`
- `externalStatus`
- `reasonCode`
- `reasonMessage`
- `traceId`

### 2.5 AdapterOrderRequest
必备字段：
- `requestId`
- `traceId`
- `orderId`
- `accountId`
- `venue`
- `symbol`
- `clientOrderId`
- `idempotencyKey`
- `side`
- `orderType`
- `price`
- `quantity`
- `quoteQuantity`（若适用）
- `timeInForce`
- `source`

### 2.6 AdapterCancelRequest
必备字段：
- `requestId`
- `traceId`
- `orderId`
- `accountId`
- `venue`
- `symbol`
- `clientOrderId` 或 `externalOrderId`
- `reason`

---

## 3. 外部回执归一契约

### 3.1 AdapterOrderAck
必备字段：
- `venue`
- `accountId`
- `symbol`
- `clientOrderId`
- `externalOrderId`
- `externalStatus`
- `ackTs`
- `rawPayload`
- `traceId`

### 3.2 AdapterOrderReject
必备字段：
- `venue`
- `accountId`
- `symbol`
- `clientOrderId`
- `externalOrderId`（允许为空）
- `rejectCode`
- `rejectMessage`
- `rejectTs`
- `rawPayload`
- `traceId`

### 3.3 AdapterTradeReport
必备字段：
- `venue`
- `accountId`
- `symbol`
- `clientOrderId`
- `externalOrderId`
- `exchangeTradeId`
- `side`
- `price`
- `quantity`
- `fee`
- `feeAsset`
- `tradeTs`
- `rawPayload`
- `traceId`

---

## 4. 查询与同步契约

### 4.1 AdapterOrderSnapshot
必备字段：
- `venue`
- `accountId`
- `symbol`
- `clientOrderId`
- `externalOrderId`
- `externalStatus`
- `price`
- `origQty`
- `executedQty`
- `avgPrice`
- `updateTs`
- `rawPayload`
- `traceId`

### 4.2 AccountSnapshot
必备字段：
- `venue`
- `accountId`
- `asset`
- `balance`
- `available`
- `frozen`
- `snapshotTs`
- `traceId`

### 4.3 PositionSnapshot
现货阶段最小字段：
- `venue`
- `accountId`
- `symbol`
- `baseAsset`
- `quantity`
- `availableQuantity`
- `avgCost`（若可得）
- `snapshotTs`
- `traceId`

### 4.4 GateD 最小查询视图

#### OrderView
- `orderId`
- `accountId`
- `venue`
- `symbol`
- `clientOrderId`
- `externalOrderId`
- `price`
- `quantity`
- `status`
- `traceId`

#### TradeView
- `tradeId`
- `orderId`
- `accountId`
- `venue`
- `symbol`
- `externalOrderId`
- `exchangeTradeId`
- `price`
- `quantity`
- `fee`
- `feeCurrency`
- `tradeTs`
- `traceId`

#### PositionView
- `accountId`
- `venue`
- `symbol`
- `quantity`
- `availableQuantity`
- `avgPrice`
- `traceId`

#### AccountView
- `accountId`
- `venue`
- `balances[]`

#### AccountBalanceView
- `currency`
- `balance`
- `available`
- `frozen`
- `snapshotTs`
- `traceId`

### 4.5 GateD 本地验收入口
- canonical route：`/__gated`
- canonical verify switch：`nq.gated.verify.enabled`
- local fallback 说明：
  - `okx_adapter_bootstrap_fallback_enabled` 只代表 local 启动 / smoke fallback 生效
  - 不得表述为真实 OKX 验收通过

---

## 5. 风控契约

### 5.1 RiskCheckRequest
- `traceId`
- `requestId`
- `accountId`
- `venue`
- `symbol`
- `side`
- `orderType`
- `price`
- `quantity`
- `clientOrderId`
- `idempotencyKey`

### 5.2 RiskCheckResult
- `passed`
- `ruleCode`
- `ruleName`
- `rejectReason`
- `hardReject`
- `traceId`

---

## 6. 契约冻结原则

- core 只消费统一契约，不消费 venue 私有 DTO
- adapter 对 venue 原始字段的处理必须在 adapter 层完成
- 文档字段名与代码字段名必须一一对应，不能一个叫 `external_order_id` 一个叫 `exchangeOrderId` 还假装世界和平
- 本轮 canonical 字段名统一为 `orderType / quantity / quoteQuantity / timeInForce / source`
- 第四批后 canonical 验收入口统一为 `__gated + nq.gated.verify.enabled + gated-verify`
- 第九批后 `GateDOrderHttpRequest` 已只接受 canonical `orderType / quantity`；旧 `type / qty` JSON alias 已从 source 删除，不能再作为本地示例请求或测试输入
- 第十批后本地脚本、手工 smoke 文档与 curl 示例也必须统一使用 `__gated + nq.gated.verify.enabled + orderType / quantity`，不得再传播旧 `__gatec`、`NQ_GATEC_VERIFY_ENABLED`、`type / qty`
- `Adapter*` 旧构造器仍允许作为兼容层保留，但不能再作为新代码事实来源

