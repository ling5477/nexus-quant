# Current API

当前 API 文档以代码实际 controller 为准。本文记录当前 API 分类和已完成 GateH API 事实；GateI-PLAN 只新增规划入口，不实现接口。

## API 分类

- Auth API：登录、当前用户、token 相关接口。
- Account API：用户账户、交易账户、默认账户上下文、凭证写侧接口。
- Trading API：订单、成交、持仓、交易工作台相关接口。
- Strategy API：策略配置、策略查询与策略运行前置数据。
- Schedule API：调度配置与调度状态。
- Run API：运行记录、执行状态、运行详情。
- Research API：研究配置与研究任务。
- Backtest API：回测配置、回测执行、回测结果。
- Evaluation API：评估任务、评估结果。
- Publish API：发布候选、发布状态。
- Instrument API：交易标的、交易所、市场类型、symbol catalog。
- Marketdata API：行情基础 ingest/query 能力。
- Marketdata Data Quality Center API：只读聚合本地 bars、dataset coverage 与 ingestion facts，供 GateP Batch 2 数据质量诊断使用。
- Trading Preflight API：只读聚合单交易所账户、credential metadata、permission probe 状态、Data Quality diagnostic 和风险前置阻断原因，供 GateP Batch 4 解释真实交易为什么仍被阻断。
- Strategy Evaluation Gate API：只读聚合 strategy version、dataset quality、evaluation、publish trace 与 SIM Paper evidence，供 GateQ-1 判断研究与评估证据是否可进入后续 Shadow review。
- Paper Shadow Comparison API：只读聚合 strategy version、dataset quality、evaluation、publish trace、SIM Paper evidence 与 Shadow 未实现状态，供 GateQ-2 判断 Paper vs Shadow 对照证据准备度。
- Shadow Live No-side-effect Preview API：只读聚合 GateQ-1 evaluation gate 与 GateQ-2 Paper/Shadow comparison 结果，供 GateQ-3 判断是否能生成 Shadow Live no-side-effect 预览计划。
- Shadow Run Read-only API：只读查询本地 Shadow Run list、detail、events、snapshots 与 latest consistency report，供 GateR-6 / GateR-8 前端 list/detail/replay view 使用。
- Shadow Run Overview API：只读聚合本地 Shadow Run overview、latest run、latest consistency、divergence severity、blockers / warnings / nextSteps 和 evidence anchors，供 GateS-1 最小后端 read model 使用。
- Paper Shadow Consistency Drilldown API：围绕单个 `shadowRunId` 只读聚合 Shadow Run 主事实、latest consistency report、snapshot / event 摘要、blockers / warnings / nextSteps、evidence anchors 和安全边界 flags，供 GateS-2 最小后端 drilldown 使用。
- Strategy Validation Overview API：只读聚合 strategy version、evaluation、publish、SIM Paper、Shadow Run 与 consistency evidence 的本地事实，供 GateS-3 Strategy Evaluation Gate runtime baseline 查看 validation-only 决策状态；不表示交易授权。
- Incident Replay Overview API：只读聚合 Shadow / consistency / Paper alert / recovery / trade replay 本地诊断证据，供 GateS-6 Incident / Replay overview read model 使用；不表示交易授权、LIVE ready 或真实 incident runtime。
- Shadow Validation Workflow API：只读聚合 GateS 本地事实并派生 Shadow Validation Workflow operator items，供 GateT-1 backend read model 使用；operator items 为 derived / deterministic，不持久化，不表示交易授权。
- Python Evaluation Artifact Binding Preview API：只读校验 request body 中的 Python offline evaluation artifact，供 GateQ-4 生成 Java fact source binding preview，不导入、不上传、不写库。
- Adapter Readiness API：只读查询 OKX / Binance / Noop 各能力当前 readiness（no-real / fail-closed），供前端展示当前不可实盘及原因。
- Runtime Operational Readiness API：只读查询 GateM-6B 运行边界与禁用能力摘要（LIVE / AI / DH / real provider / startup / profile / config / log）。
- Actuator / Health：Spring Boot actuator、健康检查。

## 当前边界

- 正式 HTTP API 统一使用 `/api/**`。
- 旧 `/__gated/**` 只允许出现在历史文档说明和归档证据中，不属于当前可执行 API；AUDIT-FIX 后 `scripts/gated_okx_dome_verify.ps1` 仅保留阻断 stub，不再保留旧验收调用。
- AI 自动交易 API 当前不存在，也不允许在本次任务新增。
- GateH-1 只收口 Trading Workspace，不新增行情接入、dataset 绑定或 AI 自动交易接口。
- GateH-2 只新增 OKX / Binance SPOT 历史 OHLCV K 线接入、接入任务与运行记录 API；不新增 dataset/backtest 绑定接口，不新增 AI 接口。
- GateH-3 新增 marketdata dataset、quality refresh、backtest config dataset binding 与 backtest run dataset snapshot API；不新增 AI 接口。
- GateI-1 新增策略版本与发布版本绑定 API；不接 AI。
- GateI-2 增强 backtest config、backtest run 和 evaluation report 追溯 API；不进入 GateI-3/4，不接 AI。
- GateM-5A 新增只读 adapter readiness status API；只读静态 readiness 决策，no-real / fail-closed，不接 AI、不接真实交易所、不读 credential、不启用 LIVE。
- GateM-6B 新增只读 runtime operational readiness summary API；仅返回安全 DTO 摘要，不读取 raw env/config，不触发 adapter / permission probe / external exchange call，不启用 LIVE / AI / DH runtime / real provider。
- GateP Batch 2 新增只读 Marketdata Data Quality Center overview API；只读取现有本地 DB 事实，不新增 migration，不改 ingestion 行为，不接 `DataOrigin.PUBLIC_OUTBOUND` runtime provider，不表示 trading authorization。
- NQ-DH-I1-NQ-LIMITED-RUNTIME-CLIENT-IMPLEMENTATION 已实现 NQ 内部 isolated limited dry-run client，但不新增 NQ API / Controller / OpenAPI / JSON Schema / contracts / golden_cases / fixture JSON。DH endpoint `POST /api/ai/decision-dry-runs` 属 DH-only inbound limited dry-run；当前 NQ API 文档不把它写成 NQ 已实现 HTTP API，也不表示 real HTTP、real provider、Integration-1 runtime 或 LIVE 已启动。
- NQ-DH-I1-INTEGRATION1-MOCK-RUNTIME-CLOSE-REVIEW 已 `PASS / CLOSED / ACCEPTED / REVIEW_ONLY`；该关闭只代表 mock runtime / test-only 里程碑可进入 PR preparation，不新增 NQ API / Controller / OpenAPI / JSON Schema / contracts / golden_cases / fixture JSON，不表示 real HTTP、real provider、Integration-1 runtime 或 LIVE 已启动。

## NQ-DH Integration-1 Runtime Client API Boundary

`NQ-DH-I1-NQ-LIMITED-RUNTIME-CLIENT-CLOSE-REVIEW` 已 `PASS / CLOSED / ACCEPTED / REVIEW_ONLY / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE`。`NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-WO` 已 `CLOSED / ACCEPTED / WORK_ORDER_ONLY / NO_TEST_IMPLEMENTATION / NO_REAL_DH_CALL / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE`。`NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-IMPLEMENTATION` 已新增 test-only / fake-transport / MockMvc / in-memory 级别测试证据；`NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-BLOCKER-FIX` 已将 HMAC source signing / verification 统一为 wire-level canonical value `NQ_DRYRUN`，并将 NQ `DEFAULT_SCHEMA_VERSION` 对齐 DH endpoint 实际返回值 `1.0.0`。`NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TEST-CLOSE-REVIEW` 已 `PASS / CLOSED / ACCEPTED / REVIEW_ONLY / NO_REAL_DH_CALL / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE`。`NQ-DH-I1-INTEGRATION1-MOCK-RUNTIME-CLOSE-REVIEW` 已 `PASS / CLOSED / ACCEPTED / REVIEW_ONLY / MOCK_RUNTIME_MILESTONE_CLOSED / NO_REAL_DH_CALL / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE`。`NQ-DH-I1-MOCK-RUNTIME-PR-PREP` 已 `READY / PR_PREP_ONLY / ALLOW_PR_CREATE / NO_MERGE`，只准备 NQ PR title/body、diff review、security boundary 与 validation summary；不新增 NQ endpoint、NQ Controller、真实 HTTP client、schema、OpenAPI、contracts、golden_cases、fixture JSON 或 migration。当前证据不表示 real HTTP、real provider、Integration-1 runtime 或 LIVE 已启动。

当前 internal client 的唯一允许方向仍是：

```text
NQ limited dry-run client -> DH POST /api/ai/decision-dry-runs
```

该方向仍必须默认关闭、dev/test only、production disabled、kill switch fail-closed，并保持：

```text
NQ limited runtime client implementation: IMPLEMENTED / DEFAULT_DISABLED / FAKE_TRANSPORT_ONLY
NQ limited runtime client close review: PASS / CLOSED / ACCEPTED
real outbound HTTP now: NO
real provider: NO
contracts/OpenAPI/schema/golden_cases formalization now: NO
Integration-1 runtime: NOT STARTED
DH runtime integrated: NO
LIVE: DISABLED
```

`LONG_BIAS / SHORT_BIAS` 只能作为 readonly bias 记录，不得映射为 `BUY / SELL`，不得进入 order / execution / risk / ledger / paper / live 链路。invalid schemaVersion、invalid signature、source alias / lowercase source、`BUY / SELL / PLACE_ORDER / CANCEL_ORDER` response 仍 fail-closed；real DH call、real HTTP、real provider、schema/contracts/golden_cases formalization 均仍需另起任务且当前不允许。
- GateP Batch 4 新增只读 Trading Preflight readiness API；只读取 account / credential summary 与 Data Quality overview，不读取 credential material，不调用 permission probe port / adapter / RiskGate / OrderCommandService，不写库，不触发真实交易所请求，不表示 trading authorization。
- GateQ-1 新增只读 Strategy Evaluation Gate API；只读取 strategy version、dataset、evaluation、publish 与 SIM Paper 既有事实，不启动 Shadow Live runner，不启动 Paper run，不写数据库，不调用真实交易所，不启用 LIVE / AI / DH runtime，不表示 trading authorization、live enable 或 strategy live-ready。
- GateQ-2 新增只读 Paper Shadow Comparison API；只读取 strategy version、dataset、evaluation、publish 与 SIM Paper 既有事实，并把 Shadow runner / Shadow run 当前建模为 `NOT_IMPLEMENTED`（未实现）/ `BLOCKED_SHADOW_NOT_IMPLEMENTED`（Shadow 未实现阻断）/ `NOT_AVAILABLE`（不可用）。该接口不启动 Shadow runner，不创建 shadow run，不启动 Paper run，不写数据库，不调用真实交易所，不启用 LIVE / AI / DH runtime，不表示 trading authorization、live enable 或 Shadow Live ready。
- GateQ-3 新增只读 Shadow Live no-side-effect preview API；只调用 GateQ-1 / GateQ-2 只读 service 聚合既有事实，不新增 repository、SQL、migration 或 scheduler，不启动真实 Shadow runner，不创建 shadow run，不写数据库，不外联，不读取 credential material，不启用 LIVE / AI / DH runtime，不表示 trading authorization、live enable 或 Shadow Live execution ready。
- GateQ-4 新增只读 Python Evaluation Artifact Binding Preview API；只校验 request body 中的 artifact JSON，不读取本地路径，不新增 import/upload endpoint，不把 Python artifact 写成 Java fact，不写数据库，不触发 strategy publish / Paper run / Shadow run，不调用真实交易所，不启用 LIVE / AI / DH runtime，不表示 strategy approved、trading authorization、ML ready 或 live execution ready。
- GateR-6 新增 Shadow Run read-only API；只读取本地 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots` 与 `shadow_consistency_reports` 事实，不新增 migration，不新增写接口，不创建 / 启动 / 停止 / 取消 / 重跑 / 执行 Shadow runner，不调用真实交易所，不读取 credential material，不修改 account / ledger / order，不启用 LIVE / AI / DH runtime，不表示 trading authorization、live enable、trade approval 或 Shadow Live ready。
- GateR-8 新增 Shadow Run read-only list API；只读取本地 `shadow_runs` 与已脱敏本地 result summary，用于前端列表入口和 detail 跳转，不新增 migration，不新增写接口，不启动 runner，不调用真实交易所，不读取 credential material，不修改 account / ledger / order，不启用 LIVE / AI / DH runtime，不表示 trading authorization、live enable、trade approval 或 Shadow Live ready。
- GateS-1 新增 `GET /api/shadow-runs/overview` 最小后端 read model API；只读取 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports` 本地事实，不新增 migration，不新增 POST / PUT / PATCH / DELETE，不启动 runner / scheduler，不深聚合 Paper / Strategy / MarketData / Risk / Incident，不调用真实交易所，不读取 credential material，不修改 account / ledger / order，不启用 LIVE / AI / DH runtime，不表示 trading authorization、live enable、trade approval 或 Shadow Live ready。
- GateS-2 新增 `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}` 最小后端 drilldown API；只读取 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports` 本地事实，不新增 migration，不新增 POST / PUT / PATCH / DELETE，不启动 runner / scheduler，不创建 consistency report，不深聚合 Paper / Strategy / MarketData / Risk / Incident，不调用真实交易所，不读取 credential material，不修改 account / ledger / order，不启用 LIVE / AI / DH runtime，不表示 trading authorization、live enable、trade approval 或 Shadow Live ready。
- GateS-3 新增 `GET /api/strategy-validation/overview` 最小后端 Strategy Evaluation Gate runtime baseline read model；只读取 `strategy_versions`、`backtest_runs`、`backtest_eval_reports`、`backtest_publish_records`、`paper_trading_runs`、`shadow_runs`、`shadow_consistency_reports` 本地事实，不新增 migration，不新增 POST / PUT / PATCH / DELETE，不启动 evaluation / publish / Paper / Shadow run，不调用真实交易所，不读取 credential material，不修改 account / ledger / order，不启用 LIVE / AI / DH runtime，不表示 trading authorization、live enable、trade approval 或 strategy 实盘就绪。
- GateS-6 新增 `GET /api/incidents/replay/overview` 最小后端 Incident / Replay read model；只读取 `shadow_run_events`、`shadow_consistency_reports`、`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records` 本地诊断事实，不新增 migration，不新增 POST / PUT / PATCH / DELETE，不创建 incident / alert / recovery / replay，不启动 runner / scheduler，不调用真实交易所，不读取 credential material，不修改 account / ledger / order，不启用 LIVE / AI / DH runtime，不表示 trading authorization、live enable、trade approval、LIVE ready 或真实 incident runtime。
- GateT-1 新增 `GET /api/shadow-validation/workflow/overview` 最小后端 Shadow Validation Workflow read model；只读取 `strategy_versions`、`backtest_runs`、`backtest_eval_reports`、`backtest_publish_records`、`paper_trading_runs`、`shadow_runs`、`shadow_run_events`、`shadow_consistency_reports`、`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records` 本地事实，不新增 migration，不新增 POST / PUT / PATCH / DELETE，不创建 operator item / review / acknowledge / incident / alert / replay / paper run / shadow run，不启动 runner / scheduler，不调用真实交易所，不读取 credential material，不修改 account / ledger / order，不启用 LIVE / AI / DH runtime，不表示 trading authorization、live enable、trade approval、LIVE ready 或 Shadow trading enabled。

## GateR-6 / GateR-8 Shadow Run Read-only API

NQ-GATER-6-SHADOW-RUN-READ-ONLY-API-IMPLEMENTATION 当前状态：`IMPLEMENTED`（已实现）/ `PUSHED`（已推送）/ `CI SUCCESS`（CI 成功）。NQ-GATER-8-SHADOW-RUN-LIST-AND-ENTRYPOINT-IMPLEMENTATION 当前状态：`IMPLEMENTED` / `PUSHED` / `CI SUCCESS`。GateR 当前状态为 `FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag 为 `nq-gater-freeze`。该状态只覆盖 Shadow Run 只读 API、DTO、Controller、query service、前端只读列表入口与测试，不代表 Shadow Run scheduler、后台 runner、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter 或 real permission probe 已启动。

