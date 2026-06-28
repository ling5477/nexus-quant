# GateL-1D Adapter Error Model Contract

任务：NQ-GATEL-1D-ERROR-MODEL-CONTRACT
日期：2026-06-23
分支：dev
任务类型：ARCHITECTURE_REVIEW + ERROR_MODEL_CONTRACT + ADAPTER_BOUNDARY_REVIEW + SECURITY_BOUNDARY_REVIEW + DOCUMENTATION
结论：**PASS / FROZEN / ACCEPTED（contract-only）**
状态：**GateL-1D error model contract FROZEN / ACCEPTED**；GateL-1C capability matrix contract **FROZEN / ACCEPTED**；GateL-1B overall No-Real hardening baseline **FROZEN / ACCEPTED**；adapter readiness **NOT READY / NOT FROZEN / NOT AUTHORIZED**。

> 本文件只定义 adapter error model 合同，不实现 adapter、不改交易逻辑、不新增 API / DTO / migration / workflow。
> 本合同只规定“错误如何被分类、是否可重试、如何 fail-closed、如何解释边界”，不启用任何真实交易所错误处理、不授权真实交易、不启用 LIVE。
> 任何 error status / category 在 GateL 内均不得解释为真实交易授权、可继续真实交易或 future-real readiness。

## 1. Scope

### 已检查（只读）

