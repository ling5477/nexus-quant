# GateO Plan：公开行情受控外联与数据质量运行化阶段

## 1. 当前事实

本轮任务 `NQ-GATEO-PLAN-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND` 是 GateO O-0 planning baseline。

当前结论：O-0 planning baseline 仍为 `PASS`（通过）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现）；O-1 controlled public outbound guard baseline 已冻结为 `PASS`（通过）/ `ACCEPTED`（已接受）/ `FROZEN`（已冻结）；O-2 Data Quality Center baseline 已冻结为 `PASS`（通过）/ `ACCEPTED`（已接受）/ `FROZEN`（已冻结）；O-3 MarketData Runtime Readiness API plan 已完成为 `PASS`（通过）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现），O-3B backend read-only API implementation 已完成并接受为 `COMPLETED`（已完成）/ `ACCEPTED`（已接受），O-3E freeze review 已 `PASS`（通过）/ `ACCEPTED`（已接受）/ `FROZEN`（已冻结）；O-4 MarketData Quality UI baseline 已冻结为 `FROZEN`（已冻结）/ `ACCEPTED`（已接受），O-4A UI contract plan review 已 `PASS`（通过）/ `ACCEPTED`（已接受），O-4B read-only UI implementation 已 `COMPLETED`（已完成）/ `ACCEPTED`（已接受），O-4E freeze review 已 `PASS`（通过）/ `ACCEPTED`（已接受）；O-5 manual public outbound smoke plan 已完成为 `COMPLETED`（已完成）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现），O-5A review 已 `PASS`（通过）/ `ACCEPTED`（已接受），O-5B runner binding plan 已 `PASS`（通过）/ `ACCEPTED`（已接受），O-5B-R1 runner binding implementation 已 `IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `COMMITTED`（已提交，commit `35413109`），O-5B-R2 runner binding review 已 `PASS`（通过）/ `ACCEPTED`（已接受）。GateO stage 仍为 `NOT COMPLETED`（未完成），manual smoke execution 后续为 `ALLOWED / MANUAL PUBLIC READONLY ONLY / NOT EXECUTED`（允许 / 仅手动公开只读 / 未执行），O-5 smoke execution / O-5D DataOrigin.PUBLIC_OUTBOUND decision / O-FREEZE 仍为 `NOT STARTED`（未开始）。

当前上游证据：

- GateJ：`VERIFIED`（已验证）。
- GateK：`VERIFIED`（已验证）。
- GateM：`VERIFIED`（已验证）。
- GateN：`PARTIAL / ACCEPTED WITH EXPLICIT CI VISIBILITY RESIDUAL`（部分验证 / 已显式接受 CI 可见性残留）。GateN tag / archive / local freeze validation / later dev CI 存在，但 `nq-gaten-freeze` tagged commit `361d2ac7bb595f72067b0e2c2d0485361e9a0540` 的 direct CI run 未定位，不能把 GateN 写成完整 `VERIFIED`。
- `NQ-GATES-JKMN-FREEZE-CI-EVIDENCE-RECONCILIATION` 已完成并提交，是 GateO planning-only 入场边界证据；本文件后续只记录 GateO O-0 / O-1 的计划与审查状态，不把瞬时 `HEAD` 写成长期事实。

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
| O-1 | Public MarketData Controlled Outbound Implementation | `PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结） | 冻结 public marketdata outbound 最小抽象、manual profile / feature flag、allowlist/denylist、disabled fallback、redaction/log summary、bounded timeout/retry、endpoint authority escape guard 与 Data Quality linkage | 不进默认 CI、不执行真实 public smoke、不接真实 provider / RealClient / permission probe / LIVE |
| O-2 | Data Quality Center Implementation | `PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结） | 冻结 source health / freshness / gap / latency / error category / data origin 的后端纯模型、mapper 和单元测试 baseline | 不新增表、不新增 API、不改 UI、不真实外联 |
| O-3 | MarketData Runtime Readiness API | `FROZEN / ACCEPTED`（已冻结 / 已接受） | 基于现有 readiness/source/quality 模型收口 API；O-3B 已扩展现有 `GET /api/marketdata/readiness` read model，O-3E 已冻结该 read-only API baseline | 不重复造接口、不新增 migration、不改 frontend、不真实外联 |
| O-4 | MarketData Quality UI Plan / O-4B Read-only UI / O-4E Freeze Review | `FROZEN / ACCEPTED`（已冻结 / 已接受）；O-4A `PASS / ACCEPTED`（通过 / 已接受）；O-4B `COMPLETED / ACCEPTED`（已完成 / 已接受）；O-4E `PASS / ACCEPTED`（通过 / 已接受） | 已规划、评审、实现并冻结数据质量只读 UI；O-4B 已在既有 `/marketdata` 实现只读 Quality / Readiness 展示、类型补齐和 mocked smoke | O-4B/O-4E 未改 backend/API/migration，未做 O-5 public smoke，不做 mock AI/DH/LIVE |
| O-5 | Manual Public Outbound Smoke Plan / O-5B Runner Binding | `COMPLETED / PLAN ONLY / NOT IMPLEMENTED`（已完成 / 仅规划 / 未实现）；O-5A `PASS / ACCEPTED`；O-5B runner binding plan `PASS / ACCEPTED`；O-5B-R1 runner binding implementation `IMPLEMENTED / SELF-REVIEWED / COMMITTED`；O-5B-R2 runner binding review `PASS / ACCEPTED` | 已规划最后阶段手动 profile 的最小 public outbound smoke 安全方案，并已绑定且接受 test-only manual runner；manual smoke execution 后续为 `ALLOWED / MANUAL PUBLIC READONLY ONLY / NOT EXECUTED` | 不进入默认 CI、不读 credential、不执行真实 HTTP；本轮不执行 smoke |
| O-FREEZE | GateO Freeze Criteria | `PLANNED / NOT STARTED` | 明确 GateO 冻结验收 | 不把 planning 写成 implementation |

## 6. O-1 Public MarketData Controlled Outbound

O-1 当前已完成最小 implementation 并在 freeze review 中被接受为 controlled public outbound guard baseline；该冻结只代表受控 public marketdata outbound guard 与 fake-server/no-egress 测试闭环已冻结，不代表 GateO completed，不代表 O-5 manual real public smoke 已执行，也不代表真实 provider、RealClient、real permission probe、LIVE 或 trading authorization 已启动。

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

- 默认 profile / production path HTTP client；O-1 仅允许 manual profile 下受 policy 保护的 JDK client。
- default CI public outbound。
- credential lookup。
- permission probe。
- trading authorization。

### 6.1 O-1 Plan Review（2026-07-01）

任务：`NQ-GATEO-O1-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND-PLAN-REVIEW`。

Review verdict：`FAIL`（未通过）/ `PLAN REVIEWED`（计划已审查）/ `NOT IMPLEMENTED`（未实现）/ `IMPLEMENTATION BLOCKED`（实现阻塞）。

结论：O-1 的方向是安全的，已经保持 public-only、no credential、no signed route、默认 no-egress 和 trading authorization 禁止边界；但当前 O-1 仍只是高层 checklist，没有形成可进入 implementation 的受控外联基线。O-1 implementation 不允许开始。

已审查证据：

- `docs/current/GATEO_PLAN.md` O-0 baseline 与 O-1 高层规划。
- `README.md`、`docs/current/README.md`、`docs/current/STATUS.md`、`docs/current/ROADMAP.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`。
- `docs/current/NQ_GATES_JKMN_FREEZE_CI_EVIDENCE_RECONCILIATION.md` 中 GateN residual 与 GateO planning-only 入场边界。
- 现有 `GET /api/marketdata/readiness`、MarketData readiness service / repository、frontend marketdata client 与 `.github/workflows/ci.yml` no-outbound / redaction / secret-scan baseline。
- GateN historical official-docs inventory 与 no-egress sandbox evidence，只作为历史参考，不替代 GateO O-1 的 current review baseline。

P0 findings：0。

P1 findings：

1. Official docs baseline 不完整：当前 O-1 只要求“协议事实必须来自交易所官方文档”和“产出官方文档入口与版本记录”，但没有列出 GateO O-1 自身的官方文档名称、用途、访问日期、版本/维护状态、引用范围和不得引用范围。
2. Public REST allowlist 不完整：当前 O-1 只写 `OKX / Binance public REST only`，没有冻结候选类别 allowlist。后续 O-1 必须至少明确 OHLCV / klines / candlesticks、ticker、instrument / symbol metadata、exchange status / server time、market capability metadata；不得把 private WebSocket、signed route 或 user data stream 夹入 allowlist。
3. Private endpoint denylist 不完整：当前 O-1 已禁止 account / order / balance / withdraw / transfer、private WebSocket、signed route 和 permission probe，但尚未完整列出 cancel、amend、position、deposit、subaccount、API key / secret / passphrase、signed request、anything requiring authentication 等禁止类别。
4. Manual profile / feature flag 不完整：当前 O-1 只要求显式 profile，尚未给出 profile / feature flag 名称、默认关闭值、关闭路径、不得读取 credential、不得启用 LIVE、不得接 private adapter、不得进入默认 CI 的验收表达。
5. Redaction / rollback 规则不完整：当前 O-1 只列出 request/response redaction 与 rollback / disable switch 的必产项，尚未冻结“不记录完整 request/response body、不记录 raw headers、不记录 query string、不上传 raw response artifact、日志只保留 source / endpoint category / status / error category / latency / traceId”的最小规则。

P2 findings：

1. Rate limit / timeout / retry 只有方向，没有默认 timeout、有限 retry 次数、backoff/circuit breaker 条件、`429 -> DEGRADED / RATE_LIMITED`、`5xx -> ERROR / TEMPORARY_FAILURE`、stale 数据不可误判 fresh 的验收规则。
2. Data Quality linkage 已列 source health / freshness / gap / latency / error rate，但尚未把 O-1 outbound result 明确映射到 O-2 source health / freshness / gap / latency / error rate 的降级语义。
3. Existing API / adapter reuse 仍需细化：现有 `/api/marketdata/readiness` 是 DB-only read model，可优先扩展质量语义；历史 OKX / Binance `HistoricalKlineAdapter` 是 network-capable legacy path，不能直接写成 current real provider enabled。
4. O-1 implementation acceptance criteria 需要补齐无 credential、无 signed request、无 private endpoint、默认 no-egress 不破坏、CI 不真实外联、manual profile 明确、allowlist/denylist 生效、redaction 生效、failure downgrade 生效、public marketdata 不等于 trading authorization 的逐项验收。

P3 findings：

- O-1 与 O-5 都提到 manual public outbound smoke。后续修订应把 O-1 限定为“设计与实现准入 plan”，把真实 public outbound smoke 留到 O-5 手动阶段，避免读者误以为 O-1 会执行真实外联。

分项 verdict：

| 项目 | Verdict | 说明 |
| --- | --- | --- |
| Official docs baseline | `FAIL`（未通过） | 没有 GateO O-1 自身的官方文档入口、访问日期、版本/维护状态和引用范围。 |
| Public REST allowlist | `FAIL`（未通过） | 只有 public REST only 方向，没有冻结具体公开只读类别。 |
| Private endpoint denylist | `CONDITIONAL FAIL`（有条件未通过） | 已有禁止方向，但 denylist 不完整。 |
| Manual profile / no-egress | `CONDITIONAL PASS`（有条件通过） | 默认 no-egress 边界清楚；manual profile 名称、flag 与关闭路径缺失。 |
| Redaction / rollback | `CONDITIONAL FAIL`（有条件未通过） | 已要求 redaction/rollback，但最小日志字段、raw artifact 禁止和 query/header 脱敏规则未冻结。 |
| Rate limit / timeout / retry | `FAIL`（未通过） | 缺少可验收的默认值、有限重试、429/5xx 降级语义。 |
| Data quality linkage | `CONDITIONAL PASS`（有条件通过） | O-2 字段方向正确，但 O-1 result 到 source health 语义仍需映射。 |
| Existing API / adapter boundary | `CONDITIONAL PASS`（有条件通过） | 已声明优先复用 readiness；仍需避免复用 legacy network-capable adapter 时越过 GateO guard。 |
| Acceptance criteria | `FAIL`（未通过） | 缺少 O-1 implementation 前逐项验收清单。 |

是否允许 O-1 implementation start：**NO**（不允许）。下一步必须先修订 O-1 plan，使 P1=0，并重新执行 `NQ-GATEO-O1-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND-PLAN-REVIEW`。

### 6.2 O-1 Plan Revision（2026-07-01）

任务：`NQ-GATEO-O1-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND-PLAN-REVISION`。

Revision status：`REVISION COMPLETED`（修订已完成）/ `READY FOR REVIEW`（可重新审查）/ `NOT IMPLEMENTED`（未实现）。本节是 O-1 implementation 之前的历史 plan revision baseline，已由 §6.3 的 O-1 最小实现消费；不应再把本节状态解释为当前 O-1 未实现。

#### 6.2.1 Official Docs Baseline

O-1 协议事实只能来自 OKX / Binance 官方文档入口。本轮只记录官方文档 baseline，不复制大段官方协议正文，不调用任何交易所 API endpoint，不访问需要登录或 API key 的页面。

| Exchange | Official docs entry | Access date | O-1 usage | Reference scope | Explicitly out of scope |
| --- | --- | --- | --- | --- | --- |
| OKX | OKX API v5 docs：<https://www.okx.com/docs-v5/en/> | 2026-07-01 | public REST endpoint 定义、public WebSocket 是否后置、rate limit、instrument metadata、kline/candlestick、ticker、server time / exchange status、error code / response shape | 仅引用 public data、market data、status 与错误响应相关官方章节；implementation 前必须重新打开官方页面确认 path、参数、分页、时间戳单位、频率限制与废弃状态 | account、balance、order、cancel、amend、positions、wallet、transfer、withdraw、private WebSocket、API key / signature、permission probe real execution |
| Binance | Binance Spot REST market data endpoints：<https://developers.binance.com/docs/binance-spot-api-docs/rest-api/market-data-endpoints> | 2026-07-01 | public instruments/symbol metadata、OHLCV / kline、ticker、order book / aggregated trades candidate、权重/限制和 response shape | 仅引用 Spot public market data endpoints；implementation 前必须重新确认 request weight、symbol 参数、时间窗口、limit 与错误语义 | signed account/order endpoints、user data stream、wallet、transfer、withdraw、margin/loan、API key validation、permission probe real execution |
| Binance | Binance Spot REST general endpoints：<https://developers.binance.com/docs/binance-spot-api-docs/rest-api/general-endpoints> | 2026-07-01 | public server time、exchange status / system status 候选、通用 response / error handling 补充 | 仅引用无需 credential 的 general endpoint；如官方文档将某状态 endpoint 标为非 public、需 API key 或迁移到 private/SAPI 受限入口，则 O-1 自动禁止 | 任何 signed endpoint、需要 API key 的 endpoint、账号或钱包类 general/SAPI endpoint |
| Binance | Binance Spot WebSocket streams：<https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams> | 2026-07-01 | 仅用于确认 public WebSocket 后置范围和不得误入 O-1 最小实现 | O-1 只记录 WebSocket 后置，不实现 WebSocket，不进入 O-1 最小 public REST implementation | private/user data stream、listenKey、account/order update stream |

引用规则：

- 禁止把第三方 SDK、博客、旧经验、历史 spike 或历史 live-0 证据当作协议事实来源。
- GateN historical official-docs inventory 只能作为查找线索；GateO O-1 implementation 前必须重新以本节 official docs baseline 为准。
- 若官方文档之间存在冲突，以 implementation 当日官方页面为准，并在 O-1 review 中记录冲突与取舍。

#### 6.2.2 Public REST Allowlist

O-1 最小实现优先只允许 OHLCV / instruments / ticker / server time。order book、recent trades / aggregated trades 即使 public，也默认后置，除非后续 review 明确纳入。public WebSocket 默认后置，不进入 O-1 最小实现。O-1 不允许任何 signed endpoint。

| Exchange | Category | Official doc source | Allowed endpoint family | Purpose | Auth required | O-1 status |
| --- | --- | --- | --- | --- | --- | --- |
| OKX | public server time | OKX API v5 docs / Public Data | `/api/v5/public/time` | 校验交易所时间与本地采集时间差 | No | `MINIMUM_ALLOWLIST` |
| OKX | public exchange status | OKX API v5 docs / Status | `/api/v5/system/status` | 只读判断官方系统状态，作为 source health 降级输入 | No | `MINIMUM_ALLOWLIST` |
| OKX | public instruments metadata | OKX API v5 docs / Public Data | `/api/v5/public/instruments` | 获取 SPOT instrument metadata、tick/lot/状态等候选字段 | No | `MINIMUM_ALLOWLIST` |
| OKX | public ticker | OKX API v5 docs / Market Data | `/api/v5/market/ticker`、`/api/v5/market/tickers` | 获取只读 ticker 快照 | No | `MINIMUM_ALLOWLIST` |
| OKX | public OHLCV / kline / candlestick | OKX API v5 docs / Market Data | `/api/v5/market/candles`、`/api/v5/market/history-candles` | 获取 public OHLCV K 线数据 | No | `MINIMUM_ALLOWLIST` |
| OKX | public trades aggregated summary | OKX API v5 docs / Market Data | `/api/v5/market/trades` | 只读 recent trades summary，若纳入必须限制窗口、脱敏并映射为 diagnostic | No | `OPTIONAL_LATER` |
| OKX | public order book snapshot | OKX API v5 docs / Market Data | `/api/v5/market/books`、`/api/v5/market/books-lite` | 只读 order book snapshot，用于 future quality diagnostics | No | `OPTIONAL_LATER` |
| Binance | public server time | Binance Spot general endpoints | `/api/v3/time` | 校验交易所时间与本地采集时间差 | No | `MINIMUM_ALLOWLIST` |
| Binance | public exchange status | Binance Spot general endpoints | public system / exchange status endpoint only if official docs confirm unauthenticated access | 只读判断官方系统状态，作为 source health 降级输入 | No, must be confirmed | `CANDIDATE_REVIEW_REQUIRED` |
| Binance | public instruments / symbols metadata | Binance Spot market data endpoints | `/api/v3/exchangeInfo` | 获取 symbol metadata、filters、trading status 等候选字段 | No | `MINIMUM_ALLOWLIST` |
| Binance | public ticker | Binance Spot market data endpoints | `/api/v3/ticker/price`、`/api/v3/ticker/24hr` | 获取只读 ticker 快照 | No | `MINIMUM_ALLOWLIST` |
| Binance | public OHLCV / kline / candlestick | Binance Spot market data endpoints | `/api/v3/klines`、`/api/v3/uiKlines` | 获取 public OHLCV K 线数据 | No | `MINIMUM_ALLOWLIST` |
| Binance | public trades aggregated summary | Binance Spot market data endpoints | `/api/v3/aggTrades` | 只读 aggregated trades summary，若纳入必须限制窗口、脱敏并映射为 diagnostic | No | `OPTIONAL_LATER` |
| Binance | public order book snapshot | Binance Spot market data endpoints | `/api/v3/depth` | 只读 order book snapshot，用于 future quality diagnostics | No | `OPTIONAL_LATER` |

Allowlist enforcement rules：

- 新增 endpoint 必须先证明属于 official public unauthenticated REST，并在 plan review 中补入本表。
- `OPTIONAL_LATER` 不属于 O-1 最小实现，不能被 implementation 默认带入。
- public WebSocket、private WebSocket、user data stream、listenKey 或任何 signed route 均不在 O-1 allowlist。
- public marketdata readiness 只能表达数据诊断状态，不能表达 trading authorization。

#### 6.2.3 Private Endpoint Denylist

O-1 denylist 采用 fail-closed：任何未列入 allowlist 的 endpoint 都默认禁止；任何需要 credential / signature / API key / secret / passphrase 的 endpoint 自动禁止。

禁止类别：

- account。
- balance。
- order。
- cancel。
- amend。
- positions。
- trade private history。
- wallet。
- transfer。
- withdraw。
- deposit。
- subaccount。
- margin / leverage / loan。
- private WebSocket。
- user data stream / listenKey。
- API key validation。
- permission probe real execution。
- signed request。
- any endpoint requiring API key / secret / passphrase / signature。

补充规则：

- permission probe real execution 不属于 GateO；真实 permission probe 必须另起 Gate 和安全审查。
- 历史 OKX / Binance private adapter、credential governance、permission probe no-real baseline 不能被 O-1 转译为 current real provider enabled。
- 若某官方页面同时包含 public 与 private sections，O-1 只允许引用 public unauthenticated REST 部分。

#### 6.2.4 Manual Profile / Feature Flag

O-1 固定采用以下名称：

```text
spring.profiles.active=public-marketdata-manual
NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=false
```

启用条件：

1. 默认 local / test / CI / paper / freeze profile 下 outbound disabled。
2. 只有 `public-marketdata-manual` profile 与 `NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=true` 同时成立，后续 O-1 implementation 才能允许 public outbound candidate。
3. manual profile 不读取 credential。
4. manual profile 不开启 LIVE。
5. manual profile 不启用 RealClient / real provider。
6. manual profile 不启用 private adapter。
7. manual profile 可一键关闭：移除 `public-marketdata-manual` profile 或把 `NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=false`。
8. 关闭后 fallback 到 `LOCAL_DB` / `FIXTURE` / `FAKE_SERVER`。
9. manual profile 不进入默认 CI。
10. O-5 之前不得执行真实 public smoke；O-1 只做设计和实现准入 plan。

#### 6.2.5 Redaction / Logging Minimum Rules

O-1 后续实现必须最小化日志与 artifact：

1. 不记录 raw request body。
2. 不记录 raw response body。
3. 不记录 raw headers。
4. 不记录 full query string。
5. 不记录 credential material。
6. 即使 public endpoint 不需要 credential，也必须防止未来 header/token 混入日志。
7. 允许记录字段仅限：`exchange`、`source_type`、`endpoint_category`、`status_code`、`error_category`、`latency_ms`、`trace_id`、`request_id`、`data_window`、`row_count`。
8. 不上传 raw response artifact。
9. 不把 public outbound response 原文写入 `TESTING.md` / `WORKLOG.md`。
10. 错误日志必须脱敏，只保留错误类别、状态码、endpoint category 和 trace/request id。

#### 6.2.6 Rollback / Disable Rules

O-1 后续实现必须具备无 migration 回滚路径：

1. 关闭 feature flag：`NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=false`。
2. 下线 profile：移除 `public-marketdata-manual`。
3. source disable：将 public source 标为 `DISABLED`，停止新的 public outbound candidate。
4. adapter fallback：回退到 `LOCAL_DB` / `FIXTURE` / `FAKE_SERVER`。
5. stale / error / degraded 状态降级：只影响 Data Quality diagnostics，不影响交易链路。
6. public outbound 失败不影响 Paper / no-real baseline。
7. public outbound 失败不得启用 private trading。
8. public outbound 失败不得让 readiness 变成 trading-ready。
9. 回滚后可继续使用 `LOCAL_DB` / `FIXTURE` / `FAKE_SERVER`。
10. 回滚不需要 migration，不修改历史 migration。

#### 6.2.7 Rate Limit / Timeout / Retry Safety Baseline

默认值是 NQ safety baseline，不是交易所协议事实；最终实现必须以 official docs 的频率限制为准。默认值只用于 O-1 设计审查与 future implementation 起点。

| Item | Default | Failure / status mapping | Boundary |
| --- | --- | --- | --- |
| connect timeout | 3s | `TIMEOUT / DEGRADED` | 不得无限等待 |
| read timeout | 5s | `TIMEOUT / DEGRADED` | 不得阻塞 Paper/no-real baseline |
| total request timeout | 8s | `TIMEOUT / DEGRADED` | 含连接、读取和解析预算 |
| retry | max 2 retries | retry exhausted -> `TEMPORARY_FAILURE / ERROR` | 不得无限重试 |
| backoff | 500ms / 1000ms 或指数退避等价实现 | backoff exhausted -> degraded/error | 不得绕过 no-egress |
| 429 | no immediate aggressive retry | `RATE_LIMITED / DEGRADED` | 遵守官方限制，不扩大 endpoint |
| 408 / timeout | bounded retry only | `TIMEOUT / DEGRADED` | 不得访问 private endpoint |
| 5xx | bounded retry only | `TEMPORARY_FAILURE / ERROR` | 不得提升为 healthy |
| malformed response | no retry unless review accepts | `INVALID_RESPONSE` | 不上传 raw response artifact |
| stale data | no success promotion | `STALE` | stale 不等于 fresh |
| gap detected | no success promotion | `GAP` | gap 不等于 healthy |
| disabled | no outbound | `DISABLED` | flag/profile 关闭后必须立即生效 |

Retry 不能绕过 no-egress、不能访问 private endpoint、不能触发 signed request、不能把 public endpoint 失败升级为 trading-ready。

#### 6.2.8 O-1 To O-2 Data Quality Linkage

O-1 的 outbound result 只作为 O-2 Data Quality Center 的诊断输入。数据成功拉取不等于数据可靠；数据可靠不等于可以交易；marketdata readiness 不等于 trading authorization。

| O-1 event/result | O-2 field/status |
| --- | --- |
| success | `source_health=HEALTHY` |
| latency high | `source_health=DEGRADED` |
| 429 | `source_health=RATE_LIMITED` |
| timeout | `source_health=DEGRADED / TIMEOUT` |
| 5xx | `source_health=ERROR` |
| stale kline | `freshness=STALE` |
| missing interval | `gap_count > 0` |
| disabled flag | `source_status=DISABLED` |
| fallback used | `data_origin=LOCAL_DB / FIXTURE / FAKE_SERVER` |
| malformed response | `source_health=ERROR` and `error_category=INVALID_RESPONSE` |
| public endpoint not allowlisted | `source_status=DISABLED` and `error_category=ENDPOINT_NOT_ALLOWLISTED` |

#### 6.2.9 Existing API / Legacy Adapter Reuse Boundary

只读梳理结果：

1. 现有 MarketData API 包括 `GET /api/marketdata/readiness`、`GET /api/marketdata/bars`、`POST /api/marketdata/ingestion-jobs`、`GET /api/marketdata/ingestion-jobs`、`GET /api/marketdata/ingestion-jobs/{jobId}`、`GET /api/marketdata/ingestion-jobs/{jobId}/runs`、`POST /api/marketdata/ingestion-jobs/{jobId}/run-once`、`GET /api/marketdata/datasets`、`POST /api/marketdata/datasets`、`GET /api/marketdata/datasets/{datasetId}`、`POST /api/marketdata/datasets/{datasetId}/refresh-quality`，以及 test/local fixture ingestion 入口。
2. 现有 readiness/source/quality 模型存在：`GET /api/marketdata/readiness` 当前基于本地 `marketdata_bars` 与 `marketdata_ingestion_jobs/runs` 聚合 `status`、`freshnessStatus`、`sourceHealthStatus`、`sourceHealthReason`、`qualityStatusSummary`、`barCount`、`expectedBarCount`、`gapCount`、`unknownQualityCount`、`lastSuccessAt`、`lastFailureAt`、`backendSupportLevel`、`generatedAt`。
3. O-1 implementation 应优先扩展现有 readiness/source/quality 语义或在 O-2/O-3 规划后补字段，不应重复造 readiness API；任何新 endpoint 仍需单独 API contract plan review。
4. 历史 OKX / Binance `HistoricalKlineAdapter`、历史 spike、历史 live-0 和 GateN fixture evidence 只能作为 historical evidence。
5. 不能把历史实盘 0、legacy adapter 或 network-capable historical kline path 写成当前 real provider enabled。
6. 不能把 legacy adapter 直接变成 GateO public outbound implementation；后续若复用，必须先套入 O-1 allowlist、manual profile、feature flag、redaction、timeout/retry、no-egress 与 rollback guard。
7. 不能重复造 readiness API，除非 O-3 证明现有模型无法承载，并完成单独 review。

#### 6.2.10 O-1 Implementation Acceptance Criteria

O-1 implementation 只有在后续 review 通过后才允许开始；implementation 结束时至少逐项满足：

1. official docs baseline complete。
2. allowlist complete。
3. denylist complete。
4. manual profile fixed：`public-marketdata-manual`。
5. feature flag fixed：`NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=false` default。
6. default no-egress intact。
7. no credential。
8. no signed request。
9. no private endpoint。
10. no LIVE。
11. no RealClient。
12. no permission probe real execution。
13. no CI public outbound。
14. redaction rules documented and implemented。
15. rollback rules documented and implemented。
16. rate limit / timeout / retry baseline documented and bounded。
17. O-2 mapping documented。
18. existing API reuse decision documented。
19. O-5 manual smoke not started。
20. public marketdata 不等于 trading authorization。

#### 6.2.11 P1 / P2 / P3 Closure

P1 closure：

| Finding | Closure in revision |
| --- | --- |
| Official docs baseline 不完整 | §6.2.1 固定 OKX / Binance official docs entry、用途、访问日期、引用范围和禁止来源。 |
| Public REST allowlist 不完整 | §6.2.2 分交易所列出 minimum allowlist、optional later 与 candidate review required。 |
| Private endpoint denylist 不完整 | §6.2.3 固定 fail-closed denylist 和 credential/signature 自动禁止规则。 |
| Manual profile / feature flag 不完整 | §6.2.4 固定 `public-marketdata-manual` 和 `NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=false`。 |
| Redaction / rollback 规则不完整 | §6.2.5 / §6.2.6 固定最小日志字段、raw artifact 禁止和关闭/回滚路径。 |

P2 closure：

| Finding | Closure in revision |
| --- | --- |
| Rate limit / timeout / retry 默认值不足 | §6.2.7 固定 NQ safety baseline、错误映射、有限 retry 和 no-egress/private endpoint 边界。 |
| O-1 result 到 O-2 映射不足 | §6.2.8 固定 outbound result 到 source health / freshness / gap / source status / data origin 的映射。 |
| existing API / legacy adapter 复用边界不足 | §6.2.9 记录现有 API、readiness 模型、legacy adapter historical-only 与复用条件。 |
| acceptance criteria 不够逐项 | §6.2.10 固定 20 条 implementation acceptance criteria。 |

P3 closure：

- O-1 只做 design / implementation entry plan 和后续最小 implementation 准入；真实 public outbound smoke 保留到 O-5 manual stage，不在 O-1 执行，不进入默认 CI。

### 6.3 O-1 Implementation（2026-07-01）

任务：`NQ-GATEO-O1-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND-IMPLEMENTATION`。

Implementation status：`IMPLEMENTED`（已实现）/ `P1 FIXED`（P1 已修复）/ `ACCEPTED BY FREEZE REVIEW`（已由冻结复核接受）。本状态只覆盖 O-1 最小受控出站边界和 endpoint authority escape P1 修复，不表示 GateO stage completed，也不表示 O-5 manual real public smoke 已执行。

已实现范围：

1. 新增 `PublicMarketDataOutboundClient` 最小抽象，调用方必须先构造脱敏 `PublicMarketDataOutboundRequest`，不得在 service 中散写 URL。
2. 新增 `PublicMarketDataOutboundPolicy` 与 `PublicMarketDataEndpointCategory`，allowlist 仅覆盖 `SERVER_TIME`、`INSTRUMENTS`、`TICKER`、`OHLCV`；`ORDER_BOOK`、`RECENT_TRADES`、`PUBLIC_WEBSOCKET` 默认后置；account / balance / order / cancel / amend / positions / wallet / transfer / withdraw / deposit / subaccount / private WebSocket / signed request / API key validation / real permission probe / authenticated / unknown 均 fail-closed。
3. 新增 `DisabledPublicMarketDataOutboundClient` 作为默认 fallback：feature flag 关闭或缺失时不创建 HTTP client、不访问网络、不读取 credential，返回 `DISABLED` 并保留 `LOCAL_DB` / `FIXTURE` / `FAKE_SERVER` fallback origin。
4. 新增 `JdkPublicMarketDataOutboundClient` 作为唯一 manual profile 下的 public REST client：只由 `public-marketdata-manual` profile 且 `nq.public-marketdata.outbound.enabled=true` 装配；每次请求和每次 retry 前都重新经过 policy；bounded timeout / retry / backoff 默认值为 connect 3s、read 5s、total request 8s、max retries 2、500ms / 1000ms backoff。
5. 新增 `PublicMarketDataOutboundResult`、`PublicMarketDataOutboundErrorCategory`、`PublicMarketDataQualitySummary`、`PublicMarketDataSourceHealthMapper`，把 success / high latency / 429 / timeout / 5xx / malformed / stale / gap / disabled / fallback 映射到 source health、freshness、gap、source status 和 data origin；legacy O-1 `PublicMarketDataQualitySummary.tradingAuthorization` 固定为 false，O-2 `DataQualitySummary` 不暴露 authorization 字段。
6. 新增 `PublicMarketDataRedactor` 与 `PublicMarketDataLogSummary`，日志白名单只保留 exchange、source type、endpoint category、status code、error category、latency、trace/request id、data window、row count；不携带 raw request body、raw response body、raw headers、full query string、credential、signature、token 或 raw response artifact。
7. 新增 `PublicMarketDataOutboundConfiguration` 与 `application-public-marketdata-manual.yml`；默认 `application.yml` 中 `nq.public-marketdata.outbound.enabled=false`，manual profile 通过 `NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED` 显式打开。
8. 扩展 `EnvSafetyGuardConfiguration` / `EnvSafetyValidator`：`public-marketdata-manual` profile 禁止 LIVE、AI、DH runtime、real provider、RealClient 和 real exchange；`NQ_PUBLIC_MARKETDATA_BASE_URL` 仅作为 no-outbound/test/CI 边界下的 endpoint fact 参与安全检查，不读取 credential。

验证结果：

| Command | Result | Scope |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-adapter-api,nq-app -am "-Dtest=PublicMarketDataOutboundPolicyTest,JdkPublicMarketDataOutboundClientTest,PublicMarketDataOutboundConfigurationTest,EnvSafetyValidatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | `BUILD SUCCESS`（构建通过） | O-1 policy/client/fake-server/config/env safety 窄口测试；`nq-adapter-api` 14 tests，`nq-app` 14 tests。 |
| `mvn -f backend/pom.xml test` | `BUILD SUCCESS`（构建通过） | 后端 23 个 reactor module 全量测试；仅有既有 SLF4J / Mockito dynamic agent / unchecked / deprecation warning，非阻塞。 |

边界确认：

- 本轮没有执行真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken public outbound smoke。
- O-5 manual real public outbound smoke 仍为 `PLANNED / NOT STARTED`。
- 默认 local / test / CI / paper / freeze 仍 no-egress；public outbound 未加入默认 CI。
- 未新增外部 API、migration、frontend、research、scripts、deploy 或 `.github/workflows` 变更。
- 未读取、打印、复制或输出 credential material；未实现 signed request、private endpoint、private WebSocket、account/balance/order/cancel/amend/positions/wallet/transfer/withdraw/deposit/subaccount endpoint。
- LIVE `DISABLED`；AI `NOT STARTED`；DH runtime `NOT_INTEGRATED`；RealClient / real provider / real permission probe `NOT_IMPLEMENTED`。
- public marketdata readiness 仍只是 diagnostic，不等于 trading authorization。

Rollback：

1. 关闭 `NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=false` 或移除 `public-marketdata-manual` profile，运行时回到 disabled fallback。
2. 回滚本轮 backend publicmarketdata 包、`PublicMarketDataOutboundConfiguration`、manual profile YAML、`application.yml` 的 public-marketdata 配置段和 EnvSafety 相关 diff。
3. 回滚本轮 current docs / README 状态同步；无 DB migration、外部 API、frontend 或 CI workflow 回滚动作。

### 6.4 O-1 P1 Endpoint Authority Escape Fix（2026-07-02）

任务：`NQ-GATEO-O1-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND-P1-FIX`。

Fix status：`P1 FIXED`（P1 已修复）/ `ACCEPTED BY FREEZE REVIEW`（已由冻结复核接受）。本修复关闭 implementation review 发现的 endpoint path authority escape：`http://...`、`https://...`、`//host/path`、带 authority / userInfo / fragment、only-query、blank 或非法 URI 均 fail-closed；`/ticker?symbol=BTC-USDT` 与 `ticker?symbol=BTC-USDT` 这类 path-only + query 仍可在 fake-server 测试中解析到配置 base host。

