# GateT Plan - Shadow Validation Operations

任务：`NQ-GATET-PLAN-SHADOW-VALIDATION-OPERATIONS`

日期：2026-07-08

状态：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）

本文是 GateT-0 planning 文档。它只定义 GateT 的目标、边界、候选批次、验证策略和 freeze 条件；不实现 GateT-1，不新增后端 / 前端 / Python / CI / DB 变更，不启动 runner / scheduler，不调用真实交易所。

## 1. GateT Current Baseline

- 当前分支：`dev`。
- 当前 HEAD：`ea963b82583796fcbd07927e3c46dba24b33db74`。
- 当前 `origin/dev`：`ea963b82583796fcbd07927e3c46dba24b33db74`。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag 为 `nq-gater-freeze`。
- GateS：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag），release tag 为 `nq-gates-freeze`。
- GateS archive：`docs/gates/gate-s/README.md`。
- GateS-0 到 GateS-6：`COMPLETED`（已完成）。
- 下一阶段：GateT `PLAN / NOT STARTED`（规划 / 未开始）。

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
- Python ML readiness：`NO`（否）。
- Python live execution readiness：`NO`（否）。

## 2. GateS Freeze Evidence Summary

GateS 已把只读诊断能力冻结为当前可用基线：

- GateS-1：`GET /api/shadow-runs/overview`，只读聚合 Shadow Run overview。
- GateS-2：`GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}`，只读查看 Paper vs Shadow consistency drilldown。
- GateS-3：`GET /api/strategy-validation/overview`，只读聚合 strategy / evaluation / publish / Paper / Shadow validation overview；`APPROVED`（验证层通过）只表示 validation evidence 暂时满足 review，不表示交易授权。
- GateS-4：Python offline `EvaluationArtifact` baseline，固定 `diagnosticOnly=true`、`notTradingAuthorization=true`、`liveExecutionReady=false`。
- GateS-5：frontend Strategy Validation / Shadow Workbench，只读聚合 validation、Shadow overview 和 Paper vs Shadow drilldown。
- GateS-6：`GET /api/incidents/replay/overview` 与 frontend panel，只读查看 incident / replay diagnostics，不创建 incident、不启动 replay、不自动处置。

GateS 足以支撑 GateT 做运营闭环规划，因为它已经提供 strategy validation、Shadow overview、Paper vs Shadow consistency、Incident / Replay 和 Python artifact 的只读事实来源。GateS 不足以直接支撑写侧运营流、scheduler 启动、真实交易或 Python artifact 生产绑定，因为这些能力均未实现且未被 GateS 授权。

## 3. GateT Objective

GateT 的主线目标是把 GateS 已完成的只读诊断能力组织成可复盘、可审计、可判断的 Shadow Validation Operations 工作流：

- 策略验证从“只读 evidence 展示”推进到“operator 可理解的 review workflow 规划”。
- Shadow Run 观察从 overview / drilldown 推进到可判断的 operational state、blocker、warning、next step 分类规划。
- Paper vs Shadow 差异从单点 drilldown 推进到一致性证据细化、freshness、severity 和 evidence anchor 规范。
- Incident / Replay 从只读 overview 推进到人工 review、复盘、处置建议和回放证据的流程规划。
- Python offline artifact 从研究产物推进到 Java read-only binding preview 的边界规划，但不导入、不写库、不驱动 runtime。
- 前端工作台从只读面板推进到 validation operations workbench 的信息架构规划。
- runtime scheduling 只做 readiness review；不启动真实 scheduler，不创建订单，不调用 private endpoint。

## 4. GateT Non-goals

GateT 不是 LIVE 阶段，不是真实交易阶段，不是 private trading adapter 阶段，不是真实 permission probe 阶段，不是 AI / DH runtime 交易阶段，也不是 Python ML readiness 阶段。

GateT-0 不做：

