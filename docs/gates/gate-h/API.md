# Current API

当前 API 文档以代码实际 controller 为准。本文记录当前 API 分类和已完成 GateH API 事实；GateI-PLAN 只新增规划入口，不实现接口。

## API 分类

- Auth API：登录、当前用户、token 相关接口。
- Account API：用户账户、交易账户、默认账户上下文、凭证写侧接口。
- Trading API：订单、成交、持仓、交易工作台相关接口。
- Strategy API：策略配置、策略查询与策略运行前置数据。
- Schedule API：调度配置与调度状态。
- Run API：运行记录、执行状态、运行详情。
- Research API：研究配置与研究任务。
- Backtest API：回测配置、回测执行、回测结果。
- Evaluation API：评估任务、评估结果。
- Publish API：发布候选、发布状态。
- Instrument API：交易标的、交易所、市场类型、symbol catalog。
- Marketdata API：行情基础 ingest/query 能力。
- Actuator / Health：Spring Boot actuator、健康检查。

## 当前边界

- 正式 HTTP API 统一使用 `/api/**`。
- 旧 `/__gated/**` 只允许出现在历史文档说明中。
- AI 自动交易 API 当前不存在，也不允许在本次任务新增。
- GateH-1 只收口 Trading Workspace，不新增行情接入、dataset 绑定或 AI 自动交易接口。
- GateH-2 只新增 OKX / Binance SPOT 历史 OHLCV K 线接入、接入任务与运行记录 API；不新增 dataset/backtest 绑定接口，不新增 AI 接口。
- GateH-3 新增 marketdata dataset、quality refresh、backtest config dataset binding 与 backtest run dataset snapshot API；不新增 AI 接口。
- GateI-1 新增策略版本与发布版本绑定 API；不接 AI。
- GateI-2 增强 backtest config、backtest run 和 evaluation report 追溯 API；不进入 GateI-3/4，不接 AI。

## GateH-1 Trading Workspace API

当前已实现的 GateH-1 交易工作台读写入口：

- `GET /api/trading/orders`：按正式 `exchangeAccountId` 账户上下文查询订单列表，支持 `orderId`、`symbol`、`status`、`environment`、分页筛选。
- `GET /api/trading/orders/{orderId}`：查询单笔订单详情。
- `GET /api/trading/orders/{orderId}/trade`：查询订单最近一笔成交事实。
- `GET /api/trading/accounts/{accountId}`：查询账户余额快照；`accountId` 仍由后端兼容映射到 legacy trading account。
- `GET /api/trading/positions/{accountId}/{symbol}`：查询账户和交易对维度持仓快照。
- `POST /api/trading/orders`：触发既有下单编排，仍走服务端风控与状态机。
- `POST /api/trading/orders/cancel`：触发既有撤单编排。
- `POST /api/trading/reconciliation/run-once`：触发既有对账维护动作。
- `POST /api/trading/recovery/run-once`：触发既有恢复维护动作。

GateH-1 不新增历史行情抓取、marketdata ingestion、dataset 绑定、AI 下单或策略自动交易接口。

## GateH-2 Marketdata Ingestion API

当前已实现的 GateH-2 行情接入入口：

- `GET /api/marketdata/bars`：按 `exchangeCode`、`marketType`、`symbol`、`interval`、`startTime`、`endTime`、`page`、`size` 查询 `marketdata_bars`。
- `POST /api/marketdata/ingestion-jobs`：创建 SPOT 历史 K 线接入任务。
- `GET /api/marketdata/ingestion-jobs`：查询最近接入任务列表。
- `GET /api/marketdata/ingestion-jobs/{jobId}`：查询接入任务详情。
- `GET /api/marketdata/ingestion-jobs/{jobId}/runs`：查询任务运行记录。
- `POST /api/marketdata/ingestion-jobs/{jobId}/run-once`：执行一次接入任务，返回 `runId`、`status`、`fetchedBars`、`insertedBars`、`updatedBars`、`skippedBars`、`startedAt`、`finishedAt`、`errorMessage`。

GateH-2 固定范围：

- `exchangeCode`：`OKX`、`BINANCE`。
- `marketType`：仅 `SPOT`。
- `symbol`：`BTC-USDT`、`ETH-USDT`、`SOL-USDT`。
- `interval`：`1m`、`5m`、`15m`、`1h`、`4h`、`1d`。
- 数据类型：OHLCV K 线。

GateH-2 不新增 AI 自动交易、AI 信号接入、dataset/backtest 绑定、合约全量接入、资金费率、深度、逐笔成交、美股/A 股适配或复杂因子平台 API。

## GateH-3 Dataset and Backtest Binding API

当前已实现的 GateH-3 数据集与回测绑定入口：

- `GET /api/marketdata/datasets`：查询 marketdata dataset 列表，支持按 `exchangeCode`、`marketType`、`symbol`、`interval` 过滤。
- `POST /api/marketdata/datasets`：创建 dataset，并立即基于 `marketdata_bars` 计算覆盖范围与质量状态。
- `GET /api/marketdata/datasets/{datasetId}`：查询 dataset 详情。
- `POST /api/marketdata/datasets/{datasetId}/refresh-quality`：重新计算 dataset 覆盖率、缺口数、异常 bar 数和质量状态。
- `PATCH /api/backtest-configs/{configId}/dataset`：把 dataset 绑定到 backtest config，并保存 `dataset_snapshot_json`。
- `GET /api/backtest-configs/{configId}`：返回 `datasetId` 和 `datasetSnapshotJson`。
- `GET /api/backtest-runs/{runId}`：返回 run 创建时固化的 `datasetSnapshotJson`。

