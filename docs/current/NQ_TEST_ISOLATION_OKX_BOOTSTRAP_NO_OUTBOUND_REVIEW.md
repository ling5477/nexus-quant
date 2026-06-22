# NQ Test Isolation OKX Bootstrap No-Outbound Review

任务：NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW
日期：2026-06-12（初轮 REVIEW + FIX）；2026-06-22（post-CI-security freeze 专项复审，见 §13）
状态：review documented；FIX implemented；no outbound exchange call performed in this fix pass。post-freeze 复审结论 **PASS / READY FOR FREEZE**（§13）。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。
GateK CI/security = FROZEN / ACCEPTED；Batch 5B-ENV = FROZEN / ACCEPTED；Batch 5B-SMOKE = FROZEN / ACCEPTED。

## 1. Scope

本报告记录 OKX adapter 在 Spring Boot local integration test 启动期触发 public instruments 外联尝试的只读审计结论，以及后续 `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-FIX` 的修复结果。

FIX 轮次修改范围限定在 OKX adapter / instruments cache 相关 Java、后端测试和 `docs/current` 文档；未新增 migration，未修改前端、Python 或部署脚本。FIX 轮次未调用 OKX、Binance 或任何真实交易所；未读取或输出真实密钥、API key、secret、token、cookie、passphrase、private key、助记词或交易所凭证；未接 AI、DH 或 LIVE。

## 2. Review Conclusion

结论：有条件通过。

`MarketdataControllerLocalIntegrationTest` 和 `ResearchBacktestHappyPathLocalTest` 本身使用本地 Spring Boot + DB-backed fixture / backtest 路径，不主动请求 OKX public API。但两个测试都会启动 `NexusQuantApplication` 的 `local` profile，Spring context 装配 `OkxExchangeAdapter` 时会构造 `OkxInstrumentsCache`，而该 cache 构造期立即执行 `refreshNow("bootstrap-okx-instruments")`，从而尝试访问 OKX public instruments endpoint。

该问题不影响 GateJ completed 阶段结论，不代表测试调用了真实交易、私有交易所接口或 credential permission probe，也不代表 AI / DH / LIVE 启动。但它违反 no-outbound test isolation 的期望，后续应作为测试隔离修复项处理。

## 3. Outbound Trigger Path

OKX public instruments 外联触发路径如下：

1. `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/MarketdataControllerLocalIntegrationTest.java`
   - `@SpringBootTest(classes = NexusQuantApplication.class)`
   - `@ActiveProfiles("local")`
   - 触发完整 Spring context 启动。
2. `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/ResearchBacktestHappyPathLocalTest.java`
   - `@SpringBootTest(classes = NexusQuantApplication.class)`
   - `@ActiveProfiles("local")`
   - 同样触发完整 Spring context 启动。
3. `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfiguration.java`
   - `okxTradingAdapter(...)` Bean 执行 `new OkxExchangeAdapter()`。
   - `stub-on-bootstrap-failure` 只在 `new OkxExchangeAdapter()` 抛异常后接管。
4. `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxExchangeAdapter.java`
   - 默认依赖创建读取 `OkxRuntimeConfig.fromSystemEnv()`。
   - 构造 OKX public `OkxHttpClient`。
   - 构造 `OkxInstrumentsCache(publicHttpClient, clock, runtimeConfig.instrumentRefresh())`。
5. `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxInstrumentsCache.java`
   - FIX 前：构造函数设置 `lastRefreshAt = Instant.EPOCH` 后立即执行 `refreshNow("bootstrap-okx-instruments")`。
   - FIX 后：构造函数只保存 `publicHttpClient`、`clock`、`refreshInterval` 和本地 cache 状态，不自动执行 HTTP。
   - 首次真正读取 metadata 时，`snapshot` / `getRequired` 通过 `refreshIfDue` 调用 `refreshNow`，再访问 `publicHttpClient.get("/api/v5/public/instruments?instType=SPOT", traceId)`。

因此，FIX 前外联发生在测试方法业务动作之前，是 Spring context bootstrap 副作用，而不是两个测试显式请求 OKX。FIX 后该 bootstrap 副作用已移除；外联只允许在首次显式读取 instruments metadata 时发生。

## 4. Triggering Tests

