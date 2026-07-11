# GateU Freeze Archive

本文是 GateU-1～GateU-5 的 durable freeze archive（持久冻结归档）入口。归档状态固定为：

> GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）

本文不表示 release tag 已创建或已推送。GateU capability baseline 固定为 `9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`；后续 docs commit 不改变实现基线。

## Archive Manifest

| 文件 | 归档职责 |
| --- | --- |
| [GATEU_FREEZE_READINESS_REVIEW.md](GATEU_FREEZE_READINESS_REVIEW.md) | freeze readiness 复核、findings 与判定 |
| [GATEU_FREEZE_CLOSEOUT.md](GATEU_FREEZE_CLOSEOUT.md) | commit / CI / tag closeout 状态与后续动作 |
| [GATEU_IMPLEMENTATION_BASELINE.md](GATEU_IMPLEMENTATION_BASELINE.md) | 依据 commits、代码、测试与 CI 重建的实现基线 |
| [GATEU_BATCH_1_5_EVIDENCE_MATRIX.md](GATEU_BATCH_1_5_EVIDENCE_MATRIX.md) | GateU-1～5 跨层证据矩阵 |
| [GATEU_TESTING_EVIDENCE_SUMMARY.md](GATEU_TESTING_EVIDENCE_SUMMARY.md) | Maven、frontend build、Playwright 与 CI 证据 |
| [GATEU_BACKEND_EVIDENCE_SUMMARY.md](GATEU_BACKEND_EVIDENCE_SUMMARY.md) | backend read-model 与 aggregate 证据 |
| [GATEU_API_EVIDENCE_SUMMARY.md](GATEU_API_EVIDENCE_SUMMARY.md) | GET-only API contract 与测试证据 |
| [GATEU_FRONTEND_EVIDENCE_SUMMARY.md](GATEU_FRONTEND_EVIDENCE_SUMMARY.md) | Strategy Validation 页面、TanStack Query 与 E2E 证据 |
| [GATEU_PYTHON_ARTIFACT_BOUNDARY_SUMMARY.md](GATEU_PYTHON_ARTIFACT_BOUNDARY_SUMMARY.md) | No-file Artifact Preview 与 Python 未接入边界 |
| [GATEU_RUNTIME_BOUNDARY_SUMMARY.md](GATEU_RUNTIME_BOUNDARY_SUMMARY.md) | runtime/read-model/no-side-effect 边界 |
| [GATEU_BOUNDARY_STATEMENT.md](GATEU_BOUNDARY_STATEMENT.md) | NQ-only、LIVE/AI/DH/交易授权禁止声明 |
| [GATEU_KNOWN_LIMITATIONS_AND_RESIDUALS.md](GATEU_KNOWN_LIMITATIONS_AND_RESIDUALS.md) | 已知限制、allowed residual 与 tag pending 状态 |

## 1. Frozen Baseline

- Repository：NexusQuant（NQ-only）。
- Branch：`dev`。
- Baseline commit：`9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`。
- Baseline commit subject：`feat(gateu): add validation runtime evidence overview`。
- GateU implementation baseline 当时的 `HEAD == origin/dev`：是。
- Baseline CI：GitHub Actions run `29108265105`，`NQ CI Baseline`，`completed / success`，`headSha=9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`。
- Release tag：`nq-gateu-freeze` 尚不存在；本任务未创建、未推送 tag。

## 2. GateU-1～GateU-5 证据矩阵

| Batch | 状态 | Commit | CI | 已接受证据 | 边界 |
| --- | --- | --- | --- | --- | --- |
| GateU-1 | `COMPLETED`（已完成） | `c276d0ea6882c96ca091435ad13cdffecfcffeee` | success 已确认；旧 batch exact run id 不纳入本次重建证据 | 新增统一 `ReadModelEvidenceMetadata`、`ReadModelEvidenceMetadataCalculator`；Shadow Validation Workflow 与 Shadow Run overview 接入统一 metadata。 | 纯只读诊断 metadata；缺时间、阈值或可用事实时 fail-closed。 |
| GateU-2 | `COMPLETED` | `14f18cba5a0826922c3b13ed9c7beacc7e186970` | success 已确认；旧 batch exact run id 不纳入本次重建证据 | Consistency Evidence overview 接入统一 availability / freshness metadata。 | 复用 SELECT-only 本地事实；不生成 consistency report。 |
| GateU-3 | `COMPLETED` | `006b8ff9344ce376d7b9779998649d302bdaafef` | success 已确认；旧 batch exact run id 不纳入本次重建证据 | Incident / Replay Review overview 接入统一 metadata。 | 不创建 incident、不启动 replay、不新增 durable operator review。 |
| GateU-4 | `COMPLETED` | `0db719f29e31445bd12c980347617e010a2e331f` | success 已确认；旧 batch exact run id 不纳入本次重建证据 | Evaluation Artifact Preview No-file baseline 接入统一 metadata，固定保留 `LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW`。 | `UNAVAILABLE / UNKNOWN` fail-closed；不读文件、不执行 Python、不导入 DB。 |
| GateU-5 | `COMPLETED` | `9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d` | run `29108265105` / `success` | 新增五来源 runtime evidence aggregate GET、Strategy Validation 页面“运行证据总览”、TanStack Query GET 与手动 refetch。 | 只聚合既有 metadata；不重算底层事实、不启动 runtime。 |