- 不新增业务功能、API、migration、前端页面、Python 代码、CI workflow 或测试代码。
- 不启动 GateT-1 implementation。
- 不启动 Shadow runner、Paper runner、scheduler 或后台常驻进程。
- 不调用真实交易所，不读取 credential material，不访问 private endpoint。
- 不下单、撤单、转账、提现。
- 不把 Shadow Run、Strategy Validation、Incident / Replay 或 Python artifact 写成交易授权。
- 不接 AI runtime，不接 DH runtime，不启动 Integration-1 runtime。

## 5. GateT Scope

本轮 GateT-0 scope：

- 新增本计划文档。
- 最小同步 `docs/current` 入口、状态、路线、事实源索引、验证记录和工作记录。
- 只读检查 GateS backend read model / controller / repository、frontend page / hook / API client、`research/py` evaluation artifact baseline。
- 输出 GateT 批次计划、边界决定、风险列表、测试策略和 freeze 条件。

后续 GateT scope 只能在单独任务中逐批启动，并且每批必须重新确认 allowed / forbidden files、验证命令、风险与回滚方式。

## 6. Shadow Validation Operations Definition

Shadow Validation Operations 是 GateT 的运营闭环定义，不是交易执行定义。

它包含：

- Intake：收集 strategy validation overview、Shadow Run overview、Paper vs Shadow drilldown、Incident / Replay overview 和 Python artifact metadata。
- Review：人工查看 blocker、warning、divergence、stale evidence、incident severity、replay anchor 和 artifact checksum。
- Decision：产出 validation review 状态，例如 `NEEDS_REVIEW`（需要复核）、`BLOCKED`（已阻断）、`REVIEWED`（已复核）、`ACKNOWLEDGED`（已确认）或 `ESCALATED`（已升级）。
- Evidence：记录只读 evidence anchor、traceId、source、generatedAt、limitation 和 next action。
- Closeout：给出下一步工程动作，例如补齐 evidence、修正策略配置、重新跑离线验证、补充 replay 证据或保持阻断。

GateT 允许规划有限 write-side operator workflow，但必须限定为本地 review / acknowledge 语义；不得触发交易、不得启动 runner、不得调用 private endpoint、不得创建真实订单、不得修改 account / ledger / order / position。任何持久化 review / acknowledge 都必须另起实现任务并先证明是否需要 migration。

## 7. Strategy Validation Workflow Plan

建议的 Strategy Validation workflow：

1. Evidence intake：读取 GateS-3 validation overview 和 GateS-5 workbench 现有证据。
2. Evidence completeness：检查 strategy version、dataset、evaluation、publish、Paper run、Shadow run、consistency report 是否齐备。
3. Decision classification：把后端 validation decision 映射为 review queue 分类；`APPROVED` 只能表达 validation 层通过，不表达交易授权。
4. Operator review：人工确认 blocker / warning / limitation / nextSteps，允许未来本地 review / acknowledge，但不得调用交易路径。
5. Review closeout：输出下一步工程任务，如补证据、补 replay、重跑 offline artifact、或继续保持 blocked。

GateT 应先做后端 read model / operator model plan，再做前端工作台。原因是 GateS 已有前端只读面板，但缺少统一的 operator review model、状态语义和 evidence 分类；先稳定后端 read model / DTO 计划可降低前端误读状态和重复拼装业务规则的风险。

## 8. Paper vs Shadow Evidence Refinement Plan

GateT 应细化 Paper vs Shadow evidence，而不是直接扩大运行能力：

- Comparison freshness：区分无 report、过期 report、最新 report、部分 evidence。
- Severity：定义 `NONE`（无偏离）、`LOW`（低）、`MEDIUM`（中）、`HIGH`（高）、`CRITICAL`（严重）等诊断优先级，不映射为交易方向。
- Metric delta：统一 count、fill、PnL、drawdown、risk block、order intent preview 等差异字段的展示边界。
- Evidence anchors：每个 divergence 必须能回到 shadowRunId、paperRunId、reportId、snapshotId、eventId 或 traceId。
- Limitation：缺失 Paper run、缺失 Shadow snapshot、缺失 report、数据 stale、schema mismatch 必须显示为 limitation，不能显示为成功。

## 9. Incident / Replay Review Workflow Plan

