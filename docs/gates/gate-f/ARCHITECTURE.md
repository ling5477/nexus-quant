# GateF ARCHITECTURE
# GateF 阶段架构摘要

本文档只回答三件事：GateF 在系统里放在哪里、和 GateE 怎么衔接、后续实现主链如何拆开。

---

## 1. 系统位置

GateF 位于 GateE 冻结执行与调度能力之上。

逻辑分层如下：

1. 研究配置层：定义研究参数、回测参数、评估参数
2. 数据输入层：加载回测所需市场数据
3. 回测运行层：执行 `backtest run`
4. 模拟执行层：生成模拟订单、模拟成交、模拟持仓、模拟资金变化
5. 评估层：产出 PnL、drawdown、metrics、summary
6. 查询层：对外提供 `backtest run` 与评估结果查询

---

## 2. 与 GateE 的衔接

GateE 可作为输入起点的只有：

- 策略定义语义
- 已冻结字段口径
- 执行域事实链参考

GateE 不能被直接拿来当 GateF 内核的部分：

- `strategy_runs` 不能直接等同 `backtest runs`
- `orders / trades` 不能直接等同模拟订单 / 模拟成交
- `schedule / trigger` 不能直接等同研究批处理入口

---

## 3. GateF 推荐主链

`ResearchConfig -> BacktestConfig -> BacktestRun -> MarketDataInput -> SimOrder/SimTrade/SimPosition -> EvaluationSummary`

---

## 4. 当前仓库事实与 GateF 关系

当前仓库已经有：

- `strategy_definitions`
- `strategy_runs`
- `orders / trades`
- run 查询面
- canonical adapter 结果模型

当前仓库还没有：

- backtest run 模型
- 市场数据输入模型
- 模拟订单 / 成交 / 持仓模型
- 评估指标模型
- GateF 独立查询面

结论：

- GateF 必须新建研究域模型
- 但字段语义与部分输入可复用 GateE 冻结事实
