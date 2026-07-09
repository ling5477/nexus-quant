# GateT-2 Consistency Evidence Refinement Work Order

任务：`NQ-GATET-2-CONSISTENCY-EVIDENCE-REFINEMENT-WO`

日期：2026-07-08

状态：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）

本文是 GateT-2 的 work order。它只定义 Paper vs Shadow consistency evidence refinement 的事实源、候选 API / DTO、query / repository 方案、语义边界、测试计划和后续实现范围；不实现 endpoint，不新增 API，不新增 migration，不修改后端 / 前端 / Python / CI / 测试代码，不启动 runner / scheduler，不调用真实交易所。

## 1. Current Baseline

本 work order 建立在以下当前事实之上：

- 当前分支：`dev`。
- 当前 HEAD / `origin/dev`：`ab65500e6e0e2baebc02d8941965996915fdce7d`。
- 当前 HEAD commit：`ab65500e feat(gatet): add shadow validation workflow frontend`。
- 当前 HEAD 对应 GitHub Actions：`NQ CI Baseline` run `28949331307`，`completed / success`（已完成 / 成功），`headSha=ab65500e6e0e2baebc02d8941965996915fdce7d`。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），tag 为 `nq-gater-freeze`。
- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），tag 为 `nq-gates-freeze`。
- GateT-0：Shadow Validation Operations plan 已完成，入口为 [GATET_PLAN.md](GATET_PLAN.md)。
- GateT-1 backend + frontend：Shadow Validation Workflow overview 已完成，当前只读消费 `GET /api/shadow-validation/workflow/overview`。
- GateT 当前不是 `FROZEN`（已冻结）、`ACCEPTED`（已接受）或 `TAGGED`（已打 tag）。

固定边界：

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

## 2. GateT-2 Objective

GateT-2 的目标是把 GateS-2 的 Paper vs Shadow drilldown 和 GateT-1 operator workflow 中的一致性证据进一步标准化：

- 定义 consistency evidence item，统一 id、source、freshness、severity、comparisonStatus、metricDelta、limitations、evidenceAnchors 和 traceId。
- 给出一个 read-only overview endpoint 候选，供后续实现聚合 consistency evidence 计数、桶、最新证据和风险提示。
- 明确 freshness、severity、comparisonStatus 和 metricDelta 的诊断语义。
- 明确 DIVERGED / HIGH / CRITICAL / VALIDATION_READY / APPROVED 不能表达交易授权或自动处置。
- 明确 GateT-2 evidence item 与 GateT-1 operator item 的只读证据关系。
- 明确 GET-only / SELECT-only / no-side-effect 测试计划。
- 明确后续 implementation 的最小文件范围和禁止范围。

## 3. Non-goals

GateT-2 work order 不做：

- 不实现 endpoint，不新增 API，不更新 `docs/current/API.md` 作为当前 API 事实。
- 不新增 migration，不更新 `docs/current/DB_SCHEMA.md` 作为当前 schema 事实。
- 不改后端 Controller / DTO / Service / Repository / SQL。
- 不改前端 type / client / hook / page / test。
- 不改 Python research 代码。
- 不改 CI workflow。
- 不创建 consistency report。
- 不启动 Shadow runner、Paper runner、scheduler 或后台 runtime。
- 不调用真实交易所，不读取 credential，不访问 private endpoint。
- 不写 account / order / ledger / position。
- 不新增 review / acknowledge / approve / reject / trade / execute 写侧。

## 4. Current Fact Sources

GateT-2 后续实现应复用以下本地事实源，按只读方式派生 evidence overview：

1. `shadow_consistency_reports`：primary consistency evidence source。提供 `id`、`shadow_run_id`、`paper_run_id`、`comparison_status`、`metric_delta`、`divergence_reasons`、`limitations`、`generated_at`、`trace_id`。
2. `shadow_runs`：提供 `strategy_version_id`、`dataset_id`、`evaluation_id`、`publish_id`、`paper_run_id`、Shadow 状态、diagnostic side-effect flags 和 authorization boundary。
3. `shadow_run_snapshots`：提供 snapshot 类型覆盖、latest captured time、checksum 和 schemaVersion anchor；overview 不返回 payload。
4. `shadow_run_events`：提供 latest diagnostic event anchor、event type、reason code 和 traceId；overview 不追加 event。
5. `paper_trading_runs`：仅作为 paperRunId、strategyVersionId、trade_env、status 和 updatedAt 的只读锚点；不得读取订单、资金或成交写侧语义。
6. `strategy_versions`、`backtest_eval_reports`、`backtest_publish_records`、`marketdata_datasets`：只作为 strategy / evaluation / publish / dataset id 和状态锚点，必须保持最小 join。

