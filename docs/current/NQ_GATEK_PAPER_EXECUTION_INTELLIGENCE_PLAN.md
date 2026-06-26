# NQ GateK Paper Execution Intelligence Plan

> 任务：NQ-GATEK-PLAN-PAPER-PORTFOLIO-TO-EXECUTION-INTELLIGENCE  
> 日期：2026-06-26  
> 等级：M 级规划文档（planning only；不改 backend/frontend/migration/endpoint）  
> 状态：**PLAN / PENDING IMPLEMENTATION**

---

## 1. 当前完成状态（事实基线）

### 1.1 Paper Run 详情层（已完成）

- `PaperRunSummaryResponse` aggregate：counts / latest / resultReview / diagnoses / timeline / safety。
- 账户资产与收益率展示（equity curve snapshot、realized/unrealized PnL）。
- 事件时间线：SIGNAL → RISK_CHECK → ORDER_SUBMIT → FILL → STOP 等节点。
- 复盘卡：finalStatus / conclusion / conclusionLevel / runtimeDurationText。
- 异常诊断（diagnoses[]）：BLOCKING / WARNING / INFO 三级。
- Backtest → Paper 对照（strategyVersionSnapshot / backtestSource linkage）。
- Strategy → Publish → Paper run 链路全可追溯。
- 异常停机处理（EmergencyStop、RecoveryEvent、Alert）。

### 1.2 Paper Portfolio 组合层（已完成）

- `PaperPortfolioSummaryResponse` endpoint：overview / strategyGroups / publishGroups / highlights / dataQuality / safety / portfolioCurve。
- Batch read 优化（单次聚合，不循环 N+1 查询）。
- Group risk counts（riskBlockedCount / openAlertCount）。
- Order/fill split counts（noOrderRunCount / orderNoFillRunCount / filledRunCount）。
- Portfolio equity curve（PaperPortfolioCurvePoint[]，按时间序列展示组合总资产）。
- Portfolio drawdown curve（drawdown 字段，组合回撤时序）。
- 最大 run 回撤、胜率相关字段（returnEligibleRunCount）。

### 1.3 前端产品化层（已完成）

- Paper 组合看板（PaperPortfolioDashboard）：总资产、总收益率、PnL、状态分布、异常计数。
- Paper 风险与回撤驾驶舱（PaperRiskDrawdownDashboard）：7 个可点击指标卡 + 风险 Run 清单 + 回撤分析 + 风控/异常清单 + 无交易清单 + 数据质量清单。
- Paper 策略表现排行（PaperStrategyRankingDashboard）：Strategy Version / Publish 两张排行表 + 4 个可点击概览卡。
- 排序维度（风险调整分 / 收益率 / 胜率 / 无订单 / 回撤最大）。
- 过滤控件（全部 / 仅有收益率 / 仅数据不足 / 仅有风控拦截 / 仅无订单 / 仅有单无成交 / 仅异常终态）。
- 指标卡 click-to-filter：7 个风险卡 + 4 个排行卡 → 联动下方列表/排行表。
- ClickableMetricCard active state（aria-pressed + outline，Loop-21 UX 收口）。
- Paper-only 文案收口（保留驾驶舱级 NqRiskBanner，去除重复长句）。

### 1.4 后端事实源（已知完成）

- `paper_run`、`paper_order`、`paper_trade`、`paper_position`、`paper_equity_snapshot`：主要执行事实。
- `paper_risk_result`：风控检查结果（checkType / status / severity / message）。
- `paper_run_alert`：运行时异常告警。
- `paper_run_daily_report`：日维度汇总报告（orderCount / tradeCount / alertCount / riskRejectCount / dailyReturn / maxDrawdown）。
- `paper_run_heartbeat`：心跳记录（lagSeconds / lastOrderTime / lastTradeTime）。
- `paper_emergency_stop` / `paper_recovery_event`：停机与恢复记录。
- `backtest_result`：回测结果（用于 Backtest→Paper 对照）。
- `strategy_version` / `publish`：策略版本与发布记录（链路可追溯）。

### 1.5 已知差距（GateK 规划依据）

**展示层差距（前端可见）：**
- 页面只能展示"发生了什么"（数量、状态、曲线），无法解释"为什么发生"（无订单原因？成交但亏损原因？风控来源？）。
- 无执行归因分类（策略未触发 / 价格未满足 / 撮合未成交 / 风控拒绝 / 数据缺失 → 目前混合在 highlights/diagnoses 中）。
- 无 Paper vs Backtest 偏差量化（回测胜率 5%，Paper 胜率 0%，原因未呈现）。
- 无策略粒度稳定性评分（多 run 聚合后总体风险调整分 + 稳定性指数）。
- 无组合级每日复盘（跨 run 的日汇总摘要，现在只有单 run 日报）。

