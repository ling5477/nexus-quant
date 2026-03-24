# GateF MODULES
# GateF 模块职责与越界约束

本文档按“当前已有模块 + GateF 预计新增最小模块职责”描述，不假装仓库里已经有 GateF 实装模块。

---

## 1. 可复用现有模块

- `nq-contracts`
- `nq-core`
- `nq-api`
- `nq-app`
- `nq-infra`
- `nq-common`

这些模块当前可复用的价值主要是：

- 字段语义
- 配置入口风格
- 查询入口风格
- 现有执行事实参考

---

## 2. GateF 推荐新增职责

### `nq-research`（候选）

负责：

- 研究配置
- 回测配置
- 研究运行发起

### `nq-backtest`（候选）

负责：

- 市场数据输入抽象
- 回测运行主链
- 模拟订单 / 成交 / 持仓 / 资金更新

### `nq-eval`（候选）

负责：

- PnL
- drawdown
- 指标汇总
- 评估摘要

---

## 3. 越界约束

- GateF 不直接改 GateE 执行主链
- GateF 不把研究对象直接塞进 `nq-scheduler`
- GateF 不复用 `strategy_runs` 作为研究运行事实表
- GateF 不把模拟成交直接写回 GateE 实盘事实表
