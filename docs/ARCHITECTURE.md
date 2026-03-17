# Architecture（GateE 导航摘要）

> Top-Level Navigation Notice
> - 本文件是根级导航摘要，用于帮助快速理解 GateE 架构全貌，不是当前阶段的 Source of Truth。
> - 当前阶段的权威入口是 `docs/current/*` 与 `docs/gates/gate-e/*`。
> - 若本文件与当前 Gate 文档不一致，以后者为准。

当前主线架构已经从 GateD 的“统一执行闭环”推进到 GateE 的“策略接入与调度编排”。

## GateE 核心链路

策略定义 / 启停命令 / 调度触发
-> 策略注册与运行管理（GateE）
-> 运行窗口判定 / 去重 / 串行化（nq-scheduler）
-> 下单意图组装（nq-core / nq-contracts）
-> pre-trade 风控（nq-risk）
-> adapter 路由（nq-adapter-api）
-> venue 执行（okx / binance / paper）
-> 订单 / 成交 / 账本 / 持仓 / 账户闭环（GateD 既有能力）
-> 策略运行结果回传与状态收口（GateE）
-> audit / event_store / metrics（nq-observability）

## GateE 关键原则
- GateE 复用 GateD 执行闭环，不重写执行域
- 策略状态机与订单状态机分层
- scheduler 只做编排，不做 venue 业务分支
- `strategyId` 与 `strategyRunId` 必须分义，不再混用
- schema 只按真实需求演化，不制造空 migration
