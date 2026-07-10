# GateS-0 Plan Review: Strategy Validation Runtime Baseline

任务名称：`NQ-GATES-0-PLAN-REVIEW`。

最终状态：`PLAN READY`（规划已就绪）/ `NOT IMPLEMENTED`（未实现）/ `READY TO COMMIT`（可进入提交前复核）。本文只做 GateS fact-source reconciliation、规划审查、read-model / frontend contract 提案和验收清单；不启动 GateS-1 implementation，不新增 API、migration、CI workflow、业务代码、前端页面、Playwright 测试或 Python 研究代码。

## 1. Current Fact Source Review

- 当前项目：NexusQuant / NQ。
- 当前分支目标：`dev`。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag 为 `nq-gater-freeze`，历史归档入口为 `docs/gates/gate-r/README.md`。
- GateS：下一阶段唯一推荐主线，定义为“策略验证运营化与 Shadow 诊断闭环阶段”。
- GateS 英文核心对象：`Strategy Validation Runtime Baseline`。
- GateS-0：`PLAN / NOT IMPLEMENTED`（规划 / 未实现）。
- GateS-1：`NEXT / NOT IMPLEMENTED`（下一实施候选 / 未实现）。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow trading：`NOT ENABLED`（未启用）。
- Python ML ready：`NO`（否）。
- Python live execution ready：`NO`（否）。

## 2. GateR Frozen Baseline

GateR 已完成 Shadow Run local fact model、runner skeleton、decision trace / risk snapshot / order intent preview、shadow consistency report、Shadow Run read-only API、Shadow Run detail / replay view 和 Shadow Run list / entrypoint。GateR freeze closeout 已完成并打 tag，当前文档不得继续把 GateR 写成 `PLAN / NOT STARTED`（规划 / 未开始）或 implementation pending。

GateR frozen baseline 只代表 read-only diagnostic local fact（只读诊断本地事实）、no-side-effect（无副作用）和 frontend read-only evidence baseline，不代表 LIVE、AI、DH runtime、real provider、private trading adapter、real permission probe、真实订单或交易授权。

## 3. Current Docs Drift Findings

本轮事实源检视发现以下 drift 需要修正：

- `docs/current/ARCHITECTURE.md` 仍写 GateR `PLAN / NOT STARTED`，与 GateR `FROZEN / ACCEPTED / TAGGED` 冲突。
- `docs/current/MODULES.md` 仍写下一阶段只能是 GateR `PLAN / NOT STARTED`，与 GateS 作为下一阶段推荐主线冲突。
- `docs/current/ROADMAP.md` 仍写下一步只能进入 GateR freeze closeout，已被 GateR release tag 和 archive closeout superseded。
- `docs/current/README.md` 的 `Current Is Not` 仍包含“不是 GateR frozen / accepted”，与当前事实冲突。
- `docs/current/API.md` 的 GateR-6 / GateR-8 API 段落仍写“不代表 GateR 已冻结或接受”，需要改成 GateR API 已属于 frozen baseline，同时新增 GateS-1 future read-model contract proposal 的未实现边界。

## 4. GateS Recommended Objective

GateS 推荐目标是把 GateR 已落地的 Shadow Run local facts、Paper vs Shadow consistency report、Strategy Evaluation evidence、Data Quality diagnostic、Risk / Preflight blocker 和 Incident / Replay facts 整理成可运营的只读验证闭环。

GateS 不授权交易。GateS 的 `Strategy Validation`（策略验证）只做策略准出审查、证据链聚合、阻断原因呈现和下一步建议。即使未来 validation report 输出 `APPROVED`（验证报告层通过），也只表示“策略验证报告层面的准出结论”，不表示 LIVE 开启、交易授权、真实 provider 启用或 Shadow Live execution。

## 5. GateS Non-goals

