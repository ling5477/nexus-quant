# GateT-3 Incident / Replay Review Workflow Work Order

任务：`NQ-GATET-3-INCIDENT-REPLAY-REVIEW-WORKFLOW-WO`

日期：2026-07-09

状态：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）

本文是 GateT-3 的 work order。它只定义 Incident / Replay Review Workflow 的事实源、候选 API / DTO、query / repository 方案、review state / decision 语义、测试计划和后续实现边界；不实现 endpoint，不新增 API，不新增 migration，不修改后端 / 前端 / Python / CI / 测试代码，不启动 runner / scheduler，不调用真实交易所。

## 1. Current Baseline

本 work order 建立在以下当前事实之上：

- 当前分支：`dev`。
- 当前 HEAD / `origin/dev`：`6f7848f7b0d1c3f5dce4be6a9bb344bc3a2ec7ae`。
- 当前 HEAD commit：`6f7848f7 feat(gatet): add consistency evidence overview frontend`。
- 当前 HEAD 对应 GitHub Actions：`NQ CI Baseline` run `28957253365`，`completed / success`（已完成 / 成功），`headSha=6f7848f7b0d1c3f5dce4be6a9bb344bc3a2ec7ae`。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），tag 为 `nq-gater-freeze`。
- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），tag 为 `nq-gates-freeze`。
- GateT-0：Shadow Validation Operations plan 已完成，入口为 [GATET_PLAN.md](GATET_PLAN.md)。
- GateT-1 backend + frontend：Shadow Validation Workflow overview 已完成，当前只读消费 `GET /api/shadow-validation/workflow/overview`。
- GateT-2 backend + frontend：Consistency Evidence overview 已完成，当前只读消费 `GET /api/paper-shadow/consistency/evidence/overview`。
- GateT 当前不是 `FROZEN`（已冻结）、`ACCEPTED`（已接受）或 `TAGGED`（已打 tag）；`nq-gatet-freeze` 不存在。

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

## 2. GateT-3 Objective

GateT-3 的目标是把 GateS-6 Incident / Replay overview、GateT-1 operator workflow 和 GateT-2 consistency evidence 连接成人工复核工作流规划：

- 定义 `IncidentReplayReviewItem`，统一 source、reviewState、reviewDecision、severity、freshness、blockers、warnings、nextSteps、evidenceAnchors 和 traceId。
- 明确 Incident / Replay review 是 read-only derived model 的第一版，不持久化 review item。
- 允许规划 review / acknowledge / escalation / closeout recommendation 概念，但仅限 planning-only；本 work order 不实现写侧 endpoint 或 durable 状态。
- 选择一个候选 GET endpoint，供后续实现聚合 Incident / Replay review counts、最新 review item、severity / freshness summary 和安全边界。
- 明确 `ACKNOWLEDGE_RECOMMENDED`、`ESCALATE_RECOMMENDED`、`CLOSED_RECOMMENDATION`、`HIGH`、`CRITICAL` 都只表达诊断复核语义，不表示自动处置、真实 incident 已关闭或交易授权。
- 明确 GateT-3 review item 与 GateT-1 operator item、GateT-2 consistency evidence item 之间只通过 read-only evidence anchors 建立关系。
- 明确后续 implementation 的 GET-only / SELECT-only / no-side-effect 测试计划、最小文件范围和禁止范围。

## 3. Non-goals

GateT-3 work order 不做：

- 不实现 endpoint，不新增 API，不更新 [API.md](API.md) 作为当前 API 事实。
- 不新增 migration，不更新 [DB_SCHEMA.md](DB_SCHEMA.md) 作为当前 schema 事实。
- 不改后端 Controller / DTO / Service / Repository / SQL。
- 不改前端 type / client / hook / page / test。
- 不改 Python research 代码。
- 不改 CI workflow。
- 不创建 incident。
- 不创建 alert。
- 不确认真实处置。
- 不生成真实 replay。
- 不修改 Paper / Shadow / account / order / ledger / position 状态。
- 不新增 review / acknowledge / approve / reject / start / stop / execute / trade 写侧。
- 不启动 Shadow runner、Paper runner、scheduler 或后台 runtime。
- 不调用真实交易所，不读取 credential，不访问 private endpoint。

