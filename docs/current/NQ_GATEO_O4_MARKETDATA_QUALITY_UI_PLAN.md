# NQ-GATEO-O4-MARKETDATA-QUALITY-UI-PLAN

## 1. 当前状态

任务名称：`NQ-GATEO-O4-MARKETDATA-QUALITY-UI-PLAN`。

任务归属：NQ-only。

任务类型：`FRONTEND_UI_PLANNING` / `MARKETDATA_QUALITY_VIEW_PLANNING` / `DATA_SOURCE_STATUS_DESIGN` / `SECURITY_BOUNDARY_REVIEW` / `DOCUMENTATION`。

本轮结论：`PASS`（通过）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现）。

O-4 implementation：`NOT STARTED`（未开始）。

GateO 当前事实：

- O-1 controlled public outbound guard：`PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结）。
- O-2 Data Quality Center：`PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结）。
- O-3 MarketData Runtime Readiness API：`FROZEN / ACCEPTED`（已冻结 / 已接受）。
- O-3 已冻结 `GET /api/marketdata/readiness` read-only API baseline。
- O-4 MarketData Quality UI plan：`PASS / PLAN ONLY / NOT IMPLEMENTED`（通过 / 仅规划 / 未实现）。
- O-4A UI contract plan review：`PASS / ACCEPTED`（通过 / 已接受）。
- O-4B MarketData Quality read-only UI implementation：`ALLOWED / READ-ONLY UI ONLY / NOT STARTED`（允许 / 仅只读 UI / 未开始）。
- O-5 manual public outbound smoke：`NOT STARTED`（未开始）。
- GateO stage：`NOT COMPLETED`（未完成）。
- LIVE：`DISABLED`（已禁用）。
- AI：`NOT STARTED`（未启动）。
- DH runtime：`NOT_INTEGRATED`（未集成）。
- RealClient / real provider / real permission probe：`NOT_IMPLEMENTED`（未实现）。

本文件只规划 NQ Console 中 MarketData Quality UI 的信息架构、组件、API 消费、状态展示、测试策略和后续实现批次；不实现页面、不改前端源码、不改后端、不新增 API、不新增 migration、不执行真实 public outbound。

## 2. 目标

O-4 的目标是在 NQ Console 中让用户清楚判断行情数据是否可信，但不能把数据质量诊断解释为交易授权。

页面需要回答：

1. 当前行情数据源是否可用。
2. 当前数据是否新鲜。
3. 当前是否存在缺口。
4. 最近成功 / 失败 / 观测时间是什么。
5. 错误类别、降级原因或禁用原因是什么。
6. 数据来自哪里：`LOCAL_DB`、`FIXTURE`、`FAKE_SERVER`、`PUBLIC_CANDIDATE` 或 `UNKNOWN`。
7. 当前诊断为什么不等于 LIVE、private trading、permission probe 或 trading authorization。

## 3. 非目标

本轮不做：

- 不新增 `/marketdata/quality`、`/marketdata/readiness` 或其他前端路由实现。
- 不改 `frontend/**`、`frontend/tests/**`、`backend/**`、`research/**`、`scripts/**`、`deploy/**`、`.github/**`。
- 不新增 API，不新增 migration，不修改历史 migration。
- 不消费真实交易所 public endpoint、private endpoint、credential endpoint、permission probe endpoint 或 O-5 manual public smoke endpoint。
- 不执行真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken HTTP。
- 不读取 `.env`、key、pem 或 credential material。
- 不开启 LIVE，不接 AI，不接 DH runtime。
- 不实现 RealClient、real provider、真实 permission probe、signed request 或 private WebSocket。
- 不展示 `real-ready`、`live-ready`、`trading authorized`、`private trading ready`、`permission granted`、`provider ready`。
- 不混入 NQ-DH Integration runtime 内容。

## 4. 已检查现有前端与 API inventory

### 4.1 路由与导航

当前路由事实：

- `frontend/src/router/routes.tsx` 已有 `/marketdata`，渲染 `MarketdataPage`。
- `frontend/src/router/navigation.tsx` 已有侧边导航项 `marketdata`，路径 `/marketdata`，label 为“行情查询”。
- `frontend/src/pages/runtime/RuntimeReadinessPage.tsx` 已通过 query string 深链到 `/marketdata` 的 readiness 视图。
- 当前不存在独立 `/marketdata/quality` 或 `/marketdata/readiness` 路由实现。

