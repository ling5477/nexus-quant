# docs/current/README.md
# Current Gate（当前阶段入口）

当前阶段：**GateE（v1.4：策略接入与调度编排）**

当前状态：**GateE-0.1 / GateE-0.2 / GateE-0.3 / GateE-1.1 已完成，下一步进入 GateE-1.2**

---

## 1. 当前阶段结论

- GateD 已冻结，只作历史证据
- GateE 主定义固定为“策略接入与调度编排”
- GateE-0 只是前置治理，不改写主目标
- GateF 研究 / 回测 / 评估能力不提前进入

---

## 2. 基于仓库现状的事实摘要

当前已经确认的真实落点：

- `strategy_runs` 已存在
- `orders.strategy_run_id` 已存在
- `PlaceOrderRequest`、`AdapterOrderRequest` 已承接运行血缘
- `PlaceOrderCommand.strategyId` 仍是兼容债务
- `StrategyScheduler` 与 `NoopStrategyScheduler` 只提供占位入口
- `GateBDemoStrategyRunner` 是历史 demo runner，不是 GateE 正式主链
- 当前没有正式策略定义表、调度表、策略注册 API、策略运行结果查询面
- GateE-0.1 已完成 Binance background reconcile 噪音治理
- GateE-0.2 已完成 schema / metadata / contract 收口
- 当前已落表 `strategy_definitions` 与 `strategy_schedules`
- GateE-1.1 已完成策略定义 / 注册 / 启停最小模型
- 下一步进入 GateE-1.2

---

## 3. 当前阶段正式边界

### 3.1 GateE 主目标

- 策略定义与注册
- 策略启停与配置快照
- 手动 trigger 与 scheduled trigger
- 运行窗口、去重、串行化
- `strategyRunId` 到执行域的血缘贯通
- 执行结果回传与查询

### 3.2 GateE-0

只做：

- Binance background reconcile 噪音治理
- schema / metadata / contract 收口
- 返回模型一致性收尾

### 3.3 不做

- GateF 研究 / 回测设计
- 新交易所扩张
- 大而全插件体系

---

## 4. 当前阶段顺序

1. GateE-DOC-2：开工基线收口
2. GateE-0.1：Binance background reconcile 噪音治理
3. GateE-0.2：schema / metadata / contract 收口
4. GateE-0.3：adapter 返回模型一致性
5. GateE-1.1：策略定义与注册模型
6. GateE-1.2：策略运行主链与手动 trigger
7. GateE-2.1：调度任务与计划配置
8. GateE-2.2：窗口 / 去重 / 串行化
9. GateE-2.3：运行结果回传与查询面

---

## 5. 当前入口跳转

- GateE 主文档：`docs/gates/gate-e/README.md`
- GateE checklist：`docs/gates/gate-e/GATE_E_CHECKLIST.md`
- GateE 契约：`docs/gates/gate-e/CONTRACTS.md`
- GateE schema：`docs/gates/gate-e/DB_SCHEMA.md`
- GateE 状态机：`docs/gates/gate-e/STATE_MACHINE.md`
- GateE 测试清单：`docs/gates/gate-e/TEST_CASES.md`
- GateE PR 拆分：`docs/gates/gate-e/PR_SPLIT_PLAN.md`
- GateE 工作台账：`docs/gates/gate-e/WORK.md`