已确认触发测试：

- `MarketdataControllerLocalIntegrationTest`
  - 文件：`backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/MarketdataControllerLocalIntegrationTest.java`
  - 测试目标：fixture ingest 与真实 DB query 闭环。
  - 业务输入使用 `BINANCE_BTCUSDT_1M_SAMPLE` fixture；OKX 外联来自 context 启动期 adapter 装配，不来自测试请求体。

- `ResearchBacktestHappyPathLocalTest`
  - 文件：`backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/web/ResearchBacktestHappyPathLocalTest.java`
  - 测试目标：marketdata ingest 后串起 `research -> backtest -> eval` 最小 DB-backed happy path。
  - 业务输入同样基于 `BINANCE_BTCUSDT_1M_SAMPLE` fixture；OKX 外联来自 context 启动期 adapter 装配，不来自 backtest 业务步骤。

## 5. Root Cause

### 5.1 `OkxInstrumentsCache` 构造期 eager refresh

`OkxInstrumentsCache` 构造函数立即调用 `refreshNow("bootstrap-okx-instruments")`。这会在 Spring Bean 创建阶段访问 OKX public instruments endpoint，导致 local integration test 仅启动上下文也会产生外联尝试。

### 5.2 `OkxRuntimeConfig.fromSystemEnv()` 不读 Spring YAML

`OkxExchangeAdapter` 默认依赖创建使用 `OkxRuntimeConfig.fromSystemEnv()`，该入口读取当前进程环境变量，不读取 Spring `application-local.yml` / `application-freeze.yml` 中的 no-outbound 或 adapter 行为配置。

当前 `application-local.yml` / `application-freeze.yml` 的 `nq.okx.adapter.stub-on-bootstrap-failure` 只传入 `ExchangeAdapterConfiguration`，不能阻止 `OkxRuntimeConfig` 使用默认 OKX base URL 和默认 refresh 行为。

### 5.3 `stub-on-bootstrap-failure` 只吞失败，不阻止外联尝试

`stub-on-bootstrap-failure=true` 时，`ExchangeAdapterConfiguration` 会在 `new OkxExchangeAdapter()` 抛出 `RuntimeException` 后创建 stub fallback adapter。该机制可以避免启动失败继续向上传播，但不能阻止首次 bootstrap 外联尝试。

换言之：

- 外联成功：真实 OKX public instruments response 被用于填充 cache。
- 外联失败：异常被 fallback 吞掉并返回 stub adapter。
- 两种情况下，首次外联尝试都已经发生。

## 6. Binance Comparison

Binance 未发现同类启动期外联：

- `BinanceExchangeAdapter` 默认依赖创建会构造 `BinanceFiltersCache`。
- `BinanceFiltersCache` 构造函数只初始化 cache 和 `lastRefreshAt`，不调用 `refreshNow`。
- Binance `exchangeInfo` 拉取发生在 `getRequired` 或 `snapshot` 调用触发 `refreshIfDue` 时，不是构造期 eager refresh。

这不表示 Binance 永远不会访问外部公开接口；它表示本次审计范围内未发现与 OKX 相同的 Spring context bootstrap 阶段 public endpoint 外联尝试。

## 7. Findings

### P0

无。

未发现测试启动期触发真实下单、撤单、转账、提现、交易所私有接口、credential material 读取、AI / DH 接入或 LIVE trading 的证据。

### P1

- FIXED：OKX adapter bootstrap 原存在 public instruments 外联尝试，违反 no-outbound test isolation 预期；触发点是 `OkxInstrumentsCache` 构造期 eager `refreshNow`。FIX 已移除构造期 refresh。
- FIXED：`stub-on-bootstrap-failure` 原只在外联失败后 fallback，不能作为 no-outbound 控制开关。FIX 后构造期不再外联，因此该 fallback 不再承担构造期外联兜底角色。

### P2

- `OkxRuntimeConfig.fromSystemEnv()` 不读取 Spring YAML，导致 Spring profile 下的测试隔离配置无法统一约束 OKX baseUrl、bootstrap mode 或 no-outbound 行为。
- 两个业务测试使用 Binance fixture，却因完整 Spring context 装配触发 OKX adapter bootstrap，测试依赖边界不够收敛，后续 CI 或离线环境可能出现非确定性网络行为。
- OKX 与 Binance cache bootstrap 策略不一致；OKX 构造期 eager refresh，Binance 惰性刷新，增加测试隔离和启动行为理解成本。
  - FIX 后 OKX 与 Binance 对齐为构造期只赋值，首次读取时惰性刷新。

