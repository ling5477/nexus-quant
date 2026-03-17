# GateE CONTRACTS
# GateE 契约说明

## 1. 目标

GateE 契约文档用于冻结**策略定义、策略运行、调度触发、执行血缘**这四类语义，避免当前仓库里 `strategyId / strategyRunId / source` 半像半不像地到处飘。

---

## 2. 身份语义冻结

### 2.1 `strategyId`
表示**策略定义身份**，用于：
- 标识某一份策略配置 / 策略实例定义
- 启停、注册、查询、调度配置
- 不直接代表某一次运行

### 2.2 `strategyRunId`
表示**策略运行实例身份**，用于：
- 表示某一次被触发的运行
- 贯穿本次运行产生的订单、成交、审计与结果摘要
- 对应 `orders.strategy_run_id`

### 2.3 当前代码中的兼容口径
- `PlaceOrderRequest.strategyRunId`：按“运行实例身份”解释
- `AdapterOrderRequest.strategyRunId`：按“运行实例身份”解释
- `PlaceOrderCommand.strategyId`：当前代码里实际承载的是 `strategyRunId` 语义；GateE 必须在实现批次中完成字段命名或文档兼容收口

结论：
- GateE 不允许再把 `strategyId` 和 `strategyRunId` 当同一个东西使用。那不是灵活，是语义走丢。

---

## 3. GateE 最小写路径契约

### 3.1 StrategyDefinition（策略定义）
必备字段：
- `strategyId`
- `strategyType`
- `accountId`
- `venue`
- `status`（DRAFT / ACTIVE / PAUSED / DISABLED）
- `paramsJson`
- `scheduleConfigJson`（可空）
- `createdAt`
- `updatedAt`
- `traceId`

### 3.2 RegisterStrategyCommand
必备字段：
- `requestId`
- `strategyId`
- `strategyType`
- `accountId`
- `venue`
- `params`
- `scheduleConfig`
- `operator`
- `traceId`

### 3.3 TriggerStrategyRunCommand
必备字段：
- `requestId`
- `strategyId`
- `strategyRunId`
- `triggerSource`（manual / scheduler / recovery）
- `triggerAt`
- `windowStart`
- `windowEnd`
- `traceId`

### 3.4 StrategyRunResult
必备字段：
- `strategyRunId`
- `strategyId`
- `status`
- `startedAt`
- `endedAt`
- `orderCount`
- `successCount`
- `failedCount`
- `resultSummary`
- `traceId`

---

## 4. GateE 与执行闭环的连接契约

### 4.1 策略触发下单时必须携带
- `strategyRunId`
- `source=strategy`（或明确的 trigger source）
- `requestId`
- `traceId`
- `clientOrderId`
- `idempotencyKey`

### 4.2 执行闭环回传给策略运行层时必须能反查
- `strategyRunId`
- `orderId`
- `clientOrderId`
- `externalOrderId`
- `finalOrderStatus`
- `tradeCount`
- `ledgerPosted`
- `traceId`

---

## 5. GateE 最小读侧契约

### 5.1 StrategyDefinitionView
最小字段：
- `strategyId`
- `strategyType`
- `accountId`
- `venue`
- `status`
- `updatedAt`

### 5.2 StrategyRunView
最小字段：
- `strategyRunId`
- `strategyId`
- `triggerSource`
- `status`
- `startedAt`
- `endedAt`
- `traceId`

### 5.3 StrategyRunOrderView
最小字段：
- `strategyRunId`
- `orderId`
- `clientOrderId`
- `symbol`
- `side`
- `orderStatus`
- `externalOrderId`
- `traceId`

---

## 6. 非目标

当前 GateE 契约文档**不冻结**以下内容：
- 回测信号格式
- 研究因子接口
- 多策略组合编排 DSL
- 分布式任务队列协议

这些内容现在写上去会很像计划书，实际上更像空气。先别飘。
