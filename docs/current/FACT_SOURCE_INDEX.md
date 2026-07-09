# Current Fact Source Index

本文是 NexusQuant 当前事实源索引。GateS 已完成 freeze closeout，过程型证据以 `docs/gates/gate-s/` 为归档入口；`docs/current` 只保留当前状态、路线、验证、工作记录和仍需作为 current 使用的 API / DB / 架构事实。

## 1. 当前事实源优先级

当事实冲突时，按以下顺序解释当前状态：

1. 当前代码和实际验证结果。
2. [GATET_PLAN.md](GATET_PLAN.md)：GateT Shadow Validation Operations planning 当前入口。
3. [API.md](API.md)：已实现 HTTP API 当前事实。
4. [GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md](GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md)：GateT-1 Shadow Validation Workflow read model / operator model work order。
5. [GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md](GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md)：GateT-2 Consistency Evidence Refinement work order。
6. [GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md](GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md)：GateT-3 Incident / Replay Review Workflow work order。
7. [GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md](GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md)：GateT-4 Python Evaluation Artifact read-only binding preview work order。
8. [GATET_5_VALIDATION_OPERATIONS_WORKBENCH_WO.md](GATET_5_VALIDATION_OPERATIONS_WORKBENCH_WO.md)：GateT-5 Validation Operations Workbench work order。
9. [STATUS.md](STATUS.md)：当前项目状态。
10. [README.md](README.md)：current 入口和 archive pointer。
11. [ROADMAP.md](ROADMAP.md)：当前路线与 GateT 边界。
12. [TESTING.md](TESTING.md)：当前验证记录和未运行说明。
13. [WORKLOG.md](WORKLOG.md)：当前工作记录。
14. [DB_SCHEMA.md](DB_SCHEMA.md)：已落地 Flyway schema 当前事实。
15. [../gates/gate-s/README.md](../gates/gate-s/README.md)：GateS 历史归档入口。
16. [../gates/gate-s/GATES_FREEZE_CLOSEOUT.md](../gates/gate-s/GATES_FREEZE_CLOSEOUT.md)：GateS freeze closeout authority。
17. [../gates/gate-s/GATES_BATCH_0_6_EVIDENCE_MATRIX.md](../gates/gate-s/GATES_BATCH_0_6_EVIDENCE_MATRIX.md)：GateS-0 到 GateS-6 evidence matrix。
18. [../gates/gate-r/README.md](../gates/gate-r/README.md)：GateR 历史归档入口。
19. [../gates/gate-q/README.md](../gates/gate-q/README.md)：GateQ 历史归档入口。
20. [../gates/gate-p/README.md](../gates/gate-p/README.md)：GateP 历史归档入口。
21. [../gates/gate-o/README.md](../gates/gate-o/README.md)：GateO 历史归档入口。
22. [../archive/current-cleanup/post-gateq/README.md](../archive/current-cleanup/post-gateq/README.md)：post-GateQ current cleanup 审计和移动索引。

`docs/gates/**` 与 `docs/archive/**` 是历史证据或归档引用，不覆盖 `docs/current` 的当前状态摘要。已完成 Gate 的过程型长文档不得再作为 current authority 扩写。

## 2. 当前阶段声明

- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）。
- GateS release tag：`nq-gates-freeze`。
- GateS archive：`docs/gates/gate-s/README.md`。
- GateS-0..6：`COMPLETED`（已完成）。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag `nq-gater-freeze`。
- GateT-1 backend read model 与 frontend overview 最小切片：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；GateT 尚未 freeze、accepted 或 tagged。
- GateT-2 backend read model 与 frontend overview 最小切片：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；GateT 尚未 freeze、accepted 或 tagged。
- GateT-3 backend read model 与 frontend overview 最小切片：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；GateT 尚未 freeze、accepted 或 tagged。
- GateT-4 backend No-file baseline 与 frontend overview 最小切片：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核），入口为 [GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md](GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md)、[API.md](API.md)、[STATUS.md](STATUS.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md)。
- GateT-5 Validation Operations Workbench work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核），入口为 [GATET_5_VALIDATION_OPERATIONS_WORKBENCH_WO.md](GATET_5_VALIDATION_OPERATIONS_WORKBENCH_WO.md)。
- GateT-0 planning：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核），入口为 [GATET_PLAN.md](GATET_PLAN.md)。
- GateT-1 work order：`PLAN READY / READY FOR IMPLEMENTATION`（规划已就绪 / 可实现），入口为 [GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md](GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md)。
- GateT-2 work order：`PLAN READY / READY FOR IMPLEMENTATION`（规划已就绪 / 可实现），入口为 [GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md](GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md)。