- 不实现 GateS-1。
- 不新增 API、Controller、DTO、domain、repository、SQL 或 migration。
- 不改前端页面、API client、hooks、route、Playwright / E2E 测试。
- 不改 Python 回测、参数实验、ML 或 live execution 代码。
- 不改 CI workflow、package.json、pom.xml、deploy 或 scripts。
- 不调用真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken API。
- 不读取或输出 credential material。
- 不接 RealClient、real provider、private trading adapter 或真实 permission probe。
- 不开启 LIVE，不下单、撤单、转账、提现，不创建真实订单。
- 不接 AI runtime，不接 DH runtime，不启动 Integration-1 runtime。
- 不把 public marketdata readiness、Data Quality、permission readiness、risk preflight、Strategy Validation 或 Shadow Run 写成 trading authorization。

## 6. GateS Batch Plan

| Batch | 状态 | 目标 | 交付物边界 |
| --- | --- | --- | --- |
| GateS-0 | `PLAN / NOT IMPLEMENTED` | Plan / fact-source reconciliation | 只做 current docs 对齐、规划审查、contract proposal 和验收清单 |
| GateS-1 | `NEXT / NOT IMPLEMENTED` | Shadow Run operational dashboard / backend read model | 未来只读 read model 和 frontend contract；不得新增写接口或交易动作 |
| GateS-2 | `NOT STARTED`（未开始） | Paper vs Shadow consistency enhancement | 未来增强 consistency summary、divergence severity、stale evidence，不修改 `shadow_runs.status` 核心状态机 |
| GateS-3 | `NOT STARTED` | Strategy Evaluation Gate runtime baseline | 未来聚合策略验证证据链和 validation decision，不等于交易授权 |
| GateS-4 | `NOT STARTED` | Python parameter sweep / evaluation artifact baseline | 未来离线 artifact baseline 和 Java binding contract，不表示 Python ML ready 或 live execution ready |
| GateS-5 | `NOT STARTED` | Frontend Strategy Validation / Shadow Workbench | 未来前端只读 workbench，不提供真实交易按钮或 LIVE 操作入口 |
| GateS-6 | `NOT STARTED` | Monitoring / Incident / Replay read-only baseline | 未来只读 incident / replay center，不启动 runner、不外发通知、不触发恢复动作 |
| GateS-FREEZE | `NOT STARTED` | freeze、docs、regression、boundary audit | 未来冻结与回归审计；只有真实验证完成后才允许写 freeze / accepted |

## 7. GateS-1 Read Model Contract Proposal

GateS-1 read model 是 future contract proposal（未来契约提案），不是当前 API 或实现。候选聚合名可暂定为 `StrategyValidationRuntimeReadModel`，只读消费既有本地 facts，不新增写侧状态，不修改 `shadow_runs.status` 核心状态机。

候选响应字段必须覆盖：

- `shadowRunOverview`：Shadow Run overview，聚合 run count、latest run、status distribution、diagnostic boundary。
- `latestShadowStatus`：latest shadow status，只表达本地 Shadow Run 诊断状态。
- `staleEvidence`：stale evidence，标记数据、evaluation、Paper 或 Shadow evidence 是否过期。
- `divergenceSeverity`：divergence severity，候选值来自证据层，不回写核心状态机。
- `paperShadowConsistencySummary`：Paper vs Shadow consistency summary，聚合 `CONSISTENT`（一致）、`DIVERGED`（偏离）、`PARTIAL`（部分可比）、`NOT_COMPARABLE`（不可比）、`FAILED`（失败）、`STALE_EVIDENCE`（证据过期）。
- `strategyValidationDecision`：Strategy Validation decision，候选值为 `APPROVED`（验证报告层通过）、`REJECTED`（拒绝）、`NEEDS_REVIEW`（需要复核）、`BLOCKED`（阻断）。`APPROVED` 不表示交易授权。
- `blockers`：blocker 列表，说明不能进入下一验证阶段的硬阻断。
- `warnings`：warning 列表，说明非阻断风险或信息不完整。
- `nextSteps`：nextSteps 列表，说明下一步工程验证、数据修复或证据补齐动作。
- `evidenceAnchors`：evidence anchors，引用 evaluation report、paper run、shadow run、consistency report、dataset quality、incident / replay chain。
- `traceId` / `requestId` / `idempotencyKey`：只做追踪与幂等审计，不表达交易授权。
- `diagnosticBoundary`：固定写明 diagnostic only（仅诊断）/ no-side-effect（无副作用）。
- `authorizationBoundary`：固定写明 not trading authorization（不是交易授权）。

