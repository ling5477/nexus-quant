# GateU Runtime Boundary Summary

> GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）

GateU 名称中的 runtime evidence 指“运行相关证据的只读聚合视图”，不表示新增或启动 runtime。

## Implemented

- request-time 读取五个既有 read-model metadata source。
- 计算 aggregate availability/freshness 与计数。
- 通过单一 GET endpoint 和现有页面展示诊断结果。

## Not Implemented Or Started

- scheduler、runner、background job、refresh loop、runtime state machine。
- replay execution、incident creation、operator action persistence。
- internal HTTP fan-out、private endpoint、credential access。
- Shadow trading、LIVE trading、real provider、RealClient、真实下单/撤单。

## Failure Mode

来源缺失、不完整、unknown、stale、future timestamp 或异常均不得提升为 healthy runtime：聚合采用 fail-closed 语义；source exception 不产生 synthetic success。
