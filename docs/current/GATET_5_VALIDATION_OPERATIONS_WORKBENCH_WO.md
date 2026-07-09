# GateT-5 Validation Operations Workbench Work Order

任务：`NQ-GATET-5-VALIDATION-OPERATIONS-WORKBENCH-WO`

日期：2026-07-09

状态：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）

本文是 GateT-5 的 documentation-only work order。它只定义现有 `/strategies/validation` 页面如何整合为 Validation Operations Workbench 的信息架构、组件边界、API 消费矩阵、状态语义和测试计划；不实现页面，不新增 route，不改前端，不改后端，不新增 API，不新增 migration，不改 Python，不改 CI。

## 1. Current Baseline

本 work order 建立在以下当前事实之上：

- 当前分支：`dev`。
- 当前 HEAD / `origin/dev`：`a5709f1afc28502a4147630a0dc7f3f0dd019eb0`。
- 当前 HEAD commit：`a5709f1a feat(gatet): add evaluation artifact preview frontend`。
- 当前 HEAD 对应 GitHub Actions：`NQ CI Baseline` run `29000065991`，`completed / success`（已完成 / 成功），`headSha=a5709f1afc28502a4147630a0dc7f3f0dd019eb0`，jobs 均为 `success`。
- GateT-4 frontend commit 已 push：当前 HEAD 等于 `origin/dev`，latest CI `headSha` 也等于当前 HEAD。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），tag 为 `nq-gater-freeze`。
- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），tag 为 `nq-gates-freeze`。
- `nq-gates-freeze` 存在；`nq-gatet-freeze` 不存在。
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

## 2. Current Fact Sources

本 work order 已只读核对以下事实源：

- `AGENTS.md`：Gate、docs-only、LIVE、AI、DH、real-provider、credential 和验证边界。
- `README.md` / [README.md](README.md)：current 入口、GateR / GateS / GateT 摘要。
- [README.md](README.md)：current authority index。
- [STATUS.md](STATUS.md)：GateS frozen / tagged、GateT-1 到 GateT-4 implementation 状态和禁止边界。
- [ROADMAP.md](ROADMAP.md)：GateT batch route。
- [API.md](API.md)：GateS、GateT-1、GateT-2、GateT-3、GateT-4 当前 GET-only API 事实。
- [TESTING.md](TESTING.md) / [WORKLOG.md](WORKLOG.md)：GateT-1 到 GateT-4 backend / frontend 验证记录。
- [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md)：当前事实源优先级和误写禁区。
- [GATET_PLAN.md](GATET_PLAN.md)：GateT Shadow Validation Operations 主线规划。
- [GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md](GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md)：GateT-1 operator workflow 语义。
- [GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md](GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md)：GateT-2 consistency evidence 语义。
- [GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md](GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md)：GateT-3 incident / replay review 语义。
- [GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md](GATET_4_PYTHON_EVALUATION_ARTIFACT_BINDING_PREVIEW_WO.md)：GateT-4 Python artifact preview 语义。
- `frontend/src/pages/strategies/StrategyValidationPage.tsx`：当前 `/strategies/validation` 页面事实、panel 顺序和现有本地组件边界。
- `frontend/src/api/query-keys.ts`：当前 query key namespace。
- `frontend/src/hooks/**`：当前 TanStack Query hook、`retry:false` 和 GET-only consumption pattern。
- `frontend/src/types/**`：当前 DTO 类型、safety flags 和 forbidden state semantics。
- `frontend/tests/e2e/strategy-validation-paper-shadow-smoke.spec.ts`：现有 targeted smoke、fixture 和 boundary assertions。

## 3. GateT-5 Objective

GateT-5 的目标是为后续最小 frontend implementation 建立 Validation Operations Workbench 整合工作单：