修复要点：

1. `PublicMarketDataOutboundPolicy` 新增 endpoint reference 校验，拒绝 scheme、authority、userInfo、fragment、only-query、blank 和非法 URI，拒绝原因不回显 raw query 或 credential-like material。
2. `PublicMarketDataOutboundRequest` 不再把 blank endpointPath 归一成 `/`，空路径会交给 policy 明确拒绝。
3. `JdkPublicMarketDataOutboundClient` 在 `baseUri.resolve(endpointPath)` 后二次校验 resolved URI 的 scheme / host / port 必须与配置 base URI 一致；即使 policy 未来漂移，也不能通过 resolved URI 改写出站 host。
4. 新增 fake-server 回归：`//example.invalid/ticker`、`http://example.invalid/ticker`、`https://example.invalid/ticker`、带 authority、带 fragment 均 `DENIED` 且 fake server 收到 0 次请求、attempts=0、无 retry。
5. 补齐 P2 测试覆盖：high latency -> `DEGRADED`、stale -> `STALE`、gap -> `gapCount > 0`。`DataOrigin` 仍保持 O-1 fake-server 语义，后续 O-5 manual real public smoke 前再单独决定是否改为 `PUBLIC_OUTBOUND`。

验证结果：

| Command | Result | Scope |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-adapter-api,nq-app -am "-Dtest=PublicMarketDataOutboundPolicyTest,JdkPublicMarketDataOutboundClientTest,PublicMarketDataOutboundConfigurationTest,EnvSafetyValidatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | `BUILD SUCCESS`（构建通过） | P1 endpoint authority escape 与 O-1 policy/client/config/env safety 窄口回归；`nq-adapter-api` 19 tests，`nq-app` 14 tests。 |

