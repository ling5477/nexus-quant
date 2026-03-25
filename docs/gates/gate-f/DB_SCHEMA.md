# GateF 数据模型与 schema 约束

当前结论：**GateF-1 / GateF-2 / GateF-3 / GateF-4 已形成最小研究域、执行域、模拟事实链与评估报告表结构。**

---

## 1. 当前已落地表

- `research_configs`
- `backtest_configs`
- `backtest_runs`
- `sim_orders`
- `sim_trades`
- `sim_positions`
- `sim_pnl_snapshots`
- `backtest_eval_reports`

---

## 2. 表职责

- `backtest_runs`
  - 保存 run 身份、状态和 run 级执行摘要
- `sim_orders / sim_trades / sim_positions / sim_pnl_snapshots`
  - 保存 GateF-3 模拟执行事实
- `backtest_eval_reports`
  - 保存 GateF-4 run 级评估报告
  - `backtest_run_id` 唯一
  - 支持重复 evaluate 覆盖更新

---

## 3. 关键约束

- `backtest_runs.summary_json` 只保存 run 级执行摘要
- sim_* 明细不回写到 `backtest_runs.summary_json`
- `backtest_eval_reports.report_json` 保存评估明细
- 评估域只读消费 sim_* 与 run/config 事实
