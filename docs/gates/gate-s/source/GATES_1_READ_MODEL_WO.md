# GateS-1 Read Model Work Order: Shadow Run Operational Overview

任务名称：`NQ-GATES-1-READ-MODEL-WO`。

最终状态：`PLAN READY`（规划已就绪）/ `NOT IMPLEMENTED`（未实现）/ `READY TO COMMIT`（可进入提交前复核）。本文只做 GateS-1 Shadow Run operational read model 的 work order、contract、owner、data source、DTO、frontend IA、test scope 和安全边界审查；不实现代码，不新增 API、migration、CI workflow、业务代码、前端页面、Playwright / E2E 测试或 Python 研究代码。

## 1. GateS-1 Objective

GateS-1 的目标是把 GateR 已完成的 Shadow Run local fact、read-only API、frontend list/detail/replay 和 latest consistency report，从“能查看单个 run”推进到“能运营地查看 Shadow Run 系统整体健康”。本轮只定义后续 implementation 的边界，不启动实现。

GateS-1 推荐核心对象是 `ShadowRunOperationalOverviewReadModel`（候选名），用于只读聚合 Shadow Run overview、latest shadow status、stale evidence、divergence severity、Paper vs Shadow consistency summary、Strategy Validation decision、blocker / warning / nextSteps、evidence anchors 和 traceability。

该 read model 只表达 diagnostic only（仅诊断）、no-side-effect（无副作用）和 not trading authorization（不是交易授权）。即使未来 `Strategy Validation decision` 返回 `APPROVED`（验证报告层通过），也只表示 validation report 层面的准出结论，不表示 LIVE、真实 provider、private trading 或实盘交易授权。

## 2. Current Baseline

