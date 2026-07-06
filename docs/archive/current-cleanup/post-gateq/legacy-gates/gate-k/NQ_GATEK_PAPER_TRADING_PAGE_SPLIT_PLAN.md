# NQ GateK Paper Trading 页面拆分规划（K5）

> 状态：**PLAN ONLY / PENDING IMPLEMENTATION**。本文件只规划 `PaperTradingPage` 的页面拆分方案，不改 frontend/backend/API/migration/测试，不接 AI/DH/LIVE，不访问真实交易所，不读取 credential。
> 任务：`NQ-GATEK-BATCH-K5-PAPER-TRADING-PAGE-SPLIT-PLAN`。
> 上游：[NQ_GATEK_PAPER_EXECUTION_INTELLIGENCE_PLAN.md](NQ_GATEK_PAPER_EXECUTION_INTELLIGENCE_PLAN.md)（K1–K4B 已完成，K5 为可选页面拆分）。
> 适用前提：当前 `frontend/src/pages/paper-trading/PaperTradingPage.tsx` 单文件 5430 行 / 约 303KB，已从单一模拟交易页演变为 Paper Execution Intelligence Console。

---

## 0. 范围与边界

- 目标：明确 Paper Trading 页面是否拆、怎么拆、拆成哪些路由、哪些模块保留原页、哪些独立、如何迁移 query/hook/state/E2E、如何保证旧功能不回归，并拆成可审查的小批次。
- 不做：任何代码改动、任何路由改动、任何 E2E 改动、任何后端/API/migration 改动。
- Paper-only：本页所有运行只作用于 SIM/Paper Trading，不触发真实交易所下单/撤单；拆分后每个子页面都必须保留 Paper-only / LIVE 未开启 / 非投资建议风险文案。
- 拆分原则：URL param 优先于全局 store；最小变更；保留旧入口兼容；不一次性大重构。

---

## 1. 当前页面模块 inventory

> 证据：`frontend/src/pages/paper-trading/PaperTradingPage.tsx`（行号为当前 `dev` 分支快照，实现迁移时以实际为准）。主渲染挂载顺序见 `PaperTradingPage()` 的 `return`（约 1597–2302 行）。

主渲染顺序（单列纵向 `Space` 堆叠，无 tabs）：PageHeader → 组合看板 → 风险回撤驾驶舱 → 执行诊断 → 策略评估 → 自动复盘 → 策略表现排行 → 运行筛选区 → Run 列表(左) + Run 详情(右)。

