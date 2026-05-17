# GateH Work Order

本文件是后续 GateH 开工单草案，不执行，不开发功能代码。

## GateH-1-WO：交易工作台正式化

### 背景

RC1 已形成账户上下文、订单、成交、持仓、风控和前端壳基础。GateH-1 需要把交易工作台从基础能力推进到可正式使用的工作台。

### 目标

- 正式化 `/trading` 工作台。
- 稳定订单列表和订单详情。
- 明确账户上下文和 SIM / LIVE 边界。
- 展示下单前风控摘要入口。

### 范围

- Trading Workspace API 规划中的订单查询和详情。
- 前端交易工作台页面。
- 账户上下文强校验。
- `trading-workspace-smoke`。

### 不做范围

- 不新增 AI 下单。
- 不新增策略自动交易。
- 不新增复杂订单算法。
- 不修改交易核心语义。

### 影响文件

- `backend/**/trading/**`
- `backend/**/account/**`
- `frontend/src/pages/**`
- `frontend/src/features/trading/**`
- `frontend/tests/e2e/**`
- `docs/current/**`

### API 变化

- 规划 `GET /api/trading/orders`。
- 规划 `GET /api/trading/orders/{orderId}`。
- 不新增下单 API。

### DB 变化

- 优先复用已有订单、成交、账户表。
- 如需新增只读索引，必须先补 migration 评审。

### 前端变化

- `/trading` 成为正式工作台入口。
- 订单列表、详情、账户上下文、SIM / LIVE 状态展示。

### 测试要求

- 后端订单查询单元测试。
- API smoke。
- `npm run build`。
- `trading-workspace-smoke`。

### 验收标准

- 当前账户订单可查询。
- 订单详情可查看。
- SIM / LIVE 边界清晰。
- E2E 通过。

### 回滚策略

- 保留旧入口。
- 可通过导航入口下线 `/trading` 新工作台。
- 后端新增查询接口不破坏既有接口。

## GateH-2-WO：历史 K 线接入

### 背景

当前系统已有行情基础表，但尚未完成 OKX / Binance 历史 K 线正式接入。GateH-2 需要完成第一版 SPOT OHLCV 历史数据接入。

### 目标

- 接入 OKX / Binance 历史 K 线。
- 强化 `instrument_catalog` 和 `marketdata_bars`。
- 支持接入任务、run once、去重、质量状态。

### 范围

- OKX / Binance。
- SPOT。
- `BTC-USDT`、`ETH-USDT`、`SOL-USDT`。
- `1m`、`5m`、`15m`、`1h`、`4h`、`1d`。
- OHLCV K 线。

### 不做范围

- 不做合约全量接入。
- 不做 tick 数据。
- 不做高频交易。
- 不做链上数据。

### 影响文件

- `backend/**/marketdata/**`
- `backend/**/adapter-okx/**`
- `backend/**/adapter-binance/**`
- `backend/**/db/migration/**`
- `frontend/src/features/marketdata/**`
- `frontend/tests/e2e/**`
- `docs/current/**`

### API 变化

- 规划 `GET /api/instruments`。
- 规划 `POST /api/instruments/sync`。
- 规划 `GET /api/marketdata/bars`。
- 规划 marketdata ingestion job API。

### DB 变化

- 规划增强 `instrument_catalog`。
- 规划增强 `marketdata_bars`。
- 规划新增 ingestion jobs/runs 表。

### 前端变化

- `/instruments` 查询交易对目录。
- `/marketdata` 查询 K 线。
- `/marketdata/ingestion` 管理接入任务。

### 测试要求

- K 线写入幂等测试。
- 交易所 adapter 边界测试。
- API smoke。
- `marketdata-bars-query-smoke`。
- `marketdata-ingestion-smoke`。

### 验收标准

- OKX / Binance SPOT K 线可入库。
- 重复写入不产生重复 bar。
- 数据来源和质量状态可查。
- E2E 通过。

### 回滚策略

- 停用 ingestion job。
- 保留已入库数据但不作为回测默认数据源。
- 新增 API 可下线导航入口，不破坏既有回测。

## GateH-3-WO：数据质量与回测绑定

### 背景

GateH-2 产生真实历史行情后，需要让回测配置可绑定可追溯的数据集，并把数据质量纳入回测前置条件。

### 目标

- 建立 `marketdata_datasets`。
- 回测配置绑定 dataset。
- 回测结果追溯 dataset 来源。
- 前端提供数据集绑定入口。

### 范围

- 数据集创建和查询。
- 回测配置绑定 dataset。
- 数据质量状态展示。
- 回测结果来源追溯。

### 不做范围

- 不做复杂因子平台。
- 不做 AI 信号。
- 不改策略核心逻辑。
- 不做多市场股票适配。

### 影响文件

- `backend/**/marketdata/**`
- `backend/**/backtest/**`
- `backend/**/db/migration/**`
- `frontend/src/features/backtest/**`
- `frontend/src/features/marketdata/**`
- `frontend/tests/e2e/**`
- `docs/current/**`

### API 变化

- 规划 `GET /api/marketdata/datasets`。
- 规划 `POST /api/marketdata/datasets`。
- 规划 `PATCH /api/backtest-configs/{configId}/dataset`。

### DB 变化

- 规划 `marketdata_datasets`。
- 规划 `backtest_configs` 与 dataset 绑定。
- 规划回测结果 dataset 快照。

### 前端变化

- `/backtests` 增加数据集绑定入口。
- 数据集质量状态和范围展示。

### 测试要求

- dataset 创建和查询测试。
- backtest config 绑定测试。
- 回测结果追溯测试。
- `backtest-dataset-binding-smoke`。

### 验收标准

- 回测可绑定真实历史行情数据集。
- 回测结果可追溯数据来源。
- 数据质量不足时能阻止或提示回测。
- E2E 通过。

### 回滚策略

- 解绑 dataset。
- 回测退回原有默认数据来源。
- 保留 dataset 表数据，停止前端入口和新绑定接口。
