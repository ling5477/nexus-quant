# docs/current/GATEF_INPUTS.md
# GateF 输入清单（已作为 GateF-DOC-1 输入落卷宗）

> 历史输入参考：RC1 当前阶段不再以此文档作为执行入口。

本文档只整理 GateF-DOC-1 的输入依据，不展开 GateF 业务实现。

---

## 1. GateE 可复用输入资产

### 数据与事实层

- `strategy_definitions`
- `strategy_schedules`
- `strategy_runs`
- `orders`
- `trades`

### 契约与模型

- canonical adapter 返回模型
- `strategyId / strategyRunId / requestId / scheduleJobId` 已冻结语义
- GateE run 查询面

### 文档资产

- `docs/gates/gate-e/README.md`
- `docs/gates/gate-e/ARCHITECTURE.md`
- `docs/gates/gate-e/MODULES.md`
- `docs/gates/gate-e/CONTRACTS.md`
- `docs/gates/gate-e/DB_SCHEMA.md`
- `docs/gates/gate-e/STATE_MACHINE.md`
- `docs/gates/gate-e/TEST_CASES.md`
- `docs/gates/gate-e/DECISIONS.md`
- `docs/gates/gate-e/WORK.md`

---

## 2. GateF 前置约束

- GateF 不回头重写 GateE 主链
- 研究 / 回测 / 评估与实盘执行必须保持边界
- GateE 的执行对象可以复用为事实输入，但不能直接等同于研究对象
- `strategy_runs / orders / trades` 可以作为 GateF 输入资产
- `trigger_id` 不存在，不得假设已有独立 trigger 事实表

---

## 3. GateF 待决策问题

- 回测运行模型如何定义
- 市场数据输入边界如何定义
- 研究配置与执行配置如何分离
- 绩效评估指标口径如何冻结
- 是否需要新表 / 新模块 / 新文档目录结构

---

## 4. 当前不直接带入 GateF 的项

- `PlaceOrderCommand.strategyId` 兼容债务
- 多实例严格一致 dedup / serialization
- `ledger / risk / event / audit` 的稳定 run 级完全聚合

说明：

- 这些都属于后续演进输入
- 但不构成 GateE 继续保持“进行中”的理由

---

## 5. 对应卷宗

- `docs/gates/gate-f/README.md`
- `docs/gates/gate-f/CONTRACTS.md`
- `docs/gates/gate-f/PR_SPLIT_PLAN.md`
