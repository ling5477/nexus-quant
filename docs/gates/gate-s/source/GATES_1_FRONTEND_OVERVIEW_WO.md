# GateS-1 Frontend Overview Work Order

任务名称：`NQ-GATES-1-FRONTEND-OVERVIEW-WO`。

最终状态：`PLAN READY`（规划已就绪）/ `NOT IMPLEMENTED`（未实现）/ `READY TO COMMIT`（可进入提交前复核）。本文只规划后续前端如何消费已实现的 `GET /api/shadow-runs/overview`；本轮不写前端实现代码，不改后端，不新增 API，不新增 E2E，不改 Python，不改 CI。

## 1. GateS-1 Frontend Objective

目标是在现有 NQ Console 中增加 Shadow Run 运营总览的最小前端入口，让用户能在 Shadow Run 列表上下文中看到本地 Shadow Run diagnostic facts（诊断事实）的总体状态、最新运行、最新一致性报告、blockers / warnings / nextSteps 和边界提示。

本 work order 的目标只覆盖后续实现规划：

- 消费已实现的 `GET /api/shadow-runs/overview`。
- 定义 TypeScript type、API client、TanStack Query hook 和最小 UI 区块。
- 明确 loading / error / empty / normal / stale evidence / diverged / blocked / failed 状态。
- 明确 LIVE、real provider、private trading、AI/DH runtime 和交易授权边界 badge。
- 明确后续实现阶段的最小验证范围。

本文不表示：

- GateS-1 frontend 已实现。
- GateS-1 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- Dashboard v2 已启动。
- Shadow trading 已启用。
- LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe 已启动或已实现。

## 2. Current Frontend Baseline

已只读审查当前前端基线：

- API client：`frontend/src/api/client.ts` 使用统一 `apiClient`，baseURL 来自 `appEnv.apiBaseUrl`，响应错误通过 `normalizeApiError` 标准化。
- Query keys：`frontend/src/api/query-keys.ts` 已有 `shadowRunsQueryKeys.all = ['shadow-runs'] as const`，并已有 list / detail / events / snapshots / latestConsistencyReport keys；尚无 overview key。
- Shadow Run API client：`frontend/src/api/shadow-runs.ts` 目前只包含 `listShadowRuns()`、`getShadowRunDetail()`、`getShadowRunEvents()`、`getShadowRunSnapshots()`、`getShadowRunLatestConsistencyReport()`；尚无 `getShadowRunOverview()`。
- Shadow Run types：`frontend/src/types/shadow-runs.ts` 已有 list/detail/event/snapshot/latest consistency 类型和通用 `JsonValue`；尚无 overview response types。
- Shadow Run hooks：`frontend/src/hooks/useShadowRunQueries.ts` 已有 list/detail/events/snapshots/latest consistency hooks；尚无 `useShadowRunOverview()`。
- 路由：`frontend/src/router/routes.tsx` 已有 `/strategies/shadow-runs` 和 `/strategies/shadow-runs/:shadowRunId`；本计划不新增 route。
- 导航：`frontend/src/router/navigation.tsx` 已有 `shadow-runs` 菜单项；本计划不新增菜单项。
- 页面：`frontend/src/pages/shadow-runs/ShadowRunListPage.tsx` 已是只读列表入口，包含 loading / error / empty / list、status 筛选、no-side-effect flags 和 detail 跳转；无写侧按钮。
- 页面：`frontend/src/pages/shadow-runs/ShadowRunDetailPage.tsx` 已只读展示 detail / events / snapshots / latest consistency report，并过滤敏感字段。
- Dashboard：`frontend/src/pages/dashboard/DashboardPage.tsx` 当前聚焦 Paper Trading 安全总览，不是 GateS Shadow overview 主入口。
- Strategy Validation：`frontend/src/pages/strategies/StrategyValidationPage.tsx` 已展示 Evaluation Gate、Paper / Shadow comparison、Shadow Live no-side-effect preview 和 Python artifact binding preview 的只读追溯；不应在本切片继续扩大为完整 validation center。
- Runtime / Marketdata：`frontend/src/pages/runtime/RuntimeReadinessPage.tsx` 与 `frontend/src/pages/marketdata/MarketdataPage.tsx` 已有独立 readiness / data quality 信息架构；本切片不重复实现这些域。
- Smoke 结构：`frontend/tests/e2e/shadow-run-detail-smoke.spec.ts` 已用 mock/no-backend 模式覆盖 Shadow Run list/detail/replay、loading/error/empty、敏感字段过滤和只读请求；后续 overview implementation 可复用其 stub 风格，但本轮不新增测试。

