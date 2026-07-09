# GateT-1 Shadow Validation Workflow Read Model Work Order

任务：`NQ-GATET-1-SHADOW-VALIDATION-WORKFLOW-READ-MODEL-WO`

日期：2026-07-08

状态：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）

本文是 GateT-1 的 work order。它只定义 Shadow Validation Workflow backend read model / operator model 的候选设计、事实源、DTO/API 方案、查询与测试计划；不实现 endpoint，不新增 API，不新增 migration，不修改后端 / 前端 / Python / CI / 测试代码，不启动 runner / scheduler，不调用真实交易所。

## 1. GateT-1 Objective

GateT-1 的目标是在 GateT 仍为 `PLAN / NOT STARTED`（规划 / 未开始）的前提下，为后续最小 backend read model implementation 建立可执行工作单：

- 把 GateS 的 strategy validation、Shadow Run、Paper vs Shadow consistency、Incident / Replay 只读事实组织为 operator 可理解的 derived workflow item。
- 明确 Shadow Validation Workflow 是人工运营复核闭环，不是交易执行闭环。
- 选择一个候选 GET endpoint，定义候选 DTO、query、repository、state / decision / severity 语义。
- 明确 operator review / acknowledge 只能作为后续本地复核概念规划，本轮不实现。
- 默认不新增 DB migration；operator item 先使用 derived / deterministic read model，不持久化。
- 给出后续 implementation 的最小文件范围、禁止范围和测试计划。

## 2. Current Fact Sources

本 work order 已只读核对以下当前事实源：

- `README.md`：当前入口和 GateT / GateS 摘要。
- `docs/current/README.md`：current authority index。
- `docs/current/STATUS.md`：GateS frozen / tagged、GateT plan 状态。
- `docs/current/ROADMAP.md`：GateT batch route。
- `docs/current/API.md`：GateS GET-only API 和 no-side-effect 边界。
- `docs/current/DB_SCHEMA.md`：`strategy_versions`、`backtest_eval_reports`、`backtest_publish_records`、`paper_trading_runs`、`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records`、`shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports` 当前 schema 事实。
- `docs/current/TESTING.md` / `docs/current/WORKLOG.md`：GateS / GateT-0 验证记录。
- `docs/current/FACT_SOURCE_INDEX.md`：当前事实源优先级。
- `docs/gates/gate-t/source/GATET_PLAN.md`：GateT-0 Shadow Validation Operations plan 的 archive source copy。
- `docs/gates/gate-s/**`：GateS freeze archive、evidence matrix、API / frontend / Python evidence summary、boundary statement。
- backend GateS read model / controller / repository / tests：只读检查 GET mappings、SELECT-only repository 和 safety flags。
- frontend GateS API client / hooks / pages / types：只读检查现有 panels 均为诊断展示。
- `research/py/src/nq_research/evaluation/artifacts.py` / `parameters.py` 与 tests：只读检查 offline artifact boundary。

当前 baseline：

- 当前分支：`dev`。
- 当前 HEAD / `origin/dev`：`524fdd55bb8cc242055691cb1ab75fdab0ba5f14`。
- 最新 `NQ CI Baseline`：run `28942457484`，`success`（成功），`headSha=524fdd55bb8cc242055691cb1ab75fdab0ba5f14`。
- GateT-0 commit：`524fdd55 docs(gatet): plan shadow validation operations`，已位于 `origin/dev`。
- `nq-gates-freeze` 存在；`nq-gatet-freeze` 不存在。

## 3. Existing GateS Capabilities Reused

GateT-1 后续 implementation 应复用以下 GateS 能力，不重新发明事实源：

