# GateY-6B OKX Spot Real-Provider Mutation Contract Implementation — Attempt 01

## 1. Task classification

- 任务：`NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-IMPLEMENTATION`。
- 类型：NQ-only、L 级、高风险交易边界 implementation。
- 状态：`IMPLEMENTED|PENDING_REVIEW`（已实现 / 待独立复核）。
- 范围：typed contract + fake/stub contract tests；不实现 credential lifecycle、真实 OKX transport、worker/runtime 接线、真实 mutation、LIVE、migration、部署或发布。

## 2. Starting baseline / Exact-head CI

- branch：`dev`。
- starting `HEAD == origin/dev == 2a00d1e3cbab8bec38f344090b0636bf69b78cd1`；起始 working tree 与 staged set 均为空。
- exact-head CI：`NQ CI Baseline` run `31804169275 / completed / success`，`headSha=2a00d1e3cbab8bec38f344090b0636bf69b78cd1`。
- authority before：accepted=`GateY-5 / ACCEPTED|CI_GREEN`；work=`GateY-6 / COMMITTED|CI_GREEN|CONTINUE_REQUIRED / 621736e9a282d0f7684e2527fe86fe8e1faf506d / 31774122178`；next=`NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-IMPLEMENTATION`。
- governance library 已验证目标 next action `NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-SECURITY-OPERATIONS-REVIEW` 的 actual/expected type 均为 `REVIEW`，`allowed=true`。

## 3. Architecture audit matrix

| 事实/组件 | 决策 | 实施边界 |
| --- | --- | --- |
| GateY-3 intent/receipt、`SEND_STARTED`、UNKNOWN recovery | REUSE | 不修改 intent/receipt ledger 或核心状态机 |
| GateY-3 stable execution identity | REUSE + EXTEND | 从既有 `ExecutionIntent.clientOrderId` 导出 OKX 32 位 lowercase hex provider id；不随机、不按 retry 换 id |
| GateY-5 worker/runtime wiring | NOT IMPLEMENTED | 不把新 provider 接到 worker、Controller、Strategy 或 Spring runtime |
| `OkxExchangeAdapter` | SEMANTIC REFERENCE ONLY | 不作为新 provider transport，不建立第二套 adapter/runtime path |
| `OkxSpotEndpointGuard` | EXTEND | 仅增加编译期封闭的 typed contract allowlist；原 `PRIVATE_READONLY_DIAGNOSTIC` 隔离不变 |
| HTTP/signer/credential transport | FORBIDDEN | 不依赖 `OkxHttpClient`，不新增 signer、credential lookup、host/URL/raw method/path/header |
| application/provider port owner | `nq-core` | application owner 持有 `SpotExecutionProviderPort` 与 normalized contract |
| OKX adapter owner | `nq-adapter-okx` | adapter 只翻译 typed request/response；`nq-core` 不依赖 OKX DTO |
| persistence / migration | NONE | 不新增第二 SoR，不新增或修改 migration |

## 4. Files changed

### Core/provider contract

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/provider/ProviderClientOrderId.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/provider/SpotExecutionProviderPort.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/provider/SpotProviderError.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/provider/SpotProviderRequests.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/provider/SpotProviderResults.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/livecontrol/execution/provider/SpotProviderContractTest.java`

### OKX adapter contract

- `backend/nq-adapter-okx/pom.xml`
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotEndpointGuard.java`
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderAdapter.java`
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderContractDecision.java`
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderOperation.java`
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderTransport.java`
- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxVenueStateTranslator.java`
- `backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderAdapterContractTest.java`

### Architecture/default-wiring guards

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/ModuleBoundaryArchTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/architecture/PackageBoundaryArchTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfigurationReadinessTest.java`

## 5. Contract implementation

