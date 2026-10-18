package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.account.application.service.AccountFeeService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.fee.FrozenDirectionalFeeModel;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.binance.readonly.JdbcBinanceAccountFeeReadPort;
import com.guidinglight.nexusquant.adapter.binance.fee.BinanceCommissionHttpReader;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.app.config.account.AccountCredentialRuntimeProperties;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.domain.eval.TradeOutcomeCalculator;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.research.infra.backtest.jdbc.JdbcSimTradeRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.security.token.port.TokenService;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import com.guidinglight.nexusquant.strategy.domain.SpotSmaTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 合成loopback原生reader/JIT观察经正式V2冻结、回测、PG原币事实和评估；不操作canonical经济事实。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@AutoConfigureMockMvc
@SpringBootTest(
        classes = {
            NexusQuantApplication.class,
            NativeFeeBacktestPostgresIntegrationTest.FixtureConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = NqAppContextPostgresSmokeTest.NoOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = NativeFeeBacktestPostgresIntegrationTest.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.sql.init.mode=never",
            "spring.task.scheduling.enabled=false",
            "nq.runtime.trading-components.enabled=false",
            "nq.strategy-sim.enabled=false",
            "nq.continuous-sim.enabled=false",
            "nq.public-marketdata.outbound.enabled=false",
            "nq.auth.bootstrap-admin.enabled=false",
            "nq.instrument.catalog-sync.enabled=false",
            "nq.okx.recovery.enabled=false",
            "nq.okx.ws.enabled=false",
            "nq.binance.ws.enabled=false",
            "nq.account.performance.sampling-enabled=false",
            "nq.account.credentials.verification-mode=STRUCTURAL",
            "nq.account-fees.binance.enabled=false",
            "nq.account.credentials.master-key=native-fee-synthetic-master-key-123456789",
            "nq.security.issuer=native-fee-synthetic",
            "nq.security.secret=native-fee-synthetic-jwt-secret-123456789"
        })
