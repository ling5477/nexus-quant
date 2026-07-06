# GateQ Plan: Shadow Live Readiness

> Archive pointer: GateQ release tag and historical archive 已完成。当前 GateQ frozen/tagged 摘要见 [README.md](README.md)、[STATUS.md](STATUS.md) 与 [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md)；完整归档入口见 [../gates/gate-q/README.md](../gates/gate-q/README.md)。本文作为 GateQ-0 planning baseline 历史证据保留，已由 GateQ-1..6、freeze closeout 与 GateQ archive 消费，不再作为 GateQ current authority 入口。

任务名称：`NQ-GATEQ-PLAN-SHADOW-LIVE-READINESS`

日期：2026-07-05

最终状态：`NQ-GATEQ-PLAN-SHADOW-LIVE-READINESS：PLAN READY / NOT IMPLEMENTED`。含义：`PLAN READY`（规划已就绪）、`NOT IMPLEMENTED`（未实现）。

本文是 GateQ-0 planning-only 基线。它只规划策略有效性验证与 Paper / Shadow Live 一致性阶段，不启动 GateQ-1 implementation，不新增 API、migration、页面、测试、CI workflow 或业务代码。

## 1. GateQ Current Baseline

当前事实：

- GateO：`FROZEN`（已冻结）/ `ACCEPTED`（已接受），归档入口为 `docs/gates/gate-o/README.md`。
- GateP：`FROZEN`（已冻结）/ `ACCEPTED`（已接受）/ `TAGGED`（已打 tag），release tag 为 `nq-gatep-freeze`。
- GateP tagged commit：`3650714ae9cd441e59eb5b09c605a14bbc9998dc`。
- GateP archive：`docs/gates/gate-p/README.md`。
- 最新 NQ CI Baseline：`PASS`（通过），GitHub Actions run `28714258374`，headSha 与 GateP tagged commit 一致。
- GateQ：`PLAN / NOT STARTED`（仅规划 / 未开始）；本轮推进为 `PLAN READY / NOT IMPLEMENTED`，仍未实现。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED`（未开始）/ mock-test-support only where applicable。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。

只读检查到的现有能力基础：

- Java 侧已有 strategy version、backtest run、evaluation report、publish record、Paper run 的追溯快照链。
- `BacktestPublishService` 已要求 backtest run 与 evaluation report 成功后才发布，并固化 strategy version snapshot 与 evaluation summary。
- `PaperTradingRunService` 创建 Paper run 时已固化 publish、strategy version、dataset、param、config 快照。
- Paper 侧已有 `/api/paper-trading/strategy-evaluations` 与 `/api/paper-trading/auto-reviews` 只读聚合，声明 Paper-only、rules-based、no LIVE、no AI、no external call。
- MarketData / adapter / trading preflight / runtime readiness API 当前均是只读诊断或 fail-closed readiness，不构成 trading authorization。
- Python Research 已有 dataset manifest、experiment metadata、evaluation skeleton、run summary 与 CLI，但保持 offline/no-network/no-credential/no-Java-runtime 边界。

## 2. GateP Freeze Evidence Summary

GateP freeze 接受范围：

- Batch 1：current fact-source and status closeout。
- Batch 2：Market Data Data Quality Center 后端只读切片。
- Batch 3：前端 Data Quality Center 与 Runtime release matrix。
- Batch 4：单交易所账户权限与风险前置只读基线。
- Batch 5：Python reproducible offline experiment foundation。
- Batch 6：freeze readiness review。
- Batch 6A：current fact-source drift fix。

GateP freeze 不代表：

- 不代表 LIVE trading authorization。
- 不代表真实交易所 private endpoint 接入。
- 不代表 RealClient、real provider、private trading adapter 或 real permission probe 已实现。
- 不代表 Data Quality diagnostic、Permission Readiness、Risk Preflight 已授权交易。
- 不代表 Python Research 已 ML ready 或 live execution ready。
- 不代表 AI runtime 或 DH runtime 已接入。

GateQ 与 GateP 的边界：

- GateP 解决“数据质量、权限可观测、风险前置和 Python offline foundation 是否具备只读诊断基础”。
- GateQ 解决“策略有效性验证、发布前 gate、Paper 与 Shadow Live 只读对照、Python artifact 与 Java fact-source 绑定如何形成可复盘证据链”。
- GateQ 不继承 GateP 的 readiness 作为交易授权；GateQ 的所有 Shadow Live 设计仍必须 no order、no cancel、no credential、no private endpoint、no LIVE。

## 3. GateQ Objective

GateQ 目标是把 NQ 从“可运行、可诊断”推进到“可验证、可复盘、可比较”：

- 建立 strategy version、dataset version、evaluation report、publish、paper run、shadow run 的统一追溯链。
- 建立 Paper vs Shadow Live 只读对照模型。
- 建立策略发布前 evaluation gate，避免未经评估或证据不足的策略进入 Paper / Shadow 对照链。
- 建立 Python offline evaluation artifact 与 Java fact-source 的绑定合同。
- 建立 Shadow Live 不触单、不写真实交易、不访问 private endpoint、不读取 credential material 的安全边界。
- 明确前端只做 Shadow / Paper 对照与策略有效性运营页，不做 AI 决策中心，不做实盘交易台。

## 4. GateQ Non-goals

GateQ 不做：

- 不启用 LIVE trading。
- 不实现真实下单、撤单、转账、提现。
- 不实现 private trading adapter。
- 不实现 RealClient 或 real provider。
- 不实现真实 permission probe。
- 不读取 credential material、API key、secret、passphrase、token、cookie 或 private key。
- 不接 AI runtime，不做 AI 自动交易或 AI recommendation execution。
- 不接 DH runtime，不允许 DH 写 NQ。
- 不做多交易所扩张、主网 agent wallet、高频交易或真实资金路径。
- 不把 Paper run 写成 live execution。
- 不把 Python evaluation 写成 ML ready 或 live execution ready。
- 不改历史 migration，不新增无注释 schema。

## 5. Shadow Live Definition

Shadow Live 是只读影子运行模式。

它可以在未来受控批次中消费真实或受控 public marketdata 快照，运行策略决策、risk preflight、order intent preview、paper-equivalent simulation，并输出可审计的 shadow decision trace。

它不得：

- 提交真实订单。
- 调用 private endpoint。
- 读取 credential material。
- 修改真实账户、资金、订单、ledger 或 exchange state。
- 把 order intent preview 发送给 adapter、gateway 或交易所。
- 把 public marketdata readiness、permission readiness 或 risk preflight 解释成 trading authorization。

本轮 GateQ-0 不允许任何真实交易所外联。后续 GateQ 若需要 Shadow Live 使用真实 public marketdata，只能消费已存在的本地 DB facts、deterministic fixture、redacted public readonly evidence，或另起受控 public-readonly 授权批次；仍不得访问 private/signed endpoint。

## 6. Paper vs Shadow Boundary

Paper Trading 与 Shadow Live 的差异：

| 维度 | Paper Trading | Shadow Live |
| --- | --- | --- |
| 目标 | 模拟盘运行、Paper orders/trades/positions/equity/replay 事实 | 使用 live-like public marketdata snapshot 做只读影子决策与对照 |
| 数据输入 | publish snapshot、dataset snapshot、Paper run 本地事实 | public marketdata snapshot、strategy snapshot、risk preflight snapshot、shadow input snapshot |
| 状态写入 | 写 Paper run、Paper order/trade/position、monitor/review 等本地模拟事实 | 只允许写 shadow run / decision trace / risk snapshot / order intent preview 等影子事实；不得写真实账户、订单、ledger |
| 交易执行 | Paper-only simulation，不触达真实交易所 | no-side-effect；不得提交真实订单或撤单 |
| 外联 | 当前 Paper 不应访问真实交易所执行路径 | GateQ-0 不外联；未来只允许受控 public readonly marketdata，禁止 private/signed |
| credential | 不读取 credential material | 不读取 credential material |
| 业务含义 | 模拟运行结果 | 只读对照和验证证据，不代表实盘 readiness |

## 7. Strategy Evaluation Gate Plan

策略发布前 evaluation gate 候选规则：

- strategy version 必须存在且状态允许进入验证，例如 `ACTIVE` 或未来明确的 `VALIDATION_READY`。
- dataset version / dataset manifest 必须可追溯，包含 checksum、source、exchange、market_type、symbol、interval、start/end time、row_count、quality_status。
- evaluation report 必须存在且状态为成功；缺失、失败、样本不足、数据质量不足时 fail-closed。
- publish 必须绑定 evaluation report 与 strategy version snapshot，且不能在无 evaluation evidence 时直接进入 Shadow Live。
- Paper run 与 shadow run 必须绑定同一 strategy version、dataset version、evaluation report、publish record，才能进入可比较集合。
- evaluation gate 输出只能是 `ALLOW_PAPER_ONLY`、`ALLOW_SHADOW_COMPARE_ONLY`、`BLOCKED_REQUIRES_EVIDENCE`、`BLOCKED_DATA_QUALITY`、`BLOCKED_RISK_PREFLIGHT` 等诊断状态，不得输出 `LIVE_READY` 或 `TRADING_AUTHORIZED`。

GateQ-1 只允许实现只读 baseline，不能新增真实交易能力。

## 8. Traceability Model

候选追溯链：

```text
strategy_version_id
  -> dataset_version_id / dataset_id / dataset_checksum
  -> experiment_id / python_artifact_id
  -> evaluation_report_id / eval_report_id
  -> publish_record_id
  -> paper_run_id
  -> shadow_run_id
  -> comparison_report_id
