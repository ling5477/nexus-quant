# GateF DB_SCHEMA
# GateF 数据模型与 schema 约束

当前结论：**GateF-DOC-1 不新增 migration，不改现有 schema。**

---

## 1. 当前可复用表

- `strategy_definitions`
- `strategy_runs`
- `orders`
- `trades`

这些表的作用：

- 作为 GateF 输入参考
- 作为字段语义参考
- 不是 GateF 直接落库模型

---

## 2. 当前不直接复用的原因

- `strategy_runs` 属于执行运行，不是研究运行
- `orders / trades` 属于实盘 / 执行事实，不是模拟事实
- `ledger / risk / event / audit` 当前也不构成完整研究输入面

---

## 3. GateF 候选数据对象（仅定义，不落表）

- `backtest_runs`
- `backtest_orders`
- `backtest_trades`
- `backtest_positions`
- `backtest_pnl_snapshots`
- `evaluation_reports`

说明：

- 这些只是 GateF-DOC-1 的候选对象
- 本批不决定是否落表
- 后续按 GateF-1 / GateF-2 / GateF-3 再决定是否需要 migration
