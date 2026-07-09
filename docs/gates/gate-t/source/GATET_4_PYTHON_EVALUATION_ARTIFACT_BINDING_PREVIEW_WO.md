# GateT-4 Python Evaluation Artifact Binding Preview Work Order

任务：`NQ-GATET-4-PYTHON-EVALUATION-ARTIFACT-BINDING-PREVIEW-WO`

日期：2026-07-09

状态：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）

本文是 GateT-4 的 work order。它只定义 Python offline `EvaluationArtifact` 作为 GateT validation operations 只读诊断证据预览的候选设计、事实源、DTO/API 方案、source / reader 边界、校验语义、安全边界和测试计划；不实现 endpoint，不新增 API，不新增 migration，不修改 backend / frontend / research / scripts / deploy / CI / 测试代码，不执行 Python，不启动 backtest / runner / scheduler，不导入 artifact 到 DB，不调用真实交易所。

## 1. Current Baseline

本 work order 建立在以下当前事实之上：

- 当前分支：`dev`。
- 当前 HEAD / `origin/dev`：`e6d2fa5d208d179abfcae8df0257bb9cbde0ec03`。
- 当前 HEAD commit：`e6d2fa5d feat(gatet): add incident replay review frontend`。
- 当前 HEAD 对应 GitHub Actions：`NQ CI Baseline` run `28989830496`，`completed / success`（已完成 / 成功），`headSha=e6d2fa5d208d179abfcae8df0257bb9cbde0ec03`。
- GateT-3 frontend commit 已 push：当前 HEAD 等于 `origin/dev`，latest CI headSha 也等于当前 HEAD。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），tag 为 `nq-gater-freeze`。
- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），tag 为 `nq-gates-freeze`。
- GateT-0：Shadow Validation Operations plan 已完成，入口为 [GATET_PLAN.md](GATET_PLAN.md)。
- GateT-1 backend + frontend：Shadow Validation Workflow overview 已完成，当前只读消费 `GET /api/shadow-validation/workflow/overview`。
- GateT-2 backend + frontend：Consistency Evidence overview 已完成，当前只读消费 `GET /api/paper-shadow/consistency/evidence/overview`。
- GateT-3 backend + frontend：Incident / Replay Review overview 已完成，当前只读消费 `GET /api/incidents/replay/review/overview`。
- GateS-4 Python offline evaluation artifact baseline 已完成，但仍属于 `research/py` 离线研究域。
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

## 2. GateT-4 Objective

GateT-4 的目标是为后续最小 backend read model implementation 建立 Python Evaluation Artifact read-only binding preview 的可执行工作单：

- 明确 Python artifact preview 只表示离线诊断材料可预览，不表示 Java 生产事实、不表示 ML ready、不表示 live execution ready、不表示交易授权。
- 明确 artifact 的可信来源、默认 source / query 策略、reader 边界和 forbidden source。
- 选择一个唯一候选 GET endpoint，供后续实现返回 artifact preview overview。
- 定义 `PythonEvaluationArtifactPreviewOverviewResponse` 与 `PythonEvaluationArtifactPreviewItem` 候选 DTO。
- 定义 `checksumStatus`、`artifactFreshness`、`metricSummaryStatus` 等枚举语义。
- 定义 `schemaVersion`、checksum、`diagnosticOnly=true`、`notTradingAuthorization=true`、`liveExecutionReady=false`、`pythonMlReady=false`、`pythonLiveExecutionReady=false` 的 fail-closed 校验。
- 明确 artifact preview 与 `strategyVersionId` / `datasetId` / `parameterSetId` / `evidenceAnchors` 的只读关系。
- 明确后续 implementation 的最小文件范围、禁止范围和测试计划。

## 3. Non-goals

GateT-4 work order 不做：