不得读取：

- credential 表或 credential material。
- account、real balance、real position、ledger、live order 或 private trading 表。
- private provider 配置或 private endpoint payload。
- `.env`、key、pem、secrets、logs、dumps、backup。

## 5. Existing Capabilities Reused

GateT-2 不替代既有能力，而是在其上定义 overview 聚合：

- GateS-2：`GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}` 已支持单个 Shadow Run 的 Paper vs Shadow consistency drilldown，返回 latest consistency、metricDelta、divergenceReasons、limitations、snapshot / event summary、evidence anchors、traceId 和固定安全边界。
- GateS-2 repository 当前只读取 `shadow_runs`、`shadow_consistency_reports`、`shadow_run_snapshots`、`shadow_run_events`，所有 SQL 为 SELECT。
- GateT-1：`GET /api/shadow-validation/workflow/overview` 已从 GateS 本地 facts 派生 operator item；其中 `sourceType=CONSISTENCY_REPORT` 的 item 可链接 consistency report 证据。
- GateT-1 frontend 已在 `/strategies/validation` 页面展示 workflowState、validationDecision、severity、evidenceFreshness 和固定边界，但它不是 consistency evidence 的专用归一化模型。

## 6. Candidate Endpoint Decision

GateT-2 后续 implementation 选择唯一候选 endpoint：

```text
GET /api/paper-shadow/consistency/evidence/overview
```

选择理由：

- 语义直接归属 Paper vs Shadow consistency evidence，与 GateS-2 现有 namespace 一致。
- overview 是跨 consistency evidence 的聚合，不适合塞进单 run drilldown `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}`。
- 不选择 `GET /api/shadow-validation/consistency/evidence/overview`，因为 GateT-1 的 `shadow-validation` namespace 已承担 operator workflow 语义；GateT-2 应避免让 consistency evidence overview 被误读成 review / acknowledge / approval 工作流。
- endpoint 仍只允许 GET，不接受 request body，不提供 POST / PUT / PATCH / DELETE。

本 work order 不实现该 endpoint，`docs/current/API.md` 不应记录为当前已实现 API。

## 7. Candidate DTO Contract

候选响应 DTO：

```text
ConsistencyEvidenceOverviewResponse
```

建议字段：

- `generatedAt`
- `diagnosticOnly`
- `noSideEffect`
- `notTradingAuthorization`
- `liveDisabled`
- `realProviderImplemented`
- `privateTradingImplemented`
- `aiDhRuntimeIntegrated`
- `totalEvidenceItems`
- `consistentCount`
- `divergedCount`
- `partialCount`
- `notComparableCount`
- `failedCount`
- `staleEvidenceCount`
- `highSeverityCount`
- `criticalSeverityCount`
- `latestEvidenceItem`
- `evidenceItems`
- `severityBuckets`
- `freshnessSummary`
- `metricDeltaSummary`
- `blockers`
- `warnings`
- `nextSteps`
- `evidenceAnchors`
- `traceId`

候选 item DTO：

```text
ConsistencyEvidenceItem
```

建议字段：

- `evidenceItemId`
- `shadowRunId`
- `paperRunId`
- `consistencyReportId`
- `strategyVersionId`
- `datasetId`
- `comparisonStatus`
- `divergenceSeverity`
- `evidenceFreshness`
- `metricDelta`
- `divergenceReasons`
- `limitations`
- `evidenceAnchors`
- `traceId`
- `generatedAt`
- `diagnosticOnly`
- `noSideEffect`
- `notTradingAuthorization`

候选 summary DTO：

- `severityBuckets`：按 `NONE / LOW / MEDIUM / HIGH / CRITICAL / UNKNOWN` 计数。
- `freshnessSummary`：按 `FRESH / STALE / MISSING / PARTIAL / UNKNOWN` 计数。
- `metricDeltaSummary`：只做结构化计数和差异字段摘要，例如 `metricCount / comparableMetricCount / nonComparableMetricCount / topDeltaMetrics / limitationCodes`；不得输出盈利结论或交易建议。

