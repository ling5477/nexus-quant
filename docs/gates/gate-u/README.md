# GateU Freeze Evidence

本文是 GateU-1～GateU-5 的最小冻结证据入口。当前结论为 `FREEZE READY / NOT TAGGED`（已具备冻结条件 / 尚未打 tag）；本文不表示 release tag 已创建或已推送。

## 1. Frozen Baseline

- Repository：NexusQuant（NQ-only）。
- Branch：`dev`。
- Baseline commit：`9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`。
- Baseline commit subject：`feat(gateu): add validation runtime evidence overview`。
- `HEAD == origin/dev`：是。
- Baseline CI：GitHub Actions run `29108265105`，`NQ CI Baseline`，`completed / success`，`headSha=9f27858375a2ee5c40ee6a7e2d179dcd29cadf4d`。
- Release tag：`nq-gateu-freeze` 尚不存在；本任务未创建、未推送 tag。

## 2. GateU-1～GateU-5 证据矩阵

| Batch | 状态 | Commit | CI | 已接受证据 | 边界 |
| --- | --- | --- | --- | --- | --- |
| GateU-1 | `COMPLETED`（已完成） | `c276d0ea6882c96ca091435ad13cdffecfcffeee` | run `29096139258` / `success` | 新增统一 `ReadModelEvidenceMetadata`、`ReadModelEvidenceMetadataCalculator`；Shadow Validation Workflow 与 Shadow Run overview 接入统一 metadata。 | 纯只读诊断 metadata；缺时间、阈值或可用事实时 fail-closed。 |
| GateU-2 | `COMPLETED` | `14f18cba5a0826922c3b13ed9c7beacc7e186970` | run `29097485546` / `success` | Consistency Evidence overview 接入统一 availability / freshness metadata。 | 复用 SELECT-only 本地事实；不生成 consistency report。 |
| GateU-3 | `COMPLETED` | `006b8ff9344ce376d7b9779998649d302bdaafef` | run `29103173171` / `success` | Incident / Replay Review overview 接入统一 metadata。 | 不创建 incident、不启动 replay、不新增 durable operator review。 |
| GateU-4 | `COMPLETED` | `0db719f29e31445bd12c980347617e010a2e331f` | run `29106454940` / `success` | Evaluation Artifact Preview No-file baseline 接入统一 metadata，固定保留 `LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW`。 | `UNAVAILABLE / UNKNOWN` fail-closed；不读文件、不执行 Python、不导入 DB。 |
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
| GateU-1 CI | run `29096139258` / `NQ CI Baseline` / `completed / success` |
| GateU-2 CI | run `29097485546` / `NQ CI Baseline` / `completed / success` |
| GateU-3 CI | run `29103173171` / `NQ CI Baseline` / `completed / success` |
| GateU-4 CI | run `29106454940` / `NQ CI Baseline` / `completed / success` |
| GateU-5 / current HEAD CI | run `29108265105` / `NQ CI Baseline` / `completed / success` |
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

- GateU：`FREEZE READY / NOT TAGGED`（已具备冻结条件 / 尚未打 tag）。
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