- `backend/nq-adapter-api/**`：`AdapterError`、`AdapterResultCategory`、`AdapterOrderAck`、`AdapterCancelAck`、`AdapterOrderSnapshot`、`MarketDataSubscriptionAck`、`NoopMarketDataAdapter`、`NoopAccountAdapter`、`HistoricalKlineAdapterException`、Trading/MarketData/Account/HistoricalKline ports。
- `backend/nq-adapter-okx/**`：`OkxErrorClassifier`、`OkxErrorCode`、`OkxApiException`、`OkxPermissionProbeBoundary`、`OkxRuntimeConfig`（`disabled://` sentinel）、`OkxBootstrapFallbackFactory`、`OkxHttpClient`（`OKX_CREDENTIALS_MISSING` fail-closed）。
- `backend/nq-adapter-binance/**`：`BinanceErrorClassifier`、`BinanceApiException`、`BinancePermissionProbeBoundary`、`BinanceRuntimeConfig`、`BinanceHttpClient`（`BINANCE_CREDENTIALS_MISSING` fail-closed）。
- `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md`、`GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md`。
- `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN.md`、`GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`。
- `docs/current/GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。

### 明确不涉及

- Java / TypeScript / Python 代码修改。
- API / DTO / migration / historical migration / workflow / frontend / research / scripts / deploy 修改。
- `.env`、API key、secret、token、pem、key、jks、p12、日志 dump、backup。
- OKX / Binance / Bybit / Bitget / Coinbase / Gate / Kraken / Crypto.com / Hyperliquid 外联。
- LIVE、AI、DH runtime、RealClient、real provider、真实 permission probe、真实 credential governance bridge。
- 下单、撤单、转账、提现。
- `AdapterOrderAck` / `AdapterOrderSnapshot` rawPayload 字段删除。

## 2. Current Frozen Baseline

- GateL canonical：**No-Real Exchange / MarketData Readiness**。
- GateL-1B overall No-Real hardening baseline：**FROZEN / ACCEPTED**。
- GateL-1C capability matrix contract：**FROZEN / ACCEPTED**。
- P1-A：**CLOSED / ACCEPTED**，Binance endpoint default sentinel / no-outbound hardening frozen。
- P1-B：**CLOSED / ACCEPTED**，OKX/Binance runtime credential source hardening frozen。
- P1-C producer suppression：**CLOSED / ACCEPTED**。
- P1-C rawPayload field deletion：**NOT DONE / SEPARATE COMPATIBILITY TASK**。
- P1-D：**CLOSED / ACCEPTED**，`NoopMarketDataAdapter` no-real disabled status hardening frozen。
- Adapter readiness：**NOT READY / NOT FROZEN / NOT AUTHORIZED**。
- LIVE：**DISABLED**。AI：**NOT STARTED**。DH runtime：**NOT INTEGRATED**。
- RealClient / real provider / real permission probe / real credential governance bridge：**NOT IMPLEMENTED**。

### 2.1 既有代码事实（error model 锚点，已只读核对）

本合同的 error status enum 是 GateL-1D 合同层词汇，用来约束未来实现；它**复用并约束**既有 `adapter-api` 错误模型，不新增 enum / DTO。既有事实：

- `AdapterResultCategory`（既有 9 类）：`SUCCESS`、`ACCEPTED`、`NOT_FOUND`、`DEFERRED`、`RETRYABLE_FAILURE`、`FATAL_FAILURE`、`THROTTLED`、`AUTH_FAILURE`、`REMOTE_UNAVAILABLE`。`isFailure()` 覆盖 RETRYABLE_FAILURE / FATAL_FAILURE / THROTTLED / AUTH_FAILURE / REMOTE_UNAVAILABLE；`isTerminalFailure()` 覆盖 FATAL_FAILURE / AUTH_FAILURE。
- `AdapterError(code, message, category, retryable)`：携带统一错误码、可审计 message、规范化 category 与 retryable 标记；便捷构造按 `retryable` 映射 RETRYABLE_FAILURE / FATAL_FAILURE。
- `NoopMarketDataAdapter`：bars / trades / order-book 订阅统一返回 `subscribed=false`、`code=NO_REAL_DISABLED`、`category=FATAL_FAILURE`、`retryable=false`，注释明确“重试不能创建真实订阅，返回 subscribed=true 会让调用方误判 no-real adapter 为 live provider”。
- `OkxErrorClassifier`：`ORDER_NOT_FOUND(51603)`→NOT_FOUND；`HTTP_TIMEOUT`→RETRYABLE_FAILURE；`50011` / HTTP 429→THROTTLED；`50113` / `50110` / `50035`→AUTH_FAILURE；HTTP≥500 / `HTTP_CLIENT_ERROR`→REMOTE_UNAVAILABLE；其余→FATAL_FAILURE。retryable 仅 RETRYABLE_FAILURE / THROTTLED / REMOTE_UNAVAILABLE。
- `BinanceErrorClassifier`：`BINANCE_CREDENTIALS_MISSING` / `-2015` / `-2014` / `-1022`→AUTH_FAILURE；`-2013` / `-2011`→DEFERRED；`-1003` / HTTP 429 / 418→THROTTLED；`HTTP_TIMEOUT` / `-1021`→RETRYABLE_FAILURE；`HTTP_CLIENT_ERROR` / HTTP≥500→REMOTE_UNAVAILABLE；其余→FATAL_FAILURE。retryable 含 RETRYABLE_FAILURE / THROTTLED / REMOTE_UNAVAILABLE / DEFERRED。
- `OkxPermissionProbeBoundary` / `BinancePermissionProbeBoundary`：`isForbiddenEndpoint` 对 order/cancel/withdraw/transfer endpoint 与 blank endpoint 返回 true（fail-closed）；`classify` 返回脱敏字符串 `TIMEOUT` / `RATE_LIMITED` / `EXCHANGE_5XX` / `AUTH_FAILED` / `IP_ALLOWLIST_FAILED` / `EXCHANGE_ERROR`，不回传 raw response。
- credential fail-closed：runtime config 默认 `*.unconfigured()`；`OkxHttpClient` / `BinanceHttpClient` 对 authenticated/signed 请求在网络前抛 `OKX_CREDENTIALS_MISSING` / `BINANCE_CREDENTIALS_MISSING`，失败信息不含 credential material。
- endpoint fail-closed：默认 `disabled://okx-not-configured` / `disabled://binance-not-configured`（及 WS sentinel），请求期非法 scheme loud fail-closed，不外联、不命中真实交易所、不被 no-outbound denylist 误判。

> 既有 `AdapterResultCategory` 没有 SUCCESS 之外的“成功”路径会被本合同放宽；本合同只增加解释约束，不降低既有 fail-closed 行为。

## 3. Error Status Enum（GateL-1D 合同词汇）

下列 status 是 GateL-1D 合同层错误分类词汇，用于统一 OKX / Binance / Noop / permission probe / future-real placeholder 的解释口径。每个 status 给出合同含义、映射到既有 `AdapterResultCategory` 的口径、以及授权效力。**所有 status 均不构成真实交易授权。**