class NativeFeeBacktestPostgresIntegrationTest {
    private static final String PASSWORD = "native-fee-synthetic-login";
    private static ReleaseBaselineTestDatabase database;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired AuthSeedService seeds;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenService tokens;
    @Autowired ExchangeAccountCommandService accounts;
    @Autowired ExchangeAccountCredentialCommandService credentials;
    @Autowired ExchangeAccountCredentialVerificationService verification;
    @Autowired ExchangeAccountRepository accountRows;
    @Autowired ExchangeAccountCredentialRepository credentialRows;
    @Autowired AccountCredentialRuntimeProperties credentialProperties;
    @Autowired KillSwitchService kill;
    @Autowired AccountFeeService fees;
    @Autowired ResearchFeeSourcePort source;
    @Autowired MarketdataBarRepository bars;
    @Autowired MarketdataDatasetService datasets;
    @Autowired StrategyDefinitionRepository definitions;
    @Autowired StrategyVersionService versions;
    @Autowired ResearchConfigService research;
    @Autowired BacktestConfigService configs;
    @Autowired BacktestRunService runs;
    @Autowired BacktestExecutionService execution;
    @Autowired BacktestEvaluationService evaluations;
    @Autowired FixtureSource fixture;
    private long owner;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        database = ReleaseBaselineTestDatabase.create();
        Flyway.configure()
                .dataSource(database.source())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                // 当前费用适配器依赖优惠换算列，随机测试库必须使用同一候选的完整冻结迁移集合。
                .target("22")
                .load()
                .migrate();
        registry.add("spring.datasource.url", database::url);
        registry.add(
                "spring.datasource.username", () -> System.getProperty("nq.postgres.smoke.user"));
        registry.add(
                "spring.datasource.password",
                () -> System.getProperty("nq.postgres.smoke.password"));
    }

    @BeforeEach
    void identities() {
        // 登录沿用正式管理员初始化前提；仅在本随机库建立合成管理员，不绕过认证安全门。
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "native-admin", passwords.encode(PASSWORD), List.of("ADMIN"), true),
                        new SeedUserCommand(
                                "native-owner",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                "native-other",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true)));
        owner =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='native-owner'", Long.class);
    }

    @Test
    void signedNativeFeesFreezeIntoAssetAwareBacktestAndBuyAndHoldThenEvaluate() throws Exception {
        asOwner(
                () -> {
                    for (String signed : List.of("0.001", "0", "-0.001"))
                        try (var provider = new NativeProvider(signed)) {
                            var account =
                                    accounts.create(
                                            owner,
                                            new ExchangeAccountCreateCommand(
                                                    "BINANCE",
                                                    "LIVE",
                                                    "Synthetic native fees " + UUID.randomUUID(),
                                                    null));
                            var version =
                                    credentials.upsert(
                                            owner,
                                            account.exchangeAccountId(),
                                            new ExchangeAccountCredentialUpsertCommand(
                                                    "BINANCE_HMAC",
                                                    NativeProvider.KEY,
                                                    NativeProvider.SECRET,
                                                    null,
                                                    null),
                                            1);
                            var verified =
                                    verification.verifyVersion(
                                            owner,
                                            account.exchangeAccountId(),
                                            version.credentialId());
                            fixture.delegate =
                                    new JdbcBinanceAccountFeeReadPort(
                                            jdbc,
                                            credentialProperties.getMasterKey(),
                                            new BinanceCommissionHttpReader(
                                                    provider.client,
                                                    provider.base(),
                                                    BinanceCommissionHttpReader.Environment
                                                            .ISOLATED_FIXTURE,
                                                    Duration.ofSeconds(4),
                                                    Clock.systemUTC()),
                                            accountRows,
                                            credentialRows,
                                            kill,
                                            owner,
                                            account.exchangeAccountId(),
                                            verified.credentialId(),
                                            "BINANCE_HMAC",
                                            Set.of("BTC-USDT"),
                                            BinanceCommissionHttpReader.PermissionScope.READ_ONLY);
                            var observation =
                                    fees.sync(
                                            owner,
                                            account.exchangeAccountId(),
                                            UUID.randomUUID(),
                                            verified.credentialId(),
                                            "BTC-USDT");
                            ObjectNode selected =
                                    mapper.createObjectNode()
                                            .put("costSource", "CURRENT_ACCOUNT_OBSERVATION")
                                            .put("feeExchangeCode", "BINANCE")
                                            .put("feePaymentPolicy", "RECEIVED_ASSET")
                                            .put(
                                                    "feeObservationId",
                                                    observation.observationId().toString())
                                            .put(
                                                    "feeAccountId",
                                                    Long.toString(account.exchangeAccountId()))
                                            .put("feeTradeEnv", "LIVE")
                                            .put("feeSymbol", "BTC-USDT");
                            // 此参数仅作为原生JSON元数据冻结，不宣称回测引擎执行未知参数；三种费用符号沿用原经济断言。
                            BigDecimal preciseParameter = d("9007199254740993.123456789012345678");
                            selected.put("researchNumericParameter", preciseParameter);
                            ObjectNode invalidScalar = selected.deepCopy().put("feeRate", signed);
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> source.prepareConfiguration(invalidScalar.toString()));
                            ObjectNode unsupportedDiscount =
                                    selected.deepCopy()
                                            .put("feePaymentPolicy", "DISCOUNT_ASSET_IF_FUNDED");
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            source.prepareConfiguration(
                                                    unsupportedDiscount.toString()));
                            List<HistoricalBar> input = inputBars();
                            // 合成回放在每根收盘时可见；按逐bar观察时间写入，不能把本次运行时刻当作历史可交易时刻。
                            for (HistoricalBar bar : input)
                                bars.upsertBars(
                                        List.of(bar),
                                        "SYNTHETIC_NATIVE_MODEL_TEST",
                                        bar.availableAt());
                            var dataset =
                                    datasets.createDataset(
                                            new CreateMarketdataDatasetCommand(
                                                    "Native synthetic bars " + UUID.randomUUID(),
                                                    "BINANCE",
                                                    "SPOT",
                                                    "BTC-USDT",
                                                    "1m",
                                                    input.getFirst().openTime(),
                                                    input.getLast().closeTime(),
                                                    "synthetic-test"));
                            assertEquals("READY", dataset.status().name());
                            String datasetJson = datasets.buildDatasetSnapshot(dataset.datasetId());
                            // 研究策略使用独立SIM身份；LIVE仅是合成费用观察的来源范围，不建立LIVE执行或资金权限。
                            var researchAccount =
                                    accounts.create(
                                            owner,
                                            new ExchangeAccountCreateCommand(
                                                    "BINANCE",
                                                    "SIM",
                                                    "Synthetic research account "
                                                            + UUID.randomUUID(),
                                                    null));
                            String suffix = UUID.randomUUID().toString();
                            String strategy = "native-" + suffix, code = "native-fee-" + suffix;
                            Instant now = Instant.now();
                            definitions.insert(
                                    new StrategyDefinition(
                                            strategy,
                                            code,
                                            "Synthetic native fee SMA",
                                            SpotSmaTargetStrategy.EXECUTABLE,
                                            "BINANCE",
                                            researchAccount.legacyAccountId(),
                                            "SIM",
                                            true,
                                            "{}",
                                            1,
                                            now,
                                            now));
                            var strategyVersion =
                                    versions.create(
                                            new StrategyVersionCreateRequest(
                                                    code,
                                                    "v1",
                                                    "ACTIVE",
                                                    "{\"window\":3,\"investedExposure\":\"1\"}",
                                                    "{}",
                                                    "{\"executable\":\"SPOT_SMA_TARGET_V1\"}",
                                                    "synthetic-test"));
                            var experiment =
                                    research.create(
                                            new ResearchConfigCreateRequest(
                                                    strategy,
                                                    "Native research " + suffix,
                                                    "synthetic",
                                                    "{}",
                                                    "{}",
                                                    datasetJson));
                            selected.put("quantityStep", "0.0001")
                                    .put("priceTick", "0.01")
                                    .put("minimumQuantity", "0.0001")
                                    .put("minimumNotional", "5")
                                    .put("slippageBps", "0");
                            var config =
                                    configs.create(
                                            new BacktestConfigCreateRequest(
                                                    experiment.researchConfigId(),
                                                    "Native backtest " + suffix,
                                                    "synthetic",
                                                    input.getFirst().openTime(),
                                                    input.getLast().closeTime(),
                                                    d("100"),
                                                    selected.toString(),
                                                    "{}"));
                            assertEquals(
                                    preciseParameter,
                                    exactTree(config.executionSpec())
                                            .path("researchNumericParameter")
                                            .decimalValue());
                            String storedConfig =
                                    jdbc.queryForObject(
                                            "SELECT config_json::text FROM backtest_configs WHERE backtest_config_id=?",
                                            String.class,
                                            config.backtestConfigId());
                            assertEquals(
                                    preciseParameter,
                                    exactTree(storedConfig)
                                            .path("executionSpec")
                                            .path("researchNumericParameter")
                                            .decimalValue());
                            configs.bindDataset(
                                    config.backtestConfigId(),
                                    dataset.datasetId().toString(),
                                    datasetJson);
                            configs.bindStrategyVersion(
                                    config.backtestConfigId(), strategyVersion.strategyVersionId());
                            var run =
                                    runs.create(
                                            new BacktestRunStartRequest(config.backtestConfigId()));
                            assertEquals(
                                    preciseParameter,
                                    exactTree(run.configSnapshotJson())
                                            .path("executionSpec")
                                            .path("researchNumericParameter")
                                            .decimalValue());
                            assertEquals(
                                    preciseParameter,
                                    exactTree(
                                                    runs.getByBacktestRunId(run.backtestRunId())
                                                            .configSnapshotJson())
                                            .path("executionSpec")
                                            .path("researchNumericParameter")
                                            .decimalValue());
                            JsonNode spec = tree(run.configSnapshotJson()).path("executionSpec"),
                                    snapshot = tree(spec.path("feeSourceSnapshotJson").asText());
                            assertFalse(spec.has("feeRate"));
                            assertEquals(
                                    FrozenDirectionalFeeModel.VERSION,
                                    spec.path("feeAssumptionVersion").asText());
                            assertEquals(
                                    FrozenDirectionalFeeModel.digest(snapshot),
                                    spec.path("feeSourceSnapshotSha256").asText());
                            ObjectNode tamperedSnapshot = ((ObjectNode) snapshot).deepCopy();
                            ((ObjectNode) tamperedSnapshot.path("commissionTerms").path("standard"))
                                    .put("buyer", "0.000000000001");
                            ObjectNode tampered =
                                    ((ObjectNode) spec)
                                            .deepCopy()
                                            .put(
                                                    "feeSourceSnapshotJson",
                                                    tamperedSnapshot.toString())
                                            .put(
                                                    "feeSourceSnapshotSha256",
                                                    FrozenDirectionalFeeModel.digest(
                                                            tamperedSnapshot));
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            source.validateFrozenExecution(
                                                    tampered.toString(), datasetJson));
                            ObjectNode wrongMarket =
                                    ((ObjectNode) tree(datasetJson))
                                            .deepCopy()
                                            .put("exchangeCode", "OKX");
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            source.validateFrozenExecution(
                                                    spec.toString(), wrongMarket.toString()));
                            credentials.revoke(
                                    owner,
                                    account.exchangeAccountId(),
                                    verified.credentialId(),
                                    "synthetic",
                                    "test");
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> source.prepareConfiguration(selected.toString()));
                            source.validateFrozenExecution(spec.toString(), datasetJson);
                            var result = execution.startRun(run.backtestRunId());
                            assertEquals(
                                    "SUCCEEDED",
                                    result.resultStatus().name(),
                                    result.summaryJson());
                            var saved =
                                    new JdbcSimTradeRepository(jdbc)
                                            .listByBacktestRunId(run.backtestRunId());
                            String decisions =
                                    tree(result.summaryJson()).path("decisionReasons").toString();
                            assertTrue(
                                    saved.stream().anyMatch(trade -> "BUY".equals(trade.side())),
                                    "BUY absent: " + decisions);
                            assertTrue(
                                    saved.stream().anyMatch(trade -> "SELL".equals(trade.side())),
                                    "SELL absent: " + decisions);
                            BigDecimal expectedCash = d("100"), held = d("0"), totalFee = d("0");
                            for (var trade : saved) {
                                assertEquals(
                                        "BUY".equals(trade.side()) ? "BTC" : "USDT",
                                        trade.feeCurrency());
                                assertEquals("RECEIVED_ASSET", trade.feePaymentStatus());
                                assertEquals(8, trade.feeValuationScale());
                                assertEquals(d(signed).signum(), trade.feeNativeAmount().signum());
                                BigDecimal basis =
                                        "BUY".equals(trade.side())
                                                ? trade.quantity()
                                                : trade.quantity().multiply(trade.tradePrice());
                                equal(
                                        basis.multiply(d(signed)).setScale(8, RoundingMode.HALF_UP),
                                        trade.feeNativeAmount());
                                held = held.add(trade.settledBaseDelta());
                                expectedCash =
                                        "BUY".equals(trade.side())
                                                ? expectedCash.subtract(
                                                        trade.quantity()
                                                                .multiply(trade.tradePrice()))
                                                : expectedCash.add(
                                                        trade.quantity()
                                                                .multiply(trade.tradePrice()));
                                expectedCash = expectedCash.subtract(trade.cashFeeAmount());
                                totalFee = totalFee.add(trade.feeAmount());
                            }
                            JsonNode summary = tree(result.summaryJson());
                            equal(expectedCash, d(summary.path("finalCashBalance").asText()));
                            equal(held, d(summary.path("finalPositionQuantity").asText()));
                            equal(totalFee, d(summary.path("totalFee").asText()));
                            assertTrue(
                                    summary.path("researchFacts")
                                            .path("assumptions")
                                            .path("feeRate")
                                            .isNull());
                            JsonNode benchmark = summary.path("researchFacts").path("benchmark");
                            assertEquals("BTC", benchmark.path("feeCurrency").asText());
                            assertEquals(
                                    "RECEIVED_ASSET", benchmark.path("feePaymentStatus").asText());
                            BigDecimal grossBenchmarkQuantity =
                                    d("100").divide(d("101"), 18, RoundingMode.DOWN)
                                            .divideToIntegralValue(d("0.0001"))
                                            .multiply(d("0.0001"));
                            if (d(signed).signum() < 0)
                                grossBenchmarkQuantity =
                                        grossBenchmarkQuantity
                                                .divide(
                                                        d("1").subtract(d(signed)),
                                                        18,
                                                        RoundingMode.DOWN)
                                                .divideToIntegralValue(d("0.0001"))
                                                .multiply(d("0.0001"));
                            BigDecimal benchmarkFee =
                                    grossBenchmarkQuantity
                                            .multiply(d(signed))
                                            .setScale(8, RoundingMode.HALF_UP);
                            equal(
                                    grossBenchmarkQuantity.subtract(benchmarkFee),
                                    benchmark.path("quantity").decimalValue());
                            equal(benchmarkFee, benchmark.path("feeNativeAmount").decimalValue());
                            assertEquals(
                                    "SUCCEEDED",
                                    evaluations
                                            .evaluate(run.backtestRunId())
                                            .evaluationStatus()
                                            .name());
                            var ownerContext = SecurityContextHolder.getContext();
                            try {
                                String token = login("native-owner");
                                JsonNode api =
                                        tree(
                                                mvc.perform(
                                                                get("/api/backtest-runs/"
                                                                                + run
                                                                                        .backtestRunId()
                                                                                + "/sim-trades")
                                                                        .header(
                                                                                "Authorization",
                                                                                "Bearer " + token))
                                                        .andExpect(status().isOk())
                                                        .andReturn()
                                                        .getResponse()
                                                        .getContentAsString());
                                assertEquals(saved.size(), api.size());
                                assertTrue(api.get(0).hasNonNull("feeNativeAmount"));
                                String other = login("native-other");
                                int denied =
                                        mvc.perform(
                                                        get("/api/backtest-runs/"
                                                                        + run.backtestRunId()
                                                                        + "/sim-trades")
                                                                .header(
                                                                        "Authorization",
                                                                        "Bearer " + other))
                                                .andReturn()
                                                .getResponse()
                                                .getStatus();
                                assertTrue(Set.of(400, 404).contains(denied));
                            } finally {
                                SecurityContextHolder.setContext(ownerContext);
                            }
                            var buy =
                                    saved.stream()
                                            .filter(trade -> "BUY".equals(trade.side()))
                                            .findFirst()
                                            .orElseThrow();
                            assertThrows(
                                    DataIntegrityViolationException.class,
                                    () ->
                                            jdbc.update(
                                                    "UPDATE sim_trades SET fee_currency='USDT' WHERE sim_trade_id=?",
                                                    buy.simTradeId()));
                            assertThrows(
                                    DataIntegrityViolationException.class,
                                    () ->
                                            jdbc.update(
                                                    "UPDATE sim_trades SET fee_native_amount=NULL WHERE sim_trade_id=?",
                                                    buy.simTradeId()));
                            assertThrows(
                                    DataIntegrityViolationException.class,
                                    () ->
                                            jdbc.update(
                                                    "UPDATE sim_trades SET fee_amount=fee_amount+1 WHERE sim_trade_id=?",
                                                    buy.simTradeId()));
                            assertThrows(
                                    DataIntegrityViolationException.class,
                                    () ->
                                            jdbc.update(
                                                    "UPDATE sim_trades SET fee_native_amount='NaN'::numeric WHERE sim_trade_id=?",
                                                    buy.simTradeId()));
                            assertEquals(
                                    saved,
                                    new JdbcSimTradeRepository(jdbc)
                                            .listByBacktestRunId(run.backtestRunId()));
                            assertEquals(3, provider.gets.get());
                            assertEquals(2, provider.signatures.get());
                            for (String table : List.of("orders", "trades", "ledger_entries"))
                                assertEquals(
                                        0L,
                                        jdbc.queryForObject(
                                                "SELECT count(*) FROM " + table, Long.class));
                            assertEquals(
                                    "ENGAGED",
                                    jdbc.queryForObject(
                                            "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                                            String.class));
                            fixture.delegate = null;
                        }
                    Flyway.configure()
                            .dataSource(database.source())
                            .locations("classpath:db/migration")
                            .cleanDisabled(true)
                            .target("11")
                            .load()
                            .validate();
                    return null;
                });
    }

    /** 正式来源、冻结、执行、评估和PG报告必须一致消费净持仓；覆盖独立微亏反例及已计价滑点。 */
    @ParameterizedTest
    @ValueSource(strings = {"1h", "1m"})
    void closedTradeOutcomesUseNativeCashBasisAndFrozenSlippageContract(String interval)
            throws Exception {
        asOwner(
                () -> {
                    int scenario = 0;
                    for (String signed : List.of("0.001", "0", "-0.001"))
                        for (String slippage : List.of("0", "25")) {
                            try (var provider = new NativeProvider(signed)) {
                                var account =
                                        accounts.create(
                                                owner,
                                                new ExchangeAccountCreateCommand(
                                                        "BINANCE",
                                                        "LIVE",
                                                        "Native outcome " + UUID.randomUUID(),
                                                        null));
                                var credential =
                                        credentials.upsert(
                                                owner,
                                                account.exchangeAccountId(),
                                                new ExchangeAccountCredentialUpsertCommand(
                                                        "BINANCE_HMAC",
                                                        NativeProvider.KEY,
                                                        NativeProvider.SECRET,
                                                        null,
                                                        null),
                                                1);
                                var verified =
                                        verification.verifyVersion(
                                                owner,
                                                account.exchangeAccountId(),
                                                credential.credentialId());
                                fixture.delegate =
                                        new JdbcBinanceAccountFeeReadPort(
                                                jdbc,
                                                credentialProperties.getMasterKey(),
                                                new BinanceCommissionHttpReader(
                                                        provider.client,
                                                        provider.base(),
                                                        BinanceCommissionHttpReader.Environment
                                                                .ISOLATED_FIXTURE,
                                                        Duration.ofSeconds(4),
                                                        Clock.systemUTC()),
                                                accountRows,
                                                credentialRows,
                                                kill,
                                                owner,
                                                account.exchangeAccountId(),
                                                verified.credentialId(),
                                                "BINANCE_HMAC",
                                                Set.of("BTC-USDT"),
                                                BinanceCommissionHttpReader.PermissionScope
                                                        .READ_ONLY);
                                var observation =
                                        fees.sync(
                                                owner,
                                                account.exchangeAccountId(),
                                                UUID.randomUUID(),
                                                verified.credentialId(),
                                                "BTC-USDT");
                                var inputs = new ArrayList<HistoricalBar>();
                                String[] prices = {"98", "99", "99.9", "100", "90", "100.2003"};
                                for (int i = 0; i < prices.length; i++) {
                                    // 同一经济oracle分别使用真实小时/分钟bar，分钟正收益必须持久化成功且明确年化超界。
                                    long step = interval.equals("1h") ? 3600 : 60;
                                    Instant at =
                                            Instant.parse(
                                                            interval.equals("1h")
                                                                    ? "2026-09-26T00:00:00Z"
                                                                    : "2026-09-27T00:00:00Z")
                                                    .plusSeconds((scenario * 8L + i) * step);
                                    var price = d(prices[i]);
                                    var bar =
                                            new HistoricalBar(
                                                    "BINANCE",
                                                    "SPOT",
                                                    "BTC-USDT",
                                                    BarInterval.fromWireValue(interval),
                                                    at,
                                                    at.plusSeconds(step - 1),
                                                    price,
                                                    price,
                                                    price,
                                                    price,
                                                    BigDecimal.ONE,
                                                    null,
                                                    null,
                                                    "OK",
                                                    "{\"fixture\":\"native-outcome-regression\"}",
                                                    at.plusSeconds(step - 1));
                                    inputs.add(bar);
                                    bars.upsertBars(
                                            List.of(bar), "NATIVE_OUTCOME_TEST", bar.availableAt());
                                }
                                scenario++;
                                var dataset =
                                        datasets.createDataset(
                                                new CreateMarketdataDatasetCommand(
                                                        "Native outcome bars " + scenario,
                                                        "BINANCE",
                                                        "SPOT",
                                                        "BTC-USDT",
                                                        interval,
                                                        inputs.getFirst().openTime(),
                                                        inputs.getLast().closeTime(),
                                                        "synthetic-test"));
                                assertEquals("READY", dataset.status().name());
                                String datasetJson =
                                        datasets.buildDatasetSnapshot(dataset.datasetId());
                                var sim =
                                        accounts.create(
                                                owner,
                                                new ExchangeAccountCreateCommand(
                                                        "BINANCE",
                                                        "SIM",
                                                        "Outcome "
                                                                + interval
                                                                + " "
                                                                + scenario
                                                                + " "
                                                                + UUID.randomUUID(),
                                                        null));
                                String strategy = "outcome-" + UUID.randomUUID(),
                                        code = "outcome-sma-" + UUID.randomUUID();
                                Instant now = Instant.now();
                                definitions.insert(
                                        new StrategyDefinition(
                                                strategy,
                                                code,
                                                "Native outcome SMA",
                                                SpotSmaTargetStrategy.EXECUTABLE,
                                                "BINANCE",
                                                sim.legacyAccountId(),
                                                "SIM",
                                                true,
                                                "{}",
                                                1,
                                                now,
                                                now));
                                var version =
                                        versions.create(
                                                new StrategyVersionCreateRequest(
                                                        code,
                                                        "v1",
                                                        "ACTIVE",
                                                        "{\"window\":3,\"investedExposure\":\"1\"}",
                                                        "{}",
                                                        "{\"executable\":\"SPOT_SMA_TARGET_V1\"}",
                                                        "synthetic-test"));
                                var experiment =
                                        research.create(
                                                new ResearchConfigCreateRequest(
                                                        strategy,
                                                        "Native outcome research",
                                                        "synthetic",
                                                        "{}",
                                                        "{}",
                                                        datasetJson));
                                var spec =
                                        mapper.createObjectNode()
                                                .put("costSource", "CURRENT_ACCOUNT_OBSERVATION")
                                                .put("feeExchangeCode", "BINANCE")
                                                .put("feePaymentPolicy", "RECEIVED_ASSET")
                                                .put(
                                                        "feeObservationId",
                                                        observation.observationId().toString())
                                                .put(
                                                        "feeAccountId",
                                                        Long.toString(account.exchangeAccountId()))
                                                .put("feeTradeEnv", "LIVE")
                                                .put("feeSymbol", "BTC-USDT")
                                                .put("quantityStep", "0.0001")
                                                .put("priceTick", "0.00000001")
                                                .put("minimumQuantity", "0.0001")
                                                .put("minimumNotional", "5")
                                                .put("slippageBps", slippage);
                                var config =
                                        configs.create(
                                                new BacktestConfigCreateRequest(
                                                        experiment.researchConfigId(),
                                                        "Native outcome backtest",
                                                        "synthetic",
                                                        inputs.getFirst().openTime(),
                                                        inputs.getLast().closeTime(),
                                                        d("100"),
                                                        spec.toString(),
                                                        "{}"));
                                configs.bindDataset(
                                        config.backtestConfigId(),
                                        dataset.datasetId().toString(),
                                        datasetJson);
                                configs.bindStrategyVersion(
                                        config.backtestConfigId(), version.strategyVersionId());
                                var run =
                                        runs.create(
                                                new BacktestRunStartRequest(
                                                        config.backtestConfigId()));
                                var result = execution.startRun(run.backtestRunId());
                                assertEquals(
                                        "SUCCEEDED",
                                        result.resultStatus().name(),
                                        result.summaryJson());
                                var trades =
                                        new JdbcSimTradeRepository(jdbc)
                                                .listByBacktestRunId(run.backtestRunId());
                                assertEquals(2, trades.size());
                                var buy = trades.get(0);
                                var sell = trades.get(1);
                                assertEquals("BUY", buy.side());
                                assertEquals("SELL", sell.side());
                                // 独立收支oracle直接用落库总成交额与净收到数量；不调用候选成本/净量辅助函数。
                                BigDecimal netBase = buy.quantity().subtract(buy.feeNativeAmount());
                                BigDecimal principal = buy.quantity().multiply(buy.tradePrice());
                                BigDecimal allocated =
                                        principal
                                                .multiply(sell.quantity())
                                                .divide(netBase, 18, RoundingMode.HALF_UP);
                                BigDecimal closedOutcome =
                                        sell.quantity()
                                                .multiply(sell.tradePrice())
                                                .subtract(sell.feeNativeAmount())
                                                .subtract(allocated)
                                                .setScale(18, RoundingMode.HALF_UP);
                                var report = evaluations.evaluate(run.backtestRunId());
                                assertEquals("SUCCEEDED", report.evaluationStatus().name());
                                String metrics =
                                        jdbc.queryForObject(
                                                "SELECT metrics_json::text FROM backtest_eval_reports WHERE backtest_run_id=?",
                                                String.class,
                                                run.backtestRunId());
                                String annualizedStatus =
                                        tree(metrics).path("annualizedReturnStatus").asText();
                                if (interval.equals("1m")
                                        && slippage.equals("0")
                                        && !signed.equals("0.001")) {
                                    assertTrue(report.netPnl().signum() > 0);
                                    assertNull(report.annualizedReturn());
                                    assertEquals("OUT_OF_RANGE", annualizedStatus);
                                } else {
                                    assertEquals("AVAILABLE", annualizedStatus);
                                    assertNotNull(report.annualizedReturn());
                                }
                                // 查询消费已提交PG报告，不用同一内存POST响应充当持久化证据。
                                var ownerContext = SecurityContextHolder.getContext();
                                try {
                                    var apiReport =
                                            tree(
                                                    mvc.perform(
                                                                    get("/api/evaluations/"
                                                                                    + report
                                                                                            .evalReportId())
                                                                            .header(
                                                                                    "Authorization",
                                                                                    "Bearer "
                                                                                            + login(
                                                                                                    "native-owner")))
                                                            .andExpect(status().isOk())
                                                            .andReturn()
                                                            .getResponse()
                                                            .getContentAsString());
                                    assertEquals(
                                            report.annualizedReturn() == null,
                                            apiReport.path("annualizedReturn").isNull());
                                    assertEquals(
                                            annualizedStatus,
                                            tree(apiReport.path("metricsJson").asText())
                                                    .path("annualizedReturnStatus")
                                                    .asText());
                                } finally {
                                    // MockMvc过滤器会清理当前线程认证；后续直接调用必须恢复真实owner，不能关闭来源权限校验。
                                    SecurityContextHolder.setContext(ownerContext);
                                }
                                var saved =
                                        jdbc.queryForMap(
                                                "SELECT winning_trade_count,losing_trade_count,flat_trade_count,win_rate FROM backtest_eval_reports WHERE backtest_run_id=?",
                                                run.backtestRunId());
                                assertEquals(
                                        closedOutcome.signum() > 0 ? 1 : 0,
                                        saved.get("winning_trade_count"));
                                assertEquals(
                                        closedOutcome.signum() < 0 ? 1 : 0,
                                        saved.get("losing_trade_count"));
                                assertEquals(
                                        closedOutcome.signum() == 0 ? 1 : 0,
                                        saved.get("flat_trade_count"));
                                equal(
                                        closedOutcome.signum() > 0
                                                ? BigDecimal.ONE
                                                : BigDecimal.ZERO,
                                        (BigDecimal) saved.get("win_rate"));
                                var outcome = new TradeOutcomeCalculator().calculate(trades, true);
                                equal(closedOutcome.max(BigDecimal.ZERO), outcome.grossProfit());
                                equal(
                                        closedOutcome.min(BigDecimal.ZERO).abs(),
                                        outcome.grossLoss());
                                if (signed.equals("0.001") && slippage.equals("0")) {
                                    equal(BigDecimal.ZERO, netBase.subtract(sell.quantity()));
                                    equal(d("-0.00000040"), report.netPnl());
                                    equal(d("0.00000040"), outcome.grossLoss());
                                    assertEquals(0, report.winningTradeCount());
                                    assertEquals(1, report.losingTradeCount());
                                }
                                assertEquals(3, provider.gets.get());
                                for (String table : List.of("orders", "trades", "ledger_entries"))
                                    assertEquals(
                                            0L,
                                            jdbc.queryForObject(
                                                    "SELECT count(*) FROM " + table, Long.class));
                                assertEquals(
                                        "ENGAGED",
                                        jdbc.queryForObject(
                                                "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                                                String.class));
                            } finally {
                                fixture.delegate = null;
                            }
                        }
                    return null;
                });
    }

    private <T> T asOwner(CheckedWork<T> work) throws Exception {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        tokens.parse(login("native-owner")).orElseThrow(), null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return work.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private String login(String user) throws Exception {
        return tree(mvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                mapper.writeValueAsString(
                                                        Map.of(
                                                                "username",
                                                                user,
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("accessToken")
                .asText();
    }

    private JsonNode exactTree(String json) throws Exception {
        return mapper.reader()
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readTree(json);
    }

    private JsonNode tree(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void equal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }

    private static List<HistoricalBar> inputBars() {
        String[] prices = {"100", "101", "102", "103", "104", "80", "81", "82"};
        var output = new ArrayList<HistoricalBar>();
        for (int i = 0; i < prices.length; i++) {
            Instant start = Instant.parse("2026-09-25T00:00:00Z").plusSeconds(i * 60L);
            BigDecimal price = d(prices[i]);
            output.add(
                    new HistoricalBar(
                            "BINANCE",
                            "SPOT",
                            "BTC-USDT",
                            BarInterval.ONE_MINUTE,
                            start,
                            start.plusSeconds(59),
                            price,
                            price,
                            price,
                            price,
                            d("1"),
                            null,
                            null,
                            "OK",
                            "{\"fixture\":\"synthetic-native-fee-bars\"}",
                            start.plusSeconds(59)));
        }
        return output;
    }

    @FunctionalInterface
    private interface CheckedWork<T> {
        T get() throws Exception;
    }

    @TestConfiguration
    static class FixtureConfiguration {
        @Bean
        FixtureSource nativeFeeTestSource() {
            return new FixtureSource();
        }
    }

    /** 仅测试装配的路由；正式JIT桥仍负责锁、解密、结构验证和ENGAGED版本重核。 */
    static class FixtureSource implements AccountFeeReadPort {
        AccountFeeReadPort delegate;

        public boolean supports(ExchangeAccountSummary account, String symbol) {
            return delegate != null && delegate.supports(account, symbol);
        }

        public AccountFeeQuote read(
                ExchangeAccountSummary account,
                ExchangeAccountCredentialSummary credential,
                String symbol) {
            return delegate.read(account, credential, symbol);
        }
    }

    /** 三个固定GET的合成供应方；HMAC由服务端校验，私有公网与真实材料不存在。 */
    private static final class NativeProvider implements AutoCloseable {
        static final String KEY = "synthetic-native-fee-key",
                SECRET = "synthetic-native-fee-secret";
        final HttpClient client = BinanceCommissionHttpReader.directHttpClient();
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final AtomicInteger gets = new AtomicInteger(), signatures = new AtomicInteger();
        final HttpServer server;
        final String signed;

        NativeProvider(String signed) throws IOException {
            this.signed = signed;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/", this::respond);
            server.start();
        }

        URI base() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        private void respond(HttpExchange request) throws IOException {
            try (request) {
                gets.incrementAndGet();
                String path = request.getRequestURI().getPath();
                if (!request.getRequestMethod().equals("GET")
                        || !path.equals("/api/v3/time") && !valid(request)) {
                    request.sendResponseHeaders(401, -1);
                    return;
                }
                String body =
                        switch (path) {
                            case "/api/v3/time" ->
                                    "{\"serverTime\":" + System.currentTimeMillis() + "}";
                            case "/sapi/v1/account/apiRestrictions" ->
                                    "{\"ipRestrict\":true,\"enableReading\":true,\"enableWithdrawals\":false,\"enableInternalTransfer\":false,\"enableSpotAndMarginTrading\":false,\"enableMargin\":false,\"enableFutures\":false,\"enableVanillaOptions\":false}";
                            case "/api/v3/account/commission" ->
                                    "{\"symbol\":\"BTCUSDT\",\"standardCommission\":{\"maker\":\"0\",\"taker\":\""
                                            + signed
                                            + "\",\"buyer\":\"0\",\"seller\":\"0\"},\"taxCommission\":{\"maker\":\"0\",\"taker\":\"0\",\"buyer\":\"0\",\"seller\":\"0\"},\"specialCommission\":{\"maker\":\"0\",\"taker\":\"0\",\"buyer\":\"0\",\"seller\":\"0\"},\"discount\":{\"enabledForAccount\":true,\"enabledForSymbol\":true,\"discountAsset\":\"BNB\",\"discount\":\"0.25\"}}";
                            default ->
                                    throw new IllegalStateException(
                                            "unexpected native fixture path");
                        };
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                request.sendResponseHeaders(200, bytes.length);
                request.getResponseBody().write(bytes);
            }
        }

        private boolean valid(HttpExchange request) {
            try {
                String raw = request.getRequestURI().getRawQuery();
                int split = raw.lastIndexOf("&signature=");
                if (split < 0 || !KEY.equals(request.getRequestHeaders().getFirst("X-MBX-APIKEY")))
                    return false;
                var mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                boolean valid =
                        HexFormat.of()
                                .formatHex(
                                        mac.doFinal(
                                                raw.substring(0, split)
                                                        .getBytes(StandardCharsets.UTF_8)))
                                .equals(raw.substring(split + 11));
                if (valid) signatures.incrementAndGet();
                return valid;
            } catch (Exception ex) {
                return false;
            }
        }

        public void close() {
            server.stop(0);
            workers.shutdownNow();
            client.close();
        }
    }

    /** 关闭Spring后只清理本夹具创建的随机库；当前验收库、端口和金融事实不动。 */
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return 999;
        }

        @Override
        public void afterTestClass(TestContext context) throws Exception {
            if (database != null) database.close();
        }
    }
}
