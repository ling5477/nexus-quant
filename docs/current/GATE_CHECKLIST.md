# GateE Checklist（当前入口摘要）

> 当前阶段：**GateE（v1.4：策略接入与调度编排）**。  
> 当前状态：**文档可开工，代码未启动**。  
> 本文件只保留 current 级阶段摘要与跳转；GateE 完整卷宗以 `docs/gates/gate-e/*` 为准。  
> 状态约定：`[x] 已完成`、`[~] 当前推进中 / 已有底座但未冻结`、`[ ] 尚未开始`。  
> 状态基线：**截至 2026-03-16 的已实现与已验证事实**。

---

## 0. 上一阶段结论

- [x] GateD 已冻结 / 已完成阶段
- [x] GateD 主阻塞已清零
- [x] GateD 冻结卷宗已固定在 `docs/gates/gate-d/*`
- [x] GateD 后续不再承载 GateE 新内容

---

## 1. 当前阶段摘要

- [x] 当前阶段已切到 GateE
- [x] GateE 主定义已固定为 `v1.4（策略接入与调度编排）`
- [x] GateE 文档基线已完善到可开工版
- [~] `strategy_runs / strategy_run_id / StrategyScheduler` 已存在底座，但语义尚未完全冻结
- [ ] GateE-0 前置治理尚未开始落代码
- [ ] GateE-1 策略接入与注册尚未开始落代码
- [ ] GateE-2 调度编排主链尚未开始落代码

---

## 2. 当前优先级与入口指引

### GateE 文档完善批（已完成）
- [x] README / CHECKLIST / PR split plan / WORK / DECISIONS
- [x] ARCHITECTURE / MODULES
- [x] CONTRACTS / DB_SCHEMA / STATE_MACHINE
- [x] TEST_CASES / SOURCES / EVOLUTION_RULES

### GateE-0（当前优先级）
- [ ] GateE-0.1 Binance background reconcile 噪音治理
- [ ] GateE-0.2 schema / metadata 收口
- [ ] GateE-0.3 返回模型一致性收尾

### GateE 主体（后续）
- [ ] GateE-1：策略接入契约与注册
- [ ] GateE-2：调度编排主链

说明：
- GateE-0 只是前置治理，不得改写 GateE 主目标。
- 当前冻结阻塞不再写成 GateD 项；所有剩余项均归到 GateE 或后续治理批。

---

## 3. 当前代码盘点结论

- [x] `strategy_runs` 已存在，可承接最小运行链路
- [x] `orders.strategy_run_id` 已存在
- [x] `PlaceOrderRequest / PlaceOrderCommand / AdapterOrderRequest` 已预留策略来源字段
- [~] `PlaceOrderCommand.strategyId` 与 `PlaceOrderRequest.strategyRunId` 当前语义存在混用，需要在 GateE 契约文档中冻结
- [~] `StrategyScheduler` 已存在，但仅有 `start/stop/restart` 占位能力
- [~] `GateBDemoStrategyRunner` 可作为历史参考，但不能直接复用为 GateE 正式编排主链
- [ ] 策略注册读写面未建立
- [ ] 策略运行状态机未代码化
- [ ] 调度窗口、去重、串行化规则未建立

---

## 4. 跳转入口

- GateE 卷宗入口：`docs/gates/gate-e/README.md`
- GateE checklist：`docs/gates/gate-e/GATE_E_CHECKLIST.md`
- GateE 契约：`docs/gates/gate-e/CONTRACTS.md`
- GateE 数据模型：`docs/gates/gate-e/DB_SCHEMA.md`
- GateE 状态机：`docs/gates/gate-e/STATE_MACHINE.md`
- GateE 验收：`docs/gates/gate-e/TEST_CASES.md`
- GateE 依据索引：`docs/gates/gate-e/SOURCES.md`
- GateD 冻结证据：`docs/gates/gate-d/FREEZE_SUMMARY.md`