- `GET /api/shadow-runs`：读取本地 Shadow Run list。
  - Query：`status` 可选；`strategyVersionId` 可选；`datasetId` 可选；`paperRunId` 可选；`limit` 默认 50、最大 100；`offset` 默认 0。
  - Response：`items / limit / offset / total`；item 含 `id / status / strategyVersionId / datasetId / paperRunId / authorizationBoundary / traceId / createdAt / updatedAt / startedAt / completedAt / blockersCount / warningsCount / nextStepsCount / noOrderSubmission / noCredentialAccess / noPrivateEndpoint / noLedgerMutation / noAccountMutation`。
  - 语义：只读 diagnostic only，用于列表查看和进入 detail / replay 页面；`authorizationBoundary` 与 no-side-effect flags 只表达诊断边界，不表达交易授权。
- `GET /api/shadow-runs/{id}`：读取本地 Shadow Run detail。
  - Response：`id / strategyVersionId / datasetId / evaluationId / publishId / paperRunId / status / windowStart / windowEnd / authorizationBoundary / sideEffectFlags / blockers / warnings / nextSteps / requestId / traceId / createdAt / updatedAt / startedAt / stoppedAt / completedAt`。
  - `authorizationBoundary` 只表达边界说明，固定不得解释为交易授权；`sideEffectFlags` 只表达 no-order-submission、no-credential-access、no-private-endpoint、no-ledger-mutation、no-account-mutation、no-external-private-io 等只读诊断边界。
- `GET /api/shadow-runs/{id}/events`：读取本地 Shadow Run lifecycle / audit events。
  - Response item：`eventType / fromStatus / toStatus / reasonCode / message / metadata / requestId / traceId / createdAt`。
  - `metadata` 在 domain 写入和 DTO 映射阶段均复用 sensitive guard，不允许原样返回敏感字段。
- `GET /api/shadow-runs/{id}/snapshots`：读取本地 Shadow Run snapshots。
  - Response item：`snapshotType / sequenceNo / source / schemaVersion / checksum / payload / capturedAt / traceId`。
  - `payload` 只用于本地 replay / diagnostic 展示，必须保持脱敏，不得包含 private endpoint payload、credential material、real account/order/position facts 或交易放行字段。
- `GET /api/shadow-runs/{id}/consistency-report/latest`：读取本地 latest Paper vs Shadow consistency report。
  - Response：`id / shadowRunId / paperRunId / comparisonStatus / metricDelta / divergenceReasons / limitations / generatedAt / traceId`。
  - `comparisonStatus` 仅表达 `CONSISTENT / DIVERGED / NOT_COMPARABLE / PARTIAL / FAILED` 等诊断结果，不代表 approval、authorization、LIVE ready 或 trade approved。
- Not found：Shadow Run 不存在，或 latest consistency report 不存在，返回项目统一 not found 语义（HTTP 404 / `RESOURCE_NOT_FOUND`）。
- 固定禁止：本 API 不提供 `POST /api/shadow-runs`、`start`、`stop`、`cancel`、`rerun`、`execute`、`trade`、`placeOrder`、`cancelOrder`、`withdraw`、`transfer` 或任何写侧 / 交易动作 endpoint。
- Response 禁止字段：`apiKey`、`secret`、`passphrase`、`token`、`privateKey`、`credentialMaterial`、`decryptedPayload`、`encryptedPayload` 真实值、`rawPrivateRequest`、`rawPrivateResponse`、private endpoint payload、`realOrderId`、`realAccountBalance`、`realPosition`、`tradingReady`、`liveReady`、`authorizedForTrading`、`tradeApproved`、order execution command、private adapter reference。

## GateS-1 Shadow Run Overview Read-only API

NQ-GATES-1-READ-MODEL-IMPLEMENTATION 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可提交前复核）。该状态只覆盖最小后端 read model、GET-only Controller/DTO、core query service / query port、JDBC SELECT-only adapter 和后端测试；不代表 GateS-1 `FROZEN`（已冻结）或 `ACCEPTED`（已接受），不代表 frontend page、GateS 全域 overview、Strategy Validation runtime、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter 或真实 permission probe 已启动。

- `GET /api/shadow-runs/overview`：只读聚合本地 Shadow Run overview。
  - Query：无请求参数；不接受 request body。
  - Response：`generatedAt / diagnosticOnly / noSideEffect / notTradingAuthorization / liveDisabled / realProviderImplemented / privateTradingImplemented / aiDhRuntimeIntegrated / totalRuns / runningRuns / blockedRuns / failedRuns / completedRuns / staleRuns / latestRun / latestConsistency / divergenceSeverity / blockers / warnings / nextSteps / evidenceAnchors / traceId`。
  - `latestRun`：`shadowRunId / strategyVersionId / datasetId / paperRunId / status / authorizationBoundary / noOrderSubmission / noCredentialAccess / noPrivateEndpoint / noLedgerMutation / noAccountMutation / noExternalPrivateIo / createdAt / updatedAt / startedAt / completedAt`。
  - `latestConsistency`：`reportId / shadowRunId / paperRunId / comparisonStatus / metricDelta / divergenceReasons / limitations / generatedAt / traceId`。
  - `divergenceSeverity`：`NONE / LOW / MEDIUM / HIGH / CRITICAL / UNKNOWN`，由 latest consistency report 派生；无 report 时为 `UNKNOWN`（未知）。该字段只用于诊断排序，不回写 `shadow_runs.status`。
  - `comparisonStatus` 只表达证据层状态：`CONSISTENT / DIVERGED / PARTIAL / NOT_COMPARABLE / FAILED`；`STALE_EVIDENCE`（证据过期）当前只作为 read model warning code，不新增表字段。
  - 固定 boundary flags：`diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`。
  - 固定 blockers / warnings 至少包含：`LIVE_DISABLED`、`REAL_PROVIDER_NOT_IMPLEMENTED`、`PRIVATE_TRADING_NOT_IMPLEMENTED`、`SHADOW_RUN_DIAGNOSTIC_ONLY`；缺 report 时返回 `CONSISTENCY_REPORT_MISSING` warning；存在 run 但缺 snapshot 或 report 时返回 `STALE_EVIDENCE` warning。
  - `nextSteps` 仅允许 review / inspect / compare / investigate / implement_frontend_readonly_overview 等诊断动作，不提供交易、批准、放行、执行、下单、撤单、转账或提现动作。
  - `evidenceAnchors` 只引用 `SHADOW_RUN`、`SHADOW_EVENT`、`SHADOW_SNAPSHOT`、`SHADOW_CONSISTENCY_REPORT` 以及 `PAPER_RUN` / `STRATEGY_VERSION` / `DATASET` id anchor；不读取 Paper / Strategy / MarketData / Risk / Incident 域做深聚合。
- 数据来源：仅 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports`。
- No-side-effect：Controller 只有 GET；service/repository 只做 SELECT；不 INSERT / UPDATE / DELETE；不 create shadow run；不 append event / snapshot / report；不启动 runner / scheduler；不调用 adapter、risk write side、order/account/ledger 服务。
- 固定禁止：不提供 `POST /api/shadow-runs/overview`、`PUT`、`PATCH`、`DELETE`、`start`、`stop`、`cancel`、`rerun`、`execute`、`trade`、`placeOrder`、`cancelOrder`、`withdraw`、`transfer` 或任何写侧 / 交易动作 endpoint。
- Response 禁止字段：`apiKey`、`secret`、`passphrase`、`token`、`privateKey`、`rawSignature`、`rawPrivateRequest`、`rawPrivateResponse`、`credentialMaterial`、`decryptedPayload`、`encryptedPayload` 真实值、private endpoint 原始载荷、`realOrderId`、`realAccountBalance`、`realPosition`、`withdrawAddress`、`transferTarget`、`tradeApproved`、`tradingReady`、`liveReady`、`authorizedForTrading`。
- GateS-1 frontend overview 已另行实现；`GET /api/gates/s/overview`、`GET /api/validation/overview`、`GET /api/validation/strategies/{strategyVersionId}`、`GET /api/risk/preflight/blockers`、`GET /api/incidents/replay` 均未实现。

## GateS-2 Paper Shadow Consistency Drilldown API

NQ-GATES-2-PAPER-SHADOW-CONSISTENCY-DRILLDOWN-IMPLEMENTATION 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可进入提交前复核）。该状态只覆盖最小后端 GET-only drilldown endpoint、DTO、core query service / query port、JDBC SELECT-only adapter 和后端测试；不代表 GateS-2 `FROZEN`（已冻结）或 `ACCEPTED`（已接受），不代表前端页面、Dashboard v2、Shadow runner、scheduler、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter 或真实 permission probe 已启动。

- `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}`：只读聚合单个 Shadow Run 的 Paper vs Shadow consistency drilldown。
  - Query：`shadowRunId` 必填，类型为 UUID；不接受 request body。
  - Response：`generatedAt / diagnosticOnly / noSideEffect / notTradingAuthorization / liveDisabled / realProviderImplemented / privateTradingImplemented / aiDhRuntimeIntegrated / shadowRun / latestConsistency / comparisonStatus / divergenceSeverity / metricDelta / divergenceReasons / limitations / snapshotSummary / eventSummary / blockers / warnings / nextSteps / evidenceAnchors / traceId`。
  - `shadowRun`：`shadowRunId / strategyVersionId / datasetId / evaluationId / publishId / paperRunId / status / authorizationBoundary / noOrderSubmission / noCredentialAccess / noPrivateEndpoint / noLedgerMutation / noAccountMutation / noExternalPrivateIo / createdAt / updatedAt / startedAt / completedAt`。
  - `latestConsistency`：`reportId / shadowRunId / paperRunId / comparisonStatus / metricDelta / divergenceReasons / limitations / generatedAt / traceId`；无本地 report 时为 `null`。
  - `comparisonStatus` 只表达证据层状态：`CONSISTENT / DIVERGED / PARTIAL / NOT_COMPARABLE / FAILED / STALE_EVIDENCE / NO_REPORT`；当前无 report 时返回 `NO_REPORT`（无报告），snapshot 缺失通过 `INCOMPLETE_SNAPSHOT_EVIDENCE` warning 表达，不伪造一致性。
  - `divergenceSeverity`：`NONE / LOW / MEDIUM / HIGH / CRITICAL / UNKNOWN`；映射规则为 `CONSISTENT -> NONE`、`PARTIAL -> LOW/MEDIUM`、`NOT_COMPARABLE -> MEDIUM`、`DIVERGED -> HIGH`、`FAILED -> CRITICAL`、无 report -> `UNKNOWN`（未知）。
  - `snapshotSummary` 只返回 `totalSnapshots / inputMarketdataSnapshots / strategyDecisionSnapshots / riskPreflightSnapshots / orderIntentPreviewSnapshots / latestSnapshotAt / latestSnapshotTypes`，不返回 snapshot payload。
  - `eventSummary` 只返回 `totalEvents / latestEventAt / latestEventType / latestReasonCode`，不追加 event。
  - 固定 boundary flags：`diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`。
  - 固定 blockers 至少包含：`LIVE_DISABLED`、`REAL_PROVIDER_NOT_IMPLEMENTED`、`PRIVATE_TRADING_NOT_IMPLEMENTED`、`SHADOW_RUN_DIAGNOSTIC_ONLY`、`NOT_TRADING_AUTHORIZATION`。
  - 无 consistency report 时返回 `NO_CONSISTENCY_REPORT` warning，并给出 `Generate or inspect consistency report in future GateS batch` nextStep；本接口不会自动生成 report。
  - snapshot 证据不完整时返回 `INCOMPLETE_SNAPSHOT_EVIDENCE` warning，并给出 `Inspect shadow snapshots` nextStep；本接口不会自动创建 snapshot。
  - `evidenceAnchors` 只引用 `SHADOW_RUN`、`SHADOW_CONSISTENCY_REPORT`、`SHADOW_EVENT`、`SHADOW_SNAPSHOT` 以及 `PAPER_RUN` / `STRATEGY_VERSION` / `DATASET` / `EVALUATION` / `PUBLISH` id anchor；不读取 Paper / Strategy / MarketData / Risk / Incident 域做深聚合。
- Not found：`shadowRunId` 不存在时返回项目统一 not found 语义（HTTP 404 / `RESOURCE_NOT_FOUND`），不能 500。
- 数据来源：仅 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports`。
- No-side-effect：Controller 只有 GET；service / repository 只做 SELECT；不 INSERT / UPDATE / DELETE；不 create shadow run；不 append event / snapshot / report；不启动 runner / scheduler；不调用 adapter、risk write side、order/account/ledger 服务。
- 固定禁止：不提供 `POST`、`PUT`、`PATCH`、`DELETE`、`start`、`stop`、`cancel`、`rerun`、`execute`、`trade`、`placeOrder`、`cancelOrder`、`withdraw`、`transfer` 或任何写侧 / 交易动作 endpoint。
- Response 禁止字段：`apiKey`、`secret`、`passphrase`、`token`、`privateKey`、`rawSignature`、`rawPrivateRequest`、`rawPrivateResponse`、`credentialMaterial`、`decryptedPayload`、`encryptedPayload` 真实值、private endpoint payload、`realOrderId`、`realAccountBalance`、`realPosition`、`withdrawAddress`、`transferTarget`、`tradeApproved`、`tradingReady`、`liveReady`、`authorizedForTrading`。