**后端聚合差距：**
- `paper_risk_result` 有详细 checkType/message，但前端只通过 riskBlockedRunCount 消费，无法穿透到"第几个 bar 的哪条规则触发了风控"。
- `paper_run_daily_report` 有 riskRejectCount，但无对应 daily 归因摘要（为什么那天有拒绝？哪类规则？）。
- `paper_order` 有 reason（风控拒绝原因文本），但 portfolio 层没有聚合提取 reason 类型分布。
- 无 noOrder 执行原因字段（是"从未产生信号"还是"信号产生但被风控前置拒绝在 SIGNAL 阶段"？）。
- 无 Paper vs Backtest 偏差聚合（需跨 `paper_run` + `backtest_result` 联合计算）。
- 策略评分（score）目前已有简化 score 字段，但无稳定性指数 / 样本不足惩罚 / 多维度评分分解。

---

## 2. GateK Paper 产品线核心目标

> **从"组合看板"升级到"执行智能诊断"**  
> **从"展示结果"升级到"解释原因"**  
> **从"单 run 复盘"升级到"组合/策略/发布维度复盘"**

### 2.1 Execution Intelligence 三层能力目标

```
Layer 1: 结果展示（已完成，GateJ）
  - 有没有成交？多少 PnL？有没有风控？最大回撤？
  
Layer 2: 执行诊断（GateK 目标）
  - 为什么无订单？为什么有订单但无成交？
  - 风控拒绝是哪类规则触发？
  - 回撤主要来自哪个 run / 哪个时间段？
  - 数据不足的根本原因？
  
Layer 3: 策略评估与复盘（GateK 目标）
  - Paper 执行与 Backtest 的偏差（规律性 or 随机？）
  - 策略多维评分（收益/稳定性/风险/样本充分度）
  - 组合级每日自动复盘摘要（规则化，不接 AI）
```

### 2.2 非目标（本阶段严格禁止）

- 不启用 LIVE，不做真实下单。
- 不接 AI/DH runtime（规则化复盘，不是 AI 生成）。
- 不访问真实交易所 API。
- 不读取凭证。
- 不新增无注释表或字段（所有 migration 须另行审批）。
- 不做真实投资建议（score 仅为 Paper 内部模拟排序分）。
- 不把 Paper 结果写成真实收益率评级。

---

## 3. GateK 分批计划

### Batch K1：Execution Diagnostics Backend

**目标**：新增只读诊断聚合 endpoint，为前端提供"执行归因"能力，不新增 migration，优先基于现有事实源。

**范围**：
- 新增 `GET /api/paper-portfolio/execution-diagnostics`（只读，认证后访问，不涉及写操作）。
- 输入：accountId / 时间范围（可选）。
- 输出：每个 paper run 的执行归因（noOrder / orderNoFill / filledLoss / riskBlocked / dataInsufficient 分类 + 原因摘要）。
- 归因依据：现有 `paper_order.reason`、`paper_risk_result.checkType + message`、`paper_equity_snapshot`、`paper_run_daily_report.riskRejectCount`。
- 禁止：不新增 migration；不修改现有表结构；不新增无注释字段。

**不做什么**：
- 不实现策略评分。
- 不实现 Paper vs Backtest 偏差计算。
- 不做前端视图。

**可能修改文件**：
```
backend/nq-core/src/main/java/.../paper/
  diagnostics/PaperRunExecutionDiagnostics.java（新增 value object）
  diagnostics/PaperExecutionDiagnosticsAssembler.java（新增 assembler）
  diagnostics/NoOrderCause.java（枚举：NEVER_SIGNALLED / SIGNAL_RISK_REJECTED / ...）
  diagnostics/OrderNoFillCause.java（枚举：PRICE_NOT_FILLED / RISK_REJECTED_POST_ORDER / ...）
  diagnostics/FilledLossCause.java（枚举：DRAWDOWN_EXCEEDED / ADVERSE_PRICE / ...）
  diagnostics/RiskBlockedCause.java（枚举：来自 paper_risk_result.checkType）
  diagnostics/DataInsufficientCause.java

backend/nq-infra/src/main/java/.../paper/
  PaperRunDiagnosticsRepository.java（JDBC 读取 risk_result / order reason / equity）

backend/nq-api/src/main/java/.../paper/
  diagnostics/PaperExecutionDiagnosticsController.java
  diagnostics/dto/ExecutionDiagnosticsResponse.java
  diagnostics/dto/RunDiagnosticsItem.java
```