### 4.2 现有 MarketData 页面

当前 `frontend/src/pages/marketdata/MarketdataPage.tsx` 已包含：

- `GET /api/marketdata/bars` bars 查询。
- `GET /api/marketdata/readiness` readiness 查询。
- K 线 / 成交量展示：`NqKlineChart`、`NqVolumeChart`。
- Data Quality / Readiness 区域。
- Sandbox Source compact block。
- bars-derived fallback、freshness、gap、qualityStatus、source health 不可用时的 fail-closed 文案。

现有页面仍是“查询 + 图表 + 质量摘要”混合页面，不是独立的数据源质量中心。

### 4.3 现有前端类型与 API client

当前 `frontend/src/api/marketdata.ts`：

- `marketdataApi.getReadiness(query)` 调用 `/marketdata/readiness`。
- 仍通过 TanStack Query 页面调用，不需要 Zustand 承载服务端状态。

当前 `frontend/src/types/marketdata.ts` 的 `MarketdataReadinessSummary` 仍主要覆盖旧字段：

- `status`
- `freshnessStatus`
- `sourceHealthStatus`
- `sourceHealthReason`
- `qualityStatusSummary`
- `barCount`
- `firstBarTime`
- `lastBarTime`
- `expectedBarCount`
- `gapCount`
- `unknownQualityCount`
- `lastSuccessAt`
- `lastFailureAt`
- `backendSupportLevel`
- `generatedAt`

O-4B implementation 前必须补齐 O-3B 后端已追加字段的前端 contract 类型，但本轮不修改前端类型。

### 4.4 O-3 readiness API 字段

O-3 已冻结 `GET /api/marketdata/readiness`，当前 response 保留旧字段并追加：

- `sourceCode`
- `exchange`
- `timeframe`
- `dataOrigin`
- `sourceStatus`
- `sourceHealth`
- `gapStatus`
- `lastObservedAt`
- `latencyMs`
- `errorRate`
- `errorCategory`
- `missingFrom`
- `missingTo`
- `staleAfterSeconds`
- `degradedReason`
- `disabledReason`
- `traceId`
- `requestId`
- `updatedAt`

关键语义：

- API 仍为 DB-only / no-egress / no-credential / diagnostic-only。
- 当前 `dataOrigin` 不包含已落地 `PUBLIC_OUTBOUND`。
- `errorRate`、`missingFrom`、`missingTo`、`traceId`、`requestId` 在没有稳定本地事实时可为 `null`。
- `null` 表示“暂无稳定事实”，不是 0。
- `exchange` 是 `exchangeCode` 的 alias；`timeframe` 是 `interval` 的 alias；alias 不代表 provider、LIVE、permission probe 或 trading readiness。

### 4.5 现有 E2E

当前 E2E 事实：

- `frontend/tests/e2e/marketdata-quality-readiness-smoke.spec.ts` 已有 no-backend mocked readiness smoke。
- `frontend/tests/e2e/marketdata-readiness-real-backend-smoke.spec.ts` 已有 real local backend readiness smoke。
- `frontend/tests/e2e/marketdata-kline-readiness-smoke.spec.ts` 已覆盖 K 线与成交量 no-backend smoke。
- `frontend/tests/e2e/marketdata-real-backend-smoke.spec.ts` 仍有旧断言文案，例如 source health unavailable fallback，O-4B/O-4C 应按 O-3B 字段更新测试，而本轮不改测试。

## 5. 页面 / 路由建议

推荐方案：优先复用现有 `/marketdata` 页面，在页面内增加 `Quality / Readiness` tab 或等价分区，不新增独立路由。

理由：

- `/marketdata` 已经是现有 MarketData 入口，并已承载 bars、K 线、Data Quality / Readiness、Sandbox Source。
- 现有 `RuntimeReadinessPage` 已深链到 `/marketdata`，复用页面能减少 GateM/GateN/GateO 入口分裂。
- O-4 第一批目标是把数据可信度讲清楚，不是扩展导航面。
- 不新增路由可以降低 O-4B 实现风险，避免侧边导航新增页面与既有 GateJ/GateO 闭环冲突。