- 不实现 endpoint，不新增 API，不更新 [API.md](API.md) 作为当前 API 事实。
- 不新增 migration，不更新 [DB_SCHEMA.md](DB_SCHEMA.md) 作为当前 schema 事实。
- 不改后端 Controller / DTO / Service / Repository / SQL。
- 不改前端 type / client / hook / page / test。
- 不改 Python research 代码。
- 不改 CI workflow。
- 不执行 Python，不调用 Java subprocess，不启动 backtest。
- 不启动 runner、scheduler、Paper run、Shadow run 或 LIVE run。
- 不导入 artifact 到 DB，不创建 artifact catalog / import record。
- 不读取任意本地路径、用户目录、上传文件或外部网络。
- 不读取 `.env`、key、pem、secret、credential、token、cookie 或日志 / dump / backup。
- 不生成交易信号，不修改 account / order / ledger / position。
- 不接 AI runtime，不接 DH runtime，不新增 NQ-DH Integration runtime 文档或代码。

## 4. Current Fact Sources

本 work order 已只读核对以下当前事实源：

- `AGENTS.md`：Gate、LIVE、AI、DH、real-provider、credential 和 docs-only 边界。
- `README.md` / [README.md](README.md)：current 入口、GateT-3 implementation 与 GateS archive 摘要。
- [docs/current/README.md](README.md)：current authority index。
- [STATUS.md](STATUS.md)：GateS frozen / tagged、GateT current implementation 状态和禁止边界。
- [ROADMAP.md](ROADMAP.md)：GateT batch route。
- [API.md](API.md)：GateS / GateT-1 / GateT-2 / GateT-3 当前 GET-only API，以及历史 GateQ-4 `POST /api/research/evaluation-artifacts/binding-preview` dry-run contract。
- [DB_SCHEMA.md](DB_SCHEMA.md)：`strategy_versions`、`backtest_eval_reports`、`paper_trading_runs`、`shadow_runs`、`shadow_consistency_reports` 等当前 schema 事实；没有 Python artifact catalog / import record 当前表。
- [TESTING.md](TESTING.md) / [WORKLOG.md](WORKLOG.md)：GateS-4 Python baseline、GateT-1 / GateT-2 / GateT-3 验证记录。
- [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md)：当前事实源优先级和误写禁区。
- [GATET_PLAN.md](GATET_PLAN.md)：GateT-0 对 Python artifact read-only binding boundary 的规划。
- [GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md](GATET_1_SHADOW_VALIDATION_WORKFLOW_WO.md)：GateT-1 read-only workflow pattern。
- [GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md](GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md)：GateT-2 evidence overview pattern。
- [GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md](GATET_3_INCIDENT_REPLAY_REVIEW_WORKFLOW_WO.md)：GateT-3 review overview pattern。
- `research/py/README.md`：Python 子工程只处理本地离线研究，不作为 Java / Python runtime bridge。
- `research/py/src/nq_research/evaluation/artifacts.py`：`EvaluationArtifact`、checksum、schema / boundary validation 和 forbidden sensitive field guard。
- `research/py/src/nq_research/evaluation/parameters.py`：`parameterSetId` 稳定生成和 JSON serializable guard。
- `research/py/tests/test_evaluation_artifacts.py`：artifact checksum、boundary、fake metrics、敏感字段和 no-network 测试。
- GateT-1 / GateT-2 / GateT-3 backend read model / controller / repository / tests：只读核对 GET-only、SELECT-only、derived item、safety flags 和 forbidden table guard。
- GateT-1 / GateT-2 / GateT-3 frontend type / API / hook / `StrategyValidationPage`：只读核对现有 `/strategies/validation` 页面仅展示诊断信息，不新增交易动作。
- `docs/gates/gate-s/GATES_PYTHON_RESEARCH_EVIDENCE_SUMMARY.md`：GateS-4 Python artifact 归档证据。

## 5. Existing Capabilities Reused

GateT-4 后续 implementation 应复用以下现有能力和事实，不重新发明生产导入链路：