GateH-3 固定范围：dataset 来源仅为 GateH-2 的 `marketdata_bars`；仅支持 `OKX` / `BINANCE`、`SPOT`、`BTC-USDT` / `ETH-USDT` / `SOL-USDT`、`1m` / `5m` / `15m` / `1h` / `4h` / `1d`。

GateH-3 不新增 AI 自动交易、AI 信号接入、合约全量接入、资金费率、深度、逐笔成交、美股/A 股适配、复杂因子平台或高频交易 API。

## GateI-1 Strategy Version and Publish API

当前已实现的 GateI-1 策略版本与发布链路入口：

- `GET /api/strategies/{strategyCode}`：按 `strategyCode` 查询策略定义详情。
- `PATCH /api/strategies/{strategyCode}/status`：按 `strategyCode` 启用或停用策略定义。
- `GET /api/strategies/{strategyCode}/versions`：查询策略版本列表。
- `POST /api/strategies/{strategyCode}/versions`：创建策略版本，固化 `paramSnapshotJson`、`configSnapshotJson`、`sourceSnapshotJson` 和 `checksum`。
- `GET /api/strategies/{strategyCode}/versions/{versionId}`：查询策略版本详情，并校验版本归属策略编码。
- `GET /api/publishes`：查询发布记录列表，可按 `strategyVersionId` 过滤。
- `GET /api/publishes/{publishId}`：查询发布记录详情。
- `POST /api/publishes?backtestRunId={runId}`：发布回测结果，可选绑定 `strategyVersionId`。
- `POST /api/backtest-runs/{runId}/publish`：兼容既有发布入口，可选传入 `strategyVersionId`。
- `GET /api/backtest-runs/{runId}/publish`：返回发布结果，并包含策略版本绑定与 `versionSnapshotJson`。

GateI-1 固定范围：

- 策略版本状态：`DRAFT`、`ACTIVE`、`ARCHIVED`。
- 发布绑定只接受存在且 `ACTIVE` 的策略版本。
- 发布时固化 `versionSnapshotJson`，后续策略版本变化不会改写历史发布记录。
- 不修改策略核心算法，不启动回测，不进入 Paper Trading。

GateI-1 不新增 AI API，不新增 AI 自动交易接口，不新增美股/A 股、合约全量、高频或复杂因子平台接口。

## GateI-2 Backtest Traceability and Evaluation API

当前已实现的 GateI-2 回测配置、运行追溯与评估报告入口：

- `GET /api/backtest-configs`：返回回测配置列表，包含 `strategyVersionId`、`strategyVersionSnapshotJson`、`paramSnapshotJson`、`configSnapshotJson`、`datasetId`、`datasetSnapshotJson`。
- `POST /api/backtest-configs`：创建回测配置，并初始化参数快照、配置快照；不启动回测。
- `GET /api/backtest-configs/{configId}`：返回单条回测配置详情，包含 strategy version、dataset、参数和配置快照。
- `PATCH /api/backtest-configs/{configId}/strategy-version`：绑定已存在的 strategy version，后端从 `strategy_versions` 读取并固化版本快照和参数快照；请求体只允许传 `strategyVersionId`。
- `PATCH /api/backtest-configs/{configId}/dataset`：复用 GateH-3 dataset 绑定入口，后端固化 dataset snapshot。
- `POST /api/backtest-runs`：根据回测配置创建 run，创建时固化 `strategyVersionId`、`strategyVersionSnapshotJson`、`datasetSnapshotJson`、`paramSnapshotJson`、`configSnapshotJson`。
- `GET /api/backtest-runs/{runId}`：返回 run 详情和完整追溯快照；后续配置重新绑定不会改写历史 run。
- `GET /api/evaluations`：查询已生成评估报告列表，返回 total return、annualized return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe、metrics JSON 等核心指标。
- `GET /api/evaluations/{evaluationId}`：按 `evalReportId` 查询评估报告详情。

GateI-2 固定范围：

- 只增强现有 backtest / evaluation 链路。
- 不修改回测核心算法，不修改策略核心算法，不修改交易核心状态机。
- 不做 SIM/Paper Trading 运行闭环，不进入 GateI-3/4。
- 不接 AI，不新增 AI 分析报告、AI 信号、AI 自动交易或 AI Paper Trading。
- 不新增美股/A 股、合约全量、高频或复杂因子平台 API。

## GateI Planning Entry

GateI API 规划入口为 [GATEI_API_PLAN.md](./GATEI_API_PLAN.md)。本轮只做规划，不实现接口。

GateI 规划 API 分类：

- Strategy Version API。
- Publish Version API。
- Backtest Config Enhanced API。
- Evaluation Report API。
- Paper Trading Run API。
- Risk Result API。
- Equity Curve API。
- Position Curve API。
- Trade Replay API。
- Emergency Stop API。

GateI 后续规划不改变当前事实：AI、AI 信号、AI 自动交易和 AI Paper Trading 仍未开始。
