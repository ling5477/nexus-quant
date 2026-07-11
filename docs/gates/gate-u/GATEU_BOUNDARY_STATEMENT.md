# GateU Boundary Statement

> GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）

## Scope

GateU 是 NQ-only 的 read-model evidence metadata 与只读 aggregate。它不授权或启动 NQ 之外的系统集成，也不扩大交易能力。

## Fixed Safety Flags

```text
diagnosticOnly=true
noSideEffect=true
notTradingAuthorization=true
liveDisabled=true
```

## Explicit Boundaries

- LIVE：`DISABLED`（关闭）。
- Shadow trading：`NOT ENABLED`（未启用）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- GateV：`NOT STARTED`（未开始）。
- 不读取 credentials，不新增 private endpoint，不新增真实交易 side effect。

`AVAILABLE`、`FRESH`、review recommendation 或页面状态都不是交易授权、LIVE readiness、production readiness 或自动执行许可。