**后端设计要点**：
- `NoOrderCause` 归因逻辑：
  - `NEVER_SIGNALLED`：run 内 `paper_order` 为 0，且 `paper_risk_result` 中无 SIGNAL 阶段拒绝记录。
  - `SIGNAL_RISK_REJECTED`：`paper_risk_result.checkType = PRE_SIGNAL` 且 `status = BLOCKED`，`paper_order = 0`。
  - `DATA_INSUFFICIENT`：位于 `dataQuality.dataInsufficientRuns`。
  - `UNKNOWN`：兜底，数据不足以判定。
- `OrderNoFillCause` 归因逻辑：
  - `PRICE_NOT_FILLED`：`paper_order` 状态为 OPEN/PENDING，无对应 `paper_trade`，price 条件未满足。
  - `ORDER_RISK_REJECTED`：`paper_risk_result.checkType = PRE_ORDER` 且 BLOCKED，order 为 REJECTED。
  - `CANCELLED_BEFORE_FILL`：order status = CANCELLED，无对应 trade。
- `RiskBlockedCause`：直接从 `paper_risk_result.checkType`（聚合各类型 count）提取，分类为 PRE_SIGNAL / PRE_ORDER / POST_ORDER。
- `FilledLossCause`：有成交（`paper_trade` > 0）但 `totalReturn < 0`，归因看 maxDrawdown 与 riskRejectCount 的相对大小。

**测试要求**：
- `PaperRunExecutionDiagnosticsAssemblerTest`：各归因路径 unit test（noOrder × 3 类 / orderNoFill × 3 类 / riskBlocked / dataInsufficient）。
- `PaperRunDiagnosticsRepositoryBatchTest`：JDBC 批量读取测试（不 N+1 查询）。
- `ExecutionDiagnosticsControllerTest`：MockMvc，认证 + 无认证 401，response 结构验证，无 secret 泄漏。
- 旧后端兼容测试：`paper_risk_result` 空时不崩，返回 `cause = UNKNOWN`。

**验收标准**：
- `GET /api/paper-portfolio/execution-diagnostics` 返回 200，结构含每个 run 的 cause 分类。
- `mvn -f backend/pom.xml test` 全绿，新增测试 ≥ 12 个。
- JDBC 批量查询，无循环单条查 risk_result。
- response 不含 secret / apiKey / credential / rawPayload。
- 旧后端（无 paper_risk_result 数据）返回 cause = UNKNOWN，不崩溃。

**风险点**：
- `paper_order.reason` 字段语义不统一（不同策略填法可能不同），归因准确性有限，需在响应中注明 `causeConfidence: HIGH/LOW`。
- `paper_risk_result` 表数据完整性依赖策略实现，部分策略可能未写 risk_result 记录。
- K1 endpoint 如不加 pagination 可能对大账户造成慢查询风险，需强制 limit。

**推荐 commit message**：
```
feat(paper-trading): add execution diagnostics backend assembler and endpoint
```

---

### Batch K2：Execution Diagnostics UI

**目标**：前端新增执行诊断视图，与风险 Run 清单联动，支持 run / strategy / publish 维度查看。

**范围**：
- 在 Paper 风险与回撤驾驶舱（PaperRiskDrawdownDashboard）下方新增"执行诊断"子区域。
- 复用 K1 endpoint 数据。
- 每个风险 Run 展示 cause 分类标签（NqStatusTag）。
- 支持按 cause 类型筛选（扩展 RiskRunFilter）。
- Strategy / Publish 维度聚合：各 cause 类型的 run 数 + 代表性 run 列表。

**不做什么**：
- 不新增后端接口（仅消费 K1 endpoint）。
- 不新增 migration。
- 不实现策略评分 UI。

**可能修改文件**：
```
frontend/src/pages/paper-trading/PaperTradingPage.tsx
  → 新增 PaperExecutionDiagnosticsCard 子组件
  → 扩展 RiskRunFilter type（加 'noOrderNeverSignalled' | 'signalRiskRejected' | ...）

frontend/src/api/paper-portfolio.ts
  → 新增 getExecutionDiagnostics() 函数

frontend/src/hooks/usePaperExecutionDiagnosticsQuery.ts
  → TanStack Query hook

frontend/tests/e2e/paper-trading-product-loop-smoke.spec.ts
  → 新增 Loop-22 / K2 E2E 测试
```

**前端设计要点**：
- 每个 Run 的执行诊断 badge 展示在风险 Run 清单表格中（新增"执行诊断"列）。
- 诊断区域以折叠卡片（Collapse）形式展示（默认收起，不增加认知负担）。
- cause 筛选按钮复用 ClickableMetricCard 模式。
- 旧后端（K1 未部署）时 graceful fallback：只显示"诊断数据暂不可用"的 muted 状态，不崩溃。

