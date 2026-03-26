# GateF（研究 / 回测 / 评估能力）

冻结状态：**GateF 已完成并冻结；本卷宗只作归档与交接，不再继续承载新功能。**

当前状态：**GateF-DOC-1 / GateF-1 / GateF-2 / GateF-3 / GateF-4 / GateF-5 已完成；下一阶段已切换为 GateG。**

GateF 是 GateE 之后的独立阶段。GateF 不回头重写 GateE，也不继续承接 GateE 遗留执行债务。

---

## 1. GateF 当前已落地

- GateF-1：`nq-research`、research/backtest 配置与 run 骨架
- GateF-2：`nq-backtest`、历史行情输入边界、显式 `start run`
- GateF-3：`sim_orders / sim_trades / sim_positions / sim_pnl_snapshots`
- GateF-4：`nq-eval`、`backtest_eval_reports`、显式 `evaluate` 与 evaluation 查询
- GateF-5：研究产物与执行域接口收口、`publish` 写链与查询呈现

---

## 2. GateF 主链现状

1. 创建研究 / 回测配置
2. 创建 `backtest run`
3. 显式 `start run`
4. 生成 sim_* 事实链
5. 显式 `evaluate`
6. 显式 `publish`
7. 查询 run summary、sim_* 明细、evaluation summary / report、publish result

GateF 主链已收口完成，不再保留“待做”语义。

---

## 3. GateF 与 GateE / GateG 的关系

- GateE 解决：策略接入与调度编排
- GateF 解决：研究 / 回测 / 评估
- GateG 解决：前端控制台与联调
- GateF 消费 GateE 已冻结产物与接口
- GateG 消费 GateF 已冻结产物与接口

---

## 4. 冻结结论

- GateF 已完成并冻结
- GateF 不再继续承载当前开发任务
- GateF 冻结后进入 GateG 阶段
- GateG 不以前置数据库大改为条件

---

## 5. 入口索引

- `docs/gates/gate-f/GATE_F_CHECKLIST.md`
- `docs/gates/gate-f/MODULES.md`
- `docs/gates/gate-f/CONTRACTS.md`
- `docs/gates/gate-f/DB_SCHEMA.md`
- `docs/gates/gate-f/WORK.md`