边界确认：未执行 O-5 manual real public outbound smoke；未调用真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken；未读取或输出 credential material；未新增 API、migration、frontend、research、scripts、deploy 或 CI workflow；未开启 LIVE、AI 或 DH runtime；未实现 RealClient、real provider、real permission probe、signed request、private WebSocket 或 private trading endpoint。

下一步：该 P1 修复已由 §6.5 O-1 freeze review 消费并接受；后续不得回退 endpoint authority guard，不得把 O-1 freeze 写成 O-5 manual real public smoke 或 trading authorization。

### 6.5 O-1 Controlled Public Outbound Guard Freeze Review（2026-07-02）

任务：`NQ-GATEO-O1-PUBLIC-MARKETDATA-CONTROLLED-OUTBOUND-FREEZE-REVIEW`。

Freeze verdict：`PASS`（通过）/ `ACCEPTED`（已接受）。

Frozen baseline：O-1 controlled public outbound guard baseline。冻结对象仅覆盖已提交 commit `8638dec0 feat(marketdata): add controlled public outbound guard` 中的 controlled public outbound guard、endpoint authority escape P1 修复、fake-server/no-egress 测试与 current docs 状态同步；不新增功能，不执行真实 public outbound smoke。

Accepted evidence：

1. O-1 commit 存在：`8638dec0 feat(marketdata): add controlled public outbound guard`。
2. freeze review 前 `git status --short` 为空；`git diff --check` 与 `git diff --stat` 为空。
3. endpoint authority escape P1 已修复：policy 拒绝 scheme、authority、userInfo、fragment、only-query、blank 和非法 URI；JDK client 在 `baseUri.resolve(endpointPath)` 后二次校验 scheme / host / port 不变。
4. O-1 窄口 Maven 通过：`nq-adapter-api` 19 tests，`nq-app` 14 tests，0 failures / 0 errors / 0 skipped。
5. 后端全量 Maven 通过：23 个 backend reactor module `SUCCESS`，整体 `BUILD SUCCESS`。
6. 禁止范围 diff 为空：未改 frontend / research / scripts / deploy / `.github` / migration。
7. 未执行真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken public outbound smoke；O-5 manual real public smoke 仍为 `PLANNED / NOT STARTED`。
8. 未读取、打印、复制或输出 credential material。
9. 默认 no-egress 不变；`application.yml` 默认 `nq.public-marketdata.outbound.enabled=false`。
10. `public-marketdata-manual` profile 与 `NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED` 仍 fail-closed：profile + flag=true 才装配 JDK client，flag false / 缺失回到 disabled fallback。
11. EnvSafety 仍禁止 `public-marketdata-manual` 与 LIVE、AI、DH runtime、real provider、RealClient、real exchange 同时启用。
12. O-1 freeze review 当时 O-2 / O-3 / O-4 / O-5 / O-FREEZE 仍为 `PLANNED / NOT STARTED`；当前 O-2 Data Quality Center baseline 已进入 `PASS / ACCEPTED / FROZEN`，O-3 final status 已为 `FROZEN / ACCEPTED`，O-4 final status 已为 `FROZEN / ACCEPTED`，O-5 plan 已完成，O-5A review 与 O-5B runner binding plan 已接受，O-5B-R1 runner binding implementation 已提交，O-5B-R2 runner binding review 已接受；manual smoke execution 后续为 `ALLOWED / MANUAL PUBLIC READONLY ONLY / NOT EXECUTED`，O-5 smoke execution、O-FREEZE 仍未开始。
13. `DataOrigin.FAKE_SERVER` 记录为 P2 residual：O-1 fake-server baseline 继续使用该语义，不阻塞本次 O-1 freeze；是否引入 `PUBLIC_OUTBOUND` 留到 O-5 前单独审查。
14. 文档未把 GateO 写成 completed，也未把 public marketdata readiness 写成 trading authorization。