```

候选核心字段：

- `strategyVersionId`：Java strategy version 当前事实。
- `datasetId` / `datasetManifestId` / `datasetChecksum`：Java dataset 或 Python manifest 绑定点。
- `experimentId`：Python offline experiment metadata 的稳定 ID。
- `evaluationReportId`：Java backtest evaluation 或导入后的 offline evaluation artifact 引用。
- `publishId`：发布记录 ID。
- `paperRunId`：Paper run ID。
- `shadowRunId`：Shadow run ID，仅代表只读影子运行。
- `shadowInputSnapshotId`：市场数据输入快照。
- `shadowDecisionTraceId`：策略决策 trace。
- `shadowRiskSnapshotId`：risk preflight / blocker snapshot。
- `orderIntentPreviewId`：只读 order intent preview，绝不执行。
- `comparisonReportId`：Paper vs Shadow 对照报告。

绑定原则：

- 历史 snapshot append-only，不重写已完成 publish、paper run 或 shadow run。
- 每条链必须能说明数据来源、策略版本、参数哈希、evaluation 版本、运行时间窗口和生成命令。
- 任一环缺失时，前端必须显示 `INCOMPLETE_TRACE` 或 `PENDING_ARTIFACT_BINDING`，不得显示通过。

## 9. Python Evaluation Artifact Boundary

Python offline evaluation 进入 Java fact-source 的候选合同：

- Python 只能生成 artifact，不直接写 Java DB、Java runtime、Paper run、Shadow run 或 trading state。
- artifact 包建议包含 `dataset_manifest.json`、`experiment_metadata.json`、`evaluation_metrics.json`、`research_run_summary.json`、`artifact_manifest.json` 和 SHA-256 checksum。
- Java 侧未来只能通过受控 import / binding service 读取 artifact metadata，校验 schema version、checksum、run_mode=`OFFLINE`、offline boundary notes 与敏感字段禁入。
- artifact 绑定后的 Java fact-source 状态建议为 `IMPORTED_OFFLINE_ARTIFACT`、`BOUND_TO_STRATEGY_VERSION`、`REJECTED_SCHEMA`、`REJECTED_CHECKSUM`、`REJECTED_BOUNDARY`。
- `NOT_AVAILABLE` 指标必须保留原义，不能填 0；缺交易明细时不得伪造 win rate、turnover、exposure、profit factor。
- Python artifact 不代表 ML ready、live execution ready、AI started 或 Java runtime bridge。

## 10. Backend Candidate DTO / API Plan

本节只列候选，不代表已实现 API。`docs/current/API.md` 仍只记录当前真实 API。

最小 DTO 候选：

- `GateQTraceabilityChainResponse`：strategy / dataset / evaluation / publish / paper / shadow 统一链。
- `StrategyEvaluationGateResponse`：evaluation gate 诊断状态、blockers、warnings、requiredNextSteps。
- `PaperShadowComparisonResponse`：同一 publish 下 Paper vs Shadow 的收益、回撤、风险、执行意图、数据窗口差异。
- `ShadowRunResponse`：shadow run metadata、input snapshot scope、status、generatedAt。
- `ShadowDecisionTraceResponse`：strategy decision、risk decision、order intent preview、no-side-effect proof。
- `PythonEvaluationArtifactBindingResponse`：artifact metadata、checksum、schema version、binding status。

最小 API 候选：

- `GET /api/gateq/traceability/chains`：只读查询 traceability chain。
- `GET /api/gateq/evaluation-gates`：只读查询 strategy evaluation gate 结果。
- `GET /api/gateq/paper-shadow/comparisons`：只读查询 Paper vs Shadow comparison。
- `GET /api/gateq/shadow-runs` / `GET /api/gateq/shadow-runs/{shadowRunId}`：只读查询 shadow run。
- `GET /api/gateq/shadow-runs/{shadowRunId}/decision-traces`：只读查询 shadow decision trace。
- `GET /api/gateq/python-artifacts/{artifactId}/binding`：只读查询 Python artifact 绑定状态。

未来若 GateQ-3 需要生成 shadow run skeleton，必须另起实现任务并明确 no-side-effect write boundary；任何 `POST` 都只能创建本地 shadow artifact，不得触达 adapter、private endpoint、credential、order gateway 或 ledger。

## 11. Frontend Candidate Pages

前端最小页面候选：

- `/gateq/strategy-validation`：策略有效性运营页，展示 strategy version、dataset、evaluation report、publish 状态和 evaluation gate blocker。
- `/gateq/paper-shadow`：Paper vs Shadow 对照页，展示同一 publish / strategy version / dataset 下 Paper run 与 shadow run 的差异。
- `/gateq/shadow-runs`：Shadow run 只读列表与详情，展示 input snapshot、decision trace、risk snapshot、order intent preview 和 no-side-effect 状态。
- `/gateq/traceability` 或在既有 `/strategies`、`/evaluations`、`/publishes`、`/paper-trading/reviews` 中增强生命周期 trace：展示 strategy version -> dataset -> evaluation -> publish -> paper -> shadow 的链路。

前端边界：

- 使用既有 React + TypeScript + Ant Design + TanStack Query 模式。
- 不做营销页，不做 AI 决策中心，不做实盘交易台。
- 所有 Shadow / Paper 对照必须显示 no-LIVE、no-order、no-credential、no-private-endpoint 边界。
- 缺数据时显示 `PENDING_BACKEND_SUPPORT`、`INCOMPLETE_TRACE`、`NO_SHADOW_RUN`、`NO_COMPARABLE_PAPER_RUN`，不得伪造成通过。

## 12. Test Strategy

GateQ-0 docs-only 验证：

- `git status --short`
- `git diff --check`
- `git diff --stat`
- forbidden-scope diff：`backend` / `frontend` / `research` / `scripts` / `deploy` / `.github` / `backend/**/db/migration`
- 指定关键词 `rg` 检查 GateP/GateQ/Shadow Live/Paper/strategy/evaluation/LIVE/AI/DH/credential/order 等边界语境。

未来 GateQ 实现测试策略：

- 后端 unit tests：evaluation gate、traceability assembler、artifact binding validator、shadow no-side-effect runner。
- Controller tests：候选 read-only API 不返回 `tradingReady`、`liveReady`、`authorizedForTrading`、credential material 或 private endpoint 字段。
- Repository tests：只读查询与 append-only shadow artifact 写入边界；如需 schema，migration 必须单独 review。
- No-side-effect tests：shadow run 不触达 adapter delegate、不调用 private endpoint、不写 order/ledger/account。
- No-credential tests：artifact、trace、log、DTO 均不得包含 credential material。
- Frontend build / Playwright：Paper vs Shadow 页面、empty/error/loading、blocked/risky 状态、forbidden wording。
- Python tests：artifact schema、checksum、`run_mode=OFFLINE`、`NOT_AVAILABLE` 指标保真、no-network/no-credential。

## 13. Security / Credential / LIVE Boundary

GateQ 安全边界：

- Shadow Live 禁止真实下单、撤单、转账、提现。
- Shadow Live 禁止 private endpoint、signed request、credential material access。
- Shadow Live 禁止读取真实账户余额、真实订单、真实持仓或 ledger。
- Shadow Live 只能生成 order intent preview；preview 必须有 `NOT_EXECUTED` / `NO_SIDE_EFFECT` 标记。
- 所有日志、DTO、artifact、trace 必须脱敏，不得包含 token、secret、passphrase、cookie、private key、raw signed request、raw private response。
- LIVE 继续 `DISABLED`；任何 UI 文案不得出现“LIVE 已就绪”、`trading authorized` 或类似放行语义。

## 14. AI / DH Runtime Boundary

GateQ 不启动 AI 或 DH runtime。

- AI：`NOT STARTED`，不允许 AI signal、AI recommendation execution、AI Paper Trading、AI order intent execution。
- DH runtime：`NOT INTEGRATED`，不允许 DH 写 NQ，不允许 DH 启动 Paper Run、Shadow Run、order intent execution 或任何交易状态变更。
- Integration-1：仍为 `NOT STARTED / mock-test-support only where applicable`，不能借 GateQ 接 runtime。
- GateQ 页面可以展示 rules-based evaluation / comparison，但不得写成 AI decision center。

## 15. P0 / P1 / P2 / P3 Risk List

### P0

- 把 Shadow Live 写成真实交易或真实交易授权。
- Shadow runner 触达 private endpoint、adapter trading method、order gateway、ledger 或 credential material。
- 将 AI / DH output 映射为 order intent execution。

### P1

- Paper vs Shadow comparison 未绑定同一 strategy version / dataset / evaluation / publish，导致不可比较。
- Python artifact 缺 checksum/schema/run_mode 校验就进入 Java fact-source。
- Evaluation gate 在缺数据、失败评估或样本不足时没有 fail-closed。
- DTO/UI 返回 `liveReady`、`tradingReady`、`authorizedForTrading` 等误导字段。

### P2

- Shadow input snapshot 未记录 public marketdata source、时间窗口、freshness/gap/error 状态。
- `NOT_AVAILABLE` 指标被填 0，导致评估质量误判。
- 前端空态 / 错误态把 no-data 误显示为通过。
- Future migration 规划未提前定义 COMMENT / JSONB sensitive-field boundary。

### P3

- 页面信息架构可能与既有 `/paper-trading/reviews`、`/evaluations`、`/strategies` 入口重复。
- GateQ batch 若拆分过细，可能造成 docs churn；中间批次应以代码和测试证据为主。
- 既有 Vite large chunk、Ant Design React 19 warning 仍是历史前端质量债，不阻断 GateQ planning。

## 16. GateQ Batch Plan

### GateQ-0：Plan / fact-source reconciliation

- 状态：本轮 `PLAN READY / NOT IMPLEMENTED`。
- 目标：冻结 planning-only 入口，明确 Shadow Live 定义、Paper vs Shadow 边界、traceability model、candidate API/pages/tests。
- 允许：只改 current docs。
- 禁止：任何实现、API、migration、页面、测试、CI、真实外联、credential。

### GateQ-1：Strategy Evaluation Gate 只读 baseline

- 目标：建立 strategy publish / Paper / Shadow 入场前 evaluation gate 只读判定。
- 候选输出：evaluation gate read model、blockers、warnings、requiredNextSteps。
- 验收：缺 evaluation、失败 evaluation、数据质量不足、样本不足时 fail-closed；不返回交易授权字段。

### GateQ-2：Paper vs Shadow Run 数据模型与只读 DTO 设计

- 目标：定义 shadow run、input snapshot、decision trace、risk snapshot、order intent preview、comparison report 的模型与 DTO。
- 注意：如涉及 schema，只能单独 migration plan/review，不得修改历史 migration。

### GateQ-3：Shadow Live no-side-effect runner skeleton

- 目标：未来最小 shadow runner skeleton，只能消费 public/local/fixture marketdata snapshot，生成本地 shadow artifact。
- 强制：不调用 adapter trading delegate、不访问 private endpoint、不读取 credential、不写真实 account/order/ledger。

### GateQ-4：Python evaluation artifact -> Java binding contract

- 目标：定义并实现 Python artifact import/binding 合同。
- 强制：Python 只产 artifact；Java 只做校验与绑定，不执行 Python runtime，不把 artifact 写成 ML/live ready。

### GateQ-5：Frontend Paper vs Shadow 对照页

- 目标：实现只读对照页，展示 comparable status、delta、blockers、traceability。
- 强制：no-LIVE/no-order/no-credential/no-private endpoint 文案必须首屏可见。

### GateQ-6：Strategy lifecycle trace 页面增强

- 目标：在策略生命周期中串起 strategy version -> dataset -> evaluation -> publish -> paper -> shadow。
- 强制：缺环节显示 incomplete，不伪造通过。

### GateQ-FREEZE：GateQ freeze closeout

- 目标：冻结 GateQ 只读验证与对照基线。
- 强制：只有 Q1-Q6 实现、测试、边界验证全部接受后才能进入；不得冻结 LIVE 或 real provider。

## 17. Validation Commands

本轮 GateQ-0 必须运行：

```powershell
git status --short
git diff --check
git diff --stat
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- .github
git diff -- backend/**/db/migration
rg "GateP|GateQ|Shadow Live|shadow|Paper|strategy version|dataset version|evaluation|publish|paper run|shadow run|LIVE|AI|DH|Integration-1|RealClient|real provider|private trading|permission probe|credential|order|cancel|withdraw|transfer|trading authorization|ML ready|live execution" README.md docs/current docs/gates backend frontend research/py
```

提交前检查：

```powershell
git status --short
git diff --cached --name-only
git diff --cached --stat
git diff --cached --check
```

本轮不运行 Maven / frontend build / Playwright / Python pytest/mypy/ruff，原因是 GateQ-0 为 docs-only planning，未修改 Java / TypeScript / Python / migration / workflow / runtime 配置。

## 18. Acceptance Criteria

GateQ-0 acceptance：

- `docs/current/GATEQ_PLAN.md` 已新增，文档正文中文为主。
- 明确 GateQ 目标、范围、非目标。
- 明确 GateQ 与 GateP 的边界。
- 明确 Shadow Live 的准确定义。
- 明确 Shadow Live 与 Paper Trading 的差异。
- 明确 Shadow Live 不允许真实下单、private endpoint、credential material、真实账户读取或真实状态变更。
- 明确 strategy version / dataset version / evaluation report / publish / paper run / shadow run 绑定策略。
- 明确 Python offline evaluation artifact 进入 Java fact-source 的合同边界。
- 明确后端 candidate DTO/API、前端 candidate pages、测试策略、批次计划和 freeze 验收标准。
- 指定 forbidden-scope diff 全为空。
- staged 文件只包含允许清单。

## 19. Exit Criteria

GateQ-FREEZE exit criteria 候选：

- Q1-Q6 均完成并有真实测试证据。
- Evaluation gate 对缺失/失败/样本不足/数据质量不足全部 fail-closed。
- Paper vs Shadow comparison 只比较同一 strategy version / dataset / evaluation / publish 链。
- Shadow runner skeleton 通过 no-side-effect / no-credential / no-private / no-order / no-ledger 验证。
- Python artifact binding 通过 checksum、schema、run_mode、boundary notes 和 sensitive-field validation。
- Frontend 只读页面 build 与 Playwright smoke 通过，并显式展示风险/空态/错误态。
- `API.md` 与 `DB_SCHEMA.md` 仅记录已实现事实，不把候选接口或未来 schema 写成当前能力。
- LIVE 仍 `DISABLED`；AI 仍 `NOT STARTED`；DH runtime 仍 `NOT INTEGRATED`；RealClient / real provider / private trading / real permission probe 仍 `NOT IMPLEMENTED`。

## 20. Next Concrete Action

下一步只能另起：

`NQ-GATEQ-1-STRATEGY-EVALUATION-GATE-READONLY-BASELINE / NOT STARTED`

进入 GateQ-1 前必须重新声明 allowed files、forbidden areas、validation commands、no-LIVE / no-AI / no-DH-runtime / no-real-provider / no-private-trading / no-credential 边界，并先做 implementation plan review。当前不允许启动 GateQ implementation。
