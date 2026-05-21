# GateI Work Order

本文件是 GateI 后续开工单草案。本轮只写规划，不执行 work order。

## GateI-1-WO：策略版本与发布链路正式化

### 背景

GateH 已提供 dataset 和 backtest snapshot 能力，但策略版本和发布版本仍需要正式化，才能保证回测、评估和 Paper run 的输入可追溯。

### 目标

- 新增策略版本管理主链。
- 发布版本引用冻结策略版本和评估报告。
- 发布版本成为 Paper Trading 的输入。

### 范围

- Strategy Version API。
- Publish Version API。
- 策略版本状态流转。
- 发布版本状态流转。
- 前端 `/strategies` 和 `/publishes` 增强。
- E2E `strategy-version-smoke`、`publish-version-smoke`。

### 不做范围

- 不接 AI。
- 不启动 Paper Trading run。
- 不改策略核心算法。
- 不新增美股/A 股或合约能力。

### 影响文件

- `backend/nq-api/**/strategy/**`
- `backend/nq-api/**/publish/**`
- `backend/nq-core/**/strategy/**`
- `backend/nq-core/**/publish/**`
- `backend/nq-infra/**/strategy/**`
- `backend/nq-infra/**/publish/**`
- `frontend/src/pages/strategies/**`
- `frontend/src/pages/publishes/**`
- `frontend/tests/e2e/**`
- `docs/current/**`

### API 变化

- 新增 Strategy Version API。
- 新增 Publish Version API。

### DB 变化

- 规划新增 `strategy_versions`。
- 规划新增或增强 `strategy_publish_versions` / `publish_records`。
- 所有新增表和字段必须写 COMMENT。

### 前端变化

- `/strategies` 支持版本管理。
- `/publishes` 支持发布版本管理。

### 测试要求

- `mvn -f backend/pom.xml test`
- `npm run build`
- `npm run test:e2e`

### 验收标准

- 策略版本可创建、查询、冻结、归档。
- 发布版本可创建、查询、审批。
- 发布版本只允许引用冻结策略版本。
- E2E smoke 通过。

### 回滚策略

- 新 API 可下线入口但保留数据。
- migration 后续必须增量可兼容，回滚以禁用入口和 revert 单独提交为主。

## GateI-2-WO：回测配置、评估指标、结果追溯增强

### 背景

GateH-3 已完成 dataset binding，但虚拟币 V1 需要把 dataset、策略版本、参数快照和评估报告串联起来。

### 目标

- 回测配置绑定 strategy version、dataset 和 parameter snapshot。
- 回测 run 固化输入快照。
- 评估报告输出核心指标。

### 范围

- Backtest Config Enhanced API。
- Evaluation Report API。
- 回测输入快照。
- 评估指标持久化。
- 前端 `/backtests` 和 `/evaluations` 增强。
- E2E `backtest-config-enhanced-smoke`、`evaluation-report-smoke`。

### 不做范围

- 不改回测核心算法。
- 不做 AI 评估解读。
- 不启动 Paper Trading run。

### 影响文件

- `backend/nq-api/**/research/**`
- `backend/nq-research/**`
- `backend/nq-backtest/**`
- `backend/nq-eval/**`
- `backend/nq-infra/**/research/**`
- `frontend/src/pages/backtests/**`
- `frontend/src/pages/evaluations/**`
- `frontend/tests/e2e/**`
- `docs/current/**`

### API 变化

- 增强 Backtest Config API。
- 增强 Evaluation Report API。

### DB 变化

- 增强 `backtest_configs`。
- 增强 `backtest_runs`。
- 增强 `backtest_eval_reports` 或等价评估表。
- 所有新增表和字段必须写 COMMENT。

### 前端变化

- `/backtests` 展示 strategy version、dataset、parameter snapshot。
- `/evaluations` 展示核心指标和输入快照。

### 测试要求

- `mvn -f backend/pom.xml test`
- `npm run build`
- `npm run test:e2e`

### 验收标准

- 回测配置可绑定 dataset 和策略版本。
- 回测 run 固化输入快照。
- 评估报告核心指标可查询。
- E2E smoke 通过。

### 回滚策略

- 保留 GateH-3 dataset binding，不破坏既有 config 查询。
- 新字段后续通过兼容默认值降低回滚风险。

## GateI-3-WO：SIM / Paper Trading 运行闭环

### 背景

虚拟币 V1 需要把发布版本推进到 SIM / Paper 运行，并回写 Paper 订单、成交和运行状态。

