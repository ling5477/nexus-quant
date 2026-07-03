# NQ-GATEO-O5B-RUNNER-BINDING-PLAN

## 1. 当前状态

任务名称：`NQ-GATEO-O5B-RUNNER-BINDING-PLAN`。

任务归属：NQ-only。

任务类型：`RUNNER_BINDING_PLANNING` / `MANUAL_PUBLIC_OUTBOUND_ENTRYPOINT_DESIGN` / `SECURITY_BOUNDARY_REVIEW` / `DOCUMENTATION`。

Runner binding plan status：`COMPLETED`（已完成）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现）。

O-5A manual public outbound smoke plan review：`PASS`（通过）/ `ACCEPTED`（已接受）。

O-5B execution status：`BLOCKED`（阻塞）/ `MANUAL RUNNER NOT BOUND`（手动 runner 未绑定）/ `NOT EXECUTED`（未执行）。

O-5 smoke execution：`NOT STARTED`（未开始）。

O-5D DataOrigin / `PUBLIC_OUTBOUND` decision：`NOT STARTED`（未开始）。

O-FREEZE：`NOT STARTED`（未开始）。

GateO stage：`NOT COMPLETED`（未完成）。

本轮只做 runner binding 规划，不实现 runner，不新增测试代码，不执行真实 HTTP，不读取 credential，不修改 backend / frontend / research / scripts / deploy / `.github` / migration。

## 2. 背景事实

上一轮 O-5B execution 只读核对确认：仓库已有 O-1 controlled public outbound client / policy / manual profile / feature flag，但没有可审查的独立 O-5B manual smoke runner。因此 O-5B execution 不能开始，状态固定为：

```text
BLOCKED / MANUAL RUNNER NOT BOUND / NOT EXECUTED
```

已存在能力：

- `PublicMarketDataOutboundClient`：O-1 public marketdata outbound 最小抽象。
- `PublicMarketDataOutboundPolicy`：public category allowlist、private/signed/auth denylist、endpoint authority escape guard、path/query token guard。
- `PublicMarketDataEndpointCategory`：默认仅允许 `SERVER_TIME`、`INSTRUMENTS`、`TICKER`、`OHLCV`。
- `JdkPublicMarketDataOutboundClient`：manual profile 下的受控 JDK HTTP client；每次 request 和 retry 前执行 policy；bounded timeout / retry / backoff；输出脱敏 result。
- `PublicMarketDataOutboundConfiguration`：默认 disabled fallback；仅 `public-marketdata-manual` profile + `nq.public-marketdata.outbound.enabled=true` 时创建真实 outbound client。
- `EnvSafetyValidator`：manual public profile 下阻断 LIVE / AI / DH runtime / real provider / RealClient / real exchange 组合。

尚不存在能力：

- 独立 O-5B manual smoke runner。
- O-5B 执行命令绑定。
- O-5B redacted evidence artifact。
- `DataOrigin.PUBLIC_OUTBOUND` 当前事实。

## 3. 目标与非目标

本计划目标：

- 决定 O-5B manual smoke runner 的最小绑定形态。
- 明确 runner 放置位置、触发方式、默认不运行策略、profile / feature flag / env gate。
- 明确 allowlist / denylist 在 runner 中的绑定方式。
- 明确输出证据字段、脱敏规则、失败语义和后续 O-5B execution 入场条件。
- 给出是否允许后续实现 runner 的结论。

本计划不做：

- 不实现 runner。
- 不新增或修改 Java / TypeScript / Python / shell / CI 代码。
- 不运行 O-5 smoke。
- 不执行真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken HTTP。
- 不读取 `.env`、credential、key、pem、token、cookie、secret、passphrase 或私钥。
- 不新增 API、migration、frontend 页面、E2E、workflow 或部署脚本。
- 不引入 LIVE、AI、DH runtime、RealClient、real provider、signed request、private endpoint 或 permission probe。

## 4. Runner 形态决策

| 方案 | 结论 | 原因 |
| --- | --- | --- |
| Test-only manual JUnit entry | `PREFERRED`（推荐） | 放在 test scope，默认不产生生产 runtime surface；可复用 Spring profile / config / existing bean；可用 JUnit guard 和 Maven 显式选择控制执行。 |
| `ApplicationRunner` / `CommandLineRunner` in main runtime | `NOT PREFERRED`（不推荐） | 会进入生产应用装配面，容易与普通启动、local profile 或后续部署混淆；需要额外证明不会被默认启动触发。 |
| 新增 HTTP endpoint / actuator endpoint | `REJECTED`（拒绝） | 增加 API surface，容易被误调用；O-5B 只需要手动 smoke，不需要长期服务入口。 |
| 外部脚本直接发 HTTP | `REJECTED`（拒绝） | 容易绕过 O-1 policy、Spring env safety 和 redaction result；审计与回滚成本更高。 |

最终建议：

```text
Runner implementation: ALLOWED / TEST-ONLY MANUAL ENTRY PREFERRED
```

