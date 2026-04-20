# GateG 前端联调契约口径

> 历史卷宗说明：本文件属于 GateG completed/frozen 历史档案，只读参考，不代表当前实现入口。当前事实以 docs/current/* 与最新源码为准.


## 1. 文档定位

本文件描述 **GateG 当前前端联调口径**，重点覆盖：

- 已落地认证与错误处理约定
- 已落地 `strategies / schedules / runs` 真实列表联调口径
- 后续仍待补齐的详情与动作边界

---

## 2. 认证契约

### 2.1 `POST /api/auth/login`

请求体：

```json
{
  "username": "admin",
  "password": "ChangeMe123!"
}
```

成功响应字段：

```json
{
  "accessToken": "<jwt-token>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "expiresAt": "2026-03-27T10:00:00Z",
  "username": "admin",
  "roles": ["ADMIN", "OPERATOR", "VIEWER"]
}
```

前端处理约定：

- 登录成功后写入 token 与最小用户信息
- Axios request interceptor 自动追加 `Authorization: Bearer <token>`
- 登录成功后进入受保护路由

### 2.2 `GET /api/auth/me`

请求头：

```http
Authorization: Bearer <token>
```

成功响应字段：

```json
{
  "username": "admin",
  "roles": ["ADMIN", "OPERATOR", "VIEWER"],
  "authenticated": true
}
```

前端处理约定：

- 应用启动后自动调用 `/api/auth/me`
- 用于刷新恢复当前用户与路由守卫判定
- `401` 时清空本地登录态并回到 `/login`

---

## 3. Bearer token 与错误处理约定

### 3.1 Bearer token

- 统一使用 `Authorization: Bearer <token>`
- 当前前端路由均走正式 `/api/**`
- 不引入第二套认证协议

### 3.2 错误模型

后端错误模型统一为 `ApiErrorResponse`，前端依赖以下字段：

- `status`
- `code`
- `message`
- `path`
- `traceId`
- `fieldErrors`

### 3.3 当前前端处理口径

- `401`：清空 token，跳回 `/login`，保留 redirect 参数
- `403`：统一提示“权限不足”，不清空 token
- `500`：统一提示“服务异常”，保留 `traceId`

---

## 4. GateG-3 已落地接口口径

当前已经不是“下一步规划”，而是已经接入三页真实列表接口。

### 4.1 策略页 `/strategies`

已接入接口：

- `GET /api/strategies`

当前查询字段：

- `strategyCode`
- `strategyType`
- `exchangeCode`
- `tradeEnv`
- `enabled`

当前列表字段：

- `strategyCode`
- `strategyName`
- `strategyId`
- `strategyType`
- `exchangeCode`
- `accountId`
- `tradeEnv`
- `status`
- `enabled`
- `updatedAt`

当前说明：

- 后端列表接口无必填查询参数
- 前端先拉全量列表，再按真实响应字段做本地筛选

### 4.2 调度页 `/schedules`

已接入接口：

- `GET /api/strategy-schedules?strategyId=...`

当前查询字段：

- `strategyId`（必填，真实请求参数）
- `scheduleType`
- `status`
- `enabled`

当前列表字段：

- `scheduleJobId`
- `strategyId`
- `scheduleType`
- `cronExpr`
- `timezone`
- `status`
- `enabled`
- `exchangeCode`
- `accountId`
- `lastTriggeredAt`
- `updatedAt`

当前说明：

- 后端当前列表查询必须提供 `strategyId`
- 前端已按该约束校验表单并发真实请求
- 其余字段先作为本地筛选字段使用

### 4.3 运行页 `/runs`

已接入接口：

- `GET /api/strategy-runs?strategyId=...`
- `GET /api/strategy-runs?scheduleId=...`

当前查询字段：

- `strategyId`
- `scheduleId`
- `status`
- `triggerType`

当前列表字段：

- `strategyRunId`
- `strategyId`
- `scheduleJobId`
- `requestId`
- `triggerType`
- `status`
- `exchangeCode`
- `accountId`
- `startedAt`
- `finishedAt`
- `errorMessage`

当前说明：

- 后端要求 `strategyId` 与 `scheduleId` 必须二选一
- 前端已按该约束校验查询区
- `status / triggerType` 作为当前本地筛选字段

---

## 5. 当前仍未完成的接口边界

本批未做，但已明确保留到后续子批或阶段：

### 5.1 `strategies`

- `GET /api/strategies/{strategyCode}`
- `POST /api/strategies`
- `POST /api/strategies/{strategyCode}/trigger`

### 5.2 `schedules`

- `GET /api/strategy-schedules/{scheduleId}`
- `POST /api/strategy-schedules`
- `POST /api/strategy-schedules/scan-once`

### 5.3 `runs`

- `GET /api/strategy-runs/{runId}`

这些接口不是不存在，而是当前前端还未进入详情页与动作联调。

---

## 6. GateG-3B 已落地接口口径

### 6.1 研究页 `/research`

已接入接口：

- `GET /api/research-configs`
- `GET /api/research-configs?sourceStrategyId=...`

当前查询字段：

- `sourceStrategyId`（真实请求参数，可空）
- `researchConfigId`
- `name`

当前列表字段：

- `researchConfigId`
- `sourceStrategyId`
- `name`
- `description`
- `createdAt`
- `updatedAt`

### 6.2 回测页 `/backtests`

已接入接口：

- `GET /api/backtest-configs`
- `GET /api/backtest-configs?researchConfigId=...`

当前查询字段：

- `researchConfigId`（真实请求参数，可空）
- `backtestConfigId`
- `name`

当前列表字段：

- `backtestConfigId`
- `researchConfigId`
- `name`
- `description`
- `startTime`
- `endTime`
- `initialCapital`
- `createdAt`
- `updatedAt`

### 6.3 评估页 `/evaluations`

已接入接口：

- `GET /api/backtest-runs`
- `GET /api/backtest-runs?researchConfigId=...`
- `GET /api/backtest-runs?backtestConfigId=...`

当前查询字段：

- `researchConfigId`（真实请求参数，可空）
- `backtestConfigId`（真实请求参数，可空）
- `sourceStrategyId`
- `evaluationStatus`

当前列表字段：

- `backtestRunId`
- `researchConfigId`
- `backtestConfigId`
- `sourceStrategyId`
- `status`
- `evaluationStatus`
- `evaluatedAt`
- `totalReturnRate`
- `maxDrawdownRate`
- `winRate`
- `sharpeRatio`
- `finishedAt`

当前说明：

- 评估页当前优先复用 `BacktestRunResponse` 中已带出的 evaluation summary 字段
- 不强造不存在的 evaluation list endpoint

### 6.4 发布页 `/publishes`

已接入接口：

- `GET /api/backtest-runs`
- `GET /api/backtest-runs?researchConfigId=...`
- `GET /api/backtest-runs?backtestConfigId=...`

当前查询字段：

- `researchConfigId`（真实请求参数，可空）
- `backtestConfigId`（真实请求参数，可空）
- `sourceStrategyId`
- `publishStatus`

当前列表字段：

- `backtestRunId`
- `researchConfigId`
- `backtestConfigId`
- `sourceStrategyId`
- `status`
- `publishStatus`
- `publishedAt`
- `publishName`
- `targetStrategyDefinitionId`
- `failureCode`
- `failureMessage`

当前说明：

- 发布页当前优先复用 `BacktestRunResponse` 中已带出的 publish summary 字段
- 不强造不存在的 publish list endpoint

---

## 7. GateG-4A 已落地接口口径

### 7.1 策略页详情与动作

详情接口：

- `GET /api/strategies/{strategyCode}`

动作接口：

- `PATCH /api/strategies/{strategyCode}/status`

当前详情字段：

- `strategyId`
- `strategyCode`
- `strategyName`
- `strategyType`
- `exchangeCode`
- `accountId`
- `tradeEnv`
- `status`
- `enabled`
- `version`
- `createdAt`
- `updatedAt`
- `configSnapshot`

当前动作：

- 启用策略
- 停用策略
- 刷新详情

### 7.2 调度页详情与动作

详情接口：

- `GET /api/strategy-schedules/{scheduleId}`

动作接口：

- `PATCH /api/strategy-schedules/{scheduleId}/status`

当前详情字段：

- `scheduleJobId`
- `strategyId`
- `scheduleType`
- `cronExpr`
- `timezone`
- `status`
- `enabled`
- `windowConfig`
- `dedupScope`
- `exchangeCode`
- `accountId`
- `tradeEnv`
- `lastTriggeredAt`
- `createdAt`
- `updatedAt`

当前动作：

- 启用调度
- 停用调度
- 刷新详情

### 7.3 运行页详情与动作

详情接口：

- `GET /api/strategy-runs/{runId}`

当前详情字段：

- `strategyRunId`
- `strategyId`
- `scheduleJobId`
- `requestId`
- `triggerType`
- `status`
- `exchangeCode`
- `accountId`
- `tradeEnv`
- `startedAt`
- `finishedAt`
- `errorMessage`
- `orders`
- `trades`
- `ledgerSummary`
- `riskSummary`
- `eventSummary`

当前动作区口径：

- 当前后端契约下无独立 run 写动作 API
- 前端按不可操作状态展示动作区
- 允许刷新详情，但不虚构写动作

---

## 8. GateG-4B 已落地接口口径

### 8.1 研究页详情与动作

详情接口：

- `GET /api/research-configs/{configId}`

动作接口：

- `POST /api/research-configs`

当前详情字段：

- `researchConfigId`
- `sourceStrategyId`
- `name`
- `description`
- `parameterSchema`
- `parameterDefaults`
- `datasetSpec`
- `createdAt`
- `updatedAt`

当前动作：

- 新建研究配置
- 刷新详情

当前说明：

- 当前无基于单条研究配置的写动作
- 前端明确将 create 放在页面动作区，不把 detail 抽屉误导成编辑页

### 8.2 回测页详情与动作

详情接口：

- `GET /api/backtest-configs/{configId}`

动作接口：

- `POST /api/backtest-configs`

当前详情字段：

- `backtestConfigId`
- `researchConfigId`
- `name`
- `description`
- `startTime`
- `endTime`
- `initialCapital`
- `executionSpec`
- `evaluationSpec`
- `configSnapshot`
- `createdAt`
- `updatedAt`

当前动作：

- 新建回测配置
- 刷新详情

当前说明：

- 当前无基于单条回测配置的写动作
- 前端明确将 create 放在页面动作区，不把 detail 抽屉误导成编辑页

### 8.3 评估页详情与动作

详情接口：

- `GET /api/backtest-runs/{runId}/evaluation`

动作接口：

- `POST /api/backtest-runs/{runId}/evaluate`

当前详情字段：

- `evalReportId`
- `backtestRunId`
- `evaluationStatus`
- `evaluatedAt`
- `initialCapital`
- `finalCashBalance`
- `finalPositionMarketValue`
- `finalEquity`
- `realizedPnl`
- `unrealizedPnl`
- `netPnl`
- `totalReturnRate`
- `totalFee`
- `totalSlippage`
- `orderCount`
- `tradeCount`
- `winningTradeCount`
- `losingTradeCount`
- `flatTradeCount`
- `winRate`
- `maxDrawdown`
- `maxDrawdownRate`
- `sharpeRatio`
- `reportJson`
- `failureCode`
- `failureMessage`

当前动作：

- 执行评估
- 刷新详情

### 8.4 发布页详情与动作

详情接口：

- `GET /api/backtest-runs/{runId}/publish`

动作接口：

- `POST /api/backtest-runs/{runId}/publish`

当前详情字段：

- `publishRecordId`
- `backtestRunId`
- `researchConfigId`
- `backtestConfigId`
- `sourceStrategyId`
- `targetStrategyDefinitionId`
- `publishStatus`
- `publishName`
- `publishedAt`
- `evaluationSummaryJson`
- `failureCode`
- `failureMessage`
- `publishSnapshotJson`

当前动作：

- 执行发布
- 刷新详情

当前说明：

- `displayName` 作为可选输入
- 留空时沿用后端默认命名

---

## 9. GateG-4C 已落地接口口径

### 9.1 trade-validation 页主查询口径

当前主查询链路基于订单主键展开，不强造不存在的列表接口。

主表查询接口：

- `GET /api/trading/orders/{orderId}`

辅助详情接口：

- `GET /api/trading/orders/{orderId}/trade`
- `GET /api/trading/accounts/{accountId}`
- `GET /api/trading/positions/{accountId}/{symbol}`

当前查询字段：

- `orderId`（真实主查询参数，必填）
- `accountId`（可空，用于账户 / 持仓辅助查询）
- `symbol`（可空，与 `accountId` 组合查询持仓）

当前列表字段：

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

当前详情字段：

- 订单详情：`orderId / accountId / venue / symbol / clientOrderId / externalOrderId / price / quantity / status / traceId`
- 最新成交：`tradeId / exchangeTradeId / price / quantity / fee / feeCurrency / tradeTs / traceId`
- 账户快照：`accountId / venue / balances / traceId`
- 持仓快照：`accountId / venue / symbol / quantity / availableQuantity / avgPrice / traceId`

### 9.2 trade-validation 动作口径

动作接口：

- `POST /api/trading/orders`
- `POST /api/trading/orders/cancel`
- `POST /api/trading/reconciliation/run-once`
- `POST /api/trading/recovery/run-once`

当前动作：

- 下单
- 撤单
- 执行对账
- 执行恢复

当前动作反馈字段：

- `action`
- `traceId`
- `detail`

当前说明：

- 页面使用最近一次 `OperationTriggerResponse` 作为统一动作反馈区
- 主表仍以订单查询结果为准，不把动作结果伪装成新的列表接口
