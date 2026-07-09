# Current Status

## 1. 当前总状态

- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateS release tag：`nq-gates-freeze`。
- GateS archive：`docs/gates/gate-s/`。
- GateS closeout：`docs/gates/gate-s/GATES_FREEZE_CLOSEOUT.md`。
- GateS-0：`COMPLETED`（已完成），Plan / fact-source reconciliation。
- GateS-1：`COMPLETED`（已完成），Shadow Run overview backend read model + frontend overview summary。
- GateS-2：`COMPLETED`（已完成），Paper vs Shadow consistency drilldown backend + frontend。
- GateS-3：`COMPLETED`（已完成），Strategy Evaluation Gate overview backend + frontend。
- GateS-4：`COMPLETED`（已完成），Python offline evaluation artifact baseline。
- GateS-5：`COMPLETED`（已完成），Strategy Validation / Shadow Workbench frontend。
- GateS-6：`COMPLETED`（已完成），Incident / Replay overview backend + frontend。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag：`nq-gater-freeze`；archive：`docs/gates/gate-r/`。
- GateQ / GateP / GateO 及更早 Gate：历史证据入口为 `docs/gates/**` 或 `docs/archive/**`。
- 当前阶段：NQ-GATET-4-PYTHON-EVALUATION-ARTIFACT-BINDING-PREVIEW-WO 已进入 `PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）；GateT 尚未 freeze、accepted 或 tagged。
- GateT-0：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核），入口为 `docs/current/GATET_PLAN.md`。
- GateT-1 work order：`PLAN READY / READY FOR IMPLEMENTATION`（规划已就绪 / 可实现），入口为 `docs/current/GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md`。
- GateT-1 implementation：`GET /api/shadow-validation/workflow/overview` 后端 read model 已实现；只派生 derived / deterministic operator items，不持久化、不新增 migration、不启动 runner / scheduler、不调用真实交易所、不读取 credential、不表示交易授权。
- GateT-1 frontend overview：现有 `/strategies/validation` 页面已最小只读消费 `GET /api/shadow-validation/workflow/overview`；展示 derived operator items、workflowState、validationDecision、severity、evidenceFreshness、blockers / warnings / nextSteps、evidenceAnchors、traceId 和固定安全边界 badges；不新增 route、Dashboard v2、review / acknowledge 写侧或交易入口。
- GateT-2 work order：`PLAN READY / READY FOR IMPLEMENTATION`（规划已就绪 / 可实现），入口为 `docs/current/GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md`。
- GateT-2 implementation：`GET /api/paper-shadow/consistency/evidence/overview` 后端 read model 已实现；只派生 deterministic consistency evidence item 和 overview summary，不持久化、不新增 migration、不创建 report、不启动 runner / scheduler、不调用真实交易所、不读取 credential、不表示交易授权。
- GateT-2 frontend overview：现有 `/strategies/validation` 页面已最小只读消费 `GET /api/paper-shadow/consistency/evidence/overview`；展示 evidence counts、latestEvidenceItem、severityBuckets、freshnessSummary、metricDeltaSummary、blockers / warnings / nextSteps、evidenceAnchors、traceId 和固定安全边界 badges；不新增 route、Dashboard v2、review / acknowledge / approve / reject 写侧或交易入口。
- GateT-3 work order：`PLAN READY / READY FOR IMPLEMENTATION`（规划已就绪 / 可实现），入口为 `docs/current/GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md`。
- GateT-3 implementation：`GET /api/incidents/replay/review/overview` 后端 read model 已实现；只派生 deterministic Incident / Replay review items，不持久化、不新增 migration、不创建 review / acknowledge / escalation / closeout / incident / alert / replay 记录、不启动 runner / scheduler、不调用真实交易所、不读取 credential、不表示交易授权、真实 incident 已关闭或自动处置。
- GateT-3 frontend overview：现有 `/strategies/validation` 页面已最小只读消费 `GET /api/incidents/replay/review/overview`；展示 review counts、latestReviewItem、reviewItems、severityBuckets、freshnessSummary、blockers / warnings / nextSteps、evidenceAnchors、traceId 和固定安全边界 badges；不新增 route、Dashboard v2、review / acknowledge / escalate / closeout 写侧、交易按钮或真实交易入口。
- GateT-4 work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核），入口为 `docs/current/GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md`；默认选择 No-file baseline，不读取 artifact 文件、不执行 Python、不导入 DB、不新增 migration。

## 2. GateS Freeze Closeout Evidence

- Readiness review：`docs/gates/gate-s/GATES_FREEZE_READINESS_REVIEW.md`。
- Evidence matrix：`docs/gates/gate-s/GATES_BATCH_0_6_EVIDENCE_MATRIX.md`。
- API summary：`docs/gates/gate-s/GATES_API_EVIDENCE_SUMMARY.md`。
- Frontend summary：`docs/gates/gate-s/GATES_FRONTEND_EVIDENCE_SUMMARY.md`。
- Python summary：`docs/gates/gate-s/GATES_PYTHON_RESEARCH_EVIDENCE_SUMMARY.md`。
- Boundary statement：`docs/gates/gate-s/GATES_BOUNDARY_STATEMENT.md`。
- 最新 closeout precondition CI：GitHub Actions run `28932927935` / `NQ CI Baseline` / `success`（成功），`headSha=5f0fcb9d4dacab95202dc7a9fb78911e60c06afe`。

## 3. 禁止边界

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。

## 4. GateS 能力边界

- GateS 新增 API 均为 GET-only / read-only / no-side-effect / not trading authorization。
- GateS frontend panels 均为只读诊断展示，不提供 start / stop / execute / trade / placeOrder / cancelOrder / withdraw / transfer 入口。
- GateS Python artifact 只属于 offline research diagnostic baseline，不绑定 Java production，不导入 DB，不驱动 Paper / Shadow / LIVE。
- GateS freeze closeout 不新增 API、migration、frontend page、E2E、CI workflow、Python runtime、runner、scheduler、真实 provider、private trading adapter 或真实交易行为。

## 5. GateT-0 Planning Decision

- GateT 主线目标：把 GateS 只读诊断能力推进为 Shadow Validation Operations / 策略验证运营闭环的规划基线。
- GateT 与 GateS 边界：GateS 提供 read-only evidence；GateT 规划 operator workflow、evidence refinement、incident / replay review、Python artifact read-only binding preview 和 frontend workbench，但不启动 runtime。
- GateT 后续可规划本地 review / acknowledge 写侧 workflow，但必须限定为本地复核记录；不得触发交易、runner、scheduler、private endpoint、account / ledger / order / position mutation。
- GateT 后续可规划 no-side-effect scheduler readiness review；不得连接真实交易所、不得调用 private endpoint、不得创建真实订单。
- GateT 第一批应先做 backend read model / operator model plan，再做 frontend workbench；原因是前端已有 GateS 只读面板，缺口在统一 operator item、review state 和 evidence anchor 语义。
- DB migration：默认不新增；只有 durable review / acknowledge audit 被证明必须持久化时，才另起 DB schema review。
- Python artifact：只允许 read-only binding preview；不导入、不写库、不驱动 Java production runtime。
- AI / DH：默认不接，仍保持 `NOT STARTED`（未开始）和 `NOT INTEGRATED`（未集成）。

## 6. GateT-1 Implementation Decision

- GateT-1 主线目标：实现 Shadow Validation Workflow backend read model / derived operator item model。
- 已实现 endpoint：`GET /api/shadow-validation/workflow/overview`；详见 `docs/current/API.md`。
- Operator item：derived / deterministic，不持久化；`operatorItemId` 由稳定事实锚点派生，不依赖数据库自增。
- Operator review / acknowledge：仍未实现；不得触发交易、runner、scheduler、private endpoint、account / ledger / order / position mutation。
- DB migration：本轮未新增；durable review / acknowledge 若后续必须持久化，必须另起 DB schema review。
- Safety flags：`diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`。
- 验证状态：`mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）。
- Frontend overview：`frontend/src/types/shadow-validation-workflow.ts`、`frontend/src/api/shadow-validation-workflow.ts`、`frontend/src/hooks/useShadowValidationWorkflowQueries.ts` 与现有 `StrategyValidationPage` 已接入 GET-only workflow overview；`VALIDATION_READY` / `READY_FOR_OPERATOR_REVIEW` 均展示为人工复核语义，不表示交易授权。
- 前端验证状态：`npm run build` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）；targeted smoke `strategy-validation-paper-shadow-smoke.spec.ts --project=chromium` 在高位 loopback 端口外部 Vite 模式下 `PASS / 2 passed`（通过 / 2 条通过）。默认 `5179` 端口在本机返回 `EACCES`，未写成测试失败。
- 下一步只能是提交前复核、stage、commit，或后续另起 GateT 任务；不得直接进入 Python binding、scheduler readiness、AI/DH runtime 或真实交易路径。

