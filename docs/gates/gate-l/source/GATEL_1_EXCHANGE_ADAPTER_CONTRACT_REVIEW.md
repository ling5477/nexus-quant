# GateL-1 Exchange Adapter Contract Review

任务：NQ-GATEL-1-EXCHANGE-ADAPTER-CONTRACT-REVIEW
日期：2026-06-22
分支：dev
结论：**CONDITIONAL PASS**
状态：**GateL-1 REVIEW / DOCS-CONTRACT ONLY**；GateL implementation **NOT STARTED**。

> 本文只冻结审查结论，不冻结现有 adapter 为 future-real-ready，不授权真实交易所接入。
> LIVE 继续 DISABLED；AI NOT STARTED；DH runtime NOT INTEGRATED；真实 permission probe / RealClient 不在本轮实现。

## 1. Task classification

- Primary：`DOCUMENTATION`。
- Auxiliary：`ARCHITECTURE_REVIEW`、`EXCHANGE_ADAPTER_CONTRACT_REVIEW`、`SECURITY_BOUNDARY_REVIEW`。
- Task level：L 级 review-only / docs-contract-only。
- Primary skill：`nq-dh-workflow-router`，用于固定 GateL、No-Real、credential、LIVE 与模块边界。
- Implementation skill：未使用；本轮禁止代码实现。

## 2. Scope

### 已审查

- `backend/nq-adapter-api/**`：adapter ports、request/ack/snapshot/error model、Noop stub。
- `backend/nq-adapter-okx/**`：trading REST、historical kline、runtime config、HTTP/WS、error classifier、permission boundary。
- `backend/nq-adapter-binance/**`：trading REST、historical kline、runtime config、HTTP/WS、error classifier、permission boundary。
- `backend/nq-core/**`：trading command/write/lifecycle/state machine、marketdata ports、credential permission probe service/port。
- `backend/nq-risk/**`：`RiskGate`、pre-trade rules、kill switch。
- `backend/nq-ledger/**`：ledger port 与 `TradeLedgerPostingService`。
- `backend/nq-api/**`：trading、exchange account、credential permission probe API。
- `docs/current/GATEL_PLAN.md`、`MODULES.md`、`ARCHITECTURE.md`、`API.md`、`DB_SCHEMA.md`、`STATUS.md`、`ROADMAP.md`、`README.md`、`TESTING.md`、`WORKLOG.md`。

### 未审查

- composition root、scheduler/reconcile 的具体 adapter wiring 不在用户允许的代码目录内，因此不对当前 bean 选择、启动顺序或运行时路由作完整结论。
- 未读取任何 `.env`、credential material、日志 dump、backup、key/certificate。
- 未访问外网、交易所、数据库、容器或 GitHub。

### 明确不涉及

- Java / TypeScript / Python 实现、API/migration/workflow 变更。
- 真实 provider、RealClient、真实 permission probe、LIVE、下单/撤单/转账、AI、DH runtime。

## 3. Current adapter state

1. `TradingAdapter` 已冻结 place/cancel/get/list-open-orders 四类交易方法；`MarketDataAdapter`、`AccountAdapter`、`HistoricalKlineAdapter` 已分开存在。
2. OKX/Binance trading adapter 不是纯 NoReal stub。两者均包含真实 REST 下单、撤单、查单、挂单和成交查询代码，以及签名 HTTP/WS client。
3. OKX 默认 REST/WS endpoint 已是 `disabled://` sentinel，请求期 loud fail-closed；但显式环境变量仍可配置网络 endpoint。
4. Binance 默认配置仍硬编码 testnet/mainnet REST 与 WS URL；`env=dome` 默认指向 testnet，`env=real` 默认指向 mainnet，不符合 GateL No-Real 默认边界。
5. OKX/Binance runtime config 都会从进程环境解析 API credential，并由 adapter 默认构造路径使用。这是 legacy network-capable 设计，不是 future-real credential-governed 合同。
6. permission probe 当前只有 core port/service 与 OKX/Binance endpoint/error boundary；在本轮允许范围内未发现真实 permission probe provider。当前合同应继续保持 NoReal / SKIPPED。
7. `NoopMarketDataAdapter` 对 bars/trades/orderbook 返回 `subscribed=true`，`NoopAccountAdapter` 返回空数据与 `SIM` 快照，但响应没有显式 `STUB/NO_REAL` 标记。