- 把现有 `/strategies/validation` 页面中多个只读诊断 panel 规划为一个更清晰的运营工作台结构。
- 减少信息堆叠，让 operator 先看 top summary、global blockers / warnings / nextSteps、boundary strip 和 evidence matrix，再进入 detail sections。
- 明确 Shadow Validation Workflow、Consistency Evidence、Incident / Replay Review、Evaluation Artifact Preview 与 GateS Strategy Validation / Incident / Replay 的关系。
- 继续复用现有 GET-only API、query key、hook 和 DTO，不新增后端契约。
- 明确状态语义，避免把 validation、consistency、review recommendation、checksum 或 Python artifact preview 写成交易授权。
- 给出后续 implementation 的最小文件范围、禁止范围和测试计划。

## 4. Non-goals

GateT-5 work order 不做：

- 不实现页面，不修改 `frontend/**`。
- 不新增 route，不新增导航，不新增 `/strategies/validation-operations`。
- 不新增 API，不修改 Controller / DTO / Repository / SQL。
- 不新增 DB migration，不更新 [DB_SCHEMA.md](DB_SCHEMA.md) 作为新 schema 事实。
- 不改 Python research，不执行 Python，不新增 artifact upload / import / path input。
- 不改 CI workflow，不新增复杂 E2E 矩阵。
- 不新增 review / acknowledge / approve / reject / escalate / closeout 写侧操作。
- 不新增 start / stop / execute / trade 类动作。
- 不启动 runner / scheduler，不调用真实交易所，不读取 credential。
- 不接 AI runtime，不接 DH runtime，不新增 NQ-DH Integration runtime 文档或代码。

## 5. Current Frontend Facts

当前 `/strategies/validation` 页面已经承载以下 GateS / GateT panel 和辅助区块：

| 当前区块 | 来源阶段 | 当前数据源 | 当前角色 | GateT-5 处理建议 |
| --- | --- | --- | --- | --- |
| `StrategyValidationOverviewPanel` | GateS-3 | `GET /api/strategy-validation/overview` | Strategy Validation runtime baseline，展示 counts、latestDecision、blockers / warnings / nextSteps、evidenceAnchors、traceId | 纳入 Workbench top summary 与 evidence matrix；原详细表格保留为 detail section |
| `ShadowValidationWorkflowPanel` | GateT-1 | `GET /api/shadow-validation/workflow/overview` | derived operator items、workflowState、validationDecision、evidenceFreshness、operatorItems | 纳入 Workbench top summary 与 operator queue preview；原 operator item 表保留为 detail section |
| `ConsistencyEvidenceOverviewPanel` | GateT-2 | `GET /api/paper-shadow/consistency/evidence/overview` | consistency evidence counts、latestEvidenceItem、severityBuckets、freshnessSummary、metricDeltaSummary | 纳入 Workbench top summary 与 evidence matrix；metric / bucket 细节保留为 detail section |
| `EvaluationArtifactPreviewOverviewPanel` | GateT-4 | `GET /api/strategy-validation/evaluation-artifacts/preview/overview` | Python Evaluation Artifact Preview No-file baseline、schema / checksum / metric coverage、Python readiness flags | 纳入 Workbench top summary、boundary strip 和 artifact lane；细节保留为 detail section |
| `IncidentReplayReviewOverviewPanel` | GateT-3 | `GET /api/incidents/replay/review/overview` | review counts、latestReviewItem、reviewItems、severityBuckets、freshnessSummary | 纳入 operator queue preview 与 Incident / Replay lane；review item 表保留为 detail section |
| `IncidentReplayOverviewPanel` | GateS-6 | `GET /api/incidents/replay/overview` | Incident / Replay diagnostic evidence，展示 latestEvidence、severity、blockers / warnings / nextSteps | 作为 Incident / Replay Review 的上游 evidence detail section 保留 |
| `StrategyValidationShadowWorkbench` | GateS-5 | `GET /api/strategy-validation/overview`、`GET /api/shadow-runs/overview`、`GET /api/paper-shadow/consistency/drilldown` | 现有 Strategy Validation / Shadow 组合视图 | 合并进新的 Workbench summary；避免与 top summary 重复展示 |
| `BoundarySummary` | GateQ / GateS / GateT 边界聚合 | 本地固定边界文案 | no-side-effect / authorization boundary | 提升为 Workbench boundary strip，放在 top summary 附近 |
| `StatusSemantics` | 本地状态解释 | 本地常量 | 解释 validation / preview 状态语义 | 保留为 detail / reference section |
| `TraceabilityChain` / `EvidenceMatrix` | GateQ / GateS / GateT 辅助追溯 | 已提交 query 后的只读响应 | 追溯链和证据矩阵 | Evidence Matrix 应升级为全局 matrix；TraceabilityChain 保留 detail |
| `EvaluationGatePanel` / `PaperShadowPanel` / `ShadowLivePreviewPanel` | GateQ legacy read-only query | 用户提交 query 后触发 | legacy detail query panels | 保留为低优先级 detail section，不进入 top summary 默认焦点 |