## GateS-3 Strategy Validation Overview Read-only API

NQ-GATES-3-STRATEGY-EVALUATION-GATE-RUNTIME-BASELINE 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可进入提交前复核）。该状态只覆盖最小后端 GET-only overview endpoint、DTO、core query service / query port、JDBC SELECT-only adapter 和后端测试；不代表 GateS-3 `FROZEN`（已冻结）或 `ACCEPTED`（已接受），不代表前端页面、Dashboard v2、scheduler、runner、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter 或真实 permission probe 已启动。

- `GET /api/strategy-validation/overview`：只读聚合 Strategy Evaluation Gate runtime baseline 概览。
  - Query：无请求参数；不接受 request body。
  - Response：`generatedAt / diagnosticOnly / noSideEffect / notTradingAuthorization / liveDisabled / realProviderImplemented / privateTradingImplemented / aiDhRuntimeIntegrated / totalStrategyVersions / evaluatedStrategyVersions / approvedForValidation / rejectedForValidation / needsReview / blocked / latestDecision / blockers / warnings / nextSteps / evidenceAnchors / traceId`。
  - `latestDecision`：`strategyVersionId / datasetId / evaluationReportId / publishId / paperRunId / shadowRunId / decision / decisionReasons / limitations / generatedAt / traceId`；无任何 strategy version 或 evaluation evidence 时为 `null`。
  - `decision` 仅表达 validation evidence 状态：`APPROVED`（验证层通过）、`REJECTED`（验证层拒绝）、`NEEDS_REVIEW`（需要人工复核）、`BLOCKED`（证据阻断）、`NO_EVIDENCE`（无证据）、`STALE_EVIDENCE`（证据不完整或过期）。`APPROVED` 只表示 validation 层 evidence 暂时满足后续 review，不表示 trading authorization、LIVE enable、strategy 实盘就绪或 trade approval。
  - 固定 boundary flags：`diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`。
  - `blockers` 只表达证据阻断，例如缺 strategy version、evaluation failed、Shadow evidence failed；`warnings` 固定包含 `LIVE_DISABLED`、`REAL_PROVIDER_NOT_IMPLEMENTED`、`PRIVATE_TRADING_NOT_IMPLEMENTED`、`VALIDATION_IS_NOT_TRADING_AUTHORIZATION`，避免把 validation 状态误读成交易准入。
  - `nextSteps` 仅允许 inspect / review / investigate / collect evidence / compare consistency 等诊断动作；不提供交易、放行、执行、下单、撤单、转账或提现动作。
  - `evidenceAnchors` 只引用 `STRATEGY_VERSION`、`DATASET`、`EVALUATION_REPORT`、`PUBLISH_RECORD`、`PAPER_RUN`、`SHADOW_RUN`、`SHADOW_CONSISTENCY_REPORT` 本地 id anchor；不返回 payload，不读取 credential、account、order、ledger 或 private provider facts。
- 数据来源：仅 `strategy_versions`、`backtest_runs`、`backtest_eval_reports`、`backtest_publish_records`、`paper_trading_runs`、`shadow_runs`、`shadow_consistency_reports`。
- No-side-effect：Controller 只有 GET；service 标记 read-only；repository 只做 SELECT；不 INSERT / UPDATE / DELETE；不 create evaluation / publish / Paper run / Shadow run；不 append event / snapshot / report；不启动 runner / scheduler；不调用 adapter、risk write side、order/account/ledger 服务。
- 固定禁止：不提供 `POST`、`PUT`、`PATCH`、`DELETE` 或任何写侧 / 交易动作 endpoint。
- Response 禁止字段：`canTrade`、`tradeApproved`、`tradingReady`、`liveReady`、`authorizedForTrading`、`apiKey`、`secret`、`passphrase`、`token`、`privateKey`、`rawSignature`、`rawPrivateRequest`、`rawPrivateResponse`、`credentialMaterial`、`decryptedPayload`、`encryptedPayload` 真实值、private endpoint payload、`realOrderId`、`realAccountBalance`、`realPosition`、`withdrawAddress`、`transferTarget`。

## GateS-6 Incident Replay Overview Read-only API

NQ-GATES-6-INCIDENT-REPLAY-READ-MODEL-IMPLEMENTATION 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可进入提交前复核）。该状态只覆盖最小后端 GET-only overview endpoint、DTO、core query service / query port、JDBC SELECT-only adapter 和后端测试；不代表 GateS-6 `FROZEN`（已冻结）或 `ACCEPTED`（已接受），不代表前端页面、Dashboard v2、scheduler、runner、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter 或真实 permission probe 已启动。

- `GET /api/incidents/replay/overview`：只读聚合 Incident / Replay 诊断概览。
  - Query：无请求参数；不接受 request body。
  - Response：`generatedAt / diagnosticOnly / noSideEffect / notTradingAuthorization / liveDisabled / realProviderImplemented / privateTradingImplemented / aiDhRuntimeIntegrated / totalEvidenceItems / shadowEventCount / consistencyDivergenceCount / paperAlertCount / recoveryEventCount / replayEventCount / latestEvidence / incidentSeverity / blockers / warnings / nextSteps / evidenceAnchors / traceId`。
  - `latestEvidence`：`source / sourceId / severity / status / summary / occurredAt / traceId`；summary 只返回规则化诊断摘要，不返回 raw payload、credential、account、order、ledger 或 private provider facts。
  - `incidentSeverity` 仅表达诊断排序：`NONE / INFO / WARNING / HIGH / CRITICAL / UNKNOWN`。`CRITICAL` 或 `HIGH` 只表示本地诊断证据需要排查，不表示交易授权、LIVE enable、真实 incident runtime 或自动处置。
  - 固定 boundary flags：`diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`。
  - `blockers` 固定包含 `LIVE_DISABLED`、`REAL_PROVIDER_NOT_IMPLEMENTED`、`PRIVATE_TRADING_NOT_IMPLEMENTED`、`NOT_TRADING_AUTHORIZATION`。
  - `warnings` 至少包含 `INCIDENT_REPLAY_DIAGNOSTIC_ONLY`；由于当前没有独立 incident 表和 runtime readiness incident fact table，还会返回 `SOURCE_NOT_AVAILABLE` warning，避免把本地 replay 证据误写成真实 incident runtime。
  - `nextSteps` 仅允许 review / inspect / keep read model GET-only / implement dedicated incident source later 等诊断动作；不提供交易、放行、执行、下单、撤单、转账或提现动作。
  - `evidenceAnchors` 只引用 `SHADOW_EVENT`、`SHADOW_CONSISTENCY_REPORT`、`PAPER_RUN_ALERT`、`PAPER_RUN_RECOVERY_EVENT`、`TRADE_REPLAY_RECORD` 本地 id anchor；不返回 raw JSON payload。
- 数据来源：仅 `shadow_run_events`、`shadow_consistency_reports`、`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records`。
- No-side-effect：Controller 只有 GET；service 标记 read-only；repository 只做 SELECT；不 INSERT / UPDATE / DELETE；不 create incident / alert / recovery / replay；不 append event / snapshot / report；不启动 runner / scheduler；不调用 adapter、risk write side、order/account/ledger 服务。
- 固定禁止：不提供 `POST`、`PUT`、`PATCH`、`DELETE` 或任何写侧 / 交易动作 endpoint。
- Response 禁止字段：`canTrade`、`tradeApproved`、`tradingReady`、`liveReady`、`authorizedForTrading`、`apiKey`、`secret`、`passphrase`、`token`、`privateKey`、`rawSignature`、`rawPrivateRequest`、`rawPrivateResponse`、`credentialMaterial`、`decryptedPayload`、`encryptedPayload` 真实值、private endpoint payload、`realOrderId`、`realAccountBalance`、`realPosition`、`withdrawAddress`、`transferTarget`。

## GateT-1 Shadow Validation Workflow Overview Read-only API

NQ-GATET-1-SHADOW-VALIDATION-WORKFLOW-READ-MODEL-IMPLEMENTATION 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可进入提交前复核）。该状态只覆盖最小后端 GET-only overview endpoint、DTO、core query service / query port、JDBC SELECT-only adapter 和后端测试；不代表 GateT `FROZEN`（已冻结）或 `ACCEPTED`（已接受），不代表前端 workbench、operator review 写侧、scheduler readiness、Python binding、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter 或真实 permission probe 已启动。

- `GET /api/shadow-validation/workflow/overview`：只读聚合 GateS 本地 facts 并派生 Shadow Validation Workflow operator items。
  - Query：无请求参数；不接受 request body。
  - Response：`generatedAt / diagnosticOnly / noSideEffect / notTradingAuthorization / liveDisabled / realProviderImplemented / privateTradingImplemented / aiDhRuntimeIntegrated / totalOperatorItems / intakeCount / evidenceReviewCount / needsEvidenceCount / readyForOperatorReviewCount / blockedCount / closedRecommendationCount / latestOperatorItem / operatorItems / blockers / warnings / nextSteps / evidenceAnchors / traceId`。
  - `operatorItems`：`operatorItemId / sourceType / sourceId / strategyVersionId / datasetId / evaluationReportId / paperRunId / shadowRunId / consistencyReportId / incidentEvidenceId / workflowState / validationDecision / severity / evidenceFreshness / blockers / warnings / nextSteps / evidenceAnchors / traceId / generatedAt / diagnosticOnly / noSideEffect / notTradingAuthorization / liveDisabled / realProviderImplemented / privateTradingImplemented / aiDhRuntimeIntegrated`。
  - `workflowState` 当前取值：`INTAKE`（进入初始诊断）、`EVIDENCE_REVIEW`（需要证据审查）、`NEEDS_EVIDENCE`（需要补充证据）、`READY_FOR_OPERATOR_REVIEW`（可人工复核）、`BLOCKED`（被本地诊断事实阻断）、`CLOSED_RECOMMENDATION`（诊断闭环建议）。
  - `validationDecision` 当前取值：`NO_DECISION`（无验证决策）、`VALIDATION_READY`（验证材料可进入人工复核）、`NEEDS_REVIEW`（需要复核）、`REJECTED`（验证材料拒绝）、`BLOCKED`（阻断）、`STALE_EVIDENCE`（证据过旧）。
  - `severity` 当前取值：`NONE`（无优先级）、`INFO`（信息）、`WARNING`（警告）、`HIGH`（高优先级）、`CRITICAL`（严重）、`UNKNOWN`（未知）。severity 只表示诊断优先级，不表示交易风险已处理。
  - `VALIDATION_READY` 只表示材料可进入人工复核，不表示交易授权；`READY_FOR_OPERATOR_REVIEW` 只表示可人工复核，不表示可交易；`CLOSED_RECOMMENDATION` 只表示诊断闭环建议，不表示自动处置完成。
  - 固定 boundary flags：`diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`。
