# ADR-001：统一执行入口

## 决策
在 GateD 中，将 `nq-core` 定义为统一执行域入口，所有 place / cancel / acknowledge / reject / query-confirm / trade-report 统一经由 core 协调。

## 原因
- 避免 scheduler、controller、adapter 各自推进状态
- 避免状态机分叉
- 便于 trace、audit、idempotency 收口

## 影响
- `OrderCommandService` 需收敛职责
- scheduler 与 app 不再直接推进订单状态