Validation commands：

| Command | Result | Scope |
| --- | --- | --- |
| `git status --short` | `PASS`（通过） | freeze review 写前工作区干净。 |
| `git log --oneline -5` | `PASS`（通过） | HEAD 为 `8638dec0 feat(marketdata): add controlled public outbound guard`。 |
| `git diff --check` | `PASS`（通过） | 写前无 whitespace error。 |
| `git diff --stat` | `PASS`（通过） | 写前无 tracked diff。 |
| `mvn -f backend/pom.xml -pl nq-adapter-api,nq-app -am "-Dtest=PublicMarketDataOutboundPolicyTest,JdkPublicMarketDataOutboundClientTest,PublicMarketDataOutboundConfigurationTest,EnvSafetyValidatorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | `PASS / BUILD SUCCESS`（通过 / 构建通过） | O-1 policy/client/fake-server/config/env safety 窄口测试；`nq-adapter-api` 19 tests，`nq-app` 14 tests。 |
| `mvn -f backend/pom.xml test` | `PASS / BUILD SUCCESS`（通过 / 构建通过） | 后端 23 个 reactor module 全量回归；保留既有 SLF4J / Mockito dynamic agent / unchecked / deprecation warning，非阻塞。 |

Findings：

- P0：0。
- P1：0。
- P2：1，`DataOrigin.FAKE_SERVER` 仍作为 O-1 fake-server baseline 语义保留；不阻塞 O-1 freeze，O-5 manual real public smoke 前需单独评估是否引入 `PUBLIC_OUTBOUND`。
- P3：1，历史测试记录保留 `READY FOR RE-REVIEW / NOT ACCEPTED` 当时状态；该历史语境不再代表当前 O-1 状态，current entry 已同步为 `PASS / ACCEPTED / FROZEN`。

Post-freeze rules：

- 不得删除或弱化 endpoint authority guard、private/signed denylist、redaction、bounded timeout/retry/backoff、disabled fallback、manual profile fail-closed 或 EnvSafety 禁止矩阵。
- 不得把 O-1 freeze 写成 GateO completed、O-5 executed、real provider ready、RealClient implemented、real permission probe implemented、LIVE enabled、AI started、DH integrated 或 trading authorization。
- O-2 implementation review 与 freeze review 已完成并接受；后续 O-3 / O-4 / O-5 / O-FREEZE 必须单独 plan/review；O-5 manual public smoke 必须手动 profile、显式 flag、无 credential、无 private endpoint、默认 CI 不执行。

O-1 final status：`FROZEN`（已冻结）/ `ACCEPTED`（已接受）。GateO stage 仍 `NOT COMPLETED`（未完成）。

## 7. O-2 Data Quality Center Implementation

任务：`NQ-GATEO-O2-DATA-QUALITY-CENTER-IMPLEMENTATION`。

Baseline status：`PASS`（通过）/ `ACCEPTED`（已接受）/ `FROZEN`（已冻结）。本状态只覆盖 O-2 后端纯模型、mapper、freshness/gap/source health 规则和单元测试；不表示 GateO stage completed，也不表示 O-3 API、O-4 UI、O-5 manual real public smoke 或 O-FREEZE 已开始。

实现要点：

1. 新增 `DataQualitySummary`：固定 Data Quality Center 的最小安全 summary，包含 `sourceCode`、`exchange`、`symbol`、`timeframe`、`dataOrigin`、`sourceStatus`、`sourceHealth`、`freshnessStatus`、`gapStatus`、`lastSuccessAt`、`lastFailureAt`、`latencyMs`、`errorCategory`、`gapCount`、`degradedReason`、`disabledReason`、`traceId` 和 `requestId`；不包含 trading authorization 字段。
2. 新增 `DataQualitySourceHealthMapper`：覆盖 success、high latency、429、timeout、5xx、malformed response、disabled、fallback、stale data 和 missing interval 的 O-1 result 到 O-2 diagnostic 映射。
3. 新增 `DataQualityFreshnessRule`：实现 1m 超过 3 分钟、5m 超过 10 分钟、1h 超过 2 小时、1d 超过 2 天为 stale 的 NQ safety baseline；支持 `NO_DATA`、`DISABLED`、`ERROR`。
4. 新增 `DataQualityGapRule`：基于 expected candles vs actual candles 计算 `NONE / GAP / PARTIAL / UNKNOWN` 与 `gapCount`。
5. 新增 JUnit 覆盖 mapper、fallback origin、no trading authorization、freshness 阈值、no data、disabled、gap 和完整 candles。
6. `PUBLIC_OUTBOUND` 未进入 O-2 `DataOrigin`；旧 O-1 enum 如出现 `PUBLIC_OUTBOUND`，O-2 仅兼容映射为 `PUBLIC_CANDIDATE`，不表示真实 public outbound 已执行。

验证结果：

| Command | Result | Scope |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-adapter-api,nq-app -am "-Dtest=*DataQuality*,*Freshness*,*Gap*,PublicMarketData*" "-Dsurefire.failIfNoSpecifiedTests=false" test` | `PASS / BUILD SUCCESS`（通过 / 构建通过） | O-2 Data Quality + O-1 PublicMarketData 窄口回归；`nq-adapter-api` 33 tests，`nq-app` 4 tests。 |
| `mvn -f backend/pom.xml test` | `PASS / BUILD SUCCESS`（通过 / 构建通过） | 后端 23 个 reactor module 全量回归全部 `SUCCESS`；保留既有 SLF4J / Mockito dynamic agent / unchecked / deprecation warning，非阻断。 |