## 7. GateT-2 Work Order Decision

- GateT-2 主线目标：定义 Paper vs Shadow Consistency Evidence Refinement 的后续 backend read model work order。
- 唯一候选 endpoint：`GET /api/paper-shadow/consistency/evidence/overview`；该路径与 GateS-2 `paper-shadow/consistency` namespace 对齐，避免与 GateT-1 operator workflow namespace 混淆。
- Candidate DTO：`ConsistencyEvidenceOverviewResponse` 和 `ConsistencyEvidenceItem`，字段覆盖 generatedAt、safety flags、evidence counts、latestEvidenceItem、evidenceItems、severityBuckets、freshnessSummary、metricDeltaSummary、blockers / warnings / nextSteps、evidenceAnchors、traceId。
- Consistency evidence item：derived / deterministic / not persisted；只表达诊断证据，不是 review / acknowledge 记录，不是交易授权。
- DB migration：默认不新增；durable evidence review / acknowledge 若后续必须持久化，必须另起 DB schema review。
- 实现边界：后续 implementation 必须 GET-only / SELECT-only / no-side-effect；不创建 report，不启动 runner / scheduler，不调用真实交易所，不读取 credential，不写 account / order / ledger / position。
- 文案边界：`DIVERGED` 只表示 Paper vs Shadow 证据不一致；`HIGH / CRITICAL` 只表示诊断优先级；`VALIDATION_READY / APPROVED` 不表示交易授权。