## 4. Current Fact Sources

GateT-3 后续实现应复用以下事实源，按只读方式派生 review overview：

1. `shadow_run_events`：Shadow event、failed / blocked / illegal transition 等 incident-like evidence。
2. `shadow_consistency_reports`：Paper vs Shadow consistency divergence、failed / partial / not comparable evidence。
3. `paper_run_alerts`：Paper run alert、severity、status、title、source、createdAt；`ACKED` 只能作为已有 alert fact 的读取语义，不等于 GateT-3 review acknowledge 已实现。
4. `paper_run_recovery_events`：Paper recovery / retry 事件；只作为 recovery evidence，不表示自动恢复已授权。
5. `trade_replay_records`：Paper trade replay facts；只读展示 replay evidence，不启动新 replay。
6. `shadow_runs` / `paper_trading_runs` / `strategy_versions`：仅作为 id、状态、strategyVersionId、paperRunId、shadowRunId 和 trace anchor 的辅助 join。
7. GateT-1 derived operator items：通过 `operatorItemId`、`sourceType`、`sourceId`、`incidentEvidenceId`、`shadowRunId`、`paperRunId`、`consistencyReportId` 建立 read-only evidence anchor；不得读取或修改 review / acknowledge 写侧状态。
8. GateT-2 consistency evidence items：通过 `evidenceItemId`、`consistencyReportId`、`shadowRunId`、`paperRunId`、`comparisonStatus`、`divergenceSeverity` 和 `evidenceFreshness` 建立 read-only evidence anchor；不得复制 raw metricDelta payload。

不得读取：

- credential 表或 credential material。
- account、real balance、real position、ledger、live order 或 private trading 表。
- private provider 配置或 private endpoint payload。
- `.env`、key、pem、secrets、logs、dumps、backup。

## 5. Existing Capabilities Reused

GateT-3 不替代既有能力，而是在其上定义 review workflow 聚合：

- GateS-6：`GET /api/incidents/replay/overview` 已支持 Incident / Replay 诊断概览，来源为 `shadow_run_events`、`shadow_consistency_reports`、`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records`；它不创建 incident / alert / recovery / replay，不启动自动处置。
- GateS-6 frontend：现有 `/strategies/validation` 页面已展示 Incident / Replay counts、latest evidence、blockers / warnings / nextSteps、anchors 和固定边界；不提供自动处置。
- GateT-1：`GET /api/shadow-validation/workflow/overview` 已派生 operator item；`sourceType=INCIDENT_REPLAY` 或带 `incidentEvidenceId` 的 item 可作为 GateT-3 review item 的只读上游锚点。
- GateT-2：`GET /api/paper-shadow/consistency/evidence/overview` 已派生 consistency evidence item；`DIVERGED / HIGH / CRITICAL` 已固定为诊断语义。
- GateS archive：`docs/gates/gate-s/GATES_API_EVIDENCE_SUMMARY.md`、`GATES_FRONTEND_EVIDENCE_SUMMARY.md`、`GATES_BOUNDARY_STATEMENT.md` 提供历史 evidence，不覆盖 current code 和 current docs。

## 6. Incident / Replay Review Workflow Definition

Incident / Replay Review Workflow 是人工诊断复核规划，不是 incident runtime、replay executor 或交易执行系统。

首版 workflow：

1. `intake`：读取 GateS-6 本地 Incident / Replay facts 和 GateT-1 / GateT-2 evidence anchors。
2. `evidence review`：按 sourceType、severity、freshness、traceId 和关联 run id 检查 evidence 是否足够复核。
3. `needs operator review`：对 divergence、failed event、critical alert、stale evidence 或 blocker 生成人工复核建议。
4. `acknowledge recommendation`：只建议人工确认已知诊断事实，不写 ack，不修改 alert status，不表示已处置。
5. `escalation recommendation`：只建议后续人工升级复核，不触发系统升级动作，不通知外部系统。
6. `closeout recommendation`：只形成诊断闭环建议，不表示 incident 已真实关闭。
7. `blocked`：当 evidence 缺失、stale、source unavailable 或安全边界漂移时 fail-closed。