边界确认：

- 未新增 API、migration、frontend、research、scripts、deploy 或 CI workflow。
- 未执行真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken public outbound smoke。
- 未读取、打印、复制或输出 credential material。
- 未开启 LIVE、AI 或 DH runtime。
- 未实现 RealClient、real provider、real permission probe、signed request、private WebSocket 或 private trading endpoint。
- O-2 Data Quality 只表示 public marketdata diagnostic，不等于 trading authorization。

### 7.1 O-2 Data Quality Center Freeze Review（2026-07-02）

任务：`NQ-GATEO-O2-DATA-QUALITY-CENTER-FREEZE-REVIEW`。

Freeze verdict：`PASS`（通过）/ `ACCEPTED`（已接受）。

Frozen baseline：O-2 Data Quality Center baseline。冻结对象仅覆盖已提交 commit `4d659d72 feat(marketdata): add data quality center baseline` 中的 `DataQualitySummary`、`DataQualitySourceHealthMapper`、`DataQualityFreshnessRule`、`DataQualityGapRule` 及 mapper / freshness / gap JUnit 测试；不新增功能，不执行真实 public outbound smoke。

Accepted evidence：

1. O-2 commit 存在：`4d659d72 feat(marketdata): add data quality center baseline`。
2. freeze review 前 `git status --short` 为空；`git diff --check` 与 `git diff --stat` 为空。
3. O-2 implementation review 已 `PASS`，O-2 implementation 已允许提交。
4. Data Quality 状态模型完整覆盖 source status / source health / freshness / gap / data origin / error category / latency / degraded/disabled reason / trace/request id。
5. O-1 result 到 O-2 mapping 覆盖 success、high latency、429、timeout、5xx、malformed response、disabled、fallback、stale data、missing interval / gap 与 `PUBLIC_OUTBOUND -> PUBLIC_CANDIDATE` 兼容降级。
6. freshness 规则覆盖 1m / 5m / 1h / 1d baseline、`NO_DATA`、`DISABLED`、`ERROR` 和 unsupported timeframe fail-closed。
7. gap 规则覆盖 missing candles、complete candles、unknown expected、inclusive window expected count 和非法输入 fail-closed。
8. `DataQualitySummary` 不包含 credential、raw request、raw response、raw headers、full query string 或 trading authorization 字段；测试通过 record component 断言防止 authorization 字段回流。
9. 窄口 Maven 与 backend 全量 Maven 均 `PASS / BUILD SUCCESS`。
10. commit diff 未改 frontend / research / scripts / deploy / `.github` / migration；dataquality 包内未发现 Spring MVC API 注解、`/api/`、HTTP client、JDBC / Repository 或 migration。
11. 未执行真实 public outbound smoke，未读取 credential material。

