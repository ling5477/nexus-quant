package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.account.application.service.AccountFeeService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
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
import com.guidinglight.nexusquant.ledger.contracts.model.SimAssetFundingPlan;
import com.guidinglight.nexusquant.ledger.service.port.LedgerPostingRepository;
import com.guidinglight.nexusquant.ledger.service.port.SimAssetFundingPort;
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
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimCancellationService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimDecisionRepository;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimDecisionRepository.DecisionWrite;
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
            DiscountAssetStrategySimPostgresIntegrationTest.FixtureConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = NqAppContextPostgresSmokeTest.NoOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = DiscountAssetStrategySimPostgresIntegrationTest.Cleanup.class,
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
class DiscountAssetStrategySimPostgresIntegrationTest {
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
    @Autowired SimAssetFundingPort assetFunding;
    @Autowired StrategySimCancellationService cancellations;
    @MockitoSpyBean LedgerPostingRepository ledgerPosting;
    @MockitoSpyBean StrategySimDecisionRepository decisionPosting;
    @Autowired StrategySimAnalysisQuery analysis;
    private long owner;
    private final AtomicInteger scenarioPeriods = new AtomicInteger();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        database = ReleaseBaselineTestDatabase.create();
        Flyway.configure()
                .dataSource(database.source())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                // 当前回测repository消费V18事件顺序列；夹具必须装配同一候选schema，资金与失败断言保持原样。
                .target("18")
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
        // 只捕获被真实 JDBC/CHECK 拒绝的合法候选表示；不替换 writer、不修改数值、不打印来源凭据。
        doAnswer(
                        invocation -> {
                            DecisionWrite write = invocation.getArgument(0);
                            try {
                                return invocation.callRealMethod();
                            } catch (RuntimeException failure) {
                                if (write.feeChargeJson() != null) {
                                    var charge = tree(write.feeChargeJson());
                                    System.out.println(
                                            "DISCOUNT_REJECTED_PAYMENT_REPRESENTATION decision="
                                                    + write.decisionId()
                                                    + " quantity="
                                                    + write.quantity().toPlainString()
                                                    + " quantityScale="
                                                    + write.quantity().scale()
                                                    + " price="
                                                    + write.executionPrice().toPlainString()
                                                    + " priceScale="
                                                    + write.executionPrice().scale()
                                                    + " frozenQuantity="
                                                    + charge.path("quantity").asText()
                                                    + " frozenQuantityScale="
                                                    + d(charge.path("quantity").asText()).scale()
                                                    + " frozenPrice="
                                                    + charge.path("price").asText()
                                                    + " frozenPriceScale="
                                                    + d(charge.path("price").asText()).scale());
                                }
                                throw failure;
                            }
                        })
                .when(decisionPosting)
                .insert(any(DecisionWrite.class), any(String.class), any(String.class));
    }

    /** 正/零/负及资金阈值均由正式冻结和canonical链消费；原币分录与报价估值不能互换。 */
    @Test
    void signedDiscountAndFallbackFlowThroughFormalResearchAndCanonicalEconomics()
            throws Exception {
        asOwner(
                () -> {
                    for (String signed : List.of("0.001", "0", "-0.001", "0.000000000001")) {
                        proveEconomics(signed, "0.1", "DISCOUNT_ASSET_USED", false);
                    }
                    proveEconomics(
                            "0.001", "0.00001", "DISCOUNT_ASSET_INSUFFICIENT_FALLBACK", false);
                    proveEconomics("0.001", "0.00024996", "DISCOUNT_ASSET_USED", true);
                    return null;
                });
    }

    private void proveEconomics(
            String signed, String fraction, String firstPaymentStatus, boolean exact)
            throws Exception {
        try (var provider = new NativeProvider(signed)) {
            Prepared prepared = prepare(provider, fraction, "1", false);
            String run = prepared.run();
            long account = prepared.account();
            var plan = prepared.plan();
            equal(d("100"), sim.facts(run).equity());
            equal(d("100"), plan.cashBudget().add(plan.assetInitialQuoteValue()));
            equal(plan.cashBudget(), funding.cashBalance(account));
            equal(plan.assetQuantity(), assetFunding.assetBalance(account, "BNB"));
            simRuns.start(run);
            sim.advance(run);
            sim.advance(run);
            var buy = sim.advance(run);
            assertEquals("ACCEPTED", buy.status());
            assertEquals("BUY", buy.side());
            JsonNode payment = tree(buy.feeChargeJson());
            assertEquals(firstPaymentStatus, payment.path("paymentStatus").asText());
            assertTrue(
                    payment.path("visibleAt")
                                    .asText()
                                    .compareTo(payment.path("executionAt").asText())
                            <= 0);
            assertEquals(
                    0,
                    d(payment.path("positiveReserve").asText())
                            .compareTo(d(payment.path("amount").asText()).max(BigDecimal.ZERO)));
            if (exact) equal(plan.assetQuantity(), d(payment.path("amount").asText()));
            var pending = sim.facts(run).discountAsset();
            equal(
                    payment.path("currency").asText().equals("BNB")
                            ? d(payment.path("positiveReserve").asText())
                            : BigDecimal.ZERO,
                    d(pending.reserved()));
            assertTrue(d(pending.available()).signum() >= 0);
            String frozen =
                    jdbc.queryForObject(
                            "SELECT fee_charge_json::text FROM strategy_sim_decisions WHERE decision_id=?",
                            String.class,
                            buy.decisionId());
            sim.advance(run);
            assertEquals(
                    frozen,
                    jdbc.queryForObject(
                            "SELECT fee_charge_json::text FROM strategy_sim_decisions WHERE decision_id=?",
                            String.class,
                            buy.decisionId()));
            assertEquals(1, matching.matchOnce(100));
            for (int i = 0; i < prepared.input().size(); i++) {
                sim.advance(run);
                matching.matchOnce(100);
            }
            var facts = sim.facts(run);
            assertEquals(2, facts.trades().size());
            var fills =
                    jdbc.queryForList(
                            "SELECT t.*,o.side,d.fee_charge_json::text AS payment FROM trades t JOIN orders o ON o.order_id=t.order_id JOIN strategy_sim_decisions d ON d.order_id=o.order_id WHERE t.account_id=? ORDER BY t.ts,t.trade_id",
                            account);
            BigDecimal cash = plan.cashBudget(),
                    base = BigDecimal.ZERO,
                    asset = plan.assetQuantity();
            for (var fill : fills) {
                boolean bought = "BUY".equals(fill.get("side"));
                var qty = (BigDecimal) fill.get("qty");
                var price = (BigDecimal) fill.get("price");
                var fee = (BigDecimal) fill.get("fee");
                String currency = (String) fill.get("fee_currency");
                var choice = tree((String) fill.get("payment"));
                assertEquals(currency, choice.path("currency").asText());
                equal(fee, d(choice.path("amount").asText()));
                BigDecimal notional = qty.multiply(price).setScale(8, RoundingMode.HALF_UP);
                if (currency.equals("BNB")) {
                    equal(
                            qty.multiply(price)
                                    .multiply(d(signed))
                                    .multiply(d("0.25"))
                                    .divide(d("600"), 8, RoundingMode.HALF_UP),
                            fee);
                    asset = asset.subtract(fee);
                    assertEquals("DISCOUNT_ASSET_USED", choice.path("paymentStatus").asText());
                    equal(
                            fee.multiply(d("600")).setScale(8, RoundingMode.HALF_UP),
                            d(choice.path("quoteValue").asText()));
                } else {
                    assertEquals(bought ? "BTC" : "USDT", currency);
                    equal(
                            (bought ? qty : qty.multiply(price))
                                    .multiply(d(signed))
                                    .setScale(8, RoundingMode.HALF_UP),
                            fee);
                    assertEquals(
                            "DISCOUNT_ASSET_INSUFFICIENT_FALLBACK",
                            choice.path("paymentStatus").asText());
                }
                cash = bought ? cash.subtract(notional) : cash.add(notional);
                base = bought ? base.add(qty) : base.subtract(qty);
                if (currency.equals("USDT")) cash = cash.subtract(fee);
                if (currency.equals("BTC")) base = base.subtract(fee);
                assertEquals(
                        1,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM risk_events WHERE scope='ORDER' AND scope_id=? AND decision='ALLOW'",
                                Integer.class,
                                fill.get("order_id")));
            }
            equal(cash, facts.cash());
            equal(base, facts.positionQuantity());
            equal(asset, assetFunding.assetBalance(account, "BNB"));
            equal(asset, d(facts.discountAsset().balance()));
            equal(BigDecimal.ZERO, d(facts.discountAsset().reserved()));
            BigDecimal equity =
                    cash.add(base.multiply(facts.markPrice()))
                            .add(asset.multiply(d("600")).setScale(8, RoundingMode.HALF_UP));
            equal(equity, facts.equity());
            equal(equity.subtract(d("100")), facts.pnl());
            equal(BigDecimal.ZERO, base);
            assertTrue(cash.signum() >= 0 && asset.signum() >= 0);
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM (SELECT currency FROM ledger_entries WHERE account_id=? GROUP BY currency HAVING sum(delta)<>0) bad",
                            Integer.class,
                            account));
            var performance = analysis.readPerformance(owner, run, null, null).orElseThrow();
            assertEquals("AVAILABLE", performance.status());
            equal(equity, performance.finalEquity());
            String frozenAfter =
                    jdbc.queryForObject(
                            "SELECT fee_charge_json::text FROM strategy_sim_decisions WHERE decision_id=?",
                            String.class,
                            buy.decisionId());
            assertEquals(frozen, frozenAfter);
            matching.matchOnce(100);
            equal(equity, sim.facts(run).equity());
            assertThrows(
                    DataIntegrityViolationException.class,
                    () ->
                            jdbc.update(
                                    "UPDATE strategy_sim_decisions SET fee_charge_json=NULL WHERE decision_id=?",
                                    buy.decisionId()));
            assertEquals(3, provider.gets.get());
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM orders WHERE trade_env='LIVE'", Integer.class));
            System.out.println(
                    "DISCOUNT_CANONICAL_ECONOMICS signed="
                            + signed
                            + " fraction="
                            + fraction
                            + " firstPayment="
                            + firstPaymentStatus
                            + " exact="
                            + exact
                            + " cash="
                            + cash
                            + " bnb="
                            + asset
                            + " equity="
                            + equity);
        } finally {
            fixture.delegate = null;
        }
    }

    /** 第一币已写后拒绝第三资产对手分录，随后并发撮合只提交一套真实经济事实。 */
    @Test
    void thirdAssetPartialPostingRollsBackAndConcurrentMatchingCommitsOnce() throws Exception {
        asOwner(
                () -> {
                    try (var provider = new NativeProvider("0.001")) {
                        Prepared prepared = prepare(provider, "0.1", "1", false);
                        long account = prepared.account();
                        String run = prepared.run();
                        simRuns.start(run);
                        sim.advance(run);
                        sim.advance(run);
                        var buy = sim.advance(run);
                        assertEquals("ACCEPTED", buy.status());
                        AtomicReference<String> fail = new AtomicReference<>(":LEDGER:FEE_2");
                        doAnswer(
                                        invocation -> {
                                            LedgerPostingEntry entry = invocation.getArgument(0);
                                            String suffix = fail.get();
                                            if (suffix != null
                                                    && entry.accountId() == account
                                                    && entry.idempotencyKey().endsWith(suffix)
                                                    && fail.compareAndSet(suffix, null))
                                                throw new IllegalStateException(
                                                        "SYNTHETIC_DISCOUNT_PARTIAL_LEDGER_FAILURE");
                                            return invocation.callRealMethod();
                                        })
                                .when(ledgerPosting)
                                .insertEntry(any(LedgerPostingEntry.class));
                        String before = accountFacts(account);
                        assertEquals(0, matching.matchOnce(100));
                        assertNull(fail.get());
                        assertEquals(before, accountFacts(account));
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
                                        account));
                        assertEquals(
                                8,
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM ledger_entries WHERE account_id=?",
                                        Integer.class,
                                        account));
                        assertEquals(
                                0,
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM (SELECT currency FROM ledger_entries WHERE account_id=? GROUP BY currency HAVING sum(delta)<>0) bad",
                                        Integer.class,
                                        account));
                    } finally {
                        fixture.delegate = null;
                    }
                    return null;
                });
    }

    /** 撤单沿用正式编排，释放正费用预留，保留原冻结选择与资金身份。 */
    @Test
    void cancellationReleasesDiscountReserveWithoutCreatingTradeOrChangingBudget()
            throws Exception {
        asOwner(
                () -> {
                    try (var provider = new NativeProvider("0.001")) {
                        Prepared prepared = prepare(provider, "0.1", "1", false);
                        String run = prepared.run();
                        long account = prepared.account();
                        simRuns.start(run);
                        sim.advance(run);
                        sim.advance(run);
                        var buy = sim.advance(run);
                        assertEquals("ACCEPTED", buy.status());
                        var reserved = sim.facts(run).discountAsset();
                        assertTrue(d(reserved.reserved()).signum() > 0);
                        String original = buy.feeChargeJson();
                        var cancelled =
                                cancellations
                                        .cancel(
                                                owner,
                                                run,
                                                buy.orderId(),
                                                "discount-synthetic-cancel")
                                        .orElseThrow();
                        assertEquals("CANCELLED", cancelled.status().name());
                        assertTrue(
                                cancellations
                                        .cancel(
                                                owner,
                                                run,
                                                buy.orderId(),
                                                "discount-synthetic-cancel-replay")
                                        .orElseThrow()
                                        .idempotentHit());
                        var after = sim.facts(run).discountAsset();
                        equal(BigDecimal.ZERO, d(after.reserved()));
                        equal(prepared.plan().assetQuantity(), d(after.available()));
                        assertEquals(
                                0,
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM trades WHERE account_id=?",
                                        Integer.class,
                                        account));
                        assertEquals(
                                tree(original),
                                tree(
                                        jdbc.queryForObject(
                                                "SELECT fee_charge_json::text FROM strategy_sim_decisions WHERE decision_id=?",
                                                String.class,
                                                buy.decisionId())));
                        equal(prepared.plan().cashBudget(), funding.cashBalance(account));
                        equal(
                                prepared.plan().assetQuantity(),
                                assetFunding.assetBalance(account, "BNB"));
                    } finally {
                        fixture.delegate = null;
                    }
                    return null;
                });
    }

    /** 不伪造订单：第二历史信号必须保留原活动执行边界，不增加资金预留或准入新订单。 */
    @Test
    void twoPendingOrdersCannotBothSpendSameDiscountAssetBudget() throws Exception {
        asOwner(
                () -> {
                    try (var provider = new NativeProvider("0.001")) {
                        Prepared prepared = prepare(provider, "0.00007", "0.25", true);
                        String run = prepared.run();
                        long account = prepared.account();
                        simRuns.start(run);
                        sim.advance(run);
                        sim.advance(run);
                        var first = sim.advance(run);
                        assertEquals("ACCEPTED", first.status());
                        BigDecimal beforeReserve = d(sim.facts(run).discountAsset().reserved());
                        String boundary;
                        try {
                            var second = sim.advance(run);
                            if ("ACCEPTED".equals(second.status()))
                                assertEquals(
                                        first.decisionId(),
                                        second.decisionId(),
                                        "new window must not be admitted while prior canonical execution is active");
                            boundary = second.reason();
                        } catch (RuntimeException denied) {
                            boundary = denied.getMessage();
                            assertNotNull(boundary);
                            assertTrue(
                                    boundary.contains("strategy_run_active")
                                            || boundary.contains("SIM_PREVIOUS_EXECUTION_PENDING"),
                                    "unexpected exception is not the active-run business boundary");
                        }
                        assertEquals(
                                1,
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM orders WHERE account_id=? AND status='ACCEPTED'",
                                        Integer.class,
                                        account));
                        JsonNode firstPayment = tree(first.feeChargeJson());
                        assertEquals(
                                "DISCOUNT_ASSET_USED", firstPayment.path("paymentStatus").asText());
                        equal(beforeReserve, d(sim.facts(run).discountAsset().reserved()));
                        assertTrue(beforeReserve.compareTo(prepared.plan().assetQuantity()) <= 0);
                        assertEquals(1, matching.matchOnce(100));
                        assertEquals(
                                1,
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM trades WHERE account_id=?",
                                        Integer.class,
                                        account));
                        equal(
                                prepared.plan()
                                        .assetQuantity()
                                        .subtract(d(firstPayment.path("amount").asText())),
                                assetFunding.assetBalance(account, "BNB"));
                        System.out.println(
                                "DISCOUNT_CANONICAL_ACTIVE_RUN_BOUNDARY reason="
                                        + boundary
                                        + " newOrders=0 additionalReserve=0");
                    } finally {
                        fixture.delegate = null;
                    }
                    return null;
                });
    }

    private Prepared prepare(
            NativeProvider provider, String fraction, String exposure, boolean twoPending) {
        var account =
                accounts.create(
                        owner,
                        new ExchangeAccountCreateCommand(
                                "BINANCE",
                                "LIVE",
                                "Synthetic discount source " + UUID.randomUUID(),
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
                        owner, account.exchangeAccountId(), credential.credentialId());
        fixture.delegate =
                new JdbcBinanceAccountFeeReadPort(
                        jdbc,
                        credentialProperties.getMasterKey(),
                        new BinanceCommissionHttpReader(
                                provider.client,
                                provider.base(),
                                BinanceCommissionHttpReader.Environment.ISOLATED_FIXTURE,
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
        // 各场景占用不同历史时窗，避免后续夹具 upsert 改写另一冻结 run 所引用的原 BTC 版本。
        long shift = scenarioPeriods.getAndIncrement() * 3600L;
        List<HistoricalBar> input =
                inputBars().stream()
                        .map(
                                bar ->
                                        new HistoricalBar(
                                                bar.exchangeCode(),
                                                bar.marketType(),
                                                bar.symbol(),
                                                bar.interval(),
                                                bar.openTime().plusSeconds(shift),
                                                bar.closeTime().plusSeconds(shift),
                                                bar.openPrice(),
                                                bar.highPrice(),
                                                bar.lowPrice(),
                                                bar.closePrice(),
                                                bar.volume(),
                                                bar.quoteVolume(),
                                                bar.tradeCount(),
                                                bar.qualityStatus(),
                                                bar.rawPayloadJson(),
                                                bar.availableAt().plusSeconds(shift)))
                        .toList();
        if (twoPending) {
            var changed = new ArrayList<>(input);
            var bar = changed.get(4);
            changed.set(
                    4,
                    new HistoricalBar(
                            bar.exchangeCode(),
                            bar.marketType(),
                            bar.symbol(),
                            bar.interval(),
                            bar.openTime(),
                            bar.closeTime(),
                            d("80"),
                            d("80"),
                            d("80"),
                            d("80"),
                            bar.volume(),
                            null,
                            null,
                            "OK",
                            bar.rawPayloadJson(),
                            bar.availableAt()));
            input = List.copyOf(changed);
        }
        for (var bar : input)
            bars.upsertBars(List.of(bar), "SYNTHETIC_DISCOUNT_STRATEGY", bar.availableAt());
        var dataset =
                datasets.createDataset(
                        new CreateMarketdataDatasetCommand(
                                "Discount strategy bars " + UUID.randomUUID(),
                                "BINANCE",
                                "SPOT",
                                "BTC-USDT",
                                "1m",
                                input.getFirst().openTime(),
                                input.getLast().closeTime(),
                                "synthetic-test"));
        List<HistoricalBar> prices = new ArrayList<>();
        for (int i = -5; i < input.size(); i++) {
            Instant open = input.getFirst().openTime().plusSeconds(i * 60L);
            prices.add(
                    new HistoricalBar(
                            "BINANCE",
                            "SPOT",
                            "BNB-USDT",
                            BarInterval.ONE_MINUTE,
                            open,
                            open.plusSeconds(59),
                            d("600"),
                            d("600"),
                            d("600"),
                            d("600"),
                            d("1"),
                            null,
                            null,
                            "OK",
                            "{\"fixture\":\"synthetic-discount-price\"}",
                            open.plusSeconds(59)));
        }
        for (var bar : prices)
            bars.upsertBars(List.of(bar), "SYNTHETIC_DISCOUNT_CONVERSION", bar.availableAt());
        var conversion =
                datasets.createDataset(
                        new CreateMarketdataDatasetCommand(
                                "Discount BNB bars " + UUID.randomUUID(),
                                "BINANCE",
                                "SPOT",
                                "BNB-USDT",
                                "1m",
                                prices.getFirst().openTime(),
                                prices.getLast().closeTime(),
                                "synthetic-test"));
        assertEquals("READY", conversion.status().name());
        String snapshot = datasets.buildDatasetSnapshot(dataset.datasetId());
        var researchAccount =
                accounts.create(
                        owner,
                        new ExchangeAccountCreateCommand(
                                "BINANCE", "SIM", "Discount research " + UUID.randomUUID(), null));
        String strategy = "discount-sim-" + UUID.randomUUID(),
                code = "discount-sma-" + UUID.randomUUID();
        Instant now = Instant.now();
        definitions.insert(
                new StrategyDefinition(
                        strategy,
                        code,
                        "Discount canonical SMA",
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
                                "{\"window\":3,\"investedExposure\":\"" + exposure + "\"}",
                                "{}",
                                "{\"executable\":\"SPOT_SMA_TARGET_V1\"}",
                                "synthetic-test"));
        var experiment =
                research.create(
                        new ResearchConfigCreateRequest(
                                strategy, "Discount research", "synthetic", "{}", "{}", snapshot));
        var spec =
                mapper.createObjectNode()
                        .put("costSource", "CURRENT_ACCOUNT_OBSERVATION")
                        .put("feeExchangeCode", "BINANCE")
                        .put("feePaymentPolicy", "DISCOUNT_ASSET_IF_FUNDED")
                        .put("feeObservationId", observation.observationId().toString())
                        .put("feeAccountId", Long.toString(account.exchangeAccountId()))
                        .put("feeTradeEnv", "LIVE")
                        .put("feeSymbol", "BTC-USDT")
                        .put("discountAssetBudgetFraction", fraction)
                        .put("discountPriceDatasetId", conversion.datasetId().toString())
                        .put("quantityStep", "0.00000001")
                        .put("priceTick", "0.00000001")
                        .put("minimumQuantity", "0.00000001")
                        .put("minimumNotional", "5")
                        .put("slippageBps", "0");
        var config =
                configs.create(
                        new BacktestConfigCreateRequest(
                                experiment.researchConfigId(),
                                "Discount backtest",
                                "synthetic",
                                input.getFirst().openTime(),
                                input.getLast().closeTime(),
                                d("100"),
                                spec.toString(),
                                "{}"));
        // 配置只保存来源引用；完整服务器来源身份在创建 run 时冻结，不能把未冻结配置宣称为冻结事实。
        assertFalse(
                tree(config.configSnapshotJson())
                        .path("executionSpec")
                        .has("discountPriceSnapshotId"));
        configs.bindDataset(config.backtestConfigId(), dataset.datasetId().toString(), snapshot);
        configs.bindStrategyVersion(config.backtestConfigId(), strategyVersion.strategyVersionId());
        var backtest = runs.create(new BacktestRunStartRequest(config.backtestConfigId()));
        var frozen = tree(backtest.configSnapshotJson()).path("executionSpec");
        assertTrue(frozen.path("discountPriceSnapshotId").asText().length() > 0);
        assertTrue(frozen.path("discountPriceSnapshotSha256").asText().matches("[a-f0-9]{64}"));
        assertEquals(
                "SUCCEEDED", execution.startRun(backtest.backtestRunId()).resultStatus().name());
        assertEquals(
                "SUCCEEDED",
                evaluations.evaluate(backtest.backtestRunId()).evaluationStatus().name());
        var publish =
                publishes.publish(
                        new BacktestPublishRequest(
                                backtest.backtestRunId(),
                                "Discount publish",
                                strategyVersion.strategyVersionId()));
        assertEquals("SUCCEEDED", publish.publishStatus().name());
        var created = sim.create(publish.publishRecordId(), d("100"), "native-owner");
        var plan =
                assetFunding
                        .findPlan(created.canonicalAccountId(), created.paperRunId())
                        .orElseThrow();
        assertEquals(
                4,
                jdbc.queryForObject(
                        "SELECT count(*) FROM ledger_entries WHERE account_id=?",
                        Integer.class,
                        created.canonicalAccountId()));
        return new Prepared(
                created.paperRunId(),
                created.canonicalAccountId(),
                backtest.backtestRunId(),
                plan,
                input);
    }

    private String accountFacts(long account) {
        return jdbc.queryForObject(
                """
            SELECT jsonb_build_object('entries',(SELECT jsonb_agg(to_jsonb(e) ORDER BY entry_id) FROM ledger_entries e WHERE account_id=?),
                'trades',(SELECT jsonb_agg(to_jsonb(t) ORDER BY trade_id) FROM trades t WHERE account_id=?),
                'positions',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM positions p WHERE account_id=?),
                'snapshots',(SELECT jsonb_agg(to_jsonb(s) ORDER BY snapshot_id) FROM account_snapshots s WHERE account_id=?),
                'orders',(SELECT jsonb_agg(to_jsonb(o) ORDER BY order_id) FROM orders o WHERE account_id=?),
                'ledgerEvents',(SELECT jsonb_agg(to_jsonb(v) ORDER BY ledger_event_id) FROM ledger_events v JOIN ledger_entries e ON e.entry_id=v.entry_id WHERE e.account_id=?),
                'events',(SELECT jsonb_agg(to_jsonb(v) ORDER BY event_id) FROM event_store v WHERE payload_json->'payload'->>'account_id'=?))::text
            """,
                String.class,
                account,
                account,
                account,
                account,
                account,
                account,
                Long.toString(account));
    }

    private record Prepared(
            String run,
            long account,
            String backtest,
            SimAssetFundingPlan plan,
            List<HistoricalBar> input) {}

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