可接受的后续方案：

- 如果 O-4B/O-4C 实现后 `/marketdata` 页面过长，可在 O-4D 或独立后续任务中评审新增 `/marketdata/quality`。
- 如果新增独立路由，必须保留 `/marketdata` 入口和深链兼容，并明确新路由仍只消费 `GET /api/marketdata/readiness`，不消费真实交易所 endpoint。

不建议：

- 不建议现在新增 `/marketdata/readiness` 独立页面。
- 不建议在 O-4 第一批引入大型图表优先布局。
- 不建议在侧边导航中新增容易被误读为“交易所已就绪”的菜单名称。

## 6. 信息架构

推荐页面名称：

- 英文任务名：`MarketData Quality`
- 中文页面名：`行情数据质量中心`

页面结构：

1. `PageHero`：标题、页面定位、只读诊断提示、LIVE disabled / no trading authorization 风险提示。
2. `MarketDataReadinessSummary`：核心状态摘要，展示 source health、freshness、gap、dataOrigin、last observed / updated。
3. `FilterCard`：沿用 existing query context，包含 exchangeCode、marketType、symbol、interval、from、to。
4. `MarketDataSourceHealthTable`：主数据源健康表，按 source / exchange / symbol / interval 展示状态和原因。
5. `MarketDataGapPanel`：展示 gapStatus、gapCount、missingFrom、missingTo；null 显示为“暂无稳定事实”。
6. `MarketDataErrorPanel`：展示 errorCategory、errorRate、lastFailureAt、degradedReason、disabledReason。
7. `MarketDataReadinessDrawer`：详情抽屉，展示完整 read-only payload、alias 说明、trace/request 字段是否存在。
8. Charts area：只保留为后续优先级，不压过质量表。

## 7. 组件规划

建议组件拆分：

| Component | 职责 | 首批建议 |
| --- | --- | --- |
| `MarketDataQualityPage` | 页面容器或现有 `MarketdataPage` 内的 Quality tab shell | O-4B 若复用页面，可先不新建独立 page。 |
| `MarketDataReadinessSummary` | 顶部状态摘要，突出 diagnostic-only 和最近更新时间 | O-4B P0。 |
| `MarketDataSourceHealthTable` | 主表，展示 sourceCode / exchange / symbol / interval / status / reason | O-4B P0。 |
| `MarketDataFreshnessBadge` | freshness 状态标签 | O-4B P0。 |
| `MarketDataOriginBadge` | dataOrigin 标签，解释 LOCAL_DB / FIXTURE / FAKE_SERVER / PUBLIC_CANDIDATE / UNKNOWN | O-4B P0。 |
| `MarketDataGapPanel` | gapStatus、gapCount、missingFrom、missingTo 展示 | O-4B P0。 |
| `MarketDataErrorPanel` | errorCategory、errorRate、lastFailureAt、degradedReason、disabledReason 展示 | O-4B P0。 |
| `MarketDataQualityNotice` | 常驻风险提示：诊断不等于交易授权 | O-4B P0。 |
| `MarketDataReadinessDrawer` | 行详情，展示完整只读字段和 alias 说明 | O-4C P1。 |

优先复用已有设计系统组件：

- `PageHero`
- `NqStatusTag`
- `DataFreshness`
- `NqRiskBanner` / `RiskBanner`
- `NqDataTable`
- `NqKlineChart`
- `NqVolumeChart`

## 8. 数据映射计划

O-4 只允许规划消费：

```text
GET /api/marketdata/readiness
```

不得规划消费：

- 真实交易所 public endpoint。
- private endpoint。
- credential endpoint。
- permission probe endpoint。
- O-5 manual public smoke endpoint。

字段展示计划：