该 workflow 不是：

- 真实 incident 创建系统。
- 真实 alert acknowledge 系统。
- 自动 recovery / remediation 系统。
- replay generation 或 replay executor。
- 交易授权或 LIVE readiness。
- AI / DH runtime 决策闭环。

## 7. Review Item Model

GateT-3 implementation 的 review item 默认是 derived / deterministic read model，不持久化。`reviewItemId` 建议由稳定事实锚点派生，例如 `sourceType + sourceId + incidentEvidenceId + consistencyReportId` 的 deterministic hash；不得依赖新表自增 id。

候选字段：

- `reviewItemId`：derived / deterministic id。
- `sourceType`：候选 `SHADOW_EVENT`、`CONSISTENCY_DIVERGENCE`、`PAPER_ALERT`、`RECOVERY_EVENT`、`TRADE_REPLAY`、`OPERATOR_ITEM`、`CONSISTENCY_EVIDENCE`。
- `sourceId`：来源事实 id。
- `incidentEvidenceId`：incident / replay evidence id，可空。
- `replayRecordId`：trade replay record id，可空。
- `shadowRunId`：Shadow run id，可空。
- `paperRunId`：Paper run id，可空。
- `consistencyReportId`：consistency report id，可空。
- `operatorItemId`：GateT-1 derived operator item id，可空；只作为 anchor，不读取或修改写侧状态。
- `reviewState`：见第 12 节。
- `reviewDecision`：见第 12 节。
- `severity`：见第 12 节。
- `evidenceFreshness`：候选 `FRESH`、`STALE`、`MISSING`、`PARTIAL`、`UNKNOWN`。
- `summary`：脱敏诊断摘要。
- `limitations`：证据限制、缺失或不可比原因。
- `blockers`：阻断原因列表。
- `warnings`：警告列表。
- `nextSteps`：下一步人工复核建议。
- `evidenceAnchors`：本地事实来源列表。
- `traceId`：本地追踪 id，可空但应优先透传已有 trace。
- `generatedAt`：read model 生成时间。
- `diagnosticOnly`：必须为 `true`。
- `noSideEffect`：必须为 `true`。
- `notTradingAuthorization`：必须为 `true`。
- `liveDisabled`：必须为 `true`。
- `realProviderImplemented`：必须为 `false`。
- `privateTradingImplemented`：必须为 `false`。
- `aiDhRuntimeIntegrated`：必须为 `false`。

review / acknowledge 概念允许在本文中规划，但 GateT-3 work order 不实现写侧 endpoint，不持久化 review note，不修改 `paper_run_alerts.status`、`paper_run_recovery_events.status`、Paper / Shadow / order / ledger / account 状态。

## 8. Candidate API Endpoint

GateT-3 后续 implementation 选择唯一候选 endpoint：

```text
GET /api/incidents/replay/review/overview
```

选择理由：

- 直接延续 GateS-6 namespace：既有 `GET /api/incidents/replay/overview` 是 Incident / Replay 诊断概览，新增 `review/overview` 能表达“在同一诊断域上派生人工复核 overview”。
- 比 `GET /api/validation-operations/incidents/replay/overview` 更窄，不把 Incident / Replay review 扩成完整 validation operations 中心。
- 比 `GET /api/shadow-validation/incidents/replay/overview` 更少歧义，避免把 Incident / Replay review 误归入 Shadow-only workflow；GateT-3 需要同时引用 Paper alert / recovery / replay 和 consistency evidence。
- endpoint 仍只允许 GET，不接受 request body，不提供 POST / PUT / PATCH / DELETE。

