# NQ OKX Runtime Config Default Endpoint Defense Plan

任务：NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE
日期：2026-06-22
状态：**Path A ACCEPTED / IMPLEMENTED / PENDING CI RUN**
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

本卷宗记录 GateK post-freeze P2 纵深防御项的计划、定稿与实现结果。该项来自 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md §10` 的 backlog 指针，属 non-blocking defense-in-depth，不推翻既有 freeze。

## 1. Problem statement

`OkxRuntimeConfig` 原存在代码级真实 host 默认值：

- `DEFAULT_BASE_URL = "https://www.okx.com"`
- `DEFAULT_DOME_WS_PRIVATE_URL = "wss://wspap.okx.com:8443/ws/v5/private"`
- `DEFAULT_REAL_WS_PRIVATE_URL = "wss://ws.okx.com:8443/ws/v5/private"`

当 `NQ_OKX_BASE_URL` / `NQ_OKX_WS_URL`（及 dome/real 前缀）缺省时，`OkxRuntimeConfig.fromSystemEnv()` 会取用这些真实默认值。启动期 `EnvSafetyValidator` 只校验**已注入**的 env/property endpoint 值，看不见代码内部默认 host，因此该真实默认值不在 fail-closed 校验范围内。

## 2. Current mitigation

实现前，该项不构成真实外联漏洞，原因（均已冻结）：

- adapter 构造惰性：`OkxInstrumentsCache` 构造期不发起 HTTP，instruments 仅在首次 `snapshot` / `getRequired` 时拉取。
- CI `no-outbound-guard` job + test-scope `ExchangeNoOutboundGuard`（ProxySelector denylist）fail-closed。
- CI 注入 `NQ_OKX_BASE_URL=PLACEHOLDER_ONLY`。
- `okx.ws.enabled` / `binance.ws.enabled` 默认 false；`okx.recovery.enabled` local/freeze=false；`instrument.catalog-sync.enabled` freeze=false。
- 无 real provider / RealClient / real exchange adapter；LIVE disabled。

## 3. Why P2 / non-blocking

属纵深防御缺口而非当前外联漏洞：真实默认值仅在“env 完全缺省且某路径真实发起调用”时才会被使用，而上述多重缓解使任何受控 profile 下都不会发生真实外联。因此评级 **P2 / non-blocking / defense-in-depth**，作为 post-freeze addendum 单独处理，不阻断既有 freeze。

## 4. Recommended fix path

**Path A（ACCEPTED / IMPLEMENTED）**：把 `OkxRuntimeConfig` 默认 endpoint 改为非真实 `disabled://` sentinel，从源头消除代码级真实 host 默认值。

- sentinel 定稿：
  - base：`disabled://okx-not-configured`
  - dome / real WS：`disabled://okx-ws-not-configured`
- 选择理由：自描述、非真实；被 `EnvSafetyValidator.isRealExchangeEndpoint()` 的 `startsWith("disabled")` 规则识别为非真实（未来若送入 guard 零误判）；host 不含真实交易所域名，不会误命中 no-outbound denylist；请求期 loud fail-closed（非法 scheme `disabled` → `IllegalArgumentException`），绝不命中真实 OKX。
- 构造安全：消费点 `OkxHttpClient`（请求期才 `URI.create(baseUrl+path)`）、`OkxWsClient`（WS connect，默认关闭）、`OkxHistoricalKlineAdapter`（构造仅存串）均不在构造期解析 URL。
- 显式 env 行为不变：env 优先级最高，显式 `NQ_OKX_BASE_URL` / `NQ_OKX_WS_URL`（及 dome/real 前缀）仍覆盖默认。

**Path B（REJECTED）**：把解析后 endpoint 桥接进 `EnvSafetyValidator`。会触碰 frozen guard、扩大 nq-app↔nq-adapter-okx 模块边界、引入重复 fallback 默认值 drift 风险；不如 Path A 直接。

## 5. Exact files to change

- `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxRuntimeConfig.java`（改 3 个默认常量 + Why 注释）。
- `backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxRuntimeConfigTest.java`（更新默认断言 + 新增 sentinel-default 用例）。
- docs：本卷宗 + `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md` + `NQ_CI_BASELINE_PLAN.md` + `STATUS.md` + `TESTING.md` + `WORKLOG.md`。
- **未改**（边界保持）：`EnvSafetyValidator` / `EnvSafetyGuardConfiguration` / `NoOutboundExchangeGuardTest` / `NoRealExchangeCredentialPermissionProbePort` / workflow / `application*.yml` / `.env.example` / migration / frontend / research / scripts / deploy。