- GateS-4 Python artifact baseline：`schemaVersion=python-evaluation-artifact.v1`、`source=PYTHON_OFFLINE`、`diagnosticOnly=true`、`notTradingAuthorization=true`、`liveExecutionReady=false`、checksum 不包含 checksum 字段自身、fake metrics fixture 不表示真实策略表现。
- GateQ-4 historical Java binding preview：`POST /api/research/evaluation-artifacts/binding-preview` 只校验 request body 中的 in-memory artifact JSON，不读取路径、不依赖 repository、不写库、不导入 artifact、不启动 Paper / Shadow run。GateT-4 可复用其 fail-closed 校验语义，但不能复用 POST/import/upload 形态，也不能把历史 GateQ-4 contract 写成 GateT-4 overview 已实现。
- GateS-3 Strategy Validation overview：`GET /api/strategy-validation/overview` 已提供 strategy version、dataset、evaluation、Paper / Shadow evidence anchors；`APPROVED`（验证层通过）不表示交易授权。
- GateT-1 / GateT-2 / GateT-3 overview pattern：后续 implementation 应保持 GET-only / read-only / no-side-effect / not trading authorization、derived item、bounded overview、空数据 safe overview 和 fixed safety flags。

## 6. Python Artifact Binding Preview Definition

Python Evaluation Artifact Binding Preview 是 GateT validation operations 中的只读诊断预览。

它表示：

- Java 后端可以在受控前提下展示 artifact binding readiness / schema compatibility / checksum status / metric summary coverage。
- artifact 与 `strategyVersionId`、`datasetId`、`parameterSetId` 和 evidence anchors 可以形成 read-only relation。
- preview item 是 derived / deterministic diagnostic item，不是 artifact import record，不是 DB entity，不是 strategy evaluation result，不是 publish approval。

它不表示：

- 不表示 Python artifact 已成为 Java production fact。
- 不表示 artifact 已导入 DB。
- 不表示 strategy 已批准、可发布、可 Paper / Shadow / LIVE 运行。
- 不表示 ML ready。
- 不表示 Python live execution ready。
- 不表示 trading authorization。
- 不表示真实收益或真实策略表现。

强制语义：

- Python artifact preview 只表示离线诊断材料可预览。
- `VALID` checksum 只表示 artifact payload 与 checksum 自洽或未被检测到篡改，不表示策略有效或可交易。
- `metricSummary` 只表示离线指标摘要，不表示真实收益。
- `FAKE_FIXTURE_ONLY` 必须明确不是真实策略表现。
- `liveExecutionReady` 必须为 `false`。
- `pythonMlReady` 必须为 `false`。
- `pythonLiveExecutionReady` 必须为 `false`。
- `notTradingAuthorization` 必须为 `true`。
- `liveDisabled` 必须为 `true`。
- `realProviderImplemented` 必须为 `false`。
- `privateTradingImplemented` 必须为 `false`。
- `aiDhRuntimeIntegrated` 必须为 `false`。

## 7. Candidate API Endpoint

GateT-4 后续 implementation 选择唯一候选 endpoint：

```text
GET /api/strategy-validation/evaluation-artifacts/preview/overview
```

选择理由：

- GateT-4 目标是把 Python offline artifact 作为 Strategy Validation / GateT validation operations 的只读诊断证据预览，而不是泛化 research file service。
- 该路径与现有 `GET /api/strategy-validation/overview` 的 validation-only 语义对齐，便于通过 `strategyVersionId`、`datasetId`、`parameterSetId` 和 evidence anchors 建立只读关系。
- 不选择 `GET /api/research/evaluation-artifacts/preview/overview`：该 namespace 更接近 research artifact 原始契约，容易与历史 GateQ-4 `POST /api/research/evaluation-artifacts/binding-preview` 混淆，并可能诱导 upload / import / path input 扩展。
- 不选择 `GET /api/shadow-validation/evaluation-artifacts/preview/overview`：artifact preview 不属于 Shadow-only workflow，不应被误读为 Shadow trading readiness、operator approval 或 Shadow runner 输入。
- endpoint 仍只允许 GET，不接受 request body，不提供 POST / PUT / PATCH / DELETE，不接受 file path query 参数，不提供 upload / import。

本 work order 不实现该 endpoint，[API.md](API.md) 不记录为当前已实现 API。

## 8. Candidate DTO Contract

