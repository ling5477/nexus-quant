# GateH API Plan

本文件只规划 GateH API，不实现接口，不新增 controller，不新增业务代码。正式 API 仍以代码中的 controller 和后续 GateH 实现为准。

## 通用约定

- 认证：沿用 `POST /api/auth/login` 与 `Authorization: Bearer <token>`。
- 权限：默认要求已登录用户；涉及 LIVE 环境、账户写操作、接入任务写操作时需要更高权限。
- 分页：列表查询统一使用 `page`、`size`，响应返回 `items`、`page`、`size`、`total`。
- 幂等：写操作使用业务唯一键或 `Idempotency-Key`，实际采用方式由 GateH 实现时确认。
- 错误码草案：`AUTH_REQUIRED`、`FORBIDDEN`、`ACCOUNT_CONTEXT_REQUIRED`、`INVALID_ARGUMENT`、`NOT_FOUND`、`CONFLICT`、`IDEMPOTENCY_CONFLICT`、`EXCHANGE_UNAVAILABLE`、`RATE_LIMITED`、`DATA_GAP_DETECTED`、`INTERNAL_ERROR`。

## Trading Workspace API

### `GET /api/trading/orders`

- Method：`GET`
- Request 字段：`accountId`、`exchangeCode`、`marketType`、`symbol`、`environment`、`status`、`from`、`to`、`page`、`size`
- Response 字段：`items[].orderId`、`clientOrderId`、`accountId`、`exchangeCode`、`marketType`、`symbol`、`side`、`type`、`price`、`quantity`、`filledQuantity`、`status`、`environment`、`createdAt`、`updatedAt`、`page`、`size`、`total`
- 权限要求：已登录；只能查询当前用户可访问账户；LIVE 数据需要账户授权。
- 是否需要分页：需要。
- 是否需要幂等：不需要，读操作。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`ACCOUNT_CONTEXT_REQUIRED`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不新增下单 API，不新增策略自动下单，不提供 AI 下单入口。

### `GET /api/trading/orders/{orderId}`

- Method：`GET`
- Request 字段：`orderId` path 参数，`accountId` 可选校验参数
- Response 字段：`orderId`、`clientOrderId`、`accountId`、`exchangeCode`、`marketType`、`symbol`、`side`、`type`、`price`、`quantity`、`filledQuantity`、`averagePrice`、`status`、`environment`、`riskSummary`、`fills[]`、`createdAt`、`updatedAt`
- 权限要求：已登录；只能查看有权限账户的订单。
- 是否需要分页：不需要。
- 是否需要幂等：不需要，读操作。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`ACCOUNT_CONTEXT_REQUIRED`、`INTERNAL_ERROR`
- 不做范围：不在详情 API 中触发撤单、补单或状态修复。

## Instrument API

### `GET /api/instruments`

- Method：`GET`
- Request 字段：`exchangeCode`、`marketType`、`symbol`、`baseAsset`、`quoteAsset`、`status`、`page`、`size`
- Response 字段：`items[].instrumentId`、`exchangeCode`、`marketType`、`symbol`、`baseAsset`、`quoteAsset`、`pricePrecision`、`quantityPrecision`、`minOrderQuantity`、`status`、`source`、`updatedAt`、`page`、`size`、`total`
- 权限要求：已登录。
- 是否需要分页：需要。
- 是否需要幂等：不需要，读操作。
- 错误码：`AUTH_REQUIRED`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不接入合约全量 instrument，不做美股/A 股 symbol 适配。

### `POST /api/instruments/sync`

- Method：`POST`
- Request 字段：`exchangeCode`、`marketType`、`symbols[]`、`dryRun`、`idempotencyKey`
- Response 字段：`jobId`、`exchangeCode`、`marketType`、`requestedSymbols[]`、`status`、`createdAt`
- 权限要求：已登录且具备运维或管理员权限。
- 是否需要分页：不需要。
- 是否需要幂等：需要，按 `idempotencyKey` 或相同同步参数防重复提交。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`INVALID_ARGUMENT`、`CONFLICT`、`EXCHANGE_UNAVAILABLE`、`RATE_LIMITED`、`INTERNAL_ERROR`
- 不做范围：不做交易所全市场自动接入，不新增实盘交易权限。

## Marketdata Bar API

### `GET /api/marketdata/bars`

- Method：`GET`
- Request 字段：`exchangeCode`、`marketType`、`symbol`、`interval`、`from`、`to`、`qualityStatus`、`page`、`size`
- Response 字段：`items[].exchangeCode`、`marketType`、`symbol`、`interval`、`openTime`、`closeTime`、`openPrice`、`highPrice`、`lowPrice`、`closePrice`、`volume`、`quoteVolume`、`tradeCount`、`source`、`qualityStatus`、`ingestedAt`、`page`、`size`、`total`
- 权限要求：已登录。
- 是否需要分页：需要；后续可补游标分页。
- 是否需要幂等：不需要，读操作。
- 错误码：`AUTH_REQUIRED`、`INVALID_ARGUMENT`、`NOT_FOUND`、`DATA_GAP_DETECTED`、`INTERNAL_ERROR`
- 不做范围：不返回完整原始 payload；不提供高频 tick 数据；不支持合约全量数据。

## Marketdata Ingestion Job API