本 work order 不实现该 endpoint，[API.md](API.md) 不应记录为当前已实现 API。

## 9. Candidate DTO Contract

候选响应 DTO：

```text
IncidentReplayReviewOverviewResponse
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
- `totalReviewItems`
- `intakeCount`
- `evidenceReviewCount`
- `needsOperatorReviewCount`
- `acknowledgedRecommendationCount`
- `escalatedRecommendationCount`
- `closedRecommendationCount`
- `blockedCount`
- `latestReviewItem`
- `reviewItems`
- `severityBuckets`
- `freshnessSummary`
- `blockers`
- `warnings`
- `nextSteps`
- `evidenceAnchors`
- `traceId`

候选 item DTO：

```text
IncidentReplayReviewItem
```

建议字段：

- `reviewItemId`
- `sourceType`
- `sourceId`
- `incidentEvidenceId`
- `replayRecordId`
- `shadowRunId`
- `paperRunId`
- `consistencyReportId`
- `operatorItemId`
- `reviewState`
- `reviewDecision`
- `severity`
- `evidenceFreshness`
- `summary`
- `limitations`
- `blockers`
- `warnings`
- `nextSteps`
- `evidenceAnchors`
- `traceId`
- `generatedAt`
- `diagnosticOnly`
- `noSideEffect`
- `notTradingAuthorization`

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

## 10. Candidate Query Design

候选 query 必须优先只读复用本地事实：

- 从 `shadow_run_events` 读取失败、阻断、非法流转、cancelled 等 incident-like event。
- 从 `shadow_consistency_reports` 读取 `DIVERGED / FAILED / PARTIAL / NOT_COMPARABLE` 等需要复核的 consistency evidence。
- 从 `paper_run_alerts` 读取 alert severity / status / title / source；不写 `ACKED` 或 `RESOLVED`。
- 从 `paper_run_recovery_events` 读取 recovery / retry evidence；不启动 recovery。
- 从 `trade_replay_records` 读取 replay facts；不创建新 replay。
- 可最小 join `shadow_runs`、`paper_trading_runs`、`strategy_versions` 用于 id、状态和 trace anchor。
- 可通过 deterministic anchor 与 GateT-1 operator item、GateT-2 consistency evidence item 对齐；不得要求这些 derived item 已持久化。

派生规则建议：

- `paper_run_alerts.severity=CRITICAL` 或 consistency `FAILED` -> `severity=CRITICAL`，`reviewState=NEEDS_OPERATOR_REVIEW` 或 `BLOCKED`。
- `paper_run_alerts.severity=HIGH`、consistency `DIVERGED`、Shadow event `FAILED` / `ILLEGAL_STATE_TRANSITION_ATTEMPT` -> `severity=HIGH`。
- alert 已是 `ACKED` 时可派生 `reviewDecision=ACKNOWLEDGE_RECOMMENDED` 或 `reviewState=ACKNOWLEDGED_RECOMMENDATION`，但必须写明这是建议/诊断读取，不是本系统已执行 acknowledge。
- recovery event `FAILED` 或 replay evidence 缺失 -> `reviewDecision=REVIEW_NEEDED` 或 `BLOCKED`。
- freshness 超过 implementation 定义窗口，或关键 anchor 缺失 -> `reviewDecision=STALE_EVIDENCE`，不得显示为 closeout。
- 空数据返回 safe overview：计数为 0，flags 固定，warning / nextStep 说明暂无 review evidence；不伪造 resolved / closed / ready。

## 11. Candidate Repository Design

候选 core port：

```text
IncidentReplayReviewOverviewQueryPort
```

候选 application service：

```text
IncidentReplayReviewOverviewQueryService
```

候选 infra adapter：

```text
JdbcIncidentReplayReviewOverviewQueryRepository
```

设计约束：

- repository 只包含 SELECT-only 查询方法，不提供 create / update / delete / acknowledge / escalation / closeout 方法。
- service 层只做 derived review item 组装、freshness 判定和 fail-closed 分类，不调用 runner、scheduler、adapter、order、account、ledger、risk write side 或 credential service。
- API controller 只声明 `@GetMapping`，不声明写侧 mapping。
- query 必须 bounded；第一版可固定 latest 50 review items，并对 latestReviewItem 单独排序。
- repository 不读取 raw credential、raw private payload、真实 provider response、真实账户余额、真实订单 ID 或 ledger mutation。
- JSONB 字段如被读取，只能摘要化或白名单提取，不返回 raw payload。

## 12. Review State / Decision / Severity Semantics

候选 `reviewState`：

- `INTAKE`：已进入 intake，但 evidence 尚未完整复核。
- `EVIDENCE_REVIEW`：正在基于只读 evidence 做复核。
- `NEEDS_OPERATOR_REVIEW`：需要人工复核；不表示系统已处置。
- `ACKNOWLEDGED_RECOMMENDATION`：建议人工确认已知诊断事实；不表示 ack 写侧已实现或已执行。
- `ESCALATED_RECOMMENDATION`：建议后续人工升级复核；不表示系统已触发升级动作。
- `CLOSED_RECOMMENDATION`：形成诊断闭环建议；不表示 incident 已真实关闭。
- `BLOCKED`：存在阻断 evidence、source unavailable、stale evidence 或安全边界漂移。

候选 `reviewDecision`：

- `NO_DECISION`：无可用判断。
- `REVIEW_NEEDED`：需要人工查看。
- `ACKNOWLEDGE_RECOMMENDED`：建议人工确认已知诊断事实，不表示自动处置。
- `ESCALATE_RECOMMENDED`：建议后续人工升级复核，不表示系统动作。
- `CLOSEOUT_RECOMMENDED`：建议形成诊断闭环，不表示真实关闭。
- `BLOCKED`：存在阻断项。
- `STALE_EVIDENCE`：证据过期或缺少新鲜度。

候选 `severity`：

- `NONE`：无诊断优先级。
- `INFO`：普通诊断信息。
- `WARNING`：需要关注的诊断警告。
- `HIGH`：高优先级诊断事项，需要优先复核。
- `CRITICAL`：严重诊断事项，需要先排查或保持阻断。
- `UNKNOWN`：证据不足或状态不可判定。

候选 `evidenceFreshness`：

- `FRESH`：最新 evidence 仍在允许 freshness window 内。
- `STALE`：latest evidence 超过 freshness window。
- `MISSING`：关键 evidence 缺失。
- `PARTIAL`：部分 evidence 存在但缺少必要 anchor、trace 或关联 run。
- `UNKNOWN`：无法判断。

强制语义：

- `ACKNOWLEDGE_RECOMMENDED` 只表示“建议人工确认已知诊断事实”，不表示自动处置。
- `ESCALATE_RECOMMENDED` 只表示“建议后续人工升级复核”，不表示系统已执行升级动作。
- `CLOSED_RECOMMENDATION` 只表示“形成诊断闭环建议”，不表示 incident 已真实关闭。
- `HIGH / CRITICAL` 只表示诊断优先级，不表示交易风险已处理。
- `ACKED` 作为上游 alert status 时，只能显示为 source fact；不得写成 GateT-3 review ack 已执行。
- `FRESH` 只表示证据新鲜，不表示交易准入。

## 13. Read-only Evidence Relationship

GateT-3 与 GateT-1 / GateT-2 的关系只能是 evidence anchor，不是状态同步或写侧联动：

- `operatorItemId`：可由 GateT-1 deterministic id 派生或重算，用于说明本 review item 对应哪个 operator item；不得要求 operator item 已持久化。
- `consistencyReportId` / `evidenceItemId`：可链接 GateT-2 consistency evidence item；不得复制 raw metricDelta payload，也不得回写 consistency report。
- `incidentEvidenceId`：可映射 GateS-6 latest evidence 的 local source id。
- `traceId`：优先串联 Shadow event / consistency report / request trace；缺失时只显示缺失，不伪造。
- `evidenceAnchors`：必须显示 sourceType、sourceId、sourceVersion/sourceTimestamp、traceId/description；不得暴露 raw JSON、credential、private provider payload 或真实账户 / 订单材料。

## 14. DB / Migration Decision

GateT-3 implementation 默认不新增 migration。

理由：

- GateS-6 已有 `shadow_run_events`、`shadow_consistency_reports`、`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records` 本地事实来源。
- GateT-1 / GateT-2 已证明 derived / deterministic read model 可以先满足 operator evidence 和 consistency evidence 展示。
- GateT-3 首版 review item 是派生视图，不需要新表。
- durable review / acknowledge / escalation / closeout 若后续必须持久化，必须另起 DB schema review，证明状态模型、actor/time/reason/audit trail、幂等 key、敏感字段禁入、COMMENT 和迁移回滚策略。

不得在 GateT-3 implementation 中偷加：

- review item 表。
- acknowledge 表。
- escalation 表。
- incident closeout 表。
- Flyway migration。
- 对 `paper_run_alerts` 或其他既有表的 status 更新逻辑。

## 15. No-side-effect Guard

GateT-3 implementation 必须固定：

- `diagnosticOnly=true`
- `noSideEffect=true`
- `notTradingAuthorization=true`
- `liveDisabled=true`
- `realProviderImplemented=false`
- `privateTradingImplemented=false`
- `aiDhRuntimeIntegrated=false`

后续实现测试必须证明：

- 只存在 GET endpoint。
- 不存在 POST / PUT / PATCH / DELETE 写侧 endpoint。
- repository 只执行 SELECT。
- controller 不接受 request body。
- service 不调用 runner / scheduler / adapter / order / account / ledger / credential service。
- 空数据返回 safe overview。
- source unavailable / stale / unknown 必须 fail-closed。

## 16. Security / Credential Boundary

GateT-3 不读取、不输出、不记录以下内容：

- `apiKey`、`secret`、`passphrase`、`token`、`private key`、credential material。
- raw private request / response、signature、decrypted payload、encrypted payload 真实值。
- real account balance、real position、real order id、ledger mutation。
- provider private endpoint payload。
- `.env`、key、pem、secrets、logs、dumps、backup。

响应和 UI 文案不得出现：

- `ready to trade`
- `live ready`
- `trade approved`
- `authorizedForTrading`
- `tradingReady`
- `liveReady`
- `canTrade`

## 17. LIVE / AI / DH / Integration Boundary

GateT-3 不改变以下事实：

- LIVE remains `DISABLED`（关闭）。
- AI remains `NOT STARTED`（未开始）。
- DH runtime remains `NOT INTEGRATED`（未集成）。
- Integration-1 remains `NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe remain `NOT IMPLEMENTED`（未实现）。
- Shadow trading remains `NOT ENABLED`（未启用）。
- Python ML ready remains `NO`（否）。
- Python live execution ready remains `NO`（否）。