候选响应 DTO：

```text
PythonEvaluationArtifactPreviewOverviewResponse
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
- `pythonMlReady`
- `pythonLiveExecutionReady`
- `totalArtifactPreviews`
- `validArtifactCount`
- `invalidArtifactCount`
- `staleArtifactCount`
- `checksumFailedCount`
- `latestArtifactPreview`
- `artifactPreviews`
- `schemaVersionSummary`
- `checksumSummary`
- `metricSummaryCoverage`
- `blockers`
- `warnings`
- `nextSteps`
- `evidenceAnchors`
- `traceId`

候选 item DTO：

```text
PythonEvaluationArtifactPreviewItem
```

建议字段：

- `artifactPreviewId`
- `artifactId`
- `experimentId`
- `strategyId`
- `strategyVersion`
- `strategyVersionId`
- `datasetId`
- `datasetVersion`
- `parameterSetId`
- `schemaVersion`
- `source`
- `checksumStatus`
- `artifactFreshness`
- `metricSummaryStatus`
- `costAssumptionsStatus`
- `slippageAssumptionsStatus`
- `validationWarnings`
- `limitations`
- `evidenceAnchors`
- `traceId`
- `generatedAt`
- `diagnosticOnly`
- `noSideEffect`
- `notTradingAuthorization`
- `liveExecutionReady`
- `pythonMlReady`
- `pythonLiveExecutionReady`

候选 enum：

- `checksumStatus`：`VALID` / `INVALID` / `MISSING` / `NOT_CHECKED` / `UNKNOWN`。
- `artifactFreshness`：`FRESH` / `STALE` / `MISSING` / `UNKNOWN`。
- `metricSummaryStatus`：`PRESENT` / `INCOMPLETE` / `FAKE_FIXTURE_ONLY` / `MISSING` / `UNKNOWN`。

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
- file path / local path / user path / uploaded file path
- `realOrderId`
- `realAccountBalance`
- `realPosition`
- `withdrawAddress`
- `transferTarget`

## 9. Candidate Source / Query Design

GateT-4 后续 implementation 默认选择：

```text
No-file baseline
```

默认行为：

- 不读取 artifact 文件。
- 不读取 manifest 文件。
- 不接受 query 参数中的文件路径。
- 不接受 request body。
- 不上传 artifact。
- 不调用 Python subprocess。
- 不访问网络。
- 只返回 binding readiness / schema compatibility overview 和 safe empty artifact preview list。
- `totalArtifactPreviews=0`，`artifactPreviews=[]`，`latestArtifactPreview=null`。
- `checksumSummary` 可返回 `NOT_CHECKED` / `UNKNOWN` 计数，说明本轮没有受控 artifact source。
- `warnings` 必须包含 `NO_ARTIFACT_SOURCE_CONFIGURED` 或同等语义。
- `nextSteps` 只能建议后续另起 Manifest-only implementation review，不能建议上传、导入、执行、发布或交易。

可信来源定义：

- GateT-4 当前可信事实源是代码和 current docs 中的 artifact contract、GateS-4 Python baseline 归档、GateQ-4 dry-run validator 和 GateT validation evidence anchors。
- 默认 implementation 不读取任何运行时 artifact 文件，因此没有“任意本地路径”可信问题。
- 如果后续另起任务从 No-file baseline 升级为 `Manifest-only`，manifest 必须是仓库受控、固定路径、不可由 query 参数指定、只指向 allowlist 目录内 artifact，且每个 artifact 必须携带 expected checksum；reader 必须在读取前做 path normalization / allowlist 检查，读取后重新计算 checksum。

禁止 source：

- 任意本地路径。
- 用户目录。
- 上传文件。
- 外部 URL / 网络。
- query 参数 file path。
- request body artifact JSON。
- Java 调用 Python subprocess。
- 生产 DB import table。
- credential / account / order / ledger / private provider 表。

## 10. Candidate Repository / Reader Design

No-file baseline 第一版建议不新增 repository，不新增 SQL，不新增 DB migration。

候选 core / application 结构：

```text
PythonEvaluationArtifactPreviewOverviewQueryService
PythonEvaluationArtifactPreviewOverviewReadModel
PythonEvaluationArtifactPreviewItem
PythonEvaluationArtifactPreviewSourceReader
NoFilePythonEvaluationArtifactPreviewSourceReader
```

候选 API 结构：

```text
PythonEvaluationArtifactPreviewOverviewController
PythonEvaluationArtifactPreviewOverviewResponse
```

reader 约束：

- 第一版 `NoFilePythonEvaluationArtifactPreviewSourceReader` 只返回空 source 和 safe warning，不读取文件系统、不读取 classpath artifact、不读取 DB。
- 如后续另起 Manifest-only reader，必须：
  - 只读取固定 manifest。
  - 只允许 manifest 指向受控 allowlist 目录。
  - 禁止绝对路径、`..` path traversal、用户目录、临时目录、网络 URL。
  - 对 manifest 和 artifact 均做 size limit。
  - artifact JSON 必须用 parser 读取，禁止字符串拼接。
  - checksum 必须重新计算并与 manifest expected checksum 比对。
  - schema / boundary / sensitive-field guard 必须 fail-closed。

service 约束：

- service 只组合 derived overview，不写库、不导入 artifact、不创建 strategy validation fact。
- 空 source 返回 safe overview，不抛 500。
- checksum missing / invalid 只返回 warning / blocker，不抛 500。
- `liveExecutionReady=true`、`pythonMlReady=true` 或 `pythonLiveExecutionReady=true` 必须 fail-closed。
- `metricSummaryStatus=FAKE_FIXTURE_ONLY` 必须展示为测试 fixture，不是真实策略表现。

controller 约束：

- 只声明 `@GetMapping("/overview")`。
- 不接受 request body。
- 不接受 file path query。
- 不声明 POST / PUT / PATCH / DELETE。

## 11. Schema / Checksum / Metric Semantics

### schemaVersion

- 支持值：`python-evaluation-artifact.v1`。
- 缺失、空字符串、未知版本必须 fail-closed。
- schema valid 只表示结构版本受支持，不表示策略可交易。

### checksumStatus

- `VALID`：重新计算 checksum 后与 expected checksum 一致。只表示 artifact 未被检测到篡改，不表示策略有效或可交易。
- `INVALID`：checksum 不一致，必须进入 blocker 或 invalid count。
- `MISSING`：artifact 或 manifest 缺 checksum，返回 warning / blocker，不抛 500。
- `NOT_CHECKED`：No-file baseline 或未配置 manifest 时使用。
- `UNKNOWN`：无法判定时使用，必须 fail-closed。

### artifactFreshness

- `FRESH`：artifact generatedAt 在实现定义 freshness window 内。
- `STALE`：artifact 过期，不能显示为 ready。
- `MISSING`：无 artifact source。
- `UNKNOWN`：无法判断。

No-file baseline 默认 `MISSING` 或 `UNKNOWN`，不得伪造 `FRESH`。

### metricSummaryStatus

- `PRESENT`：必要指标字段齐备，但仍只是 offline metric summary。
- `INCOMPLETE`：缺必要字段，必须 warning / blocker。
- `FAKE_FIXTURE_ONLY`：来自 `FAKE_METRICS_FIXTURE` 或同等测试 fixture，必须明确不是真实策略表现。
- `MISSING`：缺 metric summary。
- `UNKNOWN`：无法判定。

### cost / slippage assumptions

- `costAssumptionsStatus` 和 `slippageAssumptionsStatus` 只表示离线假设是否存在或完整。
- 费用与滑点假设不表示真实交易成本已验证，不表示 live execution readiness。

## 12. Read-only Evidence Relationship

GateT-4 artifact preview 与其他 GateT / GateS evidence 的关系只能是 evidence anchor，不是状态同步或写侧联动：

- `strategyVersionId`：只作为 Java strategy version anchor，不代表 artifact 已绑定到 production strategy fact。
- `datasetId` / `datasetVersion`：只作为 dataset anchor，不代表 artifact 已导入 dataset 或 coverage 表。
- `parameterSetId`：只表示 Python offline parameter identity，不代表 Java strategy parameter set 已发布。
- `evidenceAnchors`：可引用 `STRATEGY_VERSION`、`DATASET`、`PARAMETER_SET`、`EVALUATION_ARTIFACT_CONTRACT`、`PYTHON_OFFLINE_ARTIFACT`、`GATES_PYTHON_RESEARCH_EVIDENCE`、`GATEQ_BINDING_PREVIEW_CONTRACT`。
- `traceId`：只用于请求追踪；缺失时显示缺失，不伪造。
- GateT-1 operator item、GateT-2 consistency evidence item、GateT-3 review item 可以在后续前端工作台中引用 artifact preview anchor，但不得写回 artifact、operator item、consistency report 或 review item。

## 13. DB / Migration Decision

GateT-4 implementation 默认不新增 migration。

决定：

- artifact preview item 先使用 derived read model。
- No-file baseline 不需要 repository、不需要表、不需要 import record。
- 不把 Python artifact 导入生产 DB。
- 不新增 artifact catalog、artifact import record、artifact checksum audit table 或 strategy artifact binding table。
- 如果后续必须持久化 artifact catalog / import record，必须另起 DB schema review，证明 actor / time / source / checksum / path policy / retention / rollback / sensitive-field guard 和 COMMENT 规则；不得在 GateT-4 偷加 migration。
- [DB_SCHEMA.md](DB_SCHEMA.md) 不记录本候选模型为当前 schema。

## 14. No-side-effect Guard

GateT-4 后续 implementation 必须固定：

- `diagnosticOnly=true`
- `noSideEffect=true`
- `notTradingAuthorization=true`
- `liveDisabled=true`
- `realProviderImplemented=false`
- `privateTradingImplemented=false`
- `aiDhRuntimeIntegrated=false`
- `pythonMlReady=false`
- `pythonLiveExecutionReady=false`

后续实现测试必须证明：

- 只存在 GET endpoint。
- 不存在 POST / PUT / PATCH / DELETE 写侧 endpoint。
- controller 不接受 request body，不接受 file path query。
- No-file baseline 不读取文件、不访问网络、不执行 Python subprocess。
- service 不调用 runner / scheduler / adapter / order / account / ledger / credential service。
- 不 INSERT / UPDATE / DELETE。
- 不创建 Paper / Shadow / LIVE run。
- 不创建 strategy publish、evaluation report、artifact import 或 binding record。
- 空 source 返回 safe overview。
- source unavailable / stale / unknown 必须 fail-closed。

## 15. Security / Credential Boundary

GateT-4 不读取、不输出、不记录以下内容：

- `apiKey`、`secret`、`passphrase`、`token`、`private key`、credential material。
- raw private request / response、signature、decrypted payload、encrypted payload 真实值。
- real account balance、real position、real order id、ledger mutation。
- provider private endpoint payload。
- `.env`、key、pem、secrets、logs、dumps、backup。
- user home path、upload temp path、absolute artifact path。

如果 artifact JSON 或 manifest 出现 credential-like key、path-like key、private endpoint key、real order/account key，必须 fail-closed；不得过滤后继续返回 `VALID`。

## 16. LIVE / AI / DH / Integration Boundary

GateT-4 不改变以下事实：

- LIVE remains `DISABLED`（关闭）。
- AI remains `NOT STARTED`（未开始）。
- DH runtime remains `NOT INTEGRATED`（未集成）。
- Integration-1 remains `NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe remain `NOT IMPLEMENTED`（未实现）。
- Shadow trading remains `NOT ENABLED`（未启用）。
- Python ML ready remains `NO`（否）。
- Python live execution ready remains `NO`（否）。

