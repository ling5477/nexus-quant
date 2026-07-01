# GateO Plan：公开行情受控外联与数据质量运行化阶段

## 1. 当前事实

本轮任务 `NQ-GATEO-PLAN-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND` 是 GateO O-0 planning baseline。

当前结论：`PASS`（通过）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现）。GateO implementation 仍为 `NOT STARTED`（未开始）。

当前上游证据：

- GateJ：`VERIFIED`（已验证）。
- GateK：`VERIFIED`（已验证）。
- GateM：`VERIFIED`（已验证）。
- GateN：`PARTIAL / ACCEPTED WITH EXPLICIT CI VISIBILITY RESIDUAL`（部分验证 / 已显式接受 CI 可见性残留）。GateN tag / archive / local freeze validation / later dev CI 存在，但 `nq-gaten-freeze` tagged commit `361d2ac7bb595f72067b0e2c2d0485361e9a0540` 的 direct CI run 未定位，不能把 GateN 写成完整 `VERIFIED`。
- `NQ-GATES-JKMN-FREEZE-CI-EVIDENCE-RECONCILIATION` 已完成并提交，当前 HEAD 为 `bd745561 docs(governance): reconcile GateJ-K-M-N freeze evidence`。

当前禁止能力保持不变：

- LIVE：`DISABLED`（已禁用）。
- AI：`NOT STARTED`（未启动）。
- DH runtime：`NOT_INTEGRATED`（未集成）。
- RealClient / real provider / real private trading adapter：`NOT_IMPLEMENTED`（未实现）。
- real permission probe：`NOT_IMPLEMENTED`（未实现）。
- public marketdata readiness 只允许解释为 diagnostic，不等于 trading authorization。

现有能力边界：

- `GET /api/marketdata/readiness` 已存在，当前只读聚合本地 DB bars / ingestion facts，不触发采集、不调用 adapter、不访问外部交易所、不读取 credential。
- `/marketdata` 前端页面已展示 K 线、成交量、Data Quality / Readiness、freshness、gap、source health、backend support、last success / failure 等事实。
- `.github/workflows/ci.yml` 已包含 no-outbound guard、CI security smoke、forbidden env checks、denylist hosts、backend/frontend/research/secret scan 等基线；GateO manual public outbound smoke 不允许进入默认 CI。

## 2. GateO 定位

GateO = `Public MarketData Controlled Outbound & Data Quality Runtime`，中文名为“公开行情受控外联与数据质量运行化阶段”。

GateO 的定位是在 GateN public marketdata / exchange sandbox / no-real baseline 之后，规划如何安全推进“公开行情只读受控外联”和“数据质量中心”。GateO 只能从 public marketdata diagnostic 入手，不能跨入 private trading、LIVE、AI 自动交易或 DH runtime。

GateO O-0 本轮只完成 planning baseline，不做实现、不新增 API、不新增 migration、不新增页面、不新增 E2E、不修改 CI workflow、不调用真实交易所。

## 3. GateO 非目标

GateO 不是实盘阶段。

GateO 不是真实私有交易阶段。

GateO 不是 AI 自动交易阶段。

GateO 不是 DH runtime 接入阶段。

本阶段不做：

- 不下单、不撤单、不转账、不提现。
- 不读取账户余额、不访问 private endpoint、不访问 signed endpoint。
- 不实现 RealClient、real provider、real private trading adapter 或 real permission probe。
- 不读取、输出、复制、打印或写入 credential material。
- 不把 public marketdata readiness、图表可显示、source health 可读或数据较完整写成 trading authorization。
- 不让默认测试、默认 CI 或默认本地 profile 真实外联。

## 4. 允许方向与禁止方向

允许方向：

- 公开行情。
- 只读数据。
- 无 credential。
- 无签名。
- 显式 profile / feature flag。
- 默认 no-egress。
- 可关闭、可审计、可回放、可降级。
- source health / freshness / gap / latency / error rate。

禁止方向：

- 私有交易。
- 下单、撤单、转账、提现。
- account / order / balance / withdraw / transfer。
- private WebSocket / signed route / user data stream。
- 真实 permission probe。
- LIVE。
- RealClient 私有交易。
- DH runtime 写 NQ。
- AI 自动交易。
- public marketdata 被写成 trading authorization。