**测试要求**：
- E2E：诊断 badge 展示、按 cause 筛选、空态文案、旧后端 fallback。
- `npm run build` BUILD SUCCESS。

**验收标准**：
- 风险 Run 清单每行有执行诊断 badge。
- 按 cause 筛选正确过滤行。
- 旧后端（无诊断 endpoint）时不崩，显示 fallback。
- 全套 E2E 通过。

**风险点**：
- cause 分类较多，UI 空间有限，需要合理分组展示（主 cause + 次要 cause）。
- 诊断数据与 riskFilter 联动逻辑增加前端复杂度。

**推荐 commit message**：
```
feat(paper-trading): add execution diagnostics UI with cause filter
```

---

### Batch K3：Strategy Evaluation Backend

**目标**：后端聚合策略表现多维评分，包含 Paper vs Backtest 偏差和样本不足惩罚。

**范围**：
- 扩展 `PaperPortfolioSummaryResponse.strategyGroups[]` 字段（向后兼容，新增字段，不修改现有字段）。
- 或新增 `GET /api/paper-portfolio/strategy-evaluation`（独立 endpoint，避免 summary 膨胀）。
- 输出字段：
  - `winRate`：胜率（Paper 执行胜率，`tradeCount > 0 && totalReturn > 0` 的 run 占比）。
  - `avgHoldingDuration`：平均持仓时长（秒级，来自 paper_trade lastTradedAt - paper_run startedAt）。
  - `profitFactor`：盈亏比（总盈利 / 总亏损绝对值）。
  - `maxConsecutiveLoss`：最大连续亏损 run 数（需要按时间序列检测）。
  - `backtestPaperReturnDeviation`：Paper 收益率 vs Backtest 收益率的偏差（%，paper_run.totalReturn - backtest_result.totalReturn）。
  - `samplePenalty`：样本不足惩罚系数（run 数 < 3 时惩罚 score；可比 run 数 < 5 时降级）。
  - `compositeScore`：综合评分（各维度加权，内部排序分，非真实投资评级）。
  - `scoreConfidence`：评分置信度（HIGH/MEDIUM/LOW，基于样本量与数据完整度）。

**不做什么**：
- 不修改现有 `score` 字段语义（只新增字段）。
- 不实现 AI 评分。
- 不新增 migration（新字段来自 assembler 计算，基于现有事实源）。
- 不做投资建议（score 仅用于 Paper 内部排序，每次响应必须附带 `disclaimer: "仅 Paper 内部模拟排序分，不代表真实投资表现或建议"`）。

**可能修改文件**：
```
backend/nq-core/src/main/java/.../paper/
  evaluation/PaperStrategyEvaluation.java（value object）
  evaluation/PaperStrategyEvaluationAssembler.java（聚合计算）
  evaluation/PaperVsBacktestDeviation.java（偏差 value object）
  evaluation/SampleSufficiencyJudge.java（样本充分度判定）

backend/nq-infra/src/main/java/.../paper/
  PaperStrategyEvaluationRepository.java（JDBC 联查 paper_run + backtest_result + paper_trade）

backend/nq-api/src/main/java/.../paper/
  evaluation/PaperStrategyEvaluationController.java（可选，独立 endpoint）
  evaluation/dto/StrategyEvaluationResponse.java
  evaluation/dto/StrategyEvaluationItem.java
```

**后端设计要点**：
- `PaperVsBacktestDeviation` 计算：必须对 null totalReturn 防护（Paper 无可比收益率 → `deviation = null`）；必须对无对应 backtest_result 防护（`deviation = null`）。
- `SampleSufficiencyJudge`：
  - `comparableRunCount < 3` → LOW confidence，score 降权 50%。
  - `comparableRunCount < 1` → N/A，不输出 score。
  - `dataInsufficientRunCount / comparableRunCount > 0.5` → LOW confidence。
- `profitFactor` 需防止除以零（总亏损 = 0 时返回 null，不返回 Infinity）。
- `maxConsecutiveLoss` 需要 paper_run 按 startedAt 排序后才能计算时序连续性，仅适用于有时序的运行集。
- `compositeScore` 权重（建议默认值，配置化）：
  - 收益率：30%
  - 风险调整（Sharpe 简化版）：25%
  - 胜率：15%
  - 风控通过率：15%
  - 数据完整度：15%
  - 样本不足惩罚：上述分数 × samplePenalty。