- `GET /api/shadow-runs/overview`：Shadow Run overview，来源为 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports`。
- `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}`：Paper vs Shadow consistency drilldown，来源为 Shadow Run facts 和 latest consistency report。
- `GET /api/strategy-validation/overview`：Strategy Validation overview，来源为 strategy version、evaluation、publish、Paper run、Shadow run、consistency facts；`APPROVED`（验证层通过）只表示 validation evidence 完整，不表示交易授权。
- `GET /api/incidents/replay/overview`：Incident / Replay overview，来源为 Shadow event、consistency divergence、Paper alert、recovery event、trade replay facts。
- GateS frontend workbench：现有 `/strategies/validation` 只读页面已展示 validation、Shadow 和 Incident / Replay 诊断，但仍缺统一 operator item 模型。
- Python offline artifact baseline：仅作为未来 read-only binding preview 的参考，不在 GateT-1 implementation 直接接入。

## 4. Shadow Validation Workflow Definition

Shadow Validation Workflow 在 GateT-1 中定义为人工运营复核闭环：

1. `intake`：从 strategy validation、Shadow overview、Paper vs Shadow consistency、Incident / Replay 事实中派生候选 operator item。
2. `evidence review`：检查 evidence 是否齐备、是否 stale、是否存在 blocker / warning / limitation。
3. `operator review`：把 item 标记为需要人工复核、已具备复核材料、阻断或需要补证据。本轮只规划，不实现写侧状态。
4. `decision draft`：派生 validationDecision，用于说明当前证据是否可进入后续人工判断。
5. `closeout recommendation`：给出下一步工程建议，例如补证据、重跑离线验证、补 incident replay 证据或保持阻断。

该 workflow 不是：

- 交易执行闭环。
- 自动下单闭环。
- 真实账户风控闭环。
- AI 自动交易闭环。
- DH runtime 决策闭环。

## 5. Operator Item Model

GateT-1 implementation 的 operator item 默认是 derived read model，不持久化。`operatorItemId` 允许使用 deterministic id，例如 `sourceType + sourceId + generatedAt bucket` 或 `workflow item source fingerprint`，但不得写库。

候选字段：

- `operatorItemId`：derived / deterministic id。
- `sourceType`：来源类型，候选 `STRATEGY_VALIDATION`、`SHADOW_RUN`、`CONSISTENCY_REPORT`、`INCIDENT_REPLAY`。
- `sourceId`：来源事实 id。
- `strategyVersionId`：策略版本 id，可空。
- `datasetId`：数据集 id，可空。
- `evaluationReportId`：评估报告 id，可空。
- `paperRunId`：Paper run id，可空。
- `shadowRunId`：Shadow run id，可空。
- `consistencyReportId`：consistency report id，可空。
- `incidentEvidenceId`：incident / replay evidence id，可空。
- `workflowState`：见第 10 节。
- `validationDecision`：见第 10 节。
- `severity`：见第 10 节。
- `evidenceFreshness`：候选 `FRESH`、`STALE`、`MISSING`、`PARTIAL`、`UNKNOWN`。
- `blockers`：阻断原因列表。
- `warnings`：警告列表。
- `nextSteps`：下一步建议。
- `evidenceAnchors`：可回溯 evidence link / id / source / trace。
- `traceId`：本地追踪 id，可空但必须优先透传已有 trace。
- `diagnosticOnly`：必须为 `true`。
- `noSideEffect`：必须为 `true`。
- `notTradingAuthorization`：必须为 `true`。
- `liveDisabled`：必须为 `true`。
- `realProviderImplemented`：必须为 `false`。
- `privateTradingImplemented`：必须为 `false`。
- `aiDhRuntimeIntegrated`：必须为 `false`。

operator review / acknowledge 概念允许后续规划，但 GateT-1 work order 不实现写侧 endpoint，不持久化 review note，不修改 alert ack / recovery / order / ledger / account 状态。

## 6. Candidate API Endpoint

GateT-1 后续 implementation 推荐唯一候选 endpoint：

```text
GET /api/shadow-validation/workflow/overview
```

选择理由：

- 与 GateT 主线 `Shadow Validation Workflow` 直接对应。
- 不与已有 `GET /api/strategy-validation/overview` 混淆，避免把 Strategy Validation 的 validation-only decision 误读成更高层交易授权。
- 比 `GET /api/validation-operations/overview` 更窄，便于固定 no-side-effect 和 not trading authorization 边界。
- 只允许 GET，不接受 request body，不提供 POST / PUT / PATCH / DELETE。

本 work order 不实现该 endpoint。`docs/current/API.md` 不记录此候选为已实现 API。

## 7. Candidate DTO Contract

候选响应 DTO：

```text
ShadowValidationWorkflowOverviewResponse
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
- `totalOperatorItems`
- `intakeCount`
- `evidenceReviewCount`
- `needsEvidenceCount`
- `readyForOperatorReviewCount`
- `blockedCount`
- `closedRecommendationCount`
- `latestOperatorItem`
- `operatorItems`
- `blockers`
- `warnings`
- `nextSteps`
- `evidenceAnchors`
- `traceId`

候选 nested DTO：

- `OperatorItemResponse`：承载第 5 节字段。
- `EvidenceAnchorResponse`：`sourceType / sourceId / sourceVersion / sourceTimestamp / traceId / description`。
- `BoundaryMessageResponse`：`code / severity / message / sourceType / sourceId`。

