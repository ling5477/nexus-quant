# GateI API Plan

本文件只规划 GateI API，不实现接口，不新增 controller，不新增业务代码。正式 API 仍以代码中的 controller 和后续 GateI 实现为准。

## 通用约定

- 认证：沿用 `POST /api/auth/login` 与 `Authorization: Bearer <token>`。
- 权限：默认要求已登录用户；发布、Paper run、异常停机需要更高权限。
- 分页：列表查询统一使用 `page`、`size`，响应返回 `items`、`page`、`size`、`total`。
- 幂等：写操作优先使用业务唯一键，必要时支持 `Idempotency-Key`。
- 错误码草案：`AUTH_REQUIRED`、`FORBIDDEN`、`INVALID_ARGUMENT`、`NOT_FOUND`、`CONFLICT`、`IDEMPOTENCY_CONFLICT`、`INVALID_STATE`、`RISK_REJECTED`、`EMERGENCY_STOP_ACTIVE`、`INTERNAL_ERROR`。

## Strategy Version API

### `GET /api/strategies/{strategyId}/versions`

- Method：`GET`
- Request 字段：`strategyId`、`status`、`page`、`size`
- Response 字段：`items[].versionId`、`strategyId`、`versionName`、`status`、`parameterSnapshotJson`、`sourceSnapshotJson`、`createdBy`、`createdAt`、`frozenAt`、`archivedAt`
- 权限要求：已登录；只能访问有权限的 strategy。
- 分页要求：需要。
- 幂等要求：不需要，读操作。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不生成 AI 策略，不修改策略核心算法。

### `POST /api/strategies/{strategyId}/versions`

- Method：`POST`
- Request 字段：`strategyId`、`versionName`、`description`、`parameterSnapshotJson`、`sourceSnapshotJson`、`idempotencyKey`
- Response 字段：`versionId`、`strategyId`、`versionName`、`status`、`createdAt`
- 权限要求：已登录且具备策略编辑权限。
- 分页要求：不需要。
- 幂等要求：需要，同 strategy、versionName、参数快照避免重复创建。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`INVALID_ARGUMENT`、`CONFLICT`、`IDEMPOTENCY_CONFLICT`、`INTERNAL_ERROR`
- 不做范围：不自动发布，不启动回测或 Paper run。

### `POST /api/strategy-versions/{versionId}/freeze`

- Method：`POST`
- Request 字段：`versionId`、`reason`、`idempotencyKey`
- Response 字段：`versionId`、`status`、`frozenAt`
- 权限要求：已登录且具备策略发布前冻结权限。
- 分页要求：不需要。
- 幂等要求：需要，重复冻结同一版本返回同一冻结状态。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_STATE`、`IDEMPOTENCY_CONFLICT`、`INTERNAL_ERROR`
- 不做范围：不执行发布，不执行实盘下单。

## Publish Version API

### `GET /api/publishes`

- Method：`GET`
- Request 字段：`strategyId`、`versionId`、`status`、`environment`、`page`、`size`
- Response 字段：`items[].publishVersionId`、`strategyId`、`versionId`、`evaluationReportId`、`status`、`environment`、`createdBy`、`createdAt`、`approvedAt`
- 权限要求：已登录。
- 分页要求：需要。
- 幂等要求：不需要。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不接 AI 发布，不直接进入 LIVE。

### `POST /api/publishes`

- Method：`POST`
- Request 字段：`strategyVersionId`、`evaluationReportId`、`targetEnvironment`、`approvalNote`、`idempotencyKey`
- Response 字段：`publishVersionId`、`strategyVersionId`、`status`、`targetEnvironment`、`createdAt`
- 权限要求：已登录且具备发布权限。
- 分页要求：不需要。
- 幂等要求：需要，同策略版本、评估报告和目标环境避免重复发布。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_STATE`、`CONFLICT`、`IDEMPOTENCY_CONFLICT`、`INTERNAL_ERROR`
- 不做范围：不允许跳过评估直接发布；不允许 AI 自动批准。

## Backtest Config Enhanced API

### `GET /api/backtest-configs`

- Method：`GET`
- Request 字段：`strategyVersionId`、`datasetId`、`status`、`page`、`size`
- Response 字段：`items[].configId`、`strategyVersionId`、`datasetId`、`parameterSnapshotJson`、`datasetSnapshotJson`、`status`、`createdAt`、`updatedAt`
- 权限要求：已登录。
- 分页要求：需要。
- 幂等要求：不需要。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不改回测核心算法。

### `PATCH /api/backtest-configs/{configId}/strategy-version`

- Method：`PATCH`
- Request 字段：`configId`、`strategyVersionId`、`parameterSnapshotJson`、`idempotencyKey`
- Response 字段：`configId`、`strategyVersionId`、`strategyVersionSnapshotJson`、`updatedAt`
- 权限要求：已登录且具备该 config 编辑权限。
- 分页要求：不需要。
- 幂等要求：需要，重复绑定同一版本返回同一结果。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_STATE`、`IDEMPOTENCY_CONFLICT`、`INTERNAL_ERROR`
- 不做范围：不启动回测，不生成 AI 参数。

## Evaluation Report API

### `GET /api/evaluations/reports`

- Method：`GET`
- Request 字段：`backtestRunId`、`strategyVersionId`、`datasetId`、`page`、`size`
- Response 字段：`items[].reportId`、`backtestRunId`、`strategyVersionId`、`datasetId`、`totalReturn`、`maxDrawdown`、`winRate`、`profitLossRatio`、`tradeCount`、`createdAt`
- 权限要求：已登录。
- 分页要求：需要。
- 幂等要求：不需要。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不生成投资建议，不接 AI 解读。

### `GET /api/evaluations/reports/{reportId}`

- Method：`GET`
- Request 字段：`reportId`
- Response 字段：`reportId`、`metricsJson`、`strategyVersionSnapshotJson`、`datasetSnapshotJson`、`backtestRunSnapshotJson`、`createdAt`
- 权限要求：已登录。
- 分页要求：不需要。
- 幂等要求：不需要。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INTERNAL_ERROR`
- 不做范围：不修改报告，不启动 Paper run。

