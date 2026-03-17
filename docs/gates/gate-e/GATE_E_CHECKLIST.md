# GATE_E_CHECKLIST

> GateE 名称：**策略接入与调度编排（Strategy Integration & Scheduling Orchestration）**  
> 当前状态：**文档可开工，代码未启动**  
> 状态约定：`[x] 已完成`、`[~] 部分完成 / 当前推进中`、`[ ] 未开始`。

---

## 0. 文档基线

- [x] GateE README 已建立并按当前代码重写
- [x] GateE checklist 已建立
- [x] GateE PR split plan 已建立
- [x] GateE WORK 已建立并补充 2026-03-16 文档批记录
- [x] GateE DECISIONS 已建立并补强
- [x] GateE candidates 已建立
- [x] GateE 架构摘要已建立
- [x] GateE 模块摘要已建立
- [x] GateE CONTRACTS 已建立
- [x] GateE DB_SCHEMA 已建立
- [x] GateE STATE_MACHINE 已建立
- [x] GateE TEST_CASES 已建立
- [x] GateE SOURCES 已建立
- [x] GateE EVOLUTION_RULES 已建立
- [x] GateE ADR 说明已建立

---

## 1. 基于当前代码的起点确认

- [x] 已确认 `strategy_runs` / `orders.strategy_run_id` 现状
- [x] 已确认 `PlaceOrderRequest / PlaceOrderCommand / AdapterOrderRequest` 的策略字段现状
- [x] 已确认 `StrategyScheduler / NoopStrategyScheduler / GateBDemoStrategyRunner` 的占位现状
- [x] 已确认当前缺少策略注册读写面与调度主链
- [~] `strategyId / strategyRunId` 语义已在文档冻结，尚未代码收口

---

## 2. GateE-0 前置治理

- [ ] GateE-0.1 Binance background reconcile 噪音治理
- [ ] GateE-0.2 schema / metadata 收口
- [ ] GateE-0.3 返回模型一致性收尾

说明：
- GateE-0 只是前置治理，不代表 GateE 全部内容。
- GateE-0 完成后，必须明确给出“哪些遗留项不再阻塞 GateE 主体”。

---

## 3. GateE-1 策略接入

- [ ] 冻结策略定义 / 注册契约
- [ ] 冻结 `strategyId / strategyRunId` 语义并完成代码收口
- [ ] 建立策略注册最小存储模型
- [ ] 建立策略启停 / 人工触发最小入口
- [ ] 建立策略运行状态最小模型
- [ ] 建立策略与执行链路边界文档化

---

## 4. GateE-2 调度编排

- [ ] 建立调度编排主链
- [ ] 建立运行窗口控制
- [ ] 建立去重 / 串行化规则
- [ ] 建立运行结果回传与读侧查询
- [ ] 建立最小调度编排验收样本

---

## 5. 工程门禁

- [ ] `mvn -q -f backend/pom.xml test` 通过（GateE 相关变更后复核）
- [ ] `mvn -q -f backend/pom.xml verify` 通过（GateE 相关变更后复核）
- [ ] 文档与 current 入口一致
- [ ] 不破坏 GateD 冻结能力
- [ ] 不把 GateBDemoStrategyRunner 偷偷升级成正式 GateE 主链

---

## 6. 冻结条件

- [ ] GateE-0 前置治理收口
- [ ] GateE-1 策略接入主链收口
- [ ] GateE-2 调度编排主链收口
- [ ] GateE 文档、工程门禁、验收用例完整