- 数据来源：仅允许读取 `strategy_versions`、`backtest_runs`、`backtest_eval_reports`、`backtest_publish_records`、`paper_trading_runs`、`shadow_runs`、`shadow_run_events`、`shadow_consistency_reports`、`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records`。
- No-side-effect：Controller 只有 GET；service 标记 read-only；repository 只做 SELECT；不 INSERT / UPDATE / DELETE；不持久化 operator item、review 或 acknowledge；不创建 incident、alert、replay、Paper run 或 Shadow run；不 append event / snapshot / report；不启动 runner / scheduler；不调用 adapter、risk write side、order/account/ledger 服务。
- 固定禁止：不提供 `POST`、`PUT`、`PATCH`、`DELETE` 或任何写侧 / 交易动作 endpoint。
- Response 禁止字段：`canTrade`、`tradeApproved`、`tradingReady`、`liveReady`、`authorizedForTrading`、`apiKey`、`secret`、`passphrase`、`token`、`privateKey`、`credentialMaterial`、`decryptedPayload`、`encryptedPayload` 真实值、private endpoint payload、`realOrderId`、`realAccountBalance`、`realPosition`、`withdrawAddress`、`transferTarget`。

## GateQ-1 Strategy Evaluation Gate Read-only API

NQ-GATEQ-1-STRATEGY-EVALUATION-GATE-READONLY-BASELINE 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可提交前复核）。该状态只覆盖本轮后端只读 baseline，不代表 GateQ 整体已实现、冻结或接受。

- `GET /api/strategies/evaluation-gate`：按当前本地 facts 聚合 strategy version、dataset quality、evaluation report、publish trace 与 SIM Paper evidence。该接口只读，不写库，不创建或启动 backtest / evaluation / publish / Paper / Shadow run，不调用 adapter，不访问外部网络，不读取 credential material，不启用 LIVE / AI / DH runtime。
  - Query：`strategyId` 可选，仅用于 scope 校验和回显；`strategyVersionId` 为核心查询字段，缺失时 fail-closed；`datasetId` 可选但缺失或不存在会阻断；`evaluationId`、`publishId`、`paperRunId` 均可选，repository 只在本地表中按 strategyVersion/publish/evaluation 尝试解析既有事实。
  - Response：`scope / strategyId / strategyVersionId / datasetId / evaluationId / publishId / paperRunId / gateStatus / gateDecision / evaluationStatus / datasetQualityStatus / paperEvidenceStatus / publishTraceStatus / requiredEvidence / missingEvidence / blockers / warnings / nextSteps / generatedAt`。
  - `gateStatus` 当前语义：`READY_FOR_SHADOW_REVIEW`（可进入后续 Shadow review）、`BLOCKED_MISSING_STRATEGY_VERSION`（缺少或找不到策略版本）、`BLOCKED_MISSING_DATASET`（缺少 dataset）、`BLOCKED_MISSING_EVALUATION`（缺少 evaluation）、`BLOCKED_EVALUATION_FAILED`（evaluation 失败）、`BLOCKED_DATA_QUALITY`（数据质量不足）、`BLOCKED_MISSING_PAPER_EVIDENCE`（缺少 Paper evidence）、`BLOCKED_NOT_PUBLISHED`（缺少成功 publish trace）、`UNKNOWN`（未知）、`NOT_AVAILABLE`（不可用）。
  - `gateDecision` 当前语义：`RESEARCH_EVALUATION_READY_FOR_SHADOW_REVIEW`（研究评估证据可进入后续 review）、`RESEARCH_EVALUATION_BLOCKED`（研究评估证据阻断）、`RESEARCH_EVALUATION_UNKNOWN`（研究评估未知）、`RESEARCH_EVALUATION_NOT_AVAILABLE`（研究评估不可用）。
  - Fail-closed 规则：`strategyVersionId` 缺失、strategy version 不存在或不为 `ACTIVE`、strategyId 归属不匹配、dataset 缺失、dataset 非 `READY/OK` 或 coverage 有缺口/异常、evaluation 缺失、evaluation 非 `SUCCEEDED`、publish trace 缺失或非 `SUCCEEDED`、SIM Paper evidence 缺失或不足，均返回阻断状态，不伪造 ready。
  - `READY_FOR_SHADOW_REVIEW` 仅表示“研究与评估证据可进入后续 Shadow review”。它不代表交易授权、不代表 LIVE enable、不代表 strategy live-ready、不允许启动 Shadow Live runner，也不允许真实下单、撤单、转账或提现。
  - Response 不得包含 `tradingReady`、`liveReady`、`authorizedForTrading`、`apiKey`、`secret`、`token`、`passphrase`、`private key`、`encrypted_payload`、`decrypted_payload` 或 raw provider payload；也不得返回 `LIVE_READY`、`TRADE_APPROVED` 或 `AUTHORIZED` 放行语义。

## GateQ-2 Paper Shadow Comparison Read-only API

NQ-GATEQ-2-PAPER-SHADOW-RUN-READONLY-MODEL-AND-DTO 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可提交前复核）。该状态只覆盖本轮后端只读 baseline，不代表 GateQ 整体已实现、冻结或接受。

- `GET /api/strategies/paper-shadow/comparison`：按当前本地 facts 聚合 strategy version、dataset quality、evaluation report、publish trace、SIM Paper evidence 与 Shadow 未实现状态。该接口只读，不写库，不创建或启动 backtest / evaluation / publish / Paper / Shadow run，不调用 adapter，不访问外部网络，不读取 credential material，不启用 LIVE / AI / DH runtime。
  - Query：`strategyId` 可选，仅用于 scope 校验和回显；`strategyVersionId` 为核心查询字段，缺失时 fail-closed；`datasetId`、`evaluationId`、`publishId`、`paperRunId`、`shadowRunId` 均可选，repository 只在现有本地表中按 strategyVersion / publish / evaluation 解析既有 facts。当前没有 shadow run 表或 shadow runner，生产 repository 固定返回 Shadow fact source `NOT_IMPLEMENTED`。
  - Response：`scope / strategyId / strategyVersionId / datasetId / evaluationId / publishId / paperRunId / shadowRunId / paperRunStatus / shadowRunStatus / comparisonStatus / evaluationGateStatus / paperEvidenceStatus / shadowEvidenceStatus / dataQualityStatus / comparable / requiredEvidence / missingEvidence / blockers / warnings / nextSteps / generatedAt`。
  - `comparisonStatus` 当前语义：`READY_FOR_COMPARISON`（只读对照证据可查看）、`BLOCKED_MISSING_STRATEGY_VERSION`（缺少或找不到策略版本）、`BLOCKED_EVALUATION_GATE`（evaluation gate 阻断）、`BLOCKED_MISSING_PAPER_RUN`（缺少可比较 Paper run）、`BLOCKED_SHADOW_NOT_IMPLEMENTED`（Shadow runner / fact source 未实现）、`BLOCKED_MISSING_SHADOW_RUN`（Shadow fact source 存在后缺少 Shadow run）、`BLOCKED_DATA_QUALITY`（数据质量不足）、`BLOCKED_TRACE_INCOMPLETE`（追溯链不完整）、`UNKNOWN`（未知）、`NOT_AVAILABLE`（不可用）、`NOT_IMPLEMENTED`（未实现）。
  - Fail-closed 规则：`strategyVersionId` 缺失、strategy version 不存在或不为 `ACTIVE`、strategyId 归属不匹配、dataset 缺失、dataset 非 `READY/OK` 或 coverage 有缺口/异常、evaluation 缺失或非 `SUCCEEDED`、publish trace 缺失或非 `SUCCEEDED`、SIM Paper run 缺失或不可比较、Shadow runner 未实现、Shadow run 缺失、trace chain 不完整，均返回阻断状态，不伪造 ready。
  - `READY_FOR_COMPARISON` 仅表示“Paper / Shadow 只读对照证据可查看”。它不代表交易授权、不代表 LIVE enable、不代表 Shadow Live ready、不允许启动 Shadow runner，也不允许真实下单、撤单、转账或提现。
  - 当前生产行为：即使 strategy version / dataset / evaluation / publish / SIM Paper evidence 均满足，因 Shadow runner / Shadow fact source 未实现，仍返回 `BLOCKED_SHADOW_NOT_IMPLEMENTED`，`shadowRunStatus=NOT_IMPLEMENTED`，`shadowEvidenceStatus=NOT_IMPLEMENTED`，`comparable=false`。
  - Response 不得包含 `tradingReady`、`liveReady`、`authorizedForTrading`、`apiKey`、`secret`、`token`、`passphrase`、`private key`、`encrypted_payload`、`decrypted_payload` 或 raw provider payload；也不得返回 `LIVE_READY`、`TRADE_APPROVED` 或 `AUTHORIZED` 放行语义。

## GateQ-3 Shadow Live No-side-effect Preview API

NQ-GATEQ-3-SHADOW-LIVE-NO-SIDE-EFFECT-RUNNER-SKELETON 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可提交前复核）。该状态只覆盖本轮 Shadow Live no-side-effect runner skeleton 与只读 preview API，不代表 GateQ 整体已冻结或接受，不代表真实 Shadow Live runner 已启动。

- `GET /api/strategies/shadow-live/preview`：按当前本地 facts 聚合 GateQ-1 Strategy Evaluation Gate 与 GateQ-2 Paper Shadow Comparison 的只读结果，返回 validation、readiness、trace preview、blocked reason、side-effect policy 和 next steps。该接口只读，不写库，不新增 shadow facts，不创建或启动 backtest / evaluation / publish / Paper / Shadow run，不执行策略，不生成真实订单，不调用 adapter，不访问外部网络，不读取 credential material，不启用 LIVE / AI / DH runtime。
  - Query：`strategyId` 可选，仅用于 scope 校验和回显；`strategyVersionId` 为核心查询字段，缺失时 fail-closed；`datasetId`、`evaluationId`、`publishId`、`paperRunId`、`shadowRunId` 均可选，service 只把它们传递给 GateQ-1 / GateQ-2 只读聚合，不创建任何新事实。
  - Response：`scope / strategyId / strategyVersionId / datasetId / evaluationId / publishId / paperRunId / shadowRunId / runnerStatus / previewStatus / evaluationGateStatus / paperShadowComparisonStatus / sideEffectPolicy / inputFactStatus / traceStatus / orderIntentPreviewStatus / riskPreflightPreviewStatus / requiredEvidence / missingEvidence / blockers / warnings / nextSteps / generatedAt`。
  - `runnerStatus` 当前固定为 `SKELETON_AVAILABLE`（骨架可用）；含义仅是 no-side-effect preview skeleton 可返回诊断，不代表真实 runner、真实 Shadow Live 执行或交易路径可用。
  - `previewStatus` 当前语义：`READY_FOR_NO_SIDE_EFFECT_PREVIEW`（可生成只读预览）、`PREVIEW_BLOCKED_EVALUATION_GATE`（evaluation gate 阻断）、`PREVIEW_BLOCKED_PAPER_SHADOW_COMPARISON`（Paper/Shadow comparison 阻断）、`PREVIEW_BLOCKED_MISSING_STRATEGY_VERSION`（缺少或找不到 strategy version）、`PREVIEW_BLOCKED_DATA_QUALITY`（数据质量不足）、`PREVIEW_BLOCKED_MISSING_PAPER_EVIDENCE`（缺少 Paper evidence）、`PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE`（Shadow facts 不可用）、`PREVIEW_BLOCKED_TRACE_CHAIN_INCOMPLETE`（追溯链不完整）、`UNKNOWN`（未知）、`NOT_AVAILABLE`（不可用）。
  - `READY_FOR_NO_SIDE_EFFECT_PREVIEW` 仅代表“已有事实最多允许生成只读预览计划”。它不代表交易授权、不代表实盘放行、不代表 Shadow Live 交易启用、不允许启动 Shadow runner，也不允许下单、撤单、转账或提现。
  - `sideEffectPolicy` 当前固定全部 `FORBIDDEN`，包含 `NO_DB_WRITE / NO_EXTERNAL_IO / NO_CREDENTIAL_ACCESS / NO_PRIVATE_ENDPOINT / NO_ORDER_SUBMISSION / NO_LEDGER_MUTATION / NO_ACCOUNT_MUTATION`。
  - `orderIntentPreviewStatus` 当前固定为 `NOT_EXECUTED`；`riskPreflightPreviewStatus` 仅在 ready 时可为 `PREVIEW_ONLY`，否则为 `NOT_EXECUTED`。本轮不生成真实策略信号、真实执行建议、真实 order intent 或 buy/sell/market order 级别建议。
  - Fail-closed 规则：缺少或无法解析 `strategyVersionId`、evaluation gate 未通过、Paper/Shadow comparison 阻断、dataset 不存在或数据质量不足、publish trace 不存在、Paper run 不存在或不可比较、Shadow facts 不存在、trace chain 不完整、任一 side-effect policy 不能证明 forbidden，均返回阻断或不可用状态，不伪造 ready。
  - Response 不得包含 `tradingReady`、`liveReady`、`authorizedForTrading`、`apiKey`、`secret`、`token`、`passphrase`、`private key`、`encrypted_payload`、`decrypted_payload` 或 raw provider payload；也不得返回 `LIVE_READY`、`TRADE_APPROVED` 或 `AUTHORIZED` 放行语义。

## GateQ-4 Python Evaluation Artifact Binding Preview API

NQ-GATEQ-4-PYTHON-EVALUATION-ARTIFACT-JAVA-BINDING-CONTRACT 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可提交前复核）。该状态只覆盖 Python offline evaluation artifact 到 Java fact source 的只读绑定预览契约 baseline，不代表 GateQ 整体冻结或接受。