## 5. GateO 批次拆分

| Batch | 名称 | 状态 | 目标 | 明确不做 |
| --- | --- | --- | --- | --- |
| O-0 | GateO Plan | `PASS / PLAN ONLY / NOT IMPLEMENTED` | 建立 GateO 目标、非目标、批次、验收和安全边界 | 不改代码、不改 CI、不真实外联 |
| O-1 | Public MarketData Controlled Outbound Plan | `PLANNED / NOT STARTED`（已规划 / 未开始） | 规划 OKX / Binance public REST only 受控外联 | 不实现 HTTP client、不进默认 CI |
| O-2 | Data Quality Center Plan | `PLANNED / NOT STARTED` | 规划 source health / freshness / gap / latency / error rate 数据质量中心 | 不新增表、不新增 API、不改 UI |
| O-3 | MarketData Runtime Readiness API Plan | `PLANNED / NOT STARTED` | 基于现有 readiness/source/quality 模型规划 API 收口 | 不重复造接口、不实现接口 |
| O-4 | MarketData Quality UI Plan | `PLANNED / NOT STARTED` | 规划数据质量 UI 与图表选型 | 不新增页面、不做 mock AI/DH/LIVE |
| O-5 | Manual Public Outbound Smoke Plan | `PLANNED / NOT STARTED` | 规划最后阶段手动 profile 的最小 public outbound smoke | 不进入默认 CI、不读 credential |
| O-FREEZE | GateO Freeze Criteria | `PLANNED / NOT STARTED` | 明确 GateO 冻结验收 | 不把 planning 写成 implementation |

## 6. O-1 Public MarketData Controlled Outbound Plan

O-1 只规划真实公开行情只读外联，后续必须单独开工并先做 plan review。

范围：

- OKX / Binance public REST only。
- 无 API key。
- 无签名。
- 无 private endpoint。
- 无 account / order / balance / withdraw / transfer。
- 显式 profile 才允许 outbound；默认 profile、默认测试和默认 CI 仍必须 no-egress。
- 协议事实必须来自交易所官方文档，不能凭 SDK、博客、历史经验或第三方文章推断规则。

O-1 必须产出：

- 官方文档入口与版本记录。
- public REST endpoint allowlist。
- private / signed endpoint denylist。
- timeout / rate limit / retry / backoff / circuit breaker 计划。
- request/response redaction 规则。
- no-egress default 与 manual outbound profile 的隔离策略。
- rollback / disable switch。

O-1 不得产出：

- production HTTP client 实现。
- default CI public outbound。
- credential lookup。
- permission probe。
- trading authorization。

## 7. O-2 Data Quality Center Plan

O-2 规划数据质量中心，不新增 schema 或代码。

必须规划的字段与语义：

- `source_health`：数据源健康状态，只能解释为行情源诊断，不是交易授权。
- `freshness`：最新数据是否满足时间窗口。
- `gap_count`：本地序列或质量标记推导出的缺口数。
- `last_success_at`：最近一次成功采集或处理时间。
- `last_failure_at`：最近一次失败采集或处理时间。
- `latency_ms`：受控外联或处理链路延迟。
- `error_rate`：限定窗口内错误率。
- `source_type`：`LOCAL_DB` / `FIXTURE` / `FAKE_SERVER` / `NO_EGRESS_SANDBOX` / `PUBLIC_SANDBOX_CANDIDATE` 等来源分类。
- `data_origin`：本地 DB、fixture、manual public outbound smoke 或后续受控源的来源说明。

必须保留三条解释边界：

- 图能显示，不等于数据可靠。
- 数据可靠，不等于可以交易。
- 公开行情可读，不等于 trading authorization。

## 8. O-3 MarketData Runtime Readiness API Plan

O-3 只规划 API，不实现 API。

现有事实：

- `GET /api/marketdata/readiness` 已存在，当前基于本地 `marketdata_bars` 与 `marketdata_ingestion_jobs/runs` 聚合 readiness summary。
- `GET /api/marketdata/bars` 已存在，返回本地 historical bars。
- existing readiness fields 已覆盖 status、freshnessStatus、sourceHealthStatus、sourceHealthReason、qualityStatusSummary、barCount、expectedBarCount、gapCount、unknownQualityCount、lastSuccessAt、lastFailureAt、backendSupportLevel、generatedAt。

