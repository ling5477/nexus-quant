# GateE ARCHITECTURE
# GateE 阶段架构摘要

> 本文档是 GateE 阶段架构摘要，不是最终 source of truth 细节文档。

---

## 1. GateE 预计触达的模块边界

- `nq-core`：策略接入契约、策略运行状态、与执行链路的边界
- `nq-scheduler`：调度编排主链、运行窗口与触发协调
- `nq-adapter-api / nq-adapter-binance`：GateE-0 返回模型与 Binance reconcile 噪音治理相关边界
- `nq-infra / nq-ledger / nq-api`：schema / metadata 与查询面收口
- `nq-app`：阶段入口与最小运行支撑

---

## 2. Top 1 / Top 2 波及模块

### Top 1：Binance background reconcile 噪音治理
- 预计波及：`nq-scheduler`、`nq-adapter-binance`、`nq-core`

### Top 2：schema / metadata 收口
- 预计波及：`nq-infra`、`nq-ledger`、`nq-api`

---

## 3. 说明

- GateE-0 只处理前置治理，不展开策略接入与调度编排最终形态。
- 更细粒度设计等对应实现批次再建立专门文档。
