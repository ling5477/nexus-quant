# GateL Plan（No-Real Exchange / Marketdata Readiness Planning）

任务：NQ-GATEL-PLAN
日期：2026-06-22
分支：dev
任务类型：ARCHITECTURE_PLANNING + EXCHANGE_ADAPTER_BOUNDARY_REVIEW + MARKETDATA_BOUNDARY_REVIEW + SECURITY_BOUNDARY_REVIEW + ROADMAP_PLANNING
状态：**NQ-GATEL-PLAN = PASS / ACCEPTED（PLANNING BASELINE）**。GateL implementation **NOT STARTED**。

> 本文件是 GateL 的 planning baseline，只规划真实交易所接入前的 No-Real 交易适配器与市场数据边界。
> 本轮 docs-only：不实现代码、不新增 API、不新增 migration、不改 workflow、不接真实交易所、不读取真实凭证、不外联、不启用 LIVE、不接 AI / DH runtime。
> 本文件被 review/accept 不代表 GateL implementation started，也不代表允许接入真实 OKX / Binance 或任何真实交易所。

---

## 0. 必须先回答的硬性问题

| # | 问题 | 答案 |
| --- | --- | --- |
| 1 | 当前 NQ 是否可以直接接真实 OKX / Binance？ | **不能。** real permission probe adapter / RealClient 均 NOT IMPLEMENTED；no-outbound guard 必须 fail-closed。GateL-1B overall freeze 后 OKX / Binance 默认 endpoint 均为 `disabled://` sentinel，且 runtime credential 默认 unconfigured；这只代表 No-Real hardening baseline 已冻结，不代表允许真实 OKX / Binance 接入。 |
| 2 | 当前是否允许启用 LIVE？ | **不能。** LIVE DISABLED；PAPER / LIVE 硬隔离；任何 LIVE 能力即使只读也须另起安全审计。 |
| 3 | 当前是否允许实现 RealClient？ | **不能。** RealClient NOT IMPLEMENTED，且本轮及 GateL planning 范围内禁止实现。 |
| 4 | 当前是否允许读取真实 API key？ | **不能。** 默认 credential permission probe = NoReal / SKIPPED；`.env.example` placeholder-only；secret scan + redaction gate 已冻结。 |
| 5 | 当前是否允许 DH runtime 接入交易执行链路？ | **不能。** DH runtime NOT INTEGRATED；DH 仅允许 contract / mock / documentation work line（Integration-0），不得进入 NQ 交易执行链路。 |
| 6 | GateL 的最小可执行批次是什么？ | GateL-1（adapter contract review，纯文档/契约盘点）。见 §4。 |
| 7 | GateL 哪些内容只是 plan，哪些后续可以 implementation？ | 只 plan：真实 endpoint / 真实凭证 / RealClient / real provider / 真实 permission probe / 真实 WS / LIVE execution。后续可 implementation（仍 no-real）：契约文档、no-real/stub 合同、mock/contract test、capability/error model 文档、readiness checklist。见 §7。 |
| 8 | GateL 与 GateK CI/security 的边界是什么？ | GateL 不得削弱 GateK 已冻结的 no-outbound guard / EnvSafety / secret scan / redaction / NoReal probe / sentinel endpoint；任何触碰这些的改动须重新 CI evidence + freeze。见 §8。 |
| 9 | GateL 与未来 Integration-1 的关系是什么？ | GateL = NQ 侧 no-real 交易/行情边界就绪；Integration-1 = NQ-DH 真实只读通道。二者独立；GateL 不依赖 DH，也不授权 DH runtime。见 §9。 |
| 10 | GateL 完成后才允许进入哪类后续任务？ | GateL planning 完成只解锁“no-real 契约/mock/checklist 实现工作线”的逐项 review；真实交易所接入仍须在 readiness checklist 全部满足 + 专项安全审计 + 用户显式授权后另起 Gate（不在 GateL）。见 §10。 |

---

## 1. Task classification / 本轮范围