- application port：`SpotExecutionProviderPort` 只暴露 `placeLimit`、`queryOrderByClientOrderId`、`cancel`、`readOrderStatus`、`readFills` 五个 typed operation。
- endpoint exact allowlist：`PLACE_LIMIT`、`QUERY_ORDER`、`CANCEL_ORDER`、`READ_ORDER`、`READ_FILLS`；venue 固定 `OKX_SPOT`，order type 固定 `LIMIT`。MARKET、margin、leverage、derivatives、borrow、transfer、withdraw、funding 与 arbitrary private path/raw URL/raw method 均无法从 provider surface 表达。
- transport abstraction：`OkxSpotProviderTransport` 无 host、URL、method、path、header、credential 或 `execute(method,path,body)` escape hatch；`src/main` 无实现，只有 contract test fake transport。
- LIMIT PLACE：请求绑定 `intentId/clientOrderId/instrument/side/LIMIT/price/quantity/session/reference/trace/correlation`；immutable identity 必填，price/quantity 必须大于 0。
- clientOrderId：将已校验的 GateY-3 execution clientOrderId UUID 去连字符，得到 32 位 lowercase hex；同 intent、replay、restart 稳定，不同 intent 映射不同；非法格式、长度、tamper/collision 均 fail closed。
- PLACE outcome：只返回 `ACCEPTED`、`DEFINITIVELY_REJECTED`、`UNKNOWN`；UNKNOWN 强制 `queryByClientOrderIdRequired=true` 且 mutation retry 永远为 false。
- QUERY：normalized state 为 `NOT_FOUND/OPEN/PARTIALLY_FILLED/FILLED/CANCELED/REJECTED/UNKNOWN`；query 失败不触发 PLACE，未知 raw venue state 映射为带 sanitized error 的 UNKNOWN。
- CANCEL：OPEN 才是 controlled cancel candidate；PARTIALLY_FILLED 默认 query/reconcile first，仅在显式 remainder policy 下允许；FILLED/CANCELED/REJECTED/NOT_FOUND 不发送 mutation；UNKNOWN query-only；timeout/race 强制 query-first，禁止 blind retry。
- partial fill：只返回 executed/remaining quantity、normalized state 与 bounded fill references；不创建 Trade/Position/Ledger 或第二套 reconciliation SoR。
- error taxonomy：`TRANSPORT_TIMEOUT`、`HTTP_ERROR`、`EXCHANGE_BUSINESS_REJECTION`、`PERMISSION_DENIED`、`IP_RESTRICTION`、`CLOCK_SKEW`、`RATE_LIMITED`、`INSUFFICIENT_BALANCE`、`INSTRUMENT_RESTRICTED`、`INVALID_PRICE_OR_SIZE`、`RESPONSE_TOO_LARGE`、`MALFORMED_RESPONSE`、`UNKNOWN_RESULT`、`CANCEL_RACE`。每类携带 certainty、query retry、mutation retry=false、pause/kill recommendation 与 sanitized `REAL_*` audit code。
- clock：timestamp source、request timestamp、caller-supplied current time、observed/max skew 与 freshness 进入 typed contract；observed skew 缺失或超限 fail closed；未编造 pilot threshold。
- rate limit：不进行 mutation retry；输出 pause/backoff recommendation，mutation outcome 可能不确定时 query-first。
- response bounds：caller 必须提供 positive byte cap；fill record cap 为 1～100，fill window 上限 24h。超过 cap 分类 `RESPONSE_TOO_LARGE`；若 mutation 可能已到 venue，则结果为 UNKNOWN，禁止重发。
- default wiring：未新增 provider/transport Spring Bean、profile、auto-configuration 或真实 mutation capability；default context 明确断言不存在 `SpotExecutionProviderPort` bean，readiness 继续 fail closed。

## 6. No-egress / credential / mutation proof

- production transport implementations=`0`；test fake transport implementations=`1`；production adapter port implementations=`1`，但 runtime wiring=`0`。
- scoped static scan 的 executable-code hit=`0`；仅两个 JavaDoc 否定性边界文字包含 `URL/credential`。未发现 `java.net`、`OkxHttpClient`、signer、credential access、Spring annotation、HTTP URL 构造、host/raw headers 或通用 execute。
- credential store/env secret/local secret reads=`0`；真实 credential material=`0`。
- external network calls during implementation/tests=`0`；real OKX calls=`0`；real mutation calls=`0`。
- worker/runtime/deployment/production operation=`0`；LIVE=`DISABLED`，kill switch=`ENGAGED`。
- migration diff=`0`；V1～V39 未修改，V40 未创建。

## 7. Validation evidence