| Status | Contract meaning | Maps to existing category | Authorization effect |
| --- | --- | --- | --- |
| `NO_REAL_DISABLED` | no-real / stub adapter 明确禁用，未创建真实订阅或真实执行；不是 success。 | `FATAL_FAILURE`（既有 `NoopMarketDataAdapter` 事实） | 不允许解释为 provider 就绪、不允许重试以期“变为 live”。 |
| `NETWORK_DISABLED` | endpoint 为 `disabled://` sentinel 或 no-outbound guard 拦截；请求期 fail-closed。 | `FATAL_FAILURE`（loud fail-closed） | 不允许外联；真实 endpoint 仅显式 env opt-in 且须另起 Gate。 |
| `CREDENTIALS_MISSING` | credential 未配置（`*.unconfigured()`），authenticated 请求网络前 fail-closed。 | `AUTH_FAILURE` | 不允许 fallback 到 env / system property / .env；须 credential governance bridge（另起 Gate）。 |
| `AUTH_FAILED` | 凭证无效 / 签名错误 / key 失效（OKX `50113` / `50110`，Binance `-2015` / `-2014` / `-1022`）。 | `AUTH_FAILURE` | 不允许自动提升权限、不允许继续交易、不允许重试同凭证。 |
| `PERMISSION_DENIED` | 凭证有效但缺少所需权限 scope（如只读 key 请求交易能力）。 | `AUTH_FAILURE`（terminal） | 不允许自动扩权、不允许绕过 permission probe forbidden endpoint 边界。 |
| `IP_NOT_ALLOWED` | IP allowlist 未通过（OKX `50035`，permission probe `IP_ALLOWLIST_FAILED`）。 | `AUTH_FAILURE` | 不允许自动改写网络出口、不允许绕过 allowlist、不允许继续交易。 |
| `RATE_LIMITED` | 交易所限流（OKX `50011` / HTTP 429；Binance `-1003` / 429 / 418）。 | `THROTTLED` | 仅在 backoff / circuit breaker / rate-limit policy 下条件重试，禁止无限重试。 |
| `VENUE_UNAVAILABLE` | 交易所 5xx / 不可达 / 网络瞬时故障（HTTP≥500 / `HTTP_CLIENT_ERROR` / timeout）。 | `REMOTE_UNAVAILABLE` / `RETRYABLE_FAILURE` | 仅在 circuit breaker / kill switch / no-outbound guard 下条件重试，不允许绕过 kill switch。 |
| `INVALID_SYMBOL` | symbol 不存在 / 不被支持 / 不在 instrument catalog。 | `FATAL_FAILURE` | 终态业务拒绝；不允许盲重试，须由调用方修正请求。 |
| `UNSUPPORTED_OPERATION` | 当前 adapter / venue 不支持该能力（如 margin / futures / private stream 在 GateL 禁止）。 | `FATAL_FAILURE` | 终态；不允许重试、不允许在 GateL 内开启被禁能力。 |
| `RISK_REJECTED` | NQ `RiskGate` 拒绝（资金、限额、kill switch、风控规则）。 | NQ core 事实源（adapter 透传，不自判） | 终态；adapter 不得绕过、不得重试以试图通过风控。 |
| `ORDER_STATE_REJECTED` | NQ `OrderStateMachine` 拒绝非法状态流转。 | NQ core 事实源（adapter 透传，不自判） | 终态；adapter 不得无条件覆盖订单状态、不得绕过状态机。 |
| `LEDGER_REJECTED` | NQ ledger / 账务一致性拒绝。 | NQ core 事实源（adapter 透传，不自判） | 终态；adapter 不得自行写 ledger、不得绕过账务一致性。 |
| `RAW_PAYLOAD_SUPPRESSED` | provider raw body / headers / signature 已被 producer suppression 抑制（安全边界）。 | 安全边界标记（非错误恢复入口） | 不是错误恢复入口；不允许为“恢复诊断”重新暴露 raw payload。 |
| `UNKNOWN_REQUIRES_REVIEW` | 证据不足、未分类错误；默认 fail-closed。 | `FATAL_FAILURE`（默认） | 默认不可重试；只有后续合同明确分类后才允许改判。 |

## 4. Retry Semantics