Incident / Replay 在 GateT 中只规划 review workflow：

- Incident intake：读取 GateS-6 overview 中 Shadow event、consistency divergence、Paper alert、recovery event、trade replay 只读事实。
- Review queue：按 severity、source type、occurredAt、traceId、strategyVersionId、shadowRunId 聚合。
- Replay review：只展示 replay facts 和 evidence anchors，不启动自动 replay，不生成新 replay，不自动恢复。
- Human closeout：未来可规划本地 acknowledge / review note，但不得自动处置、不得提交交易、不得修改真实账户状态。
- Auditability：所有 review 状态必须保留 actor、time、reason、source evidence；不得保存 credential、raw private payload 或真实 provider response。

## 10. Python Artifact Read-only Binding Boundary

GateT 需要规划 Python artifact 到 Java 的 read-only binding preview，但不能做 production binding。

允许规划：

- 读取调用方提交的 artifact JSON 或指定的已脱敏 fixture。
- 校验 `schemaVersion`、checksum、`diagnosticOnly=true`、`notTradingAuthorization=true`、`liveExecutionReady=false`。
- 返回 validation result、warnings、limitations、artifact identity 和 metric summary 的只读 preview。
- 把 artifact 与 strategy validation evidence 做临时视图关联。

禁止：

- 不从本地任意路径读取 artifact。
- 不上传到生产事实源，不写 DB，不新增 import job。
- 不触发 strategy publish、Paper run、Shadow run、scheduler 或交易动作。
- 不把 fake metrics 或 offline artifact 写成真实策略表现。
- 不表示 Python ML readiness 或 Python live execution readiness。

## 11. Frontend Workbench Candidate

GateT frontend candidate 是 Validation Operations Workbench，而不是新的交易台：

- 首页摘要：GateT readiness、review queue、evidence freshness、blocked / needs review counts。
- Strategy validation lane：显示 validation decision、evidence completeness、operator review status。
- Paper vs Shadow lane：显示 latest consistency、divergence severity、metric delta 和 evidence anchor。
- Incident / Replay lane：显示 severity、source、latest evidence、review status 和 replay limitation。
- Python artifact lane：只显示 binding preview readiness，不上传、不导入、不执行。
- Safety rail：固定展示 LIVE disabled、AI/DH not integrated、real provider not implemented、not trading authorization。

前端实现应排在 GateT 后续批次；本轮只规划，不新增页面、route、hook、client、E2E 或 package 依赖。

## 12. Backend Candidate API / DTO Plan

GateT backend candidate 应分为 read-only 与 review-only 两类：

Read-only candidates：

- `GET /api/shadow-validation/operations/overview`：聚合 validation、Shadow、Paper vs Shadow、incident / replay 和 artifact preview readiness。
- `GET /api/shadow-validation/operations/items`：返回 review queue item list。
- `GET /api/shadow-validation/operations/items/{id}`：返回单 item evidence detail。

Review-only candidates：

- 可规划本地 `acknowledge` 或 `review note` endpoint，但只能写本地 review metadata，不得触发 runner、scheduler、adapter、order、account、ledger 或 provider。
- 任何 review-only endpoint 必须先完成 DB / migration decision；若没有持久化必要证据，GateT-1 不应新增。

DTO 必须包含：

- `diagnosticOnly=true`
- `noSideEffect=true`
- `notTradingAuthorization=true`
- `liveDisabled=true`
- `realProviderImplemented=false`
- `privateTradingImplemented=false`
- `aiDhRuntimeIntegrated=false`
- evidence anchors、limitations、blockers、warnings、nextSteps。

## 13. DB / Migration Decision

本轮决定：GateT 默认不新增 DB migration。

理由：