GateT-4 不新增 DH client，不新增 NQ-DH runtime 文档或代码，不接 AI signal / AI automatic trading，不启动 Integration-1 runtime。

## 17. Implementation Minimum Scope

后续实现若启动，最小允许范围应限定为：

- `backend/nq-api/src/main/java/**/PythonEvaluationArtifactPreviewOverviewController.java`
- `backend/nq-api/src/main/java/**/PythonEvaluationArtifactPreviewOverviewResponse.java`
- `backend/nq-api/src/test/java/**/PythonEvaluationArtifactPreviewOverviewControllerTest.java`
- `backend/nq-core/src/main/java/**/pyartifactpreview/**`
- `backend/nq-core/src/test/java/**/PythonEvaluationArtifactPreviewOverviewQueryServiceTest.java`
- 必要 current docs / README 同步。

No-file baseline 第一版默认不需要：

- `backend/nq-infra/**`
- repository
- SQL
- migration
- frontend
- research
- scripts
- deploy
- CI workflow

如后续升级 Manifest-only，必须另起 implementation prompt 并在最小范围中显式加入受控 manifest resource / reader test；不得在 No-file baseline 中顺手加入。

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

## 18. Testing Plan

后续 GateT-4 implementation 的测试计划必须覆盖：

1. `GET /api/strategy-validation/evaluation-artifacts/preview/overview` 返回 200。
2. response 包含 `diagnosticOnly=true`。
3. response 包含 `noSideEffect=true`。
4. response 包含 `notTradingAuthorization=true`。
5. response 包含 `liveDisabled=true`。
6. response 包含 `pythonMlReady=false`。
7. response 包含 `pythonLiveExecutionReady=false`。
8. response 不包含 `tradeApproved / tradingReady / liveReady / authorizedForTrading / canTrade`。
9. response 不包含 forbidden sensitive fields。
10. 不存在 POST / PUT / PATCH / DELETE 写侧 endpoint。
11. 空 artifact source 返回 safe overview。
12. checksum missing / invalid 返回 warning 或 blocker，不抛 500。
13. `liveExecutionReady=true` 的 artifact 必须 fail-closed。
14. fake metrics 不被标记为真实策略表现。
15. 不执行 Python subprocess。
16. 不访问网络。
17. 不读取任意文件路径。
18. repository / reader 不读取 credential / account / live order / ledger / private trading 表。
19. 不新增 migration。
20. 不调用真实交易所。

