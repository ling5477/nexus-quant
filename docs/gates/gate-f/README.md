# GateF（研究 / 回测 / 评估能力）

当前状态：**GateF-DOC-1 / GateF-1 / GateF-2 / GateF-3 / GateF-4 已完成。**

GateF 是 GateE 之后的独立阶段。GateF 不回头重写 GateE，也不继续承接 GateE 遗留执行债务。

---

## 1. GateF 当前已落地

- GateF-1：`nq-research`、research/backtest 配置与 run 骨架
- GateF-2：`nq-backtest`、历史行情输入边界、显式 `start run`
- GateF-3：`sim_orders / sim_trades / sim_positions / sim_pnl_snapshots`
- GateF-4：`nq-eval`、`backtest_eval_reports`、显式 `evaluate` 与 evaluation 查询

---

## 2. GateF 主链现状

1. 创建研究 / 回测配置
2. 创建 `backtest run`
3. 显式 `start run`
4. 生成 sim_* 事实链
5. 显式 `evaluate`
6. 查询 run summary、sim_* 明细、evaluation summary / report

当前仍未落地：

- GateF-5 的研究产物与执行域接口收口

---

## 3. GateF 与 GateE 的关系

- GateE 解决：策略接入与调度编排
- GateF 解决：研究 / 回测 / 评估
- GateF 消费 GateE 已冻结产物与接口
- GateF 不回头重写 GateE

---

## 4. 当前完成标准

- `nq-research` 模块建立
- `nq-backtest` 模块建立
- `nq-eval` 模块建立
- `research_configs / backtest_configs / backtest_runs` 已落表
- `sim_orders / sim_trades / sim_positions / sim_pnl_snapshots` 已落表
- `backtest_eval_reports` 已落表
- `nq-app` 已暴露 research/backtest/evaluation 查询入口

---

## 5. 入口索引

- `docs/gates/gate-f/GATE_F_CHECKLIST.md`
- `docs/gates/gate-f/MODULES.md`
- `docs/gates/gate-f/CONTRACTS.md`
- `docs/gates/gate-f/DB_SCHEMA.md`
- `docs/gates/gate-f/WORK.md`
