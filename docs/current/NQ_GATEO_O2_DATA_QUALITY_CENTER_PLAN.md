# NQ GateO O-2 Data Quality Center Plan

任务：`NQ-GATEO-O2-DATA-QUALITY-CENTER-PLAN`

结论：`PASS`（通过）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现）。

本文件是 GateO O-2 Data Quality Center 的 planning baseline。它只定义 marketdata 数据质量中心的目标、状态模型、字段、来源、O-1 映射、freshness/gap/error 口径、测试策略和安全边界；不实现代码，不新增 API，不新增 migration，不改前端，不改 CI，不执行真实 public outbound smoke。

## 1. Current State

- GateO O-1 controlled public outbound guard baseline 已冻结为 `PASS / ACCEPTED / FROZEN`，freeze docs commit 为 `44b4b060 docs(gateo): freeze controlled public outbound guard`。
- GateO stage 仍为 `NOT COMPLETED`（未完成）；本文件不把 GateO 写成已完成阶段。
- O-2 Data Quality Center 本轮状态为 `PASS / PLAN ONLY / NOT IMPLEMENTED`，只完成规划。
- O-3 MarketData Runtime Readiness API、O-4 MarketData Quality UI、O-5 Manual Public Outbound Smoke、O-FREEZE 均仍为 `PLANNED / NOT STARTED`（已规划 / 未开始）。
- O-1 P2 residual：`DataOrigin.FAKE_SERVER` 继续作为 fake-server baseline 语义保留；是否引入 `PUBLIC_OUTBOUND` 留到 O-5 前单独审查。
- LIVE 为 `DISABLED`（已禁用）；AI 为 `NOT STARTED`（未启动）；DH runtime 为 `NOT_INTEGRATED`（未集成）；RealClient / real provider / real permission probe 为 `NOT_IMPLEMENTED`（未实现）。
- public marketdata readiness 只能解释为 diagnostic，不等于 trading authorization。

## 2. Goal

Data Quality Center 解决的问题是让行情消费者清楚知道：

- 数据来自哪里。
- 数据是否新鲜。
- 数据是否完整。
- 数据是否存在缺口。
- 数据源是否健康。
- 数据是否可用于回测、Paper 或分析。
- 数据是否只是 public marketdata diagnostic，不代表交易授权。

必须保留三条边界：

```text
图能显示 ≠ 数据可靠
数据可靠 ≠ 可以交易
public marketdata readiness ≠ trading authorization
```

## 3. Non-goals

- 不实现 Data Quality Center。
- 不新增 migration，不新增表，不修改历史 migration。
- 不新增或修改后端 API。
- 不新增页面，不新增 E2E，不改前端实现。
- 不修改 ingestion job，不执行真实数据补洞。
- 不执行真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken HTTP。
- 不执行 O-5 manual public outbound smoke。
- 不读取 credential material。
- 不开启 LIVE，不接 AI，不接 DH runtime。
- 不实现 RealClient、real provider、real permission probe、private adapter 或 signed request。
- 不把 marketdata quality、readiness、source health、freshness、gap 或图表展示写成 trading authorization。

## 4. Scope

本计划覆盖：

- Data Quality Center 状态模型。
- 字段模型和来源说明。
- O-1 outbound result 到 O-2 diagnostic 状态映射。
- gap / freshness / rate-limit / timeout / error 规划口径。
- 与现有 MarketData API、DB 和前端 `/marketdata` 的复用关系。
- 后续 O-2 implementation 的测试策略和验收标准。

本计划不决定：

- O-3 是否新增 endpoint。默认建议优先扩展现有 `GET /api/marketdata/readiness` read model，除非 O-3 API contract plan 证明现有接口无法承载。
- O-4 UI 具体页面结构和交互实现。
- O-5 是否把 fake-server baseline 的 `FAKE_SERVER` 改为 `PUBLIC_OUTBOUND`。

## 5. Allowed Changes For This Planning Round

- 新增本文件。
- 同步 `docs/current/GATEO_PLAN.md`、`STATUS.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`、`docs/current/README.md` 和 root `README.md` 的 O-2 planning 状态与入口。

## 6. Forbidden Changes For This Planning Round

- 禁止修改 `backend/**`、`frontend/**`、`research/**`、`scripts/**`、`deploy/**`、`.github/**`。
- 禁止新增 migration、API、页面、E2E 或 CI workflow。
- 禁止执行真实 public outbound smoke。
- 禁止读取 credential。
- 禁止开启 LIVE、AI、DH runtime、RealClient、real provider 或 real permission probe。