Findings：

- P0：0。
- P1：0。
- P2：1，O-2 未接 API read model，仍保留为 O-3 API plan/review residual。
- P3：0。

O-2 final status：`PASS / ACCEPTED / FROZEN`。GateO stage 仍 `NOT COMPLETED`；当前 O-3 API plan 已 `PASS / PLAN ONLY / NOT IMPLEMENTED` 并由 O-3B/O-3E 消费，O-3B backend read-only API implementation 已 `COMPLETED / ACCEPTED`，O-3 final status 为 `FROZEN / ACCEPTED`；O-4 final status 已 `FROZEN / ACCEPTED`；O-5 plan 已 `COMPLETED / PLAN ONLY / NOT IMPLEMENTED`；O-5A review 与 O-5B runner binding plan 已接受，O-5B-R1 runner binding implementation 已提交，O-5B-R2 runner binding review 已接受；manual smoke execution 后续为 `ALLOWED / MANUAL PUBLIC READONLY ONLY / NOT EXECUTED`，O-5 smoke execution、O-FREEZE 仍 `NOT STARTED`。

## 8. O-3 MarketData Runtime Readiness API Plan

O-3 plan status：`PASS`（通过）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现）。

O-3 final status：`FROZEN`（已冻结）/ `ACCEPTED`（已接受）。

O-3B backend implementation status：`COMPLETED`（已完成）/ `ACCEPTED`（已接受）。

O-3 原 plan 只规划 API read model；O-3B 已按该计划扩展既有 `GET /api/marketdata/readiness` read model；O-3E 已冻结该 read-only API baseline。完整计划、实现与 freeze 状态见 [NQ_GATEO_O3_MARKETDATA_RUNTIME_READINESS_API_PLAN.md](NQ_GATEO_O3_MARKETDATA_RUNTIME_READINESS_API_PLAN.md)。

现有事实：

- `GET /api/marketdata/readiness` 已存在，当前基于本地 `marketdata_bars` 与 `marketdata_ingestion_jobs/runs` 聚合 readiness summary。
- `GET /api/marketdata/bars` 已存在，返回本地 historical bars。
- existing readiness fields 已覆盖 status、freshnessStatus、sourceHealthStatus、sourceHealthReason、qualityStatusSummary、barCount、expectedBarCount、gapCount、unknownQualityCount、lastSuccessAt、lastFailureAt、backendSupportLevel、generatedAt。
- O-2 `DataQualitySummary` 已覆盖 sourceCode、exchange、symbol、timeframe、dataOrigin、sourceStatus、sourceHealth、freshnessStatus、gapStatus、lastSuccessAt、lastFailureAt、latencyMs、errorCategory、gapCount、degradedReason、disabledReason、traceId 和 requestId。

规划原则：