- 任务等级：GateL planning-only（对照全局规范属 M 级文档多文件变更，docs-only）。
- 本轮做什么：只读盘点 adapter / marketdata / permission probe / paper execution / risk / ledger 现状；产出 `GATEL_PLAN.md`；同步 README / ROADMAP / STATUS / TESTING / WORKLOG 的 GateL planning 状态。
- 本轮不做什么：见 §3 GateL forbidden boundaries。

## 2. 当前事实（current state，已只读核对）

GateJ completed；GateK planning baseline FROZEN / ACCEPTED；GateK CI mainline COMPLETED / ACCEPTED；Batch 1–5 全部 FROZEN/ACCEPTED 或 CLOSED。

- AI：NOT STARTED。
- DH runtime：NOT INTEGRATED。
- LIVE：DISABLED。
- RealClient：NOT IMPLEMENTED。
- real exchange provider：NOT IMPLEMENTED。
- real OKX / Binance permission probe adapter：NOT IMPLEMENTED。
- 默认 credential permission probe：NoReal / SKIPPED / REAL_EXCHANGE_PROBE_DISABLED。
- no-outbound guard：FROZEN / ACCEPTED（fail-closed，覆盖 OKX/Binance/Bybit/Bitget/Gate/Coinbase/Kraken/Crypto/Hyperliquid）。
- Runtime 默认 endpoint：OKX / Binance 均已在 GateL-1B hardening 中收口为 `disabled://` sentinel；runtime credential 默认 `*.unconfigured()`。该事实只代表 No-Real hardening baseline 已冻结，不能描述为真实交易所 ready 或 future-real-ready。

### 2.1 现有 no-real 资产盘点（GateL 不是从零搭建，而是 review / freeze 既有边界）

下列组件**已存在**。GateL planning 的对象是这些既有契约；其中包含 no-real / stub / fixture / disabled 边界，也包含不得在当前阶段启用的 legacy network-capable 代码，不能把后者误写成已满足 No-Real 或“待新建”。

- `nq-adapter-api`（契约）：`TradingAdapter`、`MarketDataAdapter`、`AccountAdapter`、`HistoricalKlineAdapter`，及 `NoopMarketDataAdapter` / `NoopAccountAdapter`；model：`AdapterOrderRequest` / `AdapterOrderAck` / `AdapterOrderSnapshot` / `AdapterOrderQuery` / `AdapterOpenOrdersQuery` / `AdapterCancelRequest` / `AdapterCancelAck` / `AdapterTradeReport` / `AdapterError` / `AdapterResultCategory`（9 类：SUCCESS / ACCEPTED / NOT_FOUND / DEFERRED / RETRYABLE_FAILURE / FATAL_FAILURE / THROTTLED / AUTH_FAILURE / REMOTE_UNAVAILABLE） / `AccountSnapshot` / `AccountBalanceSnapshot` / `PositionSnapshot` / `HistoricalKlineBar` / `HistoricalKlineRequest` / `MarketDataSubscriptionRequest` / `MarketDataSubscriptionAck`。
- `nq-adapter-okx`：`OkxExchangeAdapter`、`OkxHttpClient`、`OkxRequestSigner`、`OkxRuntimeConfig`（默认 `disabled://` sentinel）、`OkxPermissionProbeBoundary`（forbidden endpoint：`/trade/order`、`/trade/cancel`、`/asset/withdraw`、`/asset/transfer`、`/account/transfer`；脱敏 classify）、`OkxHistoricalKlineAdapter`、`OkxInstrumentsCache`、`OkxBootstrapFallbackFactory`（stub baseUrl `http://127.0.0.1`，authenticated 抛 `OKX_ADAPTER_BOOTSTRAP_STUB`）、`OkxErrorClassifier` / `OkxErrorCode`、`OkxWsClient` 及 WS 协议栈（当前不连真实 WS）。
- `nq-adapter-binance`：`BinanceExchangeAdapter`、`BinanceHttpClient`、`BinanceHmacRequestSigner` / `BinanceEd25519RequestSigner`、`BinanceRuntimeConfig`、`BinancePermissionProbeBoundary`、`BinanceHistoricalKlineAdapter`、`BinanceFiltersCache`、`BinanceExchangeInfoClient`、`BinanceWsClient` / `BinanceListenKeyClient` 及 WS 协议栈。GateL-1B-A 已将 runtime 默认 REST/WS endpoint 冻结为 `disabled://` sentinel；这不授权真实 Binance 接入。
- `nq-core` marketdata：`HistoricalKlineProvider`、`HistoricalMarketDataPort`、`MarketdataBarRepository`、`MarketdataDatasetRepository`、`InstrumentCatalogRepository` / `InstrumentCatalogService` / `InstrumentCatalogSyncService`、`FixtureMarketdataRegistry` / `FixtureMarketdataDataset`、`BarInterval`、`HistoricalBar`、`HistoricalDatasetSpec`、`HistoricalMarketDataQuery`、`MarketdataBarIngestService`、`MarketdataIngestionService`。
- `nq-core` trading：`OrderCommandService`、`OrderCommandWriteService`、`OrderLifecycleService`、`OrderAggregate`、`OrderRecord`、`OrderRepository`、`OrderStateMachine` / `InMemoryOrderStateMachine`、`PlaceOrderRequest` / `CancelOrderRequest`、`ExecutionCommandMapper`、`StrategyExecutionGateway` / `OrderCommandStrategyExecutionGateway`、`TradingOrderStatusSnapshot`。
- `nq-risk`：`RiskGate` / `NoopRiskGate`、`PreTradeRiskService`、`RiskRuleRegistry`、`RiskRule` 及规则（`AccountTradingEnabledRule`、`DuplicateRequestRule`、`KillSwitchRiskRule` / `KillSwitchService`、`MaxOrderAmountRule`、`MinNotionalRule`、`OrderPrecisionRule`、`RateLimitRule`、`SymbolEnabledRule`）、`RiskContext`、`RiskDecisionResult`。
- `nq-ledger`：`LedgerService` / `NoopLedgerService`、`TradeLedgerPostingService`、`LedgerEntry`。
- 安全基线（GateK 已冻结）：`EnvSafetyValidator`、`NoOutboundExchangeGuardTest`、`NoRealExchangeCredentialPermissionProbePort(Test)`。