禁止响应字段：

- `tradeApproved`
- `tradingReady`
- `liveReady`
- `authorizedForTrading`
- credential / token / secret / passphrase / private key 类字段。
- order execution command、private adapter reference、real order id、real account balance、real position。

## 8. Candidate Query Design

候选 query 必须优先只读复用现有 GateS fact sources：

- Strategy validation facts：`strategy_versions`、`backtest_eval_reports`、`backtest_publish_records`、`paper_trading_runs`、`shadow_runs`、`shadow_consistency_reports`。
- Shadow facts：`shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports`。
- Paper / operational facts：`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records`。
- Dataset facts：`marketdata_datasets` / `marketdata_dataset_coverage` 可作为 strategy validation evidence 的辅助来源，但不得放大为行情接入任务。
- Python artifact：GateT-1 不直接接入；只在 future read-only binding preview 中使用。

不得读取：

- credential 表或 credential material。
- real account / real balance / real position 表。
- live order / real order / ledger mutation 表。
- private trading provider 表或配置。
- external provider secret 配置。
- `.env`、`.env.local`、key、pem、secrets、dumps、logs、backup。

派生规则建议：

- 缺 strategy / evaluation / publish / Paper / Shadow 任一关键 evidence 时，item 进入 `NEEDS_EVIDENCE` 或 `STALE_EVIDENCE`。
- Shadow status 为 `BLOCKED` / `FAILED` 或 Incident severity 为 `HIGH` / `CRITICAL` 时，item 进入 `BLOCKED` 或 `NEEDS_REVIEW`。
- consistency 为 `DIVERGED` / `PARTIAL` / `FAILED` / `NOT_COMPARABLE` 时生成 warning 或 blocker。
- evidence stale 时不得返回 ready 语义。

## 9. Candidate Repository Design

候选 core port：

```text
ShadowValidationWorkflowOverviewQueryPort
```

候选 application service：

```text
ShadowValidationWorkflowOverviewQueryService
```

候选 infra adapter：

```text
JdbcShadowValidationWorkflowOverviewQueryRepository
```

设计约束：

- repository 只包含 SELECT-only 查询方法，不提供 create / update / delete / acknowledge / review note 方法。
- service 层只做 derived item 组装和 fail-closed 分类，不调用 runner、scheduler、adapter、order、account、ledger、risk write side 或 credential service。
- API controller 只声明 `@GetMapping`，不声明写侧 mapping。
- 空数据返回 safe overview：计数为 0，flags 固定，blockers / warnings / nextSteps 说明缺少 evidence，而不是抛 500 或伪造 ready。
- query 必须 bounded，避免 per-item 无界查询放大；第一版可固定 limit，例如 latest 20 operator items。

## 10. State / Decision / Severity Semantics

候选 `workflowState`：

- `INTAKE`：已进入 intake，但 evidence 尚未完整复核。
- `EVIDENCE_REVIEW`：正在基于只读 evidence 做复核。
- `NEEDS_EVIDENCE`：需要补充 evidence 或 evidence stale。
- `READY_FOR_OPERATOR_REVIEW`：材料可进入后续人工复核，不表示交易授权。
- `BLOCKED`：存在阻断证据或安全边界阻断。
- `CLOSED_RECOMMENDATION`：已形成诊断闭环建议，不表示自动处置完成。

候选 `validationDecision`：

- `NO_DECISION`：无可用判断。
- `VALIDATION_READY`：验证材料可进入后续人工复核，不表示交易授权。
- `NEEDS_REVIEW`：需要人工查看。
- `REJECTED`：当前 evidence 不满足验证条件。
- `BLOCKED`：存在阻断项。
- `STALE_EVIDENCE`：证据过期或缺少新鲜度。

候选 `severity`：

- `NONE`：无诊断优先级。
- `LOW`：低优先级诊断事项。
- `MEDIUM`：中等优先级诊断事项。
- `HIGH`：高优先级诊断事项，需要优先复核。
- `CRITICAL`：严重诊断事项，需要阻断后续运营判断。

强制语义：

- `VALIDATION_READY` 只表示验证材料可进入后续人工复核，不表示交易授权。
- `CLOSED_RECOMMENDATION` 只表示诊断闭环建议，不表示自动处置完成。
- `severity` 只表示诊断优先级，不表示交易风险已处理。
- `notTradingAuthorization` 必须始终为 `true`。
- `liveDisabled` 必须始终为 `true`。
- `realProviderImplemented` 必须始终为 `false`。
- `privateTradingImplemented` 必须始终为 `false`。
- `aiDhRuntimeIntegrated` 必须始终为 `false`。

