# NQ-GATEO-O5B-RUNNER-BINDING-PLAN

## 1. 当前状态

任务名称：`NQ-GATEO-O5B-RUNNER-BINDING-PLAN`。

任务归属：NQ-only。

任务类型：`RUNNER_BINDING_PLANNING` / `MANUAL_PUBLIC_OUTBOUND_ENTRYPOINT_DESIGN` / `SECURITY_BOUNDARY_REVIEW` / `DOCUMENTATION`。

Runner binding plan status：`COMPLETED`（已完成）/ `PLAN ONLY`（仅规划）/ `NOT IMPLEMENTED`（未实现）。

O-5B-R1 runner binding implementation：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `COMMITTED`（已提交）。

O-5B-R2 runner binding review：`PASS`（通过）/ `ACCEPTED`（已接受）。

O-5A manual public outbound smoke plan review：`PASS`（通过）/ `ACCEPTED`（已接受）。

O-5B execution status：`ALLOWED`（允许）/ `MANUAL PUBLIC READONLY ONLY`（仅手动公开只读）/ `NOT EXECUTED`（未执行）。

O-5 smoke execution：`NOT STARTED`（未开始）。

O-5D DataOrigin / `PUBLIC_OUTBOUND` decision：`NOT STARTED`（未开始）。

O-FREEZE：`NOT STARTED`（未开始）。

GateO stage：`NOT COMPLETED`（未完成）。

R2 review addendum（2026-07-03）：`NQ-GATEO-O5B-R2-MANUAL-RUNNER-BINDING-REVIEW` 已通过并接受。R1 commit `35413109 test(gateo): bind manual public outbound smoke runner` 已存在，R1 当前状态推进为 `IMPLEMENTED / SELF-REVIEWED / COMMITTED`。本轮没有执行 smoke，未设置 manual enabling property、manual env flag 或 public outbound feature flag；O-5 smoke execution、O-5D DataOrigin.PUBLIC_OUTBOUND decision、O-FREEZE 仍 `NOT STARTED`。

O-5B-R1 已按本计划绑定 test-only manual JUnit runner，且 O-5B-R2 runner binding review 已接受。该实现只新增 test scope runner，不执行真实 HTTP，不读取 credential，不修改 backend production code / frontend / research / scripts / deploy / `.github` / migration，不新增 API / CI / migration。Manual smoke execution 后续只允许在单独任务中以 `MANUAL PUBLIC READONLY ONLY` 执行，不得从本轮 review 直接跳到 O-FREEZE。

## 2. 背景事实

上一轮 O-5B execution 只读核对确认：仓库已有 O-1 controlled public outbound client / policy / manual profile / feature flag，但当时没有可审查的独立 O-5B manual smoke runner。因此 O-5B execution 不能开始。

O-5B-R1 已补齐 runner 绑定，并已通过 O-5B-R2 专项复核；本轮仍未执行真实 public outbound smoke。因此 manual smoke execution 只允许后续单独人工执行，当前状态固定为：

```text
ALLOWED / MANUAL PUBLIC READONLY ONLY / NOT EXECUTED
```

已存在能力：

- `PublicMarketDataOutboundClient`：O-1 public marketdata outbound 最小抽象。
- `PublicMarketDataOutboundPolicy`：public category allowlist、private/signed/auth denylist、endpoint authority escape guard、path/query token guard。
- `PublicMarketDataEndpointCategory`：默认仅允许 `SERVER_TIME`、`INSTRUMENTS`、`TICKER`、`OHLCV`。
- `JdkPublicMarketDataOutboundClient`：manual profile 下的受控 JDK HTTP client；每次 request 和 retry 前执行 policy；bounded timeout / retry / backoff；输出脱敏 result。
- `PublicMarketDataOutboundConfiguration`：默认 disabled fallback；仅 `public-marketdata-manual` profile + `nq.public-marketdata.outbound.enabled=true` 时创建真实 outbound client。
- `EnvSafetyValidator`：manual public profile 下阻断 LIVE / AI / DH runtime / real provider / RealClient / real exchange 组合。

本轮新增能力：

- 独立 O-5B manual smoke runner：`backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/GateOManualPublicOutboundSmokeTest.java`。
- 默认跳过的 test-only manual JUnit entry：class-level `@EnabledIfSystemProperty(named = "nq.gateo.o5.manualSmoke.required", matches = "true")`，并带 `manual-public-outbound` / `gateo-o5-manual` tags。
- Runner 内部固定 OKX public REST category-to-path map，不接受 raw URL、raw path、任意 query 或用户输入 credential。

仍不存在能力：

- 已复核并接受的 O-5B 执行命令。
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
| O-5B-R1 runner implementation | `IMPLEMENTED / SELF-REVIEWED / COMMITTED` | 已新增并提交 test-only manual runner；本轮未执行 smoke。 |
| O-5B-R2 runner binding review | `PASS / ACCEPTED` | 已只读复核 runner 的 gate、allowlist/denylist、redaction 与默认 skip 行为。 |
| O-5B smoke execution | `ALLOWED / MANUAL PUBLIC READONLY ONLY / NOT EXECUTED` | 只允许后续单独人工 public readonly execution。 |
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