## 7. Existing Capability

当前只读核对到的既有能力：

- `GET /api/marketdata/readiness` 已存在，当前基于本地 `marketdata_bars` 与 `marketdata_ingestion_jobs/runs` 聚合 `status`、`freshnessStatus`、`sourceHealthStatus`、`sourceHealthReason`、`qualityStatusSummary`、`barCount`、`expectedBarCount`、`gapCount`、`unknownQualityCount`、`lastSuccessAt`、`lastFailureAt`、`backendSupportLevel`、`generatedAt`。
- `GET /api/marketdata/bars` 已存在，返回本地 OHLCV bars。
- `marketdata_bars` 已包含 `exchange_code`、`market_type`、`symbol`、`interval`、`open_time`、`close_time`、OHLCV、`source`、`quality_status`、`raw_payload_json`、`ingested_at`。
- `marketdata_ingestion_jobs/runs` 可提供 `lastSuccessAt`、`lastFailureAt`、latest run status 和处理事实。
- `marketdata_datasets` / `marketdata_dataset_coverage` 已有 dataset quality 与 coverage 事实，可作为未来聚合输入。
- 前端 `/marketdata` 已有 K 线、成交量、Data Quality / Readiness 区域，并可展示 readiness status、source health、backend support、freshness、gap count、unknown quality count、last success / failure。
- O-1 `PublicMarketDataQualitySummary` 已提供最小 source health / freshness / gap / source status / data origin 桥接，并在构造时拒绝 `tradingAuthorization=true`。

## 8. Core Status Model

### 8.1 source_status

规划值：

- `ENABLED`：source 配置允许参与诊断。
- `DISABLED`：feature flag、profile、policy 或安全策略禁用；只代表该 source 禁用，不代表整个系统不可用。
- `DEGRADED`：source 可用但质量下降。
- `ERROR`：source 当前错误。
- `RATE_LIMITED`：source 当前被限流。

### 8.2 source_health

规划值：

- `HEALTHY`：source health 当前健康。
- `DEGRADED`：source health 降级，例如 latency 高、stale、gap、临时降级。
- `RATE_LIMITED`：429 或等价限流。
- `TIMEOUT`：超时。
- `ERROR`：5xx、invalid response、transport error 等错误。
- `UNKNOWN`：证据不足，不能推断健康。

### 8.3 freshness_status

规划值：

- `FRESH`：最新数据满足 freshness window。
- `STALE`：超过 stale threshold。
- `VERY_STALE`：超过更长的严重陈旧阈值，后续实现可按 `stale_after_seconds * 3` 或 O-2 implementation review 决定。
- `NO_DATA`：没有可判断数据。
- `ERROR`：freshness 判断被错误事实阻断。
- `DISABLED`：source disabled，不做 freshness 推断。

### 8.4 data_origin

规划值：

- `LOCAL_DB`：本地 DB 聚合数据。
- `FIXTURE`：fixture 数据。
- `FAKE_SERVER`：O-1 fake-server baseline 或 no-egress fake-server 测试数据。
- `PUBLIC_CANDIDATE`：未来可审查的 public source 候选，不表示已真实外联。
- `PUBLIC_OUTBOUND`：未来若 O-5 前审查通过，才可考虑引入；O-2 不落实现。
- `UNKNOWN`：来源证据不足。

O-1 当前 fake-server baseline 可继续使用 `FAKE_SERVER`。`PUBLIC_OUTBOUND` 不得在 O-2 中落实现；是否引入必须在 O-5 前单独审查。

### 8.5 gap_status

规划值：

- `NONE`：没有缺口证据。
- `GAP`：存在明确 missing interval。
- `PARTIAL`：覆盖不完整或局部窗口缺失，但不足以标成完整 gap。
- `UNKNOWN`：证据不足。

## 9. Core Fields