当前问题不是缺少数据源，而是多个 panel 都以同级 card 方式顺序堆叠，operator 需要在多个位置重复查看 counts、blockers、warnings、nextSteps、evidence anchors 和 boundary badges。GateT-5 应解决信息层级和复核顺序，不应新增 runtime 能力。

## 6. Validation Operations Workbench Definition

Validation Operations Workbench 是现有 `/strategies/validation` 页面内的只读诊断与人工复核运营视图。

它表示：

- 一个 summary-first 的诊断工作台。
- 一个把 strategy validation、shadow validation、consistency evidence、incident / replay review 和 Python artifact preview 放在同一复核顺序中的页面组织方式。
- 一个 evidence-first 的人工判断入口，帮助 operator 先看 blockers / warnings / freshness / severity / nextSteps，再进入 detail panel。

它不表示：

- 不表示交易授权。
- 不表示 LIVE 启用。
- 不表示 Shadow trading 启用。
- 不表示 AI / DH runtime 已集成。
- 不表示 real provider、RealClient、private trading adapter 或 real permission probe 已实现。
- 不表示 Python ML ready 或 Python live execution ready。
- 不表示 review / acknowledge / escalation / closeout 写侧已经实现。

## 7. Candidate Page Strategy

后续 implementation 应选择候选策略 2：

```text
在现有 /strategies/validation 页面内新增局部 Workbench component，不改 route。
```

选择理由：

- 现有页面已经是 Strategy Validation / Shadow / Incident / Artifact 的事实聚合入口；新增 route 会制造第二个事实入口和导航分歧。
- 现有 API、query key、hook 和 DTO 已覆盖 GateS / GateT-1 到 GateT-4 所需只读数据；新增 route 不会降低后端复杂度。
- 局部 Workbench component 可以把 top summary、evidence matrix、operator queue preview、boundary strip 和 detail sections 组织清楚，同时保留原页面 URL、权限和 smoke 覆盖。
- 不新增 route 能最小化回滚面；若 Workbench layout 需要回退，只需恢复 `StrategyValidationPage.tsx` 中的 component 调用顺序。

候选策略 1（继续使用现有 `/strategies/validation` 页面，重构为 sectioned workbench）也是可接受实现形态；GateT-5 推荐把它落实为候选策略 2 的本地 component。候选策略 3（新增 `/strategies/validation-operations`）不推荐，且本 work order 禁止实现。

## 8. Candidate Component Structure

后续 implementation 默认不新增组件目录。建议先在 `StrategyValidationPage.tsx` 内新增或重组本地组件，保持最小 diff：