1. O-5B-R1 runner implementation 已完成并通过 O-5B-R2 runner binding review；O-5B smoke execution 仍未执行，只能后续单独人工启动。
2. `PUBLIC_OUTBOUND` 仍未进入当前 `DataOrigin` 事实；该决策必须留给 O-5D。

### P3

1. O-5 plan、O-5B runner binding plan、O-5B-R1 implementation、O-5B-R2 review 和 O-5B smoke execution 状态相近，current 入口必须持续区分 `PLAN ONLY`、`RUNNER COMMITTED`、`REVIEW ACCEPTED` 和 `NOT EXECUTED`。

## 13. Final Decision

`NQ-GATEO-O5B-RUNNER-BINDING-PLAN: PASS / ACCEPTED`。

Runner implementation：`ALLOWED / TEST-ONLY MANUAL ENTRY PREFERRED`。

O-5B-R1 runner binding implementation：`IMPLEMENTED / SELF-REVIEWED / COMMITTED`。

O-5B-R2 runner binding review：`PASS / ACCEPTED`。

O-5B smoke execution：`ALLOWED / MANUAL PUBLIC READONLY ONLY / NOT EXECUTED`。

O-5 smoke execution：`NOT STARTED`。

O-5D DataOrigin / `PUBLIC_OUTBOUND` decision：`NOT STARTED`。

O-FREEZE：`NOT STARTED`。

GateO stage：`NOT COMPLETED`。

推荐下一步：

```text
NQ-GATEO-O5B-MANUAL-PUBLIC-OUTBOUND-SMOKE-EXECUTION
```

该下一步只能在显式 manual gates 下执行 public readonly smoke；不得使用 credential、signed request、private endpoint、LIVE、AI、DH runtime、RealClient、real provider 或 permission probe。

Commit recommendation for this R2 docs sync：

```text
docs(gateo): review manual public outbound runner binding
```

## 14. O-5B-R1 Runner Binding Implementation Addendum（2026-07-03）

任务名称：`NQ-GATEO-O5B-R1-MANUAL-PUBLIC-OUTBOUND-RUNNER-BINDING-IMPLEMENTATION`。

实现状态：`IMPLEMENTED`（已实现）/ `SELF-REVIEWED`（已自审）/ `COMMITTED`（已提交）。

修改范围：

- 新增 `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/GateOManualPublicOutboundSmokeTest.java`。
- 同步允许的 `docs/current` 与根 `README.md` 状态入口。
- 未修改 backend production code、frontend、research、scripts、deploy、`.github`、migration、API、CI 或 dependency。

Runner shape：

- Test-only JUnit runner，class-level `@EnabledIfSystemProperty(named = "nq.gateo.o5.manualSmoke.required", matches = "true")`。
- JUnit tags：`manual-public-outbound`、`gateo-o5-manual`。
- 测试体额外要求 `NQ_GATEO_O5_MANUAL_SMOKE=true`、`SPRING_PROFILES_ACTIVE=public-marketdata-manual`、`NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=true`。
- HTTP 之前统一校验 LIVE / AI / DH runtime / real provider / RealClient / real exchange disabled，并拒绝 credential-like env / system property。

Endpoint binding：

- Provider 固定为 OKX reviewed public base host。
- Runner 只允许 `SERVER_TIME`、`INSTRUMENTS`、`TICKER`、`OHLCV`。
- Runner 不接受 raw URL、raw path、任意 query、credential 或用户输入 endpoint。
- 每个 request 必须经过 `PublicMarketDataOutboundPolicy`；`ORDER_BOOK`、`RECENT_TRADES`、`PUBLIC_WEBSOCKET`、private / signed / authenticated / permission probe category 均 fail-closed。

Evidence / redaction：

- 只输出 text-only redacted summary：`runId`、`startedAt`、`finishedAt`、`provider`、`endpointCategory`、`instrument`、`httpStatus`、`latencyMs`、`resultStatus`、`errorCategory`、`redactedError` 与 no-credential / no-signed / no-private / no-trading / live-disabled / ai-disabled / dh-not-integrated facts。
- 禁止输出 raw response body、raw headers、full URL、full query string、credential、API key、secret、passphrase、token、signature、cookie、private key 或 raw provider payload。

Validation evidence：

- Targeted runner validation：`mvn -f backend/pom.xml -pl nq-app,nq-adapter-api -am "-Dtest=*ManualPublic*Smoke*,*GateO*Outbound*Smoke*" "-Dsurefire.failIfNoSpecifiedTests=false" test` = `PASS / BUILD SUCCESS`；默认未设置 manual gate，runner skipped before HTTP。
- Backend full validation：`mvn -f backend/pom.xml test` = `PASS / BUILD SUCCESS`；默认 Maven test 未触发真实 public HTTP。
- O-5B smoke execution：`NOT EXECUTED`。本轮未设置 `NQ_GATEO_O5_MANUAL_SMOKE=true`，未设置 `NQ_PUBLIC_MARKETDATA_OUTBOUND_ENABLED=true`，未执行真实 OKX / Binance / Bybit / Gate / Coinbase / Kraken HTTP。