推荐类名与位置：

```text
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/GateOManualPublicOutboundSmokeTest.java
```

推荐使用 test scope 的原因：

- 需要 `public-marketdata-manual` profile 和 `PublicMarketDataOutboundClient` bean。
- 需要复用 `nq-app` 的 `PublicMarketDataOutboundConfiguration` 与 `EnvSafetyValidator`。
- 不应新增 production runner、API 或 CLI 脚本。

## 5. 默认不运行策略

后续实现必须确保默认 Maven / 默认 CI / 默认本地测试不会触发真实 HTTP。

推荐组合：

- class-level JUnit condition：`@EnabledIfSystemProperty(named = "nq.gateo.o5.manualSmoke.required", matches = "true")`。
- class-level tag：`@Tag("manual-public-outbound")` 与 `@Tag("gateo-o5")`。
- 执行前再用 `Assumptions` 或显式 guard 校验 profile、feature flag、manual confirmation 和安全 env。
- CI 不设置 `nq.gateo.o5.manualSmoke.required=true`。
- 默认 `mvn -f backend/pom.xml test` 只能 skip/ignore 该入口，不能发出 HTTP。

如果后续实现选择非 JUnit condition，必须提供等价证明：默认命令不会触达 runner，不会初始化真实 outbound client，不会访问任何真实 host。

## 6. 手动执行命令草案

本节为后续实现后的命令形态，不是本轮执行记录。

PowerShell 草案：

```powershell
$env:SPRING_PROFILES_ACTIVE = "public-marketdata-manual"
$env:NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED = "true"
$env:NQ_GATEO_O5_MANUAL_SMOKE = "true"
$env:NQ_LIVE_ENABLED = "false"
$env:NQ_AI_ENABLED = "false"
$env:NQ_DH_RUNTIME_ENABLED = "false"
$env:NQ_REAL_PROVIDER_ENABLED = "false"
$env:NQ_REAL_CLIENT_ENABLED = "false"
$env:NQ_REAL_EXCHANGE_ENABLED = "false"

mvn -f backend/pom.xml -pl nq-app -am `
  "-Dtest=GateOManualPublicOutboundSmokeTest" `
  "-Dnq.gateo.o5.manualSmoke.required=true" `
  "-Dsurefire.failIfNoSpecifiedTests=false" `
  test
```

Endpoint / provider 选择不得通过用户输入 full URL + query 直接传入。后续实现必须二选一：

1. 由 runner 内部固定 provider enum 与 category-to-path map，并且只允许 reviewed official public base host。
2. 沿用现有 `NQ_PUBLIC_MARKETDATA_BASE_URL` 时，只允许 base URL，不允许 path/query/fragment/userInfo；endpoint path 仍必须由 runner 内部 allowlist 生成。

## 7. Allowlist / Denylist 绑定

Runner 只允许以下 category：

| Category | O-5B runner 默认 | 说明 |
| --- | --- | --- |
| `SERVER_TIME` | allowed | 最小连通性和延迟观测。 |
| `INSTRUMENTS` | allowed | public instrument / symbol metadata。 |
| `TICKER` | allowed | public ticker snapshot。 |
| `OHLCV` | allowed | public kline / candlestick / OHLCV。 |
| `ORDER_BOOK` | denied by default | 即使 public，也不进入 O-5B 默认 smoke。 |
| `RECENT_TRADES` | denied by default | 即使 public，也不进入 O-5B 默认 smoke。 |
| `PUBLIC_WEBSOCKET` | denied | O-5B 只允许 public REST。 |

Runner 必须直接或间接调用 O-1 policy，不得绕过：

- `PublicMarketDataOutboundPolicy.evaluate(...)`。
- `PublicMarketDataEndpointCategory`。
- `PublicMarketDataOutboundClient.fetch(...)`。

Runner 自身不得新增宽松 allowlist；任何新增 endpoint family 必须先进入单独 plan/review。

明确 denylist：

- account、balance、order、orders、cancel、amend。
- position / positions、wallet、transfer、withdraw、deposit、subaccount。
- margin、leverage、loan、private、listenKey、user data stream。
- API key、secret、passphrase、signature、signed request、auth header。
- private WebSocket、permission probe、API key validation、passphrase validation。

命中 denylist 时必须 `POLICY_DENIED` / fail-closed，不得补 credential 或改 endpoint。

## 8. Evidence 输出契约

Runner 成功或失败都只能输出 text-only redacted summary。

允许字段：

- `taskName`。
- `runStartedAt`。
- `runFinishedAt`。
- `provider`。
- `endpointCategory`。
- `symbol` / `instrument`。
- `responseStatus`。
- `latencyMs`。
- `retryCount`。
- `sanitizedErrorCategory`。
- `redactedError`。
- `dataOriginCandidate`。
- `noCredentialUsed`。
- `noSignedRequest`。
- `noPrivateEndpoint`。
- `noTradingSideEffect`。
- `liveDisabled`。
- `manualProfileConfirmed`。
- `featureFlagConfirmed`。

