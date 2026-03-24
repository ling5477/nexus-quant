# GateF CONTRACTS
# GateF 输入 / 输出 / 接口口径

本文档只冻结 GateF-DOC-1 需要的最小对象边界。

---

## 1. GateF 输入

### 1.1 研究起点

- `strategy_definition`
- `research_config`
- `backtest_config`
- `evaluation_config`

### 1.2 市场数据输入

- `market_data_input`
- 当前只定义边界，不定义具体来源实现

### 1.3 可复用 GateE 事实

- `strategy_definitions`：可作为研究配置起点
- `strategy_runs`：只能作为执行运行参考语义，不能直接复用为研究运行表
- `orders / trades`：只能作为执行事实参考语义，不能直接复用为模拟结果表

---

## 2. GateF 输出

- `backtest_run`
- `sim_order`
- `sim_trade`
- `sim_position`
- `sim_pnl`
- `evaluation_summary`

---

## 3. GateF 与 GateE 的字段关系

可沿用字段语义：

- `strategy_id`
- `exchange_code`
- `account_id`
- `trade_env`

只能映射、不能直接共用实体模型：

- `strategy_runs`
- `orders`
- `trades`

必须新建研究域模型：

- `backtest_run`
- `market_data_input`
- `sim_order`
- `sim_trade`
- `sim_position`
- `evaluation_summary`

---

## 4. 最小对象清单

### `ResearchConfig`

- 研究配置对象
- 用于描述研究参数与实验维度

### `BacktestConfig`

- 回测配置对象
- 描述数据区间、撮合模式、成本参数

### `BacktestRun`

- 回测运行对象
- 研究运行主身份

### `SimOrder / SimTrade / SimPosition`

- 模拟执行结果对象
- 与 GateE 的实盘 `orders / trades` 严格分层

### `EvaluationSummary`

- 评估摘要对象
- 输出 PnL、drawdown、指标结果