## 11. No-side-effect Guard

后续 implementation 必须证明：

- HTTP 只提供一个 GET endpoint。
- Controller 不接受 request body。
- Service 只调用 read-only query port。
- Repository 只执行 SELECT。
- 不 INSERT / UPDATE / DELETE。
- 不创建 Shadow Run、consistency report、incident、alert、recovery、replay、order 或 ledger 记录。
- 不启动 runner、scheduler、adapter、provider 或 background job。
- 不调用真实交易所 HTTP / WebSocket 或 private endpoint。
- 不修改 account、credential、order、position、ledger、risk、Paper / Shadow 状态。

## 12. Security / Credential Guard

后续 implementation 必须 fail-closed：

- 不读取 credential 表或 credential material。
- 不输出 credential、token、secret、passphrase、private key、cookie、signature、raw private request / response。
- 不读取 `.env`、`.env.local`、key、pem、secrets、dumps、logs、backup。
- JSON / evidence anchor / blocker / warning / nextSteps 必须经过敏感字段名 guard。
- 如果现有 evidence 中出现 credential-like key，应丢弃该 item 或返回 blocker，不得原样透出。

## 13. LIVE / AI / DH Boundary

GateT-1 后续 implementation 仍必须保持：

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

GateT-1 不接 DH，不新增 NQ-DH runtime 文档或代码，不接 AI signal / AI automatic trading，不启动 Integration-1 runtime。

## 14. DB / Migration Decision

GateT-1 implementation 默认不新增 migration。

决定：

- operator item 先使用 derived read model。
- `operatorItemId` 使用 deterministic id，不需要新表。
- local review / acknowledge 本轮只规划，不实现，不持久化。
- durable review / acknowledge 如果后续证明必须持久化，必须另起 DB schema review，不得在 GateT-1 偷加 migration。
- `docs/current/DB_SCHEMA.md` 不记录本候选模型为当前 schema。

## 15. Backend Implementation Scope