- 优先扩展现有 `GET /api/marketdata/readiness` read model，不重复造接口。
- API 只返回 read model，不触发采集、不访问外部交易所、不读取 credential、不授权交易。
- 新 endpoint 只允许在 plan 中作为 candidate，后续必须单独做 API contract plan review。
- `dataOrigin` 只允许 `LOCAL_DB`、`FIXTURE`、`FAKE_SERVER`、`PUBLIC_CANDIDATE`、`UNKNOWN`；O-3 不引入 `PUBLIC_OUTBOUND` 作为已落地事实。
- `sourceStatus` / `sourceHealth` / `freshnessStatus` / `gapStatus` 必须 fail-closed；`NO_DATA`、`UNKNOWN`、`STALE`、`GAP`、`ERROR`、`DISABLED` 均不得解释成 ready。
- response 不得包含 `tradingAuthorized`、`liveReady`、`privateTradingReady`、`permissionGranted`、`realProviderReady`、`apiKey`、`secret`、`passphrase`、`credentialRef`、`rawRequest`、`rawResponse`、`rawHeaders` 或 `fullQueryString`。

候选接口仅作为 plan：

- `GET /api/marketdata/readiness`：O-3 主入口，后续优先扩展 existing read model。
- `GET /api/marketdata/readiness/sources`：后置候选，仅当 O-3A/O-3B 证明需要多 source 列表时进入单独 contract review。
- `GET /api/marketdata/readiness/gaps`：后置候选，仅当 gap detail 需要分页明细时进入单独 contract review。
- `GET /api/marketdata/readiness/quality/overview`：后置候选，可作为 O-4 UI 聚合输入，但不得写成当前 API。

O-3 implementation 批次：

| Batch | 名称 | 状态 | 目标 |
| --- | --- | --- | --- |
| O-3A | API read model contract + DTO plan review | `CONSUMED BY O-3B`（已由 O-3B 消费） | 字段、enum、兼容策略和测试矩阵已随 O-3B 落地。 |
| O-3B | backend read-only endpoint implementation | `COMPLETED / ACCEPTED`（已完成 / 已接受） | 扩展现有 `/api/marketdata/readiness` read model，已由 O-3E freeze review 接受。 |
| O-3C | controller/service tests | `COVERED IN O-3B`（已随 O-3B 覆盖） | 覆盖 no-outbound、no-credential、no-authorization、状态映射回归。 |
| O-3D | docs/API sync | `CURRENT DOCS SYNCED`（当前文档已同步） | 只把已实现的真实 endpoint 写入 API/current docs。 |
| O-3E | O-3 freeze review | `PASS / ACCEPTED / FROZEN`（通过 / 已接受 / 已冻结） | 冻结 O-3 read-only API baseline。 |

Final decision：O-3B backend read-only API implementation 已完成并接受；O-3 final status 为 `FROZEN / ACCEPTED`。O-4 plan 已完成，O-4A review 已 `PASS / ACCEPTED`；O-4B read-only UI implementation 已 `COMPLETED / ACCEPTED`；O-4E freeze review 已 `PASS / ACCEPTED`；O-4 final status 已 `FROZEN / ACCEPTED`。O-5 manual real public smoke 仍不得提前执行。

## 9. O-4 MarketData Quality UI Plan

O-4 plan status：`PASS`（通过）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现）。

O-4 final status：`FROZEN / ACCEPTED`（已冻结 / 已接受）。

O-4B implementation status：`COMPLETED / ACCEPTED`（已完成 / 已接受）。

O-4E freeze review status：`PASS / ACCEPTED`（通过 / 已接受）。

完整计划见 [NQ_GATEO_O4_MARKETDATA_QUALITY_UI_PLAN.md](NQ_GATEO_O4_MARKETDATA_QUALITY_UI_PLAN.md)。

O-4 已完成并冻结前端数据质量只读 UI baseline：复用既有 `/marketdata` 区域增强，不新增页面，不改 backend，不新增 API，不新增 migration，不执行真实 public outbound。

核心决策：

- 页面命名建议：`MarketData Quality` / `行情数据质量中心`。
- 路由建议：优先复用现有 `/marketdata` 页面并增加 `Quality / Readiness` tab 或等价分区；暂不新增 `/marketdata/quality` 或 `/marketdata/readiness` 独立路由。
- API 消费：只消费 `GET /api/marketdata/readiness`；不得消费真实交易所 public endpoint、private endpoint、credential endpoint、permission probe endpoint 或 O-5 manual public smoke endpoint。
- 重点展示：`sourceCode`、`exchangeCode / exchange`、`symbol`、`interval / timeframe`、`dataOrigin`、`sourceStatus`、`sourceHealth`、`freshnessStatus`、`gapStatus`、`lastSuccessAt`、`lastFailureAt`、`lastObservedAt`、`latencyMs`、`errorRate`、`errorCategory`、`gapCount`、`missingFrom`、`missingTo`、`staleAfterSeconds`、`degradedReason`、`disabledReason`、`updatedAt`。
- null 展示规则：`errorRate`、`missingFrom`、`missingTo`、`gapCount` 等 nullable 字段在缺少稳定事实时必须显示“暂无稳定事实”或等价中文，不得显示为 0。
- `dataOrigin` 规则：当前 readiness API 不包含已落地 `PUBLIC_OUTBOUND`；`PUBLIC_CANDIDATE` 不证明真实 public outbound 已执行。

组件规划：

- `MarketDataReadinessSummary`
- `MarketDataSourceHealthTable`
- `MarketDataFreshnessBadge`
- `MarketDataOriginBadge`
- `MarketDataGapPanel`
- `MarketDataErrorPanel`
- `MarketDataQualityNotice`
- `MarketDataReadinessDrawer`

图表优先级：

- P0：数据源健康表、freshness 状态、gap 状态、错误类别。
- P1：latency 趋势、error rate 趋势、gap 分布。
- P2：K 线 / 成交量图表接入或复用增强。
- P3：多源对比图。

禁止 UI 语义：

- 不展示 AI signal ready。
- 不展示 DH runtime connected。
- 不展示 LIVE enabled。
- 不展示 trading-ready / provider-ready / private trading-ready。
- 不展示 trading authorized、live ready、permission granted、real-ready。
- 不用 public source healthy 或 data quality normal 推导“可交易”。

后续批次：

| Batch | 名称 | 状态 | 说明 |
| --- | --- | --- | --- |
| O-4A | UI contract plan review | `PASS / ACCEPTED` | 已复核路由、字段、文案、风险提示和测试矩阵；API.md enum drift 已修正。 |
| O-4B | MarketData Quality read-only page/table implementation | `COMPLETED / ACCEPTED` | 已复用 `/marketdata`，补齐前端类型、只读 source health table、summary、notice、nullable 中文空态和 no-backend smoke；已由 O-4E 接受。 |
| O-4C | 状态 badge / notice / drawer polish | `NOT STARTED` | 统一状态、null 文案、风险提示和详情抽屉。 |
| O-4D | 图表 foundation | `OPTIONAL / NOT STARTED` | 仅在有稳定历史窗口事实后规划趋势图。 |
| O-4E | O-4 freeze review | `PASS / ACCEPTED` | 冻结 O-4 UI baseline；P0/P1=0，build 与指定 mocked smoke 已通过。 |

Final decision：O-4 final status 已 `FROZEN / ACCEPTED`；O-4A UI contract plan review 已 `PASS / ACCEPTED`；O-4B read-only UI implementation 已 `COMPLETED / ACCEPTED`；O-4E freeze review 已 `PASS / ACCEPTED`。O-4B/O-4E 未改 backend/API/migration，未执行 O-5 manual public outbound smoke；GateO stage 仍 `NOT COMPLETED`，manual smoke execution 后续为 `ALLOWED / MANUAL PUBLIC READONLY ONLY / NOT EXECUTED`，O-5 smoke execution / O-FREEZE 仍 `NOT STARTED`。

## 10. O-5 Manual Public Outbound Smoke Plan

O-5 planning baseline 已完成，详见 [NQ_GATEO_O5_MANUAL_PUBLIC_OUTBOUND_SMOKE_PLAN.md](NQ_GATEO_O5_MANUAL_PUBLIC_OUTBOUND_SMOKE_PLAN.md)。O-5A review 已 `PASS / ACCEPTED`；O-5B runner binding plan 已 `PASS / ACCEPTED`，详见 [NQ_GATEO_O5B_RUNNER_BINDING_PLAN.md](NQ_GATEO_O5B_RUNNER_BINDING_PLAN.md)；O-5B-R1 runner binding implementation 已 `IMPLEMENTED / SELF-REVIEWED / COMMITTED`（commit `35413109`）；O-5B-R2 runner binding review 已 `PASS / ACCEPTED`。Manual smoke execution 后续为 `ALLOWED / MANUAL PUBLIC READONLY ONLY / NOT EXECUTED`；O-5 smoke execution 仍未开始，本轮不执行真实 HTTP。

O-5 定位：

- O-5 是 manual public outbound smoke。
- O-5 只允许 public REST / readonly marketdata。
- O-5 不是默认 CI。
- O-5 不是 private trading。
- O-5 不是 permission probe。
- O-5 不是 LIVE readiness。
- O-5 不是 trading authorization。

O-5 前置条件：

