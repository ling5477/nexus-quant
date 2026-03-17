# Modules（GateE 导航摘要）

> Top-Level Navigation Notice
> - 本文件是根级导航摘要，用于帮助快速定位 GateE 模块分工，不是当前阶段的 Source of Truth。
> - 当前阶段的模块职责事实以 `docs/current/*`、`docs/gates/gate-e/MODULES.md` 为准。

## 1. backend 模块职责

### nq-core
GateE 负责：
- 策略触发到执行请求的语义收口
- `strategyRunId` 与执行链路的血缘传递
- 继续复用 GateD 订单状态推进能力

### nq-scheduler
GateE 负责：
- 策略调度编排主链
- 运行窗口控制、去重、串行化
- GateE-0 的 Binance background reconcile 噪音治理

### nq-adapter-api / nq-adapter-okx / nq-adapter-binance
GateE 负责：
- 返回模型一致性收尾
- 继续隔离交易所方言，不把脏语义返流到 core / scheduler

### nq-infra
GateE 负责：
- GateE 真正需要的 migration
- strategy / schedule / run 数据落库基础设施

### nq-api
GateE 负责：
- 策略注册、运行、查询的最小读侧
- schema / metadata 查询口径收口

### nq-app
GateE 负责：
- 暴露阶段验收入口
- 绑定 GateE feature gate 与 profile

## 2. GateE 不作为主改对象
- nq-auth
- nq-security
- nq-gateway
- frontend
- research