规划原则：

- 如已有 readiness/source/quality 模型，优先扩展，不重复造接口。
- API 只返回 read model，不触发采集、不访问外部交易所、不读取 credential、不授权交易。
- 新 endpoint 只允许在 plan 中作为 candidate，后续必须单独做 API contract plan review。

候选接口仅作为 plan：

- `GET /api/marketdata/readiness`
- `GET /api/marketdata/sources`
- `GET /api/marketdata/gaps`
- `GET /api/marketdata/quality/overview`

## 9. O-4 MarketData Quality UI Plan

O-4 只规划前端数据质量页面或既有 `/marketdata` 区域增强，不实现 UI。

展示范围：

- K 线。
- 成交量。
- freshness。
- gap。
- source health。
- latency。
- error rate。
- last success / last failure。
- source type / data origin。

图表选型：

- K 线 / 成交量 / 买卖点：TradingView Lightweight Charts。
- 普通分析图：ECharts。

禁止 UI 语义：

- 不展示 AI signal ready。
- 不展示 DH runtime connected。
- 不展示 LIVE enabled。
- 不展示 trading-ready / provider-ready / private trading-ready。
- 不用 public source healthy 推导“可交易”。

## 10. O-5 Manual Public Outbound Smoke Plan

O-5 规划最后阶段的最小真实公开行情 smoke，不在本轮执行。

O-5 必须满足：

- 只能由手动 profile 启动。
- 不进入默认 CI。
- 不读 credential。
- 不访问 private WebSocket。
- 不下单、不撤单、不转账、不提现。
- 不触发 permission probe。
- smoke 结果只能证明 public endpoint 可读与解析路径可用，不能写成 trading authorization。

O-5 必须产出：

- 手动 profile 名称。
- 显式启停方式。
- endpoint allowlist 与 denylist。
- log redaction 规则。
- timeout / retry 上限。
- 失败降级与 fallback。
- 证据记录模板。

## 11. 安全边界

GateO 必须继承 GateN residual 与 no-real 边界：

- GateN 维持 `PARTIAL / ACCEPTED WITH EXPLICIT CI VISIBILITY RESIDUAL`，不得在 GateO 文档中提升为 `VERIFIED`。
- public marketdata 仅限只读、公开、无 credential、无签名。
- default profile、默认测试和默认 CI 仍保持 no-egress。
- manual public outbound profile 必须可关闭、可审计、可回滚。
- 所有 response / log / artifact 不得输出 credential-like material、签名、cookie、token、private key、raw credential payload 或 private provider response。
- public adapter 必须与 private trading adapter 分离。

P0 触发条件：

- 将 GateO 写成 implementation started。
- 将 public marketdata 写成 trading authorization。
- 将 LIVE / real provider / RealClient 写成 enabled / implemented。
- 允许默认测试真实外联。
- 读取或输出 credential material。

## 12. 测试策略

本轮 O-0 不运行 Maven / npm build / Playwright / pytest / mypy / ruff，原因是本轮只做 docs-only planning，不改 Java / TypeScript / Python / API / migration / CI workflow / runtime 配置。

后续批次测试策略：

- O-1 plan review：只做文档、endpoint allowlist/denylist、no-egress 规则与官方文档证据审查。
- O-2 plan review：审查字段语义、数据来源、质量状态和误导性 wording。
- O-3 API plan review：审查是否复用现有 `/api/marketdata/readiness`，并确认 candidate endpoint 不被写成当前 API。
- O-4 UI plan review：审查 UI copy 是否避免 real-ready / live-ready / trading-authorized。
- O-5 manual smoke plan review：确认 smoke 不进默认 CI、不读 credential、不访问 private endpoint。
- O-FREEZE：只在所有前置 planning/review 证据完成后冻结；任何实现或真实外联都必须有单独授权。

## 13. 回滚与降级策略

文档回滚：

- 还原 `docs/current/GATEO_PLAN.md`、`README.md`、`docs/current/README.md`、`docs/current/STATUS.md`、`docs/current/ROADMAP.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md` 的本轮 diff 即可。