- `ValidationOperationsWorkbench`：接收现有 query bundle，作为 workbench 容器。
- `ValidationOperationsTopSummary`：展示 Shadow Validation Workflow、Consistency Evidence、Incident / Replay Review、Evaluation Artifact Preview 四个主状态，以及 global blockers / warnings / nextSteps。
- `ValidationOperationsEvidenceMatrix`：统一展示 strategy validation、shadow validation、consistency evidence、incident / replay review、Python artifact preview 的 source、status、freshness、severity、traceId、anchor count。
- `ValidationOperationsOperatorQueuePreview`：从现有 operatorItems、reviewItems、blockers、warnings、nextSteps 派生只读 preview rows；只展示 severity / freshness / decision / source，不新增写侧动作。
- `ValidationOperationsBoundaryStrip`：固定展示 LIVE disabled、real provider not implemented、private trading not implemented、not trading authorization、Python ML ready NO、Python live execution ready NO、AI/DH runtime not integrated。
- `ValidationOperationsDetailSections`：把现有 panel 作为 detail sections 保留，可用折叠、锚点、分组或 summary/detail 方式组织。

如果实现时 `StrategyValidationPage.tsx` 继续膨胀到难以维护，可另起小范围 refactor，把 Workbench 本地组件拆到同目录的普通组件文件；但 GateT-5 默认不要求新增组件目录，也不要求抽出共享 design system。

## 9. Information Hierarchy

Workbench 信息层级应按 operator 复核顺序组织：

1. Top summary：先展示四条 GateT 主线状态、全局 blockers / warnings / nextSteps 和最新 generatedAt / traceId。
2. Boundary strip：紧贴 summary 展示固定安全边界，避免 operator 把诊断状态理解成交易放行。
3. Evidence matrix：按 source lane 展示 evidence status、freshness、severity、anchor count、traceId 和 limitation。
4. Operator queue preview：展示 derived operator items 与 review items 的优先级、freshness、decision 和 source；仅为预览，不可操作。
5. Detail sections：保留现有 panel 的完整表格、bucket、metric、nextSteps 和 anchors；默认可折叠或通过锚点跳转。
6. Legacy query panels：`EvaluationGatePanel`、`PaperShadowPanel`、`ShadowLivePreviewPanel` 作为用户提交 query 后的 detail，不作为默认 top summary 主路径。

## 10. Workflow Relationship

Workbench 应表达四条 GateT 主线的关系：

- `Shadow Validation Workflow` 是 operator workflow 主线，负责把 GateS 本地 facts 派生为人工复核条目。
- `Consistency Evidence` 是证据质量主线，负责说明 Paper vs Shadow 是否一致、是否 stale、是否 partial、metricDelta 是否只作为诊断摘要。
- `Incident / Replay Review` 是人工复核建议主线，负责把 incident / replay evidence 组织成 review recommendation；recommendation 不等于写侧处置。
- `Evaluation Artifact Preview` 是 Python offline artifact 只读预览主线，负责说明 No-file baseline、schema / checksum / metric coverage 和 Python readiness flags。

四条主线都必须汇入同一个 no-side-effect / not-trading-authorization boundary。Workbench 不应把其中任一主线提升为交易授权、策略批准、真实 provider 可用或 live execution readiness。

## 11. Data Consumption Matrix