| API field | UI label | 展示规则 |
| --- | --- | --- |
| `sourceCode` | 数据源 | 诊断 key，不表示 provider ready。 |
| `exchangeCode` / `exchange` | 交易所 | 同义 alias；优先显示 `exchangeCode`，详情说明 alias。 |
| `symbol` | 交易对 | 只表示行情查询对象。 |
| `interval` / `timeframe` | 周期 | 同义 alias；优先显示 `interval`。 |
| `dataOrigin` | 数据来源 | 使用 `MarketDataOriginBadge`；`PUBLIC_CANDIDATE` 不是真实 public outbound 已执行。 |
| `sourceStatus` | 数据源状态 | `ENABLED / DISABLED / DEGRADED / ERROR / RATE_LIMITED`。 |
| `sourceHealth` | 数据源健康 | `HEALTHY / DEGRADED / RATE_LIMITED / TIMEOUT / ERROR / UNKNOWN`。 |
| `freshnessStatus` | 新鲜度 | `FRESH / STALE / VERY_STALE / NO_DATA / ERROR / DISABLED`。 |
| `gapStatus` | 缺口状态 | `NONE / GAP / PARTIAL / UNKNOWN`；`UNKNOWN` 不能显示成 0。 |
| `lastSuccessAt` | 最近成功 | null 显示“暂无稳定事实”。 |
| `lastFailureAt` | 最近失败 | null 显示“暂无稳定事实”。 |
| `lastObservedAt` | 最近观测 | null 显示“暂无稳定事实”。 |
| `latencyMs` | 延迟 | null 显示“暂无稳定事实”；有值时显示 ms。 |
| `errorRate` | 错误率 | null 显示“暂无稳定事实”；有值时按百分比显示，不默认 0%。 |
| `errorCategory` | 错误类别 | `NONE` 可显示“无错误”；其他使用 warning/error 语义。 |
| `gapCount` | 缺口数量 | null 显示“暂无稳定事实”；0 只在 API 明确返回 0 时显示。 |
| `missingFrom` / `missingTo` | 缺口区间 | null 显示“暂无稳定事实”。 |
| `staleAfterSeconds` | 过期阈值 | null 显示“暂无稳定事实”；不在前端硬编码业务阈值。 |
| `degradedReason` | 降级原因 | 脱敏文本；空值显示“暂无稳定事实”。 |
| `disabledReason` | 禁用原因 | 脱敏文本；空值显示“暂无稳定事实”。 |
| `updatedAt` / `generatedAt` | 更新时间 | 优先显示 `updatedAt`，兼容显示 `generatedAt`。 |

前端类型 O-4B 必须补齐上述字段，并把 nullable 字段声明为 `string | number | null | undefined` 等精确类型，不得用 `any` 掩盖 contract gap。

## 9. 状态展示规则

必须覆盖的状态：

| Status | 中文 | 颜色语义 | 规则 |
| --- | --- | --- | --- |
| `FRESH` | 新鲜 | success / green | 只表示数据诊断新鲜，不表示可交易。 |
| `STALE` | 过期 | warning / orange | 提示最近数据落后。 |
| `VERY_STALE` | 严重过期 | danger / red | 显示强风险。 |
| `NO_DATA` | 无数据 | default / gray | 不显示为成功。 |
| `ERROR` | 错误 | danger / red | 显示错误类别和原因。 |
| `DISABLED` | 禁用 | default / gray | 显示禁用原因。 |
| `HEALTHY` | 健康 | success / green | 仅用于系统健康，不表示行情涨跌。 |
| `DEGRADED` | 降级 | warning / orange | 显示降级原因。 |
| `RATE_LIMITED` | 限流 | warning / orange | 显示限流语义，不自动重试。 |
| `TIMEOUT` | 超时 | warning / orange | 显示 timeout，不暴露 raw provider payload。 |
| `GAP` | 存在缺口 | warning / orange | 显示 gap count 和区间事实。 |
| `NONE` | 无缺口 | success / green | 仅当 API 明确返回 `NONE` 时显示。 |
| `UNKNOWN` | 未知 | default / gray | fail-closed，不显示 ready。 |

视觉规则：

- 不用 success / danger 直接表示市场涨跌。
- 数据健康状态与市场涨跌颜色分离。
- up/down 只用于行情涨跌，不用于系统健康。
- warning/danger 用于风险、错误、过期、缺口、降级或限流。
- null 字段必须显示为“暂无稳定事实”或等价中文，不得显示为 0。

## 10. 图表规划

O-4 第一批优先级：

- P0：数据源健康表、freshness 状态、gap 状态、错误类别、风险提示。
- P1：latency 趋势、error rate 趋势、gap 分布。
- P2：K 线 / 成交量图表接入或复用增强。
- P3：多源对比图。