建议后续验证命令：

```powershell
mvn -f backend/pom.xml -pl nq-api,nq-core -am "-Dtest=PythonEvaluationArtifactPreviewOverviewControllerTest,PythonEvaluationArtifactPreviewOverviewQueryServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -f backend/pom.xml -pl nq-api,nq-core -am test
git diff --check
git diff -- backend/**/db/migration
rg -n "tradeApproved|tradingReady|liveReady|authorizedForTrading|canTrade|apiKey|secret|passphrase|token|private key|placeOrder|cancelOrder|withdraw|transfer|Python subprocess|ProcessBuilder" backend docs/current
```

Docs-only 本轮验证：

- Git baseline：branch、HEAD、origin/dev、GateS tag、GateT tag、latest CI。
- 文档 diff：`git diff --check`、`git diff --stat`。
- Forbidden-area diff：backend、frontend、research、scripts、deploy、`.github`、migration、`docs/gates`、`docs/archive` 均必须为空。
- Boundary `rg`：检查 GateT / EvaluationArtifact / Python Evaluation / checksum / LIVE / AI / DH / credential / trading wording 命中语境。
- Staged checks：只允许 staged current docs 和必要 README。

## 19. P0/P1/P2/P3 Findings

P0：

- 无当前阻断。若后续 implementation 读取任意路径、执行 Python、导入 DB、调用真实交易所、读取 credential、创建 Paper / Shadow / LIVE run 或返回交易授权语义，必须阻断并重新切 Gate。

