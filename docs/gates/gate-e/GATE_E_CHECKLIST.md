# GATE_E_CHECKLIST

GateE 名称：**策略接入与调度编排**

冻结状态：**GateE 已完成并冻结**

当前状态：**GateE-0.x、GateE-1.x、GateE-2.x 已完成，GateE 已冻结**

状态约定：

- `[x]` 已完成
- `[~]` 已冻结文档语义、代码待收口
- `[ ]` 未开始

---

## 0. 开工基线

- [x] 已核对 `docs/current/*`、`docs/gates/gate-d/*`、`docs/gates/gate-e/*`
- [x] 已核对 `strategy_runs`
- [x] 已核对 `orders.strategy_run_id`
- [x] 已核对 `StrategyScheduler / NoopStrategyScheduler / GateBDemoStrategyRunner`
- [x] 已核对 `PlaceOrderRequest / PlaceOrderCommand / AdapterOrderRequest`
- [x] 已冻结 GateE 主链、对象语义、状态机、测试清单、PR 顺序

---

## 1. GateE-0 前置治理

- [x] GateE-0.1 Binance background reconcile 噪音治理
- [x] GateE-0.2 schema / metadata / contract 收口
- [x] GateE-0.3 adapter 返回模型一致性

---

## 2. GateE-1 策略接入

- [x] 新增策略定义模型
- [x] 新增策略注册入口
- [x] 新增策略启停入口
- [x] 新增手动 trigger
- [~] `strategyId / strategyRunId` 最终代码收口移交后续执行域契约清理，不再作为 GateE 冻结阻塞项
- [x] 建立定义级最小查询与启停能力
- [x] 建立运行级最小状态与 trigger 返回

---

## 3. GateE-2 调度编排

- [x] 新增调度作业模型
- [x] 新增 scheduled trigger
- [x] 新增窗口控制
- [x] 新增 dedupKey 去重
- [x] 新增同策略串行化保护
- [x] 新增运行结果查询与反查执行结果

---

## 4. 当前已确认的兼容债务

- [~] `PlaceOrderCommand.strategyId` 语义与运行血缘不一致，保留为冻结遗留债务
- [x] `strategy_definitions` / `strategy_schedules` 已落表
- [~] `trigger_id` 事实表未落
- [~] `ledger / risk / event / audit` 未形成稳定 run 级完全聚合
- [~] 多实例严格一致 dedup / serialization 未解决

---

## 5. 门禁

- [x] current 入口与 GateE 卷宗口径一致
- [x] GateD / GateE / GateF 边界已写死
- [x] `strategyId` 与 `strategyRunId` 已分义
- [x] GateE 相关实现已复跑 `mvn -q -f backend/pom.xml test`
- [~] `mvn -q -f backend/pom.xml verify` 已在后续 Gate 复跑，但不是 GateE 冻结时的单独门禁项
- [x] 所有主实现批次已回填 `WORK.md` 与 `DECISIONS.md`