| Workbench lane | Current hook | Query key | Current endpoint | Summary fields | Detail fields retained | Need new API |
| --- | --- | --- | --- | --- | --- | --- |
| Strategy validation | `useStrategyValidationOverview()` | `strategyValidationQueryKeys.overview()` | `GET /api/strategy-validation/overview` | total / evaluated / approvedForValidation / rejected / needsReview / blocked / latestDecision | blockers / warnings / nextSteps / evidenceAnchors | No |
| Shadow validation workflow | `useShadowValidationWorkflowOverview()` | `shadowValidationWorkflowQueryKeys.overview()` | `GET /api/shadow-validation/workflow/overview` | totalOperatorItems / readyForOperatorReviewCount / blockedCount / latestOperatorItem | operatorItems / blockers / warnings / nextSteps / evidenceAnchors | No |
| Consistency evidence | `useConsistencyEvidenceOverview()` | `consistencyEvidenceQueryKeys.overview()` | `GET /api/paper-shadow/consistency/evidence/overview` | totalEvidenceItems / consistent / diverged / stale / high / critical / latestEvidenceItem | evidenceItems / severityBuckets / freshnessSummary / metricDeltaSummary / anchors | No |
| Incident / Replay review | `useIncidentReplayReviewOverview()` | `incidentReplayReviewQueryKeys.overview()` | `GET /api/incidents/replay/review/overview` | totalReviewItems / needsOperatorReview / acknowledgedRecommendation / escalatedRecommendation / blocked / latestReviewItem | reviewItems / severityBuckets / freshnessSummary / blockers / warnings / anchors | No |
| Python artifact preview | `useEvaluationArtifactPreviewOverview()` | `evaluationArtifactPreviewQueryKeys.overview()` | `GET /api/strategy-validation/evaluation-artifacts/preview/overview` | totalArtifactPreviews / checksumFailedCount / pythonMlReady / pythonLiveExecutionReady / latestArtifactPreview | artifactPreviews / schemaVersionSummary / checksumSummary / metricSummaryCoverage / anchors | No |
| GateS incident / replay evidence | `useIncidentReplayOverview()` | `incidentReplayQueryKeys.overview()` | `GET /api/incidents/replay/overview` | totalEvidenceItems / incidentSeverity / latestEvidence count | latestEvidence / blockers / warnings / nextSteps / anchors | No |
| GateS shadow context | `useShadowRunOverview()` + `usePaperShadowConsistencyDrilldown()` | `shadowRunsQueryKeys.overview()` / `paperShadowQueryKeys.consistencyDrilldown()` | `GET /api/shadow-runs/overview` / `GET /api/paper-shadow/consistency/drilldown` | latestRun / divergenceSeverity / comparisonStatus | drilldown reasons / limitations / anchors | No |

结论：GateT-5 implementation 不需要新增 API；现有 API 足以支持 summary、evidence matrix、operator queue preview 和 detail sections。

## 12. State / Wording Semantics

Workbench 必须保持以下状态语义：

- `APPROVED`（验证层通过）只表示 validation evidence 满足后续 review 条件，不表示交易授权。
- `VALIDATION_READY`（验证材料就绪）只表示可进入人工复核，不表示策略可执行或 LIVE 可用。
- `CONSISTENT`（一致）只表示 Paper vs Shadow evidence 当前一致，不表示盈利、风险通过或交易授权。
- `ACKNOWLEDGE_RECOMMENDED`（建议人工确认）只表示 review recommendation，不表示系统已确认、已写入 ack 或已处置。
- `ESCALATE_RECOMMENDED`（建议人工升级复核）只表示后续人工处理建议，不触发系统升级或通知。
- `CLOSEOUT_RECOMMENDED` / `CLOSED_RECOMMENDATION`（建议形成诊断闭环）只表示诊断闭环建议，不表示真实 incident 已关闭。
- `HIGH` / `CRITICAL`（高 / 严重诊断优先级）只用于排序和警示，不映射行情方向、风控通过或交易状态。
- checksum `VALID`（校验通过）只表示 payload integrity，不表示策略有效、ML ready、live execution ready 或交易授权。
- `pythonMlReady=false` 与 `pythonLiveExecutionReady=false` 必须可见；Python artifact preview 不能被写成 Python runtime 已就绪。

UI 文案应避免交易放行、实盘就绪、批准交易、可交易等误导表达。测试只需验证 Workbench UI 不出现用户指定的禁用交易授权文案集合，不需要为每个 enum 建立完整 E2E 矩阵。

## 13. Testing Plan

后续 GateT-5 implementation 的测试计划：

1. 运行 `npm run build`。
2. 复用现有 Strategy Validation targeted smoke：`npm run test:e2e -- tests/e2e/strategy-validation-paper-shadow-smoke.spec.ts`。
3. 验证 Workbench summary 能渲染主要 section：top summary、evidence matrix、operator queue preview、boundary strip、detail sections。
4. 验证 boundary strip 出现，并展示 LIVE disabled、real provider not implemented、private trading not implemented、not trading authorization、Python ML ready NO、Python live execution ready NO、AI/DH runtime not integrated。
5. 验证 Workbench UI 不出现用户指定的禁用交易授权文案集合。
6. 验证不出现上传、导入、路径输入、执行 Python 入口。
7. 验证不发出 forbidden private / exchange request。
8. 不新增复杂 E2E 矩阵。
9. 不为每个状态补全 E2E；状态语义由 focused smoke、visible text assertion 和 existing type / component rendering 覆盖。

