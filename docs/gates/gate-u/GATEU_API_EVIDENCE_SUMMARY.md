# GateU API Evidence Summary

> GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）

## Endpoint

```text
GET /api/validation-operations/runtime-evidence/overview
```

该 endpoint 无 request body、无 path/query mutation 参数，返回 aggregate `evidenceMetadata`、五来源计数、固定来源列表与 `traceId`。

## Response Semantics

- aggregate source：`LOCAL_VALIDATION_OPERATIONS_RUNTIME_EVIDENCE`。
- sources：固定五项，包含 `EVALUATION_ARTIFACT_PREVIEW`，不得过滤 unavailable source。
- No-file 来源当前为 `LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW / UNAVAILABLE / UNKNOWN`。
- `diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`。
- response 不含 `canTrade`、`tradingReady`、`liveReady`、`authorizedForTrading`、credential material 或真实订单字段。

## Controller Evidence

Controller test 验证只有一个 `@GetMapping`，不存在 `POST / PATCH / PUT / DELETE` mapping；验证 trace header、五来源、No-file unavailable 状态、四项 safety flags 与 forbidden fields guard。该 API 是 read-only diagnostic evidence，不是 trading authorization。