GateU 功能提交之间存在独立提交 `e151db81 chore(frontend): 切换本地端口至51888`；该提交不属于 GateU-1～GateU-5 证据矩阵，也不作为 GateU capability evidence。

## 3. API 与前端证据索引

### API

- Endpoint：`GET /api/validation-operations/runtime-evidence/overview`。
- Controller：`backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/validationoperations/runtimeevidence/ValidationOperationsRuntimeEvidenceOverviewController.java`。
- Aggregate service：`backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/validationoperations/runtimeevidence/ValidationOperationsRuntimeEvidenceOverviewQueryService.java`。
- 固定来源顺序：
  1. `SHADOW_VALIDATION_WORKFLOW`
  2. `SHADOW_RUNS`
  3. `CONSISTENCY_EVIDENCE`
  4. `INCIDENT_REPLAY_REVIEW`
  5. `EVALUATION_ARTIFACT_PREVIEW`
- 每个来源严格调用一次；来源异常沿现有异常链路传播，不伪造 `AVAILABLE`、`FRESH` 或成功响应。
- Availability 聚合只有五个来源全部为 `AVAILABLE` 时才为 `AVAILABLE`。
- Freshness 聚合只有五个来源全部为 `AVAILABLE / FRESH` 时才为 `FRESH`；任一 `STALE` 聚合为 `STALE`，其余不完整情况为 `UNKNOWN`。
- No-file Artifact Preview 作为第五来源保留，当前真实语义为 `UNAVAILABLE / UNKNOWN`，不会被过滤或忽略。

### Frontend

- Page：`frontend/src/pages/strategies/StrategyValidationPage.tsx`，展示“运行证据总览”。
- API client：`frontend/src/api/validation-operations-runtime-evidence.ts`，只发起 aggregate GET。
- TanStack Query hook：`frontend/src/hooks/useValidationOperationsRuntimeEvidenceOverview.ts`。
- Query key：`['validation-operations', 'runtime-evidence', 'overview']`。
- “刷新总览”调用 TanStack Query `refetch()`，只重新请求 aggregate GET；不分别触发五来源写侧刷新。
- Targeted E2E 验证固定五来源、No-file 来源、`PARTIAL / UNKNOWN` fail-closed 展示以及一次手动 refetch 对应一次 GET。

## 4. 测试与 CI 证据

| Evidence | Result |
| --- | --- |
| GateU-1～4 CI | 各 batch success 已确认；exact run id 不纳入本次重建证据 |
| GateU-5 implementation baseline CI | run `29108265105` / `NQ CI Baseline` / `completed / success` / `headSha=9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d` |
| Maven | `mvn -ntp -f backend/pom.xml -pl nq-core,nq-api,nq-infra,nq-app -am test` -> `BUILD SUCCESS` |
| Frontend build | `npm --prefix frontend run build` -> PASS |
| Targeted Playwright | 两个指定 smoke / Chromium -> `4 passed` |

已知非阻断 warning：Maven 输出 SLF4J provider、Mockito dynamic agent / Byte Buddy warning；Vite 输出 chunk size warning；Playwright WebServer 输出 Ant Design v5 与 React 19 compatibility warning。上述 warning 未改变本轮 PASS 结果。

## 5. Safety Boundary

所有统一 metadata 与 aggregate metadata 均强制：

- `diagnosticOnly=true`
- `noSideEffect=true`
- `notTradingAuthorization=true`
- `liveDisabled=true`

GateU 只形成 read-model evidence metadata 与只读总览，不新增 migration、写 SQL、scheduler、runner、内部 HTTP client、credential read、private endpoint、real provider、RealClient、真实交易、下单、撤单、转账或提现。GateU 不代表 LIVE、交易授权、Shadow trading、Python artifact import、ML readiness 或 live execution readiness。

固定状态：

- GateU：`FREEZE READY / TAG PENDING`（已具备冻结条件 / tag 待创建）。
- GateU-1～GateU-5：`COMPLETED`（已完成）。
- GateV：`NOT STARTED`（未开始）。
- LIVE：`DISABLED`（关闭）。
- Shadow trading：`NOT ENABLED`（未启用）。

## 6. Known Limitations And Residuals

- `nq-gateu-freeze` 尚未创建或推送；不得写成 `TAGGED`。
- Freeze 文档提交后的 commit 必须再次由当前 commit 对应的 `NQ CI Baseline / completed / success` 证明，用户才可创建并推送 tag。
- 本任务 allowlist 不包含 root `README.md`、`docs/current/README.md` 或 `docs/current/API.md`；它们未在本轮修改。当前 GateU freeze 状态以 `docs/current/STATUS.md`、`ROADMAP.md`、`FACT_SOURCE_INDEX.md` 与本文为准。
- GateV 不得从 GateU freeze readiness 自动启动；必须另起任务并重新确认范围与禁止边界。

## 7. Post-freeze Rules

本任务只准备 release tag，不执行 commit、push 或 tag。推荐 commit：

```text
docs(gateu): freeze validation runtime evidence baseline
```

仅在用户提交并推送、该新提交对应 CI 成功后，才可由用户创建并推送 annotated tag：

```powershell
git tag -a nq-gateu-freeze `
  -m "NexusQuant GateU freeze: validation runtime evidence baseline"

git push origin nq-gateu-freeze
```
