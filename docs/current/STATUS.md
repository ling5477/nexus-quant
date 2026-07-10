# Current Status

## 1. 当前总状态

- GateU：`FREEZE READY / NOT TAGGED`（已具备冻结条件 / 尚未打 tag）。
- GateU-1～GateU-5：`COMPLETED`（已完成）。
- GateU baseline commit：`9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`。
- GateU current HEAD CI：GitHub Actions run `29108265105`，`NQ CI Baseline`，`completed / success`，`headSha` 与 baseline commit 一致。
- GateU archive entry：`docs/gates/gate-u/README.md`。
- GateU release tag：`nq-gateu-freeze` 尚不存在；不得写成 `TAGGED`。
- GateV：`NOT STARTED`（未开始）。
- GateT / GateS / GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），历史证据入口为 `docs/gates/gate-t/`、`docs/gates/gate-s/`、`docs/gates/gate-r/`。

## 2. GateU Evidence

- GateU-1：统一 `ReadModelEvidenceMetadata` 与 calculator；Shadow Validation Workflow、Shadow Run overview 接入统一 metadata；commit `c276d0ea`；CI run `29096139258` success。
- GateU-2：Consistency Evidence metadata；commit `14f18cba`；CI run `29097485546` success。
- GateU-3：Incident / Replay Review metadata；commit `006b8ff9`；CI run `29103173171` success。
- GateU-4：Evaluation Artifact Preview No-file metadata；commit `0db719f2`；CI run `29106454940` success。
- GateU-5：`GET /api/validation-operations/runtime-evidence/overview`、固定五来源、每来源一次、fail-closed aggregate、Strategy Validation 页面运行证据总览、TanStack Query GET/refetch；commit `9f278583`；CI run `29108265105` success。
- 完整证据矩阵、API / frontend / test / CI / safety index：`docs/gates/gate-u/README.md`。

## 3. GateU Capability Boundary

- 固定五来源顺序：`SHADOW_VALIDATION_WORKFLOW`、`SHADOW_RUNS`、`CONSISTENCY_EVIDENCE`、`INCIDENT_REPLAY_REVIEW`、`EVALUATION_ARTIFACT_PREVIEW`。
- No-file Artifact Preview 保留为第五来源，当前为 `UNAVAILABLE / UNKNOWN`，不会被忽略。
- 只有五来源全部 `AVAILABLE`，aggregate availability 才为 `AVAILABLE`；只有五来源全部 `AVAILABLE / FRESH`，aggregate freshness 才为 `FRESH`。
- `diagnosticOnly`、`noSideEffect`、`notTradingAuthorization`、`liveDisabled` 均固定为 `true`。
- GateU 不新增 migration、写 SQL、scheduler、runner、内部 HTTP、credential、private endpoint、真实交易或写侧动作。

## 4. 禁止边界

- GateV：`NOT STARTED`（未开始）。
- LIVE：`DISABLED`（关闭）。
- Shadow trading：`NOT ENABLED`（未启用）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- Python artifact import：`NOT STARTED`（未开始）。
- Python ML readiness / live execution readiness：`NO`（否）。

## 5. 下一步

下一步只能是由用户精确暂存本轮 6 个允许文档，提交并推送 `docs(gateu): freeze validation runtime evidence baseline`，等待该新提交对应 `NQ CI Baseline / completed / success`，再由用户创建并推送 `nq-gateu-freeze`。在 tag 实际推送前，GateU 保持 `FREEZE READY / NOT TAGGED`；不得启动 GateV，不得继续新增 read-model。