后续实现前必须规划的运行时降级：

- manual public outbound profile 可一键关闭。
- 外联失败默认降级为本地 DB / fixture / no-egress sandbox diagnostic。
- source health failure 不触发交易，不触发 order/cancel/transfer/withdraw，不触发 permission probe。
- latency / error rate 超阈值时只标记数据源不可用或 degraded，不提升为 trading-ready。

## 14. 验收标准

O-0 本轮验收标准：

- `docs/current/GATEO_PLAN.md` 新增完成。
- `STATUS.md` / `ROADMAP.md` / `TESTING.md` / `WORKLOG.md` / `docs/current/README.md` / root `README.md` 同步 GateO planning-only 状态。
- GateO 明确为 planning-only。
- GateO implementation 仍 `NOT STARTED`。
- LIVE / AI / DH runtime / RealClient / real provider / real permission probe 仍 `DISABLED` / `NOT STARTED` / `NOT_IMPLEMENTED`。
- public marketdata readiness 未写成 trading authorization。
- 未修改代码、CI、API、migration、页面或 E2E。
- 未读取或输出 credential material。
- 文档正文中文为主。

O-FREEZE 后续验收标准：

- O-0 plan 完成。
- O-1 public outbound plan 完成。
- O-2 data quality center plan 完成。
- O-3 readiness API plan 完成。
- O-4 UI plan 完成。
- O-5 manual public outbound smoke 规则明确。
- 无真实 credential。
- 无 LIVE。
- 无 private trading adapter。
- 无 DH runtime。
- 无 AI trading。
- CI 状态不被误写。
- 文档状态一致。

## 15. 风险清单 P0/P1/P2/P3

P0：

- 当前未发现 P0。
- 后续若将 GateO 写成 implementation started、将 public marketdata 写成 trading authorization、将 LIVE / real provider / RealClient 写成 enabled / implemented、允许默认测试真实外联、读取或输出 credential material，均为 P0。

P1：

- public/private adapter 边界不清。
- 默认 no-egress 与 manual public outbound profile 边界不清。
- O-5 smoke 进入默认 CI。
- 未明确 public REST only。
- 未明确禁止 private endpoint。

P2：

- Data Quality Center 字段设计不完整。
- 前端 source/status 文案可能误导。
- API plan 与现有 marketdata API 重复。
- 官方文档引用入口未列清。

P3：

- 文档入口重复。
- 中英混排不统一。
- 历史 GateN residual 说明不够集中。

## 16. 后续任务建议

推荐下一步：

1. `NQ-GATEO-O1-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND-PLAN-REVIEW`：只做 O-1 plan review，官方文档、allowlist/denylist、manual profile、no-egress default 与 rollback 先冻结。
2. `NQ-GATEO-O2-DATA-QUALITY-CENTER-PLAN-REVIEW`：只做字段、状态、来源、freshness/gap/latency/error rate 语义审查。
3. `NQ-GATEO-O3-MARKETDATA-RUNTIME-READINESS-API-PLAN-REVIEW`：只做 API contract plan，优先复用现有 readiness。
4. `NQ-GATEO-O4-MARKETDATA-QUALITY-UI-PLAN-REVIEW`：只做 UI 信息架构与 wording 审查。
5. `NQ-GATEO-O5-MANUAL-PUBLIC-OUTBOUND-SMOKE-PLAN-REVIEW`：只做手动 smoke 规则审查，不进默认 CI。

不建议从 O-0 直接进入 implementation。

## 17. 本轮未做事项

- 未修改 `backend/**`、`frontend/**`、`research/**`、`scripts/**`、`deploy/**`、`.github/**` 或 `backend/**/db/migration/**`。
- 未新增或修改 migration。
- 未新增 API。
- 未新增页面。
- 未新增 E2E。
- 未修改 CI workflow。
- 未实现 public outbound。
- 未调用真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未读取或输出 credential material。
- 未开启 LIVE。
- 未接 AI。
- 未接 DH runtime。
- 未实现 RealClient。
- 未实现 real provider。
- 未实现真实 permission probe。
- 未下单、撤单、转账或提现。
- 未把 GateO 写成 implementation started。
