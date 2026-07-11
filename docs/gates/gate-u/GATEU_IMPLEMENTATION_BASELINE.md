# GateU Implementation Baseline

> GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）

本文件是依据已验证 commit、代码、测试及 CI 重建的归档基线，
不是原始 GateU plan 文档的伪造副本。

仓库中不存在可作为本次 durable archive source 的原始 GateU plan，因此本文件只冻结可由 Git 历史、当前代码、测试和 CI 复核的已实现事实，不反推或伪造当时的计划文本。

## Commit Baseline

| Batch | Commit | 已实现事实 |
| --- | --- | --- |
| GateU-1 | `c276d0ea` | 统一 `ReadModelEvidenceMetadata` 与 calculator；Shadow Validation Workflow / Shadow Runs 接入 metadata |
| GateU-2 | `14f18cba` | Consistency Evidence 接入 availability / freshness metadata |
| GateU-3 | `006b8ff9` | Incident / Replay Review 接入 metadata |
| GateU-4 | `0db719f2` | No-file Evaluation Artifact Preview 接入 metadata，真实语义保持 `UNAVAILABLE / UNKNOWN` |
| GateU-5 | `9f278583` | 五来源 runtime evidence aggregate GET、页面总览、TanStack Query client/hook/refetch 与测试 |

GateU capability baseline 为 `9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`。提交链中的 `e151db81 chore(frontend): 切换本地端口至51888` 不属于 GateU-1～5 capability evidence。

## Accepted Semantics

- Availability：五来源全为 `AVAILABLE` 才聚合为 `AVAILABLE`；不完整来源 fail-closed 为 `PARTIAL`、`UNAVAILABLE` 或 `UNKNOWN`。
- Freshness：五来源全为 `AVAILABLE / FRESH` 才聚合为 `FRESH`；任一 `STALE` 为 `STALE`；其余不完整状态为 `UNKNOWN`。
- 固定来源：`SHADOW_VALIDATION_WORKFLOW`、`SHADOW_RUNS`、`CONSISTENCY_EVIDENCE`、`INCIDENT_REPLAY_REVIEW`、`EVALUATION_ARTIFACT_PREVIEW`。
- 每个来源每次 aggregate request 只调用一次；来源异常沿既有异常链传播，不合成成功。
- `diagnosticOnly`、`noSideEffect`、`notTradingAuthorization`、`liveDisabled` 固定为 `true`。

## Verification Baseline

- Maven：`BUILD SUCCESS`，23-module reactor。
- Frontend build：`PASS`。
- Playwright：`4 passed`。
- CI：run `29108265105`，`completed / success`，`headSha=9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`。

以上是 GateU implementation evidence，不是 tag 已创建、LIVE 已启用、runtime 已启动或交易已获授权。