| Field | Meaning | Source type | O-2 status |
| --- | --- | --- | --- |
| `source_code` | 数据源内部编码，例如 `BINANCE_PUBLIC_BARS`、`LOCAL_DB_MARKETDATA_BARS` | planning only / not implemented | 规划字段，未实现 |
| `exchange` | 交易所或 venue 代码，例如 OKX / BINANCE | local DB / fixture；future O-3 API aggregation | 现有 `exchangeCode` 可部分承载 |
| `symbol` | 行情 symbol，例如 `BTC-USDT` | local DB / fixture；future O-3 API aggregation | 现有 bars/readiness 可承载 |
| `instrument_id` | 标准化 instrument id；当前可与 symbol 相同 | local DB / fixture；future O-3 API aggregation | 现有 readiness 已返回 |
| `timeframe` | K 线周期，例如 `1m`、`5m`、`1h`、`1d` | local DB / fixture；future O-3 API aggregation | 现有 `interval` 可承载 |
| `source_type` | source 分类，例如 DB、fixture、public candidate | planning only / not implemented | 需后续实现定义 |
| `data_origin` | 实际数据来源：`LOCAL_DB` / `FIXTURE` / `FAKE_SERVER` / future `PUBLIC_OUTBOUND` | O-1 outbound result；local DB / fixture；future O-3 API aggregation | O-1 bridge 已有部分 enum；O-2 需扩展语义 |
| `source_status` | source 开关与可用状态 | O-1 outbound result；future O-3 API aggregation | O-1 仅有 ENABLED / DISABLED；O-2 规划扩展 |
| `source_health` | source 健康状态 | O-1 outbound result；local DB / fixture；future O-3 API aggregation | O-1 mapper 与现有 readiness 已部分覆盖 |
| `freshness_status` | 数据 freshness 状态 | O-1 outbound result；local DB / fixture；future O-3 API aggregation | 现有 readiness 已部分覆盖；O-2 规划 `VERY_STALE` |
| `gap_status` | 缺口状态 | local DB / fixture；future O-3 API aggregation | 现有 readiness 有 `gapCount`；O-2 规划 explicit status |
| `last_success_at` | 最近一次成功观测、采集或处理时间 | O-1 outbound result；local DB / fixture；future O-3 API aggregation | 现有 readiness 已返回 ingestion success |
| `last_failure_at` | 最近一次失败观测、采集或处理时间 | O-1 outbound result；local DB / fixture；future O-3 API aggregation | 现有 readiness 已返回 ingestion failure |
| `last_observed_at` | 最近一次 source 被观测到的时间；可不同于 bar close time | O-1 outbound result；future O-3 API aggregation | 规划字段，未实现 |
| `latency_ms` | outbound 或处理链路延迟 | O-1 outbound result；future O-3 API aggregation | O-1 result 可提供，当前 API 未聚合 |
| `error_rate` | 限定窗口内错误率 | O-1 outbound result；future O-3 API aggregation | 规划字段，未实现 |
| `error_category` | 错误分类，如 `RATE_LIMITED`、`TIMEOUT`、`INVALID_RESPONSE` | O-1 outbound result；future O-3 API aggregation | O-1 result 有 error category，O-2 规划统一语义 |
| `gap_count` | 缺失 interval 数量 | local DB / fixture；future O-3 API aggregation | 现有 readiness 已返回 |
| `missing_from` | 缺口起始时间 | planning only / not implemented | 规划字段，未实现 |
| `missing_to` | 缺口结束时间 | planning only / not implemented | 规划字段，未实现 |
| `stale_after_seconds` | freshness 判定阈值秒数 | future O-3 API aggregation；planning only / not implemented | 规划字段，未实现 |
| `degraded_reason` | 降级原因，供 UI 解释 | O-1 outbound result；local DB / fixture；future O-3 API aggregation | 现有 `sourceHealthReason` 可部分承载 |
| `disabled_reason` | 禁用原因，例如 flag off / policy denied | O-1 outbound result；future O-3 API aggregation | 规划字段，未实现 |
| `trace_id` | 诊断 trace id；不得携带 raw credential 或 raw payload | O-1 outbound result；future O-3 API aggregation | 规划字段，未实现 |
| `request_id` | 请求级 id；用于审计和排障 | O-1 outbound result；future O-3 API aggregation | 规划字段，未实现 |
| `updated_at` | Data Quality summary 更新时间 | local DB / fixture；future O-3 API aggregation | 现有 `generatedAt` 可部分承载 |

## 10. O-1 To O-2 Mapping

| O-1 result | O-2 status |
| --- | --- |
| success | `source_health=HEALTHY` |
| high latency | `source_health=DEGRADED` |
| 429 | `source_health=RATE_LIMITED` |
| timeout | `source_health=TIMEOUT / DEGRADED` |
| 5xx | `source_health=ERROR` |
| malformed response | `source_health=ERROR`, `error_category=INVALID_RESPONSE` |
| disabled flag | `source_status=DISABLED` |
| fallback | `data_origin=LOCAL_DB / FIXTURE / FAKE_SERVER` |
| stale data | `freshness_status=STALE` |
| missing interval | `gap_status=GAP`, `gap_count > 0` |

