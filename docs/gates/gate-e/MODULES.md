# GateE MODULES
# GateE 模块摘要

> 本文档只写 GateE 第一阶段涉及的模块和职责变化方向，不展开详细设计。

---

## 1. GateE 第一阶段涉及模块

- `nq-core`
- `nq-scheduler`
- `nq-adapter-api`
- `nq-adapter-binance`
- `nq-infra`
- `nq-ledger`
- `nq-api`
- `nq-app`

---

## 2. 最小职责变化方向

### `nq-core`
- 承接策略接入契约与策略运行状态边界

### `nq-scheduler`
- 承接调度编排主链与运行窗口控制
- 在 GateE-0 中承接 Binance background reconcile 噪音治理

### `nq-adapter-api / nq-adapter-binance`
- 承接返回模型一致性收尾
- 承接 Binance 相关口径收敛

### `nq-infra / nq-ledger / nq-api`
- 承接 schema / metadata 收口与查询面一致性增强

### `nq-app`
- 继续承接阶段入口与最小运行支撑