## 3. GateL forbidden boundaries（GateL 全程禁止）

1. 禁止接真实 OKX / Binance / Bybit / Bitget / Coinbase / Gate / Kraken / Crypto.com / Hyperliquid。
2. 禁止实现 RealClient / real provider / 真实 permission probe adapter。
3. 禁止启用 LIVE；禁止真实下单、撤单、转账、提现。
4. 禁止读取真实凭证（`.env` / API key / secret / token / pem / key / jks / p12 / 日志 dump / backup）。
5. 禁止外联；禁止真实 WS / orderbook / trades / funding / open interest 实时行情。
6. 禁止接 AI；禁止接 DH runtime 进入交易执行链路。
7. 禁止修改 Java / TS / Python 代码、API、migration、historical migration、`.github/workflows/ci.yml`、前端页面、research、deploy / scripts。
8. 禁止削弱 GateK 已冻结的 no-outbound guard / EnvSafety / secret scan / redaction / NoReal probe / sentinel endpoint。
9. 禁止 adapter 绕过 NQ 风控（`RiskGate`）、订单状态机（`OrderStateMachine`）、credential governance、ledger/audit。
10. 禁止把 GateL plan 写成 GateL implementation started。

## 4. GateL workstream plan（拆分批次）

> 所有批次默认 docs-only / contract-only / mock-only；任何后续 implementation 仍须逐项 review，且不得越过 §3。

### GateL-1：Exchange adapter contract review（**最小可执行批次**）

- 盘点 `nq-adapter-api` 现有 port / DTO / error model / result category，明确哪些是稳定契约、哪些需补文档。
- 明确 capability model：每个 venue（okx / binance / future-real）声明支持的能力（place / cancel / query / list-open-orders / account / position / historical kline / marketdata subscription），区分“已实现 no-real”“stub”“future real（仅规划）”。
- 明确边界规则：adapter 只做交易所方言映射，不得直接写库、不得绕过 `RiskGate` / `OrderStateMachine` / credential governance / ledger。
- 明确哪些接口未来允许真实实现（read-only：historical kline / instrument catalog / 只读 permission probe），哪些必须继续 no-real（任何下单 / 撤单 / 转账 / 提现，须 LIVE Gate + 安全审计）。
- 产出：adapter contract review 文档（capability/error matrix），不改代码。

