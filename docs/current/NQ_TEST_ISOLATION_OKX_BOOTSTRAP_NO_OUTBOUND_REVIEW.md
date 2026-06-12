# NQ Test Isolation OKX Bootstrap No-Outbound Review

任务：NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND-REVIEW
日期：2026-06-12
状态：review documented；code fix not started；no outbound exchange call performed in this documentation pass。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

## 1. Scope

本报告记录 OKX adapter 在 Spring Boot local integration test 启动期触发 public instruments 外联尝试的只读审计结论。本轮只把审计结论落到 `docs/current`，未修改 Java、配置、migration、测试、前端、Python 或部署脚本。

本轮未调用 OKX、Binance 或任何真实交易所；未读取或输出真实密钥、API key、secret、token、cookie、passphrase、private key、助记词或交易所凭证；未接 AI、DH 或 LIVE；未实现修复。

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
   - 构造函数设置 `lastRefreshAt = Instant.EPOCH` 后立即执行 `refreshNow("bootstrap-okx-instruments")`。
   - `refreshNow` 调用 `publicHttpClient.get("/api/v5/public/instruments?instType=SPOT", traceId)`。

因此，外联发生在测试方法业务动作之前，是 Spring context bootstrap 副作用，而不是两个测试显式请求 OKX。

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

- OKX adapter bootstrap 存在 public instruments 外联尝试，违反 no-outbound test isolation 预期。触发点是 `OkxInstrumentsCache` 构造期 eager `refreshNow`。
- `stub-on-bootstrap-failure` 只在外联失败后 fallback，不能作为 no-outbound 控制开关。把该配置理解为“禁止外联”会形成错误验收结论。

### P2

- `OkxRuntimeConfig.fromSystemEnv()` 不读取 Spring YAML，导致 Spring profile 下的测试隔离配置无法统一约束 OKX baseUrl、bootstrap mode 或 no-outbound 行为。
- 两个业务测试使用 Binance fixture，却因完整 Spring context 装配触发 OKX adapter bootstrap，测试依赖边界不够收敛，后续 CI 或离线环境可能出现非确定性网络行为。
- OKX 与 Binance cache bootstrap 策略不一致；OKX 构造期 eager refresh，Binance 惰性刷新，增加测试隔离和启动行为理解成本。

### P3

- 现有文档需要明确：local integration test 通过不等于 no-outbound 通过；需要单独补 no-outbound 回归测试。
- `stub-on-bootstrap-failure` 命名容易被误读为“不触发真实 bootstrap”，后续可在修复时同步补充注释和测试说明。

## 8. Gate And Credential Impact

### GateJ completed

GateJ completed 不受影响。本问题是 local integration test 启动期 public endpoint 外联隔离问题，不新增业务能力，不影响 GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed 的既有结论，也不代表 GateK implementation、AI、DH 或 LIVE started。

### Credential permission probe

Credential permission probe 不受影响。该问题发生在 OKX public instruments cache bootstrap，不读取 credential material，不调用交易所私有认证接口，不执行 permission probe，不改变 V31 schema-only permission probe 仍未实现的事实。

## 9. Recommended Follow-Up FIX

推荐后续单独开 FIX 任务，按以下优先级处理。

### 首选：惰性化 `OkxInstrumentsCache` bootstrap

- 移除构造函数内的 eager `refreshNow("bootstrap-okx-instruments")`。
- 保持 `getRequired` / `snapshot` 触发 `refreshIfDue` 的现有语义。
- 如需要启动期预热，应通过显式 Spring-controlled bootstrap runner 或 profile/config 开关执行，而不是 cache constructor 副作用。

### 备选：增加 Spring 驱动 no-outbound / stub bootstrap mode

- 新增 Spring 配置项表达 `no-outbound` 或 `stub bootstrap`。
- 让 `ExchangeAdapterConfiguration` 在构造真实 OKX adapter 前决定使用 stub / no-op public client / disabled bootstrap。
- 该配置必须能被 `application-local.yml`、测试 profile 和 CI profile 稳定控制。

### 必须补 no-outbound 回归测试

- 覆盖 `MarketdataControllerLocalIntegrationTest` 与 `ResearchBacktestHappyPathLocalTest` 的 context bootstrap 不访问 OKX public endpoint。
- 测试应使用明确的 fake public client、blocked HTTP client 或请求计数器，不依赖真实网络不可达来证明通过。
- 覆盖 `stub-on-bootstrap-failure=true` 不等价于 no-outbound 的负例或配置语义说明。
- 保持 Binance 对照：构造期不触发 `exchangeInfo`。

## 10. Boundary Confirmation

- 本轮未修改 Java。
- 本轮未修改配置。
- 本轮未新增 migration。
- 本轮未修改测试。
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

## 12. Rollback

如需回滚本轮文档落档，删除本文件，并回退 `README.md`、`docs/current/README.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md` 中对应索引和记录即可。本轮没有代码、配置、数据库或部署副作用。