retry 语义是本合同的核心安全约束，用于防止把 disabled / missing / denied / unknown 误判为可重试或可继续真实交易。

### 4.1 retryable = false（终态，禁止重试）

- `NO_REAL_DISABLED`
- `NETWORK_DISABLED`
- `CREDENTIALS_MISSING`
- `AUTH_FAILED`
- `PERMISSION_DENIED`
- `IP_NOT_ALLOWED`
- `UNSUPPORTED_OPERATION`
- `RISK_REJECTED`
- `ORDER_STATE_REJECTED`
- `LEDGER_REJECTED`
- `RAW_PAYLOAD_SUPPRESSED`

附加约束：`INVALID_SYMBOL` 也为 retryable=false（业务终态拒绝，须修正请求）。上述任一 status 重试都不会改变结果，重试本身即被视为合同违规。

### 4.2 retryable = conditional（受控条件重试）

- `RATE_LIMITED`：必须有 backoff / circuit breaker / rate-limit policy；**禁止无限重试**；超过 policy 上限后须降级为终态并上报。
- `VENUE_UNAVAILABLE`：必须有 circuit breaker / kill switch / no-outbound guard；**禁止绕过 kill switch 或 no-outbound guard**；恢复重试须在受控开关下进行。

任何条件重试都必须有最大次数、退避策略与熔断；不得在事务中等待交易所长耗时响应。

### 4.3 retryable = false by default

- `UNKNOWN_REQUIRES_REVIEW`：默认 fail-closed、不可重试，除非后续合同明确分类。未分类错误不得被乐观当作瞬时错误重试。

## 5. Adapter / Venue Error Matrix

| Adapter / venue | 默认错误语义 | retry 口径 | 当前授权 |
| --- | --- | --- | --- |
| Noop adapter | marketdata 订阅 = `NO_REAL_DISABLED`（`FATAL_FAILURE` / `subscribed=false`）；account = 空 SIM snapshot（stub）。 | 全部 false（重试无法变为 live）。 | 非真实交易所 adapter；非 future-real-ready。 |
| OKX adapter | 既有 `OkxErrorClassifier` 映射；未配置时 `CREDENTIALS_MISSING` / `NETWORK_DISABLED` fail-closed。 | RATE_LIMITED / VENUE_UNAVAILABLE 受控；AUTH/PERMISSION/IP false。 | `NOT READY / NOT FROZEN / NOT AUTHORIZED`；非 future-real-ready。 |
| Binance adapter | 既有 `BinanceErrorClassifier` 映射；未配置时 `CREDENTIALS_MISSING` / `NETWORK_DISABLED` fail-closed。 | 同上；`DEFERRED`（`-2013`/`-2011`）须按受控查询语义处理，不得无限轮询。 | `NOT READY / NOT FROZEN / NOT AUTHORIZED`；非 future-real-ready。 |
| Future-real adapter placeholder | 规划占位；无运行时错误路径。 | 不适用（未实现）。 | `FUTURE_REAL_REQUIRES_GATE`；须另起 Gate + 安全审计 + readiness checklist + 用户授权。 |
| Permission probe placeholder | boundary classifier 仅产出脱敏字符串 + forbidden endpoint fail-closed。 | 不适用（无真实 probe）。 | 真实 probe `NOT_IMPLEMENTED`；须 allowlisted read-only endpoint 设计 + 另起 Gate。 |
| Marketdata no-real / future-real placeholder | no-real = `NO_REAL_DISABLED`；future-real = 占位。 | no-real false；future-real 不适用。 | 非真实 marketdata provider；future-real 须另起 Gate。 |

## 6. Trading Path Error Matrix