候选 read model 禁止字段：`tradeApproved`、`authorizedForTrading`、`liveReady`、`tradingReady`、`realProviderReady`、`privateTradingReady`、`apiKey`、`secret`、`passphrase`、`token`、`credentialMaterial`、真实订单 ID、真实账户余额、真实仓位、private endpoint raw payload。

## 8. GateS-1 Frontend Page Contract Proposal

GateS-1 frontend page contract 是 future proposal，不代表页面已实现。前端必须是专业金融后台只读诊断界面，不做 Binance Pro 式全屏交易终端，不做营销页，不隐藏风险状态。

候选页面与区域：

- Dashboard v2：system health、Data Quality、Shadow / Paper status、risk blockers；必须显示 `LIVE DISABLED`、Real provider `NOT IMPLEMENTED`、Private trading `NOT IMPLEMENTED`、AI/DH runtime not integrated。
- Strategy Validation Center：strategy version、dataset、evaluation report、paper run、shadow run、consistency report；显示 validation decision 但明确 `APPROVED` 不等于 trading authorization。
- Paper vs Shadow Workbench：paper decision、shadow decision、metric delta、divergence reasons；只读展示差异，不提供修正、执行、交易按钮。
- MarketData Quality Drilldown：source health、freshness、gap、duplicate、out-of-order、latest bar time；Data Quality diagnostic is not trading authorization。
- Risk / Preflight Blocker Panel：解释“为什么不能交易”、哪个规则阻断、下一步是什么；不得展示 live-ready / trading-ready / provider-ready。
- Incident / Replay Center：alert、event、snapshot、consistency report、replay chain；只读 replay，不触发恢复、重试、runner 或交易动作。

固定前端边界：

- 不做真实交易按钮。
- 不做 AI 决策中心。
- 不做 LIVE 操作入口。
- 不显示 live-ready / trading-ready / provider-ready。
- 必须显示 Shadow Run is diagnostic only。
- 必须显示 Data Quality diagnostic is not trading authorization。
- 必须显示 Real provider `NOT IMPLEMENTED`、Private trading `NOT IMPLEMENTED`、AI/DH runtime not integrated。

## 9. Unified Terms

| Term | 统一含义 |
| --- | --- |
| Shadow Run operationalization | 把 Shadow Run 本地诊断事实运营化展示、聚合、追踪和复盘，不启动真实交易 |
| Strategy Validation | 策略准出审查与证据链，不等于交易授权 |
| Paper vs Shadow consistency | Paper 与 Shadow 证据层一致性对照，不修改交易状态 |
| Strategy Validation Runtime Baseline | GateS 核心对象，表示策略验证运行时只读基线 |
| blocker | 硬阻断原因，必须先处理才能进入下一验证步骤 |
| warning | 非阻断风险或证据不完整提示 |
| nextSteps | 下一步工程验证、数据补齐或审查动作 |
| evidence anchor | 指向 evaluation、paper run、shadow run、dataset、incident、replay 的证据锚点 |
| diagnostic only | 仅诊断，不执行交易或写真实账户状态 |
| no-side-effect | 无副作用，不下单、不撤单、不转账、不提现、不写真实 ledger |
| not trading authorization | 不是交易授权，不表示 LIVE 或真实 provider 可用 |
| read model | 只读聚合模型，不改变源事实 |
| incident / replay | 告警、事件、快照和回放链路，只读诊断 |
| Python offline evaluation artifact | Python 离线评估产物，不表示 ML ready 或 live execution ready |
| Java binding contract | Java 侧对离线 artifact 的绑定契约，不导入真实交易状态 |

## 10. Forbidden Wording / Forbidden Actions

Forbidden wording：