响应禁止字段：

- `tradeApproved`
- `tradingReady`
- `liveReady`
- `authorizedForTrading`
- `canTrade`
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

## 8. Consistency Evidence Definition

Consistency evidence item 是从本地 Paper vs Shadow consistency facts 派生的只读诊断条目。

一个 item 至少满足：

- 能定位到 `consistencyReportId` 或明确记录为缺 report 的 safe overview 状态。
- 能定位到 `shadowRunId`；如可用，也应定位 `paperRunId`、`strategyVersionId`、`datasetId`。
- 明确 `comparisonStatus`、`divergenceSeverity`、`evidenceFreshness`。
- 包含脱敏的 `metricDelta`、`divergenceReasons`、`limitations`，并保留 evidence anchors 和 traceId。
- 固定 `diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`。

它不是：

- 交易授权。
- 自动处置结果。
- operator review 持久化记录。
- consistency report 生成命令。
- Shadow runner / scheduler 触发器。
- 真实交易所或真实账户事实。

`evidenceItemId` 建议使用 deterministic id，例如 `consistencyReportId` 存在时使用 `cse-<reportId>`，缺 report 的 safe overview item 使用 `shadowRunId + NO_REPORT` 的稳定 hash。不得依赖新表自增 id。

## 9. Freshness / Severity / Metric Semantics

### comparisonStatus

- `CONSISTENT`：本地 Paper vs Shadow 证据在当前 read model 口径下可比较且未发现差异；只表示诊断一致，不表示盈利、准入或授权。
- `DIVERGED`：本地 Paper vs Shadow 证据存在差异；需要复核，不自动拒绝、不自动处置、不自动触发交易阻断以外的写侧动作。
- `PARTIAL`：证据部分可比或部分缺失；必须 fail-closed，不能显示为成功。
- `NOT_COMPARABLE`：比较基础缺失、schema 不兼容或 evidence 不足；必须 fail-closed。
- `FAILED`：比较计算或读取失败；属于诊断阻断，需要排查。
- `NO_REPORT`：overview 可在空数据或缺 report 时用 warning 表达；不会自动创建 report。

### divergenceSeverity

- `NONE`：无诊断偏离。
- `LOW`：低优先级诊断差异。
- `MEDIUM`：中优先级诊断差异。
- `HIGH`：高优先级诊断差异，需要优先复核。
- `CRITICAL`：严重诊断阻断，需要先排查。
- `UNKNOWN`：证据不足或状态不可判定。

`HIGH` / `CRITICAL` 只表示诊断优先级，不表示自动处置、自动风控完成或交易授权。

建议首版映射：

- `CONSISTENT -> NONE`
- `PARTIAL` 无 divergenceReasons 时 `LOW`，有 divergenceReasons 时 `MEDIUM`
- `NOT_COMPARABLE -> MEDIUM`
- `DIVERGED -> HIGH`
- `FAILED -> CRITICAL`
- 无 report 或缺证据 -> `UNKNOWN`

### evidenceFreshness

候选取值：

- `FRESH`：latest report / snapshot / event 仍在允许 freshness window 内。
- `STALE`：latest evidence 超过 freshness window。
- `MISSING`：关键 evidence 缺失。
- `PARTIAL`：部分 evidence 存在但缺少 report、snapshot 类型或 trace anchor。
- `UNKNOWN`：无法判断。

后续实现可沿用 GateT-1 的 7 天 freshness window 作为首版常量，也可以在 implementation WO 中显式定义更窄常量。无论阈值如何，过期或未知不得显示为 ready。

### metricDelta

`metricDelta` 是诊断差异数据，不是收益结论。候选规范：

- 允许返回 metric name、paperValue、shadowValue、delta、unit、comparable、limitationCodes。
- 不伪造 missing metric。
- 不把正 delta 写成盈利，不把负 delta 写成亏损，不把 metric delta 写成可交易信号。
- 对 JSONB 原文应做字段白名单或摘要化；overview 不返回 raw payload。

## 10. GateT-1 Operator Item Relationship

GateT-2 consistency evidence item 与 GateT-1 operator item 的关系是只读证据关系：