P1：

- 选择 Manifest-only 或 request-body artifact 时会扩大输入面；GateT-4 默认 No-file baseline，避免路径、上传和任意 JSON 输入风险。
- 若把 `VALID` checksum、`PRESENT` metricSummary 或 `FRESH` artifact 写成策略有效、ML ready、live execution ready 或交易授权，会破坏 GateT 安全边界；必须固定 fail-closed 文案和测试。
- 历史 GateQ-4 `POST /api/research/evaluation-artifacts/binding-preview` 容易被误用为 GateT-4 overview；GateT-4 必须使用新的 GET-only overview 规划，且不接受 request body。

P2：

- No-file baseline 第一版不会展示真实 artifact preview item，前端或 operator 只能看到 readiness / no source warning；这是安全收敛，不是功能完成。
- 如果后续需要 manifest，必须补 path allowlist、checksum、size limit、schema validation 和 reader tests，否则会引入本地文件读取面。
- GateS-4 Python artifact schema 与 GateQ-4 historical Java binding contract 字段形态存在差异，后续 implementation 必须做明确 adapter / compatibility mapping，不能用字符串拼接或模糊字段推断。

P3：

- current docs 中历史 forbidden wording 较多，宽范围 `rg` 会命中否定语境；验证时需按上下文判断。
- `artifactFreshness` 的 window 尚未实现；后续实现必须显式定义并测试 stale / unknown。
- `metricSummaryCoverage` 可先做字段覆盖摘要；不要在 overview 里输出复杂收益解释。