- GateS 已有 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports`、Paper alert / recovery / replay 等本地事实来源。
- GateT-1 可以先做 read-only operator model plan，不需要新表。
- Python artifact binding preview 可以先走 request-body / fixture-level validation，不需要入库。
- write-side operator review / acknowledge 若要求 durable audit，才可能需要新表；必须另起 DB design review，证明不能复用现有 alert ack 或 worklog 类机制，并补齐 `COMMENT ON TABLE` / `COMMENT ON COLUMN` / sensitive-field 禁入规则。

## 14. Testing Strategy

本轮 GateT-0 docs-only 验证：

- Git baseline：branch、HEAD、origin/dev、GateS tag、GateT tag。
- 文档 diff：`git diff --check`、`git diff --stat`。
- Forbidden-area diff：backend、frontend、research、scripts、deploy、`.github`、migration、`docs/gates`、`docs/archive` 均必须为空。
- Boundary `rg`：检查 GateS / GateT / LIVE / AI / DH / credential / trading wording 命中语境，确认新文档未把计划写成实现或交易授权。
- Staged checks：只允许 staged current docs 和必要 README。

后续 GateT implementation 批次验证：

- 后端 read model：目标 Maven tests + 相关模块 Maven regression。
- 前端 workbench：`npm run build` + targeted Playwright smoke；必要时全量 E2E。
- Python artifact preview：pytest / mypy / ruff；Java 端只读 parser 还需要 Maven tests。
- DB migration：Flyway / migration smoke / schema comment review。
- Scheduler readiness：只允许 no-side-effect tests；不得启动真实 scheduler 或外联。

## 15. Security / Credential / LIVE Boundary

- 不读取 `.env`、`.env.local`、key、pem、secret、token、credential、logs、dumps 或 backup。
- 不输出 credential material、signature、cookie、raw private request / response 或 provider payload。
- 不调用真实交易所 HTTP / WebSocket。
- 不访问 private endpoint。
- 不新增真实下单、撤单、转账、提现路径。
- 不修改 account、ledger、order、position 或 credential state。
- LIVE 固定保持 `DISABLED`（关闭）。
- Strategy Validation review 状态、Shadow consistency 状态、Incident severity 和 Python artifact validation 都不得解释为交易授权。

## 16. AI / DH Runtime Boundary

- AI 固定保持 `NOT STARTED`（未开始）。
- DH runtime 固定保持 `NOT INTEGRATED`（未集成）。
- Integration-1 固定保持 `NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- GateT 不接 AI signal，不接 AI automatic trading，不接 DH runtime，不新增 DH client，不新增 NQ-DH runtime 文档或代码。
- `LONG_BIAS / SHORT_BIAS` 若出现在历史或 mock evidence 中，只能作为只读 bias，不得映射为 `BUY / SELL` 或交易动作。

## 17. P0 / P1 / P2 / P3 Risk List

P0：

- 无当前阻断。若后续计划要求真实交易、credential 读取、private endpoint 或 LIVE，则必须阻断并重切 Gate。

P1：

- GateT 若过早新增 review-only 写接口，可能需要 DB migration 与审计模型；当前证据不足，默认不放入 GateT-1。
- Strategy Validation 的 `APPROVED` 可能被误读为交易授权；所有 API / DTO / UI / docs 必须继续使用 validation-only wording。

P2：

- GateS 证据分散在多个 API / panel；GateT 需要统一 operator item / evidence anchor 模型，否则前端会重复拼接业务规则。
- Incident / Replay 目前是 overview，不是 durable incident workflow；GateT 需要先规划 review semantics，再考虑持久化。
- Python artifact 目前是 offline research baseline；Java preview 若读取本地路径会扩大安全面，应先限定 request-body 或 fixture。

P3：

- 部分 GateS frontend 批次依赖 build / targeted smoke / CI，而非 component tests；GateT workbench 后续可补更聚焦的 frontend smoke。
- Current docs 中历史 Gate 记录较多，宽范围 `rg` 会命中否定语境；后续验证必须按上下文判定。

## 18. GateT Batch Plan

