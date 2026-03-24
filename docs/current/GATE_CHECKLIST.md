# GateE Checklist（当前入口摘要）

当前阶段：**GateE（v1.4：策略接入与调度编排）**

当前状态：**开工基线已完成，后续按 GateE-0 / GateE-1 / GateE-2 批次推进**

---

## 0. 阶段切换

- [x] GateD 已冻结
- [x] current 入口已切到 GateE
- [x] GateD 后续不再承载 GateE 新内容

---

## 1. 当前基线

- [x] 已检查 GateE 相关文档与真实代码落点
- [x] 已明确 GateE / GateE-0 / GateF 边界
- [x] 已明确 `strategyId / strategyRunId / requestId / dedupKey`
- [x] 已明确 `StrategySignal -> ExecutionRequest -> Order/Trade -> Ledger/Position -> StrategyRunResult`
- [x] 已明确 manual trigger / scheduled trigger / recovery retry 的边界
- [x] 已落表 `strategy_definitions` / `strategy_schedules`

---

## 2. 后续实施顺序

- [x] GateE-0.1 Binance background reconcile 噪音治理
- [x] GateE-0.2 schema / metadata / contract 收口
- [x] GateE-0.3 adapter 返回模型一致性
- [x] GateE-1.1 策略定义与注册模型
- [x] GateE-1.2 策略运行主链与手动 trigger
- [x] GateE-2.1 调度任务与计划配置
- [ ] GateE-2.2 窗口 / 去重 / 串行化
- [ ] GateE-2.3 运行结果回传与查询面

---

## 3. 当前风险

- [~] `PlaceOrderCommand.strategyId` 仍需收口
- [x] 调度对象已具备正式持久化模型
- [~] 运行结果查询面仍未实现

---

## 4. 使用指引

- GateE 当前事实以 `docs/current/*` 与 `docs/gates/gate-e/*` 为准
- 详细实施顺序看 `docs/gates/gate-e/PR_SPLIT_PLAN.md`
- 详细对象语义看 `docs/gates/gate-e/CONTRACTS.md`
