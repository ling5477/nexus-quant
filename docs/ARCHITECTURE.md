# Architecture（GateD 对齐版）

当前主线架构已经从 GateC 的“交易所接入”推进到 GateD 的“统一执行闭环”。

## GateD 核心链路

策略意图 / 手工命令
-> 统一执行入口（nq-core）
-> pre-trade 风控（nq-risk）
-> adapter 路由（nq-adapter-api）
-> venue 执行（okx / binance / paper）
-> 回执 / 成交归一事件
-> 状态机推进（nq-core）
-> trade / ledger / position / account 投影（nq-ledger）
-> reconcile / recovery / degrade（nq-scheduler）
-> audit / event_store / metrics（nq-observability）

## GateD 关键原则
- WS 只做加速，不做唯一事实来源
- REST reconcile 是长期兜底，不是一次性补丁
- 风控前置拦截必须先于真实执行
- 状态推进统一归口，禁止多点推进
- 投影可重建，事实链不可丢