### GateL-2：Market data no-real pipeline

- 盘点 historical OHLCV、instrument catalog、marketdata dataset、bars 查询链路现状（`HistoricalKlineProvider` / `HistoricalMarketDataPort` / `MarketdataBarRepository` / `InstrumentCatalogService` / `FixtureMarketdataRegistry`）。
- 规划 future exchange marketdata provider 的 no-real / stub 合同（fixture-first，禁止外联）。
- 明确分层：historical（已具备 fixture/数据库链路）/ realtime / websocket / orderbook / trades / funding / open interest；后四类本轮仅做分层定义，不实现、不接 WS、不外联。
- 产出：marketdata 分层与 no-real provider 合同文档。

### GateL-3：Permission probe contract

- 盘点 credential permission probe guarded baseline（`NoRealExchangeCredentialPermissionProbePort`、`OkxPermissionProbeBoundary`、`BinancePermissionProbeBoundary`）。
- 保持默认 NoReal / SKIPPED / REAL_EXCHANGE_PROBE_DISABLED。
- 规划 future real permission probe 的接口/输入输出/脱敏/audit/rate limit/no-outbound/安全开关：仅 allowlisted read-only endpoint；forbidden endpoint（order/cancel/withdraw/transfer）fail-closed；raw response 不回传 Service / audit metadata；错误脱敏分类（TIMEOUT / RATE_LIMITED / EXCHANGE_5XX / AUTH_FAILED / IP_ALLOWLIST_FAILED / EXCHANGE_ERROR）。
- 产出：permission probe 契约文档；**不实现真实 OKX / Binance probe**。

### GateL-4：Paper-first execution boundary

- 明确 Paper execution / future real execution / `RiskGate` / `OrderStateMachine` / ledger·audit 的隔离合同。
- 明确任何 future real execution 必须经过：pre-trade `RiskGate`（kill switch / account-enabled / symbol-enabled / min-notional / max-amount / precision / rate-limit / duplicate）→ `OrderStateMachine` 合法流转 → ledger posting + audit；不得无条件覆盖订单状态。
- 规划 future live gate 前置条件（见 §5 readiness checklist）。
- 产出：paper/live execution 隔离合同文档；**不实现 LIVE / 真实 execution**。

### GateL-5：Real exchange readiness checklist

- 输出未来接 OKX / Binance 前的 checklist（见 §5）。
- 明确 checklist 只是规划，不代表允许接真实交易所。

## 5. Real exchange readiness checklist（仅规划，不授权接入）

接入真实交易所前，下列每项必须满足并通过专项安全审计 + 用户显式授权（任一不满足即 fail-closed，禁止接入）：

- Credential policy：凭证仅经环境变量 / 安全存储注入；NQ credential lifecycle（active material / enable / rotate / revoke governance）就绪；禁止入库/日志/artifact/test report。
- IP allowlist：交易所侧 IP 白名单绑定；`IP_ALLOWLIST_FAILED` 可被探测与告警。
- Permission scope：最小权限（read-only 优先；下单权限单独审批）；禁止 withdraw / transfer 权限默认开启。
- Dry-run / sandbox / testnet：先在 testnet / sandbox 跑通；real 与 sim/testnet 硬隔离。
- Rate limit：客户端限流 + 交易所限流对齐；`THROTTLED` / `RATE_LIMITED` 退避。
- Circuit breaker：连续失败 / 5xx / auth 失败熔断。
- Kill switch：`KillSwitchService` 可一键停用交易；停用即 fail-closed。
- Audit：下单 / 撤单 / 状态流转 / 风控拒绝 / 凭证生命周期全审计，字段脱敏。
- Rollback：任何 LIVE 能力可快速回退到 PAPER / disabled。
- Incident plan：故障/异常/资损/凭证泄漏的处置与上报流程。
- No-outbound / EnvSafety / secret-scan baseline：接入改动须重新 CI evidence + freeze，不得复用历史 freeze。

## 6. Risks（planning 风险）

### P0

- 无。本轮 docs-only，不触碰代码 / workflow / 真实凭证 / 真实交易所，不引入运行时风险。