## 3. GateT Planning Facts

- GateT 主线是 Shadow Validation Operations / 策略验证运营闭环规划，基于 GateS 只读诊断 evidence，不启动真实交易。
- GateT 第一批当前已实现 backend read model / operator model 与现有 `/strategies/validation` 的最小 frontend overview 消费；后续如进入完整 Operator Console、review / acknowledge 写侧、Python binding 或 scheduler readiness，必须另起任务并重新审查边界。
- GateT-1 已实现 endpoint `GET /api/shadow-validation/workflow/overview`；operator item 为 derived / deterministic / not persisted。
- GateT-2 已实现 endpoint `GET /api/paper-shadow/consistency/evidence/overview`；这是 GET-only / read-only / no-side-effect / not trading authorization 后端 read model。
- GateT-2 consistency evidence item 为 derived / deterministic / not persisted；只表达诊断证据，不表示交易授权或自动处置。
- GateT-2 frontend overview 已在现有 `/strategies/validation` 页面消费该 GET-only endpoint；只展示 consistency evidence 诊断证据，不新增 route、写侧 client、交易按钮、Dashboard v2 或真实交易入口。
- GateT-3 已实现 endpoint `GET /api/incidents/replay/review/overview`；这是 GET-only / read-only / no-side-effect / not trading authorization 后端 read model。
- GateT-3 review item 为 derived / deterministic / not persisted；review / acknowledge / escalation / closeout 仅为 recommendation 语义，不表示自动处置、真实 incident 已关闭或交易授权。
- GateT-4 已实现 backend No-file baseline；endpoint 为 `GET /api/strategy-validation/evaluation-artifacts/preview/overview`，属于当前 GET-only / read-only / no-side-effect API 事实。
- GateT-4 默认 No-file baseline：不读取 artifact 文件、不读取 manifest、不接受 file path query、不接受 request body、不上传 artifact、不执行 Python、不访问网络、不导入 DB。
- GateT-4 frontend overview 已在现有 `/strategies/validation` 页面只读消费该 GET-only endpoint；只展示 No-file baseline 诊断信息，不新增 route、上传 / 导入 / 文件路径输入、Python 执行、写侧 client 或交易入口。
- GateT 默认不新增 DB migration；review / acknowledge 若需要持久化，必须另起 DB schema review。
- GateT 不接 Python production binding，只允许 Python artifact read-only binding preview。
- GateT 不接 AI runtime，不接 DH runtime，不启动 Integration-1 runtime。
- GateT-5 只规划现有 `/strategies/validation` 页面内 Validation Operations Workbench 的 summary / detail 信息架构；不新增 route、API、migration、写侧操作或 runtime 能力。

## 4. GateT-1 Current Code Facts

- GateT-1 API fact：`GET /api/shadow-validation/workflow/overview` 为 GET-only / read-only / no-side-effect / not trading authorization。
- GateT-1 read model fact：`ShadowValidationWorkflowOverviewQueryService` 从 GateS 本地事实派生 `workflowState / validationDecision / severity / evidenceFreshness / blockers / warnings / nextSteps / evidenceAnchors`；不会持久化 operator item。
- GateT-1 repository fact：`JdbcShadowValidationWorkflowOverviewQueryRepository` 只做 SELECT-only bounded union，不读取 credential / account / live order / ledger / private trading 表，不读取 raw JSONB payload。
- GateT-1 validation fact：`mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）。
- GateT-1 frontend fact：现有 `/strategies/validation` 页面通过 TanStack Query 只读消费 `GET /api/shadow-validation/workflow/overview`，展示 operator counts、latest item、item list、blockers / warnings / nextSteps、evidence anchors、traceId 和固定边界 badges；不新增 route、导航、写侧 client 或交易按钮。
- GateT-1 frontend validation fact：`npm run build` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）；targeted smoke `strategy-validation-paper-shadow-smoke.spec.ts --project=chromium` 在高位 loopback 外部 Vite 模式下为 `PASS / 2 passed`。
- GateT-1 boundary fact：未新增 migration、Python、CI workflow、runner、scheduler、adapter 调用、真实交易所调用、credential 读取、account / order / ledger mutation。

## 5. GateT-2 Current Code Facts

- GateT-2 API fact：`GET /api/paper-shadow/consistency/evidence/overview` 为 GET-only / read-only / no-side-effect / not trading authorization。
- GateT-2 read model fact：`ConsistencyEvidenceOverviewQueryService` 从本地 consistency facts 派生 `comparisonStatus / divergenceSeverity / evidenceFreshness / metricDeltaSummary / blockers / warnings / nextSteps / evidenceAnchors`；不会持久化 evidence item。
- GateT-2 repository fact：`JdbcConsistencyEvidenceOverviewQueryRepository` 只读取 `shadow_consistency_reports`、`shadow_runs`、`shadow_run_snapshots`、`shadow_run_events`，不读取 credential / account / live order / ledger / private trading 表，不读取 `shadow_run_snapshots.payload`。
- GateT-2 validation fact：`mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）；新增 Controller 2 tests、service 5 tests、repository 2 tests 已纳入验证。
- GateT-2 frontend fact：现有 `/strategies/validation` 页面通过 TanStack Query 只读消费 `GET /api/paper-shadow/consistency/evidence/overview`，展示 counts、latestEvidenceItem、buckets、metricDeltaSummary、blockers / warnings / nextSteps、anchors、traceId 和固定边界 badges；`DIVERGED / HIGH / CRITICAL` 均按诊断语义展示，不表示交易授权或自动处置。
- GateT-2 frontend validation fact：`npm run build` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）；targeted smoke `strategy-validation-paper-shadow-smoke.spec.ts --project=chromium` 在高位 loopback 外部 Vite 模式下为 `PASS / 2 passed`。
- GateT-2 boundary fact：未新增 migration、Python、CI workflow、runner、scheduler、adapter 调用、真实交易所调用、credential 读取、account / order / ledger mutation、写侧 client 或交易入口。

