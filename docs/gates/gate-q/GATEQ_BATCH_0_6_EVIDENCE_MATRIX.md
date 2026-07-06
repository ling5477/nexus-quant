# GateQ Batch 0-6 Evidence Matrix

本文归档 GateQ-0..6 的完成证据。GateQ 当前状态为 `FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `TAGGED`（已打 tag），但该状态只覆盖只读验证、只读对照、no-side-effect preview、binding preview contract 与前端证据展示基线。

## Evidence matrix

| Batch | Final state | Primary evidence | Accepted meaning | Boundary |
| --- | --- | --- | --- | --- |
| GateQ-0 | `COMPLETED`（已完成） | [GATEQ_PLAN.md](GATEQ_PLAN.md) | GateQ Plan / Shadow Live readiness planning 已完成，并被 GateQ-1..6 消费。 | Planning-only；不实现 API、migration、页面、测试、CI workflow 或 runtime。 |
| GateQ-1 | `COMPLETED` | `GET /api/strategies/evaluation-gate`、service/controller tests、[GATEQ_FREEZE_READINESS_REVIEW.md](GATEQ_FREEZE_READINESS_REVIEW.md) | Strategy Evaluation Gate read-only baseline 已完成；fail-closed 聚合 strategy / dataset / evaluation / publish / Paper facts。 | `READY_FOR_SHADOW_REVIEW` 不是交易授权，不启动 Shadow Live runner。 |
| GateQ-2 | `COMPLETED` | `GET /api/strategies/paper-shadow/comparison`、service/controller tests、[GATEQ_FREEZE_READINESS_REVIEW.md](GATEQ_FREEZE_READINESS_REVIEW.md) | Paper vs Shadow Comparison read-only baseline 已完成；Shadow 未实现时返回阻断状态。 | `READY_FOR_COMPARISON` 不是交易授权，不创建 shadow run。 |
| GateQ-3 | `COMPLETED` | `GET /api/strategies/shadow-live/preview`、service/controller tests、[GATEQ_FREEZE_READINESS_REVIEW.md](GATEQ_FREEZE_READINESS_REVIEW.md) | Shadow Live no-side-effect preview skeleton 已完成，只组合 GateQ-1 / GateQ-2 结果。 | 不写库、不外联、不读 credential、不启动 runner、不下单。 |
| GateQ-4 | `COMPLETED` | `POST /api/research/evaluation-artifacts/binding-preview`、service/controller tests、[GATEQ_FREEZE_READINESS_REVIEW.md](GATEQ_FREEZE_READINESS_REVIEW.md) | Python Evaluation Artifact Java Binding Contract 已完成；只校验 request body artifact JSON。 | 不导入、不上传、不持久化；不代表 Python ML ready 或 live execution ready。 |
| GateQ-5 | `COMPLETED` | `/strategies/validation`、`strategy-validation-paper-shadow-smoke.spec.ts`、[GATEQ_FREEZE_READINESS_REVIEW.md](GATEQ_FREEZE_READINESS_REVIEW.md) | Frontend Paper / Shadow Comparison View 已完成；只消费 GateQ-1/2/3 GET API。 | 不新增后端能力，不展示交易授权正向文案。 |
| GateQ-6 | `COMPLETED` | `/strategies/validation` lifecycle trace / Evidence Matrix、smoke test、[GATEQ_FREEZE_READINESS_REVIEW.md](GATEQ_FREEZE_READINESS_REVIEW.md) | Strategy Lifecycle Trace View Enhancement 已完成。 | GateQ-4 在前端仍为 `PENDING_FRONTEND_SUPPORT` / `NOT_CONNECTED`，不新增上传、导入或写入能力。 |

## Freeze and archive evidence

- Freeze readiness review：`NQ-GATEQ-FREEZE-READINESS-REVIEW` = `PASS`（通过）/ `READY FOR FREEZE CLOSEOUT`（可进入冻结收口）；P0/P1=0。
- Freeze closeout：`NQ-GATEQ-FREEZE-CLOSEOUT` = `PASS`（通过）/ `FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `READY FOR ARCHIVAL`（可归档）。
- Release archive：`NQ-GATEQ-RELEASE-TAG-AND-ARCHIVE` = `PASS`（通过）/ `COMPLETED`（已完成）/ `RELEASE TAG PUSHED`（release tag 已推送）。

## Boundary confirmation

- LIVE `DISABLED`（关闭）。
- AI `NOT STARTED`（未开始）。
- DH runtime `NOT INTEGRATED`（未集成）。
- Integration-1 `NOT STARTED`（未开始）/ mock-test-support only where applicable。
- RealClient / real provider / private trading adapter / real permission probe `NOT_IMPLEMENTED`（未实现）。
- Shadow Live trading `NOT ENABLED`（未启用）。
- Python ML ready `NO`（否）。
- Python live execution ready `NO`（否）。