当前缺口：

- 无 `ShadowRunOverviewResponse` 前端类型。
- 无 `getShadowRunOverview()` API client。
- 无 `shadowRunsQueryKeys.overview()`。
- 无 `useShadowRunOverview()`。
- `/strategies/shadow-runs` 顶部无 overview summary。

## 3. Backend Endpoint Contract

当前后端 endpoint 已实现并 push：

- Endpoint：`GET /api/shadow-runs/overview`。
- 请求参数：无。
- 请求体：不接受 request body。
- 方法语义：GET-only、read-only、no-side-effect（无副作用）。
- 数据来源：仅 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports` 本地事实。
- 不新增 migration。
- 不新增 POST / PUT / PATCH / DELETE。
- 不启动 runner / scheduler。
- 不调用 adapter、risk write side、order / account / ledger 服务。
- 不读取 credential material。
- 不调用真实交易所。

响应字段按 `docs/current/API.md` 与后端 DTO 当前事实解释：

- 顶层：`generatedAt / diagnosticOnly / noSideEffect / notTradingAuthorization / liveDisabled / realProviderImplemented / privateTradingImplemented / aiDhRuntimeIntegrated / totalRuns / runningRuns / blockedRuns / failedRuns / completedRuns / staleRuns / latestRun / latestConsistency / divergenceSeverity / blockers / warnings / nextSteps / evidenceAnchors / traceId`。
- `latestRun`：`shadowRunId / strategyVersionId / datasetId / paperRunId / status / authorizationBoundary / noOrderSubmission / noCredentialAccess / noPrivateEndpoint / noLedgerMutation / noAccountMutation / noExternalPrivateIo / createdAt / updatedAt / startedAt / completedAt`。
- `latestConsistency`：`reportId / shadowRunId / paperRunId / comparisonStatus / metricDelta / divergenceReasons / limitations / generatedAt / traceId`。
- `divergenceSeverity`：`NONE`（无偏离）/ `LOW`（低）/ `MEDIUM`（中）/ `HIGH`（高）/ `CRITICAL`（严重）/ `UNKNOWN`（未知）。
- `comparisonStatus`：`CONSISTENT`（一致）/ `DIVERGED`（偏离）/ `PARTIAL`（部分可比）/ `NOT_COMPARABLE`（不可比）/ `FAILED`（失败）。
- `blockers` / `warnings`：`code / severity / message / sourceType / sourceId`。
- `nextSteps`：`code / owner / action / expectedEvidence / blocking`。
- `evidenceAnchors`：`sourceType / sourceId / sourceVersion / sourceTimestamp / checksum`。

前端不得把该 endpoint 解释为交易授权、LIVE 放行、真实 provider readiness、private trading readiness、runner trigger、scheduler trigger 或 strategy approval。

## 4. API Type Plan

后续实现阶段建议在 `frontend/src/types/shadow-runs.ts` 增加以下类型。字段应保持与后端响应同名，时间字段用 ISO string，JSON 字段复用现有 `JsonValue`。

```ts
export interface ShadowRunOverviewResponse {
    generatedAt: string;
    diagnosticOnly: boolean;
    noSideEffect: boolean;
    notTradingAuthorization: boolean;
    liveDisabled: boolean;
    realProviderImplemented: boolean;
    privateTradingImplemented: boolean;
    aiDhRuntimeIntegrated: boolean;
    totalRuns: number;
    runningRuns: number;
    blockedRuns: number;
    failedRuns: number;
    completedRuns: number;
    staleRuns: number;
    latestRun: ShadowRunOverviewLatestRun | null;
    latestConsistency: ShadowRunOverviewLatestConsistency | null;
    divergenceSeverity: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | 'UNKNOWN' | string;
    blockers: ShadowRunOverviewBlocker[];
    warnings: ShadowRunOverviewWarning[];
    nextSteps: ShadowRunOverviewNextStep[];
    evidenceAnchors: ShadowRunOverviewEvidenceAnchor[];
    traceId: string;
}

export interface ShadowRunOverviewLatestRun {
    shadowRunId: string;
    strategyVersionId: string;
    datasetId: string;
    paperRunId: string | null;
    status: string;
    authorizationBoundary: string;
    noOrderSubmission: boolean;
    noCredentialAccess: boolean;
    noPrivateEndpoint: boolean;
    noLedgerMutation: boolean;
    noAccountMutation: boolean;
    noExternalPrivateIo: boolean;
    createdAt: string;
    updatedAt: string;
    startedAt: string | null;
    completedAt: string | null;
}

