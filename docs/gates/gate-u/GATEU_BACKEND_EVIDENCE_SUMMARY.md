# GateU Backend Evidence Summary

> GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）

## Backend Baseline

- `ReadModelEvidenceMetadata` 定义 source、availability、lastCalculatedAt、freshness、age、threshold、reason 与四项 safety flags。
- `ReadModelEvidenceMetadataCalculator` 为既有 read model 提供统一、保守的 freshness 计算。
- GateU-1～4 依次覆盖 Shadow Validation Workflow、Shadow Runs、Consistency Evidence、Incident / Replay Review 与 Evaluation Artifact Preview。
- GateU-5 `ValidationOperationsRuntimeEvidenceOverviewQueryService` 只聚合五个既有 metadata source，不重算底层业务事实。

## Aggregate Rules

- 固定五来源且顺序稳定；每次请求每来源调用一次。
- 全部来源 `AVAILABLE` 才返回 aggregate `AVAILABLE`。
- 全部来源 `AVAILABLE / FRESH` 才返回 aggregate `FRESH`。
- 任一 stale 保留 aggregate `STALE`；不完整、unknown 或 unavailable 状态 fail-closed。
- future `lastCalculatedAt` 不产生负 age，而返回 `UNKNOWN` 与明确 reason。
- source exception 直接传播，不返回 synthetic success。

## Persistence And Runtime Boundary

GateU 未新增 migration、写 SQL、repository 写方法、scheduler、runner、background job、内部 HTTP client、credential read 或 private endpoint。backend 结果是 request-time read-model aggregate，不是 runtime execution 状态机。