后续 GateT-1 implementation 的最小候选文件范围：

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowvalidation/**`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/**`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/**`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/**`
- 对应 backend unit / controller / repository tests。
- `docs/current/API.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md` 仅在 implementation 实际新增 endpoint 且验证通过后最小同步。

建议类名：

- `ShadowValidationWorkflowOverviewQueryService`
- `ShadowValidationWorkflowOverviewReadModel`
- `ShadowValidationWorkflowOverviewQueryPort`
- `ShadowValidationWorkflowOverviewFacts`
- `JdbcShadowValidationWorkflowOverviewQueryRepository`
- `ShadowValidationWorkflowOverviewController`
- `ShadowValidationWorkflowOverviewResponse`

本 work order 不创建这些文件。

## 16. Forbidden Scope

GateT-1 implementation 禁止：

- 新增 migration 或修改历史 migration。
- 新增 POST / PUT / PATCH / DELETE。
- 新增前端页面、route、hook、client 或 E2E。
- 修改 Python research code。
- 修改 CI workflow。
- 启动 runner / scheduler。
- 调用真实交易所。
- 读取 credential material。
- 接 AI runtime 或 DH runtime。
- 实现 RealClient / real provider / private trading adapter / real permission probe。
- 下单、撤单、转账、提现。
- 把 operator review 写成交易授权。
- 把 Strategy Validation `APPROVED` 写成可交易。
- 把 Incident / Replay 写成自动处置。
- 把 Python artifact 写成 ML 或 live execution readiness。

## 17. Testing Plan

后续 implementation 必须至少覆盖：

1. `GET /api/shadow-validation/workflow/overview` 返回 200。
2. response 包含 `diagnosticOnly=true`。
3. response 包含 `noSideEffect=true`。
4. response 包含 `notTradingAuthorization=true`。
5. response 包含 `liveDisabled=true`。
6. response 不包含 `tradeApproved`、`tradingReady`、`liveReady`、`authorizedForTrading`。
7. response 不包含 forbidden sensitive fields。
8. 不存在 POST / PUT / PATCH / DELETE 写侧 endpoint。
9. 空数据返回 safe overview。
10. 有 validation / shadow / consistency / incident facts 时派生 operator item。
11. stale evidence 返回 `NEEDS_EVIDENCE` 或 `STALE_EVIDENCE`。
12. blocker 返回 `BLOCKED`。
13. `VALIDATION_READY` 仍不是交易授权。
14. repository 只 SELECT。
15. repository 不读取 credential / account / live order / ledger / private trading 表。
16. 不启动 runner / scheduler。
17. 不新增 migration。
18. 不调用真实交易所。

建议验证命令：

```powershell
mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am "-Dtest=ShadowValidationWorkflowOverviewControllerTest,ShadowValidationWorkflowOverviewQueryServiceTest,JdbcShadowValidationWorkflowOverviewQueryRepositoryTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test
git diff --check
git diff -- backend/**/db/migration
rg -n "tradeApproved|tradingReady|liveReady|authorizedForTrading|apiKey|secret|passphrase|token|private key|placeOrder|cancelOrder|withdraw|transfer" backend docs/current
```

## 18. Documentation Plan

本 work order 同步：

- 新增本文件。
- `README.md` / `docs/current/README.md` 增加 GateT-1 work order pointer。
- `docs/current/STATUS.md` 记录 GateT-1 work order planning decision。
- `docs/current/ROADMAP.md` 将 GateT-1 更新为 work order ready，但不写成 implementation started。
- `docs/current/FACT_SOURCE_INDEX.md` 增加本 work order 的当前事实源优先级。
- `docs/current/TESTING.md` / `WORKLOG.md` 记录本轮 docs-only 验证和未运行代码测试说明。

本 work order 不更新 `docs/current/API.md` 或 `DB_SCHEMA.md`，因为候选 endpoint / DTO / query / operator item 都尚未实现。

## 19. P0 / P1 / P2 / P3 Risk List

P0：

- 无当前阻断。若后续 implementation 要求真实交易、credential 读取、private endpoint、LIVE 或写侧交易路径，必须阻断并重新切 Gate。

P1：

- operator review / acknowledge 若过早落地为写侧 endpoint，会引入 durable audit、权限、DB schema 和误触发交易风险；GateT-1 默认不实现。
- `VALIDATION_READY` / `APPROVED` 可能被误读为交易授权；DTO、UI 和 docs 必须固定 `notTradingAuthorization=true`。

P2：

- GateS 事实源分散在 strategy、shadow、paper、monitoring、research 域；后续 read model 需要 bounded query 和清晰 evidence anchor，避免前端重复拼接规则。
- Incident / Replay 目前是 overview，不是 durable incident workflow；GateT-1 只能派生 review item，不得写成自动处置。
- Python artifact 当前是 offline research baseline；GateT-1 不应直接接 Java production binding。

P3：

- current docs 中历史 NQ-DH / Gate 记录较多，宽范围 rg 会命中否定语境；验证时需按上下文判断。
- `GATET_PLAN.md` 记录的是 GateT-0 创建时的 baseline；GateT-1 的当前 HEAD / CI 以本文件和验证记录为准。

## 20. Acceptance Criteria

GateT-1 work order acceptance：

- GateT-1 objective、facts、reused GateS capabilities、workflow definition、operator model、candidate endpoint、DTO、query、repository、state / decision / severity 语义已定义。
- 已明确 operator item 默认 derived / not persisted。
- 已明确 no migration decision。
- 已明确 not trading authorization、no-side-effect、credential、LIVE、AI、DH、Integration boundary。
- 已提供后续 implementation 文件范围和测试计划。
- 本轮只改允许的文档，不修改 backend、frontend、research、scripts、deploy、CI、migration、business code 或 test code。

后续 implementation acceptance：

- 候选 endpoint 实际落地后必须 GET-only / SELECT-only / no-side-effect。
- 所有 response safety flags 必须固定为安全值。
- repository 不读取 credential / account / live order / ledger / private trading 表。
- 不新增 migration；不启动 runner / scheduler；不调用真实交易所。

## 21. Next Implementation Prompt

后续可单独启动：

```text
NQ-GATET-1-SHADOW-VALIDATION-WORKFLOW-READ-MODEL-IMPLEMENTATION
```

范围建议：

- 只实现 `GET /api/shadow-validation/workflow/overview` 的最小 backend read model。
- 只读复用 GateS fact sources，派生 operator item。
- 不新增 migration，不实现 review / acknowledge 写侧，不改 frontend，不改 Python，不改 CI。
- 运行 targeted Maven tests 和 forbidden-area diff。

禁止后续 prompt 顺带进入 GateT-2、frontend workbench、Python binding、scheduler readiness、AI/DH runtime 或真实交易路径。