| # | 模块 | 顶层组件 | 数据来源 / query hook | 主要 state | E2E 覆盖 | 适合独立页面 | 必须留在 `/paper-trading` |
|---|------|----------|----------------------|-----------|----------|--------------|--------------------------|
| 1 | 页头 + Paper-only 风险横幅 | `NqPageHeader` + `NqRiskBanner`（1601） | 无 | 无 | 间接（所有 spec 进入页面即渲染） | — | 每个子页面各保留一份 |
| 2 | Paper 组合看板 | `PaperPortfolioDashboard`（2756） | `usePaperPortfolioSummaryQuery`（portfolioQuery，1297） | 无（纯展示派生） | product-loop 「组合看板」组（约 1266/1316） | 是 | 否 |
| 3 | Paper 风险与回撤驾驶舱 | `PaperRiskDrawdownDashboard`（3182）+ `PortfolioEquityCurveCard`（3083） | 复用 portfolioQuery（同一 query 实例） | `riskFilter`（3333，click-to-filter） | product-loop 「风险与回撤」「Loop-18/19/20/21」组（约 1328–2060） | 是 | 否 |
| 4 | Paper 执行诊断（K2） | `PaperExecutionDiagnosticsDashboard`（4065） | `usePaperExecutionDiagnosticsQuery`（diagnosticsQuery，1299） | `causeFilter`/`severityFilter`（4116–4117） | product-loop 「K2」组（约 2061–2195） | 是 | 否 |
| 5 | Paper 策略评估（K3B） | `PaperStrategyEvaluationDashboard`（4426） | `usePaperStrategyEvaluationsQuery`（strategyEvaluationsQuery，1301） | `ratingFilter`/`confidenceFilter`/`deviationFilter`/`sortDim`/`sortDir`（4475–4479） | product-loop 「K3B」组（约 2196–2362） | 是 | 否 |
| 6 | Paper 自动复盘（K4B） | `PaperAutoReviewDashboard`（4844） | `usePaperAutoReviewsQuery`（autoReviewsQuery，1303） | `severityFilter`/`causeFilter`/`dimensionFilter`（4902–4904） | product-loop 「K4B」组（约 2363–2536） | 是 | 否 |
| 7 | Paper 策略表现排行 | `PaperStrategyRankingDashboard`（5183） | 复用 portfolioQuery（同一 query 实例） | `sortDim`/`sortDir`/`rankFilter`（5233–5235，click-to-filter） | product-loop 「策略表现排行」「Loop-19/20/21」组（约 1553–2060） | 是（与组合/风险同源，建议同页） | 否 |
| 8 | Run 筛选区 + 创建 Run | `NqFilterBar` + `Form`（1628）+ 创建 Modal | `usePaperTradingListQuery`（listQuery，1288）；`useCreatePaperTradingRunMutation`（1345） | `submittedFilters`/`searchVersion`/`createOpen`（1280–1283） | paper-trading-run-smoke / product-loop run 组 | 否（run 生命周期入口） | 是 |
| 9 | Run 列表 | `NqDataTable`（左列，约 1670） | listQuery（同上） | `selectedRow`（1282）→ `focusRunId`（1305） | run-smoke / product-loop | 否 | 是 |
| 10 | 选中 Run 详情（生命周期 + 复盘 + 诊断 + 对照 + 链路 + 账户表现 + 时间线 + 明细 Tab） | 右列：`PaperRunReviewCard`/`PaperRunDiagnosisCard`/`BacktestPaperComparisonCard`/`PaperLineageCard`/`PaperAccountPerformanceCard`/`PaperRunTimeline`/`PaperFactSection`/`SnapshotBlock` | `usePaperTradingDetailQuery`(1306)、`usePaperRunSummaryQuery`(1318)、`usePublishDetailQuery`(1308)、`useBacktestDetailQuery`(1311)、`useEvaluationsListQuery`(1313)；按需懒加载：orders/trades/positions/risk-results/equityCurve/positionCurve/replay(1320–1327)；附属：emergencyStops/dailyReports/heartbeats/schedules/alerts/stabilityChecks/recoveryEvents(1328–1337)；mutations：start/stop/riskOnce/emergencyStop/generateDailyReport(1346–1350) | `factTab`（1286，详情明细 Tab）；依赖 `selectedRow`/`focusRunId` | run-smoke / alert / daily-report / recovery / schedule / stability-check / product-loop 详情组 | 否 | 是 |

### 关键架构事实（决定拆分难度）

1. **三块聚合智能模块共享同一个 query**：组合看板(2)、风险回撤(3)、策略排行(7) 全部消费同一个 `portfolioQuery` 实例（1297）。三者拆到不同路由会触发 `usePaperPortfolioSummaryQuery` 多次实例化 → 重复请求。**结论：2/3/7 必须放在同一路由**，否则违反「避免 query 重复请求」原则。
2. **聚合模块与 run 详情解耦**：组合/风险/诊断/评估/复盘/排行均为 run-independent 聚合（mount 即拉），不依赖 `selectedRunId`。run 列表 + run 详情共享 `selectedRow`（本地 state），且只在同一页面内使用。**结论：拆分后无需跨路由共享 `selectedRunId`，不需要全局 store。**
3. **所有 click-to-filter / 排序 state 都是各 dashboard 组件内部 local state**（riskFilter/causeFilter/ratingFilter/… 均在子组件内 `useState`），天然随组件迁移，不需要提升。
4. **当前 nav 是扁平分组**：`AppSiderMenu` 用 Antd `Menu mode="inline"` + `type:'group'`（不可折叠分组），`AppNavItem` 类型无 `children`，不支持可展开子菜单。`resolveMenuKey` 用「最长前缀匹配」（`pathname === item.path || pathname.startsWith(item.path + '/')`，navigation.tsx:146-152）。**结论：在 `/paper-trading/*` 下加子路由时，菜单高亮会自动通过前缀匹配命中 `paper-trading`，无需改 nav；而新增并列顶层路由则需新增 nav item + menuKey + handle。**