## 6. GateT-3 Current Code Facts

- GateT-3 work order fact：`docs/current/GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md` 已定义 Incident / Replay Review Workflow 的 implementation 输入。
- GateT-3 API fact：`GET /api/incidents/replay/review/overview` 为 GET-only / read-only / no-side-effect / not trading authorization。
- GateT-3 read model fact：`IncidentReplayReviewOverviewQueryService` 从本地 incident / replay / shadow / consistency facts 派生 `reviewState / reviewDecision / severity / evidenceFreshness / blockers / warnings / nextSteps / evidenceAnchors`；不会持久化 review item。
- GateT-3 repository fact：`JdbcIncidentReplayReviewOverviewQueryRepository` 只读取 `shadow_run_events`、`shadow_consistency_reports`、`shadow_runs`、`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records`，不读取 credential / account / live order / ledger / private trading 表，不读取 raw JSONB payload。
- GateT-3 validation fact：`mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）；新增 Controller 2 tests、service 7 tests、repository 2 tests 已纳入验证。
- GateT-3 review semantics fact：`ACKNOWLEDGE_RECOMMENDED` 只表示建议人工确认已知诊断事实，`ESCALATE_RECOMMENDED` 只表示建议人工升级复核，`CLOSEOUT_RECOMMENDED` / `CLOSED_RECOMMENDATION` 只表示诊断闭环建议；均不表示自动处置、真实 incident 已关闭或交易授权。
- GateT-3 evidence relation fact：只通过 evidence anchors 关联 GateT-1 operator item、GateT-2 consistency evidence item 和 GateS-6 Incident / Replay facts；不得写回这些来源。
- GateT-3 frontend fact：现有 `/strategies/validation` 页面通过 TanStack Query 只读消费 `GET /api/incidents/replay/review/overview`，展示 review counts、latestReviewItem、reviewItems、severityBuckets、freshnessSummary、blockers / warnings / nextSteps、evidence anchors、traceId 和固定边界 badges；`ACKNOWLEDGE_RECOMMENDED / ESCALATE_RECOMMENDED / CLOSEOUT_RECOMMENDED / HIGH / CRITICAL` 均按诊断建议或诊断优先级展示，不表示交易授权或自动处置。
- GateT-3 frontend validation fact：`npm run build` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）；targeted smoke `strategy-validation-paper-shadow-smoke.spec.ts --project=chromium` 为 `PASS / 2 passed`（通过 / 2 条通过）。
- GateT-3 boundary fact：未新增 migration、Python、CI workflow、runner、scheduler、adapter 调用、真实交易所调用、credential 读取、account / order / ledger mutation、review / acknowledge / escalation / closeout 写侧或交易入口。

## 7. GateT-4 Current Code Facts

- GateT-4 work order fact：`docs/current/GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md` 已定义 Python Evaluation Artifact read-only binding preview 的 implementation 输入。
- GateT-4 API fact：`GET /api/strategy-validation/evaluation-artifacts/preview/overview` 为 GET-only / read-only / no-side-effect / not trading authorization。
- GateT-4 read model fact：`PythonEvaluationArtifactPreviewOverviewQueryService` 只依赖 `Clock` 并派生安全空基线；不读取 artifact 文件、manifest、任意路径、用户目录、上传文件、外部网络或 DB artifact catalog。
- GateT-4 No-file baseline fact：当前固定 `totalArtifactPreviews=0`、`artifactPreviews=[]`、`latestArtifactPreview=null`、`NO_ARTIFACT_SOURCE_CONFIGURED` warning、Manifest-only / schema review next step、`pythonMlReady=false`、`pythonLiveExecutionReady=false`。
- GateT-4 preview item fact：`PythonEvaluationArtifactPreviewItem` 为 derived / deterministic / not persisted；No-file baseline 不生成 item；预留字段只表达离线诊断材料可预览，不是 DB entity、artifact import record、strategy evaluation result、publish approval、Paper / Shadow / LIVE run trigger 或交易授权。
- GateT-4 validation fact：`schemaVersion=python-evaluation-artifact.v1`、checksum、`diagnosticOnly=true`、`notTradingAuthorization=true`、`liveExecutionReady=false`、`pythonMlReady=false`、`pythonLiveExecutionReady=false` 必须 fail-closed；`VALID` checksum 只表示 payload integrity，不表示策略有效。
- GateT-4 validation result fact：`mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）；新增 Controller 2 tests 和 service 5 tests 已纳入验证。
- GateT-4 frontend fact：现有 `/strategies/validation` 页面通过 TanStack Query 只读消费 `GET /api/strategy-validation/evaluation-artifacts/preview/overview`，展示 No-file baseline、artifact preview counts、schemaVersionSummary、checksumSummary、metricSummaryCoverage、blockers / warnings / nextSteps、evidenceAnchors、traceId 和固定边界 badges。
- GateT-4 frontend validation fact：`npm run build` 为 `PASS / BUILD SUCCESS`（通过 / 构建成功）；targeted smoke `strategy-validation-paper-shadow-smoke.spec.ts` 为 `PASS / 2 passed`（通过 / 2 条通过）。
- GateT-4 DB fact：不新增 migration，不导入 Python artifact 到生产 DB；如果未来必须持久化 artifact catalog / import record，必须另起 DB schema review。
- GateT-4 boundary fact：未修改 backend、research、scripts、deploy、CI、migration、docs/gates、docs/archive、pom.xml、package / lock files；未执行 Python、backtest、runner、scheduler、真实交易所或 credential 读取；未修改 Paper / Shadow / account / order / ledger 状态；未新增 artifact upload / import / file path input / Python execution / trade 操作入口。