- `GateS implemented`、`GateS frozen`、`GateS accepted`。
- `GateR PLAN / NOT STARTED`、`GateR implementation pending`。
- `Shadow Run real trading`、`Shadow Live execution`、`Shadow Live trading enabled`。
- `Strategy Validation APPROVED = live trading enabled`。
- `Data Quality trading authorization`、`permission readiness = live authorization`。
- `Python ML ready`、`Python live execution ready`。
- `AI trading started`、`DH integrated`、`Integration-1 runtime started`。
- `LIVE READY`、`REAL PROVIDER ENABLED`、`PRIVATE TRADING ENABLED`、`REAL PERMISSION PROBE ENABLED`、`TRADE APPROVED`。

Forbidden actions：

- 调用真实交易所 HTTP / WebSocket。
- 读取或输出 credential material。
- 实现 private trading adapter、RealClient、real provider 或真实 permission probe。
- 开启 LIVE、下单、撤单、转账、提现或创建真实订单。
- 写真实账户、资金、订单、ledger 状态。
- 新增 API、migration、controller、DTO、domain、repository、SQL、frontend page、Playwright test、Python 研究代码、CI workflow 或部署配置。

## 11. Risk List

- P0：把 validation decision 或 consistency status 误写成交易授权，可能造成 LIVE / real provider 边界误读。
- P1：把 GateR 当前状态误写回 `PLAN / NOT STARTED`，会破坏 GateR tag 后事实源一致性。
- P1：在 GateS-1 contract 中引入写接口、runner trigger、execute/trade action 或真实 provider 字段，会越过本轮 docs-only 边界。
- P2：把 Python offline evaluation artifact 写成 ML ready 或 live execution ready，会制造能力漂移。
- P2：把 stale evidence、divergence severity 或 incident / replay status 回写到 `shadow_runs.status` 核心状态机，会扩大 blast radius。
- P3：current docs 仍残留历史 GateR planning 文案，可能误导后续 agent 的下一步选择。

## 12. Validation Commands

本轮 docs-only 验证应执行：

```powershell
git status --short
git branch --show-current
git diff --check
git diff --stat
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- backend/**/db/migration
rg "GateR|GateS|Shadow Run|Shadow Live|Strategy Validation|Paper vs Shadow|consistency|diagnostic only|no-side-effect|not trading authorization|LIVE|AI|DH|Integration-1|RealClient|real provider|private trading|permission probe|ML ready|live execution|trading authorization|tradeApproved|authorizedForTrading|liveReady|tradingReady" README.md docs/current docs/gates backend frontend research/py
```

不运行 Maven、npm build、Playwright、Python pytest / mypy / ruff，除非误触代码、测试、frontend、research 或 runtime 配置。本轮不应触碰这些范围。

## 13. Acceptance Criteria

- `docs/current/*` 内部不再出现 GateR 已冻结与 GateR 未开始并存的 current-state 冲突。
- GateS 被写成下一阶段唯一推荐主线。
- GateS-0 被写成 `PLAN / NOT IMPLEMENTED`。
- GateS-1 被写成 `NEXT / NOT IMPLEMENTED`。
- GateS batch plan 覆盖 GateS-0、GateS-1、GateS-2、GateS-3、GateS-4、GateS-5、GateS-6、GateS-FREEZE。
- GateS-1 read-model contract proposal 覆盖 Shadow Run overview、latest shadow status、stale evidence、divergence severity、Paper vs Shadow summary、Strategy Validation decision、blocker / warning / nextSteps、evidence anchors、traceId / requestId / idempotencyKey、diagnostic-only boundary 和 not trading authorization boundary。
- GateS-1 frontend page contract proposal 覆盖 Dashboard v2、Strategy Validation Center、Paper vs Shadow Workbench、MarketData Quality Drilldown、Risk / Preflight Blocker Panel、Incident / Replay Center。
- 不新增业务代码、API、migration、测试、前端页面、Python 研究代码、CI workflow 或外部行为。
- 禁止区 diff 为空。

## 14. Next Concrete Action

推荐下一步是独立发起 `NQ-GATES-1-READ-MODEL-WO`（GateS-1 work order / implementation plan review），先审查 read model owner、数据来源、DTO 字段、query shape、frontend information architecture、测试范围和禁止边界；只有 GateS-1 work order 被接受后，才允许另起 implementation 任务。

本轮 review decision：`NQ-GATES-0-PLAN-REVIEW：PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）。