export interface ShadowRunOverviewLatestConsistency {
    reportId: string;
    shadowRunId: string;
    paperRunId: string | null;
    comparisonStatus: string;
    metricDelta: JsonValue;
    divergenceReasons: JsonValue;
    limitations: JsonValue;
    generatedAt: string;
    traceId: string | null;
}

export interface ShadowRunOverviewEvidenceAnchor {
    sourceType: string;
    sourceId: string;
    sourceVersion: string | null;
    sourceTimestamp: string | null;
    checksum: string | null;
}

export interface ShadowRunOverviewBlocker {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

export interface ShadowRunOverviewWarning {
    code: string;
    severity: string;
    message: string;
    sourceType: string;
    sourceId: string | null;
}

export interface ShadowRunOverviewNextStep {
    code: string;
    owner: string;
    action: string;
    expectedEvidence: string;
    blocking: boolean;
}
```

类型边界：

- 不新增 `tradeApproved`、`authorizedForTrading`、`liveReady`、`tradingReady`、`realProviderReady`、`privateTradingReady` 等字段。
- 不新增 credential、private payload、真实账户余额、真实仓位、真实订单 ID、withdraw / transfer 相关字段。
- `APPROVED`（验证报告层通过）若未来出现在其他 validation report 中，只表示 validation report 层语义，不进入本 overview type。

## 5. API Client / Hook Plan

后续实现阶段建议在 `frontend/src/api/shadow-runs.ts` 增加：

```ts
export async function getShadowRunOverview(): Promise<ShadowRunOverviewResponse> {
    const {data} = await apiClient.get<ShadowRunOverviewResponse>('/shadow-runs/overview');
    return data;
}
```

后续实现阶段建议在 `frontend/src/api/query-keys.ts` 增加：

```ts
overview: () => [...shadowRunsQueryKeys.all, 'overview'] as const,
```

canonical TanStack Query key 必须等价于：

```ts
['shadow-runs', 'overview']
```

后续实现阶段建议在 `frontend/src/hooks/useShadowRunQueries.ts` 增加：

```ts
export function useShadowRunOverview() {
    return useQuery({
        queryKey: shadowRunsQueryKeys.overview(),
        queryFn: () => shadowRunsApi.getShadowRunOverview(),
        retry: false,
    });
}
```

Hook 边界：

- 默认 `retry: false`，避免 404 / 500 被 UI 长时间误显示为可用状态。
- 不启用 polling，除非后续单独规划 freshness 策略。
- 不触发 list/detail/events/snapshots 重取作为副作用；overview 和 list 可独立加载。
- 不在 hook 内写入 Zustand 或其他全局状态。

## 6. UI Placement Decision

推荐方案 A：在现有 `/strategies/shadow-runs` 列表页顶部增加 `Overview Summary`。

理由：

- 当前用户已能从 `Shadow Runs` 导航进入 `/strategies/shadow-runs`，该位置是 Shadow Run 事实的自然入口。
- 只需要扩展既有只读页面，不新增 route，不新增菜单，不创建 Dashboard v2。
- overview 与 list 共用 `shadow-runs` 信息架构，用户可以先看聚合状态，再用 status filter 查看 run 列表。
- 避免把 GateS-1 前端首切片扩大成完整 Dashboard / Strategy Validation Center / Runtime Center。

备选方案 B：在 Dashboard v2 规划前，先把 Shadow Run Overview Card 放到现有 Shadow Runs 页面。

决策：采用方案 A。方案 B 与方案 A 在实际落点上等价，本文不建议新增 Dashboard v2 或全局控制台卡片。

最小 UI 区块：

- `totalRuns`：本地 Shadow Run 总数。
- `runningRuns`：运行中数量。
- `blockedRuns`：阻断数量。
- `failedRuns`：失败数量。
- `completedRuns`：完成数量。
- `staleRuns`：证据过期数量。
- `latestRun.status`：最新 run 状态。
- `latestConsistency.comparisonStatus`：最新 consistency 状态。
- `divergenceSeverity`：偏离严重度。
- `blockers / warnings / nextSteps`：机器可读诊断列表，优先展示 code、severity、message、owner/action。
- `evidenceAnchors`：只展示 sourceType / sourceId / timestamp / checksum 摘要，不展开 JSON payload。

## 7. UI State Plan

后续 UI 必须覆盖以下状态：

| 状态 | 触发条件 | 用户可见表现 | 禁止误写 |
| --- | --- | --- | --- |
| loading | `useShadowRunOverview()` 首次加载 | 顶部 summary skeleton 或 `NqLoadingState`；列表可独立加载 | 不显示任何成功态结论 |
| error | overview query 失败 | `NqErrorState` + retry，只说明 overview 加载失败 | 不隐藏错误，不把失败当空数据 |
| empty | `totalRuns = 0` 且无 `latestRun` | 空态说明暂无本地 Shadow Run facts | 不解释为风险解除或交易可用 |
| normal | overview 加载成功且无 blocked / failed / diverged / stale | 展示 counts、latest、boundary badges | success 只表示诊断查询成功，不表示盈利或交易授权 |
| stale evidence | `staleRuns > 0` 或 warning code 包含 `STALE_EVIDENCE` | warning tone，提示证据过期和 nextSteps | 不自动降级/升级核心 `shadow_runs.status` |
| diverged | `comparisonStatus = DIVERGED` 或 `divergenceSeverity` 为 `MEDIUM/HIGH/CRITICAL` | warning/danger tone，突出 divergence reasons 摘要 | danger 不表示价格下跌 |
| blocked | `blockedRuns > 0` 或 blocker 列表非空 | danger tone，展示 blocker codes 与 blocking nextSteps | blocker 是诊断阻断，不是交易动作入口 |
| failed | `failedRuns > 0` 或 `latestRun.status = FAILED` | danger tone，展示最新失败 run 和 evidence anchors | 不提供 retry / rerun / execute 操作 |

状态优先级建议：

1. error。
2. loading。
3. blocked / failed。
4. diverged。
5. stale evidence。
6. empty。
7. normal。

## 8. Boundary Badge Plan

后续 UI 顶部必须固定展示以下 badge，且中文 tooltip 或说明要明确边界：

- `LIVE DISABLED`：LIVE 关闭；不表示后续可开启。
- `Real provider NOT IMPLEMENTED`：真实 provider 未实现。
- `Private trading NOT IMPLEMENTED`：私有交易适配未实现。
- `Shadow Run is diagnostic only`：Shadow Run 仅用于诊断。
- `Not trading authorization`：不是交易授权。
- `AI/DH runtime not integrated`：AI / DH runtime 未集成。

如果后端返回：

- `liveDisabled !== true`。
- `realProviderImplemented !== false`。
- `privateTradingImplemented !== false`。
- `aiDhRuntimeIntegrated !== false`。
- `diagnosticOnly !== true`。
- `noSideEffect !== true`。
- `notTradingAuthorization !== true`。

前端应显示 fail-closed warning，不得把异常 flag 当成功态渲染。

## 9. Color / Wording Rules

颜色规则：

- `success` 只表示查询成功、证据一致或任务完成状态，不表示盈利、上涨或交易可用。
- `danger` 只表示失败、阻断、严重偏离或边界异常，不表示价格下跌。
- 价格或 PnL 的 up/down 必须和 success/danger 分离；本 overview 首切片不展示收益曲线或价格涨跌。
- `APPROVED`（验证报告层通过）只允许出现在 validation report 语境；不得解释为交易授权。

页面文案规则：

- 用户可见文案中文为主，英文 enum 可保留，但首次出现必须有中文解释或 tooltip。
- 可以显示 `diagnostic only`、`no-side-effect`、`not trading authorization` 作为边界短语，但必须配中文说明。
- 不得使用“可交易”“已授权交易”“实盘可用”“交易放行”等正向交易授权文案。
- 不得把 Shadow Run 写成 Shadow Live trading。
- 不得把 Data Quality diagnostic 写成交易授权。
- 不得把 Python offline evaluation 写成 ML ready 或 live execution ready。
- 不得把 AI / DH runtime 写成已接入。

## 10. Test Scope

后续实现阶段只要求：

- `npm run build`。
- 一个最小 smoke / component test，如现有测试体系支持：
  - 页面能渲染 overview summary。
  - mock `GET /api/shadow-runs/overview` 成功返回时展示 counts、latestRun、latestConsistency、boundary badges。
  - mock error / empty 至少各覆盖一条主路径。
  - 断言不出现 start / stop / execute / trade / placeOrder / cancelOrder / withdraw / transfer 入口。
  - 断言不调用真实交易所 host。
  - 断言不渲染 credential / forbidden fields。
- 不为每个状态补 E2E。
- 不启动真实后端以外的交易链路。
- 不调用真实交易所。
- 不读取 credential。

本轮验证只运行 docs / read-only 检查，不运行：

- Maven：本轮不改后端。
- `npm run build`：本轮不改前端。
- Playwright：本轮不改页面或 E2E。
- Python pytest / mypy / ruff：本轮不改 research。

## 11. Non-goals

本轮不做：

- 不写 frontend 实现。
- 不新增 page。
- 不改 route。
- 不改 API client。
- 不新增 E2E。
- 不改 backend。
- 不新增 API。
- 不新增 migration。
- 不改 Python。
- 不改 CI。
- 不新增真实交易按钮。
- 不新增 start / stop / execute / trade / placeOrder / cancelOrder / withdraw / transfer 入口。
- 不读取 `.env`、`.env.local`、`*.key`、`*.pem`、secrets、dumps、logs、backup。
- 不调用真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken API。
- 不把 overview 写成交易授权、LIVE readiness、AI started、DH integrated 或 Strategy Validation 交易批准。

## 12. P0 / P1 / P2 / P3 Findings

### P0

- 未发现阻断本 frontend work order 的 P0。最大 P0 风险是后续 UI 把 overview、`APPROVED`、Data Quality diagnostic 或 consistency status 误写成交易授权；本文已将边界 badge、文案和测试断言列为后续实现验收项。

### P1

- 现有前端没有 overview type/client/hook；后续实现必须先补类型与 query key，再落 UI，避免直接在页面内 ad hoc fetch。
- 若后续把 overview 放入 Dashboard v2 或新增路由，会扩大 GateS-1 前端首切片范围；本 work order 建议只扩展 `/strategies/shadow-runs` 顶部。
- 后续实现必须避免新增任何写侧按钮；即使 nextSteps 中出现 action 字段，也只能展示诊断动作文本，不映射为执行按钮。

### P2

- stale evidence 阈值由后端 read model 当前规则决定，前端首版只消费 `staleRuns` 和 warning code，不自行推导跨域 freshness。
- `metricDelta / divergenceReasons / limitations` 是 JSON 值；前端首版应摘要展示并沿用敏感字段过滤，不应渲染 raw payload。
- `success` / `danger` 与收益、涨跌无关；前端实现需要复用现有 status tone 时显式区分。

### P3

- Shadow Run list/detail、Strategy Validation、Runtime readiness、Marketdata readiness 已分散存在；GateS-1 首版应避免重复建设导航和概念。
- 后续如果要做 Dashboard v2 或 Strategy Validation Center，应拆到后续 GateS batch，不应并入本 frontend overview 首切片。

## 13. Acceptance Criteria

- Frontend work order 文档完成。
- 明确使用 `GET /api/shadow-runs/overview`。
- 明确 `ShadowRunOverviewResponse`、`ShadowRunOverviewLatestRun`、`ShadowRunOverviewLatestConsistency`、`ShadowRunOverviewEvidenceAnchor`、`ShadowRunOverviewBlocker`、`ShadowRunOverviewWarning`、`ShadowRunOverviewNextStep` 类型计划。
- 明确 `getShadowRunOverview()` API client 计划。
- 明确 `useShadowRunOverview()` hook 计划。
- 明确 TanStack Query key：`['shadow-runs', 'overview']`。
- 明确 UI 放置位置：现有 `/strategies/shadow-runs` 列表页顶部。
- 明确 loading / error / empty / normal / stale evidence / diverged / blocked / failed 状态。
- 明确 boundary badges。
- 明确颜色与文案规则。
- 明确后续测试范围。
- 本轮不修改 `frontend/**`。
- 本轮不修改 `backend/**`。
- 本轮不新增 API。
- 本轮不新增 migration。
- 本轮不调用真实交易所。
- 本轮不读取 credential。
- 不出现交易授权、实盘可用、AI started、DH integrated 误写。
- 产出可直接衔接 frontend implementation 的任务边界。

## 14. Next Concrete Action

完成本 work order 提交后，后续可另起 `NQ-GATES-1-FRONTEND-OVERVIEW-IMPLEMENTATION`，仅在现有 Shadow Run 前端范围内实现：

- `frontend/src/types/shadow-runs.ts` overview types。
- `frontend/src/api/query-keys.ts` overview query key。
- `frontend/src/api/shadow-runs.ts` `getShadowRunOverview()`。
- `frontend/src/hooks/useShadowRunQueries.ts` `useShadowRunOverview()`。
- `frontend/src/pages/shadow-runs/ShadowRunListPage.tsx` 顶部 `Overview Summary`。
- 最小 build + smoke/component validation。

本轮 review decision：`NQ-GATES-1-FRONTEND-OVERVIEW-WO：PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）。