**测试要求**：
- `PaperStrategyEvaluationAssemblerTest`：
  - 正常路径（多 run，有收益/有亏损/有风控）。
  - 样本不足（run < 3）→ LOW confidence。
  - Paper vs Backtest 偏差（run 有对应 backtest_result）。
  - profitFactor 除以零防护。
  - 无 backtest 数据时 deviation = null。
- `StrategyEvaluationControllerTest`：结构验证 + disclaimer 字段存在 + 无真实投资建议文案。

**验收标准**：
- `StrategyEvaluationResponse` 每个 strategy 含 winRate / profitFactor / deviation / compositeScore / scoreConfidence / disclaimer。
- disclaimer 字段非空，内容不包含"真实投资建议 / 推荐买入 / 推荐卖出"等语义。
- `mvn test` 全绿。
- JDBC 无 N+1，有明确 LIMIT。

**风险点**：
- Paper vs Backtest 偏差语义难以严格定义（不同品种/周期的 backtest 不具可比性）；响应必须注明 `deviationDisclaimer`。
- `compositeScore` 权重为工程约定，后续可能需要配置化，首版先硬编码默认值（注释说明）。

**推荐 commit message**：
```
feat(paper-trading): add strategy evaluation backend with paper-vs-backtest deviation
```

---

### Batch K4：Auto Review / Daily Review

**目标**：规则化执行复盘摘要（不接 AI runtime），每日组合复盘，异常聚类。

**范围**：
- 新增 `GET /api/paper-portfolio/daily-review`：组合级每日复盘聚合。
  - 输入：accountId + date（YYYY-MM-DD）。
  - 输出：当日活跃 run 数 / 成交数 / 新告警 / 风控拒绝数 / 平均日收益率 / 最差日回撤 run / 数据质量摘要。
- 新增组合级 auto review rule engine：
  - 规则 1：noOrder run > X% → 摘要含"本日超过 X% 的 run 无订单，建议检查触发频率配置"。
  - 规则 2：riskBlockedCount 连续 N 天 > 0 → "风控拒绝持续发生，建议复盘风控配置"。
  - 规则 3：drawdown > threshold → "当日最大回撤 run ${id} 超过 ${threshold}，建议确认仓位管理"。
  - 规则 4：dataInsufficientRunCount 增加 → "数据不足 run 数量上升，建议检查数据源"。
- 规则输出：`AutoReviewMessage[]`（type / severity / text / affectedRunIds）。
- 异常聚类：相同 cause 的 run 聚合为一组（`AnomalyCluster[]`），展示 clusterType / runCount / representativeRunId。

**不做什么**：
- 不接 AI runtime（规则化 if/else/threshold，非 LLM 生成）。
- 不新增 migration（复盘依赖现有 paper_run_daily_report / paper_risk_result / paper_equity_snapshot）。
- 不修改已有 daily report 生成逻辑。
- 不做自动操作（复盘只读摘要，不触发任何状态改变）。

**可能修改文件**：
```
backend/nq-core/src/main/java/.../paper/
  review/AutoReviewRule.java（interface：evaluate(DailyReviewContext) → AutoReviewMessage?）
  review/AutoReviewRuleEngine.java（运行规则集，返回 AutoReviewMessage[]）
  review/rules/NoOrderThresholdRule.java
  review/rules/RiskBlockedContinuousRule.java
  review/rules/DrawdownThresholdRule.java
  review/rules/DataInsufficientTrendRule.java
  review/DailyReviewAssembler.java（聚合当日 run daily reports + risk results）
  review/AnomalyCluster.java（异常聚类 value object）

backend/nq-infra/src/main/java/.../paper/
  PaperDailyReviewRepository.java（JDBC 批量读取 daily_report by account + date）

backend/nq-api/src/main/java/.../paper/
  review/PaperDailyReviewController.java
  review/dto/DailyReviewResponse.java（date / activeRunCount / tradeSummary / autoReview[] / anomalyClusters[]）
```

**前端接入（K4-UI，可拆为独立子 batch）**：
- 在 Paper 组合看板下方新增"每日复盘摘要"卡片。
- 日期选择器（默认今天，最多回溯 30 天）。
- AutoReviewMessage 以 Alert 组件展示（severity → type 映射）。
- AnomalyCluster 以 Collapse + Badge 展示。
- 无数据（当日无 run / 无 daily report）→ 空态文案。

**测试要求**：
- `AutoReviewRuleEngineTest`：各规则触发 / 不触发路径 + 规则组合。
- `DailyReviewAssemblerTest`：正常路径 + 当日无数据（空 daily_report）。
- `PaperDailyReviewControllerTest`：结构验证，无 AI 文字，无投资建议。
- E2E（K4-UI）：日期切换、AutoReviewMessage 展示、空态、旧后端 fallback。

