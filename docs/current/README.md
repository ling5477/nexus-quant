# docs/current/README.md
# Current Gate（当前阶段入口）

当前阶段：**GateE（v1.4：策略接入与调度编排）**。

当前状态：**已启动文档基线完善，业务实现未启动**。

GateD 已冻结。当前 source of truth 已切换到 GateE，GateD 卷宗只作为冻结证据保留，不再承载后续阶段新增内容。

---

## 1. 当前阶段结论

当前主战场已经切到 GateE，当前先把“要做什么、现在有什么、缺什么”写死，避免一头扎进代码后再补锅。

### 1.1 当前阶段摘要（截至 2026-03-16）

- [x] GateD 已冻结
- [x] GateE 主定义已固定为 `v1.4（策略接入与调度编排）`
- [x] GateE 文档基线已从“骨架”提升到“可开工版”
- [x] GateE-0 / GateE-1 / GateE-2 的边界已重新梳理
- [ ] GateE 业务代码实现尚未开始

### 1.2 基于当前项目文件的现状盘点

当前代码里已经有一些 GateE 相关底座，但还不是完整 GateE：

- 已有 `strategy_runs` 表，可记录最小运行链路
- `orders.strategy_run_id` 已存在，订单与策略运行的血缘可追踪
- `PlaceOrderRequest / PlaceOrderCommand / AdapterOrderRequest` 已预留 `strategyRunId / strategyId / source`
- `StrategyScheduler` 与 `NoopStrategyScheduler` 已存在，但只是占位接口
- `GateBDemoStrategyRunner` 仍是旧阶段演示触发器，不能直接当成 GateE 正式编排主链
- `nq-api` 还没有策略注册、策略运行、调度窗口的读侧接口
- `nq-infra` 还没有 GateE 专属 migration，当前必须先冻结语义，再决定是否新增 `V5+`

一句话：地基里已经埋了几根钢筋，但房子还没立起来。现在先把图纸画准。

---

## 2. 当前阶段目标

### 2.1 GateE 主目标
- 策略接入
- 策略注册与运行状态管理
- 调度编排主链

### 2.2 GateE-0 前置治理
只做：
- Binance background reconcile 噪音治理
- schema / metadata 收口
- 返回模型一致性收尾

### 2.3 明确边界
- GateE-0 不是 GateE 主体
- GateE 主体不是“治理收尾阶段”
- GateE 不能回写 GateD 新内容
- GateE 的策略状态机不能和 GateD 的订单状态机搅成一锅面

---

## 3. 当前优先级

### Top 1
- GateE 文档完善批

### Top 2
- Binance background reconcile 噪音治理

### Top 3
- schema / metadata 收口

### Top 4
- 返回模型一致性收尾

说明：
- Top 1 先做，是因为当前代码里已经出现 `strategyId / strategyRunId / StrategyScheduler / strategy_runs` 这些半成品语义；不先收口，后面越写越歪。
- Top 2 ~ Top 4 全部属于 GateE-0，只是清场，不是 GateE 的定义本体。

---

## 4. 当前入口与跳转

- GateE 卷宗入口：`docs/gates/gate-e/README.md`
- GateE checklist：`docs/gates/gate-e/GATE_E_CHECKLIST.md`
- GateE 契约：`docs/gates/gate-e/CONTRACTS.md`
- GateE 数据模型：`docs/gates/gate-e/DB_SCHEMA.md`
- GateE 状态机：`docs/gates/gate-e/STATE_MACHINE.md`
- GateE 验收用例：`docs/gates/gate-e/TEST_CASES.md`
- GateE 依据索引：`docs/gates/gate-e/SOURCES.md`
- GateD 冻结证据：`docs/gates/gate-d/FREEZE_SUMMARY.md`

---

## 5. 当前执行原则

1. 先补 GateE 文档，再改代码
2. 先解决语义歧义，再上 migration
3. scheduler 只负责编排，不接管执行域真业务
4. 订单状态机继续以 GateD 为准；GateE 新增的是策略定义/策略运行/调度状态机
5. 若发现历史遗留项，先判断它属于 GateE-0、GateE 主体，还是仅为 GateD 冻结证据