Mapping rules:

- O-1 result 映射只是数据质量状态，不产生 trading authorization。
- O-1 failure 不影响 Paper / no-real baseline，不触发下单、撤单、转账、提现或 permission probe。
- O-1 disabled 不代表系统不可用，只代表 public outbound disabled。
- fallback 来源必须与真实 public outbound 分开表达。
- `PUBLIC_OUTBOUND` 是否进入 data origin，必须等 O-5 前单独审查，不能由 O-2 plan 直接启用。

## 11. Gap Model

O-2 gap model 只规划，不实现。

核心口径：

- K 线缺失区间以 timeframe 为步长识别 missing interval。
- `expected candles` 由 `[from, to]` 或 `[firstOpenTime, lastOpenTime]` 按 interval 计算。
- `actual candles` 来自本地 bars 或 fixture facts。
- 基础公式：`gap_count = max(0, expected_candles - actual_candles)`，再与 `quality_status` 中的 gap signal 取更保守结果。
- `dataset completeness = actual_candles / expected_candles`，无 expected 时为 `UNKNOWN`。
- timezone / exchange calendar 必须后续单独建模；O-2 不能把自然日规则写成所有市场官方事实。
- 不同 timeframe 的 gap 判断必须按 interval 递增，不得把 1m 缺口规则直接套到 1d。
- historical bars gap 与 realtime public outbound gap 是两类证据：historical 证明本地序列覆盖，realtime 只证明最近观测窗口。

边界：

- 不新增 migration。
- 不跑真实数据补洞。
- 不新增 API。
- 不修改 ingestion job。
- 不把 gap=0 写成可交易。

## 12. Freshness Model

O-2 freshness model 只规划，不实现。

状态口径：

- `FRESH`：最新 bar 或 latest observed time 未超过 freshness threshold。
- `STALE`：超过 `stale_after_seconds`。
- `VERY_STALE`：超过更严重阈值，后续实现需单独确定默认值。
- `NO_DATA`：没有可判断 bars 或 source 观测事实。
- `DISABLED`：source disabled，不做 freshness 推断。

建议默认规则：

| Timeframe | Suggested stale baseline |
| --- | --- |
| `1m` | 超过 3 分钟可视为 `STALE` |
| `5m` | 超过 10 分钟可视为 `STALE` |
| `1h` | 超过 2 小时可视为 `STALE` |
| `1d` | 超过 2 个交易日或自然日需标记 `STALE`，具体按市场后续定义 |

这些是 NQ planning baseline，不是交易所官方协议事实。

`last_success_at` 与 bars max timestamp 必须分开：前者表示 source / ingestion / outbound 处理成功时间，后者表示行情数据自身覆盖到的业务时间。二者任一陈旧都不能推断为可交易。

## 13. Rate Limit / Timeout / Error Model

规划映射：

- `429` -> `RATE_LIMITED`。
- `408` / timeout -> `TIMEOUT`。
- `5xx` -> `TEMPORARY_FAILURE / ERROR`。
- `4xx public endpoint error` -> `CLIENT_ERROR / INVALID_REQUEST`。
- malformed response -> `INVALID_RESPONSE`。
- unsupported symbol -> `SYMBOL_UNSUPPORTED`。
- disabled flag -> `DISABLED`。
- no data -> `NO_DATA`。

禁止规则：

- 不得把错误映射成 tradable state。
- 不得用 retryable error 推导可以继续交易。
- 不得把 public endpoint 4xx/5xx、timeout、rate limit、malformed response 写成 provider ready。
- 不得输出 raw response body、raw headers、full query string、credential-like material、signature、token 或 cookie。

## 14. Relation To Existing API / DB / Frontend

- 现有 `GET /api/marketdata/bars` / OHLCV 可作为 Data Quality 输入。
- 现有 `GET /api/marketdata/readiness` 已是 DB-only readiness read model，可作为 O-3 的默认扩展候选。
- 现有 instrument/source 模型可在 O-3 implementation plan 中评估复用，但 O-2 不扩展 schema。
- 现有 readiness 字段已覆盖 status、freshness、source health、gap、quality summary、last success/failure 和 backend support；O-2 不重复造模型，只规划增强字段。
- O-3 应默认优先扩展现有 `/api/marketdata/readiness`，除非 API contract plan 证明需要新 endpoint。
- O-4 UI 应展示 source health、freshness、gap、latency、error rate、last success/failure、data origin，但不能展示 trading-ready、provider-ready、LIVE-ready 或 AI/DH-ready。
- O-2 不决定新增 API 或页面实现。