## Paper Trading Run API

### `POST /api/paper-trading/runs`

- Method：`POST`
- Request 字段：`publishVersionId`、`accountId`、`environment`、`initialCapital`、`riskProfileId`、`idempotencyKey`
- Response 字段：`runId`、`publishVersionId`、`status`、`environment`、`createdAt`
- 权限要求：已登录且具备 Paper run 创建权限。
- 分页要求：不需要。
- 幂等要求：需要，避免重复创建相同发布版本和账户上下文的 run。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_STATE`、`CONFLICT`、`IDEMPOTENCY_CONFLICT`、`EMERGENCY_STOP_ACTIVE`、`INTERNAL_ERROR`
- 不做范围：不创建 LIVE 自动交易 run，不接 AI 信号。

### `POST /api/paper-trading/runs/{runId}/start`

- Method：`POST`
- Request 字段：`runId`、`idempotencyKey`
- Response 字段：`runId`、`status`、`startedAt`
- 权限要求：已登录且具备 run 操作权限。
- 分页要求：不需要。
- 幂等要求：需要，重复 start 返回当前运行状态。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_STATE`、`EMERGENCY_STOP_ACTIVE`、`INTERNAL_ERROR`
- 不做范围：不绕过风控，不切换 LIVE。

## Risk Result API

### `GET /api/risk/results`

- Method：`GET`
- Request 字段：`runId`、`orderId`、`status`、`riskRuleCode`、`page`、`size`
- Response 字段：`items[].riskResultId`、`runId`、`orderId`、`riskRuleCode`、`status`、`reason`、`checkedAt`
- 权限要求：已登录。
- 分页要求：需要。
- 幂等要求：不需要。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不修改风控规则，不绕过风控结果。

## Equity Curve API

### `GET /api/portfolio/equity-curve`

- Method：`GET`
- Request 字段：`runId`、`from`、`to`、`interval`、`page`、`size`
- Response 字段：`items[].snapshotTime`、`equity`、`cashBalance`、`positionValue`、`realizedPnl`、`unrealizedPnl`、`drawdown`
- 权限要求：已登录。
- 分页要求：需要；后续可补游标分页。
- 幂等要求：不需要。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不提供实时高频推送。

## Position Curve API

### `GET /api/portfolio/position-curve`

- Method：`GET`
- Request 字段：`runId`、`symbol`、`from`、`to`、`interval`、`page`、`size`
- Response 字段：`items[].snapshotTime`、`symbol`、`quantity`、`averagePrice`、`marketPrice`、`marketValue`、`unrealizedPnl`
- 权限要求：已登录。
- 分页要求：需要。
- 幂等要求：不需要。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_ARGUMENT`、`INTERNAL_ERROR`
- 不做范围：不接合约持仓，不接美股/A 股持仓。

## Trade Replay API

### `GET /api/replay/trades/{tradeId}`

- Method：`GET`
- Request 字段：`tradeId`
- Response 字段：`tradeId`、`runId`、`signalSnapshotJson`、`riskResultJson`、`orderSnapshotJson`、`tradeSnapshotJson`、`positionBeforeJson`、`positionAfterJson`、`equityBeforeJson`、`equityAfterJson`
- 权限要求：已登录。
- 分页要求：不需要。
- 幂等要求：不需要。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INTERNAL_ERROR`
- 不做范围：不生成 AI 复盘结论，不修改历史交易记录。

## Emergency Stop API

### `POST /api/emergency-stop/events`

- Method：`POST`
- Request 字段：`scopeType`、`scopeId`、`reasonCode`、`reasonDetail`、`idempotencyKey`
- Response 字段：`eventId`、`scopeType`、`scopeId`、`status`、`triggeredAt`
- 权限要求：已登录且具备 emergency stop 权限。
- 分页要求：不需要。
- 幂等要求：需要，同 scope active stop 避免重复触发。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`INVALID_ARGUMENT`、`CONFLICT`、`IDEMPOTENCY_CONFLICT`、`INTERNAL_ERROR`
- 不做范围：不直接操作 LIVE 交易所账户；GateI 第一版只作用于 SIM / Paper 主链。

### `POST /api/emergency-stop/events/{eventId}/resolve`

- Method：`POST`
- Request 字段：`eventId`、`resolutionNote`、`idempotencyKey`
- Response 字段：`eventId`、`status`、`resolvedAt`
- 权限要求：已登录且具备解除权限。
- 分页要求：不需要。
- 幂等要求：需要，重复解除返回同一结果。
- 错误码：`AUTH_REQUIRED`、`FORBIDDEN`、`NOT_FOUND`、`INVALID_STATE`、`IDEMPOTENCY_CONFLICT`、`INTERNAL_ERROR`
- 不做范围：不自动恢复 LIVE 交易。