## 18. Testing Plan

后续 GateT-3 implementation 的测试计划必须覆盖：

1. `GET /api/incidents/replay/review/overview` 返回 200。
2. response 包含 `diagnosticOnly=true`。
3. response 包含 `noSideEffect=true`。
4. response 包含 `notTradingAuthorization=true`。
5. response 包含 `liveDisabled=true`。
6. response 不包含 `tradeApproved / tradingReady / liveReady / authorizedForTrading / canTrade`。
7. response 不包含 forbidden sensitive fields。
8. 不存在 POST / PUT / PATCH / DELETE 写侧 endpoint。
9. 空数据返回 safe overview。
10. 有 alert / recovery / replay / shadow event / consistency evidence 时派生 review item。
11. stale evidence 能被识别。
12. `HIGH / CRITICAL / ACKNOWLEDGE_RECOMMENDED` 不表示自动处置或交易授权。
13. `reviewItemId` deterministic。
14. repository 只 SELECT。
15. repository 不读取 credential / account / live order / ledger / private trading 表。
16. 不启动 runner / scheduler。
17. 不新增 migration。
18. 不调用真实交易所。

Docs-only 本轮验证：

- Git baseline：branch、HEAD、origin/dev、GateS tag、GateT tag、latest CI。
- 文档 diff：`git diff --check`、`git diff --stat`。
- Forbidden-area diff：backend、frontend、research、scripts、deploy、`.github`、migration、`docs/gates`、`docs/archive` 均必须为空。
- Boundary `rg`：检查 GateT / Incident Replay Review / LIVE / AI / DH / credential / trading wording 命中语境。
- Staged checks：只允许 staged current docs 和必要 README。

