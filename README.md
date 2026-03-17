# README.md
# NexusQuant（nexus-quant）

NexusQuant 是面向数字资产交易场景的量化系统工程骨架，核心原则是：
**幂等（client_order_id） + 严格状态机 + 事实链（event_store） + 账本（ledger_entries） + 可审计 + 可恢复 + 可观测**。

> 当前处于哪个 Gate、当前 Gate 要做什么，以 `docs/current/` 为准。  
> 历史 Gate 冻结快照位于 `docs/gates/gate-*/`，只读参考，不作为当前实现边界。

---

## 1. 当前阶段

当前阶段：**GateE（v1.4：策略接入与调度编排）**。

GateD 已冻结。GateE 现在进入**文档基线完善阶段**，当前先完成两件事：
- 把 GateE 的边界、契约、状态机、验收口径写实
- 在写实基础上，明确 GateE-0 / GateE-1 / GateE-2 的开工顺序

当前阶段不是把 GateD 再翻炒一遍。GateE 的主目标仍然是：
- 策略接入
- 策略注册与运行状态管理
- 调度编排主链

当前优先顺序：
1. GateE 文档完善批
2. GateE-0 前置治理
3. GateE-1 策略接入
4. GateE-2 调度编排

---

## 2. 文档结构（按 Gate 冻结）

### 当前阶段入口（Source of Truth）
- `docs/current/README.md`：当前 Gate 总览入口
- `docs/current/GATE_CHECKLIST.md`：当前 Gate 入口摘要与状态收口
- `docs/current/WORK_TEMPLATE.md`：当前阶段工作记录模板

### 当前 Gate 权威文档（GateE）
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
- `docs/gates/gate-e/EVOLUTION_RULES.md`
- `docs/gates/gate-e/GATE_E_CANDIDATES.md`
- `docs/gates/gate-e/adr/README.md`

### GateD 冻结卷宗（只读证据）
- `docs/gates/gate-d/FREEZE_SUMMARY.md`
- `docs/gates/gate-d/README.md`
- `docs/gates/gate-d/*`

### 历史 Gate 冻结快照
- `docs/gates/gate-a/`
- `docs/gates/gate-b/`
- `docs/gates/gate-c/`

根级 `docs/*.md` 继续只作导航摘要，不承载当前阶段细节。

---

## 3. 当前实现范围（Scope）

当前阶段在 `backend/` 内推进两层内容：

### GateE-0：前置治理批
- `nq-scheduler`：Binance background reconcile 噪音治理
- `nq-adapter-binance / nq-adapter-api / nq-core / nq-api`：返回模型一致性收尾
- `nq-infra / nq-ledger / nq-api`：schema / metadata 收口

### GateE 主体
- `nq-core`：策略接入契约、策略运行状态、与执行链路边界
- `nq-scheduler`：调度编排主链、运行窗口、触发与串行化约束
- `nq-app / nq-api`：策略注册、启动、暂停、查询的最小入口
- `nq-infra`：GateE 真实需要的最小 schema 迁移

以下目录与能力**不属于当前 Gate 实现范围**：
- `frontend/`
- `research/`
- 生产大基建（Kafka / Debezium / K8s / Grafana 等）
- 合约 / 杠杆 / 期货 / 期权执行域
- 回测 / 因子 / 研究平台

---

## 4. 顶层入口跳转

- 当前入口：`docs/current/README.md`
- GateE 卷宗：`docs/gates/gate-e/README.md`
- GateD 冻结卷宗：`docs/gates/gate-d/FREEZE_SUMMARY.md`

---

## 5. 当前建议顺序

1. GateE 文档完善批
2. GateE-0.1 Binance background reconcile 噪音治理
3. GateE-0.2 schema / metadata 收口
4. GateE-0.3 返回模型一致性收尾
5. GateE-1.1 策略接入契约与注册
6. GateE-1.2 策略运行状态与最小读写面
7. GateE-2.1 调度编排主链
8. GateE-2.2 运行窗口、去重与验收样本