- 当前分支：`dev`。
- GateS-0 前置条件：`HEAD = origin/dev = 801d705b88c9c8938d927395fff38c9790a70498`，最新提交为 `docs(gates): reconcile current facts and add GateS-0 plan review baseline`，`docs/current/GATES_0_PLAN.md` 已被 Git 跟踪。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag 为 `nq-gater-freeze`，历史归档入口为 `docs/gates/gate-r/README.md`。
- GateS：下一阶段唯一推荐主线，目标为策略验证运营化与 Shadow 诊断闭环阶段。
- GateS-0：`PLAN / NOT IMPLEMENTED`（规划 / 未实现）。
- GateS-1 work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`；GateS-1 implementation 仍 `NOT IMPLEMENTED`。
- GateR-6 / GateR-8 已有 Shadow Run read-only API：`GET /api/shadow-runs`、`GET /api/shadow-runs/{id}`、`GET /api/shadow-runs/{id}/events`、`GET /api/shadow-runs/{id}/snapshots`、`GET /api/shadow-runs/{id}/consistency-report/latest`。
- GateQ / GateP / GateJ 已有只读辅助事实面：Strategy Evaluation Gate、Paper Shadow Comparison、Data Quality Center、Trading Preflight readiness、Paper alerts / recovery / replay。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。

## 3. Read Model Owner

后续 GateS-1 implementation 推荐 owner 分层如下；本轮不创建这些代码文件。

| Layer | Future owner | 职责 | 本轮边界 |
| --- | --- | --- | --- |
| Backend API | `backend/nq-api` | 后续新增只读 Controller / DTO，建议只暴露 GET-only overview | 本轮不新增 Controller、DTO 或 endpoint |
| Backend core | `backend/nq-core` | 后续定义 read model contract、query service、query port 和 enum 语义 | 本轮不新增 domain / service / port |
| Backend infra | `backend/nq-infra` | 后续用 JDBC query adapter 聚合本地 Shadow Run facts | 本轮不写 SQL、不新增 repository |
| Frontend API client | `frontend/src/api/*` | 后续封装 GET-only read model client | 本轮不改 API client |
| Frontend route/page | `frontend/src/pages/*` | 后续接入 Dashboard v2 / Strategy Validation Center / Workbench IA | 本轮不改页面、route 或 E2E |
| Docs | `docs/current/GATES_1_READ_MODEL_WO.md` | 本轮 work order authority | 后续实现阶段再更新 `API.md`、`TESTING.md`、`WORKLOG.md` |

## 4. Source Tables / Existing APIs

### Shadow Run

已落地本地事实表：

- `shadow_runs`：Shadow Run 主事实、状态、traceability、no-side-effect flags、blockers、warnings、next_steps。
- `shadow_run_events`：生命周期和审计事件。
- `shadow_run_snapshots`：`INPUT_MARKETDATA`、`STRATEGY_DECISION`、`RISK_PREFLIGHT`、`ORDER_INTENT_PREVIEW` 本地快照。
- `shadow_consistency_reports`：Paper vs Shadow consistency report，含 `comparison_status`、`metric_delta`、`divergence_reasons`、`limitations`。

已实现只读 API：

- `GET /api/shadow-runs`
- `GET /api/shadow-runs/{id}`
- `GET /api/shadow-runs/{id}/events`
- `GET /api/shadow-runs/{id}/snapshots`
- `GET /api/shadow-runs/{id}/consistency-report/latest`

### Paper Trading

当前 schema / API 可作为后续扩展 source，但不建议进入 GateS-1 首个实现切片：

- `paper_trading_runs`
- `paper_trading_orders`
- `paper_trading_trades`
- `paper_trading_positions`
- `paper_risk_check_results`
- `equity_curve_snapshots`
- `position_curve_snapshots`
- `trade_replay_records`
- `emergency_stop_events`
- `paper_run_schedules`
- `paper_run_schedule_fires`
- `paper_run_heartbeats`
- `paper_run_daily_reports`
- `paper_run_alerts`
- `paper_run_recovery_events`
- `paper_run_stability_checks`

### Strategy / Evaluation

当前可作为 Strategy Validation source 的事实：

- `strategy_versions`
- `backtest_publish_records`
- `backtest_eval_reports`
- `marketdata_datasets`
- `marketdata_dataset_coverage`
- `GET /api/strategies/evaluation-gate`
- `GET /api/strategies/paper-shadow/comparison`
- `GET /api/strategies/shadow-live/preview`
- Python Evaluation Artifact Binding Preview API 只做 request body 预览校验，不导入、不上传、不写库。

### MarketData Quality

- `marketdata_bars`
- `marketdata_ingestion_jobs`
- `marketdata_ingestion_runs`
- `marketdata_datasets`
- `marketdata_dataset_coverage`
- `GET /api/marketdata/quality/overview`

Data Quality diagnostic 只表示数据质量诊断，不表示 trading authorization。

### Risk / Preflight

- `GET /api/trading/preflight/readiness`
- 已有 fail-closed 字段：`liveStatus=LIVE_DISABLED`、`realProviderStatus=REAL_PROVIDER_NOT_IMPLEMENTED`、`privateTradingStatus=PRIVATE_TRADING_NOT_IMPLEMENTED`、`riskPreflightStatus=RISK_PREFLIGHT_BLOCKED`。
- `blockers` 至少覆盖 `LIVE_DISABLED`、`REAL_PROVIDER_NOT_IMPLEMENTED`、`PRIVATE_TRADING_NOT_IMPLEMENTED`、`PERMISSION_PROBE_NOT_IMPLEMENTED` 等诊断阻断。

### Incident / Replay

- Shadow side：`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports`。
- Paper side：`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records`、`paper_run_stability_checks`。
- GateS-1 首个实现切片不建议深聚合 Paper incident / replay；后续 GateS-6 再纳入 Monitoring / Incident / Replay read-only baseline。

## 5. Data Source Mapping

| Read model area | Primary source | Secondary source | GateS-1 首切片建议 |
| --- | --- | --- | --- |
| Shadow Run overview | `shadow_runs` | `shadow_consistency_reports` latest status | 纳入 |
| latest shadow status | `shadow_runs` latest row | `shadow_run_events` latest event | 纳入 |
| stale evidence | `shadow_runs.updated_at`、latest event / snapshot / report time | future thresholds from config or request constants | 纳入，但阈值先写死为 DTO 解释字段，不新增表 |
| divergence severity | `shadow_consistency_reports.comparison_status`、`divergence_reasons`、`metric_delta` | future severity mapping rule | 纳入，只做派生，不回写 |
| Paper vs Shadow summary | `shadow_consistency_reports` | `paper_trading_runs` 仅作为 id anchor | 首切片只使用 report 中的 `paper_run_id`，不深查 Paper tables |
| Strategy Validation decision | Shadow overview + latest report | Strategy Evaluation Gate / Paper Shadow Comparison | 首切片可返回 conservative placeholder；深度聚合留到 GateS-3 |
| blocker / warning / nextSteps | `shadow_runs.blockers`、`warnings`、`next_steps` | Risk / Preflight API 后续可接 | 首切片纳入 Shadow Run 本地 facts |
| evidence anchors | Shadow run/report/event/snapshot ids | Paper / strategy / dataset ids | 纳入 anchor，不展开深层详情 |
| incident / replay | `shadow_run_events`、`shadow_run_snapshots` | `paper_run_alerts`、`trade_replay_records` | 首切片只纳入 Shadow side，Paper side 延后 |
| boundary flags | `shadow_runs` no-* flags | current runtime constants | 纳入，必须 fail-closed |

默认结论：GateS-1 implementation 首切片只聚合 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports`。Paper / Strategy / MarketData / Risk / Incident source 只在 work order 中作为后续 contract mapping，不在首个 API 中一次性深聚合。

## 6. Candidate DTO Contract

候选 DTO 名称可暂定为 `ShadowRunOperationalOverviewResponse`。字段为 future proposal（未来提案），不是当前已实现 DTO。

```text
shadowRunOverview:
  totalRuns
  runningRuns
  blockedRuns
  failedRuns
  completedRuns
  staleRuns
  latestRunAt
  latestConsistencyStatus

latestShadowStatus:
  shadowRunId
  strategyVersionId
  datasetId
  paperRunId
  status
  authorizationBoundary
  noOrderSubmission
  noCredentialAccess
  noPrivateEndpoint
  noLedgerMutation
  noAccountMutation
  noExternalPrivateIo

staleEvidence:
  isStale
  staleReason
  latestSnapshotAt
  latestEventAt
  latestConsistencyReportAt
  freshnessThreshold

divergenceSeverity:
  severity
  comparisonStatus
  divergenceReasons
  metricDelta
  limitations

paperShadowConsistencySummary:
  paperRunId
  shadowRunId
  comparisonStatus
  comparable
  notComparableReason
  evidenceCompleteness

strategyValidationDecision:
  decision
  decisionReasons
  blockers
  warnings
  nextSteps

evidenceAnchors:
  sourceType
  sourceId
  sourceVersion
  sourceTimestamp
  checksum
  digest

traceability:
  requestId
  idempotencyKey
  traceId
  generatedAt

boundaryFlags:
  diagnosticOnly
  noSideEffect
  notTradingAuthorization
  liveDisabled
  realProviderImplemented
  privateTradingImplemented
  aiDhRuntimeIntegrated
```

固定边界值建议：

- `diagnosticOnly=true`
- `noSideEffect=true`
- `notTradingAuthorization=true`
- `liveDisabled=true`
- `realProviderImplemented=false`
- `privateTradingImplemented=false`
- `aiDhRuntimeIntegrated=false`

DTO / docs 示例禁止出现真实或疑似真实值：

- `apiKey`
- `secret`
- `passphrase`
- `token`
- `privateKey`
- `rawSignature`
- `rawPrivateRequest`
- `rawPrivateResponse`
- `credentialMaterial`
- `decryptedPayload`
- `encryptedPayload` 真实值
- private endpoint payload
- `realOrderId`
- `realAccountBalance`
- `realPosition`
- `withdrawAddress`
- `transferTarget`

允许出现的安全字段：

- `noCredentialAccess`
- `noPrivateEndpoint`
- `noOrderSubmission`
- `diagnosticOnly`
- `reviewOnly`
- `replayOnly`
- `sideEffectPolicy`
- `authorizationBoundary`
- `notTradingAuthorization`

## 7. Candidate API Contract

候选 API 必须是 GET-only（仅 GET 只读）规划，不代表当前已实现。

| Candidate | 评价 | GateS-1 建议 |
| --- | --- | --- |
| `GET /api/shadow-runs/overview` | 与现有 `/api/shadow-runs*` owner 一致，scope 收敛，适合只读 overview | 推荐首切片 |
| `GET /api/shadow-runs/operational-summary` | 语义清晰，但与 overview 接近，可能形成重复入口 | 可作为备选，不推荐并行 |
| `GET /api/gates/s/overview` | 覆盖 GateS 全局，未来可包含 Strategy / Paper / MarketData / Risk | 不作为首切片，避免过宽 |
| `GET /api/validation/overview` | 更贴近 Strategy Validation Runtime Baseline | 延后到 GateS-3 |
| `GET /api/validation/strategies/{strategyVersionId}` | 适合策略维度 drilldown | 延后到 GateS-3 / GateS-5 |
| `GET /api/paper-shadow/consistency/drilldown` | 适合增强 Paper vs Shadow | 延后到 GateS-2 |
| `GET /api/risk/preflight/blockers` | 适合集中显示阻断原因 | 延后，先复用既有 `GET /api/trading/preflight/readiness` |
| `GET /api/incidents/replay` | 适合 incident / replay center | 延后到 GateS-6 |

必须评估的结论：

1. 不建议复用 `GET /api/shadow-runs` 承载 overview；现有 list API 已有 bounded list / pagination 语义，overview 应独立为 `GET /api/shadow-runs/overview`。
2. `GET /api/gates/s/overview` 可以作为 GateS 后续总览，但首切片不应覆盖 Paper / Strategy / MarketData / Risk / Incident 全域聚合。
3. Strategy Validation 与 Shadow Run operational read model 初期应分开：Shadow Run overview 先稳定本地事实聚合，Strategy Validation 后续在 GateS-3 消费该 read model。
4. 后续实现应先做一个最小 API，避免一次性扩太多。
5. 默认不需要 DB migration；现有 `shadow_*` 表已能支持首切片。如果后续要持久化 stale thresholds、incident correlation 或 validation decision history，再作为 P2/P3 future risk 单独规划。

固定禁止：不得规划或实现 `POST /api/shadow-runs/overview`、`start`、`stop`、`cancel`、`rerun`、`execute`、`trade`、`placeOrder`、`cancelOrder`、`withdraw`、`transfer` 或任何写侧 / 交易动作 endpoint。

## 8. Frontend IA Contract

本轮只规划 IA，不改前端代码。GateS-1 后续前端必须是专业金融后台只读诊断界面，不做 Binance Pro 式全屏交易终端，不做真实交易按钮，不做 AI 决策中心，不做 LIVE 操作入口。

### Dashboard v2

- system health：显示 Shadow Run overview、stale evidence、latest consistency status。
- Data Quality：只显示诊断状态，不显示 trading-ready。
- Shadow / Paper status：显示 Shadow latest status 与 Paper anchor，不触发 Paper run。
- risk blockers：显示 blockers / warnings / nextSteps。
- 必须显示 `LIVE DISABLED`、Real provider `NOT IMPLEMENTED`、Private trading `NOT IMPLEMENTED`、AI/DH runtime not integrated。

### Strategy Validation Center

- strategy version
- dataset
- evaluation report
- paper run
- shadow run
- consistency report
- validation decision 必须标注不等于 trading authorization。

### Paper vs Shadow Workbench

- paper decision
- shadow decision
- metric delta
- divergence reasons
- 只读展示差异，不提供修正、执行、交易、批准或放行动作。

### Risk / Preflight Blocker Panel

- 为什么不能交易。
- 哪个规则阻断。
- 下一步是什么。
- 必须保留 `LIVE DISABLED`、real provider / private trading / permission probe not implemented 的 fail-closed 显示。

### Incident / Replay Center

- event
- alert
- snapshot
- consistency report
- replay chain
- 只读 replay，不触发 recovery、retry、runner、scheduler、订单或外部通知。

### Frontend boundary

- 不做 Binance Pro 式全屏交易终端。
- 不做真实交易按钮。
- 不做 AI 决策中心。
- 不做 LIVE 操作入口。
- 不显示 live-ready / trading-ready / provider-ready。
- 必须显示 LIVE DISABLED。
- 必须显示 Real provider NOT IMPLEMENTED。
- 必须显示 Private trading NOT IMPLEMENTED。
- 必须显示 Shadow Run is diagnostic only。
- 必须显示 Data Quality diagnostic is not trading authorization。
- 必须显示 AI/DH runtime not integrated。

## 9. Status / Enum Semantics

### Shadow Run core status

既有 `shadow_runs.status` 核心状态机为：

- `CREATED`
- `PRECHECKING`
- `READY`
- `RUNNING`
- `STOP_REQUESTED`
- `STOPPED`
- `COMPLETED`
- `BLOCKED`
- `FAILED`
- `CANCELLED`

GateS-1 不修改该核心状态机，不把 stale evidence、divergence severity、incident status 或 Strategy Validation decision 回写为 `shadow_runs.status`。

### Consistency / health status

一致性与运行健康状态仅用于 Paper / Shadow / replay 证据层：

- `CONSISTENT`（一致）
- `DIVERGED`（偏离）
- `PARTIAL`（部分可比）
- `NOT_COMPARABLE`（不可比）
- `FAILED`（失败）
- `STALE_EVIDENCE`（证据过期）

这些状态不表达 approval、LIVE readiness 或 trading authorization。

### Divergence severity

候选 `severity`（严重度）只用于排序和风险呈现：

- `NONE`（无偏离）
- `LOW`（低）
- `MEDIUM`（中）
- `HIGH`（高）
- `CRITICAL`（严重）

### Strategy Validation decision

候选 `decision`（验证结论）只表示 validation report 层面准出：

- `APPROVED`（验证报告层通过）
- `REJECTED`（拒绝）
- `NEEDS_REVIEW`（需要复核）
- `BLOCKED`（阻断）

`APPROVED` 不表示交易授权，不启用 LIVE，不允许真实下单、撤单、转账或提现。

## 10. Evidence Anchor Model

候选 `evidenceAnchors` 用于把 read model 结论追溯到源事实，不复制敏感 payload，不导入外部 facts。

| Anchor sourceType | sourceId | sourceVersion | sourceTimestamp | checksum / digest |
| --- | --- | --- | --- | --- |
| `SHADOW_RUN` | `shadow_runs.id` | `shadow_runs.version` | `updated_at` / `created_at` | 可空 |
| `SHADOW_EVENT` | `shadow_run_events.id` | event type | `created_at` | 可空 |
| `SHADOW_SNAPSHOT` | `shadow_run_snapshots.id` | `schema_version` | `captured_at` | `checksum` |
| `SHADOW_CONSISTENCY_REPORT` | `shadow_consistency_reports.id` | comparison status | `generated_at` | 可空 |
| `PAPER_RUN` | `paper_run_id` | 可空 | 可空 | 可空 |
| `STRATEGY_VERSION` | `strategy_version_id` | strategy version | 可空 | 可空 |
| `DATASET` | `dataset_id` | dataset quality version / schema | latest coverage time | checksum 可空 |
| `PYTHON_OFFLINE_ARTIFACT` | artifact id / manifest id | artifact schema version | created_at | digest |

Evidence anchor 只是证据锚点，不代表被锚定对象已经授权交易。

## 11. Blocker / Warning / NextSteps Model

候选模型应复用结构化列表，不使用自由文本替代机器可读 code。

```text
blockers:
  code
  severity
  message
  sourceType
  sourceId
  nextStepRef

warnings:
  code
  severity
  message
  sourceType
  sourceId

nextSteps:
  code
  owner
  action
  expectedEvidence
  blocking
```

语义：

- `blocker`：硬阻断原因，必须处理后才能进入下一验证步骤。
- `warning`：非阻断风险或证据不完整提示。
- `nextSteps`：下一步工程验证、数据修复、证据补齐或审查动作。

固定推荐 blocker code：

- `LIVE_DISABLED`
- `REAL_PROVIDER_NOT_IMPLEMENTED`
- `PRIVATE_TRADING_NOT_IMPLEMENTED`
- `PERMISSION_PROBE_NOT_IMPLEMENTED`
- `STALE_EVIDENCE`
- `DIVERGENCE_HIGH`
- `CONSISTENCY_REPORT_MISSING`
- `SHADOW_RUN_MISSING`
- `DATA_QUALITY_NOT_OK`
- `PYTHON_ARTIFACT_OFFLINE_ONLY`

这些 code 只用于诊断，不用于交易放行。

## 12. No-side-effect Boundary

GateS-1 read model 必须是 no-side-effect：

- 只读本地 DB facts。
- 不创建 Shadow Run。
- 不追加 Shadow Run event / snapshot / report。
- 不启动 runner。
- 不接 scheduler。
- 不启动 Paper run。
- 不触发 strategy evaluation 写侧。
- 不调用真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken API。
- 不调用 adapter。
- 不读取 credential material。
- 不提交订单、撤单、转账或提现。
- 不写真实 account / ledger / order / position 状态。

后续实现阶段应以 backend tests 锁住 `@Transactional(readOnly = true)`、GET-only controller、repository read-only query、DTO forbidden fields 和 no credential / no private payload。

## 13. Security / Credential Boundary

本轮和后续 GateS-1 implementation 均禁止：

- 读取、复制、打印、输出 `.env`、`.env.local`、`*.key`、`*.pem`、secrets、dumps、logs、backup。
- 输出 credential material、API key、exchange secret、token、cookie、passphrase、signature、private key、raw provider response。
- 在 read model / DTO / docs 示例中提供真实或疑似真实 private payload。
- 把 `encryptedPayload` 真实值、`decryptedPayload`、raw private request / response 或 private endpoint payload 放入响应。
- 暴露真实订单 ID、真实账户余额、真实仓位、withdraw address 或 transfer target。

后续实现阶段必须验证 DTO 不含 `tradingReady`、`liveReady`、`authorizedForTrading`、`tradeApproved`、credential / private payload / real order fields。

## 14. LIVE / AI / DH / Integration Boundary

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python offline evaluation artifact 只表示离线研究产物，不表示 ML ready 或 live execution ready。
- Java binding contract 只表示 Java 侧如何引用离线 artifact 元数据，不导入真实交易状态。

GateS-1 不得把 Integration-1 mock/test-support 写成 runtime started，不得把 AI/DH runtime 写成 integrated，不得把 public marketdata / Data Quality / preflight readiness 写成 trading authorization。

## 15. Testing Scope

### 后续 backend implementation 阶段

- Controller read-only test。
- Service / query model test。
- Repository query test，如新增 JDBC query adapter。
- no-side-effect boundary test。
- DTO forbidden field test。
- no credential / no private payload test。
- stale evidence / divergence severity mapping test。
- 空数据 / 缺 report / 缺 snapshot / 缺 event 稳定响应 test。

### 后续 frontend implementation 阶段

- `npm run build`。
- 最小 smoke，覆盖页面可加载、主要状态卡片、boundary badges 和 error / empty 状态。
- 不为每个按钮状态补 E2E，因为页面不应提供真实交易按钮。
- 只覆盖主链路和关键边界展示。

### CI / runtime

- 默认 no-outbound。
- 不调用真实交易所。
- 不读取 credential。
- 不启动 runner / scheduler。
- 不新增 real provider、RealClient、private trading adapter 或 permission probe。

### 本轮

本轮只运行 docs / read-only 检查，不运行 Maven、npm build、Playwright 或 Python pytest / mypy / ruff，因为未修改 backend、frontend、research、migration、CI workflow、测试或 runtime 配置。

## 16. Implementation Slice Recommendation

推荐下一轮 GateS-1 implementation 只做一个最小后端 read model：

- 新增 future endpoint：`GET /api/shadow-runs/overview`。
- 只聚合 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports`。
- 不深聚合 Paper / Strategy / MarketData / Risk / Incident。
- 不新增 migration。
- 不新增 runner。
- 不新增 scheduler。
- 不新增前端页面。
- 不新增 Playwright / E2E。
- 不接真实交易所、不读取 credential、不访问 private endpoint。
- 先用后端 tests 锁住 read-only、no-side-effect、DTO forbidden fields 和 stale / divergence 派生规则。

不建议首切片同时做 `GET /api/gates/s/overview`、Strategy Validation Center、Paper vs Shadow Workbench、Risk blocker drilldown 和 Incident / Replay Center；这些应拆到 GateS-2、GateS-3、GateS-5、GateS-6。

## 17. P0 / P1 / P2 / P3 Findings

### P0

- 未发现必须阻断 GateS-1 work order 的 P0。最大 P0 风险是后续实现把 Strategy Validation `APPROVED`、Paper vs Shadow `CONSISTENT` 或 Data Quality OK 写成交易授权；本文已明确禁止。

### P1

- GateS-1 首切片如果一次性聚合 Paper / Strategy / MarketData / Risk / Incident，会扩大 blast radius，增加 API / query / frontend / test 范围漂移风险。建议首切片只做 Shadow Run overview。
- 如果候选 API 包含写接口、runner trigger、scheduler trigger 或 execute / trade action，应视为越界并停止。

### P2

- stale evidence 阈值当前没有独立配置表；首切片可以用响应字段解释默认阈值或服务常量，后续如需用户可配置阈值再单独规划，不应本轮新增 migration。
- divergence severity 映射需要稳定规则；首切片可从 `comparison_status` 派生，复杂 metric delta 阈值留后续增强。
- Paper incident / replay 和 Shadow event / snapshot 的跨源 correlation 尚未统一；建议 GateS-6 再做 incident / replay center。

### P3

- Frontend IA 已有 Strategy Validation、Shadow Run list/detail、Marketdata Data Quality 和 Paper diagnostics 的分散页面；GateS-1 后续需要避免重复导航和概念冲突。
- Python offline artifact 已有 manifest / experiment / summary，但 Java binding 仍应保持只读 contract，不应写成 Python ML ready。

## 18. Acceptance Criteria

- GateS-1 work order 文档完成。
- read model owner 清楚。
- 数据来源清楚。
- DTO 字段清楚。
- API 候选清楚。
- frontend IA 清楚。
- 测试范围清楚。
- 禁止边界清楚。
- 默认实现切片收敛到最小后端 read model。
- 明确本轮不实现 GateS-1。
- 不新增 API。
- 不新增 migration。
- 不修改 backend / frontend / research / scripts / deploy / `.github`。
- 不调用真实交易所。
- 不读取 credential。
- 不出现 trading authorization、live-ready、AI started、DH integrated 误写。
- 产出可直接衔接 GateS-1 implementation 的任务边界。

## 19. Next Concrete Action

完成本 work order 提交后，后续可独立发起 `NQ-GATES-1-READ-MODEL-IMPLEMENTATION`，只实现 `GET /api/shadow-runs/overview` 的最小后端 read model，并限定在 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports` 的 read-only aggregation 内。

本轮 review decision：`NQ-GATES-1-READ-MODEL-WO：PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）。