## 8. GateT-2 Implementation Decision

- GateT-2 主线目标：实现 Paper vs Shadow Consistency Evidence Refinement 的 GET-only backend read model。
- 已实现 endpoint：`GET /api/paper-shadow/consistency/evidence/overview`；详见 `docs/current/API.md`。
- Consistency evidence item：derived / deterministic，不持久化；`evidenceItemId` 由 `consistencyReportId + shadowRunId + paperRunId + strategyVersionId` 稳定派生。
- Query source：只读取 `shadow_consistency_reports`、`shadow_runs`、`shadow_run_snapshots` 和 `shadow_run_events` 的本地事实；不读取 credential / account / live order / ledger / private trading 表，不读取 snapshot payload。
- metricDelta：只做摘要化，不返回 raw JSONB，不推断收益结论，不生成交易建议。
- DB migration：本轮未新增；durable evidence review / acknowledge 若未来必须持久化，必须另起 DB schema review。
- Safety flags：`diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`。
- 验证状态：`mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）。
- Frontend overview：`frontend/src/types/consistency-evidence.ts`、`frontend/src/api/consistency-evidence.ts`、`frontend/src/hooks/useConsistencyEvidenceOverview.ts` 与现有 `StrategyValidationPage` 已接入 GET-only consistency evidence overview；`CONSISTENT` 不表示盈利或交易授权，`DIVERGED` 只表示 Paper vs Shadow 证据不一致，`HIGH / CRITICAL` 只表示诊断优先级，`metricDelta` 只显示诊断差异摘要。
- 前端验证状态：`npm run build` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）；targeted smoke `strategy-validation-paper-shadow-smoke.spec.ts --project=chromium` 在高位 loopback 外部 Vite 模式下为 `PASS / 2 passed`（通过 / 2 条通过）。默认 `5179` 端口在本机返回 `EACCES`，未写成测试失败。
- 下一步只能是提交前复核、stage、commit，或后续另起 GateT 任务；不得直接进入 Python binding、scheduler readiness、AI/DH runtime 或真实交易路径。

## 9. GateT-3 Work Order Decision

- GateT-3 主线目标：定义 Incident / Replay Review Workflow 的后续 read-only derived model work order；该 work order 已作为本轮 implementation 输入。
- 唯一候选 endpoint：`GET /api/incidents/replay/review/overview`；该路径延续 GateS-6 `GET /api/incidents/replay/overview` namespace，并已按后端 GET-only read model 实现，不是完整 validation operations center 或 Shadow-only endpoint。
- Candidate DTO：`IncidentReplayReviewOverviewResponse` 和 `IncidentReplayReviewItem`，字段覆盖 generatedAt、safety flags、review counts、latestReviewItem、reviewItems、severityBuckets、freshnessSummary、blockers / warnings / nextSteps、evidenceAnchors、traceId。
- Review item：derived / deterministic / not persisted；只表达诊断复核建议，不是 durable review / acknowledge 记录，不是自动处置，也不是交易授权。
- Review / acknowledge / escalation：当前仍无写侧持久化；`ACKNOWLEDGE_RECOMMENDED` 只表示建议人工确认已知诊断事实，`ESCALATE_RECOMMENDED` 只表示建议人工升级复核，`CLOSED_RECOMMENDATION` 只表示诊断闭环建议。
- DB migration：默认不新增；durable review / acknowledge / escalation / closeout 若后续必须持久化，必须另起 DB schema review。
- 实现边界：implementation 已按 GET-only / SELECT-only / no-side-effect 落地；不创建 incident / alert / replay，不修改 Paper / Shadow / account / order / ledger 状态，不启动 runner / scheduler，不调用真实交易所，不读取 credential。
- 文案边界：`HIGH / CRITICAL` 只表示诊断优先级；`ACKNOWLEDGED` / `CLOSED_RECOMMENDATION` 不表示自动处置完成、真实 incident 已关闭或交易授权。

## 10. GateT-3 Implementation Decision

- GateT-3 主线目标：实现 Incident / Replay Review Workflow 的 GET-only backend read model。
- 已实现 endpoint：`GET /api/incidents/replay/review/overview`；详见 `docs/current/API.md`。
- Review item：derived / deterministic，不持久化；`reviewItemId` 由 sourceType、sourceId、shadowRunId、paperRunId、consistencyReportId 稳定派生。
- Query source：只读取 `shadow_run_events`、`shadow_consistency_reports`、`shadow_runs`、`paper_run_alerts`、`paper_run_recovery_events` 和 `trade_replay_records` 本地诊断事实；不读取 credential / account / live order / ledger / private trading 表，不读取 raw private payload。
- Review semantics：`ACKNOWLEDGE_RECOMMENDED` 只表示建议人工确认诊断事实；`ESCALATE_RECOMMENDED` 只表示建议人工升级复核；`CLOSEOUT_RECOMMENDED` / `CLOSED_RECOMMENDATION` 只表示诊断闭环建议；均不表示自动处置、真实 incident 已关闭或交易授权。
- DB migration：本轮未新增；durable review / acknowledge / escalation / closeout 若未来必须持久化，必须另起 DB schema review。
- Safety flags：`diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`。
- 验证状态：`mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）。
- Frontend overview：`frontend/src/types/incident-replay-review.ts`、`frontend/src/api/incident-replay-review.ts`、`frontend/src/hooks/useIncidentReplayReviewOverview.ts` 与现有 `StrategyValidationPage` 已接入 GET-only Incident / Replay Review overview；`ACKNOWLEDGE_RECOMMENDED` / `ESCALATE_RECOMMENDED` / `CLOSEOUT_RECOMMENDED` 均展示为人工诊断建议，不表示自动处置、真实 incident 已关闭或交易授权。
- 前端验证状态：`npm run build` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）；targeted smoke `strategy-validation-paper-shadow-smoke.spec.ts --project=chromium` 为 `PASS / 2 passed`（通过 / 2 条通过）。
- 下一步只能是提交前复核、stage、commit，或后续另起 GateT 任务；不得直接进入 Python binding、scheduler readiness、AI/DH runtime 或真实交易路径。