| 路径 | Noop | OKX / Binance（当前 no-real） | Future-real | retry / 边界 |
| --- | --- | --- | --- | --- |
| place order | `STUB_ONLY` / 无真实执行 | `CREDENTIALS_MISSING` / `NETWORK_DISABLED` fail-closed | `FUTURE_REAL_REQUIRES_GATE` | 须先过 `RiskGate` + `OrderStateMachine`；`RISK_REJECTED` / `ORDER_STATE_REJECTED` 终态不重试。 |
| cancel order | `STUB_ONLY` / 无真实执行 | fail-closed | `FUTURE_REAL_REQUIRES_GATE` | `ORDER_STATE_REJECTED` 终态；幂等撤单不得绕过状态机。 |
| query order | `STUB_ONLY` | fail-closed；`NOT_FOUND`（OKX `51603`）须区分于失败 | `FUTURE_REAL_REQUIRES_GATE` | `NOT_FOUND` 非可重试错误，是可审计降级；`DEFERRED` 须受控有限查询。 |
| account balance | 空 SIM snapshot | fail-closed | `FUTURE_REAL_REQUIRES_GATE` | 无真实账户访问；`CREDENTIALS_MISSING` 终态。 |
| REST private trading | `NOT_IMPLEMENTED` | fail-closed | `FUTURE_REAL_REQUIRES_GATE` | 须 credential bridge + RiskGate + state machine + ledger + audit + 另起 Gate。 |
| WebSocket private/user stream | `NOT_IMPLEMENTED` | `FORBIDDEN_IN_GATEL` | `FUTURE_REAL_REQUIRES_GATE` | 私有/用户流需凭证，GateL 内禁止。 |
| risk gate | 边界规则 | 不得绕过 `RiskGate` | `FUTURE_REAL_REQUIRES_GATE` | `RISK_REJECTED` 由 NQ core 决定，adapter 透传不自判。 |
| order state machine | 边界规则 | 不得绕过 `OrderStateMachine` | `FUTURE_REAL_REQUIRES_GATE` | `ORDER_STATE_REJECTED` 由 NQ core 决定。 |
| ledger / audit | `STUB_ONLY` | 不得绕过 NQ ledger / audit ownership | `FUTURE_REAL_REQUIRES_GATE` | `LEDGER_REJECTED` 由 NQ core 决定；adapter 不写 ledger。 |

## 7. Marketdata Path Error Matrix

| 路径 | Noop | OKX / Binance（当前 no-real） | Future-real | retry / 边界 |
| --- | --- | --- | --- | --- |
| REST public marketdata | `NOT_IMPLEMENTED` | `NETWORK_DISABLED` fail-closed | `FUTURE_REAL_REQUIRES_GATE` | 既有 historical adapter 不是当前真实 provider 授权。 |
| WebSocket public marketdata | `NO_REAL_DISABLED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | GateL 内无真实 WS public 订阅。 |
| historical OHLCV | `NOT_IMPLEMENTED` | `NETWORK_DISABLED` fail-closed；失败抛 `HistoricalKlineAdapterException`（不吞错） | `FUTURE_REAL_REQUIRES_GATE` | legacy GateH adapter 存在，但当前合同不授权真实交易所读取。 |
| ticker | `NOT_IMPLEMENTED` | fail-closed | `FUTURE_REAL_REQUIRES_GATE` | 无当前 ticker provider 授权。 |
| orderbook | `NO_REAL_DISABLED` | fail-closed | `FUTURE_REAL_REQUIRES_GATE` | Noop 返回 disabled，非 success。 |
| trades | `NO_REAL_DISABLED` | fail-closed | `FUTURE_REAL_REQUIRES_GATE` | Noop 返回 disabled，非 success。 |
| bars / trades / order-book subscription | `NO_REAL_DISABLED`（`subscribed=false`） | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | 全部 retryable=false；重试无法创建真实订阅。 |

## 8. Credential / Permission Error Matrix

| 维度 | 当前合同 | 错误 status | retry / 边界 |
| --- | --- | --- | --- |
| credential source | 默认 `*.unconfigured()`；不读 env / system property / .env credential material | `CREDENTIALS_MISSING` | 终态 fail-closed；禁止 fallback；须 credential governance bridge（另起 Gate）。 |
| endpoint default | `disabled://` sentinel；no-outbound guard fail-closed | `NETWORK_DISABLED` | 终态；真实 endpoint 仅显式 env opt-in 且须另起 Gate。 |
| permission probe | boundary classifier only；真实 probe `NOT_IMPLEMENTED` | `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` / `RATE_LIMITED` / `VENUE_UNAVAILABLE`（脱敏字符串映射） | forbidden endpoint（order/cancel/withdraw/transfer/blank）一律 fail-closed；不回传 raw response。 |
| REST private permission check | `FUTURE_REAL_REQUIRES_GATE` | — | 须 allowlisted read-only endpoint + 脱敏 audit。 |
| raw payload boundary | producer suppression CLOSED；字段删除 NOT DONE | `RAW_PAYLOAD_SUPPRESSED` | 安全边界，非恢复入口；不得为诊断重新暴露 raw body / headers / signature / token / cookie / private key path。 |
| rate limit / retry policy | 真实 policy `FUTURE_REAL_REQUIRES_GATE` | `RATE_LIMITED` | 条件重试须 backoff + circuit breaker，禁止无限重试。 |
| kill switch / no-outbound guard | `CLOSED_NO_REAL` fail-closed 下界 | `VENUE_UNAVAILABLE` | 不得绕过 kill switch / no-outbound guard。 |