## 8. GateS Current Code Facts

- GateS API facts：`GET /api/shadow-runs/overview`、`GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}`、`GET /api/strategy-validation/overview`、`GET /api/incidents/replay/overview` 均为 GET-only / read-only / no-side-effect / not trading authorization。
- GateS frontend facts：Shadow Run overview summary、Paper vs Shadow drilldown panel、Strategy Validation overview panel、Strategy Validation / Shadow Workbench 和 Incident / Replay overview panel 均为只读诊断展示。
- GateS Python facts：`research/py/src/nq_research/evaluation/artifacts.py` 和 `parameters.py` 只提供 offline evaluation artifact baseline，不表示 Java production binding、API、migration、runner、Python ML readiness、Python live execution readiness 或真实交易授权。

## 9. 禁止误写清单

- 不得把 GateS freeze closeout 写成 GateT implementation。
- 不得把 GateT plan 写成 runtime 已启动。
- 不得把 GateS GET-only endpoints 写成写接口、runner trigger、scheduler trigger 或交易 endpoint。
- 不得把 frontend panels 写成执行按钮、自动处置、真实交易入口或 Shadow trading enabled。
- 不得把 Strategy Validation 的 `APPROVED`（验证层通过）写成真实交易授权。
- 不得把 Incident / Replay severity 写成真实 incident runtime 或实盘就绪。
- 不得把 Python offline artifact 写成 Python ML ready 或 Python live execution ready。
- 不得把 Python artifact checksum valid 写成策略有效、真实收益、交易授权或 live execution ready。
- 不得把 No-file baseline 写成已读取 artifact 或 Java production binding。
- 不得把 LIVE 写成 enabled。
- 不得把 AI 写成 started。
- 不得把 DH runtime 写成 integrated。
- 不得把 Integration-1 mock/test-support 写成 runtime started。
- 不得把 RealClient、real provider、private trading adapter 或 real permission probe 写成 implemented。