当前建议：

- O-4B 不新增复杂图表，只确保质量状态可读。
- K 线 / 成交量继续复用现有 `NqKlineChart` / `NqVolumeChart`。
- 如果后续需要 TradingView Lightweight Charts 专项增强，放到 O-4D 或 O-4C 后的单独图表 foundation 任务。
- 普通趋势图可复用 ECharts，但必须先确认 API 是否提供稳定历史窗口数据；没有稳定事实时不得伪造趋势。

## 11. 风险提示文案

页面必须常驻显示：

```text
当前页面只显示行情数据质量诊断。
数据质量正常不代表可以交易。
public marketdata readiness 不等于 trading authorization。
LIVE 当前禁用。
private trading / permission probe / real provider 未实现。
```

禁用文案：

- 不写“可交易”。
- 不写“交易所已就绪”。
- 不写“权限已授予”。
- 不写“LIVE ready”。
- 不写“provider ready”。
- 不把 `HEALTHY` 翻译为“可实盘”。

## 12. 测试规划

O-4 implementation 后续最低验证：

```powershell
Set-Location frontend
npm run build
npm run test:e2e
```

建议拆分测试：

1. no-backend smoke：使用 route mock `GET /api/marketdata/readiness`，覆盖 `FRESH / HEALTHY / NONE`、`STALE`、`NO_DATA`、`ERROR`、`DISABLED`、`GAP`、null 字段。
2. backend-dependent smoke：只在本地后端可用时执行，验证真实 `GET /api/marketdata/readiness` 驱动 UI；不调用真实交易所。
3. forbidden text test：断言页面不显示 `trading authorized`、`live ready`、`permission granted`、`real-ready`、`provider ready`。
4. forbidden endpoint test：断言浏览器只调用 `/api/marketdata/readiness` 和既有必要本地 API，不调用 private endpoint、credential endpoint 或真实交易所 host。

测试场景：

- `FRESH / HEALTHY / NONE`。
- `STALE`。
- `VERY_STALE`。
- `NO_DATA`。
- `ERROR`。
- `DISABLED`。
- `RATE_LIMITED`。
- `TIMEOUT`。
- `GAP`。
- `UNKNOWN`。
- `errorRate = null`。
- `missingFrom = null`。
- `missingTo = null`。
- `gapCount = null` 与 `gapCount = 0` 的不同展示。
- 不显示 trading authorization / LIVE / permission grant 文案。
- 不调用 private endpoint。
- 不读取 credential。

本轮为 planning-only，不运行 `npm run build` 或 Playwright；不能把未运行 E2E 写成通过。

## 13. O-4 implementation 批次

| Batch | 名称 | 状态 | 目标 | 成功标准 |
| --- | --- | --- | --- | --- |
| O-4A | UI contract plan review | `PASS / ACCEPTED`（通过 / 已接受） | 已复核路由、页面、字段、文案、风险提示和测试矩阵；API.md enum drift 已修正 | P0/P1=0，允许进入 O-4B。 |
| O-4B | MarketData Quality read-only page/table implementation | `ALLOWED / READ-ONLY UI ONLY / NOT STARTED`（允许 / 仅只读 UI / 未开始） | 在既有 `/marketdata` 复用 Quality / Readiness tab 或分区，补齐前端 types 与只读表格 | `npm run build` 与对应 no-backend smoke 通过。 |
| O-4C | 状态 badge / notice / drawer polish | `NOT STARTED`（未开始） | 统一状态 badge、null 展示、风险提示和详情抽屉 | forbidden wording test 通过。 |
| O-4D | 图表 foundation | `OPTIONAL / NOT STARTED`（可选 / 未开始） | 如果 API 提供趋势事实，再做 latency/error/gap 趋势或 K 线增强 | 不伪造历史趋势，不新增真实外联。 |
| O-4E | O-4 freeze review | `NOT STARTED`（未开始） | 冻结 O-4 UI baseline | P0/P1=0，验证记录完整。 |

是否允许 O-4 implementation 开始：