- GateT-2 evidence item 是 GateT-1 operator item 的 evidence anchor 输入之一。
- GateT-1 `sourceType=CONSISTENCY_REPORT` 的 operator item 可引用 GateT-2 的 `consistencyReportId` 或 `evidenceItemId`。
- GateT-2 不读取或修改 operator review / acknowledge 状态。
- GateT-2 不生成 `VALIDATION_READY`、`APPROVED` 或 review closeout 写侧状态；它只提供 consistency evidence 的 normalized overview。
- GateT-1 可以在未来把 GateT-2 overview 的 evidence item 作为 `evidenceAnchors` 展示，但不得把它解释为交易授权。

## 11. Candidate Query Design

候选 query 应返回 bounded overview：

- 默认按 `generated_at DESC, created_at DESC, id DESC` 取最新 N 条 consistency evidence，例如 20 或 50 条，避免无界返回。
- 统计 `consistentCount / divergedCount / partialCount / notComparableCount / failedCount`。
- 统计 `staleEvidenceCount / highSeverityCount / criticalSeverityCount`。
- `latestEvidenceItem` 取最新 evidence item；无数据时为 `null`。
- `evidenceItems` 是 bounded list。
- `blockers / warnings / nextSteps` 从安全边界、缺证据、stale evidence、failed comparison 和 not-comparable 状态派生。
- `evidenceAnchors` 只汇总本地 id anchor，不返回 payload。

建议第一版 facts：

- primary from `shadow_consistency_reports scr`
- join `shadow_runs sr` on `sr.id = scr.shadow_run_id`
- left join latest snapshot aggregate by `shadow_run_id`
- left join latest event aggregate by `shadow_run_id`
- optional left join `paper_trading_runs pr` only for `paperRunId / status / trade_env` anchor
- optional left join `strategy_versions sv` only for `strategyVersionId / status` anchor

空数据 safe overview：

- `totalEvidenceItems=0`
- all counts 为 0
- `latestEvidenceItem=null`
- `warnings` 包含 `NO_CONSISTENCY_EVIDENCE`
- `nextSteps` 只允许 inspect / collect local evidence，不触发 report creation
- safety flags 固定为 fail-closed 值

## 12. Candidate Repository Design

候选 core port：

```text
ConsistencyEvidenceOverviewQueryPort
```

候选 application service：

```text
ConsistencyEvidenceOverviewQueryService
```

候选 infra adapter：

```text
JdbcConsistencyEvidenceOverviewQueryRepository
```

repository 约束：

- 只执行 SELECT，不提供 create / update / delete / review / acknowledge 方法。
- 只读允许表：`shadow_consistency_reports`、`shadow_runs`、`shadow_run_snapshots`、`shadow_run_events`，以及必要时的 `paper_trading_runs`、`strategy_versions`、`backtest_eval_reports`、`marketdata_datasets` id / status anchor。
- 不读取 `paper_trading_orders`、`paper_trading_trades`、`paper_trading_positions`、account、ledger、credential、provider、private trading 表。
- 不读取 raw snapshot payload；只读取计数、latest timestamp、snapshot_type、schema_version、checksum。
- 对 `metric_delta`、`divergence_reasons`、`limitations` 做摘要或敏感字段名 guard。
- 没有 report 时不创建 report，不 append event，不启动 runner。

service 约束：

- 只做 derived counts、freshness、severity、warnings、nextSteps 和 anchors。
- 不调用 runner、scheduler、adapter、order、account、ledger、risk write side 或 credential service。
- 不把 consistency 状态转成交易方向、交易准入、review approval 或自动处置。

controller 约束：

- 只声明 `@GetMapping("/overview")`。
- 不接受 request body。
- 不声明 POST / PUT / PATCH / DELETE。

## 13. DB / Migration Decision

GateT-2 implementation 默认不新增 migration。

决定：

- consistency evidence item 先使用 derived read model。
- `evidenceItemId` 使用 deterministic id，不需要新表。
- `severityBuckets`、`freshnessSummary`、`metricDeltaSummary` 都从本地 facts 派生，不持久化。
- durable evidence review / acknowledge 如果未来必须持久化，必须另起 DB schema review；不得在 GateT-2 偷加 migration。
- `docs/current/DB_SCHEMA.md` 不记录本候选模型为当前 schema。

## 14. No-side-effect Guard

后续 implementation 必须证明：

