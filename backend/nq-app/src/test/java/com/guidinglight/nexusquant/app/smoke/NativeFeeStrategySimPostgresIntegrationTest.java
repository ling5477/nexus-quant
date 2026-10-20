package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingEntry;
import com.guidinglight.nexusquant.ledger.service.port.LedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.service.port.SimCashFundingPort;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.research.application.BacktestPublishService;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.command.BacktestPublishRequest;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.research.domain.eval.TradeOutcomeCalculator;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.research.infra.backtest.jdbc.JdbcSimTradeRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 合成loopback原生reader/JIT观察经正式V2冻结、回测、PG原币事实和评估；经正式Risk/Execution/PAPER撮合进入canonical经济事实；不联网，不授予LIVE。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@AutoConfigureMockMvc
@SpringBootTest(
        classes = {
            NexusQuantApplication.class,
            NativeFeeStrategySimPostgresIntegrationTest.FixtureConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = NqAppContextPostgresSmokeTest.NoOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = NativeFeeStrategySimPostgresIntegrationTest.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.sql.init.mode=never",
            "spring.task.scheduling.enabled=false",
            "nq.runtime.trading-components.enabled=true",
            "nq.strategy-sim.enabled=true",
            "nq.continuous-sim.enabled=false",
            "nq.okx.recovery.enabled=false",
            "nq.okx.ws.enabled=false",
            "nq.binance.ws.enabled=false",
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
class NativeFeeStrategySimPostgresIntegrationTest {
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
    @Autowired BacktestPublishService publishes;
    @Autowired StrategySimRunService sim;
    @Autowired PaperTradingRunService simRuns;
    @Autowired PaperMatchingService matching;
    @Autowired SimCashFundingPort funding;
    @MockitoSpyBean LedgerPostingRepository ledgerPosting;
    @Autowired StrategySimAnalysisQuery analysis;
    private long owner;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        database = ReleaseBaselineTestDatabase.create();
        Flyway.configure()
                .dataSource(database.source())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                // 当前适用费率与优惠字段共用同一正式schema，保留原费用断言。
                .target("27")
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

    /** 原生观察到发布、资金预留、Risk、Order、Trade、Ledger与曲线同一经济链；正/零/负费用及滑点均不改名。 */
    @Test
    void nativeFeesFlowThroughCanonicalSimWithOriginalMarketAndNetAssets() throws Exception {
        AtomicReference<String> failSuffix = new AtomicReference<>();
        // 只在真实JDBC writer的一次分录写入前注入失败；其余调用执行原方法，证明外层交易原子回滚。
        doAnswer(
                        invocation -> {
                            LedgerPostingEntry entry = invocation.getArgument(0);
                            String suffix = failSuffix.get();
                            if (suffix != null
                                    && entry.idempotencyKey().endsWith(suffix)
                                    && failSuffix.compareAndSet(suffix, null)) {
                                throw new IllegalStateException("SYNTHETIC_PARTIAL_LEDGER_FAILURE");
                            }
                            return invocation.callRealMethod();
                        })
                .when(ledgerPosting)
                .insertEntry(any(LedgerPostingEntry.class));
        asOwner(
                () -> {
                    for (String signed : List.of("0.001", "0", "-0.001"))
                        for (String slippage : List.of("0", "25")) {
                            try (var provider = new NativeProvider(signed)) {
                                var account =
                                        accounts.create(
                                                owner,
                                                new ExchangeAccountCreateCommand(
                                                        "BINANCE",
                                                        "LIVE",
                                                        "Canonical synthetic fees "
                                                                + UUID.randomUUID(),
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
                                                BinanceCommissionHttpReader.PermissionScope
                                                        .READ_ONLY);
                                var observation =
                                        fees.sync(
                                                owner,
                                                account.exchangeAccountId(),
                                                UUID.randomUUID(),
                                                verified.credentialId(),
                                                "BTC-USDT");
                                List<HistoricalBar> input = inputBars();
                                for (var bar : input)
                                    bars.upsertBars(
                                            List.of(bar),
                                            "CANONICAL_NATIVE_SYNTHETIC",
                                            bar.availableAt());
                                var dataset =
                                        datasets.createDataset(
                                                new CreateMarketdataDatasetCommand(
                                                        "Canonical native bars "
                                                                + UUID.randomUUID(),
                                                        "BINANCE",
                                                        "SPOT",
                                                        "BTC-USDT",
                                                        "1m",
                                                        input.getFirst().openTime(),
                                                        input.getLast().closeTime(),
                                                        "synthetic-test"));
                                assertEquals("READY", dataset.status().name());
                                String snapshot =
                                        datasets.buildDatasetSnapshot(dataset.datasetId());
                                var researchAccount =
                                        accounts.create(
                                                owner,
                                                new ExchangeAccountCreateCommand(
                                                        "BINANCE",
                                                        "SIM",
                                                        "Native research " + UUID.randomUUID(),
                                                        null));
                                String strategy = "native-sim-" + UUID.randomUUID(),
                                        code = "native-sim-sma-" + UUID.randomUUID();
                                Instant now = Instant.now();
                                definitions.insert(
                                        new StrategyDefinition(
                                                strategy,
                                                code,
                                                "Canonical native SMA",
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
                                                        "Native canonical research",
                                                        "synthetic",
                                                        "{}",
                                                        "{}",
                                                        snapshot));
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
                                                .put("quantityStep", "0.00000001")
                                                .put("priceTick", "0.00000001")
                                                .put("minimumQuantity", "0.00000001")
                                                .put("minimumNotional", "5")
                                                .put("slippageBps", slippage);
                                var config =
                                        configs.create(
                                                new BacktestConfigCreateRequest(
                                                        experiment.researchConfigId(),
                                                        "Native canonical backtest",
                                                        "synthetic",
                                                        input.getFirst().openTime(),
                                                        input.getLast().closeTime(),
                                                        d("100"),
                                                        spec.toString(),
                                                        "{}"));
                                configs.bindDataset(
                                        config.backtestConfigId(),
                                        dataset.datasetId().toString(),
                                        snapshot);
                                configs.bindStrategyVersion(
                                        config.backtestConfigId(),
                                        strategyVersion.strategyVersionId());
                                var backtest =
                                        runs.create(
                                                new BacktestRunStartRequest(
                                                        config.backtestConfigId()));
                                assertEquals(
                                        "SUCCEEDED",
                                        execution
                                                .startRun(backtest.backtestRunId())
                                                .resultStatus()
                                                .name());
                                assertEquals(
                                        "SUCCEEDED",
                                        evaluations
                                                .evaluate(backtest.backtestRunId())
                                                .evaluationStatus()
                                                .name());
                                var publish =
                                        publishes.publish(
                                                new BacktestPublishRequest(
                                                        backtest.backtestRunId(),
                                                        "Canonical native publish",
                                                        strategyVersion.strategyVersionId()));
                                assertEquals("SUCCEEDED", publish.publishStatus().name());
                                var created =
                                        sim.create(
                                                publish.publishRecordId(),
                                                d("100"),
                                                "native-owner");
                                assertNull(created.rules());
                                var persistedRun = simRuns.getById(created.paperRunId());
                                assertEquals("BINANCE", persistedRun.exchangeCode());
                                assertEquals("SIM", persistedRun.tradeEnv());
                                assertEquals(
                                        "PAPER",
                                        jdbc.queryForObject(
                                                "SELECT venue FROM accounts WHERE account_id=?",
                                                String.class,
                                                created.canonicalAccountId()));
                                simRuns.start(created.paperRunId());
                                sim.advance(created.paperRunId());
                                sim.advance(created.paperRunId());
                                var buyDecision = sim.advance(created.paperRunId());
                                assertEquals("ACCEPTED", buyDecision.status());
                                assertEquals("BUY", buyDecision.side());
                                assertNull(buyDecision.feeRate());
                                assertNotNull(buyDecision.feeModelJson());
                                assertEquals(
                                        "BINANCE",
                                        tree(buyDecision.feeModelJson())
                                                .path("feeExchangeCode")
                                                .asText());
                                sim.advance(created.paperRunId());
                                assertEquals(1, sim.facts(created.paperRunId()).orders().size());
                                failSuffix.set(signed.equals("0") ? ":LEDGER:2" : ":LEDGER:FEE_2");
                                assertEquals(0, matching.matchOnce(100));
                                assertNull(failSuffix.get());
                                assertEquals(
                                        0,
                                        jdbc.queryForObject(
                                                "SELECT count(*) FROM trades WHERE account_id=?",
                                                Integer.class,
                                                created.canonicalAccountId()));
                                assertEquals(
                                        0,
                                        jdbc.queryForObject(
                                                "SELECT count(*) FROM ledger_entries WHERE account_id=? AND ref_type='TRADE'",
                                                Integer.class,
                                                created.canonicalAccountId()));
                                equal(d("100"), funding.cashBalance(created.canonicalAccountId()));
                                // 两个真实撮合事务争用同一账户锁；同一订单只允许一个Trade和一套双边分录提交。
                                try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                                    var first = workers.submit(() -> matching.matchOnce(100));
                                    var second = workers.submit(() -> matching.matchOnce(100));
                                    assertEquals(
                                            1,
                                            first.get(20, TimeUnit.SECONDS)
                                                    + second.get(20, TimeUnit.SECONDS));
                                }
                                assertEquals(
                                        1,
                                        jdbc.queryForObject(
                                                "SELECT count(*) FROM trades WHERE account_id=?",
                                                Integer.class,
                                                created.canonicalAccountId()));
                                for (int i = 0; i < input.size(); i++) {
                                    sim.advance(created.paperRunId());
                                    matching.matchOnce(100);
                                }
                                var facts = sim.facts(created.paperRunId());
                                assertEquals(2, facts.trades().size());
                                var trades =
                                        jdbc.queryForList(
                                                "SELECT t.*,o.side FROM trades t JOIN orders o ON o.order_id=t.order_id WHERE t.account_id=? ORDER BY t.ts,t.trade_id",
                                                created.canonicalAccountId());
                                BigDecimal cash = d("100"), base = BigDecimal.ZERO;
                                for (var trade : trades) {
                                    boolean buy = "BUY".equals(trade.get("side"));
                                    var quantity = (BigDecimal) trade.get("qty");
                                    var price = (BigDecimal) trade.get("price");
                                    var fee = (BigDecimal) trade.get("fee");
                                    var notional =
                                            quantity.multiply(price)
                                                    .setScale(8, RoundingMode.HALF_UP);
                                    equal(
                                            (buy ? quantity : quantity.multiply(price))
                                                    .multiply(d(signed))
                                                    .setScale(8, RoundingMode.HALF_UP),
                                            fee);
                                    assertEquals(buy ? "BTC" : "USDT", trade.get("fee_currency"));
                                    base =
                                            buy
                                                    ? base.add(quantity).subtract(fee)
                                                    : base.subtract(quantity);
                                    cash =
                                            buy
                                                    ? cash.subtract(notional)
                                                    : cash.add(notional).subtract(fee);
                                    assertEquals(
                                            1,
                                            jdbc.queryForObject(
                                                    "SELECT count(*) FROM risk_events WHERE scope='ORDER' AND scope_id=? AND decision='ALLOW'",
                                                    Integer.class,
                                                    trade.get("order_id")));
                                }
                                equal(cash, facts.cash());
                                equal(base, facts.positionQuantity());
                                equal(cash, funding.cashBalance(created.canonicalAccountId()));
                                equal(BigDecimal.ZERO, base);
                                assertEquals(
                                        0,
                                        jdbc.queryForObject(
                                                "SELECT count(*) FROM (SELECT currency FROM ledger_entries WHERE account_id=? GROUP BY currency HAVING sum(delta)<>0) invalid",
                                                Integer.class,
                                                created.canonicalAccountId()));
                                var view =
                                        analysis.readPerformance(
                                                        owner, created.paperRunId(), null, null)
                                                .orElseThrow();
                                assertEquals("AVAILABLE", view.status());
                                equal(facts.equity(), view.finalEquity());
                                equal(facts.pnl(), view.pnl());
                                // SMA在平价窗口也可能退出；按两笔真实收支独立确定胜负，不预设该夹具一定盈利。
                                BigDecimal closedCashOutcome = cash.subtract(d("100"));
                                assertEquals("AVAILABLE", view.outcomes().status());
                                assertEquals(
                                        closedCashOutcome.signum() > 0 ? 1 : 0,
                                        view.outcomes().winningCount());
                                assertEquals(
                                        closedCashOutcome.signum() < 0 ? 1 : 0,
                                        view.outcomes().losingCount());
                                assertEquals(
                                        closedCashOutcome.signum() == 0 ? 1 : 0,
                                        view.outcomes().flatCount());
                                equal(
                                        closedCashOutcome.max(BigDecimal.ZERO),
                                        view.outcomes().netProfit());
                                equal(
                                        closedCashOutcome.min(BigDecimal.ZERO).abs(),
                                        view.outcomes().netLoss());
                                System.out.println(
                                        "CANONICAL_NATIVE_SIM_CASE signed="
                                                + signed
                                                + " slippage="
                                                + slippage
                                                + " source=BINANCE execution=PAPER pnl="
                                                + closedCashOutcome
                                                + " trades=2 base=0");
                                String frozen =
                                        jdbc.queryForObject(
                                                "SELECT config_snapshot_json::text FROM paper_trading_runs WHERE paper_run_id=?",
                                                String.class,
                                                created.paperRunId());
                                matching.matchOnce(100);
                                sim.advance(created.paperRunId());
                                assertEquals(2, sim.facts(created.paperRunId()).trades().size());
                                assertEquals(
                                        frozen,
                                        jdbc.queryForObject(
                                                "SELECT config_snapshot_json::text FROM paper_trading_runs WHERE paper_run_id=?",
                                                String.class,
                                                created.paperRunId()));
                                assertThrows(
                                        DataIntegrityViolationException.class,
                                        () ->
                                                jdbc.update(
                                                        "UPDATE strategy_sim_decisions SET fee_rate=0 WHERE decision_id=?",
                                                        buyDecision.decisionId()));
                                assertThrows(
                                        DataIntegrityViolationException.class,
                                        () ->
                                                jdbc.update(
                                                        "UPDATE strategy_sim_decisions SET fee_model_json=NULL WHERE decision_id=?",
                                                        buyDecision.decisionId()));
                                assertThrows(
                                        DataIntegrityViolationException.class,
                                        () ->
                                                jdbc.update(
                                                        "UPDATE strategy_sim_decisions SET quantity=quantity+0.01 WHERE decision_id=?",
                                                        buyDecision.decisionId()));
                                assertEquals(3, provider.gets.get());
                                assertEquals(
                                        "ENGAGED",
                                        jdbc.queryForObject(
                                                "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                                                String.class));
                            } finally {
                                fixture.delegate = null;
                            }
                        }
                    assertEquals(
                            0,
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM orders WHERE trade_env='LIVE'",
                                    Integer.class));
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
        String[] prices = {"98", "99", "99.9", "100", "100", "100", "90", "101"};
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