- `POST /api/research/evaluation-artifacts/binding-preview`：只校验 request body 中的 Python offline evaluation artifact JSON 与 Java expected anchors，返回 binding preview。该接口只读 / dry-run / preview，不读取磁盘文件或真实路径，不新增 upload/import/persist endpoint，不写数据库，不把 artifact 转成 backtest_eval_reports、strategy evaluation、publish record 或 Paper evidence，不启动策略发布、Paper run 或 Shadow run，不外联，不读取 credential material，不启用 LIVE / AI / DH runtime。
  - Request body：`artifact / expectedDatasetId / expectedStrategyVersionId / expectedStrategyVersion / expectedEvaluationVersion / expectedChecksum / expectedParametersHash / source / dryRun`。`artifact` 必须是 JSON object；`source` 允许 `PYTHON_OFFLINE`（Python 离线来源）；`dryRun=false` 会 fail-closed，`dryRun` 缺失按 preview endpoint 固有 dry-run 处理。
  - Response：`scope / bindingStatus / validationStatus / artifactType / runMode / datasetId / strategyVersion / evaluationVersion / parametersHash / checksumStatus / schemaStatus / metricsStatus / offlineBoundaryStatus / traceabilityStatus / requiredEvidence / missingEvidence / blockers / warnings / nextSteps / generatedAt`。
  - `bindingStatus` / `validationStatus` 当前语义：`VALID_FOR_BINDING_PREVIEW`（仅表示 artifact 可进入只读绑定预览）、`BLOCKED_SCHEMA_INVALID`（schema 无效阻断）、`BLOCKED_UNSUPPORTED_SCHEMA_VERSION`（schemaVersion 不支持阻断）、`BLOCKED_RUN_MODE_NOT_OFFLINE`（runMode 非 OFFLINE 阻断）、`BLOCKED_DATASET_MISMATCH`（dataset 不一致阻断）、`BLOCKED_STRATEGY_VERSION_MISMATCH`（strategyVersion 不一致阻断）、`BLOCKED_CHECKSUM_MISMATCH`（checksum 不一致阻断）、`BLOCKED_PARAMETERS_HASH_MISMATCH`（parametersHash 不一致阻断）、`BLOCKED_METRICS_INCOMPLETE`（metrics 不完整阻断）、`BLOCKED_TRACEABILITY_INCOMPLETE`（traceability 不完整阻断）、`BLOCKED_BOUNDARY_VIOLATION`（offline boundary 或敏感/runtime 字段违规阻断）、`UNKNOWN`（未知）、`NOT_AVAILABLE`（不可用）。
  - Fail-closed 规则：artifact 为空或非 JSON object、`schemaVersion` 缺失或非 `python-evaluation-artifact.v1`、`runMode` 非 `OFFLINE`、`datasetId` / `strategyVersion` / `evaluationVersion` / `checksum` / `parametersHash` 缺失或与 expected anchors 不一致、required metrics 不完整、`startTime` / `endTime` / `barCount` 缺失、offline boundary 缺失或不完整、traceability fields 不完整、出现 `liveExecution` / `realOrder` / `credential` / `privateEndpoint` / `brokerAccount` / path-like 字段或 credential-like 字段，均返回阻断状态，不伪造 ready。
  - `VALID_FOR_BINDING_PREVIEW` 仅代表“可生成只读绑定预览”。它不代表 Java fact 已写入，不代表 artifact 已导入，不代表策略已批准或可发布，不代表 Paper run / Shadow run 可启动，不代表交易授权，不代表 Python ML ready 或 live execution ready。
  - Response 不得包含 `tradingReady`、`liveReady`、`authorizedForTrading`、`apiKey`、`secret`、`token`、`passphrase`、`private key`、`encrypted_payload`、`decrypted_payload` 或 raw provider payload；也不得返回 `LIVE_READY`、`TRADE_APPROVED`、`AUTHORIZED` 或 `ML_READY` 放行语义。

## Adapter Readiness API

GateM-5A 新增的只读 adapter readiness 状态查询入口：

- `GET /api/adapters/readiness`：只读返回当前各 venue × capability 的 readiness 快照，供前端展示 OKX / Binance / Noop 当前不可实盘及原因。需要认证（bearerAuth），归属 `/api/**` 受保护路由。
  - 响应：`{ generatedAt, items[] }`；每个 item 含 `venue / capability / status / allowed / liveAuthorized / reasons[] / message`。
  - 覆盖 venue：`NOOP / PAPER / SIM / OKX / BINANCE`；覆盖 capability：`PUBLIC_MARKETDATA / SUBSCRIBE_BARS / SUBSCRIBE_TRADES / SUBSCRIBE_ORDERBOOK / PLACE_ORDER / CANCEL_ORDER / QUERY_ORDER / ACCOUNT_BALANCE / PERMISSION_PROBE`（5 × 9 = 45 条）。
  - 当前 baseline（no-real / LIVE disabled）行为：NOOP/PAPER/SIM → `status=NO_REAL`；OKX/BINANCE → `status=NOT_READY`；所有条目 `allowed=false`、`liveAuthorized=false`，无 `READY`；PLACE_ORDER/CANCEL_ORDER 带 `LIVE_DISABLED` 原因，PERMISSION_PROBE 带 `REAL_PROVIDER_NOT_IMPLEMENTED` 原因。
  - 边界：只读静态 readiness 决策；不触达 adapter delegate、不发起 HTTP/socket、不读取 env/credential、不触发下单/撤单/行情订阅；响应不含 secret/apiKey/token/signature/passphrase 或 raw payload。

## Runtime Operational Readiness API

GateM-6B 新增的只读运行边界与禁用能力摘要入口：

- `GET /api/runtime/operational-readiness`：只读返回当前 runtime disabled capability / startup boundary summary。需要认证（bearerAuth），归属 `/api/**` 受保护路由。
  - 响应：`{ generatedAt, liveStatus, aiStatus, dhRuntimeStatus, realProviderStatus, credentialExposureStatus, externalExchangeCallStatus, permissionProbeStatus, startupBoundaryStatus, profileBoundaryStatus, configDiagnosticsStatus, logDiagnosticsStatus }`。
  - 每个 status item 含 `status / ready / reasonCode / reason`；当前 baseline 全部 `ready=false`，不得解释为 real-ready 或 LIVE authorization。
  - 当前 baseline：`liveStatus=DISABLED`、`aiStatus=NOT_STARTED`、`dhRuntimeStatus=NOT_INTEGRATED`、`realProviderStatus=NOT_IMPLEMENTED`、`credentialExposureStatus=NOT_EXPOSED`、`externalExchangeCallStatus=DISABLED`、`permissionProbeStatus=SKIPPED`、`startupBoundaryStatus=SAFE_BY_DEFAULT`、`profileBoundaryStatus=SAFE_SUMMARY_ONLY`、`configDiagnosticsStatus=SAFE_SUMMARY_ONLY`、`logDiagnosticsStatus=SAFE_SUMMARY_ONLY`。
  - 边界：safe DTO only；不返回 raw env / full config / credential material / provider payload；不触达 adapter、permission probe、HTTP client、DB、file、external exchange；不改变 actuator、adapter readiness、MarketData readiness、Trading、Paper Trading 或 scheduler 行为。

## Account Credential API

当前已实现的账户凭证写侧与生命周期入口：

- `GET /api/exchange-accounts/{accountId}/credentials/active`：读取当前 active credential 摘要，支持可选查询参数 `credentialType` 显式选择 `OKX_API_V5 / BINANCE_HMAC / BINANCE_ED25519`；未指定 `credentialType` 且同一 account 存在多个 ACTIVE credential type 时返回 `409 STATE_CONFLICT`，不再按更新时间静默选择。响应只包含 `credentialId`、`exchangeAccountId`、`credentialType`、`maskedAccessKey`、`credentialStatus`、`verificationStatus`、`isActive`、`revokedAt`、`rotatedFromCredentialId`、`rotatedAt`、`lastVerifiedAt`、`lastVerificationError`、`updatedAt` 等非敏感字段。
- `POST /api/exchange-accounts/{accountId}/credentials`：新增 credential 版本；旧 active 版本仅写为 `credential_status='ROTATED'` 且 `is_active=false`，不再把轮换旧版本混同为不可恢复 `REVOKED`。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/rotate`：显式轮换指定 ACTIVE credential；`credentialType` 从旧 credential 派生，请求体必须包含新 credential material 和 rotate reason；同事务内旧 credential 标记 `ROTATED`、新 credential 创建为 `ACTIVE`，并追加旧 `ROTATED` / 新 `CREATED` audit log。
- `POST /api/exchange-accounts/{accountId}/credentials/verify`：对当前 active credential 做结构性校验，支持可选查询参数 `credentialType` 显式选择；未指定 `credentialType` 且同一 account 存在多个 ACTIVE credential type 时返回 `409 STATE_CONFLICT`。该接口只处理 `credential_status='ACTIVE'` 且 `is_active=true` 的 active material。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/revoke`：不可恢复撤销 credential，写入 `credential_status='REVOKED'`、`revokedAt` 和 append-only `credential_audit_logs` 事件；重复 revoke 幂等返回当前摘要。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/disable`：临时禁用 credential，写入 `credential_status='DISABLED'` 和 append-only audit 事件。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/enable`：重新启用临时禁用的 credential；只允许 `credential_status='DISABLED' AND is_active=false` 的 credential 经本地结构性校验后恢复为 `ACTIVE`，拒绝 `ACTIVE / REVOKED / ROTATED / EXPIRED`，同事务内检查同 account + credentialType 无其他 ACTIVE，写入 `ENABLED` audit log。请求体只包含必填 `reason`，`credentialType` 从 credentialId 派生；不调用真实交易所，不返回或记录敏感材料。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe`：触发最小 permission probe 编排；请求体只允许 `reason / dryRun / mode / paperSafetyConfirmed` 等非敏感字段，拒绝 `apiKey / secret / signature / headers` 等未知字段；`credentialType` 与 actor 均由服务端派生。Service 先做 owner/account/credential、ACTIVE、Paper safety、LIVE disabled、`withdraw_enabled=false` 和 IN_PROGRESS gate，再调用独立 `ExchangeCredentialPermissionProbePort`。本轮默认 port 为 no-real-exchange fake，只返回脱敏 `SKIPPED`，不访问真实交易所。
- `GET /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe/latest`：读取 latest permission probe summary；只读 `permission_probe_status / permission_scope / ip_allowlist_probe_status / failed_auth_count / last_permission_probe_at / last_permission_probe_error` 等脱敏字段，不触发 adapter，不读取 credential material。
- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/expire`：标记 credential 过期，写入 `credential_status='EXPIRED'` 和 append-only audit 事件。

Credential API 固定边界：