### P1

- **GateL-1B No-Real hardening baseline = FROZEN / ACCEPTED（2026-06-23）**：P1-A Binance 默认 endpoint sentinel、P1-B OKX/Binance runtime credential source hardening、P1-C order ack/snapshot rawPayload producer suppression、P1-D Noop marketdata no-real status hardening 均已 **CLOSED / ACCEPTED / FROZEN**；overall freeze 见 `GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`。`rawPayload` 字段删除仍 **NOT DONE / SEPARATE COMPATIBILITY TASK**。现有合同仍不得标记 future-real-ready，不代表允许真实 OKX/Binance、真实 marketdata、LIVE、真实 credential、AI 或 DH runtime；adapter readiness 仍 **NOT READY / NOT FROZEN / NOT AUTHORIZED**。
- **Roadmap GateL 语义冲突 = RESOLVED / CLOSED（2026-06-22，`NQ-GATEL-CANONICAL-ROUTE-SYNC`）**：原冲突为现有路线图把 GateL 标注为「AI Paper Trading」，而本计划把 GateL-PLAN 范围定为「No-Real 交易适配器与市场数据边界就绪」。用户已裁决 canonical：**GateL = No-Real Exchange / MarketData Readiness**；旧口径「GateL = AI Paper Trading」作废；**AI Paper Trading 后移到 GateM**（后续独立 AI/DH 阶段，当前 NOT STARTED），AI 小资金 LIVE → GateN，美股 → GateO，A 股 → GateP。README / docs-current README / ROADMAP / STATUS / GATEL_PLAN / TESTING / WORKLOG 已同步该 canonical。本项不再阻断 GateL-1。

### P2

- 既有 adapter 契约文档化程度不均（capability/error matrix 未集中成文）；GateL-1 应补齐，但属文档完善，非阻断。
- `application-ci.yml` / `application-paper.yml` 命名差异（GateK 遗留 P3）与 profile 默认值集中说明可在 GateL-4 一并梳理；非阻断。

## 7. GateL：plan-only vs future-implementation 边界

| 类别 | 只能 plan（禁止本阶段实现） | 后续可 implementation（仍 no-real，须逐项 review） |
| --- | --- | --- |
| Adapter | RealClient / real provider / 真实下单·撤单·转账 | capability/error matrix 文档、no-real/stub 合同、contract test（mock） |
| Marketdata | 真实 WS / orderbook / trades / funding / OI 实时接入、外联 provider | 分层定义文档、fixture-first no-real provider 合同、historical mock test |
| Permission probe | 真实 OKX/Binance probe、真实凭证读取 | probe 契约文档、脱敏/audit/限流规则、boundary 单测（no-outbound） |
| Execution | LIVE execution、真实 ledger 资金变动 | paper/live 隔离合同文档、风控+状态机+审计前置规则文档 |
| Checklist | 真实接入 | readiness checklist 文档维护 |

## 8. GateL 与 GateK CI/security 边界

- GateK CI/security baseline（9-job 管线 + no-outbound guard + EnvSafety + secret scan + redaction + NoReal probe + sentinel endpoint）= FROZEN / ACCEPTED，是 GateL 的下界，GateL 不得削弱。
- 任何触碰 `ci.yml` / no-outbound guard / `EnvSafetyValidator` / NoReal probe / `OkxRuntimeConfig` / `BinanceRuntimeConfig` / adapter construction·bootstrap·recovery·catalog sync / `application*.yml` defaults / `.env.example` / 任何 real exchange adapter·provider·RealClient 路径的改动，须重新 review + CI evidence + freeze/addendum（依 GateK 卷宗 regression boundary）。
- GateL planning 本身不产生 CI evidence 需求（docs-only）。

## 9. GateL 与 Integration-1 关系

- GateL = NQ 侧 no-real 交易/行情/probe/execution 边界就绪（与 DH 无关）。
- Integration-1 = NQ-DH 真实只读通道（须先修复 DH P1-4 残留：rate limit / memory cap / replay nonce 持久化），是独立工作线。
- GateL 不依赖 DH，也不授权 DH runtime 进入 NQ 交易执行链路；Integration-0 仍仅 contract / mock / docs。

