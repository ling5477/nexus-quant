# GateE README
# GateE（v1.4：策略接入与调度编排）

当前状态：**文档可开工，代码未启动**。

GateE 不是 GateD 的返工阶段。GateD 已冻结，GateE 的主目标固定为：**策略接入与调度编排**。

---

## 1. GateE 与 GateD 的边界

### GateD 已完成
- 统一执行入口与统一状态推进
- pre-trade 风控规则链
- Paper / OKX / Binance 最小执行闭环
- trade / ledger / position / account 投影联动
- reconcile / recovery / degrade / query-confirm 收口

### GateE 负责
- 策略定义、注册、启停与配置边界
- 策略运行状态管理
- 调度编排主链
- 运行窗口、去重、串行化、手动触发与回传
- 为上述主体开路的前置治理

### GateE 不负责
- 回写 GateD 新内容
- 把前置治理扩写成 GateE 主目标
- 把 scheduler 再膨胀成新的业务核心
- 提前展开回测 / 研究平台

---

## 2. 基于当前项目文件的真实起点

当前仓库里已经有一些 GateE 相关前置底座：

### 2.1 已有底座
- `backend/nq-infra/.../V1__init.sql` 已包含 `strategy_runs`
- `orders.strategy_run_id` 与 `idx_orders_strategy_run_id` 已存在
- `PlaceOrderRequest`、`PlaceOrderCommand`、`AdapterOrderRequest` 已带策略来源字段
- `StrategyScheduler` / `NoopStrategyScheduler` 已存在
- `GateBDemoStrategyRunner` 证明过“策略触发 -> 下单”最小链路能跑通

### 2.2 当前缺口
- 没有“策略定义 / 注册 / 调度配置”的正式模型
- `strategyId` 与 `strategyRunId` 在 contracts / core 当前存在语义混用
- 没有 GateE 独立的策略状态机与运行状态机
- 没有策略注册、启停、运行查询的正式 API
- 没有调度窗口、去重、串行化、人工触发、结果回传的正式编排主链

### 2.3 结论
当前不是“从零开始”，也绝对不是“已经差不多了”。
现在最该做的，是把这堆半成品语义先压成一套一致的 GateE 文档基线。

---

## 3. 当前阶段结构

### GateE-0：前置治理批
只做：
- Binance background reconcile 噪音治理
- schema / metadata 收口
- 返回模型一致性收尾

### GateE-1：策略接入与注册
- 冻结策略定义与注册契约
- 冻结 `strategyId / strategyRunId` 语义
- 建立策略运行状态最小模型
- 建立策略注册、启停、人工触发最小入口

### GateE-2：调度编排主链
- 建立调度编排主链
- 建立运行窗口控制、去重、串行化规则
- 建立策略运行结果回传与读侧查询
- 建立最小验收样本

说明：
- GateE-0 只是前置治理，不等于 GateE 主体。
- GateE 主定义始终是“策略接入与调度编排”。

---

## 4. 当前建议顺序

- Top 1：GateE 文档完善批
- Top 2：Binance background reconcile 噪音治理
- Top 3：schema / metadata 收口
- Top 4：返回模型一致性收尾
- Top 5：策略接入契约与注册
- Top 6：调度编排主链

排序原因：
- Top 1 先做，是因为当前代码里已经有策略相关占位语义，先收口才能避免继续漂移。
- Top 2 ~ Top 4 是清场项，优先级高，但不能吃掉 GateE 主体。
- Top 5 / Top 6 才是 GateE 的真正主菜。

---

## 5. 当前文档入口

- GateE checklist：`docs/gates/gate-e/GATE_E_CHECKLIST.md`
- GateE 拆批计划：`docs/gates/gate-e/PR_SPLIT_PLAN.md`
- GateE 工作记录：`docs/gates/gate-e/WORK.md`
- GateE 决策：`docs/gates/gate-e/DECISIONS.md`
- GateE 架构摘要：`docs/gates/gate-e/ARCHITECTURE.md`
- GateE 模块摘要：`docs/gates/gate-e/MODULES.md`
- GateE 契约：`docs/gates/gate-e/CONTRACTS.md`
- GateE 数据模型：`docs/gates/gate-e/DB_SCHEMA.md`
- GateE 状态机：`docs/gates/gate-e/STATE_MACHINE.md`
- GateE 验收用例：`docs/gates/gate-e/TEST_CASES.md`
- GateE 依据索引：`docs/gates/gate-e/SOURCES.md`
- GateE 演化规则：`docs/gates/gate-e/EVOLUTION_RULES.md`
- GateE 候选清单：`docs/gates/gate-e/GATE_E_CANDIDATES.md`
- GateE ADR 说明：`docs/gates/gate-e/adr/README.md`
- GateD 冻结证据：`docs/gates/gate-d/FREEZE_SUMMARY.md`