- API response 不返回 `encryptedPayload`、`decryptedPayloadJson`、`apiKey`、`secretKey`、`token`、`privateKeyPem`、`passphrase` 或任何明文 credential material。
- Permission probe response 只返回 `accountId`、`credentialId`、`credentialType`、`exchange`、`permissionProbeStatus`、`permissionScope`、`withdrawEnabled`、`ipAllowlistProbeStatus`、`failedAuthCount`、`lastPermissionProbeAt`、`sanitizedErrorCategory`、`requestId`、`traceId`；不返回 raw response、headers、signature、request body、encrypted/decrypted payload、API key、secret、private key 或 passphrase。
- Permission probe `requestId` 与 `traceId` 是两类不同审计字段：`requestId` 标识本次 probe result，`traceId` 标识调用链路。默认 NoReal port 使用本地脱敏 requestId，不复用 traceId，也不包含 credential material。
- revoke / disable / expire lifecycle command request body 只接收 `reason`；enable command request body 只接收必填 `reason`；rotate command request body 接收新 credential material 和必填 `reason`。应用层限制 reason 长度并拒绝明显包含 token、API key、secret、private key、password、助记词、密钥等敏感材料的原因。
- `DISABLED / REVOKED / EXPIRED / ROTATED` 均不会进入 active material 查询；`REVOKED / ROTATED` 不允许通过本轮接口改写为 `DISABLED / EXPIRED`。
- Batch 5-E-B 后，active summary / active material 无 `credentialType` 路径只在候选唯一时返回；多 ACTIVE credential type 必须显式选择或返回 409。`permission_scope=NULL` 仍表示权限尚未由代码确认，enable 不把 `permission_scope=NULL` 解释为 `TRADE`，本轮不把 `permission_scope` 作为交易权限判断。
- 当前 no-real-exchange permission probe 后端编排已冻结为 guarded baseline；默认 port 仍为 `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。未接真实 OKX/Binance/Bybit/Gate adapter，未新增 AI / DH / Agent credential 调用、LIVE 交易或真实下单路径；future real adapter 必须另起任务并重新安全审查。

## GateH-1 Trading Workspace API

当前已实现的 GateH-1 交易工作台读写入口：

- `GET /api/trading/orders`：按正式 `exchangeAccountId` 账户上下文查询订单列表，支持 `orderId`、`symbol`、`status`、`environment`、分页筛选。
- `GET /api/trading/orders/{orderId}`：查询单笔订单详情。
- `GET /api/trading/orders/{orderId}/trade`：查询订单最近一笔成交事实。
- `GET /api/trading/accounts/{accountId}`：查询账户余额快照；`accountId` 仍由后端兼容映射到 legacy trading account。
- `GET /api/trading/positions/{accountId}/{symbol}`：查询账户和交易对维度持仓快照。
- `POST /api/trading/orders`：触发既有下单编排，仍走服务端风控与状态机。
- `POST /api/trading/orders/cancel`：触发既有撤单编排。
- `POST /api/trading/reconciliation/run-once`：触发既有对账维护动作。
- `POST /api/trading/recovery/run-once`：触发既有恢复维护动作。

GateH-1 不新增历史行情抓取、marketdata ingestion、dataset 绑定、AI 下单或策略自动交易接口。

## GateH-2 Marketdata Ingestion API

当前已实现的 GateH-2 行情接入入口：

- `GET /api/marketdata/bars`：按 `exchangeCode`、`marketType`、`symbol`、`interval`、`startTime`、`endTime`、`page`、`size` 查询 `marketdata_bars`。
- `POST /api/marketdata/ingestion-jobs`：创建 SPOT 历史 K 线接入任务。
- `GET /api/marketdata/ingestion-jobs`：查询最近接入任务列表。
- `GET /api/marketdata/ingestion-jobs/{jobId}`：查询接入任务详情。
- `GET /api/marketdata/ingestion-jobs/{jobId}/runs`：查询任务运行记录。
- `POST /api/marketdata/ingestion-jobs/{jobId}/run-once`：执行一次接入任务，返回 `runId`、`status`、`fetchedBars`、`insertedBars`、`updatedBars`、`skippedBars`、`startedAt`、`finishedAt`、`errorMessage`。

GateH-2 固定范围：

- `exchangeCode`：`OKX`、`BINANCE`。
- `marketType`：仅 `SPOT`。
- `symbol`：`BTC-USDT`、`ETH-USDT`、`SOL-USDT`。
- `interval`：`1m`、`5m`、`15m`、`1h`、`4h`、`1d`。
- 数据类型：OHLCV K 线。

GateH-2 不新增 AI 自动交易、AI 信号接入、dataset/backtest 绑定、合约全量接入、资金费率、深度、逐笔成交、美股/A 股适配或复杂因子平台 API。

## GateM-2E / GateO O-3E Marketdata Readiness API Frozen Baseline

GateM-2E 新增只读 MarketData readiness 后端 MVP；GateO O-3B 在不新增重复 endpoint 的前提下扩展同一个 read model：

GateO O-3E freeze review（2026-07-03）结论：`PASS`（通过）/ `ACCEPTED`（已接受）/ `FROZEN`（已冻结）。冻结对象为 commit `7a42ca03 feat(marketdata): extend readiness API read model` 中既有 `GET /api/marketdata/readiness` read-only response baseline；GateO stage 仍 `NOT COMPLETED`（未完成），O-4 / O-5 / O-FREEZE 仍 `NOT STARTED`（未开始）。

- `GET /api/marketdata/readiness`：按本地 DB 既有 MarketData facts 聚合 source health / freshness / gap / qualityStatus summary。该接口只读，不触发采集，不调用 adapter，不访问外部网络，不读取 credential，不启用 LIVE，不接 AI / DH runtime。
  - Query：`exchangeCode` 必填；`marketType` 可选，默认 `SPOT`；`symbol` 或 `instrumentId` 至少提供一个，二者同时提供时必须一致；`interval` 必填；`from` / `to` 可选，使用 ISO-8601 instant。O-3B 未新增 query 参数。
  - Response 保留既有字段：`exchangeCode / marketType / instrumentId / symbol / interval / status / freshnessStatus / sourceHealthStatus / sourceHealthReason / qualityStatusSummary / barCount / firstBarTime / lastBarTime / expectedBarCount / gapCount / unknownQualityCount / lastSuccessAt / lastFailureAt / backendSupportLevel / generatedAt`。
  - O-3B 追加字段：`exchange / timeframe / sourceCode / dataOrigin / sourceStatus / sourceHealth / gapStatus / missingFrom / missingTo / lastObservedAt / latencyMs / errorRate / errorCategory / staleAfterSeconds / degradedReason / disabledReason / traceId / requestId / updatedAt`。
  - Alias 兼容：`exchange` 是 `exchangeCode` 的展示别名；`timeframe` 是 `interval` 的展示别名。二者不表示真实 provider、LIVE 或 permission probe readiness。
  - `qualityStatusSummary` 包含 `okCount / gapSignalCount / invalidCount / unknownQualityCount / statuses`。
  - Status set：`status` / `freshnessStatus` / `sourceHealthStatus` 为 `FRESH / STALE / VERY_STALE / GAP / ERROR / DISABLED / UNKNOWN / NO_DATA`；`sourceStatus` 为 `ENABLED / DISABLED / DEGRADED / ERROR / RATE_LIMITED`；`sourceHealth` 为 `HEALTHY / DEGRADED / RATE_LIMITED / TIMEOUT / ERROR / UNKNOWN`；`gapStatus` 为 `NONE / PARTIAL / GAP / UNKNOWN`；`errorCategory` 为 `NONE / DISABLED / POLICY_DENIED / RATE_LIMITED / TIMEOUT / TEMPORARY_FAILURE / INVALID_RESPONSE / STALE / GAP / TRANSPORT_ERROR / UNKNOWN`。`NO_DATA`、`UNKNOWN`、`STALE`、`VERY_STALE`、`GAP`、`ERROR`、`DISABLED` 均不得解释成 ready。
  - `dataOrigin` 当前由本地 facts 映射为 `LOCAL_DB`；允许枚举仍只用于诊断表达：`LOCAL_DB / FIXTURE / FAKE_SERVER / PUBLIC_CANDIDATE / UNKNOWN`。O-3B 不把 `PUBLIC_OUTBOUND` 写成已执行事实。
  - `errorRate`、`missingFrom`、`missingTo`、`traceId`、`requestId` 在没有稳定本地事实时返回 `null`，不得伪造窗口错误率、缺口区间或追踪 ID。
  - `backendSupportLevel=NO_MIGRATION_MVP` 表示本轮仅基于 `marketdata_bars` 与 `marketdata_ingestion_jobs/runs` 的本地聚合，不代表真实交易所 source health 已完成。
  - Gap 只基于本地 bars 序列和 `quality_status` 证据估算；不猜测真实交易所状态，不伪造 OKX / Binance health。
  - Response 不得包含 `tradingAuthorized`、`liveReady`、`privateTradingReady`、`permissionGranted`、`realProviderReady`、`apiKey`、`secret`、`passphrase`、`credentialRef`、`rawRequest`、`rawResponse`、`rawHeaders`、`fullQueryString`、`encrypted_payload`、`decrypted_payload`。

## GateP Batch 2 Marketdata Data Quality Center Read-only API

NQ-GATEP-BATCH-2-MARKET-DATA-DATA-QUALITY-CENTER-BACKEND-READONLY-SLICE 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可提交前复核）。该状态只覆盖本轮后端只读切片，不代表 GateP 已实现、已冻结或已接受。

- `GET /api/marketdata/quality/overview`：按本地 `marketdata_bars`、`marketdata_datasets`、`marketdata_dataset_coverage`、`marketdata_ingestion_jobs/runs` 聚合 Data Quality overview。该接口只读，不写库，不触发采集，不调用 adapter，不访问外部网络，不读取 credential，不启用 LIVE，不接 AI / DH runtime。
  - Query：`exchangeCode` 或别名 `exchange` 可选，二者同时提供时必须一致；`marketType` 可选，默认 `SPOT`；`symbol`、`interval`、`sourceType`、`dataOrigin`、`datasetId`、`from`、`to` 均可选；`interval` 使用既有 `1m / 5m / 15m / 1h / 4h / 1d`；`from/to` 使用 ISO-8601 instant。
  - Response：`scope / totalBars / expectedBars / gapCount / duplicateCount / outOfOrderCount / staleCount / latestBarTime / earliestBarTime / lastSuccessAt / lastFailureAt / lastIngestionRunId / sourceHealth / freshnessStatus / qualityStatus / dataOriginSummary / datasetCoverageSummary / topIssues / generatedAt`。
  - `duplicateCount` 仅在现有 dataset coverage 存在 `duplicate_bars` 事实时返回 `AVAILABLE`；否则返回 `NOT_AVAILABLE` 并说明原因。`outOfOrderCount` 当前 schema 未持久化跨 scope out-of-order 诊断，固定返回 `NOT_AVAILABLE`，不得伪造为 0。
  - `dataOriginSummary.requestedDataOrigin` 只回显请求维度；`effectiveDataOrigin` 当前固定为 `LOCAL_DB`，`supportLevel=LOCAL_DB_ONLY_READ_MODEL`。即使请求传入 `PUBLIC_OUTBOUND`，也不表示 `DataOrigin.PUBLIC_OUTBOUND` runtime provider 已实现或 public outbound 已默认启用。
  - `datasetCoverageSummary` 复用最新 dataset coverage 的 `expectedBars / actualBars / missingBars / duplicateBars / invalidBars / latestDatasetId / latestCoverageAt`；接口不会触发 `refresh-quality`，不会新增 coverage。
  - `topIssues` 仅来自本地聚合结果，例如 `NO_DATA / INGESTION_FAILURE / GAP_DETECTED / STALE_DATA / INVALID_BARS`；不包含 provider raw response、headers、credential、URL 或交易建议。
  - Response 不得包含 `tradingReady`、`liveReady`、`authorizedForTrading`、`privateTradingReady`、`permissionGranted`、`realProviderReady`、`apiKey`、`secret`、`passphrase`、`credential`、`rawRequest`、`rawResponse`、`rawHeaders`、`fullQueryString`、`encrypted_payload`、`decrypted_payload`。

## GateP Batch 4 Trading Preflight Readiness Read-only API

NQ-GATEP-BATCH-4-SINGLE-VENUE-ACCOUNT-PERMISSION-AND-RISK-PREFLIGHT-READONLY-BASELINE 当前状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `READY TO COMMIT`（可提交前复核）。该状态只覆盖本轮后端只读基线，不代表 GateP 已实现、已冻结或已接受。

- `GET /api/trading/preflight/readiness`：按当前认证用户聚合单交易所 account metadata、active credential metadata、permission probe latest summary、Data Quality diagnostic 和风险前置阻断原因。该接口只读，不写库，不触发下单 / 撤单 / 转账 / 提现，不调用 adapter，不调用真实 permission probe，不访问外部网络，不读取 credential material，不启用 LIVE，不接 AI / DH runtime。
  - Query：`exchangeCode` 可选，默认 `OKX`；`accountId` 可选，传入时必须为正数；`marketType` 可选，默认 `SPOT`；`symbol`、`strategyId` 可选，仅作为诊断 scope 回显或 Data Quality 查询维度，不触发策略读取或执行。
  - Response：`scope / exchangeCode / accountId / marketType / symbol / liveStatus / realProviderStatus / privateTradingStatus / permissionProbeStatus / credentialConfigured / credentialStatus / credentialTypeSummary / accountConfigured / accountStatus / dataQualityStatus / riskPreflightStatus / blockers / warnings / requiredNextSteps / generatedAt`。
  - 当前 fail-closed 基线：`liveStatus=LIVE_DISABLED`、`realProviderStatus=REAL_PROVIDER_NOT_IMPLEMENTED`、`privateTradingStatus=PRIVATE_TRADING_NOT_IMPLEMENTED`、`riskPreflightStatus=RISK_PREFLIGHT_BLOCKED`。只要真实 permission probe、real provider、private trading 和 LIVE 未完成独立授权，该接口不会返回交易放行语义。
  - `credentialTypeSummary` 仅返回 `credentialId / credentialType / credentialStatus / verificationStatus / active / permissionProbeStatus / permissionScope / ipAllowlistProbeStatus / failedAuthCount / lastVerifiedAt / lastPermissionProbeAt` 等 metadata；不返回 `maskedAccessKey` 或任何 credential material。
  - `blockers` 当前至少覆盖 `LIVE_DISABLED`、`REAL_PROVIDER_NOT_IMPLEMENTED`、`PRIVATE_TRADING_NOT_IMPLEMENTED`、`PERMISSION_PROBE_NOT_IMPLEMENTED`；账号或凭证缺失时追加 `ACCOUNT_UNCONFIGURED` / `CREDENTIAL_UNCONFIGURED`；Data Quality 非 OK 时追加 `DATA_QUALITY_NOT_OK`。
  - `warnings` 固定说明 `DATA_QUALITY_DIAGNOSTIC_ONLY` 和 `RISK_PREFLIGHT_READONLY`：data quality 与 risk preflight 均是诊断，不代表交易授权、真实 provider readiness 或 LIVE readiness。
  - Response 不得包含 `tradingReady`、`liveReady`、`authorizedForTrading`、`privateTradingReady`、`permissionGranted`、`realProviderReady`、`apiKey`、`secret`、`token`、`passphrase`、`privateKey`、`encrypted_payload`、`decrypted_payload`、`rawRequest`、`rawResponse`、`rawHeaders` 或 provider payload。

## GateH-3 Dataset and Backtest Binding API

当前已实现的 GateH-3 数据集与回测绑定入口：

- `GET /api/marketdata/datasets`：查询 marketdata dataset 列表，支持按 `exchangeCode`、`marketType`、`symbol`、`interval` 过滤。
- `POST /api/marketdata/datasets`：创建 dataset，并立即基于 `marketdata_bars` 计算覆盖范围与质量状态。
- `GET /api/marketdata/datasets/{datasetId}`：查询 dataset 详情。
- `POST /api/marketdata/datasets/{datasetId}/refresh-quality`：重新计算 dataset 覆盖率、缺口数、异常 bar 数和质量状态。
- `PATCH /api/backtest-configs/{configId}/dataset`：把 dataset 绑定到 backtest config，并保存 `dataset_snapshot_json`。
- `GET /api/backtest-configs/{configId}`：返回 `datasetId` 和 `datasetSnapshotJson`。
- `GET /api/backtest-runs/{runId}`：返回 run 创建时固化的 `datasetSnapshotJson`。

GateH-3 固定范围：dataset 来源仅为 GateH-2 的 `marketdata_bars`；仅支持 `OKX` / `BINANCE`、`SPOT`、`BTC-USDT` / `ETH-USDT` / `SOL-USDT`、`1m` / `5m` / `15m` / `1h` / `4h` / `1d`。

GateH-3 不新增 AI 自动交易、AI 信号接入、合约全量接入、资金费率、深度、逐笔成交、美股/A 股适配、复杂因子平台或高频交易 API。

## GateI-1 Strategy Version and Publish API

当前已实现的 GateI-1 策略版本与发布链路入口：

- `GET /api/strategies/{strategyCode}`：按 `strategyCode` 查询策略定义详情。
- `PATCH /api/strategies/{strategyCode}/status`：按 `strategyCode` 启用或停用策略定义。
- `GET /api/strategies/{strategyCode}/versions`：查询策略版本列表。
- `POST /api/strategies/{strategyCode}/versions`：创建策略版本，固化 `paramSnapshotJson`、`configSnapshotJson`、`sourceSnapshotJson` 和 `checksum`。
- `GET /api/strategies/{strategyCode}/versions/{versionId}`：查询策略版本详情，并校验版本归属策略编码。
- `GET /api/publishes`：查询发布记录列表，可按 `strategyVersionId` 过滤。
- `GET /api/publishes/{publishId}`：查询发布记录详情。
- `POST /api/publishes?backtestRunId={runId}`：发布回测结果，可选绑定 `strategyVersionId`。
- `POST /api/backtest-runs/{runId}/publish`：兼容既有发布入口，可选传入 `strategyVersionId`。
- `GET /api/backtest-runs/{runId}/publish`：返回发布结果，并包含策略版本绑定与 `versionSnapshotJson`。

GateI-1 固定范围：

- 策略版本状态：`DRAFT`、`ACTIVE`、`ARCHIVED`。
- 发布绑定只接受存在且 `ACTIVE` 的策略版本。
- 发布时固化 `versionSnapshotJson`，后续策略版本变化不会改写历史发布记录。
- 不修改策略核心算法，不启动回测，不进入 Paper Trading。

GateI-1 不新增 AI API，不新增 AI 自动交易接口，不新增美股/A 股、合约全量、高频或复杂因子平台接口。

## GateI-2 Backtest Traceability and Evaluation API

当前已实现的 GateI-2 回测配置、运行追溯与评估报告入口：

- `GET /api/research-configs`：返回默认业务可见的研究配置列表；Batch 4-A 后默认不包含 `status=ARCHIVED`，`status=DISABLED` 仍可见。
- `GET /api/research-configs/{configId}`：返回单条研究配置详情；允许读取 `status=ARCHIVED` 的配置，用于历史追溯。
- `POST /api/research-configs/{configId}/archive`：把研究配置标记为 `ARCHIVED`，写入 `archivedAt / archivedBy / archiveReason / updatedAt`；请求体 `archiveReason` 可空，不得包含密钥、token、API secret、私钥、助记词等敏感信息；重复归档幂等返回当前详情。
- `GET /api/backtest-configs`：返回回测配置列表，包含 `strategyVersionId`、`strategyVersionSnapshotJson`、`paramSnapshotJson`、`configSnapshotJson`、`datasetId`、`datasetSnapshotJson`。
- `POST /api/backtest-configs`：创建回测配置，并初始化参数快照、配置快照；不启动回测。
- `GET /api/backtest-configs/{configId}`：返回单条回测配置详情，包含 strategy version、dataset、参数和配置快照。
- `POST /api/backtest-configs/{configId}/archive`：把回测配置标记为 `ARCHIVED`，写入 `archivedAt / archivedBy / archiveReason / updatedAt`；请求体 `archiveReason` 可空，不得包含密钥、token、API secret、私钥、助记词等敏感信息；重复归档幂等返回当前详情。
- `PATCH /api/backtest-configs/{configId}/strategy-version`：绑定已存在的 strategy version，后端从 `strategy_versions` 读取并固化版本快照和参数快照；请求体只允许传 `strategyVersionId`。
- `PATCH /api/backtest-configs/{configId}/dataset`：复用 GateH-3 dataset 绑定入口，后端固化 dataset snapshot。
- `POST /api/backtest-runs`：根据回测配置创建 run，创建时固化 `strategyVersionId`、`strategyVersionSnapshotJson`、`datasetSnapshotJson`、`paramSnapshotJson`、`configSnapshotJson`。
- `GET /api/backtest-runs/{runId}`：返回 run 详情和完整追溯快照；后续配置重新绑定不会改写历史 run。
- `GET /api/evaluations`：查询已生成评估报告列表，返回 total return、annualized return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe、metrics JSON 等核心指标。
- `GET /api/evaluations/{evaluationId}`：按 `evalReportId` 查询评估报告详情。
- `GET /api/backtest-runs/{runId}/evaluation`：返回该 run 的评估报告。
- `GET /api/backtest-runs/{runId}/sim-orders`：返回 run 模拟订单事实列表（GateF-3 sim facts）。
- `GET /api/backtest-runs/{runId}/sim-trades`：返回 run 模拟成交事实列表。
- `GET /api/backtest-runs/{runId}/sim-positions`：返回 run 模拟持仓事实列表。
- `GET /api/backtest-runs/{runId}/pnl-snapshots`：返回 run 权益/PnL 快照序列（来源表 `sim_pnl_snapshots`，按 `snapshot_time` 升序：`equity / cashBalance / positionMarketValue / realizedPnl / unrealizedPnl / totalFee / totalSlippage / netPnl`）。**回测权益/回撤曲线的时间序列来源即此既有端点。**

> 上述 run-fact 端点(sim-orders / sim-trades / sim-positions / pnl-snapshots)早已在 `BacktestRunController` 实现,此前 `API.md` 漏记,本轮补记为事实。回测权益/回撤曲线后端契约与前端对接计划见 [BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md](./BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md):`pnl-snapshots` 端点与 `sim_pnl_snapshots` 表已存在,**无需新增后端 API / 表 / migration**;前端消费(B1.1,equity 曲线 + 派生 drawdown)为 **planning,尚未实现**。

GateI-2 固定范围：

- 只增强现有 backtest / evaluation 链路。
- 归档命令是配置生命周期命令，不是删除接口；不会删除或隐藏已经产生的 backtest runs、evaluations 或 publish records。
- 不新增 `includeArchived` HTTP 查询参数；默认列表隐藏 `ARCHIVED`，详情按 ID 仍可读取。
- 不修改回测核心算法，不修改策略核心算法，不修改交易核心状态机。
- 不做 SIM/Paper Trading 运行闭环，不进入 GateI-3/4。
- 不接 AI，不新增 AI 分析报告、AI 信号、AI 自动交易或 AI Paper Trading。
- 不新增美股/A 股、合约全量、高频或复杂因子平台 API。

## GateI Planning Entry

GateI API 规划入口为 [GATEI_API_PLAN.md](../gates/gate-i/GATEI_API_PLAN.md)。本轮只做规划，不实现接口。

GateI 规划 API 分类：

- Strategy Version API。
- Publish Version API。
- Backtest Config Enhanced API。
- Evaluation Report API。
- Paper Trading Run API。
- Risk Result API。
- Equity Curve API。
- Position Curve API。
- Trade Replay API。
- Emergency Stop API。

GateI 后续规划不改变当前事实：AI、AI 信号、AI 自动交易和 AI Paper Trading 仍未开始。

## GateI-3 Paper Trading Run API

当前已实现的 GateI-3 SIM/Paper Trading 运行闭环入口：

- `GET /api/paper-trading/runs`：查询 Paper Trading run 列表，可按 `publishId`、`status` 过滤。
- `POST /api/paper-trading/runs`：基于 `publishId` 创建 Paper Trading run，固化 publish/strategy version/dataset/param/config 快照。
- `GET /api/paper-trading/runs/{paperRunId}`：查询 Paper Trading run 详情。
- `GET /api/paper-trading/runs/{paperRunId}/summary`：只读聚合该 Paper run 的运行结果复盘、异常原因诊断、运行事件时间线与关键计数（counts/latest/resultReview/diagnoses/timeline/safety），供前端详情区优先消费；不触发任何状态机或外部调用，environment 固定 SIM/PAPER、LIVE 未开启。
- `GET /api/paper-trading/portfolio/summary`：跨多个 Paper run 只读聚合组合看板（overview/strategyGroups/publishGroups/highlights/dataQuality/safety/portfolioCurve）：组合总资产/总 PnL/累计收益率/最大回撤、按 strategyVersionId 与 publishId 的收益排行（strategyGroups/publishGroups 每组含组内精确风险计数 `noTradeCount`/`dataInsufficientCount`/`comparableRunCount`/`runningCount`/`stoppedCount`/`failedCount`/`cancelledCount`/`createdCount`，基于完整 bounded runs、向后兼容新增；并含订单/成交执行计数 `orderCount`/`tradeCount` 与执行进度三态 run 计数 `noOrderCount`/`orderNoFillCount`/`filledRunCount`，其中 `noOrderCount + orderNoFillCount == noTradeCount`，向后兼容新增）、overview 同步新增 `noOrderRunCount`/`orderNoFillRunCount`/`filledRunCount`（把「无交易」拆为「无订单」「有订单无成交」并显式「有成交」，三者互斥穷尽 totalRuns，向后兼容新增）、Run 排行 run 引用新增 `orderCount` 与执行进度标记 `noOrder`/`orderNoFill`/`hasFill`（向后兼容新增）、Run 排行（收益最高/回撤最大/风险最高/最近活跃/无交易/风控拦截）、数据质量提示与组合级 equity/drawdown 时间序列（`portfolioCurve`：points/latestEquity/peakEquity/currentDrawdown/maxDrawdown/pointCount/coverage，向后兼容新增，数据不足时 points 为空、指标为 null）；仅基于已有 run/equity/日报/风控/告警/成交事实，不触发任何状态机或外部调用，数据不足时不伪造收益率，environment 固定 SIM/PAPER、LIVE 未开启。
- `GET /api/paper-trading/execution-diagnostics`：跨多个 bounded Paper run 只读聚合执行诊断（GateK K1），对每个 run 做规则化归因，回答「为什么无订单/有订单无成交/成交亏损/风控拦截/数据不足/高回撤/异常终态」。响应含 `overview`（`totalRuns`/`noOrderRunCount`/`orderNoFillRunCount`/`filledRunCount`/`filledLossRunCount`/`riskBlockedRunCount`/`dataInsufficientRunCount`/`highDrawdownRunCount`/`failedRunCount`/`runningRunCount`，按事实独立计数、桶可重叠）、`causeDistribution`（按 primaryCause 聚合 `cause`/`count`/`severity`/`confidence`/`description`）、`runDiagnostics`（单 run：`primaryCause`/`secondaryCauses`/`severity`/`causeConfidence`/`explanation`/`suggestedAction` 等）、`strategyDiagnostics`/`publishDiagnostics`（按 strategyVersionId/publishId 聚合 `primaryCause`/`topCauses` 与各 cause 计数）、`safety`。`cause` 取值 `NO_ORDER`/`ORDER_NO_FILL`/`FILLED_LOSS`/`RISK_BLOCKED`/`DATA_INSUFFICIENT`/`HIGH_DRAWDOWN`/`FAILED_RUN`/`RUNNING_NO_RESULT`/`HEALTHY`/`UNKNOWN`，primaryCause 按该顺序优先级取最紧急者；`severity` ∈ `INFO`/`WARNING`/`CRITICAL`，`causeConfidence` ∈ `HIGH`/`MEDIUM`/`LOW`；高回撤阈值首版固定 -10%。复用组合看板同一批量只读事实，不引入 per-run 查询放大，无 run 时返回稳定空结构；不触发任何状态机或外部调用，environment 固定 SIM/PAPER、LIVE 未开启，诊断结论为 Paper-only 规则化归因、不构成真实投资建议。
- `GET /api/paper-trading/strategy-evaluations`：跨多个 bounded Paper run 只读聚合策略评估（GateK K3），从 strategyVersionId / publishId 维度评估 Paper 模拟表现、Paper vs Backtest 偏差、样本充足性与风险调整评分。响应含 `overview`（`strategyCount`/`publishCount`/`evaluatedRunCount`/`comparableRunCount`/`sampleInsufficientStrategyCount`/`profitableStrategyCount`/`lossStrategyCount`/`highRiskStrategyCount`/`backtestDeviationStrategyCount`/`topCompositeScore`/`worstCompositeScore`）、`strategyEvaluations`（每 strategyVersionId 含 run/收益/回撤/胜率聚合 + 评分 `sampleScore`/`riskScore`/`returnScore`/`executionScore`/`backtestDeviationScore`/`compositeScore` + `ratingLabel`/`evaluationConfidence`/`primaryWeakness`/`warnings` + `backtestDeviation`）、`publishEvaluations`（publishId 维度同类字段）、`rankings`（topComposite/worstComposite/topReturn/worstDrawdown/sampleInsufficient/highDeviation/highRisk 的 key 列表）、`safety`。评分为 0~100 Paper 内部启发式分（returnScore 30% / riskScore 25% / executionScore 20% / sampleScore 15% / backtestDeviationScore 10%，缺 backtest 时按剩余权重归一），`ratingLabel` ∈ `STRONG_PAPER_PERFORMER`/`WATCHLIST`/`HIGH_RISK`/`SAMPLE_INSUFFICIENT`/`DATA_INSUFFICIENT`/`EXECUTION_PROBLEM`/`UNKNOWN`，`evaluationConfidence` ∈ `HIGH`/`MEDIUM`/`LOW`，`deviationLevel` ∈ `LOW`/`MEDIUM`/`HIGH`/`UNAVAILABLE`。复用组合看板批量只读事实并 join publish/backtest 评估投影，不引入 per-run 查询放大；Backtest 缺失时偏差为 null 并在 warnings/confidence 降级、不伪造；无 run 时返回稳定空结构；不触发任何状态机或外部调用，environment 固定 SIM/PAPER、LIVE 未开启，评分为 Paper 内部启发式分、非真实投资评级、不构成投资建议。
- `GET /api/paper-trading/auto-reviews`：跨多个 bounded Paper run 只读聚合规则化自动复盘（GateK K4），复用 K1 执行诊断与 K3 策略评估，把组合 / 重点 run / 策略 / 发布事实归纳为结构化复盘摘要并按问题类型确定性聚类。响应含 `overview`（`totalRuns`/`reviewedRunCount`/`issueRunCount`/`healthyRunCount`/`criticalIssueCount`/`warningIssueCount`/`strategyReviewedCount`/`publishReviewedCount`/`topIssueCause`/`topWeakness`/`generatedAt`）、`portfolioReview`（`headline`/`summary`/`keyFindings`/`riskHighlights`/`executionHighlights`/`strategyHighlights`/`backtestDeviationHighlights`/`suggestedNextActions`/`limitations`，headline 按「关键问题 > 显著偏差 > 整体稳定 > 无数据」优先级生成）、`runReviews`（重点 run：`primaryCause`/`severity`/`confidence`/`reviewHeadline`/`reviewSummary`/`keyFacts`/`likelyReasons`/`suggestedActions`/`tags`）、`strategyReviews`/`publishReviews`（`ratingLabel`/`compositeScore`/`evaluationConfidence`/`primaryWeakness`/`reviewSummary`/`strengths`/`weaknesses`/`warnings`/`suggestedActions`）、`issueClusters`（按 `EXECUTION_NO_ORDER`/`EXECUTION_ORDER_NO_FILL`/`EXECUTION_FILLED_LOSS`/`RISK_BLOCKED`/`DATA_INSUFFICIENT`/`HIGH_DRAWDOWN`/`BACKTEST_DEVIATION_HIGH`/`SAMPLE_INSUFFICIENT`/`FAILED_RUN` 聚类，含 `count`/`affectedRunIds`/`affectedStrategyVersionIds`/`affectedPublishIds`/`summary`/`suggestedAction`）与 `safety`（`paperOnly`/`rulesBased`/`noInvestmentAdvice`/`noLiveTrading`/`noAiRuntime` 恒为 true）。复盘由 if/else + enum mapping + 字符串模板规则化生成，suggestedActions 一律为工程排查动作、不含买入/卖出/加仓/减仓/做多/做空/实盘等投资动作；复用诊断/评估的批量只读事实，不引入 per-run 查询放大，无 run / 无策略时返回稳定空结构；不触发任何状态机或外部调用，不接 AI / DH runtime，environment 固定 SIM/PAPER、LIVE 未开启，结论为 Paper-only 规则化摘要、不构成真实投资建议。
- `POST /api/paper-trading/runs/{paperRunId}/start`：启动 Paper run（CREATED → RUNNING）。
- `POST /api/paper-trading/runs/{paperRunId}/stop`：停止 Paper run（RUNNING → STOPPED）。
- `GET /api/paper-trading/runs/{paperRunId}/orders`：查询 Paper run 订单事实列表。
- `GET /api/paper-trading/runs/{paperRunId}/trades`：查询 Paper run 成交事实列表。
- `GET /api/paper-trading/runs/{paperRunId}/positions`：查询 Paper run 持仓事实列表。

GateI-3 固定范围：

- 只做 SIM/Paper，不接 LIVE 自动交易。
- 不接 AI、AI 信号、AI Paper Trading。
- 不改交易核心状态机、策略核心算法、回测核心算法。
- 不新增美股/A 股、合约全量、高频或复杂因子平台 API。
- Paper run 状态流转：CREATED → RUNNING → STOPPED；CREATED/RUNNING → FAILED。
- Paper run 创建时固化 publish snapshot、strategy version snapshot、dataset snapshot、param snapshot、config snapshot。
- 第一版 orders/trades/positions 为空列表，由后续 GateI-4 风控回写和撮合填充。

## GateI-4 Paper Trading Monitor API

当前已实现的 GateI-4 风控回写、资金曲线、持仓曲线、交易复盘与异常停机入口：

- `GET /api/paper-trading/runs/{paperRunId}/risk-results`：查询 Paper run 风控检查结果列表。
- `POST /api/paper-trading/runs/{paperRunId}/risk-results/run-once`：触发一次最小 BASIC_HEALTH_CHECK 风控检查并写入结果。
- `GET /api/paper-trading/runs/{paperRunId}/equity-curve`：查询 Paper run 资金曲线快照列表（按时间倒序）。
- `GET /api/paper-trading/runs/{paperRunId}/position-curve`：查询 Paper run 持仓曲线快照列表（按时间倒序）。
- `GET /api/paper-trading/runs/{paperRunId}/replay`：查询 Paper run 交易复盘事件记录列表（按时间倒序）。
- `POST /api/paper-trading/runs/{paperRunId}/emergency-stop`：触发异常停机；当 run 处于 RUNNING 时调用 stop 状态机并返回 APPLIED，否则返回 FAILED 并记录原因。
- `GET /api/paper-trading/runs/{paperRunId}/emergency-stops`：查询 Paper run 异常停机事件列表。

GateI-4 固定范围：

- 只做 SIM/Paper Trading 监控与异常停机，不接 LIVE 自动交易。
- 不接 AI、AI 信号、AI Paper Trading。
- 不改交易核心状态机、策略核心算法、回测核心算法。
- 风控检查第一版仅写入最小 BASIC_HEALTH_CHECK；具体规则与撮合回写在后续 Gate 实现。
- 异常停机仅复用既有 PaperTradingRunService.stop，不引入额外状态。

## GateJ Planning Entry

GateJ API 规划入口为 [GATEJ_API_PLAN.md](./GATEJ_API_PLAN.md)。本轮只做规划，不实现接口。

GateJ 规划 API 分类：

- Paper Run Schedule API（调度计划 CRUD + run-once）。
- Paper Run Heartbeat API（心跳记录 + run-once）。
- Paper Run Daily Report API（日报生成 + 查询）。
- Paper Run Alert API（告警查询 + 确认）。
- Paper Run Recovery API（恢复 + 重试）。
- GateJ Stability Acceptance API（稳定性验收生成 + 查询）。

GateJ 后续规划不改变当前事实：AI、AI 信号、AI 自动交易和 AI Paper Trading 仍未开始。GateJ 不是 AI 阶段。

## GateJ-1 Paper Run Schedule and Heartbeat API

当前已实现的 GateJ-1 调度计划、触发记录与心跳入口：

- `GET /api/paper-trading/schedules`：查询调度计划列表，可按 `paperRunId`、`status` 过滤。
- `POST /api/paper-trading/schedules`：创建调度计划，默认 ENABLED 状态。
- `GET /api/paper-trading/schedules/{scheduleId}`：查询调度计划详情。
- `PATCH /api/paper-trading/schedules/{scheduleId}/status`：更新调度状态（ENABLED / DISABLED / PAUSED）。
- `POST /api/paper-trading/schedules/{scheduleId}/run-once`：手动触发一次调度，写入 fire 记录。
- `GET /api/paper-trading/schedules/{scheduleId}/fires`：查询调度触发记录列表。
- `GET /api/paper-trading/runs/{paperRunId}/heartbeats`：查询 Paper run 心跳记录列表。
- `POST /api/paper-trading/runs/{paperRunId}/heartbeats/run-once`：手动生成一次心跳记录。

GateJ-1 固定范围：

- 只做调度计划、触发记录和心跳。
- 不做日报、告警、恢复、稳定性验收（GateJ-2/3）。
- 不做后台常驻调度器自动触发（第一版只支持 run-once 手动触发）。
- 不接 AI、AI 信号、AI Paper Trading。
- 不改交易核心状态机、策略核心算法、回测核心算法。

## GateJ-2 Paper Run Daily Report and Alert API

当前已实现的 GateJ-2 日报与告警入口：

- `GET /api/paper-trading/runs/{paperRunId}/daily-reports`：查询 Paper run 日报列表（按 report_date 倒序）。
- `POST /api/paper-trading/runs/{paperRunId}/daily-reports/generate`：生成 Paper run 日报；请求体 `reportDate` 可空，空时使用当前 UTC 日期；按 (paper_run_id, report_date) 幂等。
- `GET /api/paper-trading/runs/{paperRunId}/daily-reports/{reportId}`：查询日报详情。
- `GET /api/paper-trading/runs/{paperRunId}/alerts`：查询 Paper run 告警列表（按 created_at 倒序），可按 `status`、`severity` 过滤。
- `POST /api/paper-trading/runs/{paperRunId}/alerts`：创建一条告警事件；severity 必须为 LOW / MEDIUM / HIGH / CRITICAL；状态固定 OPEN。
- `PATCH /api/paper-trading/runs/{paperRunId}/alerts/{alertId}/ack`：确认告警；OPEN → ACKED；幂等；RESOLVED 状态返回 409。
- `PATCH /api/paper-trading/runs/{paperRunId}/alerts/{alertId}/resolve`：解决告警；任意非 RESOLVED → RESOLVED；幂等。

GateJ-2 固定范围：

- 只做日报与告警。
- 不做恢复、稳定性验收（GateJ-3）。
- 不做外部通知（邮件、Slack、钉钉）。
- 不引入图表库。
- 不接 AI、AI 信号、AI Paper Trading。
- 不改交易核心状态机、策略核心算法、回测核心算法。

## GateJ-3 Paper Run Recovery and Stability API

当前已实现的 GateJ-3 恢复、重试、稳定性验收、监控守护入口：

- `GET /api/paper-trading/runs/{paperRunId}/recovery-events`：查询 Paper run 恢复事件列表（按 created_at 倒序），可按 `recoveryType`、`status` 过滤。
- `POST /api/paper-trading/runs/{paperRunId}/recover`：触发一次手动恢复（MANUAL_RECOVER）；请求体 `reason / requestJson` 可选；不调用真实交易所下单接口。
- `POST /api/paper-trading/runs/{paperRunId}/retry-failed-step`：触发一次失败步骤重试（RETRY_FAILED_STEP）；请求体 `failedStep / reason / requestJson` 可选；不调用真实交易所下单接口。
- `GET /api/paper-trading/runs/{paperRunId}/stability-checks`：查询稳定性验收列表（按 created_at 倒序），可按 `status` 过滤。
- `POST /api/paper-trading/runs/{paperRunId}/stability-checks/generate`：生成 Paper run 稳定性验收；请求体 `checkWindowStart / checkWindowEnd` 必填；按 (paper_run_id, check_window_start, check_window_end) 幂等。
- `GET /api/paper-trading/runs/{paperRunId}/stability-checks/{stabilityCheckId}`：查询稳定性验收详情。
- `POST /api/paper-trading/runs/{paperRunId}/monitor/run-once`：执行一次监控守护；检查 heartbeat lag（默认阈值 300s）并落库 HEARTBEAT_LAG 告警；检查最近 5 分钟内 schedule fire failed 并落库 SCHEDULE_FIRE_FAILED 告警；同一类型在 5 分钟去重窗口内不重复创建；第一版只落库，不外发通知。

GateJ-3 recovery event 状态流转：

- `STARTED → SUCCEEDED / FAILED / SKIPPED`。
- 第一版根据 Paper run 状态映射：RUNNING/CREATED → SUCCEEDED，STOPPED → SKIPPED。
- 每次恢复/重试产生新记录，不幂等（每次产生新的 recovery_event_id）。

GateJ-3 stability check 第一版口径：

- `PASSED`：窗口内 heartbeat_count > 0，且无 CRITICAL 未处理告警，且 failed_fire_count = 0。
- `PARTIAL`：窗口内有心跳但存在普通告警或恢复事件。
- `FAILED`：窗口内无心跳，或存在 CRITICAL 未处理告警，或 failed_fire_count > 0。
- 第一版 `uptime_ratio` 按粗略判定（PASSED=1.0、PARTIAL=0.9、FAILED 有心跳=0.5/无心跳=0）。
- 第一版口径不等于 GateJ-FREEZE 的 1h/24h/7d 最终验收。

GateJ-3 自动告警口径：

- `HEARTBEAT_LAG`：监控守护检测到最近 heartbeat 不存在或 lag_seconds ≥ 300，且 Paper run 状态为 RUNNING；severity = HIGH；source = MONITOR。
- `SCHEDULE_FIRE_FAILED`：监控守护检测到最近 5 分钟内存在 paper_run_schedule_fires.status = FAILED 记录；severity = MEDIUM；source = SCHEDULE。
- 第一版去重：每种 alert_type 在 5 分钟内最多创建 1 条；不做更复杂的策略去重。
- 第一版只落库 paper_run_alerts，不外发通知（邮件 / Slack / 钉钉 / 短信 / Webhook 均不接入）。

GateJ-3 固定范围：

- 只做恢复、重试、稳定性验收、HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED 自动告警最小落库。
- 不做 1h/24h/7d 正式验收归档（GateJ-FREEZE）。
- 不做外部通知（邮件、Slack、钉钉、企业微信、Telegram、Webhook、短信）。
- 不做自动恢复策略引擎。
- 不接 AI、AI 信号、AI Paper Trading。
- 不改交易核心状态机、策略核心算法、回测核心算法。
- 不调用真实交易所下单接口。