- O-4A UI contract plan review 已 `PASS / ACCEPTED`。
- 允许进入 O-4B implementation，但只允许 read-only UI：复用 `/marketdata`，补齐 frontend readiness type、状态表、summary、notice、drawer 和 no-backend smoke。
- 任何 O-4B+ 代码实现仍不得触碰 backend、API、migration、O-5 public smoke、LIVE、AI、DH runtime、RealClient、real provider 或 permission probe。

## 14. 安全边界

O-4 UI 必须保持：

- 只读消费 `GET /api/marketdata/readiness`。
- DB-only / no-egress / no-credential / diagnostic-only 语义。
- `PUBLIC_CANDIDATE` 不是 `PUBLIC_OUTBOUND` 已执行。
- `HEALTHY` 不是 real provider ready。
- `FRESH` 不是 trading authorized。
- `NONE` gap 不是 future bars 永远完整。
- `NO_DATA`、`UNKNOWN`、`DISABLED`、`STALE`、`GAP`、`ERROR` 均 fail-closed。
- 不读取或展示 credential、token、secret、cookie、private key、签名串、raw headers、raw request、raw response。
- 不把 O-4 与 NQ-DH Integration runtime 混在一起。

## 15. 风险分级

### P0

当前 P0：0。

P0 触发条件：

- O-4 plan 允许真实外联。
- O-4 plan 读取 credential。
- O-4 plan 把 readiness 写成 trading authorization。
- O-4 plan 混入 private trading / permission probe / LIVE。
- O-4 plan 混入 NQ-DH Integration runtime。

### P1

当前 P1：0。

O-4A review 曾发现 `docs/current/API.md` 的 readiness enum 描述与后端 enum 不一致；本轮已修正为后端实际 vocabulary，因此不再阻断 O-4B。

P1 触发条件：

- 页面文案误导为可以交易。
- API 字段理解错误，null 被显示为 0。
- DataOrigin 语义误导。
- 新增页面/路由规划与现有导航冲突。
- O-4/O-5 边界不清。

### P2

当前 P2：

1. `frontend/src/types/marketdata.ts` 仍未包含 O-3B 新增 readiness 字段；O-4B 必须补齐前端类型。
2. 现有 `/marketdata` 页面混合 bars 查询、图表、ingestion、dataset 与 readiness，O-4B 需要用 tab 或分区降低认知负担。
3. 现有部分 E2E 仍有旧 fallback 文案，O-4B/O-4C 需要更新而不是删除风险断言。
4. 图表优先级不能压过数据质量表。

### P3

当前 P3：

1. 现有页面文案存在中英混排；O-4B 应优先中文化用户可见风险文案，同时保留 API 字段原名。
2. 若新增独立路由，current README / navigation 入口可能重复；建议先复用 `/marketdata`。

## 16. 回滚方式

本轮只改文档。回滚方式：

```powershell
git restore --worktree -- README.md docs/current/README.md docs/current/GATEO_PLAN.md docs/current/STATUS.md docs/current/ROADMAP.md docs/current/TESTING.md docs/current/WORKLOG.md
Remove-Item -LiteralPath docs/current/NQ_GATEO_O4_MARKETDATA_QUALITY_UI_PLAN.md
```

如果已提交，使用普通 revert：

```powershell
git revert <commit>
```

不得使用 `git reset --hard` 或 `git clean -fd` 处理本任务回滚。

## 17. Final decision

O-4 plan 结论：`PASS / PLAN ONLY / NOT IMPLEMENTED`。

O-4A review：`PASS / ACCEPTED`。

O-4B implementation：`ALLOWED / READ-ONLY UI ONLY / NOT STARTED`。

O-5 manual public outbound smoke：`NOT STARTED`。

GateO stage：`NOT COMPLETED`。

页面 / 路由推荐：优先复用现有 `/marketdata` 页面并增加 Quality / Readiness tab 或分区；暂不新增独立路由。

API 消费推荐：只消费 `GET /api/marketdata/readiness`；不消费真实交易所 endpoint、private endpoint、credential endpoint、permission probe endpoint 或 O-5 manual public smoke endpoint。

下一步：

```text
NQ-GATEO-O4B-MARKETDATA-QUALITY-READ-ONLY-UI-IMPLEMENTATION
```

O-4B 只能实现只读 UI，不得执行 O-5 manual public outbound smoke，不得触碰 LIVE、AI、DH runtime、RealClient、real provider、real permission probe 或 private trading。
