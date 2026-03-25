# Modules（GateF 导航摘要）

> Top-Level Navigation Notice
> - 本文件是根级导航摘要，用于帮助快速定位 GateF 模块分工，不是当前阶段的 Source of Truth。
> - 当前阶段的模块职责事实以 `docs/current/*`、`docs/gates/gate-f/MODULES.md` 为准。

## 1. backend 模块职责

### nq-core
GateF 当前继续复用：
- GateE 已冻结的策略定义与执行域事实
- `strategy_definitions` 作为研究配置的来源引用
- 执行语义参考，但不承载研究运行事实

### nq-research
GateF-1 已落地：
- 研究配置
- 回测配置
- 回测运行骨架
- 独立研究域状态与快照

### nq-backtest
GateF 当前已落地：
- 历史行情输入模型
- fixture 历史数据读取
- 最小回测执行主链
- backtest run 显式启动与状态流转
- 模拟订单 / 成交 / 持仓 / PnL 事实链

### nq-scheduler
GateE 负责：
- 策略调度编排主链
- 运行窗口控制、去重、串行化
- GateE-0 的 Binance background reconcile 噪音治理

### nq-adapter-api / nq-adapter-okx / nq-adapter-binance
GateE 负责：
- 返回模型一致性收尾
- 继续隔离交易所方言，不把脏语义返流到 core / scheduler

### nq-infra
GateF 当前负责：
- `research_configs / backtest_configs / backtest_runs` migration
- `sim_orders / sim_trades / sim_positions / sim_pnl_snapshots` migration
- 研究域 JDBC repository
- 历史行情 fixture 读取实现

### nq-api
当前仍主要负责：
- GateE 查询面
- 不承担 GateF 回测结果查询聚合

### nq-app
GateF 当前负责：
- 暴露研究域最小 HTTP 入口
- 暴露显式 `start backtest run` 入口
- 暴露 sim_* 明细查询入口
- 暴露显式 evaluate 与 evaluation 查询入口
- 绑定 GateF local verify 路由

### nq-eval
GateF-4 已落地：
- run 级评估报告
- 指标计算与查询聚合

## 2. 当前不作为主改对象
- nq-auth
- nq-security
- nq-gateway
- frontend
- `research/py` 之外的 research 主体扩展