## 9. Risk / Order / Ledger Ownership Rules

1. `RISK_REJECTED` / `ORDER_STATE_REJECTED` / `LEDGER_REJECTED` 必须由 NQ core 事实源决定；adapter 只透传，不自行判定、不覆盖、不绕过。
2. Adapter 不得绕过 `RiskGate`，不得在风控拒绝后重试以试图通过。
3. Adapter 不得绕过 `OrderStateMachine`，不得无条件覆盖订单状态。
4. Adapter 不得写 ledger entry、不得拥有 ledger 一致性、不得拥有 audit mutation。
5. Adapter 不得从进程环境派生 credential active material / tenant / account / owner / active version / permission scope。
6. Adapter 不得把 provider raw body / headers / signature source / credential material / private key path / cookie / token / sensitive query 暴露给 core / HTTP API / 日志 / audit / ledger。
7. Adapter 不得仅凭 `tradeEnv` 做 LIVE 或真实 endpoint 决策。
8. Adapter 不得把 testnet/sandbox 默认视为安全；所有外部 endpoint 须显式 Gate + no-outbound/readiness review。

## 10. Forbidden Interpretations

以下解释一律禁止：

- 把 `NO_REAL_DISABLED` 当作成功或 provider 就绪。
- 把 `NETWORK_DISABLED` / `disabled://` sentinel 当作可用真实 endpoint，或绕过 no-outbound guard。
- 把 `CREDENTIALS_MISSING` 当作可 fallback 到 env / system property / .env credential。
- 把 `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` 当作可自动提升权限或可继续交易。
- 把 `RATE_LIMITED` 当作可无限重试。
- 把 `VENUE_UNAVAILABLE` 当作可绕过 kill switch / no-outbound guard。
- 把 `RISK_REJECTED` / `ORDER_STATE_REJECTED` / `LEDGER_REJECTED` 当作 adapter 可自行覆盖或绕过。
- 把 `RAW_PAYLOAD_SUPPRESSED` 当作错误恢复入口或重新暴露 raw payload 的理由。
- 把 `UNKNOWN_REQUIRES_REVIEW` 当作可乐观重试的瞬时错误。
- 把任一 retryable=false status 写成“可继续交易”“可重试后下单”“可绕过边界”。
- 把本 error model 当作启用真实交易所错误处理、LIVE、AI、DH runtime、RealClient、real provider、real credential bridge 或 real permission probe 的授权。
- 把 OKX / Binance 既有 adapter 代码当作 future-real-ready 或真实交易授权。

## 11. Future-Real Prerequisites

任何真实交易所错误处理与真实交易能力须**另起 Gate**，且至少满足：

- 用户对目标 venue / 环境 / 账户 scope / 能力的显式授权。
- credential governance bridge 设计与安全审计；adapter 不从进程环境选 credential material。
- endpoint allowlist、no-outbound guard 更新、sentinel 回归证据。
- permission probe 限定 allowlisted read-only endpoint；order / cancel / withdraw / transfer endpoint fail-closed。
- rate limit / retry / timeout / circuit breaker / backoff / kill switch policy 落地，且 `RATE_LIMITED` / `VENUE_UNAVAILABLE` 重试受控。
- PAPER / LIVE 硬隔离与回滚到 disabled / no-real。
- `RiskGate` / `OrderStateMachine` / Ledger / Audit 集成证明；`RISK_REJECTED` / `ORDER_STATE_REJECTED` / `LEDGER_REJECTED` 由 NQ core 拥有。
- raw payload 脱敏策略覆盖 provider body / headers / signature / cookie / token / private key path / query string。
- testnet/sandbox 证据先于任何 LIVE；testnet/sandbox 本身仍不授权 LIVE。
- 专项 review/freeze，Maven / frontend / Python / CI scope 按实际实现影响选择。