**验收标准**：
- `GET /api/paper-portfolio/daily-review` 返回 200，含 autoReview 和 anomalyClusters。
- autoReview 文字不包含 AI 生成文案（固定模板字符串）。
- 无 run 当日返回空 autoReview，不崩溃。
- `mvn test` 全绿。

**风险点**：
- `paper_run_daily_report` 生成可能有延迟或空档（当日 report 未生成），DailyReview 需对 null report 优雅处理。
- 规则阈值（X%、N 天、drawdown 阈值）首版硬编码，需注释说明，后续配置化。
- 复盘摘要文字固定模板需审查，避免引入"建议操作"性质的表述（只展示事实，不推荐动作）。

**推荐 commit message**：
```
feat(paper-trading): add daily review auto review rule engine and endpoint
```

---

### Batch K5（可选）：独立 Portfolio / Execution Intelligence 页面拆分

**目标**：Paper Trading 页面已承载组合看板 + 风险驾驶舱 + 策略排行 + 执行诊断 + 每日复盘，加入 K1～K4 后页面复杂度显著上升，考虑拆分独立页面。

**拆分方案 A（推荐）**：
- 保留 `/paper-trading`：Paper Run 列表 + 单 run 详情（已有功能）。
- 新增 `/paper-portfolio`：组合看板 + 风险驾驶舱 + 策略排行 + 执行诊断 + 每日复盘。
  - 导航菜单新增"Paper 组合"入口。
  - PaperTradingPage.tsx 抽出 Dashboard 三个组件到 `paper-portfolio/` 子目录。
  - 路由 `/paper-portfolio` → `PaperPortfolioPage.tsx`。

**拆分方案 B（保守）**：
- 不新增路由，仅在现有 PaperTradingPage 内部用 Tabs 区分"Run 列表"和"组合分析"。
- 实现成本低，但单页更长。

**不做什么**：
- 不新增后端接口（仅路由和组件重组）。
- 不新增 migration。
- 不改变 URL 参数和 API 调用契约。

**可能修改文件**：
```
frontend/src/pages/paper-portfolio/（新目录）
  PaperPortfolioPage.tsx
  PaperPortfolioDashboard.tsx（从 PaperTradingPage 迁移）
  PaperRiskDrawdownDashboard.tsx（从 PaperTradingPage 迁移）
  PaperStrategyRankingDashboard.tsx（从 PaperTradingPage 迁移）
  PaperExecutionDiagnosticsCard.tsx（K2 新增）
  PaperDailyReviewCard.tsx（K4 新增）

frontend/src/router/routes.tsx（新增 /paper-portfolio 路由）
frontend/src/router/navigation.tsx（新增菜单入口）

frontend/tests/e2e/paper-portfolio-*.spec.ts（迁移相关 E2E spec）
```

**验收标准**：
- `/paper-portfolio` 路由可访问，展示完整组合看板。
- `/paper-trading` 路由保留原有 Run 列表功能，无回归。
- 全套 E2E 通过（含路由变更后的 URL 断言更新）。
- `npm run build` BUILD SUCCESS。

**风险点**：
- E2E 测试中大量使用 `/paper-trading` URL，路由变更需批量更新断言。
- PaperTradingPage.tsx 当前 ~4200+ 行，组件拆分需谨慎，避免引入新 bug。
- 建议 K5 单独作为低风险重组任务，不混入 K1～K4 业务逻辑。

**推荐 commit message**：
```
refactor(paper-trading): extract portfolio dashboard to dedicated /paper-portfolio page
```

---

## 4. 事实源收口（GateK 必须明确的边界）

### 4.1 哪些数据来自 repository（直接读 DB）

| 数据 | repository | 字段完整性 |
|------|-----------|-----------|
| 执行事实（order/trade/position） | `PaperOrderRepository` / `PaperTradeRepository` | 完整 |
| 风控结果 | `PaperRiskResultRepository` | 依赖策略实现，可能为空 |
| 资产快照 | `PaperEquitySnapshotRepository` | 依赖快照触发频率 |
| 日报 | `PaperRunDailyReportRepository` | 依赖日报生成任务是否运行 |
| 告警 | `PaperRunAlertRepository` | 完整 |
| 心跳 | `PaperRunHeartbeatRepository` | 依赖调度频率 |
| 回测结果 | `BacktestResultRepository` | 完整（已运行回测的 run） |
| 策略/发布 | `StrategyVersionRepository` / `PublishRepository` | 完整 |

### 4.2 哪些数据来自 assembler 派生（计算/聚合）