禁止字段：

- raw response body。
- raw headers。
- full URL。
- full query string。
- credential、API key、secret、passphrase、token、signature、cookie、private key。
- account id、balance、order id、private provider payload。
- `tradingAuthorized`、`liveReady`、`permissionGranted`、`realProviderReady`、`privateTradingReady`。

## 9. DataOrigin 边界

本轮不决定 `DataOrigin.PUBLIC_OUTBOUND`。

后续 O-5B smoke 即使成功，也只能形成脱敏执行证据，不得自动修改 O-2/O-3/O-4 当前 API 或 UI 语义。

`PUBLIC_OUTBOUND` 是否成为正式 `DataOrigin`，必须进入：

```text
NQ-GATEO-O5D-DATAORIGIN-PUBLIC-OUTBOUND-DECISION-REVIEW
```

在 O-5D 之前：

- readiness API 仍不得显示 LIVE-ready / trading-ready / provider-ready。
- O-5 evidence 不得被写成交易授权。
- `PUBLIC_CANDIDATE` / local diagnostic 语义不得被误读为真实 provider 已接入。

## 10. 失败语义

| 失败场景 | 处理 |
| --- | --- |
| 缺少 manual system property | skip / abort before Spring HTTP path。 |
| 缺少 `public-marketdata-manual` profile | fail before HTTP。 |
| feature flag 未启用 | fail before HTTP。 |
| LIVE / AI / DH / real provider / RealClient / real exchange 任一开启 | fail before HTTP。 |
| credential-like env/property present in runner scope | fail before HTTP，只输出变量名或冲突类型，不输出值。 |
| policy denied | `POLICY_DENIED`，不重试绕过。 |
| 401 / 403 | `AUTH_BOUNDARY_UNEXPECTED`，停止；不得补 credential。 |
| 429 | `RATE_LIMITED`，只按 O-1 bounded retry；不得提高重试上限。 |
| 5xx | `TEMPORARY_FAILURE`，脱敏记录。 |
| timeout | `TIMEOUT`，脱敏记录。 |
| malformed response | `INVALID_RESPONSE`，不得保存 raw body。 |

## 11. 后续批次

| Batch | 状态 | 目标 |
| --- | --- | --- |
| O-5A | `PASS / ACCEPTED` | manual public outbound smoke plan review。 |
| O-5B runner binding plan | `COMPLETED / PLAN ONLY / NOT IMPLEMENTED` | 绑定 runner 形态、命令、allowlist/denylist 与证据契约。 |
| O-5B runner implementation | `ALLOWED / TEST-ONLY MANUAL ENTRY PREFERRED / NOT STARTED` | 后续单独实现 test-only manual runner；不得执行 smoke。 |
| O-5B smoke execution | `BLOCKED / MANUAL RUNNER NOT BOUND / NOT EXECUTED` | 必须等 runner implementation review 通过后才可执行。 |
| O-5C | `NOT STARTED` | first smoke result review。 |
| O-5D | `NOT STARTED` | DataOrigin / `PUBLIC_OUTBOUND` decision review。 |
| O-5E | `NOT STARTED` | O-5 freeze review。 |
| O-FREEZE | `NOT STARTED` | GateO freeze。 |

## 12. Findings

### P0

无。

### P1

无。

### P2

1. Runner implementation 仍未完成；O-5B smoke execution 继续阻塞，直到 test-only manual entry 被实现并通过 review。
2. `PUBLIC_OUTBOUND` 仍未进入当前 `DataOrigin` 事实；该决策必须留给 O-5D。

### P3

1. O-5 plan、O-5B runner binding plan 和 O-5B smoke execution 状态相近，current 入口必须持续区分 `PLAN ONLY`、`RUNNER NOT BOUND` 和 `NOT EXECUTED`。

## 13. Final Decision

`NQ-GATEO-O5B-RUNNER-BINDING-PLAN: PASS / ACCEPTED`。

Runner implementation：`ALLOWED / TEST-ONLY MANUAL ENTRY PREFERRED`。

O-5B smoke execution：`BLOCKED / MANUAL RUNNER NOT BOUND / NOT EXECUTED`。

O-5 smoke execution：`NOT STARTED`。

O-5D DataOrigin / `PUBLIC_OUTBOUND` decision：`NOT STARTED`。

O-FREEZE：`NOT STARTED`。

GateO stage：`NOT COMPLETED`。

推荐下一步：

```text
NQ-GATEO-O5B-RUNNER-BINDING-IMPLEMENTATION
```

该下一步只能实现 test-only manual runner，不得执行真实 public outbound smoke；smoke execution 必须再单独开 `NQ-GATEO-O5B-MANUAL-PUBLIC-OUTBOUND-SMOKE-EXECUTION`。

Commit recommendation：

```text
docs(gateo): plan O5B manual smoke runner binding
```
