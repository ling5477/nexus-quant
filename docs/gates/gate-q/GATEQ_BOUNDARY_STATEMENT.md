# GateQ Boundary Statement

本文是 GateQ release archive 的边界声明。GateQ `FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `TAGGED`（已打 tag）只冻结策略验证、Paper / Shadow 只读对照、Shadow Live no-side-effect preview、Python artifact binding preview contract 与前端证据展示基线。

## Positive scope

- GateQ-0 planning 已完成。
- GateQ-1 Strategy Evaluation Gate read-only baseline 已完成。
- GateQ-2 Paper vs Shadow Comparison read-only baseline 已完成。
- GateQ-3 Shadow Live no-side-effect preview skeleton 已完成。
- GateQ-4 Python Evaluation Artifact Binding Preview Contract 已完成。
- GateQ-5 Frontend Paper / Shadow Comparison View 已完成。
- GateQ-6 Strategy Lifecycle Trace View Enhancement 已完成。
- GateQ freeze readiness review 已通过，P0/P1=0。
- GateQ freeze closeout 已通过，final state 为 `FROZEN / ACCEPTED`。
- GateQ release archive 完成后，release tag 为 `nq-gateq-freeze`。

## Negative boundary

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED`（未开始）/ mock-test-support only where applicable。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow Live trading：`NOT ENABLED`（未启用）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。

## Forbidden claims

不得把 GateQ archive 或任何 GateQ evidence 写成：

- LIVE ready。
- Shadow Live trading enabled。
- real provider enabled。
- private trading enabled。
- real permission probe enabled。
- trade approved。
- Python ML ready。
- Python live execution ready。
- DH integrated。
- AI started。
- Integration-1 runtime started。

## Operational restrictions

GateQ archive 不允许：

- 调用真实交易所。
- 读取 credential material、API key、secret、passphrase、token、cookie 或 private key。
- 下单、撤单、转账、提现。
- 启动 Shadow Live runner。
- 创建 shadow run。
- 写真实账户、资金、订单或 ledger 状态。
- 新增 API、migration、前端页面、测试、CI workflow 或业务能力。
- 启动 GateR implementation。

## Next stage rule

下一阶段只能是 `GateR PLAN / NOT STARTED`（GateR 规划 / 未开始）。GateR planning 必须另起任务，重新声明目标、允许文件、禁止范围、验证矩阵、rollback、no-LIVE / no-AI / no-DH / no-real-provider / no-private-trading / no-credential 边界。GateQ release archive 不得被解释为 GateR implementation 已启动。