| 字段 | 派生来源 | GateK 新增 |
|------|---------|-----------|
| `noOrderCause` | order count + risk_result.checkType | ✓ K1 |
| `orderNoFillCause` | order status + trade count | ✓ K1 |
| `riskBlockedCause` | risk_result.checkType 聚合 | ✓ K1 |
| `winRate` | trade/return 分布计算 | ✓ K3 |
| `profitFactor` | 盈亏分组统计 | ✓ K3 |
| `backtestPaperDeviation` | paper_run + backtest_result 联查 | ✓ K3 |
| `samplePenalty` | comparableRunCount 判定 | ✓ K3 |
| `compositeScore` | 多维度加权 | ✓ K3（扩展现有 score） |
| `autoReviewMessages` | 规则引擎计算 | ✓ K4 |
| `anomalyClusters` | cause 类型聚类 | ✓ K4 |

### 4.3 哪些字段后续可能需要 migration（本阶段不实施）

| 潜在字段 | 当前状态 | 后续需要 migration 的条件 |
|---------|---------|------------------------|
| `paper_run.no_order_cause` | 无（assembler 派生） | 若需持久化诊断历史，需加字段 |
| `paper_run.composite_score` | 无（runtime 计算） | 若需排序优化，可加索引列 |
| `paper_run_daily_review` | 无（runtime 聚合） | 若需持久化复盘历史，需新表 |
| `paper_strategy_group.win_rate` | 无（assembler 派生） | 若需历史趋势，需加字段 |

**约束**：K1～K5 本批次不新增任何 migration；所有新字段通过 assembler 在 runtime 派生。如后续有性能压力或历史趋势需求，可另起独立 migration 审批任务。

---

## 5. 前端信息架构下一阶段建议

### 5.1 当前 Paper Trading 页面结构

```
/paper-trading
  ├── Paper Portfolio Dashboard（组合看板）
  ├── Paper Risk & Drawdown Dashboard（风险驾驶舱）
  ├── Paper Strategy Ranking Dashboard（策略排行）
  └── Paper Run List（run 列表 + 详情）
```

### 5.2 建议目标结构（K5 后）

```
/paper-portfolio（新页面，K5）
  ├── 组合看板（已有）
  ├── 风险与回撤驾驶舱（已有 + K2 执行诊断）
  ├── 执行诊断汇总（K2）
  ├── 策略表现排行（已有 + K3 评分扩展）
  ├── 每日复盘摘要（K4）
  └── 异常聚类（K4）

/paper-trading（现有页面，保留并瘦身）
  ├── Paper Run 列表（已有）
  └── Paper Run 详情（已有）
```

### 5.3 各区域保留 / 折叠 / 迁移 / 拆分建议

| 区域 | 当前页面 | 建议 |
|------|---------|------|
| 组合看板（总览指标、资产曲线） | `/paper-trading` | 迁移到 `/paper-portfolio`（K5） |
| 风险驾驶舱（指标卡、风险清单） | `/paper-trading` | 迁移到 `/paper-portfolio`（K5），K2 扩展诊断 |
| 策略排行（排行表、排序控件） | `/paper-trading` | 迁移到 `/paper-portfolio`（K5），K3 扩展评分 |
| Paper Run 列表 | `/paper-trading` | 保留 |
| Paper Run 详情 | `/paper-trading` | 保留 |
| 执行诊断 | 无 | K2 新增到 `/paper-portfolio` |
| 每日复盘 | 无 | K4 新增到 `/paper-portfolio` |

---

## 6. 测试策略

### 6.1 后端测试

| 类型 | 目标 | 要求 |
|------|------|------|
| Unit（value object） | PaperRunExecutionDiagnostics / PaperStrategyEvaluation / AutoReviewMessage | 各归因路径 × 3 类以上 |
| Unit（assembler） | PaperExecutionDiagnosticsAssembler / PaperStrategyEvaluationAssembler / DailyReviewAssembler | null 防护、样本不足、旧数据兼容 |
| Unit（rule engine） | AutoReviewRuleEngine × 4 规则 | 触发 + 不触发路径 |
| Repository / JDBC | PaperRunDiagnosticsRepository / PaperStrategyEvaluationRepository / PaperDailyReviewRepository | 批量查询无 N+1，有 LIMIT |
| Controller（MockMvc） | 认证 / 无认证 401 / 结构验证 / 无 secret | 每个 endpoint |
| 旧后端兼容 | risk_result 为空时 cause = UNKNOWN | noOrder / riskBlocked 路径 |
| 无 migration 校验 | 不新增 Flyway migration 文件 | CI 构建验证 |

### 6.2 前端 E2E 测试

