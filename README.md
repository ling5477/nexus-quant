# README.md
# NexusQuant（nexus-quant）

NexusQuant 是面向数字资产交易场景的量化系统工程骨架，核心原则是：  
**幂等（client_order_id） + 严格状态机 + 事实链（event_store） + 账本（ledger_entries） + 可审计 + 可恢复 + 可观测**。

> 当前处于哪个阶段、当前入口代表什么，以 `docs/current/` 为准。  
> 历史 Gate 冻结卷宗位于 `docs/gates/gate-*/`，只读参考。

---

## 1. 当前阶段

当前阶段：**GateG（前端控制台与联调）**

当前状态：

- GateD 已冻结
- GateE 已冻结
- GateF 已完成并冻结
- GateG-DOC-1 已完成
- GateG-1 尚未开工

当前仓库入口代表：

- GateF 已完成交接
- GateG 主卷宗已建立
- 后续按 GateG PR_SPLIT_PLAN 开工前端与联调批次

---

## 2. 最近已冻结 Gate

最近已冻结 Gate：**GateF（研究 / 回测 / 评估能力）**

GateF 最终完成能力：

- 研究配置管理
- 回测配置管理
- 回测运行主链
- sim_orders / sim_trades / sim_positions / sim_pnl_snapshots
- evaluation / publish 查询与最小写链
- GateG 前端联调所需的研究 / 回测查询面

GateF 冻结后保留的结论：

- 现有表结构不是 GateG 开工前置阻塞
- `/api/**`、认证链、研究 / 回测 / 交易验证查询面已达到 GateG 首批联调最低可用标准
- GateG 不以前置数据库大改为条件，只在联调中补最小前端向接口

---

## 3. 当前入口

- 当前阶段入口：`docs/current/README.md`
- 当前阶段 checklist：`docs/current/GATE_CHECKLIST.md`
- GateG 输入清单：`docs/current/GATEG_INPUTS.md`
- GateG 主卷宗：`docs/gates/gate-g/README.md`
- GateF 冻结卷宗：`docs/gates/gate-f/README.md`

---

## 4. 文档结构

### 当前入口

- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`
- `docs/current/WORK_TEMPLATE.md`
- `docs/current/GATEG_INPUTS.md`
- `docs/gates/gate-g/README.md`

### 最近冻结 Gate（GateF）

- `docs/gates/gate-f/README.md`
- `docs/gates/gate-f/GATE_F_CHECKLIST.md`
- `docs/gates/gate-f/PR_SPLIT_PLAN.md`
- `docs/gates/gate-f/WORK.md`
- `docs/gates/gate-f/DECISIONS.md`
- `docs/gates/gate-f/ARCHITECTURE.md`
- `docs/gates/gate-f/MODULES.md`
- `docs/gates/gate-f/CONTRACTS.md`
- `docs/gates/gate-f/DB_SCHEMA.md`
- `docs/gates/gate-f/STATE_MACHINE.md`
- `docs/gates/gate-f/TEST_CASES.md`
- `docs/gates/gate-f/SOURCES.md`

### 历史冻结 Gate

- `docs/gates/gate-a/`
- `docs/gates/gate-b/`
- `docs/gates/gate-c/`
- `docs/gates/gate-d/`
- `docs/gates/gate-e/`

---

## 5. 当前建议顺序

1. 先阅读 `docs/current/README.md`
2. 再阅读 `docs/current/GATE_CHECKLIST.md`
3. 再阅读 `docs/current/GATEG_INPUTS.md`
4. 开工 GateG 时，以 `docs/gates/gate-g/*` 为当前 Gate 权威卷宗