## 11. GateT-4 Work Order Decision

- GateT-4 主线目标：定义 Python Evaluation Artifact read-only binding preview 的后续 backend read model work order。
- 唯一候选 endpoint：`GET /api/strategy-validation/evaluation-artifacts/preview/overview`；该路径与 Strategy Validation / GateT validation operations 的只读 evidence 语义对齐，不选择 research namespace 或 shadow-only namespace。
- 默认 source / query 策略：No-file baseline；后续 implementation 第一版不读取 artifact 文件、不读取 manifest、不接受 file path query、不接受 request body、不上传 artifact、不调用 Python subprocess。
- Candidate DTO：`PythonEvaluationArtifactPreviewOverviewResponse` 和 `PythonEvaluationArtifactPreviewItem`，字段覆盖 generatedAt、safety flags、pythonMlReady、pythonLiveExecutionReady、artifact counts、latestArtifactPreview、artifactPreviews、schemaVersionSummary、checksumSummary、metricSummaryCoverage、blockers / warnings / nextSteps、evidenceAnchors、traceId。
- Artifact preview item：derived / deterministic / not persisted；只表达 Python offline diagnostic material preview readiness，不是 artifact import record、strategy evaluation result、publish approval、Paper / Shadow / LIVE run trigger 或交易授权。
- 校验边界：`schemaVersion=python-evaluation-artifact.v1`、checksum、`diagnosticOnly=true`、`notTradingAuthorization=true`、`liveExecutionReady=false`、`pythonMlReady=false`、`pythonLiveExecutionReady=false` 必须 fail-closed；`VALID` checksum 只表示 payload 与 checksum 自洽，不表示策略有效。
- Evidence relation：artifact preview 只能通过 `strategyVersionId`、`datasetId`、`parameterSetId` 和 evidence anchors 与 GateT / GateS 事实建立只读关系；不得写回 strategy validation、operator item、consistency report、review item 或 DB。
- DB migration：默认不新增；若未来必须持久化 artifact catalog / import record，必须另起 DB schema review。
- 本 work order 未实现 endpoint，未更新 `docs/current/API.md` 或 `docs/current/DB_SCHEMA.md` 当前事实。
- 下一步只能是提交前复核、stage、commit，或后续另起 GateT-4 backend No-file baseline implementation；不得直接进入 frontend workbench、Manifest-only reader、scheduler readiness、AI/DH runtime 或真实交易路径。