## 10. Recommended next task

- **GateL-1A：Exchange adapter contract review freeze = PASS / FROZEN / ACCEPTED**（docs-only）。只冻结 review 事实、P1/P2 与处理顺序；adapter readiness = NOT READY / NOT FROZEN，P1/P2 均保持 OPEN。
- 后续顺序冻结为：GateL-1B No-Real hardening plan → GateL-1C capability matrix contract → GateL-1D error model contract → GateL-1E future-real readiness checklist refinement。GateL-1B plan + plan review 已 **PASS / FROZEN / ACCEPTED**；GateL-1B-A（commit `04ddb774`）、GateL-1B-B（commit `ad7f58b0`）、GateL-1B-C producer suppression（commit `316497ad`）、GateL-1B-D（commit `7e442eb7`）均 **CLOSED / ACCEPTED / FROZEN**；GateL-1B overall No-Real hardening baseline 已 **PASS / FROZEN / ACCEPTED**（详见 `GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`）。GateL-1C capability matrix contract 已 **PASS / FROZEN / ACCEPTED**（详见 `GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md` / `GATEL_1C_CAPABILITY_MATRIX_CONTRACT_REVIEW.md` / `GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md`），冻结能力状态枚举与 Noop / OKX / Binance / future-real / permission probe / marketdata placeholder 矩阵；`rawPayload` 字段删除未做，另起兼容性任务；adapter readiness 仍 **NOT READY / NOT FROZEN / NOT AUTHORIZED**。GateL-1D error model contract 已 **PASS / FROZEN / ACCEPTED（contract-only）**（详见 `GATEL_1D_ERROR_MODEL_CONTRACT.md`），冻结 error status enum（`NO_REAL_DISABLED` / `NETWORK_DISABLED` / `CREDENTIALS_MISSING` / `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` / `RATE_LIMITED` / `VENUE_UNAVAILABLE` / `INVALID_SYMBOL` / `UNSUPPORTED_OPERATION` / `RISK_REJECTED` / `ORDER_STATE_REJECTED` / `LEDGER_REJECTED` / `RAW_PAYLOAD_SUPPRESSED` / `UNKNOWN_REQUIRES_REVIEW`）、retry 语义（retryable=false 终态集合；conditional 仅受控 RATE_LIMITED / VENUE_UNAVAILABLE；UNKNOWN fail-closed）与 adapter / venue / trading / marketdata / credential / permission 错误矩阵，并映射既有 `AdapterResultCategory`；真实交易所错误处理须另起 Gate。GateL-1D error model contract review 已 **PASS / REVIEW ACCEPTED（contract-only）**（详见 `GATEL_1D_ERROR_MODEL_CONTRACT_REVIEW.md`），复核 enum / 映射 / retry / 路径矩阵 / fail-closed / 禁止解释并以 `git grep` 校验源码事实，P0/P1=0、P2 为既知 follow-up 不阻断冻结。下一步 `NQ-GATEL-1D-ERROR-MODEL-CONTRACT-FREEZE`，不得直接进入 real adapter。
- 真实交易所接入仍须在 readiness checklist 全部满足 + 专项安全审计 + 用户显式授权后**另起 Gate**，不在 GateL 范围内。

## 11. 验收标准（GateL planning 验收）

- 本计划 baseline 已 PASS / ACCEPTED；路线语义 P1 已关闭；GateL-1 adapter contract P1/P2 已由 GateL-1A freeze review 保留为 OPEN，且无安全边界被削弱。
- README / ROADMAP / STATUS / TESTING / WORKLOG 的 GateL planning 状态一致，且未把 plan 写成 implementation started。
- working tree 仅 docs/current 变更；backend / frontend / research / scripts / deploy / migration / workflow diff 为空。

## 12. 回滚方式

删除 `docs/current/GATEL_PLAN.md`，并还原 README / ROADMAP / STATUS / TESTING / WORKLOG 的本轮 GateL 状态入口即可；无代码 / workflow / DB / runtime / credential / provider / exchange 副作用（docs-only）。

## 13. 边界声明

No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。GateL implementation NOT STARTED。