## 6. Exact tests to add/update

- `OkxRuntimeConfigTest`（更新）：
  - `shouldSelectDomeCredentialsAndMaskApiKey`：dome ws 默认断言由 `wss://wspap.okx.com:8443/ws/v5/private` 改为 `disabled://okx-ws-not-configured`；baseUrl 仍由显式 env 提供（验证 explicit override）。
  - 新增 `shouldDefaultToNonRealSentinelEndpointsWhenEnvAbsent`：empty env → base=`disabled://okx-not-configured`、dome/real ws=`disabled://okx-ws-not-configured`；默认值不含 `okx.com`/`ws.okx.com`/`wspap.okx.com`；real env 默认同样为 sentinel。
  - 保留 `shouldPreferUnifiedRuntimeVariablesForRealEnvironment`（explicit env still overrides defaults）与 `shouldTreatLegacyDemoAsDome`。
- `OkxExchangeAdapterBootstrapNoOutboundTest`（**未改**，说明原因）：sentinel 默认已在 `OkxRuntimeConfigTest` 充分断言；该 bootstrap 测试已用本地 fake server 证明构造期 0 次 HTTP（与 baseUrl 取值无关），新增 sentinel 断言为重复覆盖，且若用 empty-env 默认构造后触发首读会因 `disabled://` 在请求期抛错而偏离“no-outbound 计数”测试形态，无新增有效覆盖，故不改。
- 回归（不改，复跑通过）：`NoOutboundExchangeGuardTest`、`EnvSafetyValidatorTest`、`NoRealExchangeCredentialPermissionProbePortTest`、`OkxBootstrapNoOutboundLocalContextTest`。

## 7. Validation command

```powershell
mvn -f backend/pom.xml -pl nq-adapter-okx -am test "-Dtest=OkxRuntimeConfigTest,OkxExchangeAdapterBootstrapNoOutboundTest" "-Dsurefire.failIfNoSpecifiedTests=false"
mvn -f backend/pom.xml -pl nq-app,nq-infra -am test "-Dtest=NoRealExchangeCredentialPermissionProbePortTest,EnvSafetyValidatorTest,NoOutboundExchangeGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dnq.no-outbound.guard.required=true"
mvn -f backend/pom.xml test
git diff --check
git diff -- .github/workflows/ci.yml backend/**/db/migration frontend research scripts deploy .env.example
```

本地实测结果（2026-06-22）：`OkxRuntimeConfigTest` 4/0/0/0 + `OkxExchangeAdapterBootstrapNoOutboundTest` 1/0/0/0；no-outbound 套件 `NoReal` 1/0/0/0 + `EnvSafety` 8/0/0/0 + `NoOutbound` 3/0/0/0；全量 `mvn -f backend/pom.xml test` **BUILD SUCCESS**（0 fail / 0 error；既有条件性 skip 不变）。CI 真实运行待 CI-RUN-REVIEW 采证。

## 8. Rollback boundary

- revert 本实现 commit（`OkxRuntimeConfig` + 测试 + docs）即回到当前 frozen 状态（默认恢复为 www.okx.com）。
- 显式 env 配置下 adapter 行为不变，回滚安全。
- 无 runtime / DB / credential / provider / exchange 副作用。

## 9. Post-freeze addendum strategy

`OkxRuntimeConfig` 属 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md §11` regression boundary（“change to OKX runtime config … requires new review/freeze”）。因此本实现**不得静默并入既有 freeze**：

1. 本轮：IMPLEMENTED / PENDING CI RUN。
2. 下一步 `NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE-CI-RUN-REVIEW`：采集并只读复核 CI green run。
3. 之后以 addendum 形式触发 `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND` 复审 + freeze addendum，确认 sentinel 默认未削弱（实为强化）no-outbound 边界，再把 P2 标记为 CLOSED。

## 10. Explicit non-goals

- no real OKX。
- no credential read。
- no outbound。
- no LIVE。
- no AI。
- no DH。
- no RealClient。
- no real provider。
- no real exchange adapter。
- no real permission probe。

## 11. 固定状态口径

```text
GateK CI/security = FROZEN / ACCEPTED
NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND = FROZEN / ACCEPTED
NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE = IMPLEMENTED / PENDING CI RUN
No real credential read
No outbound call
No LIVE
No AI
No DH runtime
No RealClient
No real provider
No real exchange adapter
No real permission probe
```