---

## 2. 拆分方案评估

| 判断标准 | A 内部 tabs/anchors | B 拆 2 页（/paper-trading + /paper-portfolio） | C 拆 3–4 个并列页 | **D 单入口 + 子路由（/paper-trading/*）** |
|----------|---------------------|----------------------------------------------|-------------------|------------------------------------------|
| 1 用户易理解 | 中（仍一页） | 中（两页语义略含糊） | 中（入口分散） | **高（一个心智入口 + 清晰子页）** |
| 2 导航清晰 | 低（无 URL 锚点） | 中 | 高（但 nav 项变多） | **高（子路由 + 页内 Segmented）** |
| 3 URL 稳定 | 低（tab 状态不入 URL） | 中（新增顶层 path） | 中（多个新顶层 path） | **高（旧 `/paper-trading` 保留为 index redirect）** |
| 4 未来可扩展 | 低 | 中 | 中（每加一页改 nav） | **高（`/paper-trading/<new>` 直接加）** |
| 5 E2E 迁移可控 | **高（goto 不变）** | 中 | 低（goto 大面积改 + nav 断言改） | 高（index redirect 保旧，分批改 goto） |
| 6 降低页面复杂度 | 低（仍一个巨型组件） | 高 | 高 | **高** |
| 7 避免一次性大重构 | 高 | 中 | 低 | **高（可按子路由逐个迁移）** |
| 8 保留旧入口兼容 | 高 | 中（旧入口语义变窄） | 低（旧入口被拆散） | **高（index redirect 天然兼容）** |

- **方案 A**：路由零改动、E2E 改动最小，但没有解决核心问题——`PaperTradingPage` 仍是一个 5430 行巨型组件，模块继续耦合、首屏仍长。只适合「短期止血」，不作为目标态。
- **方案 B**：拆 2 页能减重，但 `/paper-portfolio` 同时塞「组合+风险+诊断+评估+复盘+排行」六块，仍是一个偏重的聚合页；且新增一个与 `/paper-trading` 并列的顶层 nav 项，语义边界（为什么诊断不在 paper-trading）对用户不够直观。
- **方案 C**：信息架构最分明，但代价最大——新增 3 个顶层 nav 项 + menuKey + handle，所有跨模块 E2E 的 `goto` 和导航断言全要改，违反「避免一次性大重构 / E2E 渐进迁移」。可作为 D 的远期演进（子路由稳定后再提升为顶层），不作为当前批次目标。
- **方案 D（推荐）**：保留 `/paper-trading` 单一心智入口，内部用子路由 `/paper-trading/runs|portfolio|diagnostics|reviews`，页内用 Antd `Segmented`/`Tabs`（route-driven）切换。旧 `/paper-trading` 通过 index redirect 到 `/paper-trading/runs` 保持兼容；菜单高亮靠 `resolveMenuKey` 前缀匹配自动命中，nav 零改动。子路由可逐个迁移，E2E 渐进迁移。

### 推荐方案：**D（单入口 + 子路由）**

理由：在「降低复杂度 / 可扩展 / URL 稳定 / 旧入口兼容 / 避免大重构 / E2E 可控」六项上同时最优；唯一相对 A 多出的成本是子路由 shell 与 E2E goto 渐进迁移，但通过 index redirect + 分批迁移可控。聚合三件套（组合/风险/排行）共享 `portfolioQuery` 的约束在 D 下也容易满足——它们落在同一个 `/paper-trading/portfolio` 子路由。

---

## 3. 推荐路由结构（方案 D）

> 实现位置：`frontend/src/router/routes.tsx`（现 147–150 行为单条 `paper-trading`）。改为带 `children` 的嵌套路由 + index redirect。nav（`navigation.tsx`）保持单条 `paper-trading` 不变。

| route path | route name | nav label | 页面职责 | included modules | not included | fallback / redirect |
|------------|-----------|-----------|----------|------------------|--------------|---------------------|
| `/paper-trading` | `paper-trading`(index) | 模拟交易（唯一菜单项不变） | 仅做重定向壳 | 无 | 无 | `index` → `Navigate to="runs" replace` |
| `/paper-trading/runs` | `paper-trading-runs` | （沿用 paper-trading 高亮，无独立 nav） | Run 生命周期：筛选/创建/启停/紧急停机 + Run 列表 + 选中 Run 详情（含复盘/诊断/对照/链路/账户表现/时间线/明细 Tab） | 模块 8/9/10 | 聚合智能模块 2–7 | 默认子路由（`/paper-trading` 落到此） |
| `/paper-trading/portfolio` | `paper-trading-portfolio` | 同上 | Paper 组合智能：组合看板 + 风险与回撤驾驶舱 + 策略表现排行 + 组合曲线 | 模块 2/3/7 + `PortfolioEquityCurveCard` | run 详情、诊断、评估、复盘 | — |
| `/paper-trading/diagnostics` | `paper-trading-diagnostics` | 同上 | Paper 执行诊断（K2）：总览/分布/Run/Strategy/Publish 诊断 | 模块 4 | run 详情、组合、评估、复盘 | — |
| `/paper-trading/reviews` | `paper-trading-reviews` | 同上 | Paper 评估与复盘：策略评估（K3B）+ 自动复盘（K4B） | 模块 5/6 | run 详情、组合、诊断 | — |

页内二级导航：在 `/paper-trading` 布局壳内放一个 route-driven 的 `Segmented`/`Tabs`（「运行 / 组合 / 执行诊断 / 评估复盘」4 项），点击 `navigate` 到对应子路由；高亮由当前 `pathname` 决定。`menuKey` 仍统一为 `paper-trading`，`createHandle('paper-trading')` 复用，面包屑保持「模拟交易」。

> 为什么不立刻拆顶层 nav（C）：保持单一菜单项可让 E2E 的 nav 断言、面包屑断言全部不变，且用户原有书签 `/paper-trading` 永久可用。待子路由稳定、产品确认信息架构后，可在后续独立任务把高频子页提升为顶层 nav（演进到 C），届时再单独评估。

---

## 4. 页面职责边界

- `/paper-trading/runs`：**只负责 run lifecycle + run detail**。承接现有 run 筛选/创建/列表/启停/紧急停机/每日报告/详情明细全部逻辑与 mutation。
- `/paper-trading/portfolio`：组合 + 风险回撤 + 策略排行（三者共享 `portfolioQuery`，必须同页）。
- `/paper-trading/diagnostics`：执行诊断（K2）独立页。
- `/paper-trading/reviews`：策略评估（K3B）+ 自动复盘（K4B）合页（两者都是「事后评价」语义，信息相关、各自独立 query，可同页分区或页内子 Tab）。
- **保留现有 `PaperTradingPage` 作为兼容壳**：`PaperTradingPage.tsx` 演进为 `<Outlet/>` 布局壳（页头 + Paper-only 横幅 + 二级 Segmented + 子路由出口），不再直接堆所有 dashboard；run 逻辑迁到 `PaperTradingRunsPage.tsx`。
- **需要 route redirect**：`/paper-trading` index → `/paper-trading/runs`，确保旧入口可用。

---

## 5. 状态与 query 迁移方案

| query / state | 现位置 | 迁移目标 | 说明 |
|---------------|--------|----------|------|
| `usePaperTradingListQuery`（listQuery）| 页面顶层 | `PaperTradingRunsPage` | run 列表，随 run 页迁移 |
| `selectedRow` / `focusRunId` / `factTab` | 页面顶层 local state | `PaperTradingRunsPage` local state | run 详情焦点，**仅 run 页内使用，保持 local state，不提升、不入全局 store** |
| 所有 run 详情 query（detail/summary/publish/backtest/evaluations/orders/trades/positions/risk-results/equityCurve/positionCurve/replay/emergencyStops/dailyReports/heartbeats/schedules/alerts/stabilityChecks/recoveryEvents）| 页面顶层（由 focusRunId 驱动）| `PaperTradingRunsPage` | 随 run 详情迁移，懒加载条件（`factTab === ...`）保持不变 |
| run 生命周期 mutations（create/start/stop/riskOnce/emergencyStop/generateDailyReport）| 页面顶层 | `PaperTradingRunsPage` | 随 run 页迁移 |
| `usePaperPortfolioSummaryQuery`（portfolioQuery）| 页面顶层（被 2/3/7 共享）| `PaperPortfolioPage`（**单实例**，传给 3 个 dashboard）| 必须单页单实例，避免重复请求 |
| `riskFilter`（风险驾驶舱）/ `sortDim`+`sortDir`+`rankFilter`（排行）| 子组件内 local | 随子组件进 `PaperPortfolioPage` | 保持组件内 local state |
| `usePaperExecutionDiagnosticsQuery`（diagnosticsQuery）+ `causeFilter`/`severityFilter` | 页面顶层 + 子组件 local | `PaperDiagnosticsPage` | 整体迁移 |
| `usePaperStrategyEvaluationsQuery` + `usePaperAutoReviewsQuery` + 各自 filter/sort state | 页面顶层 + 子组件 local | `PaperReviewsPage` | 整体迁移 |

迁移原则与决策：

- **是否共享 `selectedRunId`**：否。run 列表与 run 详情同处 `/paper-trading/runs`，聚合页不依赖 selected run。无跨路由状态共享。
- **是否使用 URL param**：run 焦点默认保留 local state；**可选增强**（K5-C 之后）在 `/paper-trading/runs?run=<paperRunId>` 用 query param 支持深链/刷新保焦点，但非必须、不在首批，避免破坏现有行为。
- **是否保留 local state**：是。`selectedRow`/`factTab`/各 filter 全部保持 local state。
- **是否引入全局 store / 新增 Zustand**：**否**。本拆分不新增任何 Zustand 状态（Zustand 仍只放 auth/account-context）。
- **是否破坏现有 hook**：否。所有 `usePaper*Query` hook 签名/调用方式不变，只是调用位置从单页迁到对应子页。

---

## 6. 组件拆分方案（目录规划，本轮不创建文件）

```text
frontend/src/pages/paper-trading/
  PaperTradingPage.tsx              # 演进为布局壳：页头 + Paper-only 横幅 + 二级 Segmented + <Outlet/>
  PaperTradingRunsPage.tsx          # 模块 8/9/10：run 筛选/创建/列表/详情/明细 Tab/mutations
  PaperPortfolioPage.tsx            # 模块 2/3/7：单实例 portfolioQuery → 组合/风险/排行
  PaperDiagnosticsPage.tsx          # 模块 4：执行诊断（K2）
  PaperReviewsPage.tsx              # 模块 5/6：策略评估（K3B）+ 自动复盘（K4B）
  components/
    PaperPortfolioDashboard.tsx            # 从主文件抽出（现 2756）
    PaperRiskDrawdownDashboard.tsx         # 现 3182（含 PortfolioEquityCurveCard / ClickableMetricCard）
    PaperStrategyRankingDashboard.tsx      # 现 5183
    PaperExecutionDiagnosticsDashboard.tsx # 现 4065
    PaperStrategyEvaluationDashboard.tsx   # 现 4426
    PaperAutoReviewDashboard.tsx           # 现 4844
    run/                                   # run 详情子卡片（可选进一步细分）
      PaperRunReviewCard.tsx / PaperRunDiagnosisCard.tsx /
      BacktestPaperComparisonCard.tsx / PaperLineageCard.tsx /
      PaperAccountPerformanceCard.tsx / PaperRunTimeline.tsx /
      PaperFactSection.tsx / SnapshotBlock.tsx
  shared/
    paperTradingHelpers.ts          # 纯函数：buildPaperRunReview/buildPaperRunDiagnoses/
                                    # buildPaperTimelineEvents/buildBacktestPaperComparison/
                                    # buildPaperLineage/buildPaperAccountPerformance 等（现 151–1276 的派生逻辑）
    paperTradingConstants.ts        # 各类 *_FILTER_OPTIONS / *_TONE / *_LABEL 常量表
```

抽取顺序原则：先抽**纯函数与常量**（无 JSX、无 state，零行为风险）→ 再抽**自包含 dashboard 组件**（已是独立 function，仅依赖传入 query）→ 最后建**子页面壳**。run 详情子卡片可在 K5-A 抽到 `components/run/`，也可保留在 `PaperTradingRunsPage` 内分文件，视体积决定。

---

## 7. E2E 迁移计划

> 现状：`frontend/tests/e2e/paper-trading-product-loop-smoke.spec.ts` 2537 行 / **45 个 test**，全部 `goto('/paper-trading')`；另有 7 个独立 spec（run/alert/daily-report/recovery/schedule/stability-check），各 1–2 test；共享 fixture `paper-trading-fixtures.ts`（`prepareGateI3PaperTradingFixture`）。

策略：**index redirect 是 E2E 渐进迁移的关键**。`/paper-trading` → `/paper-trading/runs` 后，凡 run 相关 spec（run/alert/daily-report/recovery/schedule/stability-check 及 product-loop 的 run/详情组）`goto('/paper-trading')` **无需改动**即可落到 runs 页继续通过。只有被搬到 portfolio/diagnostics/reviews 子路由的模块，其对应 test 的 `goto` 才需改为子路由 path。

| 现有 spec / test 组 | 处置 | 目标 goto |
|---------------------|------|-----------|
| `paper-trading-run-smoke.spec.ts`、`-alert-`、`-daily-report-`、`-recovery-`、`-schedule-`、`-stability-check-` | **保留不动**（靠 index redirect） | `/paper-trading`（→runs） |
| product-loop：run 创建/启停/详情/账户收益/链路/对照/明细懒加载组（约 801–1265） | 保留断言，逐步迁入 `paper-trading-runs-smoke.spec.ts` | `/paper-trading/runs` |
| product-loop：组合看板 + 风险回撤 + Loop-18/19/20/21 + 策略排行组（约 1266–2060） | 迁入 `paper-portfolio-smoke.spec.ts` | `/paper-trading/portfolio` |
| product-loop：K2 诊断组（约 2061–2195） | 迁入 `paper-diagnostics-smoke.spec.ts` | `/paper-trading/diagnostics` |
| product-loop：K3B 评估 + K4B 复盘组（约 2196–2536） | 迁入 `paper-reviews-smoke.spec.ts` | `/paper-trading/reviews` |

新增 spec（最小 smoke 优先，不一次性重写 45 用例）：

- `paper-trading-runs-smoke.spec.ts`
- `paper-portfolio-smoke.spec.ts`
- `paper-diagnostics-smoke.spec.ts`
- `paper-reviews-smoke.spec.ts`

fixture 复用：`paper-trading-fixtures.ts` 的 `prepareGateI3PaperTradingFixture` 与各 spec 顶部 mock route（`**/api/paper-trading/portfolio/summary`、`/execution-diagnostics`、`/strategy-evaluations`、`/auto-reviews`、`/runs/**`、`/publishes/**`、`/backtest-configs/**`）保持复用；mock 与路由无关，只跟随 `goto` 目标页加载需要的接口。

测试预算执行：

1. K5-A/K5-B 阶段 `/paper-trading` 仍渲染全部模块（兼容壳期），45 用例全绿、零 goto 改动。
2. K5-C 每迁一个模块群，才改该群对应 test 的 goto，并加一条该子路由的最小 smoke；其余 spec 不动。
3. K5-D 才把 product-loop 大 spec 按域拆成 4 个 spec、删重复断言；主链路 smoke 保留。

---

## 8. 分批施工计划

> 每批独立可审查、可回滚、E2E 可验证；批与批之间 `/paper-trading` 始终可用。

- **K5-A：Component extraction only**
  - 从 `PaperTradingPage.tsx` 抽出纯函数/常量到 `shared/`，抽出 6 个 dashboard 组件 + run 子卡片到 `components/`。
  - 不改路由、不改 UI 行为、不改 props 语义、不改 E2E。
  - 验收：`npm run build` 通过；`npm run test:e2e` 全绿（45 + 7 用例不变）。
  - commit：`refactor(paper-trading): extract dashboard sections`

- **K5-B：Add child routes / route shell**
  - `routes.tsx` 把 `paper-trading` 改为带 `children`：index redirect → `runs`，新增 `runs/portfolio/diagnostics/reviews` 子路由；`PaperTradingPage` 改为 `<Outlet/>` 壳 + 二级 Segmented；新建 4 个子页面文件（首版 portfolio/diagnostics/reviews 可仍复用现有 dashboard，runs 承接 run 逻辑）。
  - 保留旧入口（`/paper-trading` 经 redirect 可达）；nav 不变。
  - 初版可让 4 个子页都先各自渲染对应模块；E2E goto 仍可用 `/paper-trading`（落到 runs）。
  - 验收：build 通过；E2E 全绿（旧 goto 经 redirect 通过）；`/paper-trading/portfolio` 等子路由可直达。
  - commit：`feat(paper-trading): add paper intelligence routes`

- **K5-C：Move portfolio / diagnostics / reviews modules**
  - 每次只迁一个模块群（建议顺序：portfolio → diagnostics → reviews），把对应 dashboard 从 runs 页/兼容壳真正移到目标子页，`portfolioQuery` 在 portfolio 页单实例化。
  - 每迁一群，新增该子路由最小 smoke，并把该群 product-loop 用例 goto 改到子路由。
  - 保持旧入口兼容、其余 spec 不动。
  - 验收：build 通过；被迁群的子路由 smoke + 全量 E2E 绿；无重复请求回归。
  - commit：`refactor(paper-trading): move portfolio diagnostics views`

- **K5-D：E2E split / cleanup**
  - 把 `paper-trading-product-loop-smoke.spec.ts` 按域拆成 `paper-trading-runs-smoke` / `paper-portfolio-smoke` / `paper-diagnostics-smoke` / `paper-reviews-smoke`；保留主链路 smoke，删除跨 spec 重复断言。
  - 验收：拆分后 E2E 总用例覆盖不降、运行更快、全绿。
  - commit：`test(paper-trading): split paper trading smoke specs`

- **K5-E：Old page slimming / redirect**
  - 在覆盖充分后，将 `PaperTradingPage` 收敛为纯导航/布局壳（仅页头 + 横幅 + Segmented + Outlet），确认无残留死代码；保留 `/paper-trading` → `/paper-trading/runs` redirect。
  - 不做破坏性删除，除非对应 E2E 覆盖充分。
  - 验收：build 通过；E2E 全绿；`/paper-trading` 仍可达。
  - commit：`refactor(paper-trading): slim paper trading page`

---

## 9. 风险清单

| # | 风险 | 等级 | 触发条件 | 缓解 |
|---|------|------|----------|------|
| R1 | route 变更导致 E2E 大面积失败 | 中 | 直接改 `/paper-trading` 语义或删旧入口 | index redirect 保旧入口；goto 仅随模块迁移逐组改；分批小步 |
| R2 | selected run 详情状态丢失 | 低 | 把 `selectedRow` 提升为全局/跨路由 | 保持 local state，run 列表与详情同页；不跨路由共享 |
| R3 | query 重复请求增多 | 中 | 组合/风险/排行被拆到不同子路由各自实例化 `portfolioQuery` | 三者强制同页（portfolio），单实例 query 传参 |
| R4 | 页面跳转后筛选状态丢失 | 低 | 子路由切换卸载组件 | filter 为各页 local；如需跨切换保留，后续可选 URL param，本批不强制 |
| R5 | 用户找不到原入口 | 低 | nav 改名/拆项 | 保留单一「模拟交易」菜单 + 旧 URL redirect；二级 Segmented 显式 |
| R6 | docs / nav / breadcrumb 不一致 | 低 | 子路由 handle 缺失 | 子路由统一 `createHandle('paper-trading')`，面包屑「模拟交易」一致 |
| R7 | 多页面后安全文案重复或缺失 | 中 | 拆页后漏带 Paper-only / LIVE 未开启 / 非投资建议横幅 | 文案下沉到布局壳（页头 NqRiskBanner 每页可见）；各子页 smoke 断言 Paper-only 文案 |
| R8 | 45+ E2E 迁移成本 / 一次性重写 | 中 | K5-B 即重写全部 spec | 兼容壳 + redirect 让旧 spec 先全过；K5-D 才拆 spec |
| R9 | run 详情懒加载行为回归（factTab 条件请求）| 中 | 抽组件时改动 `factTab === ...` 触发条件 | K5-A 保持 props/触发条件逐字不变；E2E「明细按需懒加载」用例守护 |

---

## 10. 验收标准（后续 implementation 通用）

每个 K5 批次合入前必须满足：

- `frontend` build 通过：`Set-Location frontend; npm run build`。
- 相关 E2E 通过：`npm run test:e2e`（兼容壳期 45+7 全绿；迁移期被改组 + 新 smoke 全绿）。
- 旧 `/paper-trading` 入口可用（经 index redirect 落到 `/paper-trading/runs`）。
- 每个子页面仍保留 Paper-only / LIVE 未开启 / 非投资建议（rules-based、工程排查语义）文案。
- 不改后端、不改 API、不新增 migration。
- 不接 AI / DH runtime、不启用 LIVE、不访问真实交易所、不读取 credential。
- 不引入新 Zustand 全局状态；服务端数据仍走 TanStack Query。
- 无 query 重复请求回归（组合/风险/排行单实例 `portfolioQuery`）。

---

## 11. Commit message 建议（按批次）

```text
refactor(paper-trading): extract dashboard sections      # K5-A
feat(paper-trading): add paper intelligence routes        # K5-B
refactor(paper-trading): move portfolio diagnostics views # K5-C
test(paper-trading): split paper trading smoke specs      # K5-D
refactor(paper-trading): slim paper trading page          # K5-E
```

本规划文档本身（docs-only）：`docs(paper-trading): plan Paper Trading page split`。

---

## 12. 结论

- **是否拆**：拆。`PaperTradingPage` 5430 行 / 303KB 已超出单页可维护边界。
- **怎么拆**：方案 D — 单入口 `/paper-trading` + 子路由 `runs/portfolio/diagnostics/reviews`，旧入口 index redirect 兼容，nav 不变。
- **分几批**：K5-A 抽组件 → K5-B 加子路由壳 → K5-C 逐群迁移 → K5-D 拆 E2E → K5-E 瘦身旧页。
- **状态**：无需全局 store，无需共享 selectedRunId，filter/selected 保持 local；URL param 为可选增强。
- **下一步**：`NQ-GATEK-BATCH-K5-A-PAPER-TRADING-COMPONENT-EXTRACTION`（只抽组件，不改路由/行为/E2E）。
