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