### 目标

- 创建 Paper Trading run。
- 启动、暂停、停止 run。
- 回写 Paper 订单和成交。
- 保持 SIM / Paper 与 LIVE 隔离。

### 范围

- Paper Trading Run API。
- Paper order/trade 最小模型。
- run 状态流转。
- 前端 `/paper-trading` 或 `/runs`。
- E2E `paper-trading-run-smoke`。

### 不做范围

- 不接 AI。
- 不触发 LIVE 自动交易。
- 不改交易核心状态机。
- 不接合约全量。

### 影响文件

- `backend/nq-api/**/paper/**`
- `backend/nq-core/**/paper/**`
- `backend/nq-infra/**/paper/**`
- `backend/nq-trading/**`
- `backend/nq-ledger/**`
- `backend/nq-risk/**`
- `frontend/src/pages/paper-trading/**`
- `frontend/tests/e2e/**`
- `docs/current/**`

### API 变化

- 新增 Paper Trading Run API。
- 后续可查询 run、orders、trades。

### DB 变化

- 新增 `paper_trading_runs`。
- 新增 `paper_trading_orders`。
- 新增 `paper_trading_trades`。
- 所有新增表和字段必须写 COMMENT。

### 前端变化

- 新增 Paper run 管理入口。
- 展示 run 状态、订单、成交和账户上下文。

### 测试要求

- `mvn -f backend/pom.xml test`
- `npm run build`
- `npm run test:e2e`

### 验收标准

- 发布版本可创建 Paper run。
- run 可启动、暂停、停止。
- Paper 订单和成交可回写并查询。
- E2E smoke 通过。

### 回滚策略

- 禁用 Paper run 创建入口。
- 保留历史 Paper 数据用于审计。

## GateI-4-WO：风控回写、资金曲线、持仓曲线、复盘与异常停机

### 背景

Paper run 只有形成风控、资金、持仓、复盘和异常停机闭环，才具备进入 GateJ 稳定运行的基础。

### 目标

- 风控结果回写。
- 资金曲线和持仓曲线可查询。
- 单次交易可复盘。
- 异常停机机制具备最小闭环。

### 范围

- Risk Result API。
- Equity Curve API。
- Position Curve API。
- Trade Replay API。
- Emergency Stop API。
- 前端 `/risk`、`/portfolio/equity-curve`、`/portfolio/position-curve`、`/replay`、`/emergency-stop`。
- E2E `risk-result-smoke`、`equity-curve-smoke`、`position-curve-smoke`、`trade-replay-smoke`、`emergency-stop-smoke`。

### 不做范围

- 不接 AI。
- 不做 AI Paper Trading。
- 不直接操作 LIVE 交易所账户。
- 不做复杂组合归因平台。

### 影响文件

- `backend/nq-api/**/risk/**`
- `backend/nq-api/**/portfolio/**`
- `backend/nq-api/**/replay/**`
- `backend/nq-core/**/risk/**`
- `backend/nq-core/**/portfolio/**`
- `backend/nq-ledger/**`
- `backend/nq-observability/**`
- `frontend/src/pages/risk/**`
- `frontend/src/pages/portfolio/**`
- `frontend/src/pages/replay/**`
- `frontend/src/pages/emergency-stop/**`
- `frontend/tests/e2e/**`
- `docs/current/**`

### API 变化

- 新增 Risk Result API。
- 新增 Equity Curve API。
- 新增 Position Curve API。
- 新增 Trade Replay API。
- 新增 Emergency Stop API。

### DB 变化

- 新增 `risk_check_results`。
- 新增 `equity_curve_snapshots`。
- 新增 `position_curve_snapshots`。
- 新增 `trade_replay_records`。
- 新增 `emergency_stop_events`。
- 所有新增表和字段必须写 COMMENT。

### 前端变化

- 展示风控结果、曲线、复盘和异常停机事件。
- 提供触发和解除 Paper scope emergency stop 的操作。

### 测试要求

- `mvn -f backend/pom.xml test`
- `npm run build`
- `npm run test:e2e`

### 验收标准

- 风控结果可查询。
- 资金曲线、持仓曲线可查询。
- 单次交易复盘可查看。
- 异常停机可触发和解除。
- E2E smoke 通过。

### 回滚策略

- 禁用异常停机前端入口。
- 保留 stop 事件审计记录。
- 曲线和复盘查询失败不阻断 Paper run 主链。