| 场景 | 测试 |
|------|------|
| 执行诊断 badge 展示 | 风险 Run 清单每行有 cause badge |
| 按 cause 筛选 | 各 cause 类型过滤正确 |
| 旧后端 fallback | 诊断 endpoint 404 时不崩 |
| 评分展示 | strategyRows 含 winRate / compositeScore |
| 每日复盘 | AutoReviewMessage Alert + AnomalyCluster |
| 路由（K5） | `/paper-portfolio` 可访问，`/paper-trading` 无回归 |
| Paper-only 边界 | disclaimer 文案存在，无"真实投资建议"字样 |

### 6.3 安全边界测试

- `GET /api/paper-portfolio/execution-diagnostics` 响应不含 apiKey / secret / rawPayload。
- `autoReview[].text` 不含 credential / session token。
- `compositeScore.disclaimer` 必须存在且不为空。
- 无 LIVE trading 路径触达（controller 不调用任何 trading adapter）。

---

## 7. 推荐执行顺序与理由

### 7.1 推荐顺序

```
K1（执行诊断后端） → K2（执行诊断 UI） → K3（策略评估后端）
→ K3-UI（评分 UI 扩展） → K4（每日复盘）→ K4-UI → K5（可选拆分）
```

### 7.2 为什么先做 K1（后端诊断），而不是先做前端 polish

当前前端已经在 Loop-20/21 完成了指标卡 click-to-filter 和 active state，UX 层已有一轮完整收口。继续 polish（如 CSV 导出、URL 状态持久化、列配置保存）属于体验增强，不提升产品智能层。

优先 K1（诊断后端）的原因：
- 用户看到的是"11 个 run，3 个无订单"，但不知道为什么。解释"为什么"的价值高于让图表更好看。
- 诊断后端是 K2 UI 的先决条件，前端不能 mock 出真实的诊断数据。
- 后端 assembly 层的 noOrderCause / riskBlockedCause 一旦定义，可以同时服务于风险 run 清单（K2）、策略排行（K3）、每日复盘（K4）。

### 7.3 为什么不先做 CSV / URL 状态持久化

CSV 导出和 URL 状态是标准 SaaS feature，与 Execution Intelligence 无关。在当前阶段（Paper 产品只有内部用户），这些是优先级较低的体验项。

### 7.4 为什么不直接进入 LIVE

当前 Paper Trading 正在运行，但 Execution Intelligence 层缺失。在不理解 Paper 执行行为之前进入 LIVE：
- 无法判断策略是否真的在"正确执行"还是在"无订单/无成交地安静地失败"。
- LIVE 开启前必须通过 GateL 所有准入门槛（adapter readiness、credential governance、permission probe 等），这些均未完成。
- GateM（AI Paper Trading）在 LIVE 之前，同样不允许在未理解 Paper 执行诊断的情况下启动。

---

## 8. 边界声明

- **LIVE**：DISABLED。本规划不含任何 LIVE 启用路径。
- **真实交易所**：不访问，不调用，不读取 credential。
- **Credential / Secret**：不读取、不存储、不打印。所有新 endpoint 不含 credential 读取。
- **AI/DH runtime**：不接入。K4 规则化复盘为 if/else/threshold rule engine，非 LLM 生成。
- **Migration**：K1～K4 不新增 migration。如后续需要持久化诊断历史，另起独立 migration 审批任务。
- **文档预算**：本轮只新增 `NQ_GATEK_PAPER_EXECUTION_INTELLIGENCE_PLAN.md`，最小更新 `docs/current/README.md` 索引入口一行。

---

## 9. 下一步第一个实施任务提示词

```
NQ-GATEK-BATCH-K1-EXECUTION-DIAGNOSTICS-BACKEND

任务类型：M 级代码实现
目标：新增只读执行诊断聚合 endpoint，为 Paper Portfolio 前端提供执行归因能力。
不做：不新增 migration，不改前端，不接 AI，不启用 LIVE，不访问真实交易所。
范围：backend/nq-core（诊断 value object + assembler）+ backend/nq-infra（JDBC batch repository）+ backend/nq-api（controller + DTO）。
验收：mvn -f backend/pom.xml test 全绿，新增测试 ≥ 12，JDBC 无 N+1，无 migration 新增。
参考本文件 §3 Batch K1 章节。
```

---

> **声明**：
> - 本文件是规划文档，不代表任何实现已启动。
> - 所有 Batch 均需独立任务发起和代码 review。
> - `compositeScore` / `winRate` / `profitFactor` 为 Paper 内部模拟排序参考指标，不构成真实投资建议，不代表真实收益率评级。
> - GateK Paper Execution Intelligence 计划不授权 LIVE 交易、真实交易所接入、AI 信号或 DH runtime 集成。