本文档任务不运行 `npm run build` 或 Playwright；原因是本轮未修改 `frontend/**`。

## 14. DB / Migration Decision

GateT-5 不需要新增 DB migration。

理由：

- 本轮只规划前端信息架构，不持久化任何 operator review、acknowledge、escalation 或 closeout 状态。
- Workbench summary、evidence matrix 和 operator queue preview 都可以从现有 GET-only overview response 派生。
- 若未来需要 durable review / acknowledge / escalation / closeout，必须另起 DB schema review，先证明持久化必要性，再设计表、索引、约束、COMMENT 和敏感字段禁入规则。

## 15. No-side-effect Guard

后续 implementation 必须保持：

- 不新增 POST / PUT / PATCH / DELETE client。
- 不新增 review / acknowledge / approve / reject / escalate / closeout 写侧操作。
- 不新增 start / stop / execute / trade 操作。
- 不启动 runner / scheduler。
- 不调用真实交易所，不访问 private endpoint。
- 不修改 account / order / ledger / position / strategy / Paper / Shadow 状态。
- 所有 Workbench rows 都是 derived view model，不持久化，不写 Zustand 全局业务状态。

## 16. Security / Credential Boundary

后续 implementation 不得读取、展示、复制或记录：

- `.env`、key、pem、secret、credential、token、cookie。
- API key、exchange secret、passphrase、private key、signature。
- raw private request / response。
- real account balance、real position、real order id。
- artifact local path、user path、uploaded file path 或未经脱敏的 raw artifact payload。

Workbench 只能展示已由现有 GET-only response 返回的脱敏字段、counts、status、summary、traceId 和 evidence anchors。

## 17. LIVE / AI / DH / Integration Boundary

GateT-5 必须继续固定：

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

## 18. Implementation Minimum Scope

后续 GateT-5 implementation 的最小候选文件范围：

- `frontend/src/pages/strategies/StrategyValidationPage.tsx`
- `frontend/tests/e2e/strategy-validation-paper-shadow-smoke.spec.ts`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `docs/current/FACT_SOURCE_INDEX.md`
- `docs/current/README.md`
- `README.md`（仅当入口摘要需要同步）

默认不修改：

- `frontend/src/api/**`
- `frontend/src/hooks/**`
- `frontend/src/types/**`
- `frontend/src/routes/**`
- `backend/**`
- `research/**`
- `scripts/**`
- `deploy/**`
- `.github/**`
- `backend/**/db/migration/**`
- `docs/gates/**`
- `docs/archive/**`
- `pom.xml`
- `package.json`
- lock files

## 19. P0/P1/P2/P3 Findings

### P0

- 无。

### P1

- 无。

### P2

- 当前 `/strategies/validation` 页面已经承载多个同级只读 panel，信息层级偏平，operator 需要重复在多个 panel 中查找 blockers、warnings、nextSteps、evidence anchors 和 boundary flags。GateT-5 implementation 应优先解决 summary / detail 分层。

### P3

- `StrategyValidationPage.tsx` 已较大；后续 implementation 若继续追加本地组件，需要控制 diff，避免把纯布局调整扩成无关重构。

## 20. Review Decision

NQ-GATET-5-VALIDATION-OPERATIONS-WORKBENCH-WO：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）。

推荐下一步：

- 后续另起 GateT-5 frontend implementation，只在现有 `/strategies/validation` 页面内实现局部 Workbench component，不新增 route / API / migration。

推荐 commit message：

```text
docs(gatet): define validation operations workbench work order
```