## 15. Future O-2 Implementation Test Strategy

后续 O-2 implementation 至少需要：

- mapper unit test：覆盖 O-1 result 到 O-2 状态映射。
- freshness rule unit test：覆盖 1m / 5m / 1h / 1d threshold 和 `NO_DATA` / `DISABLED`。
- gap calculation unit test：覆盖 expected vs actual、quality gap signal、unknown expected。
- disabled source test：确认 disabled 不被写成 source healthy。
- fallback origin test：确认 `LOCAL_DB` / `FIXTURE` / `FAKE_SERVER` 与 future `PUBLIC_OUTBOUND` 分开。
- no trading authorization test：确认 public marketdata quality 永不产生交易授权。
- redaction test：确认 summary/log 不输出 raw payload、credential-like material 或 query/header。
- no real outbound test：默认测试不访问真实交易所。
- no credential test：默认路径不读取 credential material。

## 16. Future O-2 Implementation Acceptance Criteria

未来 O-2 implementation 只有满足以下条件才可接受：

1. Data Quality 状态模型完整。
2. O-1 result 到 O-2 mapping 可测试。
3. freshness / gap / health 规则可测试。
4. DataOrigin 语义不误导。
5. public marketdata readiness 不产生 trading authorization。
6. 默认 no-egress 不破坏。
7. 不执行真实 public smoke。
8. 不读取 credential。
9. 不新增 private adapter。
10. 文档状态一致。

## 17. Security Boundary

- O-2 只处理 public marketdata diagnostic。
- 默认 no-egress 不能被 O-2 implementation 破坏。
- O-1 guard、allowlist/denylist、endpoint authority guard、redaction、bounded timeout/retry/backoff 和 disabled fallback 不能被削弱。
- EnvSafety 仍必须阻止 LIVE、AI、DH runtime、real provider、RealClient 和 real exchange。
- Data Quality Center 不能调用 private endpoint，不能访问 signed route，不能读取 credential，不能做 real permission probe。
- `PUBLIC_OUTBOUND` 不得在 O-2 中落实现；是否引入留到 O-5 前 review。

## 18. P0 / P1 / P2 / P3 Risks

P0:

- O-2 plan 把数据质量写成交易授权。
- O-2 plan 要求真实外联或真实 credential。
- O-2 plan 启用 LIVE / RealClient / real provider。
- O-2 plan 绕过 O-1 guard。

P1:

- 状态模型缺失。
- O-1 到 O-2 映射不清。
- DataOrigin 语义误导。
- freshness / gap 规则缺失。
- O-3 / O-4 / O-5 边界不清。

P2:

- 字段列表不完整。
- error category 不完整。
- API 复用边界不够清晰。
- 测试策略不够细。

P3:

- 文档入口重复。
- 中英混排。
- 历史 residual 描述不集中。

本计划当前评估：P0=0，P1=0，P2=0，P3=0。

## 19. Decision

O-2 planning verdict：`PASS / PLAN ONLY / NOT IMPLEMENTED`。

O-2 implementation may start：YES，但只能在后续单独授权的 `NQ-GATEO-O2-DATA-QUALITY-CENTER-IMPLEMENTATION` 任务中开始；本轮没有开始 implementation。

Remaining GateO status：

- O-1 controlled public outbound guard：`PASS / ACCEPTED / FROZEN`。
- O-2 Data Quality Center plan：`PASS / PLAN ONLY / NOT IMPLEMENTED`。
- O-3 MarketData Runtime Readiness API：`PLANNED / NOT STARTED`。
- O-4 MarketData Quality UI：`PLANNED / NOT STARTED`。
- O-5 Manual Public Outbound Smoke：`PLANNED / NOT STARTED`。
- O-FREEZE：`PLANNED / NOT STARTED`。
- GateO stage：`NOT COMPLETED`。

## 20. Recommended Next Implementation Task

推荐下一步：`NQ-GATEO-O2-DATA-QUALITY-CENTER-IMPLEMENTATION`。

最小实现范围应由下一轮单独确认，默认只能围绕 O-1 result mapping、existing readiness read model、local DB / fixture facts、freshness / gap / source health 规则和单元测试推进；不得新增真实 public smoke、credential、private endpoint、LIVE、AI、DH runtime、RealClient、real provider、real permission probe 或 trading authorization。