- HTTP 只提供 GET endpoint。
- Controller 不接受 request body。
- Service 只调用 read-only query port。
- Repository 只执行 SELECT。
- 不 INSERT / UPDATE / DELETE。
- 不创建 Shadow Run、snapshot、event、consistency report、alert、recovery、replay、order 或 ledger 记录。
- 不启动 runner、scheduler、adapter、provider 或 background job。
- 不调用真实交易所 HTTP / WebSocket 或 private endpoint。
- 不修改 account、credential、order、position、ledger、risk、Paper / Shadow 状态。

## 15. Security / Credential Boundary

后续 implementation 必须 fail-closed：

- 不读取 credential 表或 credential material。
- 不输出 credential、token、secret、passphrase、private key、cookie、signature、raw private request / response。
- 不读取 `.env`、key、pem、secrets、logs、dumps、backup。
- JSON / evidence anchor / blocker / warning / nextSteps 必须经过敏感字段名 guard。
- 如果现有 evidence 中出现 credential-like key，应过滤该字段或返回 blocker，不得原样透出。

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

## 16. LIVE / AI / DH / Integration Boundary

GateT-2 后续 implementation 仍必须保持：

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

GateT-2 不新增 DH client，不新增 NQ-DH runtime 文档或代码，不接 AI signal / AI automatic trading，不启动 Integration-1 runtime。

## 17. Implementation Scope

