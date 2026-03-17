# GateE MODULES
# GateE 模块摘要

> 本文档按“当前已有资产 + GateE 责任变化”来写，不搞空中楼阁。

---

## 1. GateE 当前涉及模块

- `nq-core`
- `nq-scheduler`
- `nq-adapter-api`
- `nq-adapter-binance`
- `nq-adapter-okx`
- `nq-infra`
- `nq-ledger`
- `nq-api`
- `nq-app`
- `nq-contracts`

---

## 2. 最小职责变化方向

### `nq-core`
当前已有：
- `PlaceOrderRequest`
- `OrderCommandService`
- `ExecutionCommandMapper`

GateE 负责：
- 冻结策略运行与执行请求的边界
- 统一 `strategyRunId` 的输入语义
- 保证策略层不会绕开 GateD 执行闭环

### `nq-scheduler`
当前已有：
- `StrategyScheduler`
- `NoopStrategyScheduler`
- `GateBDemoStrategyRunner`
- reconcile / recovery / ws degrade 相关服务

GateE 负责：
- 承接调度编排主链与运行窗口控制
- 承接 GateE-0 中的 Binance background reconcile 噪音治理
- 明确把旧 demo runner 与正式编排主链切开

### `nq-adapter-api / nq-adapter-binance / nq-adapter-okx`
当前已有：
- 统一 adapter 请求模型
- venue 映射与回执收口能力

GateE 负责：
- 承接返回模型一致性收尾
- 确保上层策略编排不需要感知 venue 方言

### `nq-infra`
当前已有：
- `V1 -> V4` 迁移基线
- `strategy_runs` 与 orders/trades 相关表

GateE 负责：
- 承接真实需要的 GateE migration
- 不允许凭感觉空造 `V5__whatever.sql`

### `nq-ledger`
当前已有：
- Trade / Ledger / Position / Account 投影闭环

GateE 负责：
- 保持策略运行与执行结果之间的血缘可追踪
- 配合 schema / metadata 收口

### `nq-api`
当前已有：
- order / trade / position / account 最小查询视图

GateE 负责：
- 新增策略定义 / 运行 / 调度结果的读侧
- 收敛 metadata 查询口径

### `nq-app`
当前已有：
- Spring Boot 启动入口
- GateD 验收入口与 profile 装配

GateE 负责：
- 暴露策略注册、启停、触发、查询的最小入口
- 增加 GateE feature gate 与验收脚本入口

### `nq-contracts`
当前已有：
- `PlaceOrderCommand`
- `OrderCreated` 等事件 DTO

GateE 负责：
- 冻结策略定义 / 策略运行 / 调度触发相关契约
- 收口 `strategyId / strategyRunId` 语义
