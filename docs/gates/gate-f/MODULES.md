# GateF 模块职责与越界约束

---

## 1. 已落地模块

### `nq-research`
- 研究配置
- 回测配置
- 回测运行发起

### `nq-backtest`
- 历史行情输入
- run 执行主链
- sim_* 事实链

### `nq-eval`
- run 级评估报告
- 指标计算与查询聚合
- 只读消费 sim_* 与 run/config 事实

---

## 2. 越界约束

- GateF 不直接改 GateE 执行主链
- GateF 不复用 `strategy_runs / orders / trades / positions / ledger`
- GateF-3 不把 sim_* 明细塞回 `backtest_runs.summary_json`
- GateF-4 不把 evaluation 明细塞回 `backtest_runs.summary_json`
- GateF-4 不改写 sim_* 事实