### P3

- 现有文档需要明确：local integration test 通过不等于 no-outbound 通过；需要单独补 no-outbound 回归测试。
- `stub-on-bootstrap-failure` 命名容易被误读为“不触发真实 bootstrap”，后续可在修复时同步补充注释和测试说明。

## 8. Gate And Credential Impact

### GateJ completed

GateJ completed 不受影响。本问题是 local integration test 启动期 public endpoint 外联隔离问题，不新增业务能力，不影响 GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed 的既有结论，也不代表 GateK implementation、AI、DH 或 LIVE started。

### Credential permission probe

Credential permission probe 不受影响。该问题发生在 OKX public instruments cache bootstrap，不读取 credential material，不调用交易所私有认证接口，不执行 permission probe，不改变 V31 schema-only permission probe 仍未实现的事实。

## 9. Recommended Follow-Up FIX

FIX 已按首选方案落地；以下内容保留为修复记录和后续回归边界。

### 首选：惰性化 `OkxInstrumentsCache` bootstrap

- 已移除构造函数内的 eager `refreshNow("bootstrap-okx-instruments")`。
- 已保持 `getRequired` / `snapshot` 触发 `refreshIfDue` 的现有语义。
- 如需要启动期预热，应通过显式 Spring-controlled bootstrap runner 或 profile/config 开关执行，而不是 cache constructor 副作用。

### 备选：增加 Spring 驱动 no-outbound / stub bootstrap mode（本轮未采用）

- 新增 Spring 配置项表达 `no-outbound` 或 `stub bootstrap`。
- 让 `ExchangeAdapterConfiguration` 在构造真实 OKX adapter 前决定使用 stub / no-op public client / disabled bootstrap。
- 该配置必须能被 `application-local.yml`、测试 profile 和 CI profile 稳定控制。

### 已补 no-outbound 回归测试

- `OkxInstrumentsCacheTest.shouldNotFetchDuringConstructionAndRefreshOnFirstSnapshot`：构造 cache 后 `getCount=0`，首次 `snapshot` 后 `getCount=1`。
- `OkxExchangeAdapterBootstrapNoOutboundTest.shouldCreateDefaultDependenciesWithoutFetchingPublicInstruments`：创建默认依赖与 adapter 后本地 fake instruments server `hitCount=0`，首次显式 `snapshot` 后 `hitCount=1`。
- `OkxBootstrapNoOutboundLocalContextTest.shouldBootstrapLocalContextWithoutOkxPublicInstrumentsOutbound`：local full Spring context 启动前安装 `ProxySelector` 探针，若访问 `https://www.okx.com/api/v5/public/instruments?instType=SPOT` 会失败；当前断言访问次数为 0，且日志不包含 `okx_adapter_bootstrap_fallback_enabled`。
- `BinanceFiltersCacheTest` 增加构造后 `fetchCount=0` 对照，确认 Binance 行为未被修改。

## 10. Boundary Confirmation

- 本轮未修改 Java。
- REVIEW 轮未修改 Java；FIX 轮修改 OKX adapter / instruments cache 相关 Java 和后端测试。
- FIX 轮未修改 Spring YAML 或生产配置。
- 本轮未新增 migration。
- FIX 轮新增/修改后端测试。
- 本轮未修改前端。
- 本轮未修改 Python。
- 本轮未修改部署脚本。
- 本轮未调用 OKX、Binance 或任何真实交易所。
- 本轮未实现 fix。
- 本轮未接 AI / DH / LIVE。
- 本轮未读取或输出真实密钥。

## 11. Validation

本报告为 documentation-only 落档。本轮应执行并记录：

- `git diff --check`
- `git diff --stat`
- `git status --short`

未执行后端、前端、Python 全量测试；原因是本轮只修改 `docs/current` 与 README 索引，不修改任何业务代码、测试代码、配置、migration、前端、Python 或部署脚本。