| Batch | 状态 | 目标 | 明确不做 |
| --- | --- | --- | --- |
| GateT-0 | `PLAN READY / NOT IMPLEMENTED`（规划已就绪 / 未实现） | Plan / fact-source reconciliation；定义 GateT 主线、边界、批次和 freeze 条件 | 不启动 implementation，不改业务代码 |
| GateT-1 | `PLANNED / NOT STARTED`（已规划 / 未开始） | Shadow Validation Workflow read model / operator model plan；先稳定 read-only item、state、evidence anchor、review semantics | 不新增交易写侧，不启动 runner |
| GateT-2 | `PLANNED / NOT STARTED` | Consistency Evidence Refinement；细化 Paper vs Shadow freshness、severity、metric delta、limitations | 不创建 consistency report，不启动 scheduler |
| GateT-3 | `PLANNED / NOT STARTED` | Incident / Replay Review Workflow plan；定义人工 review、acknowledge、escalation 的边界 | 不自动处置，不生成真实 replay |
| GateT-4 | `PLANNED / NOT STARTED` | Python Evaluation Artifact read-only binding preview plan | 不写 DB，不导入 production，不表示 ML 或 live execution readiness |
| GateT-5 | `PLANNED / NOT STARTED` | Frontend Validation Operations Workbench plan；基于稳定 read model 做前端信息架构 | 不新增交易按钮，不隐藏风险状态 |
| GateT-6 | `PLANNED / NOT STARTED` | Runtime scheduling readiness review；只审查 no-side-effect scheduler 条件 | 不启动 scheduler，不调用真实交易所，不创建真实订单 |
| GateT-FREEZE | `PLANNED / NOT STARTED` | GateT closeout 条件复核、证据归档和 current docs 压缩 | 不重打 GateS tag，不把未验证内容写成通过 |

## 19. Acceptance Criteria

GateT planning acceptance：

- GateT 主线目标、非目标、scope、批次和边界已明确。
- GateS freeze evidence 已映射到 GateT 可复用能力和缺口。
- 已回答 write-side operator workflow、no-side-effect scheduler、backend/frontend 先后、DB migration、Python artifact、AI/DH、freeze 条件等关键问题。
- Current docs 最小入口已更新。
- Forbidden-area diff 为空。
- 本轮未新增 API、migration、前端页面、Python、CI、runner、scheduler 或真实交易行为。

GateT 后续 implementation acceptance：

- 每批都有独立 work order、allowed / forbidden files、验证命令和 rollback。
- 所有新增 read model 固定 no-side-effect / not trading authorization。
- 任何 review-only 写侧必须先通过 DB / audit / security review。
- 所有 frontend 状态 fail-closed，不把缺数据、通过、确认、复核或一致性状态显示成交易授权。

## 20. Exit Criteria

GateT-FREEZE closeout 必须满足：

- GateT-1 到 GateT-6 的已授权批次均有实现或明确 not-started / deferred 记录。
- 后端、前端、Python、DB、docs validation 证据按实际修改范围完成，且不能把未运行验证写成通过。
- No-side-effect scheduler 若仍仅规划，必须明确未启动；若实现 readiness review，也必须证明不外联、不创建订单、不调用 private endpoint。
- Review-only operator workflow 若实现，必须仅写本地 review metadata，并有 audit / rollback / sensitive-field guard。
- GateT current docs、API / DB facts、TESTING、WORKLOG、FACT_SOURCE_INDEX 已同步。
- LIVE、AI、DH runtime、Integration-1、RealClient、real provider、private trading adapter、real permission probe、Shadow trading、Python ML readiness、Python live execution readiness 均未越界。
- Freeze closeout 前必须有干净 forbidden-area diff、staged scope check、tag absence check和 release tag 计划。

## 21. Next Concrete Action

下一步建议单独启动：

`NQ-GATET-1-SHADOW-VALIDATION-WORKFLOW-READ-MODEL-WO`

建议范围：

- 只做 work order / implementation plan 或最小 backend read model 设计。
- 优先定义 operator item、review state、evidence anchor、freshness、severity、blockers、warnings、nextSteps 的 DTO 语义。
- 默认不新增 DB migration；如要 durable review / acknowledge，先拆出 DB schema review。
- 不改前端、不改 Python、不改 CI、不启动 scheduler、不接 AI/DH、不触碰真实交易。