### `POST /api/marketdata/ingestion-jobs`

- Method：`POST`
- Request 字段：`exchangeCode`、`marketType`、`symbols[]`、`intervals[]`、`from`、`to`、`mode`、`schedule`、`enabled`、`idempotencyKey`
- Response 字段：`jobId`、`exchangeCode`、`marketType`、`symbols[]`、`intervals[]`、`from`、`to`、`mode`、`schedule`、`enabled`、`status`、`createdAt`
- 权限要求：已登录且具备运维或管理员权限。
- 是否需要分页：不需要。
- 是否需要幂等：需要，避免重复创建相同接入任务。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`INVALID_ARGUMENT`、`CONFLICT`、`IDEMPOTENCY_CONFLICT`、`INTERNAL_ERROR`
- 不做范围：不执行实时行情推送，不接入链上数据，不做复杂任务编排平台。

### `GET /api/marketdata/ingestion-jobs`

- Method：`GET`
- Request 字段：`exchangeCode`、`marketType`、`symbol`、`interval`、`status`、`enabled`、`page`、`size`
- Response 字段：`items[].jobId`、`exchangeCode`、`marketType`、`symbols[]`、`intervals[]`、`status`、`enabled`、`lastRunAt`、`nextRunAt`、`createdAt`、`updatedAt`、`page`、`size`、`total`
- 权限要求：已登录。
- 是否需要分页：需要。
- 是否需要幂等：不需要，读操作。
- 错误码：`AUTH_REQUIRED`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不展示敏感交易所凭证。

### `GET /api/marketdata/ingestion-jobs/{jobId}`

- Method：`GET`
- Request 字段：`jobId` path 参数
- Response 字段：`jobId`、`exchangeCode`、`marketType`、`symbols[]`、`intervals[]`、`from`、`to`、`mode`、`schedule`、`enabled`、`status`、`lastRun`、`recentRuns[]`、`createdAt`、`updatedAt`
- 权限要求：已登录。
- 是否需要分页：详情内 `recentRuns` 可固定最近 N 条；完整 runs 后续另设分页 API。
- 是否需要幂等：不需要，读操作。
- 错误码：`AUTH_REQUIRED`、`NOT_FOUND`、`INTERNAL_ERROR`
- 不做范围：不在详情 API 中修改任务配置。

### `POST /api/marketdata/ingestion-jobs/{jobId}/run-once`

- Method：`POST`
- Request 字段：`jobId` path 参数，`from`、`to`、`symbols[]`、`intervals[]`、`idempotencyKey`
- Response 字段：`runId`、`jobId`、`status`、`requestedRange`、`createdAt`
- 权限要求：已登录且具备运维或管理员权限。
- 是否需要分页：不需要。
- 是否需要幂等：需要，避免重复触发同一时间范围 run。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_ARGUMENT`、`CONFLICT`、`EXCHANGE_UNAVAILABLE`、`RATE_LIMITED`、`INTERNAL_ERROR`
- 不做范围：不支持长时间同步阻塞等待完成；不做高频实时任务。

## Marketdata Dataset API

### `GET /api/marketdata/datasets`

- Method：`GET`
- Request 字段：`exchangeCode`、`marketType`、`symbol`、`interval`、`qualityStatus`、`page`、`size`
- Response 字段：`items[].datasetId`、`name`、`exchangeCode`、`marketType`、`symbols[]`、`intervals[]`、`from`、`to`、`qualityStatus`、`barCount`、`source`、`createdAt`、`updatedAt`、`page`、`size`、`total`
- 权限要求：已登录。
- 是否需要分页：需要。
- 是否需要幂等：不需要，读操作。
- 错误码：`AUTH_REQUIRED`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不做复杂因子数据集，不做多资产组合优化数据集。

### `POST /api/marketdata/datasets`

- Method：`POST`
- Request 字段：`name`、`description`、`exchangeCode`、`marketType`、`symbols[]`、`intervals[]`、`from`、`to`、`qualityPolicy`、`idempotencyKey`
- Response 字段：`datasetId`、`name`、`exchangeCode`、`marketType`、`symbols[]`、`intervals[]`、`from`、`to`、`qualityStatus`、`createdAt`
- 权限要求：已登录且具备研究或管理员权限。
- 是否需要分页：不需要。
- 是否需要幂等：需要，同名同范围数据集避免重复创建。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`INVALID_ARGUMENT`、`CONFLICT`、`DATA_GAP_DETECTED`、`INTERNAL_ERROR`
- 不做范围：不生成交易信号，不启动回测。

## Backtest Dataset Binding API

### `PATCH /api/backtest-configs/{configId}/dataset`

- Method：`PATCH`
- Request 字段：`configId` path 参数，`datasetId`、`idempotencyKey`
- Response 字段：`configId`、`datasetId`、`datasetSummary`、`updatedAt`
- 权限要求：已登录且具备该 backtest config 的访问权限。
- 是否需要分页：不需要。
- 是否需要幂等：需要，重复绑定同一 dataset 返回同一结果。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_ARGUMENT`、`CONFLICT`、`IDEMPOTENCY_CONFLICT`、`INTERNAL_ERROR`
- 不做范围：不启动回测执行，不修改策略核心逻辑，不接入 AI 信号。