后续 GateT-2 implementation 的最小候选文件范围：

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowvalidation/**` 或同域 consistency evidence 子包。
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/**` 中新增 read-only query port / facts。
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/**` 中新增 SELECT-only repository。
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/**` 中新增 GET-only controller / response DTO。
- 对应 backend unit / controller / repository tests。
- `docs/current/API.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md` 仅在 endpoint 实际实现且验证通过后最小同步。

建议类名：

- `ConsistencyEvidenceOverviewQueryService`
- `ConsistencyEvidenceOverviewReadModel`
- `ConsistencyEvidenceOverviewQueryPort`
- `ConsistencyEvidenceOverviewFacts`
- `JdbcConsistencyEvidenceOverviewQueryRepository`
- `ConsistencyEvidenceOverviewController`
- `ConsistencyEvidenceOverviewResponse`

本 work order 不创建这些代码文件。

## 18. Forbidden Scope

GateT-2 implementation 禁止：

- 新增 migration 或修改历史 migration。
- 新增 POST / PUT / PATCH / DELETE。
- 增强现有 drilldown 为写侧或无界 overview。
- 新增前端页面、route、hook、client 或 E2E，除非另起 frontend 任务。
- 修改 Python research code。
- 修改 CI workflow。
- 启动 runner / scheduler。
- 调用真实交易所。
- 读取 credential material。
- 接 AI runtime 或 DH runtime。
- 实现 RealClient / real provider / private trading adapter / real permission probe。
- 下单、撤单、转账、提现。
- 把 DIVERGED / HIGH / CRITICAL 写成交易授权或自动处置。
- 把 VALIDATION_READY / APPROVED 写成可交易。
- 把 metricDelta 写成收益结论或投资建议。

## 19. Testing Plan

后续 implementation 必须至少覆盖：

1. `GET /api/paper-shadow/consistency/evidence/overview` 返回 200。
2. response 包含 `diagnosticOnly=true`。
3. response 包含 `noSideEffect=true`。
4. response 包含 `notTradingAuthorization=true`。
5. response 包含 `liveDisabled=true`。
6. response 不包含 `tradeApproved / tradingReady / liveReady / authorizedForTrading / canTrade`。
7. response 不包含 forbidden sensitive fields。
8. 不存在 POST / PUT / PATCH / DELETE 写侧 endpoint。
9. 空数据返回 safe overview。
10. 有 `CONSISTENT / DIVERGED / PARTIAL / FAILED / NOT_COMPARABLE` 证据时统计正确。
11. stale evidence 能被识别。
12. `metricDelta` 不伪造收益结论。
13. repository 只 SELECT。
14. repository 不读取 credential / account / live order / ledger / private trading 表。
15. 不启动 runner / scheduler。
16. 不新增 migration。
17. 不调用真实交易所。

建议验证命令：

```powershell
mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am "-Dtest=ConsistencyEvidenceOverviewControllerTest,ConsistencyEvidenceOverviewQueryServiceTest,JdbcConsistencyEvidenceOverviewQueryRepositoryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test
git diff --check
git diff -- backend/**/db/migration
rg -n "tradeApproved|tradingReady|liveReady|authorizedForTrading|canTrade|apiKey|secret|passphrase|token|private key|placeOrder|cancelOrder|withdraw|transfer" backend docs/current
```

## 20. Documentation Plan

本 work order 同步：

- 新增本文件。
- `README.md` / `docs/current/README.md` 增加 GateT-2 work order pointer。
- `docs/current/STATUS.md` 记录 GateT-2 work order planning decision。
- `docs/current/ROADMAP.md` 将 GateT-2 更新为 plan ready / not implemented，但不写成 implementation started。
- `docs/current/FACT_SOURCE_INDEX.md` 增加本 work order 的当前事实源优先级。
- `docs/current/TESTING.md` / `WORKLOG.md` 记录本轮 docs-only 验证和未运行代码测试说明。

本 work order 不更新 `docs/current/API.md` 或 `DB_SCHEMA.md`，因为候选 endpoint / DTO / query / consistency evidence item 都尚未实现。

## 21. P0 / P1 / P2 / P3 Risk List

P0：

- 无当前阻断。若后续 implementation 要求真实交易、credential 读取、private endpoint、LIVE 或写侧交易路径，必须阻断并重新切 Gate。

P1：

- 如果把 `DIVERGED`、`HIGH`、`CRITICAL` 写成自动拒绝、自动处置或交易授权，会破坏诊断边界；必须固定 not-trading-authorization 文案。
- 如果 overview 读取 raw payload 或 credential / account / ledger / order 表，会扩大安全面；repository 测试必须锁定允许表和 SELECT-only。

P2：

- GateS-2 当前 drilldown 是单 run 模型；GateT-2 overview 需要 bounded aggregate，避免无界查询和前端重复拼装。
- `metric_delta` 为 JSONB，字段形态可能不稳定；首版应摘要化并保留 limitations，而不是强行推断收益。
- GateT-1 operator workflow 已有 consistency report item；GateT-2 需要清楚定位为 evidence normalization，避免重复定义 operator review 状态。

P3：

- current docs 中历史 forbidden wording 较多，宽范围 `rg` 会命中否定语境；验证时需按上下文判断。
- freshness window 首版若沿用 7 天，后续可能需根据 evidence 生成频率调整；必须另起实现或 review 任务后再改。

## 22. Acceptance Criteria

GateT-2 work order acceptance：

- 已回答 GateT-2 应复用哪些事实源。
- 已选择唯一候选 endpoint：`GET /api/paper-shadow/consistency/evidence/overview`。
- 已明确 DB migration 默认不需要。
- 已定义 consistency evidence item。
- 已定义 freshness、severity、comparisonStatus、metricDelta 语义。
- 已明确 DIVERGED / HIGH / CRITICAL / VALIDATION_READY / APPROVED 不表示交易授权或自动处置。
- 已明确与 GateT-1 operator item 的只读证据关系。
- 已提供 GET-only / SELECT-only / no-side-effect 测试计划。
- 已明确后续 implementation 最小文件范围和禁止范围。
- 本轮只改允许文档，不修改 backend、frontend、research、scripts、deploy、CI、migration、business code 或 test code。

后续 implementation acceptance：

- 候选 endpoint 实际落地后必须 GET-only / SELECT-only / no-side-effect。
- 所有 response safety flags 必须固定为安全值。
- repository 不读取 credential / account / live order / ledger / private trading 表。
- 不新增 migration；不启动 runner / scheduler；不调用真实交易所。
- `metricDelta`、`divergenceReasons`、`limitations` 不透出敏感字段，不生成交易建议。

## 23. Next Implementation Prompt

后续可单独启动：

```text
NQ-GATET-2-CONSISTENCY-EVIDENCE-REFINEMENT-IMPLEMENTATION
```

范围建议：

- 只实现 `GET /api/paper-shadow/consistency/evidence/overview` 的最小 backend read model。
- 只读复用 GateS-2 consistency facts 和 Shadow facts。
- 不新增 migration，不改 frontend，不改 Python，不改 CI。
- 运行 targeted Maven tests、相关模块 Maven regression 和 forbidden-area diff。

禁止后续 prompt 顺带进入 GateT-3、frontend workbench、Python binding、scheduler readiness、AI/DH runtime 或真实交易路径。

## 24. Final Decision

```text
NQ-GATET-2-CONSISTENCY-EVIDENCE-REFINEMENT-WO：PLAN READY / NOT IMPLEMENTED / READY TO COMMIT
```