| Command / suite | Result | Evidence |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-adapter-okx -am -DskipTests compile` | PASS（通过） | `BUILD SUCCESS` |
| provider focused tests | PASS（通过） | core `6/0/0/0`；adapter `9/0/0/0`；`BUILD SUCCESS` |
| default wiring + ArchUnit | PASS（通过） | 24 tests，failures/errors/skips=`0/0/0`；跨模块依赖与 package boundary 通过 |
| GateY-3/5 focused regression | PASS（通过） | `ExecutionIntentRuntimeTest` 11、`LoopbackFakeExchangeHttpClientTest` 2、`DisposableWorkerReleaseVerifierTest` 2；全部通过 |
| `mvn -f backend/pom.xml test` | PASS（通过） | 23/23 modules success；`nq-app` 279 tests，failures/errors/skips=`0/0/27`；非阻断既有 SLF4J NOP 与 Mockito dynamic-agent warnings |
| `git diff --check`（代码阶段） | PASS（通过） | whitespace errors=`0` |
| current authority checker | PASS（通过） | `errors=0 / CURRENT_AUTHORITY_CONSISTENT`；work status/commit/CI/next action 与目标精确一致 |
| next-action governance regression | PASS（通过） | `IMPLEMENTED|PENDING_REVIEW` 的 expected action type=`REVIEW`；目标 action actual type=`REVIEW`，合法 |
| current docs link checker | PASS WITH HISTORICAL WARNINGS（通过并有历史 warning） | `297 checked / 14 historical warnings / 0 errors`；warning 均为 append-only `TESTING.md` 既有 GateJ/GateX 历史路径 |
| forbidden-area / manifest diff | PASS（通过） | frontend/research/migration/deploy/`.github`/scripts/hard-gate manifest diff 均为 0；manifest 实算/声明均为 `PASS=0 / NOT_MET=25 / NOT_VERIFIABLE=5` |

已保留两次非产品缺陷的失败历史：首次 focused Maven 命令因 PowerShell 未引用 `-D...` 参数而被 Maven 解析为 unknown lifecycle phase；修正引用后通过。首次 ArchUnit focused run因当前 ArchUnit 版本不支持 `haveSimpleNameMatching` 而 testCompile 失败；改用受支持的 `haveSimpleNameStartingWith` 与 fully-qualified names 后通过。

## 8. Findings / authority after

- P0：0。
- P1：0。
- P2：0。
- P3：既有 SLF4J NOP、Mockito dynamic-agent warning；不影响本轮 contract correctness，后续独立 review 可继续记录。
- hard-gate manifest：未修改；`PASS=0 / NOT_MET=25 / NOT_VERIFIABLE=5`。
- authority after：accepted=`GateY-5 / ACCEPTED|CI_GREEN`；work=`GateY-6 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；next=`NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-SECURITY-OPERATIONS-REVIEW`。
- safety after：real provider=`NOT_IMPLEMENTED`；private trading=`NOT_IMPLEMENTED`；`FIRST_REAL_ORDER=NOT_AUTHORIZED`；micro-live=`NOT_AUTHORIZED`；LIVE=`DISABLED`；kill switch=`ENGAGED`。

## 9. Final decision

`PASS / GATEY_6B_REAL_PROVIDER_MUTATION_CONTRACT_IMPLEMENTED / TYPED_OKX_SPOT_CONTRACT / LIMIT_ONLY / ENDPOINT_ALLOWLIST_FAIL_CLOSED / STABLE_CLIENT_ORDER_ID / QUERY_FIRST_UNKNOWN_RECOVERY / NO_BLIND_RETRY / STATE_AWARE_CANCEL / NO_REAL_CREDENTIAL / NO_OKX_NETWORK / NO_REAL_MUTATION / DEFAULT_RUNTIME_FAIL_CLOSED / MIGRATION_UNCHANGED / REAL_PROVIDER_NOT_YET_ACCEPTED / PRIVATE_TRADING_NOT_IMPLEMENTED / FIRST_REAL_ORDER_NOT_AUTHORIZED / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED / PENDING_INDEPENDENT_SECURITY_OPERATIONS_REVIEW`。

唯一下一动作：`NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-SECURITY-OPERATIONS-REVIEW`。

建议未来 commit：`feat(gatey): add OKX Spot real-provider mutation contract`。本任务未 stage、commit、push 或 tag。