## 12. Acceptance Criteria

- error status enum 全部定义：`NO_REAL_DISABLED`、`NETWORK_DISABLED`、`CREDENTIALS_MISSING`、`AUTH_FAILED`、`PERMISSION_DENIED`、`IP_NOT_ALLOWED`、`RATE_LIMITED`、`VENUE_UNAVAILABLE`、`INVALID_SYMBOL`、`UNSUPPORTED_OPERATION`、`RISK_REJECTED`、`ORDER_STATE_REJECTED`、`LEDGER_REJECTED`、`RAW_PAYLOAD_SUPPRESSED`、`UNKNOWN_REQUIRES_REVIEW`。
- retry 语义定义：retryable=false 集合、conditional 集合（RATE_LIMITED / VENUE_UNAVAILABLE）、UNKNOWN_REQUIRES_REVIEW fail-closed by default。
- adapter / venue 覆盖：Noop、OKX、Binance、future-real placeholder、permission probe placeholder、marketdata no-real / future-real placeholder。
- 路径覆盖：place / cancel / query / account balance / permission probe / REST public / REST private / WS public / WS private / historical OHLCV / ticker / orderbook / trades / 订阅 / credential source / endpoint default / raw payload boundary / no-outbound guard / risk gate / order state machine / ledger / audit。
- 明确：`NO_REAL_DISABLED` 非成功；`NETWORK_DISABLED` fail-closed；`CREDENTIALS_MISSING` 不 fallback；AUTH/PERMISSION/IP 不自动提升、不继续交易；`RATE_LIMITED` 不无限重试；`VENUE_UNAVAILABLE` 不绕过 kill switch；RISK/ORDER/LEDGER 由 NQ core 拥有；`RAW_PAYLOAD_SUPPRESSED` 是安全边界；`UNKNOWN_REQUIRES_REVIEW` fail-closed。
- 真实交易所错误处理须另起 Gate；adapter readiness 仍 `NOT READY / NOT FROZEN / NOT AUTHORIZED`。
- LIVE / 真实 credential / AI / DH runtime / real provider / RealClient / real permission probe 仍 disallowed / not implemented。
- 本合同 contract-only：未改代码、未新增 API / DTO / migration / workflow；仅 `docs/current/**` 变更。

## 13. Findings

### P0

- 无。本轮 docs-only contract，没有 runtime、DB、credential、provider、exchange、LIVE、AI 或 DH side effect。

### P1

- 无。本合同明确禁止把 disabled / missing / denied / unknown / suppressed 错误解释成可重试或可继续真实交易。

### P2

- error status enum 是 GateL-1D 合同层词汇，与既有 `AdapterResultCategory`（9 类）是“合同约束 ↔ 代码实现”关系；未来实现须保持映射一致，不得新增 enum 削弱 fail-closed（实现时另起 Gate review）。
- `RAW_PAYLOAD_SUPPRESSED` 仅冻结 producer suppression 安全边界；rawPayload field deletion 仍是 separate compatibility task。
- 真实 RATE_LIMITED / VENUE_UNAVAILABLE backoff / circuit breaker / kill switch policy 的实现仍属 future-real，须在 GateL-1E readiness checklist 与后续实现 Gate 落地，本合同只冻结解释口径。

## 14. Commands Run

- `Get-Location` / `git status --short` / `git branch --show-current`。
- bounded reads：项目规范、GateL current docs、`backend/nq-adapter-api/**`、`backend/nq-adapter-okx/**`、`backend/nq-adapter-binance/**` 允许文件。
- `Glob` / `Grep` 定位 adapter 错误模型源码与 current docs 同步点。
- 后置文档验证命令记录在 `TESTING.md` 与最终任务输出（`git diff --check` / `git diff --stat` / bounded `rg` 禁止措辞检查 / scope check）。

## 15. Rollback