FIX 轮次验证记录见 `docs/current/TESTING.md` 的 `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-FIX` 章节。已执行：

- `mvn -f backend/pom.xml -pl nq-adapter-okx,nq-app -am test`：通过，`BUILD SUCCESS`。
- `git diff --check`：通过，无 whitespace error；仅有 Git LF/CRLF 工作区提示。
- `mvn -f backend/pom.xml test`：通过，23 个 backend module 全部 `SUCCESS`，`BUILD SUCCESS`。
- 禁止范围 diff 检查：通过，`backend/nq-infra/src/main/resources/db/migration`、`frontend`、`research`、`scripts` 无 diff。

## 12. Rollback

如需回滚本轮文档落档，删除本文件，并回退 `README.md`、`docs/current/README.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md` 中对应索引和记录即可。本轮没有代码、配置、数据库或部署副作用。

---

## 13. Post-CI-Security Freeze Re-Review（2026-06-22）

任务：NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW（post-CI-security freeze 专项复审）
日期：2026-06-22
本节为 GateK CI/security final freeze（`8d126f9f`，FROZEN / ACCEPTED）之后，对 OKX bootstrap / test isolation / no-outbound 边界的独立只读复审记录。本节为 docs-only 落档；未修改 workflow / backend / Java / TypeScript / Python / `application*.yml` / `.env.example` / migration / frontend / research / scripts / deploy / 测试。

### 13.1 Review object

OKX bootstrap / test isolation / no-outbound boundary。

### 13.2 Review result

**PASS / READY FOR FREEZE**。

### 13.3 Baseline

- 当前 HEAD：`e3b12e33788bd23e3d96507dd8efcc511db33043`。
- 当前分支：`dev`。
- working tree baseline：复审前 clean。
- final freeze commit：`8d126f9f`（HEAD 领先部分为后续 docs-only 提交，代码 / workflow / migration 无漂移）。

### 13.4 审查范围

- `.github/workflows/ci.yml`
- `.env.example`
- `application.yml` / `-local` / `-test` / `-gated-verify` / `-freeze` / `-prod`
- OKX bootstrap / adapter / runtime / probe boundary
- `ExchangeNoOutboundGuard` / `NoOutboundExchangeGuardTest`
- `EnvSafetyValidator` / `EnvSafetyGuardConfiguration`
- `NoRealExchangeCredentialPermissionProbePort`
- `ExchangeAdapterConfiguration`
- `LocalTestFallbackConfiguration`
- `AccountModuleConfiguration`
- `OkxRecoveryService` 启动钩子（`@EventListener(ContextRefreshedEvent.class)`）
- `docs/current/**`

### 13.5 明确不涉及

- 真实 OKX 外联。
- 真实凭证读取。
- 下单 / 撤单 / 转账 / 提现。
- LIVE 开启。

### 13.6 OKX bootstrap 结论

- 存在启动兜底 stub 工厂 `OkxBootstrapFallbackFactory`（仅 `nq.okx.adapter.stub-on-bootstrap-failure=true` 时接管）。
- adapter 构造惰性：`OkxInstrumentsCache` 构造期不发起 HTTP，instruments 仅在首次 `snapshot` / `getRequired` 时拉取（`OkxExchangeAdapterBootstrapNoOutboundTest`、`OkxBootstrapNoOutboundLocalContextTest` 固化）。
- 启动期不访问真实 OKX（local full Spring context 启动期对 `www.okx.com/api/v5/public/instruments` 访问次数断言为 0，且无 `okx_adapter_bootstrap_fallback_enabled` 日志）。
- fallback stub baseUrl = `http://127.0.0.1`；public stub 返回内置 payload。
- authenticated stub 直接抛 `OKX_ADAPTER_BOOTSTRAP_STUB`，不外联。

### 13.7 test / ci / paper / local 自动启用结论

- 不会自动启用真实连接：`okx.ws.enabled` / `binance.ws.enabled` 默认 false；`okx.recovery.enabled` 在 local / freeze 为 false；`instrument.catalog-sync.enabled` 在 freeze 为 false；test profile `no-outbound=true`。
- `OkxRecoveryService.onContextRefreshed` 在 `recovery.enabled=false` 时仅打印脱敏日志（mask apiKey，不输出 secret/passphrase）后返回，不外联；启用时只 reconcile 既有 OKX 订单（clean DB 下无候选）。
- no-outbound / no-real 边界成立。