### 3.1 关键证据

| 结论 | 文件 / 行号 |
| --- | --- |
| Trading port 只有 place/cancel/get/list-open-orders | `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/service/TradingAdapter.java:20-49` |
| 当前结果分类只有 9 类 | `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/AdapterResultCategory.java:10-20` |
| order ack/snapshot 暴露 `rawPayload` | `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/AdapterOrderAck.java:12-25`；`AdapterOrderSnapshot.java:25-41` |
| Noop marketdata 返回普通成功 | `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/service/NoopMarketDataAdapter.java:28-45` |
| OKX 默认 endpoint 为 sentinel | `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxRuntimeConfig.java:43-49` |
| OKX trading adapter 含真实 REST order endpoint/call | `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxExchangeAdapter.java:47-55`、`:107-170`、`:204-225` |
| Binance 默认 REST/WS 指向 testnet/mainnet | `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceRuntimeConfig.java:47-51`、`:73-90` |
| Binance trading adapter 含真实 private order call | `backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/service/BinanceExchangeAdapter.java:42-44`、`:90-200` |
| adapter runtime config 解析进程 credential | `OkxRuntimeConfig.java:59-109`；`BinanceRuntimeConfig.java:63-100` |
| RiskGate 与状态机在 adapter IO 前执行 | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java:93-268`；adapter 调用见 `OrderCommandService.java:123-143` |
| trading API 进入 core command service | `backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/TradingVerificationController.java:230-290` |

## 4. Adapter API findings

### 4.1 当前方法边界

| Port | 当前方法 | 结论 |
| --- | --- | --- |
| `TradingAdapter` | `placeOrder` / `cancelOrder` / `getOrder` / `listOpenOrders` | 交易命令与查单边界清楚，但缺 capability、execution mode、credential binding 与 no-real 状态声明。 |
| `MarketDataAdapter` | `subscribeBars` / `subscribeTrades` / `subscribeOrderBook` | 与 trading 已分离；仅 realtime subscription，未覆盖 ticker，且没有 concrete venue capability contract。 |
| `HistoricalKlineAdapter` | `fetchHistoricalKlines` | historical 与 realtime 分离合理；异常模型独立，未复用统一 adapter error contract。 |
| `AccountAdapter` | balances / positions / account snapshot | 与 trading 分离合理；当前仅 Noop 实现，缺 permission scope 与数据新鲜度语义。 |
| `ExchangeCredentialPermissionProbePort` | `probe` | 位于 core credential governance 边界；不应并入 `TradingAdapter`。 |

### 4.2 合同缺口

- 没有 `AdapterCapability` / `VenueCapability` 合同，调用方不能在调用前确定 SPOT、marketdata、account、order、sandbox、WS 支持范围。
- `AdapterResultCategory` 只有 9 个宽分类，不能稳定区分 permission denied、IP allowlist、invalid symbol、unsupported、network/no-real disabled。
- `AdapterError.retryable` 与 category 可形成不一致组合，缺少单一 retry policy 事实源。
- `AdapterOrderAck` / `AdapterOrderSnapshot` 暴露 `rawPayload`。该字段可能把 provider 原始响应带入 core、持久化或日志，future-real 前必须移除、隔离或强制 allowlist/redaction。
- adapter request 只有 `accountId`，默认 adapter 却绑定进程级 credential；无法证明 credential 与 account/tenant/active-version 的治理绑定。
- `NoopMarketDataAdapter` 返回成功订阅，容易把 stub 误判成真实行情能力；stub 结果必须显式携带 `STUB` / `NO_REAL_DISABLED`。

## 5. No-Real / Stub / Future-Real findings

| 层级 | 定义 | 当前结论 | 冻结要求 |
| --- | --- | --- | --- |
| No-Real | 无网络、无真实 credential、所有副作用 fail-closed | OKX 默认 endpoint 符合；Binance 默认 endpoint 不符合 | endpoint/config/constructor 三层均须 no-real；不能只依赖缺 credential 或外部 workflow guard。 |
| Stub | 可预测的本地占位，不伪装真实成功 | Account stub 基本明确；MarketData stub 返回成功，标记不足 | 返回必须显式 `STUB`，不得产生“已连接/已订阅真实 venue”语义。 |
| Future-Real | 仅合同和 checklist，不可运行 | 现有 OKX/Binance 类已有网络能力，不能称为“未实现代码” | 只能标为 legacy network-capable / FUTURE_REAL_REQUIRES_GATE；另起 Gate 后方可治理、重构和启用。 |

当前不能把 OKX/Binance adapter 整体描述为“只是 no-real/stub”。准确口径是：**真实协议与网络调用代码已存在；当前允许状态仍必须是 no-real disabled，且合同与配置尚未达到 future-real readiness。**

## 6. Capability matrix

状态值：`NO_REAL` / `STUB` / `NOT_IMPLEMENTED` / `FUTURE_REAL_REQUIRES_GATE`。

| Capability | OKX | Binance | Contract decision |
| --- | --- | --- | --- |
| venue | `OKX` | `BINANCE` | 已有字符串标识；后续改为受控 venue id。 |
| spot | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | 现有实现聚焦 SPOT，但当前不得真实运行。 |
| futures / perpetual | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | GateL 不扩展。 |
| margin | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | GateL 不扩展。 |
| historical OHLCV | `NO_REAL` | `FUTURE_REAL_REQUIRES_GATE` | 两者有 public REST 代码；OKX 默认 disabled，Binance 默认可外联，后者须先收口。 |
| realtime ticker | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | `MarketDataAdapter` 无 ticker 方法。 |
| orderbook | `STUB` | `STUB` | adapter-api Noop subscription only；无 venue capability 标记。 |
| trades stream | `STUB` | `STUB` | adapter-api Noop subscription only；私有成交 WS 不等于公共行情 trades。 |
| account balance | `STUB` | `STUB` | `NoopAccountAdapter` only。 |
| order placement | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | 代码存在；当前禁止调用。 |
| order cancel | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | 代码存在；当前禁止调用。 |
| order query / open orders | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | 代码存在；属于 private API。 |
| permission probe | `NO_REAL` | `NO_REAL` | 仅 boundary/classifier；真实 provider 未在允许范围内发现。 |
| testnet / sandbox | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | testnet 仍是外部网络，不等于 No-Real。 |
| websocket | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | client/protocol 代码存在；当前禁止连接。 |
| rate limit policy | `STUB` | `STUB` | 只有错误分类/重连参数，缺统一 quota、backoff、budget 合同。 |
| credential scope requirement | `NOT_IMPLEMENTED` | `NOT_IMPLEMENTED` | 缺 account/tenant/active-version/scope binding contract。 |

## 7. Error model review

### 7.1 建议标准

| Error code | Retry | Fail-closed / handling |
| --- | --- | --- |
| `AUTH_FAILED` | No | 立即 fail-closed；禁用调用路径并告警，不自动重试。 |
| `PERMISSION_DENIED` | No | fail-closed；要求人工修正最小权限。 |
| `IP_NOT_ALLOWED` | No | fail-closed；不得回退到其他 credential/endpoint。 |
| `RATE_LIMITED` | Yes, bounded | 尊重 retry-after + jitter + 最大次数/预算；交易命令先 query-confirm，禁止盲重下单。 |
| `VENUE_UNAVAILABLE` | Yes, bounded | 熔断、退避；命令状态保持可恢复，不伪造拒单或成功。 |
| `INVALID_SYMBOL` | No | fail-closed；修正 instrument catalog 后再发起新请求。 |
| `UNSUPPORTED_OPERATION` | No | fail-closed；capability pre-check 应先阻止调用。 |
| `NETWORK_DISABLED` | No | 预期安全拒绝；不允许 runtime fallback。 |
| `NO_REAL_DISABLED` | No | 预期安全拒绝；审计记录 no-real policy。 |
| `RISK_REJECTED` | No | 只由 NQ `RiskGate` 产生，不属于 venue adapter error。 |
| `UNKNOWN` | No by default | fail-closed；完成分类前不得自动重试。 |

### 7.2 现有映射评价

- OKX/Binance 已能表达 timeout、throttle、auth、remote unavailable、fatal/not-found/deferred 的一部分语义。
- permission probe boundary 使用另一套字符串分类（`TIMEOUT`、`RATE_LIMITED`、`EXCHANGE_5XX`、`AUTH_FAILED`、`IP_ALLOWLIST_FAILED`、`EXCHANGE_ERROR`），与 `AdapterResultCategory` 未统一。
- 缺 `PERMISSION_DENIED`、`INVALID_SYMBOL`、`UNSUPPORTED_OPERATION`、`NETWORK_DISABLED`、`NO_REAL_DISABLED`、`UNKNOWN` 的稳定平台码。
- `RISK_REJECTED` 必须保留在 core/risk，不得由 adapter 伪造。

## 8. Security boundary review

### 已成立的正向边界

- `OrderCommandService` 通过 `OrderCommandWriteService` 在 adapter IO 前执行 `RiskGate`，并把外部调用放在本地事务之外。
- `OrderStateMachine` / `OrderLifecycleService` 负责合法状态迁移，adapter 回执不能直接更新订单事实源。
- `TradeLedgerPostingService` 独立承载成交记账与审计；adapter-api 没有数据库写入端口。
- permission probe API 只接收非敏感控制字段，credential type/material 由服务端派生；默认合同应继续 NoReal / SKIPPED。

### 必须关闭的缺口

1. **P1：Binance default endpoint 不 fail-closed。** 默认 testnet/mainnet URL 与 GateL No-Real canonical 冲突。
2. **P1：adapter 自行读取进程 credential。** future-real 必须由 credential governance 通过短生命周期、account/tenant-bound handle 提供；adapter 不得自行解析真实 material。
3. **P1：统一合同允许 `rawPayload` 穿透。** 原始 provider payload 不得进入 core、audit、ledger 或 API；只允许 adapter 内短暂解析和脱敏摘要。
4. **P1：stub 成功语义不显式。** `NoopMarketDataAdapter` 不能以普通 success 表示真实订阅能力。
5. **P2：合同无法强制“只能经 NQ execution orchestration 调用”。** future-real wiring 必须让 `OrderCommandService -> RiskGate/StateMachine -> TradingVenueGateway -> adapter` 成为唯一写路径，并用 architecture/contract tests 防旁路。

future-real adapter 还必须同时受 no-outbound guard、EnvSafety、secret scan/redaction、credential lifecycle、rate limiter、circuit breaker、kill switch、audit 与 incident rollback 保护。任何一项缺失都必须 fail-closed。

## 9. Paper / Future-Real execution boundary

- Paper execution 是当前唯一允许的交易执行语义；LIVE 继续禁用。
- future-real 最小接入点应是 core 定义的 `TradingVenueGateway` 之后的受控 adapter bridge，而不是 Controller、strategy、scheduler 或 adapter 直接成为交易事实源。
- 唯一事实源：NQ `OrderStateMachine`、NQ `RiskGate`、NQ ledger/audit、NQ credential governance。
- adapter 只负责 venue protocol、签名、字段映射、错误归一化与受控 IO；不得拥有订单主状态、风控裁决、账本主权、credential lifecycle 或重试主权。
- place/cancel 的不确定结果必须 query-confirm/recovery；任何 retry 都需要 idempotency key、限次预算与状态机保护。

## 10. Findings

### P0

- 无。本轮未执行运行时、网络、凭证或交易写操作。

### P1

1. Binance runtime 默认 endpoint 指向真实外部 testnet/mainnet host，No-Real 默认边界不成立。
2. OKX/Binance adapter 默认构造链直接读取进程 credential，未绑定 NQ credential governance/account/tenant/active version。
3. `rawPayload` 是统一 order ack/snapshot 合同字段，存在原始 provider 数据跨层传播与敏感信息泄露风险。
4. Noop marketdata 以普通 success 返回，缺 STUB/NO_REAL 标记，可能造成能力误判。

### P2

1. capability matrix 尚未代码化，调用前无法 fail-fast 判断 venue/market/operation 支持度。
2. error model 与 permission probe 分类分叉，retry policy 未成为单一事实源。
3. historical/realtime/account/trading 的 port 已拆分，但 `MarketDataAdapter` 缺 ticker 与明确 public/private stream 边界。
4. 合同缺少 architecture rule，不能从类型层阻止 adapter 被 core orchestration 之外直接调用。

### P3

- 既有注释仍使用 GateC/GateD/GateH、DOME/REAL 等历史口径；后续合同实现批次应统一 canonical 文案，但不得在本轮改代码。

## 11. GateL follow-up split

1. **GateL-1A：adapter contract review freeze**
   冻结本文的 current-state、P1/P2 与 no-real/future-real 边界；不改代码。
2. **GateL-1B：capability matrix contract**
   先做 docs + mock contract tests 设计；定义 venue/market/operation/status/credential scope。
3. **GateL-1C：error model contract**
   统一 platform error code、retry policy、query-confirm 规则与 raw payload prohibition。
4. **GateL-1D：No-Real hardening plan**
   只规划 Binance sentinel、credential handle、stub marker、wiring guard 的最小实现批次；任何实现仍需独立 review。
5. **GateL-1E：future-real readiness checklist refinement**
   只完善 checklist；不实现、不授权真实交易所。

推荐下一任务：**NQ-GATEL-1A-EXCHANGE-ADAPTER-CONTRACT-REVIEW-FREEZE**。在 P1 进入独立 no-real hardening plan 前，不得把现有 adapter contract 标记为 FROZEN / future-real-ready。

## 12. Validation

- 执行：限定目录 `rg --files`、符号/调用点 grep、关键文件逐行只读检查。
- 执行：文档链接/路径、Gate 状态、禁止边界、术语与 diff 范围检查（见本轮 `TESTING.md` 记录）。
- 未执行 Maven/frontend/Python 测试：本轮只改文档，无运行时代码变更。
- 未执行网络、交易所、数据库、容器、GitHub Actions。
- 过程偏差：一次探索性 `rg` 使用了 `backend` 根目录而非允许模块白名单，返回了白名单外少量文件名/命中行。未打开这些文件、未读取敏感路径或值，且本报告所有结论与上表证据均只使用用户允许目录。后续检索已恢复白名单范围。

## 13. Rollback

删除本文件，并还原 `GATEL_PLAN.md`、`README.md`、`STATUS.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md` 的 GateL-1 条目即可。无代码、DB、workflow、credential、provider、交易或 runtime 副作用。

## 14. Final recommendation

**NQ-GATEL-1-EXCHANGE-ADAPTER-CONTRACT-REVIEW：CONDITIONAL PASS。**

通过的是 review/docs-contract 交付，不是现有 adapter 合同的 readiness。GateL implementation 仍 NOT STARTED；真实交易所接入仍须 P1 全部关闭、readiness checklist 全满足、专项安全审计和用户显式授权后另起 Gate。