- 删除 `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT.md`。
- 还原本轮对 `docs/current/GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md` 的同步。
- 无 code / DB / migration / workflow / runtime / credential / provider / exchange / LIVE / AI / DH side effect。

## 16. Next Task Recommendation

**NQ-GATEL-1D-ERROR-MODEL-CONTRACT-REVIEW**。

下一任务须保持 docs-only，除非另行授权。不得实现真实 adapter、real provider、RealClient、LIVE、AI、DH runtime、rawPayload field deletion、real credential governance bridge 或 real permission probe；不得把 error model 写成真实交易授权；不得把 GateL-1D 写成 implementation started。

## 17. Final Recommendation

**NQ-GATEL-1D-ERROR-MODEL-CONTRACT：PASS / CONTRACT FROZEN。**

- 是否允许真实交易所接入：**NO**。
- 是否允许 LIVE：**NO**。
- 是否允许真实 credential：**NO**。
- 是否允许 AI / DH runtime：**NO**。
- 是否允许将 adapter 标记为 future-real-ready：**NO**。
- 推荐下一步：**NQ-GATEL-1D-ERROR-MODEL-CONTRACT-REVIEW**。

## 18. Review Acceptance Update

**NQ-GATEL-1D-ERROR-MODEL-CONTRACT-REVIEW：PASS / REVIEW ACCEPTED（2026-06-23）。** 详见 `GATEL_1D_ERROR_MODEL_CONTRACT_REVIEW.md`。

Review 接受本 error model contract 为 GateL-1D frozen contract-only baseline：error status enum 15 项完整无歧义；合同层 status 到既有 `AdapterResultCategory`（9 类）映射与源码事实一致；retryable=false 列表未被写成可继续交易；RATE_LIMITED / VENUE_UNAVAILABLE 为受控 conditional retry；UNKNOWN_REQUIRES_REVIEW fail-closed；Noop / OKX / Binance / future-real placeholder / permission probe placeholder / marketdata placeholder 与 trading / marketdata / credential / permission path 全覆盖。P0=0 / P1=0；P2 为既知 follow-up（合同层细粒度 status 在既有 `AdapterResultCategory` 折叠为 `AUTH_FAILURE`、rawPayload field deletion 独立任务、真实 backoff/circuit breaker policy 属 future-real），不阻断冻结。

adapter readiness 仍 **NOT READY / NOT FROZEN / NOT AUTHORIZED**。是否允许真实交易所接入 / LIVE / 真实 credential / AI / DH runtime / adapter future-real-ready：**NO**。下一步 **NQ-GATEL-1D-ERROR-MODEL-CONTRACT-FREEZE**。

## 19. Freeze Acceptance Update

**NQ-GATEL-1D-ERROR-MODEL-CONTRACT-FREEZE：PASS / FROZEN / ACCEPTED（2026-06-23）。** 详见 `GATEL_1D_ERROR_MODEL_CONTRACT_FREEZE_REVIEW.md`。

Freeze 接受本 error model contract 与其 review 为 GateL-1D frozen contract-only baseline：error status enum（15 项）、合同层 status ↔ 既有 `AdapterResultCategory`（9 类）映射、retry 语义（retryable=false 终态集合；conditional 仅受控 RATE_LIMITED / VENUE_UNAVAILABLE；UNKNOWN fail-closed）、adapter/venue 与 trading/marketdata/credential/permission 路径矩阵、fail-closed 与禁止解释全部冻结，作为 GateL-1E readiness checklist refinement 与 future-real 实现 Gate 的错误分类、retry、fail-closed、安全解释基线。`git grep` 复核 Noop `NO_REAL_DISABLED` / OKX·Binance `disabled://` sentinel / `*.unconfigured()` credential 冻结不变量仍成立。P0=0 / P1=0；P2 为既知 follow-up（细粒度 status 在既有 `AdapterResultCategory` 折叠为 `AUTH_FAILURE`、rawPayload field deletion 独立任务、真实 backoff/circuit breaker/kill switch policy 属 future-real），不阻断 freeze。

该 freeze 不启用任何能力。adapter readiness 仍 **NOT READY / NOT FROZEN / NOT AUTHORIZED**。是否允许真实交易所接入 / LIVE / 真实 credential / AI / DH runtime / adapter future-real-ready：**NO**。下一步 **NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT**。