### 13.8 credential boundary

- 存在设计内 env 读取入口（`OkxRuntimeConfig.fromSystemEnv()` 在 adapter 构造时读取 `NQ_OKX_API_KEY/SECRET/PASSPHRASE`）。
- CI / no-outbound guard 禁止真实交易所 credential env（workflow env-name 断言 + `NoOutboundExchangeGuardTest`，本轮本地运行 0 skip 通过，确认无 forbidden env）。
- 不打印 secret / passphrase：`fingerprint()` 仅输出 `env / baseUrl / maskApiKey`。

### 13.9 permission probe

- 默认 `NoRealExchangeCredentialPermissionProbePort`（`AccountModuleConfiguration` 装配，`NqAppContextPostgresSmokeTest` 断言所绑定即 NoReal）。
- 返回 `SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`。
- 不创建 HTTP client。
- 不访问交易所。

### 13.10 no-outbound guard

- denylist 覆盖 OKX / Binance / Bybit / Bitget / Gate / Coinbase / Kraken / Crypto / Hyperliquid（含子域 `endsWith` 匹配）。
- `select()` / `connectFailed()` 对命中 host fail-closed 抛 `AssertionError`。
- CI `no-outbound-guard` job 保留；`ci-security-smoke` job 复用同组 guard / validator / NoReal 测试。

### 13.11 profile boundary

- `LIVE / AI / DH / real-provider / real-client / real-exchange` 全部 `absence => false`。
- test profile `no-outbound=true`。
- `EnvSafetyValidator` 启动期对冲突组合一次性 fail-closed（`effectiveNoOutbound = configured || ci || testProfile`）。

### 13.12 .env.example

- placeholder-only（仅 `PLACEHOLDER_ONLY` / `DO_NOT_COMMIT_REAL_VALUE` / `REPLACE_WITH_LOCAL_PLACEHOLDER`）；无真实 endpoint / key / secret / passphrase。

### 13.13 测试结果（本轮本地只读复核执行）

- `NoRealExchangeCredentialPermissionProbePortTest` 1/0/0/0。
- `EnvSafetyValidatorTest` 8/0/0/0。
- `NoOutboundExchangeGuardTest` 3/0/0/0（0 skipped，CI-required env-absence 断言已执行并通过）。
- `BUILD SUCCESS`。命令：`mvn -f backend/pom.xml -pl nq-app,nq-infra -am test -Dtest=NoRealExchangeCredentialPermissionProbePortTest,EnvSafetyValidatorTest,NoOutboundExchangeGuardTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.no-outbound.guard.required=true`（CI / no-outbound 环境，无真实外联、无真实凭证读取）。

### 13.14 Findings

- P0 = 0。
- P1 = 0。
- P2 = 1（非阻断，纵深防御建议）：`OkxRuntimeConfig` 代码级真实 host 默认值（`DEFAULT_BASE_URL=https://www.okx.com`、真实 WS 默认）仅在 `NQ_OKX_BASE_URL` / `NQ_OKX_WS_URL` 完全缺省时取用，且未纳入启动期 `EnvSafetyValidator` endpoint 校验（该 guard 只检查已注入的 env/property 值）。当前由惰性构造 + test/CI ProxySelector denylist + CI 注入 `PLACEHOLDER_ONLY` + ws/recovery/catalog-sync 关闭/手动 + 无 real provider/RealClient 多重缓解，**当前任何受控 profile 下不产生真实外联**。后续单独任务处理，不在本轮修复，不阻断 freeze。
- P3 = 任务清单提及的 `application-ci.yml` / `application-paper.yml` 不存在为独立文件；CI 通过 `CI=true` + test / no-outbound 语义生效，`EnvSafetyValidator.testProfileActive()` 已识别 `ci` / `paper` / `*-smoke` profile 名，语义无缺口（仅命名预期差异）。非阻断。

### 13.15 是否允许进入 freeze

允许。

### 13.16 风险与回滚边界

- 本轮 docs-only。
- 回滚 review docs（删除 §13 与各 docs/current 对应记录）即可。
- 无 runtime / DB / credential / provider / exchange 副作用。