## 20. Acceptance Criteria

GateT-4 work order acceptance：

- 已回答 Python artifact 是否应该接入 Java/前端：默认只做 backend read-only preview planning，不实现 Java/前端接入。
- 已定义 artifact 可信来源：默认 No-file baseline，不读取任意路径、用户目录、上传文件或外部网络。
- 已明确 DB migration 默认不需要。
- 已明确不运行 Python。
- 已明确 Java 默认不读取 artifact JSON；若后续允许，必须另起 Manifest-only 任务并限定受控 manifest / allowlist / checksum。
- 已定义 Artifact Binding Preview item。
- 已定义 schemaVersion、checksum、diagnosticOnly、notTradingAuthorization、liveExecutionReady=false、pythonMlReady=false、pythonLiveExecutionReady=false 的 fail-closed 校验。
- 已定义 artifact preview 与 `strategyVersionId` / `datasetId` / `parameterSetId` / `evidenceAnchors` 的只读关系。
- 已明确避免把 Python evaluation 写成 ML ready、live execution ready 或交易授权。
- 已明确 GateT-4 implementation 最小文件范围和禁止范围。
- 已选择唯一候选 endpoint：`GET /api/strategy-validation/evaluation-artifacts/preview/overview`。
- 本轮只改允许文档，不修改 backend、frontend、research、scripts、deploy、CI、migration、business code 或 test code。

后续 implementation acceptance：

- 候选 endpoint 实际落地后必须 GET-only / no request body / no file path query / no-side-effect。
- No-file baseline 必须不读取文件、不访问网络、不执行 Python subprocess、不新增 repository / migration。
- 所有 response safety flags 必须固定为安全值。
- `validArtifactCount`、`checksumStatus`、`metricSummaryStatus` 不能表达策略准入、真实收益或交易授权。
- 不新增 migration；不启动 runner / scheduler；不调用真实交易所。

## 21. Next Implementation Prompt

后续可单独启动：

```text
NQ-GATET-4-PYTHON-EVALUATION-ARTIFACT-BINDING-PREVIEW-IMPLEMENTATION
```

范围建议：

- 只实现 `GET /api/strategy-validation/evaluation-artifacts/preview/overview` 的 No-file baseline backend read model。
- 只返回 safe overview、fixed safety flags、zero artifact preview list、no source warning 和 nextSteps。
- 不新增 migration，不改 frontend，不改 Python，不改 CI。
- 运行 targeted Maven tests、相关模块 Maven regression 和 forbidden-area diff。

禁止后续 prompt 顺带进入 frontend workbench、Manifest-only reader、scheduler readiness、AI/DH runtime 或真实交易路径。

## 22. Final Decision

```text
NQ-GATET-4-PYTHON-EVALUATION-ARTIFACT-BINDING-PREVIEW-WO：PLAN READY / NOT IMPLEMENTED / READY TO COMMIT
```