## 19. Implementation Minimum Scope

后续实现若启动，最小允许范围应限定为：

- `backend/nq-api/src/main/java/**/IncidentReplayReviewOverviewController.java`
- `backend/nq-api/src/main/java/**/IncidentReplayReviewOverviewResponse.java`
- `backend/nq-api/src/test/java/**/IncidentReplayReviewOverviewControllerTest.java`
- `backend/nq-core/src/main/java/**/incidentreview/**`
- `backend/nq-core/src/main/java/**/IncidentReplayReviewOverviewFacts.java`
- `backend/nq-core/src/main/java/**/IncidentReplayReviewOverviewQueryPort.java`
- `backend/nq-core/src/test/java/**/IncidentReplayReviewOverviewQueryServiceTest.java`
- `backend/nq-infra/src/main/java/**/JdbcIncidentReplayReviewOverviewQueryRepository.java`
- `backend/nq-infra/src/test/java/**/JdbcIncidentReplayReviewOverviewQueryRepositoryTest.java`
- 必要的 current docs / README 同步。

后续实现禁止范围：

- `backend/**/db/migration/**`
- `frontend/**`，除非另起 frontend implementation 任务。
- `research/**`
- `scripts/**`
- `deploy/**`
- `.github/**`
- `docs/gates/**`
- `docs/archive/**`
- `pom.xml`
- `package.json`
- lock files
- 任意交易、credential、runner、scheduler、AI / DH runtime、real provider、private trading adapter 或真实交易所路径。

