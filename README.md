# README.md
# NexusQuant（nexus-quant）

NexusQuant 是面向数字资产交易场景的量化系统工程骨架，核心原则是：  
**幂等（client_order_id） + 严格状态机 + 事实链（event_store） + 账本（ledger_entries） + 可审计 + 可恢复 + 可观测**。

> 当前处于哪个阶段、当前入口代表什么，以 `docs/current/` 为准。  
> 历史 Gate 冻结卷宗位于 `docs/gates/gate-*/`，只读参考。

---

## 1. 当前阶段

当前阶段：**GateF（研究 / 回测 / 评估能力）**

当前状态：

- GateD 已冻结
- GateE 已完成并冻结
- GateF-DOC-1 已完成

当前仓库入口不再表示“GateE 开工中”，而是表示：

- GateE 已可归档与交接
- GateF 主卷宗已建立，后续可按 PR_SPLIT_PLAN 开工

---

## 2. 最近已冻结 Gate

最近已冻结 Gate：**GateE（策略接入与调度编排）**

GateE 最终完成能力：

- 策略定义管理
- 手动 trigger / `strategyRunId` 主链
- schedule config / `scanOnce`
- `window / dedup / serialization`
- run 结果查询面

GateE 已知遗留但不再继续挂在活跃状态里的事项：

- `PlaceOrderCommand.strategyId` 兼容债务
- `trigger_id` 事实表未落
- `ledger / risk / event / audit` 未形成稳定 run 级直接聚合
- 多实例严格一致 dedup / serialization 未解决

---

## 3. 当前入口

- 当前阶段入口：`docs/current/README.md`
- 当前阶段 checklist：`docs/current/GATE_CHECKLIST.md`
- GateF 输入清单：`docs/current/GATEF_INPUTS.md`
- GateF 主卷宗：`docs/gates/gate-f/README.md`
- GateE 冻结卷宗：`docs/gates/gate-e/README.md`

---

## 4. 文档结构

### 当前入口

- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`
- `docs/current/WORK_TEMPLATE.md`
- `docs/current/GATEF_INPUTS.md`
- `docs/gates/gate-f/README.md`

### 最近冻结 Gate（GateE）

- `docs/gates/gate-e/README.md`
- `docs/gates/gate-e/GATE_E_CHECKLIST.md`
- `docs/gates/gate-e/PR_SPLIT_PLAN.md`
- `docs/gates/gate-e/WORK.md`
- `docs/gates/gate-e/DECISIONS.md`
- `docs/gates/gate-e/ARCHITECTURE.md`
- `docs/gates/gate-e/MODULES.md`
- `docs/gates/gate-e/CONTRACTS.md`
- `docs/gates/gate-e/DB_SCHEMA.md`
- `docs/gates/gate-e/STATE_MACHINE.md`
- `docs/gates/gate-e/TEST_CASES.md`
- `docs/gates/gate-e/SOURCES.md`

### 历史冻结 Gate

- `docs/gates/gate-a/`
- `docs/gates/gate-b/`
- `docs/gates/gate-c/`
- `docs/gates/gate-d/`

---

## 5. 当前建议顺序

1. 先阅读 `docs/current/README.md`
2. 再阅读 `docs/current/GATE_CHECKLIST.md`
3. 若需要理解最近完成的执行与编排事实，再阅读 `docs/gates/gate-e/*`
4. GateF 当前已完成文档基线，后续应按 `docs/gates/gate-f/PR_SPLIT_PLAN.md` 分批实现
