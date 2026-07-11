# GateU Frontend Evidence Summary

> GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）

## UI Scope

- 既有 `frontend/src/pages/strategies/StrategyValidationPage.tsx` 增加“运行证据总览”。
- client 仅调用 `GET /api/validation-operations/runtime-evidence/overview`。
- TanStack Query key 为 `['validation-operations', 'runtime-evidence', 'overview']`。
- “刷新总览”只调用该 query 的 `refetch()`；不会分别执行五来源写侧刷新。

## State Coverage

页面展示 aggregate availability/freshness、来源计数、固定五来源和四项 safety flags。No-file Artifact Preview 保持可见，并以 `UNAVAILABLE / UNKNOWN` 告知证据缺口；不会伪装成 artifact 已接入。

## Verification And Boundary

- Frontend build：`PASS`。
- Targeted Playwright：合计 `4 passed`。
- E2E 覆盖五来源、fail-closed 状态、No-file source 与手动 refetch 的单次 aggregate GET。
- 未新增 route、上传/导入、文件路径输入、Python 执行、start/stop/execute/trade、下单、撤单或其他写侧 UI。