## 20. P0/P1/P2/P3 Risks

P0：

- 把 review / acknowledge / closeout recommendation 写成真实处置或交易授权。
- 新增写侧 endpoint、migration、runner / scheduler 调用、真实交易所调用或 credential 读取。
- repository 读取 credential / account / ledger / live order / private trading 表。

P1：

- `ACKED` 上游 alert fact 被误写成 GateT-3 已执行 acknowledge。
- `HIGH / CRITICAL` 被误写成交易风险已处理或自动处置完成。
- `CLOSED_RECOMMENDATION` 被误写成 incident 已关闭。
- source unavailable / stale evidence 被误显示为正常或 ready。

P2：

- reviewItemId 不稳定，导致前端或日志难以追踪。
- freshness window 未明确，导致 stale 判定不可复盘。
- evidence anchors 不足，无法追溯到 GateT-1 operator item 或 GateT-2 consistency evidence。

P3：

- 文案中英文状态缺少中文说明，operator 误解枚举。
- severity / freshness buckets 缺少 unknown / missing 空态。
- overview item limit 未写明，后续查询可能过宽。

## 21. Final Decision

GateT-3 可以进入后续 backend read model implementation，但本 work order 本身只到规划就绪：

```text
NQ-GATET-3-INCIDENT-REPLAY-REVIEW-WORKFLOW-WO：PLAN READY / NOT IMPLEMENTED / READY TO COMMIT
```

推荐下一步任务：

```text
NQ-GATET-3-INCIDENT-REPLAY-REVIEW-WORKFLOW-IMPLEMENTATION
```

下一步仍必须保持 GET-only / SELECT-only / no-side-effect / not trading authorization，不得新增 migration、写侧 review / acknowledge、runner、scheduler、真实交易所、credential、LIVE、AI runtime 或 DH runtime。