- O-1 guard 已冻结。
- O-2 DataQuality 已冻结。
- O-3 readiness API 已冻结。
- O-4 UI 已冻结。
- 工作区 clean，当前分支 `dev`。
- 默认 profile 下 no-egress 仍成立。
- 手动 profile 必须显式启用。
- feature flag 必须显式启用。
- 无 credential、无 signed request、无 private endpoint。
- smoke 执行前必须有 allowlist 和 denylist review。

O-5 profile / flag 继承 O-1：

```text
spring.profiles.active=public-marketdata-manual
NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=true
```

默认配置仍必须关闭 outbound，默认 CI 不得启用 manual profile。本地手动执行前必须显示声明：

```text
this is public-readonly smoke
no credential
no private endpoint
no trading side effect
```

O-5 public allowlist 只允许 O-1 已审查官方文档 / allowlist 中的 public REST 类别：`SERVER_TIME`、`INSTRUMENTS`、`TICKER`、`OHLCV`。`ORDER_BOOK`、`RECENT_TRADES` 和 `PUBLIC_WEBSOCKET` 继续后置；任何未审查 endpoint 不得临时加入。

O-5 private denylist 继续 fail-closed，明确禁止 account、balance、order、cancel、amend、position、wallet、transfer、withdraw、deposit、subaccount、private WebSocket、signed request、permission probe、API key validation、passphrase validation 和任何需要 authentication 的 endpoint。

O-5 evidence / audit 只允许记录 run started at、endpoint category、provider、symbol / instrument、response status、latency、redacted error、dataOrigin、no credential used、no signed request、no private endpoint、no trading side effect。禁止记录 raw response body、full URL query string、raw headers、credential、API key、secret、passphrase、token 或签名串。

O-5 后续必须拆分：

| Batch | 状态 | 目标 |
| --- | --- | --- |
| O-5A | `PASS / ACCEPTED` | manual public outbound smoke plan review |
| O-5B runner binding plan | `PASS / ACCEPTED` | test-only manual runner 形态、默认不运行策略、allowlist/denylist 与 redacted evidence 契约 |
| O-5B-R1 runner implementation | `IMPLEMENTED / SELF-REVIEWED / COMMITTED` | 已新增并提交 test-only manual runner，不执行 smoke |
| O-5B-R2 runner binding review | `PASS / ACCEPTED` | 已复核 runner gate、allowlist/denylist、redaction 与默认 skip |
| O-5B smoke execution | `ALLOWED / MANUAL PUBLIC READONLY ONLY / NOT EXECUTED` | 后续单独人工 public readonly smoke execution |
| O-5C | `NOT STARTED` | first smoke result review |
| O-5D | `NOT STARTED` | DataOrigin / `PUBLIC_OUTBOUND` decision review |
| O-5E | `NOT STARTED` | O-5 freeze review |
| O-FREEZE | `NOT STARTED` | GateO freeze |

O-5A review、O-5B runner binding plan 与 O-5B-R2 runner binding review 均 P0/P1=0；O-5B-R1 已实现并提交 test-only manual runner。下一步只允许单独执行 O-5B manual public outbound smoke execution，不得从 review 直接跳到 O-FREEZE。

## 11. 安全边界

GateO 必须继承 GateN residual 与 no-real 边界：

- GateN 维持 `PARTIAL / ACCEPTED WITH EXPLICIT CI VISIBILITY RESIDUAL`，不得在 GateO 文档中提升为 `VERIFIED`。
- public marketdata 仅限只读、公开、无 credential、无签名。
- default profile、默认测试和默认 CI 仍保持 no-egress。
- manual public outbound profile 必须可关闭、可审计、可回滚。
- 所有 response / log / artifact 不得输出 credential-like material、签名、cookie、token、private key、raw credential payload 或 private provider response。
- public adapter 必须与 private trading adapter 分离。

P0 触发条件：

- 将 GateO 写成 completed 或把未授权后续批次写成 started。
- 将 public marketdata 写成 trading authorization。
- 将 LIVE / real provider / RealClient 写成 enabled / implemented。
- 允许默认测试真实外联。
- 读取或输出 credential material。

## 12. 测试策略

O-2 implementation 与 freeze review 均已运行 Maven 窄口与后端全量回归；未运行 npm build / Playwright / pytest / mypy / ruff，原因是本轮不改 frontend / research / Python。

后续批次测试策略：

- O-1 controlled public outbound guard：已 `PASS / ACCEPTED / FROZEN`。
- O-2 Data Quality Center baseline：已 `PASS / ACCEPTED / FROZEN`；O-2 未接 API read model 作为 P2 residual 留给 O-3。
- O-3 API plan：已 `PASS / PLAN ONLY / NOT IMPLEMENTED`，决策为优先扩展现有 `/api/marketdata/readiness`，并确认 candidate endpoint 不被写成当前 API。
- O-3B backend read-only API implementation：已 `COMPLETED / ACCEPTED`；scoped Maven、controller/service/DTO tests、后端全量 Maven、forbidden diff 和 forbidden field scan 已作为 O-3E freeze evidence。
- O-3E read-only API freeze review：已 `PASS / ACCEPTED / FROZEN`；只冻结已实现 read-only API baseline，不新增功能。
- O-4 UI baseline：已 `FROZEN / ACCEPTED`；O-4A plan review 已 `PASS / ACCEPTED`；O-4B read-only UI implementation 已 `COMPLETED / ACCEPTED`；O-4E freeze review 已 `PASS / ACCEPTED`，已验证前端类型、只读表格、状态文案、null 展示和 UI copy 边界。
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
- O-1 controlled public outbound guard 完成并冻结。
- O-2 data quality center implementation 完成并冻结。
- O-3 readiness API plan 完成。
- O-4 UI baseline 已 `FROZEN / ACCEPTED`。
- O-4B implementation 已完成并被 O-4E freeze review 接受；build / mocked no-backend smoke 验证记录已复核；当前不代表 GateO completed，O-5 manual public outbound smoke 仍未开始。
- O-5 manual public outbound smoke plan 已完成；O-5A 已 `PASS / ACCEPTED`；O-5B runner binding plan 已 `PASS / ACCEPTED`；O-5B smoke execution 结果必须由后续 runner implementation review 通过后的 O-5B/O-5C 单独提供。
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
- 后续若将 GateO 写成 completed、将 public marketdata 写成 trading authorization、将 LIVE / real provider / RealClient 写成 enabled / implemented、允许默认测试真实外联、读取或输出 credential material，均为 P0。

P1：

- public/private adapter 边界不清。
- 默认 no-egress 与 manual public outbound profile 边界不清。
- O-5 smoke 进入默认 CI。
- 未明确 public REST only。
- 未明确禁止 private endpoint。

P2：

- O-2 已冻结为纯模型和规则 baseline；O-3B 已接入现有 `/api/marketdata/readiness` read model 并保持 DB-only / no-egress / no-credential；O-3E 已冻结该 read-only API baseline。`errorRate`、`missingFrom`、`missingTo` 在缺少稳定事实时返回 `null`，后续若需真实窗口指标必须另起设计。
- 前端 source/status 文案可能误导。
- API plan 与现有 marketdata API 重复。
- 官方文档引用入口未列清。

P3：

- 文档入口重复。
- 中英混排不统一。
- 历史 GateN residual 说明不够集中。

## 16. 后续任务建议

推荐下一步：

1. `NQ-GATEO-O5B-MANUAL-PUBLIC-OUTBOUND-SMOKE-EXECUTION`：仅在后续单独任务中执行手动 public readonly smoke，必须显式 manual gates，且不得使用 credential、signed request 或 private endpoint。
2. `NQ-GATEO-O5C-FIRST-SMOKE-RESULT-REVIEW`：只复核脱敏 smoke 结果。
3. `NQ-GATEO-O5D-DATAORIGIN-PUBLIC-OUTBOUND-DECISION-REVIEW`：单独决定是否引入 `PUBLIC_OUTBOUND`，不得在 O-5 plan 中预写成当前事实。
4. `NQ-GATEO-O5E-FREEZE-REVIEW`：冻结 O-5 baseline。
5. `NQ-GATEO-O-FREEZE-CRITERIA-PLAN-REVIEW`：只在 O-5 前置证据完成后规划 GateO freeze criteria。

不得把 O-3/O-4/O-5 plan 写成 GateO completed、O-5 execution、LIVE-ready、real-provider-ready 或 trading authorization；不得提前执行 O-5 manual public outbound smoke。

## 17. 本轮未做事项

- 未修改 `frontend/**`、`research/**`、`scripts/**`、`deploy/**`、`.github/**` 或 `backend/**/db/migration/**`。
- 未新增或修改 migration。
- 未新增 API。
- 未新增页面。
- 未新增 E2E。
- 未修改 CI workflow。
- 未执行真实 public outbound。
- 未调用真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- 未读取或输出 credential material。
- 未开启 LIVE。
- 未接 AI。
- 未接 DH runtime。
- 未实现 RealClient。
- 未实现 real provider。
- 未实现真实 permission probe。
- 未下单、撤单、转账或提现。
- 未把 GateO 写成 completed。
