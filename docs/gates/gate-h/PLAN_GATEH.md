# GateH Plan

## GateH 背景

NexusQuant 已完成 DOC-CLEAN、BASELINE-FIX 和 BASELINE-FIX-2，当前本地 PostgreSQL 默认端口固定为 `5432`，后端、前端、E2E、Python 验证基线均已通过。GateH 之前只允许做规划，本文件用于明确 GateH 的正式开工边界。

GateH 的定位是交易工作台与历史行情数据接入阶段。它承接 RC1 冻结后的账户、交易、策略、研究、回测、评估、发布、风控、行情基础表和前端壳能力，把系统推进到可使用真实历史 K 线做回测、可在前端查询行情数据和接入任务状态的阶段。

## GateH 目标

- 交易工作台可正式使用，订单列表、订单详情、账户上下文、SIM / LIVE 边界清晰。
- OKX / Binance 历史 K 线可以进入本地数据库。
- `instrument_catalog` 作为交易对选择来源。
- `marketdata_bars` 数据可追溯来源、接入任务和质量状态。
- 回测配置可以绑定真实历史行情数据集。
- 前端可以查询行情数据、交易对目录、行情接入任务和数据集绑定状态。
- GateH 主链具备后端、前端、API smoke、E2E 覆盖。

## GateH 不做范围

- AI 自动交易。
- AI 信号接入。
- AI 直接下单。
- 多 Agent 实盘决策。
- 合约全量接入。
- 高频交易。
- 美股适配。
- A 股适配。
- 复杂因子平台。
- 链上数据全量接入。
- 社交媒体情绪系统。

## GateH 拆分

### GateH-1：交易工作台正式化

输入：

- RC1 冻结的账户上下文、订单、成交、持仓、风控、交易 API 基线。
- 当前前端账户上下文 E2E 基线。
- `SIM / LIVE` canonical 环境口径。

输出：

- 正式交易工作台规划落地为可验收页面和接口清单。
- 订单列表、订单详情、账户上下文、下单前风控摘要、SIM / LIVE 显示与隔离完成。
- `trade-validation` alias 退役路径明确。

验收标准：

- `/trading` 能以当前账户上下文展示订单主链状态。
- 页面不会要求用户手工输入长期 `accountId`。
- SIM / LIVE 边界在 API、UI 和 E2E 中可验证。
- `trading-workspace-smoke` 通过。

### GateH-2：OKX / Binance 历史 K 线接入

输入：

- 当前 `instrument_catalog` 与 `marketdata_bars` 基础表。
- OKX / Binance adapter 边界。
- PostgreSQL `5432` 本地验证基线。

输出：

- OKX / Binance SPOT 历史 OHLCV K 线接入计划落地。
- 支持按 `exchange_code`、`market_type`、`symbol`、`interval`、时间范围入库。
- 支持续拉、去重、完整性检查和接入任务追踪。

验收标准：

- 第一版覆盖 `OKX`、`BINANCE`、`SPOT`、`BTC-USDT`、`ETH-USDT`、`SOL-USDT`。
- 第一版 interval 覆盖 `1m`、`5m`、`15m`、`1h`、`4h`、`1d`。
- `marketdata_bars` 唯一约束能保证 K 线幂等写入。
- `marketdata-ingestion-smoke` 和 `marketdata-bars-query-smoke` 通过。

### GateH-3：行情数据质量、数据集绑定、回测链路增强

输入：

- GateH-2 的真实历史 K 线数据。
- 当前 research -> backtest -> evaluation -> publish 最小 DB-backed happy path。
- 当前回测配置与结果追溯能力。

输出：

- 行情数据质量状态、数据范围和接入 run 状态可查询。
- 回测配置可绑定真实历史行情数据集。
- 回测结果可追溯数据集来源。
- 前端提供数据集绑定入口和质量状态展示。

验收标准：

- `marketdata_datasets` 可描述可复用数据集范围。
- `backtest_configs` 可绑定数据集。
- 回测结果能追溯 exchange、market_type、symbol、interval、time range 和 dataset。
- `backtest-dataset-binding-smoke` 通过。

## API 规划入口

API 只在 GateH-PLAN 中规划，不在本轮实现。正式规划入口为 [GATEH_API_PLAN.md](./GATEH_API_PLAN.md)。

GateH API 分类：

- Trading Workspace API。
- Instrument API。
- Marketdata Bar API。
- Marketdata Ingestion Job API。
- Marketdata Dataset API。
- Backtest Dataset Binding API。

## DB 规划入口

DB 只在 GateH-PLAN 中规划，不在本轮新增 migration。正式规划入口为 [GATEH_DB_PLAN.md](./GATEH_DB_PLAN.md)。

GateH DB 重点：

- `instrument_catalog` 增强。
- `marketdata_bars` 来源、质量、幂等增强。
- `marketdata_ingestion_jobs`、`marketdata_ingestion_runs`、`marketdata_datasets` 草案。
- `backtest_configs` 与 dataset 绑定草案。

## 前端规划入口

前端只在 GateH-PLAN 中规划，不在本轮新增页面实现。正式规划入口为 [GATEH_FRONTEND_PLAN.md](./GATEH_FRONTEND_PLAN.md)。

GateH 前端页面：

- `/trading` 正式交易工作台。
- `/instruments` 交易对目录。
- `/marketdata` 行情数据查询。
- `/marketdata/ingestion` 行情接入任务。
- `/backtests` 数据集绑定入口。

## 测试规划入口

测试只在 GateH-PLAN 中规划，当前验证基线继续保留。正式规划入口为 [GATEH_TEST_PLAN.md](./GATEH_TEST_PLAN.md)。

当前基线：

- `mvn -f backend/pom.xml test` 已通过。
- `npm run build` 已通过。
- `npm run test:e2e` 已通过，结果为 5 passed / 3 skipped。
- `python -m pytest -q`、`python -m mypy src`、`python -m ruff check .` 已通过。

## 风险与回滚策略

风险：

- 历史行情接入可能引入交易所限流、分页边界、时区和缺口处理问题。
- `marketdata_bars` 唯一约束和索引设计不足会影响幂等和查询性能。
- 回测绑定真实数据后，历史结果可复现性依赖 dataset version 和数据质量状态。
- SIM / LIVE 边界若只在前端提示，不在 API 和后端校验中落地，会造成误用风险。

回滚策略：

- GateH 每个子 Gate 使用独立 work order 和独立提交。
- DB migration 必须可向后兼容，新增字段和表先不破坏现有查询。
- 新 API 默认新增而不是替换旧 API，废弃路径需要文档化。
- 前端新页面和入口通过路由开关或导航入口控制，必要时可下线入口但保留数据。
- 历史行情接入任务必须可暂停、可重跑、可按唯一键去重。

## GateH 完成后进入 GateI 的条件

- GateH-1 / GateH-2 / GateH-3 全部通过验收。
- 交易工作台可正式使用。
- OKX / Binance 历史 K 线接入稳定。
- `instrument_catalog` 和 `marketdata_bars` 成为行情与回测主数据来源。
- 回测可以绑定真实历史行情数据集并追溯来源。
- 前端可查询行情数据、接入任务状态和数据集绑定状态。
- SIM / LIVE 边界在后端、前端和 E2E 中均可验证。
- GateH E2E 主链全部通过。
- 文档、API、DB、前端、测试结果完成冻结归档。
