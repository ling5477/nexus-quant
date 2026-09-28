package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.app.marketdata.PublicMarketReplayCaptureService;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.BacktestPublishService;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.command.BacktestPublishRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;
import com.guidinglight.nexusquant.strategy.domain.SpotSmaTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** 一次性 PostgreSQL 中验证合成研究服务链与隔离 SIM 的 canonical 事实。 */
@EnabledIfSystemProperty(named = "nq.strategy-sim.pg.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@SpringBootTest(classes = NexusQuantApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = NqAppContextPostgresSmokeTest.NoOutboundInitializer.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false", "spring.sql.init.mode=never", "spring.task.scheduling.enabled=false",
        "nq.runtime.trading-components.enabled=true", "nq.strategy-sim.enabled=true",
        "nq.auth.bootstrap-admin.enabled=false", "nq.instrument.catalog-sync.enabled=false",
        "nq.okx.recovery.enabled=false", "nq.okx.ws.enabled=false", "nq.binance.ws.enabled=false",
        "nq.account.credentials.verification-mode=STRUCTURAL",
        "nq.account.credentials.master-key=strategy-sim-synthetic-master-key-123456789",
        "nq.security.issuer=nexus-quant-strategy-sim-synthetic",
        "nq.security.secret=strategy-sim-synthetic-secret-123456789",
        "nq.security.access-token-ttl=PT30M"
})
class StrategySimPostgresIntegrationTest {
    private static String schema;
    private static String independentSchema;
    private static String counterfactualSchema;
    private static String baseUrl;
    private static String databaseUser;
    private static String databasePassword;
    private static DriverManagerDataSource admin;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        baseUrl = System.getProperty("nq.strategy-sim.pg.url", "");
        if (!baseUrl.startsWith("jdbc:postgresql://127.0.0.1:"))
            throw new IllegalArgumentException("disposable loopback PostgreSQL URL required");
        databaseUser = System.getProperty("nq.strategy-sim.pg.user", "postgres");
        databasePassword = System.getProperty("nq.strategy-sim.pg.password", "disposable");
        if (databaseUser.isBlank() || databasePassword.isBlank())
            throw new IllegalArgumentException("disposable PostgreSQL credentials required");
        schema = "strategy_sim_sim_" + UUID.randomUUID().toString().replace("-", "");
        admin = new DriverManagerDataSource(baseUrl, databaseUser, databasePassword);
        JdbcTemplate jdbc = new JdbcTemplate(admin);
        jdbc.execute("CREATE SCHEMA " + schema);
        Flyway flyway = Flyway.configure().dataSource(admin).schemas(schema)
                .locations("classpath:db/migration").load();
        flyway.migrate();
        flyway.validate();
        registry.add("spring.datasource.url", () -> baseUrl + "?currentSchema=" + schema);
        registry.add("spring.datasource.username", () -> databaseUser);
        registry.add("spring.datasource.password", () -> databasePassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll
    static void cleanup() {
        if (admin != null && schema != null) {
            new JdbcTemplate(admin).execute("DROP SCHEMA " + schema + " CASCADE");
        }
        if (admin != null && independentSchema != null) {
            new JdbcTemplate(admin).execute("DROP SCHEMA " + independentSchema + " CASCADE");
        }
        if (admin != null && counterfactualSchema != null) {
            new JdbcTemplate(admin).execute("DROP SCHEMA " + counterfactualSchema + " CASCADE");
        }
        ExchangeNoOutboundGuard.restoreDefault();
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private StrategySimRunService sim;
    @Autowired private PaperTradingRunService runs;
    @Autowired private PaperMatchingService matching;
    @Autowired private MarketdataBarRepository marketdataBars;
    @Autowired private MarketdataDatasetService datasets;
    @Autowired private StrategyDefinitionRepository definitions;
    @Autowired private StrategyVersionService versions;
    @Autowired private ResearchConfigService researchConfigs;
    @Autowired private BacktestConfigService backtestConfigs;
    @Autowired private BacktestRunService backtestRuns;
    @Autowired private BacktestExecutionService backtestExecution;
    @Autowired private BacktestEvaluationService evaluations;
    @Autowired private BacktestPublishService publishing;

    @Test
    void syntheticBudgetCreatesOneCanonicalFillAndLedgerWithoutPrivateProvider() {
        Fixture fixture = seed();
        // 此状态只写入本类随机 schema，正式全局 kill switch 仍保持 ENGAGED。
        jdbc.update("""
                UPDATE kill_switch_states SET status='DISENGAGED', version=version+1,
                    reason_code='SYNTHETIC_TEST_ONLY', source='STRATEGY_SIM_TEST',
                    updated_at=now(), updated_by='test', trace_id='strategy-sim-test'
                WHERE scope='GLOBAL_TRADING'
                """);
        var created = sim.create(fixture.publishId(), new BigDecimal("100.00000000"), "synthetic-test");
        assertEquals(fixture.versionId(), created.strategyVersionId());
        assertEquals(fixture.digest(), created.barContentSha256());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM account_snapshots s JOIN paper_trading_runs r
                  ON r.canonical_account_id=s.account_id
                WHERE r.paper_run_id=? AND s.trade_env='SIM'
                  AND s.balance_basis='LEDGER_CASH_PROJECTION'
                  AND s.balance_scope='NQ_MANAGED_ACCOUNT' AND s.recorded_at IS NOT NULL
                """, Integer.class, created.paperRunId()));
        runs.start(created.paperRunId());
        assertEquals("NO_SIGNAL", sim.advance(created.paperRunId()).status());
        assertEquals("NO_SIGNAL", sim.advance(created.paperRunId()).status());
        var accepted = sim.advance(created.paperRunId());
        assertEquals("ACCEPTED", accepted.status());
        assertNotNull(accepted.orderId());
        assertTrue(matching.matchOnce(100) >= 1);
        assertEquals(0, matching.matchOnce(100));
        var facts = sim.facts(created.paperRunId());
        assertEquals(1, facts.orders().size());
        assertEquals(1, facts.trades().size());
        assertTrue(facts.positionQuantity().signum() > 0);
        assertTrue(facts.cash().signum() >= 0);
        assertEquals(0, new BigDecimal("103").compareTo(facts.markPrice()));
        assertEquals(6, facts.ledgerEntries().size());
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM account_snapshots s JOIN paper_trading_runs r
                  ON r.canonical_account_id=s.account_id
                WHERE r.paper_run_id=? AND (s.trade_env<>'SIM' OR s.balance_scope<>'NQ_MANAGED_ACCOUNT'
                    OR s.balance_basis IS NULL OR s.recorded_at IS NULL)
                """, Integer.class, created.paperRunId()));
        assertEquals("FILLED", facts.orders().getFirst().get("status"));
        var trade = facts.trades().getFirst();
        BigDecimal tradedPrice = new BigDecimal(trade.get("price").toString());
        BigDecimal tradedQuantity = new BigDecimal(trade.get("qty").toString());
        BigDecimal fee = new BigDecimal(trade.get("fee").toString());
        assertEquals(0, tradedQuantity.compareTo(facts.positionQuantity()));
        assertEquals(0, new BigDecimal("100").subtract(tradedPrice.multiply(tradedQuantity))
                .subtract(fee).compareTo(facts.cash()));
        assertEquals(0, facts.cash().add(facts.positionQuantity().multiply(facts.markPrice()))
                .compareTo(facts.equity()));
        assertEquals(0, facts.equity().subtract(facts.initialBudget()).compareTo(facts.pnl()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM strategy_sim_decisions WHERE paper_run_id=? AND status='ACCEPTED'
                """, Integer.class, created.paperRunId()));
    }

    @Test
    void budgetAndPendingExposureRemainBoundedAndStopPreservesAcceptedFacts() {
        Fixture fixture = seed();
        jdbc.update("""
                UPDATE kill_switch_states SET status='DISENGAGED', version=version+1,
                    reason_code='SYNTHETIC_TEST_ONLY', source='STRATEGY_SIM_TEST',
                    updated_at=now(), updated_by='test', trace_id='sim-test'
                WHERE scope='GLOBAL_TRADING'
                """);
        for (String budget : List.of("10", "100", "1000")) {
            var created = sim.create(fixture.publishId(), new BigDecimal(budget), "synthetic-test");
            runs.start(created.paperRunId());
            assertEquals("NO_SIGNAL", sim.advance(created.paperRunId()).status());
            assertEquals("NO_SIGNAL", sim.advance(created.paperRunId()).status());
            var first = sim.advance(created.paperRunId());
            assertTrue(List.of("ACCEPTED", "NOT_TRADABLE").contains(first.status()));
            if ("ACCEPTED".equals(first.status())) {
                assertTrue(first.quantity().signum() > 0);
                var pending = sim.advance(created.paperRunId());
                assertEquals("NOT_TRADABLE", pending.status());
                assertEquals(1, sim.facts(created.paperRunId()).orders().size());
            } else {
                assertNotNull(first.reason());
            }
            var finalBar = sim.advance(created.paperRunId());
            assertEquals("NOT_TRADABLE", finalBar.status());
            assertEquals("NO_LATER_TRADABLE_EVENT", finalBar.reason());
            assertEquals(null, finalBar.executionOpenTime());
            runs.stop(created.paperRunId());
            assertThrows(IllegalStateException.class, () -> sim.advance(created.paperRunId()));
            matching.matchOnce(100);
            assertEquals("STOPPED", runs.getById(created.paperRunId()).status().name());
            if ("ACCEPTED".equals(first.status())) {
                assertEquals(1, sim.facts(created.paperRunId()).trades().size());
            }
        }
    }

    @Test
    void pendingBuyReservesFrozenFillPriceWhenNextBarFalls() {
        Fixture fixture = seed(List.of(bar(0, "100"), bar(1, "101"), bar(2, "102"),
                bar(3, "103"), bar(4, "10")));
        jdbc.update("""
                UPDATE kill_switch_states SET status='DISENGAGED', version=version+1,
                    reason_code='SYNTHETIC_TEST_ONLY', source='STRATEGY_SIM_TEST',
                    updated_at=now(), updated_by='test', trace_id='sim-reserve-test'
                WHERE scope='GLOBAL_TRADING'
                """);
        var created = sim.create(fixture.publishId(), new BigDecimal("100"), "synthetic-test");
        runs.start(created.paperRunId());
        sim.advance(created.paperRunId());
        sim.advance(created.paperRunId());
        var first = sim.advance(created.paperRunId());
        assertEquals("ACCEPTED", first.status());
        var second = sim.advance(created.paperRunId());
        assertEquals("NOT_TRADABLE", second.status());
        assertEquals("MIN_NOTIONAL", second.reason());
        assertEquals(1, sim.facts(created.paperRunId()).orders().size());
        assertEquals(1, matching.matchOnce(100));
        assertTrue(sim.facts(created.paperRunId()).cash().signum() >= 0);
    }

    @Test
    void rejectsNonSpotFrozenBarsBeforeFunding() {
        List<HistoricalBar> swapBars = List.of(bar(0, "100"), bar(1, "101"), bar(2, "102"),
                bar(3, "103"), bar(4, "104")).stream().map(bar -> new HistoricalBar(
                bar.exchangeCode(), "SWAP", bar.symbol(), bar.interval(), bar.openTime(),
                bar.closeTime(), bar.openPrice(), bar.highPrice(), bar.lowPrice(), bar.closePrice(),
                bar.volume(), bar.quoteVolume(), bar.tradeCount(), bar.qualityStatus(),
                bar.rawPayloadJson(), bar.availableAt())).toList();
        Fixture fixture = seed(swapBars);
        int accountsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE venue='PAPER'",
                Integer.class);
        assertEquals("SIM_SCOPE_OR_VERSION_INVALID", assertThrows(IllegalStateException.class,
                () -> sim.create(fixture.publishId(), new BigDecimal("100"), "synthetic-test")).getMessage());
        assertEquals(accountsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE venue='PAPER'",
                Integer.class));
    }

    @Test
    void factsKeepLatestExecutionMarkWhenLaterSignalHasNoTradableBar() {
        Fixture fixture = seed(List.of(bar(0, "100"), bar(1, "101"),
                availableAt(bar(2, "102"), "2026-09-25T00:03:00Z"),
                availableAt(bar(3, "103"), "2026-09-25T00:04:00Z"), bar(4, "104")));
        jdbc.update("""
                UPDATE kill_switch_states SET status='DISENGAGED', version=version+1,
                    reason_code='SYNTHETIC_TEST_ONLY', source='STRATEGY_SIM_TEST',
                    updated_at=now(), updated_by='test', trace_id='sim-mark-test'
                WHERE scope='GLOBAL_TRADING'
                """);
        var created = sim.create(fixture.publishId(), new BigDecimal("100"), "synthetic-test");
        runs.start(created.paperRunId());
        sim.advance(created.paperRunId());
        sim.advance(created.paperRunId());
        assertEquals("ACCEPTED", sim.advance(created.paperRunId()).status());
        assertEquals("NO_LATER_TRADABLE_EVENT", sim.advance(created.paperRunId()).reason());
        assertEquals(0, new BigDecimal("104").compareTo(sim.facts(created.paperRunId()).markPrice()));
    }

    @Test
    void interruptedDecisionLinkageRecoversWithoutSecondCanonicalOrder() {
        Fixture fixture = seed();
        jdbc.update("""
                UPDATE kill_switch_states SET status='DISENGAGED', version=version+1,
                    reason_code='SYNTHETIC_TEST_ONLY', source='STRATEGY_SIM_TEST',
                    updated_at=now(), updated_by='test', trace_id='sim-test'
                WHERE scope='GLOBAL_TRADING'
                """);
        var created = sim.create(fixture.publishId(), new BigDecimal("100"), "synthetic-test");
        runs.start(created.paperRunId());
        sim.advance(created.paperRunId());
        sim.advance(created.paperRunId());
        var accepted = sim.advance(created.paperRunId());
        assertEquals("ACCEPTED", accepted.status());
        String originalOrderId = accepted.orderId();
        // 仅在随机测试 schema 内模拟 canonical 订单提交后、决策关联前的进程中断。
        jdbc.execute("ALTER TABLE strategy_sim_decisions DISABLE TRIGGER trg_strategy_sim_preserve_decision");
        try {
            jdbc.update("""
                    UPDATE strategy_sim_decisions SET status='NOT_TRADABLE',reason='DECIDING',
                        strategy_run_id=NULL,order_id=NULL WHERE decision_id=?
                    """, accepted.decisionId());
        } finally {
            jdbc.execute("ALTER TABLE strategy_sim_decisions ENABLE TRIGGER trg_strategy_sim_preserve_decision");
        }
        var recovered = sim.advance(created.paperRunId());
        assertEquals("ACCEPTED", recovered.status());
        assertEquals(originalOrderId, recovered.orderId());
        assertEquals(1, sim.facts(created.paperRunId()).orders().size());
        matching.matchOnce(100);
        assertEquals(1, sim.facts(created.paperRunId()).trades().size());
    }

    @Test
    void concurrentDecisionsCompeteForOneAccountBudget() throws Exception {
        Fixture fixture = seed();
        jdbc.update("""
                UPDATE kill_switch_states SET status='DISENGAGED', version=version+1,
                    reason_code='SYNTHETIC_TEST_ONLY', source='STRATEGY_SIM_TEST',
                    updated_at=now(), updated_by='test', trace_id='sim-test'
                WHERE scope='GLOBAL_TRADING'
                """);
        var created = sim.create(fixture.publishId(), new BigDecimal("100"), "synthetic-test");
        runs.start(created.paperRunId());
        sim.advance(created.paperRunId());
        sim.advance(created.paperRunId());
        CountDownLatch release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                release.await();
                return sim.advance(created.paperRunId());
            });
            var second = workers.submit(() -> {
                release.await();
                return sim.advance(created.paperRunId());
            });
            release.countDown();
            var results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(d -> "ACCEPTED".equals(d.status())).count());
            assertEquals(1, results.stream().filter(d -> "NOT_TRADABLE".equals(d.status())).count());
        }
        assertEquals(1, sim.facts(created.paperRunId()).orders().size());
        matching.matchOnce(100);
        assertTrue(sim.facts(created.paperRunId()).cash().signum() >= 0);
    }

    @Test
    void distinctJvmProcessesRecoverSubmittedOrderWithoutDuplicateFacts() throws Exception {
        Fixture fixture = seed();
        jdbc.update("""
                UPDATE kill_switch_states SET status='DISENGAGED', version=version+1,
                    reason_code='SYNTHETIC_TEST_ONLY', source='STRATEGY_SIM_TEST',
                    updated_at=now(), updated_by='test', trace_id='sim-test'
                WHERE scope='GLOBAL_TRADING'
                """);
        var created = sim.create(fixture.publishId(), new BigDecimal("100"), "synthetic-test");
        runs.start(created.paperRunId());
        sim.advance(created.paperRunId());
        sim.advance(created.paperRunId());
        String firstOrder = restartProcess("A", created.paperRunId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE account_id=?",
                Integer.class, created.canonicalAccountId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM trades WHERE account_id=?",
                Integer.class, created.canonicalAccountId()));
        String recoveredOrder = restartProcess("B", created.paperRunId());
        assertEquals(firstOrder, recoveredOrder);
        assertEquals(1, sim.facts(created.paperRunId()).orders().size());
        assertEquals(1, sim.facts(created.paperRunId()).trades().size());
        assertEquals(6, sim.facts(created.paperRunId()).ledgerEntries().size());
    }

    @Test
    void changedBacktestBarsCannotReplaceAnAlreadyBoundSimInput() {
        Fixture fixture = seed();
        var created = sim.create(fixture.publishId(), new BigDecimal("100"), "synthetic-test");
        runs.start(created.paperRunId());
        String backtestId = jdbc.queryForObject("""
                SELECT backtest_run_id FROM backtest_publish_records WHERE publish_record_id=?
                """, String.class, fixture.publishId());
        ObjectNode changed = (ObjectNode) read(jdbc.queryForObject("""
                SELECT summary_json::text FROM backtest_runs WHERE backtest_run_id=?
                """, String.class, backtestId));
        var changedBars = List.of(bar(0, "100"), bar(1, "101"), bar(2, "102"),
                bar(3, "103"), bar(4, "105"));
        var identity = SpotBarIdentity.capture(changedBars, mapper);
        changed.set("consumedBars", read(identity.canonicalJson()));
        changed.put("barContentSha256", identity.sha256());
        jdbc.update("UPDATE backtest_runs SET summary_json=?::jsonb WHERE backtest_run_id=?",
                changed.toString(), backtestId);
        assertEquals("SIM_INPUT_IDENTITY_DRIFT", assertThrows(IllegalStateException.class,
                () -> sim.advance(created.paperRunId())).getMessage());
        assertEquals("SIM_INPUT_IDENTITY_DRIFT", assertThrows(IllegalStateException.class,
                () -> sim.facts(created.paperRunId())).getMessage());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE account_id=?",
                Integer.class, created.canonicalAccountId()));
    }

    @Test
    void changedDatasetIdentityCannotReplaceAnAlreadyBoundSimRun() {
        Fixture fixture = seed();
        var created = sim.create(fixture.publishId(), new BigDecimal("100"), "synthetic-test");
        runs.start(created.paperRunId());
        jdbc.update("""
                UPDATE backtest_runs SET dataset_snapshot_json='{"datasetId":"other"}'::jsonb
                WHERE backtest_run_id=(SELECT backtest_run_id FROM backtest_publish_records
                                       WHERE publish_record_id=?)
                """, fixture.publishId());
        assertEquals("SIM_DATASET_IDENTITY_DRIFT", assertThrows(IllegalStateException.class,
                () -> sim.advance(created.paperRunId())).getMessage());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE account_id=?",
                Integer.class, created.canonicalAccountId()));
    }

    private String restartProcess(String mode, String paperRunId) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java")
                .toString();
        String classpath = System.getProperty("surefire.test.class.path",
                System.getProperty("java.class.path"));
        Path log = Files.createTempFile("strategy-sim-restart-", ".log");
        try {
            ProcessBuilder builder = new ProcessBuilder(executable, "-cp", classpath,
                    StrategySimRestartProbeMain.class.getName(), mode, baseUrl, schema, paperRunId);
            builder.environment().put("SPRING_DATASOURCE_URL", baseUrl + "?currentSchema=" + schema);
            builder.environment().put("SPRING_DATASOURCE_USERNAME", databaseUser);
            builder.environment().put("SPRING_DATASOURCE_PASSWORD", databasePassword);
            builder.redirectErrorStream(true).redirectOutput(log.toFile());
            Process process = builder.start();
            try {
                if (!process.waitFor(90, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new AssertionError("strategy SIM restart process timed out");
                }
                String output = Files.readString(log);
                assertEquals(0, process.exitValue(), () -> "restart process failed: " + output);
                String marker = "STRATEGY_SIM_RESTART_" + mode + " ";
                return output.lines().filter(line -> line.startsWith(marker))
                        .map(line -> line.substring(marker.length())).findFirst()
                        .orElseThrow(() -> new AssertionError("restart marker missing: " + output));
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
        } finally {
            Files.deleteIfExists(log);
        }
    }

    @Test
    void capturedPublicInputDrivesFormalBacktestAndCanonicalSim() throws Exception {
        byte[] raw;
        try (var input = getClass().getResourceAsStream(
                "/public-market-replay/okx-btc-usdt-1h-20260920-20260923.json")) {
            raw = input.readAllBytes();
        }
        byte[] rawRule;
        try (var input = getClass().getResourceAsStream(
                "/public-market-replay/okx-btc-usdt-public-instrument-20260925.json")) {
            rawRule = input.readAllBytes();
        }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v5/market/history-candles", exchange -> {
            exchange.sendResponseHeaders(200, raw.length);
            try (var output = exchange.getResponseBody()) { output.write(raw); }
        });
        server.createContext("/api/v5/public/instruments", exchange -> {
            exchange.sendResponseHeaders(200, rawRule.length);
            try (var output = exchange.getResponseBody()) { output.write(rawRule); }
        });
        server.start();
        PublicMarketReplayCaptureService.CaptureView capture;
        try {
            var service = new PublicMarketReplayCaptureService(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), mapper, jdbc,
                    new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())),
                    Clock.systemUTC());
            capture = service.capture(Instant.parse("2026-09-20T00:00:00Z"),
                    Instant.parse("2026-09-23T00:00:00Z"), "captured-public-replay-test");
        } finally {
            server.stop(0);
        }
        String snapshot = datasets.buildDatasetSnapshot(capture.datasetId());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long account = jdbc.queryForObject("""
                INSERT INTO accounts(account_code,venue,status)
                VALUES (?,'OKX','ACTIVE') RETURNING account_id
                """, Long.class, "PUBLIC-REPLAY-SOURCE-" + suffix);
        String strategyId = "str-public-" + suffix;
        String strategyCode = "public-sma-" + suffix;
        Instant now = Instant.now();
        definitions.insert(new StrategyDefinition(strategyId, strategyCode, "Public capture SMA",
                SpotSmaTargetStrategy.EXECUTABLE, "OKX", account, "SIM", true,
                "{}", 1, now, now));
        var version = versions.create(new StrategyVersionCreateRequest(strategyCode, "v1", "ACTIVE",
                "{\"window\":3,\"investedExposure\":\"1\"}", "{}",
                "{\"executable\":\"SPOT_SMA_TARGET_V1\"}", "captured-public-replay-test"));
        var research = researchConfigs.create(new ResearchConfigCreateRequest(strategyId,
                "Public capture SMA research", "captured-public-replay", "{}", "{}", snapshot));
        ObjectNode spec = mapper.createObjectNode();
        spec.put("quantityStep", "0.00000001");
        spec.put("priceTick", "0.1");
        spec.put("minimumQuantity", "0.00001");
        spec.put("minimumNotional", "5");
        spec.put("minimumNotionalSource", "EXPERIMENT_ASSUMPTION");
        spec.put("feeRate", "0.001");
        spec.put("feeAssumptionVersion", "PUBLIC_SIM_FEE_V1");
        spec.put("slippageBps", "10");
        spec.put("slippageAssumptionVersion", "PUBLIC_SIM_SLIPPAGE_V1");
        spec.put("costSource", "EXPERIMENT_ASSUMPTION");
        spec.put("ruleSha256", capture.ruleSha256());
        spec.put("rulePolicy", "CURRENTLY_OBSERVED_PUBLIC_RULES");
        var config = backtestConfigs.create(new BacktestConfigCreateRequest(
                research.researchConfigId(), "Public capture SMA backtest", "captured-public-replay",
                capture.start(), capture.end().minusMillis(1), new BigDecimal("100"),
                spec.toString(), "{}"));
        backtestConfigs.bindDataset(config.backtestConfigId(), capture.datasetId().toString(), snapshot);
        backtestConfigs.bindStrategyVersion(config.backtestConfigId(), version.strategyVersionId());
        var backtest = backtestRuns.create(new BacktestRunStartRequest(config.backtestConfigId()));
        assertEquals("SUCCEEDED", backtestExecution.startRun(backtest.backtestRunId()).resultStatus().name());
        assertEquals("SUCCEEDED", evaluations.evaluate(backtest.backtestRunId()).evaluationStatus().name());
        var publish = publishing.publish(new BacktestPublishRequest(backtest.backtestRunId(),
                "Public capture SMA publish", version.strategyVersionId()));
        assertEquals("SUCCEEDED", publish.publishStatus().name());
        independentSchema = cloneFrozenPublicReplayInput();
        counterfactualSchema = cloneFrozenPublicReplayInput();
        // 隔离随机 schema 的 SIM 风控开关只为本测试短暂打开，外部运行状态不受影响。
        jdbc.update("""
                UPDATE kill_switch_states SET status='DISENGAGED', version=version+1,
                    reason_code='PUBLIC_REPLAY_TEST_ONLY', source='STRATEGY_SIM_TEST',
                    updated_at=now(), updated_by='test', trace_id='public-replay-test'
                WHERE scope='GLOBAL_TRADING'
                """);
        var created = sim.create(publish.publishRecordId(), new BigDecimal("100"),
                "captured-public-replay-test");
        assertEquals(version.strategyVersionId(), created.strategyVersionId());
        assertEquals(capture.consumedSha256(), created.barContentSha256());
        runs.start(created.paperRunId());
        boolean accepted = false;
        StrategySimRunService.FactsView firstFacts = null;
        com.guidinglight.nexusquant.scheduler.paper.StrategySimDecisionRepository.DecisionView firstDecision = null;
        List<String> trajectoryA = new ArrayList<>();
        for (int i = 0; i < capture.barCount(); i++) {
            var decision = sim.advance(created.paperRunId());
            if (i < 2) {
                assertEquals("NO_SIGNAL", decision.status());
                assertEquals("INSUFFICIENT_HISTORY", decision.reason());
                assertEquals(0, sim.facts(created.paperRunId()).orders().size());
            }
            if ("ACCEPTED".equals(decision.status())) {
                if (!accepted) {
                    assertEquals("SIM_PREVIOUS_EXECUTION_PENDING", assertThrows(IllegalStateException.class,
                            () -> sim.advance(created.paperRunId())).getMessage());
                    assertEquals(i + 1, sim.decisions(created.paperRunId()).size());
                }
                assertEquals(1, matching.matchOnce(100));
                accepted = true;
                if (firstFacts == null) {
                    firstFacts = sim.facts(created.paperRunId());
                    firstDecision = decision;
                }
            }
            trajectoryA.add(trajectoryRow(i, decision, sim.facts(created.paperRunId())));
        }
        assertTrue(accepted);
        assertEquals(72, sim.decisions(created.paperRunId()).size());
        assertPublicBarAccounting(sim, jdbc, created.paperRunId(), capture.barCount());
        var ordersBeforeRepeat = orderSemanticRows(jdbc, created.paperRunId());
        var tradesBeforeRepeat = tradeSemanticRows(jdbc, created.paperRunId());
        var ledgerBeforeRepeat = ledgerSemanticRows(jdbc, created.paperRunId());
        String lastA = sim.decisions(created.paperRunId()).getFirst().decisionId();
        assertEquals(lastA, sim.advance(created.paperRunId()).decisionId());
        assertEquals(0, matching.matchOnce(100));
        assertEquals(ordersBeforeRepeat, orderSemanticRows(jdbc, created.paperRunId()));
        assertEquals(tradesBeforeRepeat, tradeSemanticRows(jdbc, created.paperRunId()));
        assertEquals(ledgerBeforeRepeat, ledgerSemanticRows(jdbc, created.paperRunId()));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM strategy_runs r JOIN strategy_sim_decisions d
                  ON d.strategy_run_id=r.strategy_run_id
                WHERE d.paper_run_id=? AND r.status NOT IN ('SUCCEEDED','FAILED')
                """, Integer.class, created.paperRunId()));
        var facts = sim.facts(created.paperRunId());
        assertEquals("SIM", jdbc.queryForObject(
                "SELECT trade_env FROM paper_trading_runs WHERE paper_run_id=?",
                String.class, created.paperRunId()));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE account_id=? AND trade_env='LIVE'",
                Long.class, created.canonicalAccountId()));
        assertTrue(facts.orders().size() >= 1);
        assertTrue(facts.trades().size() >= 1);
        assertTrue(firstFacts.positionQuantity().signum() > 0);
        assertTrue(facts.positionQuantity().signum() >= 0);
        assertTrue(facts.cash().signum() >= 0);
        assertTrue(facts.ledgerEntries().size() >= 1);
        assertEquals(0, facts.equity().subtract(facts.initialBudget()).compareTo(facts.pnl()));
        var backtestFirstTrade = jdbc.queryForMap("""
                SELECT side,quantity,trade_price,fee_amount,slippage_amount,traded_at
                FROM sim_trades WHERE backtest_run_id=? ORDER BY traded_at,sim_trade_id LIMIT 1
                """, backtest.backtestRunId());
        var simFirstTrade = firstFacts.trades().getFirst();
        var simFirstOrder = firstFacts.orders().getFirst();
        assertEquals(backtestFirstTrade.get("side"), simFirstOrder.get("side"));
        assertEquals(0, ((BigDecimal) backtestFirstTrade.get("quantity"))
                .compareTo(new BigDecimal(simFirstTrade.get("qty").toString())));
        assertEquals(0, ((BigDecimal) backtestFirstTrade.get("trade_price"))
                .compareTo(new BigDecimal(simFirstTrade.get("price").toString())));
        BigDecimal backtestFee = (BigDecimal) backtestFirstTrade.get("fee_amount");
        BigDecimal simFee = new BigDecimal(simFirstTrade.get("fee").toString());
        // canonical ledger 以 8 位费用精度入账；回测保留 18 位，差额只能是该舍入边界。
        assertTrue(backtestFee.subtract(simFee).abs().compareTo(new BigDecimal("0.00000001")) <= 0);
        assertTrue(firstDecision.executionOpenTime().isAfter(firstDecision.signalAvailableAt()));
        assertEquals(((Timestamp) backtestFirstTrade.get("traded_at")).toInstant(),
                firstDecision.executionOpenTime());
        var backtestAtFirstExecution = jdbc.queryForMap("""
                SELECT cash_balance,position_market_value,net_pnl
                FROM sim_pnl_snapshots WHERE backtest_run_id=? AND snapshot_time=?
                """, backtest.backtestRunId(),
                Timestamp.from(firstDecision.executionOpenTime().plusSeconds(3600)));
        BigDecimal backtestPosition = (BigDecimal) backtestFirstTrade.get("quantity");
        assertEquals(0, backtestPosition.compareTo(firstFacts.positionQuantity()));
        BigDecimal backtestCash = (BigDecimal) backtestAtFirstExecution.get("cash_balance");
        BigDecimal backtestPnl = (BigDecimal) backtestAtFirstExecution.get("net_pnl");
        assertTrue(backtestCash.subtract(firstFacts.cash()).abs().compareTo(
                new BigDecimal("0.00000001").multiply(BigDecimal.valueOf(firstFacts.trades().size()))) <= 0);
        // 回测按首笔执行 bar 收盘标记，增量 SIM 按执行事件开盘标记；差额由标记价格和账本舍入解释。
        BigDecimal explainedPnlDifference = backtestCash.subtract(firstFacts.cash())
                .add((BigDecimal) backtestAtFirstExecution.get("position_market_value"))
                .subtract(firstFacts.positionQuantity().multiply(firstFacts.markPrice()));
        assertTrue(backtestPnl.subtract(firstFacts.pnl()).subtract(explainedPnlDifference).abs()
                .compareTo(new BigDecimal("0.00000001")) <= 0);

        var replayBacktest = backtestRuns.create(new BacktestRunStartRequest(config.backtestConfigId()));
        assertEquals("SUCCEEDED", backtestExecution.startRun(replayBacktest.backtestRunId())
                .resultStatus().name());
        assertEquals("SUCCEEDED", evaluations.evaluate(replayBacktest.backtestRunId())
                .evaluationStatus().name());
        var replayPublish = publishing.publish(new BacktestPublishRequest(replayBacktest.backtestRunId(),
                "Public capture SMA replay", version.strategyVersionId()));
        assertEquals("SUCCEEDED", replayPublish.publishStatus().name());
        var replay = runIndependentReplay(publish.publishRecordId(), capture.barCount(), independentSchema);
        var replayCreated = replay.created();
        var replayFacts = replay.facts();
        assertEquals(created.strategyVersionId(), replayCreated.strategyVersionId());
        assertEquals(created.barContentSha256(), replayCreated.barContentSha256());
        var trajectoryB = replay.trajectory();
        var decisionsA = stablePublicDecisions(created.paperRunId());
        var decisionsB = replay.decisions();
        for (int i = 0; i < capture.barCount(); i++) {
            assertEquals(decisionsA.get(i), decisionsB.get(i), "decision row " + i);
            assertEquals(trajectoryA.get(i), trajectoryB.get(i), "trajectory row " + i);
        }
        var canonicalA = canonicalSemanticRows(created.paperRunId());
        var canonicalB = replay.canonical();
        assertEquals(canonicalA.size(), canonicalB.size());
        for (int i = 0; i < canonicalA.size(); i++) {
            assertEquals(canonicalA.get(i), canonicalB.get(i), "canonical row " + i);
        }
        var ledgerA = ledgerSemanticRows(created.paperRunId());
        var ledgerB = replay.ledger();
        assertEquals(ledgerA, ledgerB);
        var orderA = orderSemanticRows(jdbc, created.paperRunId());
        var tradeA = tradeSemanticRows(jdbc, created.paperRunId());
        assertEquals(orderA, replay.orders());
        assertEquals(tradeA, replay.trades());
        assertTradeAccounting(created.paperRunId(), facts.trades().size(), facts.ledgerEntries().size());
        assertEquals(facts.orders().size(), replayFacts.orders().size());
        assertEquals(facts.trades().size(), replayFacts.trades().size());
        assertEquals(0, facts.cash().compareTo(replayFacts.cash()));
        assertEquals(0, facts.positionQuantity().compareTo(replayFacts.positionQuantity()));
        assertEquals(0, facts.pnl().compareTo(replayFacts.pnl()));
        assertEquals(jdbc.queryForList("""
                SELECT side,quantity,trade_price,fee_amount,slippage_amount,traded_at
                FROM sim_trades WHERE backtest_run_id=? ORDER BY traded_at,side,quantity
                """, backtest.backtestRunId()), jdbc.queryForList("""
                SELECT side,quantity,trade_price,fee_amount,slippage_amount,traded_at
                FROM sim_trades WHERE backtest_run_id=? ORDER BY traded_at,side,quantity
                """, replayBacktest.backtestRunId()));
        System.out.println("PUBLIC_REPLAY_CHAIN dataset=" + capture.datasetId()
                + " version=" + version.strategyVersionId() + " backtest=" + backtest.backtestRunId()
                + " sim=" + created.paperRunId() + " replayBacktest=" + replayBacktest.backtestRunId()
                + " replaySim=" + replayCreated.paperRunId() + " consumed=" + capture.consumedSha256()
                + " orders=" + facts.orders().size() + " trades=" + facts.trades().size()
                + " ledger=" + facts.ledgerEntries().size() + " pnl=" + facts.pnl()
                + " backtestCash=" + backtestCash + " simCash=" + facts.cash()
                + " backtestPosition=" + backtestPosition + " simPosition=" + facts.positionQuantity()
                + " backtestPnl=" + backtestPnl + " simPnl=" + facts.pnl()
                + " pnlDifferenceOwner=MARK_TIME_AND_LEDGER_ROUNDING"
                + " firstSignal=" + firstDecision.signalAvailableAt()
                + " firstExecution=" + firstDecision.executionOpenTime()
                + " firstSide=" + simFirstOrder.get("side")
                + " firstQuantity=" + simFirstTrade.get("qty")
                + " firstPrice=" + simFirstTrade.get("price")
                + " backtestFee=" + backtestFee + " simFee=" + simFee
                + " feeDifferenceOwner=CANONICAL_LEDGER_8DP_ROUNDING"
                + " backtestSlippage=" + backtestFirstTrade.get("slippage_amount"));
        System.out.println("PUBLIC_FULL_WINDOW decision=" + digest(stablePublicDecisions(created.paperRunId()))
                + " trajectory=" + digest(trajectoryA)
                + " order=" + digest(orderA) + " trade=" + digest(tradeA)
                + " ledger=" + digest(ledgerA)
                + " bars=" + trajectoryA.size() + " orders=" + facts.orders().size()
                + " trades=" + facts.trades().size() + " ledger=" + facts.ledgerEntries().size());
        String frozenSummary = jdbc.queryForObject(
                        "SELECT summary_json::text FROM backtest_runs WHERE backtest_run_id=?",
                        String.class, backtest.backtestRunId());
        System.out.println("PUBLIC_REPLAY_IDENTITIES strategyChecksum="
                + sim.decisions(created.paperRunId()).getFirst().strategyChecksum()
                + " costAndRule=" + read(frozenSummary).path("costAndRuleSha256").asText()
                + " publicRule=" + capture.ruleSha256()
                + " visibility=" + read(snapshot).path("capture").path("replayVisibilityVersion").asText());
        var changedBars = assertFutureBarCannotChangeEarlierSignals(created.paperRunId(), frozenSummary,
                backtest.strategyVersionSnapshotJson());
        var counterfactual = runCounterfactualReplay(publish.publishRecordId(), frozenSummary,
                changedBars, capture.barCount());
        assertCounterfactualCausality(sim.decisions(created.paperRunId()).reversed(),
                counterfactual.rawDecisions(), trajectoryA, counterfactual.trajectory(),
                changedBars.get(60).openTime());
        assertPublicReplayIdentityDefectsFailClosed(backtest.backtestRunId(), created.paperRunId(), facts);
    }

    private List<HistoricalBar> assertFutureBarCannotChangeEarlierSignals(String paperRunId,
            String summaryJson, String strategyJson) {
        var stored = read(summaryJson).path("consumedBars");
        List<HistoricalBar> original = new ArrayList<>();
        stored.forEach(node -> original.add(new HistoricalBar(node.path("exchangeCode").asText(),
                node.path("marketType").asText(), node.path("symbol").asText(),
                BarInterval.fromWireValue(node.path("interval").asText()),
                Instant.parse(node.path("openTime").asText()), Instant.parse(node.path("closeTime").asText()),
                new BigDecimal(node.path("openPrice").asText()), new BigDecimal(node.path("highPrice").asText()),
                new BigDecimal(node.path("lowPrice").asText()), new BigDecimal(node.path("closePrice").asText()),
                new BigDecimal(node.path("volume").asText()),
                node.path("quoteVolume").isNull() ? null : new BigDecimal(node.path("quoteVolume").asText()),
                node.path("tradeCount").isNull() ? null : node.path("tradeCount").asLong(),
                node.path("qualityStatus").asText(), node.path("rawPayloadJson").asText(),
                Instant.parse(node.path("availableAt").asText()))));
        assertEquals(72, original.size());
        List<HistoricalBar> changed = new ArrayList<>(original);
        HistoricalBar future = changed.get(60);
        changed.set(60, new HistoricalBar(future.exchangeCode(), future.marketType(), future.symbol(),
                future.interval(), future.openTime(), future.closeTime(),
                future.openPrice().add(BigDecimal.ONE),
                future.highPrice(), future.lowPrice(), future.closePrice().add(BigDecimal.ONE),
                future.volume(), future.quoteVolume(), future.tradeCount(), future.qualityStatus(),
                future.rawPayloadJson(), future.availableAt()));
        var strategy = SpotSmaTargetStrategy.fromSnapshot(strategyJson, mapper);
        var persisted = sim.decisions(paperRunId).reversed();
        for (int i = 0; i < 60; i++) {
            var expected = strategy.evaluate(original.subList(0, i + 1));
            assertEquals(expected, strategy.evaluate(changed.subList(0, i + 1)),
                    "signal before future bar " + i);
            assertEquals(0, expected.targetExposure().compareTo(persisted.get(i).targetExposure()),
                    "persisted target before future bar " + i);
            assertEquals(SpotBarIdentity.capture(original.subList(0, i + 1), mapper).sha256(),
                    SpotBarIdentity.capture(changed.subList(0, i + 1), mapper).sha256(),
                    "input identity before future bar " + i);
            assertEquals(SpotBarIdentity.capture(original.subList(0, i + 1), mapper).sha256(),
                    persisted.get(i).inputSha256(), "persisted input before future bar " + i);
        }
        // signal 58 的执行价属于 bar 60 开盘事件；前序 signal 只绑定各自已收盘前缀。
        assertEquals(original.get(60).openTime(), persisted.get(58).executionOpenTime());
        assertEquals(SpotBarIdentity.capture(List.of(original.get(60)), mapper).sha256(),
                persisted.get(58).executionBarSha256());
        assertTrue(!SpotBarIdentity.capture(original, mapper).sha256()
                .equals(SpotBarIdentity.capture(changed, mapper).sha256()));
        return List.copyOf(changed);
    }

    private ReplayResult runCounterfactualReplay(String publishId, String originalSummary,
            List<HistoricalBar> changedBars, int barCount) {
        JdbcTemplate isolated = new JdbcTemplate(new DriverManagerDataSource(
                baseUrl + "?currentSchema=" + counterfactualSchema, databaseUser, databasePassword));
        String backtestId = isolated.queryForObject(
                "SELECT backtest_run_id FROM backtest_publish_records WHERE publish_record_id=?",
                String.class, publishId);
        var changedIdentity = SpotBarIdentity.capture(changedBars, mapper);
        ObjectNode summary = (ObjectNode) read(originalSummary).deepCopy();
        summary.set("consumedBars", read(changedIdentity.canonicalJson()));
        summary.put("barContentSha256", changedIdentity.sha256());
        // 第三次运行是测试专用的反事实数据集，使用新身份，不伪称原始 OKX 捕获。
        summary.put("datasetProvider", "synthetic");
        ObjectNode dataset = (ObjectNode) read(isolated.queryForObject(
                "SELECT dataset_snapshot_json::text FROM backtest_runs WHERE backtest_run_id=?",
                String.class, backtestId)).deepCopy();
        dataset.put("provider", "synthetic");
        dataset.put("source", "TEST_ONLY_COUNTERFACTUAL");
        dataset.put("datasetId", "counterfactual-" + changedIdentity.sha256());
        dataset.remove("capture");
        isolated.update("""
                UPDATE backtest_runs SET summary_json=?::jsonb,dataset_snapshot_json=?::jsonb
                WHERE backtest_run_id=?
                """, summary.toString(), dataset.toString(), backtestId);
        var replay = runIndependentReplay(publishId, barCount, counterfactualSchema);
        assertTrue(!replay.created().barContentSha256().equals(
                read(originalSummary).path("barContentSha256").asText()));
        return replay;
    }

    private void assertCounterfactualCausality(
            List<com.guidinglight.nexusquant.scheduler.paper.StrategySimDecisionRepository.DecisionView> original,
            List<com.guidinglight.nexusquant.scheduler.paper.StrategySimDecisionRepository.DecisionView> changed,
            List<String> originalTrajectory, List<String> changedTrajectory, Instant changedEventTime) {
        assertEquals(72, original.size());
        assertEquals(72, changed.size());
        for (int i = 0; i < 60; i++) {
            var left = original.get(i);
            var right = changed.get(i);
            assertEquals(left.signalOpenTime(), right.signalOpenTime(), "signal time " + i);
            assertEquals(left.signalAvailableAt(), right.signalAvailableAt(), "signal visibility " + i);
            assertEquals(left.strategyChecksum(), right.strategyChecksum(), "strategy " + i);
            assertEquals(0, left.targetExposure().compareTo(right.targetExposure()), "target " + i);
            assertEquals(left.inputSha256(), right.inputSha256(), "signal prefix " + i);
            // 已发生的执行事件可完整比较；bar60 的开盘变异只能改变 bar60 及其后事件。
            if (left.executionOpenTime() == null
                    || left.executionOpenTime().isBefore(changedEventTime)) {
                assertEquals(left.status(), right.status(), "prior execution status " + i);
                assertEquals(left.reason(), right.reason(), "prior execution reason " + i);
                assertEquals(left.side(), right.side(), "prior execution side " + i);
                assertEquals(numeric(left.quantity()), numeric(right.quantity()), "prior execution qty " + i);
                assertEquals(numeric(left.executionPrice()), numeric(right.executionPrice()),
                        "prior execution price " + i);
            }
        }
        for (int i = 0; i < originalTrajectory.size(); i++) {
            Instant eventTime = Instant.parse(originalTrajectory.get(i).split("\\|", -1)[1]);
            if (eventTime.isBefore(changedEventTime)) {
                assertEquals(originalTrajectory.get(i), changedTrajectory.get(i),
                        "economic trajectory before changed event " + i);
            }
        }
        assertEquals(changedEventTime, original.get(58).executionOpenTime());
        assertEquals(changedEventTime, changed.get(58).executionOpenTime());
        assertTrue(!original.get(58).executionBarSha256().equals(changed.get(58).executionBarSha256()));
    }

    private void assertPublicReplayIdentityDefectsFailClosed(String backtestId, String paperRunId,
            StrategySimRunService.FactsView before) {
        String originalSummary = jdbc.queryForObject(
                "SELECT summary_json::text FROM backtest_runs WHERE backtest_run_id=?",
                String.class, backtestId);
        String originalDataset = jdbc.queryForObject(
                "SELECT dataset_snapshot_json::text FROM backtest_runs WHERE backtest_run_id=?",
                String.class, backtestId);
        String originalStrategy = jdbc.queryForObject(
                "SELECT strategy_version_snapshot_json::text FROM paper_trading_runs WHERE paper_run_id=?",
                String.class, paperRunId);
        String originalConfig = jdbc.queryForObject(
                "SELECT config_snapshot_json::text FROM paper_trading_runs WHERE paper_run_id=?",
                String.class, paperRunId);
        try {
            for (String defect : List.of("missing", "duplicate", "disorder", "interval", "unclosed", "digest")) {
                ObjectNode changed = (ObjectNode) read(originalSummary).deepCopy();
                ArrayNode bars = (ArrayNode) changed.path("consumedBars");
                switch (defect) {
                    case "missing" -> bars.remove(20);
                    case "duplicate" -> bars.insert(20, bars.get(19).deepCopy());
                    case "disorder" -> {
                        var previous = bars.get(19).deepCopy();
                        bars.set(19, bars.get(20).deepCopy());
                        bars.set(20, previous);
                    }
                    case "interval" -> ((ObjectNode) bars.get(20)).put("interval", "1m");
                    case "unclosed" -> ((ObjectNode) bars.get(20)).put("qualityStatus", "OPEN");
                    case "digest" -> ((ObjectNode) bars.get(20)).put("closePrice", "1");
                    default -> throw new IllegalStateException(defect);
                }
                jdbc.update("UPDATE backtest_runs SET summary_json=?::jsonb WHERE backtest_run_id=?",
                        changed.toString(), backtestId);
                assertThrows(RuntimeException.class, () -> sim.advance(paperRunId), defect);
            }
            jdbc.update("UPDATE backtest_runs SET summary_json=?::jsonb WHERE backtest_run_id=?",
                    originalSummary, backtestId);
            ObjectNode downgraded = (ObjectNode) read(originalSummary).deepCopy();
            downgraded.put("datasetProvider", "synthetic");
            jdbc.update("UPDATE backtest_runs SET summary_json=?::jsonb WHERE backtest_run_id=?",
                    downgraded.toString(), backtestId);
            assertEquals("SIM_DATASET_PROVIDER_MISMATCH", assertThrows(IllegalStateException.class,
                    () -> sim.advance(paperRunId)).getMessage());
            jdbc.update("UPDATE backtest_runs SET summary_json=?::jsonb WHERE backtest_run_id=?",
                    originalSummary, backtestId);
            ObjectNode visibility = (ObjectNode) read(originalDataset).deepCopy();
            ((ObjectNode) visibility.path("capture")).put("replayVisibilityVersion", "OTHER");
            jdbc.update("UPDATE backtest_runs SET dataset_snapshot_json=?::jsonb WHERE backtest_run_id=?",
                    visibility.toString(), backtestId);
            assertEquals("SIM_DATASET_IDENTITY_DRIFT", assertThrows(IllegalStateException.class,
                    () -> sim.advance(paperRunId)).getMessage());
            jdbc.update("UPDATE backtest_runs SET dataset_snapshot_json=?::jsonb WHERE backtest_run_id=?",
                    originalDataset, backtestId);
            ObjectNode strategy = (ObjectNode) read(originalStrategy).deepCopy();
            String parameterField = strategy.has("paramSnapshotJson") ? "paramSnapshotJson" : "paramSnapshot";
            var parameters = strategy.path(parameterField);
            ObjectNode changedParameters = (ObjectNode) (parameters.isTextual()
                    ? read(parameters.asText()) : parameters.deepCopy());
            changedParameters.put("window", 4);
            strategy.set(parameterField, changedParameters);
            jdbc.update("UPDATE paper_trading_runs SET strategy_version_snapshot_json=?::jsonb WHERE paper_run_id=?",
                    strategy.toString(), paperRunId);
            assertEquals("SIM_STRATEGY_VERSION_DRIFT", assertThrows(IllegalStateException.class,
                    () -> sim.advance(paperRunId)).getMessage());
            jdbc.update("UPDATE paper_trading_runs SET strategy_version_snapshot_json=?::jsonb WHERE paper_run_id=?",
                    originalStrategy, paperRunId);
            for (String field : List.of("feeRate", "slippageBps", "ruleSha256")) {
                ObjectNode config = (ObjectNode) read(originalConfig).deepCopy();
                String changedValue = switch (field) {
                    case "feeRate" -> "0.002";
                    case "slippageBps" -> "11";
                    default -> "b".repeat(64);
                };
                ((ObjectNode) config.path("costAndRuleAssumptions")).put(field, changedValue);
                jdbc.update("UPDATE paper_trading_runs SET config_snapshot_json=?::jsonb WHERE paper_run_id=?",
                        config.toString(), paperRunId);
                assertEquals("SIM_COST_ASSUMPTION_DRIFT", assertThrows(IllegalStateException.class,
                        () -> sim.advance(paperRunId)).getMessage(), field);
            }
        } finally {
            jdbc.update("UPDATE backtest_runs SET summary_json=?::jsonb,dataset_snapshot_json=?::jsonb WHERE backtest_run_id=?",
                    originalSummary, originalDataset, backtestId);
            jdbc.update("UPDATE paper_trading_runs SET strategy_version_snapshot_json=?::jsonb,config_snapshot_json=?::jsonb WHERE paper_run_id=?",
                    originalStrategy, originalConfig, paperRunId);
        }
        var after = sim.facts(paperRunId);
        assertEquals(before.orders().size(), after.orders().size());
        assertEquals(before.trades().size(), after.trades().size());
        assertEquals(before.ledgerEntries().size(), after.ledgerEntries().size());
    }

    private void assertPublicBarAccounting(String paperRunId, int count) {
        assertPublicBarAccounting(sim, jdbc, paperRunId, count);
    }

    private void assertPublicBarAccounting(StrategySimRunService service, JdbcTemplate database,
            String paperRunId, int count) {
        var chronological = service.decisions(paperRunId).reversed();
        var snapshots = database.queryForList("""
                SELECT input_snapshot_json::text AS snapshot FROM strategy_sim_decisions
                WHERE paper_run_id=? ORDER BY signal_open_time
                """, paperRunId);
        assertEquals(count, chronological.size());
        assertEquals(count, snapshots.size());
        for (int i = 0; i < count; i++) {
            var row = chronological.get(i);
            Instant expected = Instant.parse("2026-09-20T00:00:00Z").plusSeconds(3600L * i);
            assertEquals(expected, row.signalOpenTime());
            assertTrue(row.signalAvailableAt().isAfter(row.signalOpenTime()));
            var snapshot = read((String) snapshots.get(i).get("snapshot"));
            var signalBars = snapshot.path("signalBars");
            assertEquals(i + 1, signalBars.size());
            assertEquals(expected.toString(), signalBars.get(i).path("openTime").asText());
            assertEquals("OK", signalBars.get(i).path("qualityStatus").asText());
            assertTrue(!Instant.parse(signalBars.get(i).path("availableAt").asText())
                    .isBefore(Instant.parse(signalBars.get(i).path("closeTime").asText())));
            if (row.executionOpenTime() != null) {
                assertTrue(row.executionOpenTime().isAfter(row.signalAvailableAt()));
                assertEquals(row.executionOpenTime().toString(),
                        snapshot.path("executionBar").path("openTime").asText());
            }
        }
    }

    private String cloneFrozenPublicReplayInput() {
        String targetSchema = "strategy_sim_independent_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + targetSchema);
        Flyway independentFlyway = Flyway.configure().dataSource(admin).schemas(targetSchema)
                .initSql("SET search_path TO " + targetSchema + "," + schema + ",public")
                .locations("classpath:db/migration").load();
        independentFlyway.migrate();
        independentFlyway.validate();
        JdbcTemplate isolation = new JdbcTemplate(new DriverManagerDataSource(
                baseUrl + "?currentSchema=" + targetSchema + "," + schema + ",public",
                databaseUser, databasePassword));
        // B 仅取得 A 尚未创建 SIM run 时的冻结输入；不能复制任何已执行的订单或账本。
        for (String table : List.of("accounts", "marketdata_datasets", "marketdata_bars",
                "marketdata_dataset_coverage", "public_market_captures", "strategy_definitions",
                "strategy_versions", "research_configs", "backtest_configs", "backtest_runs",
                "sim_orders", "sim_trades", "sim_positions", "sim_pnl_snapshots",
                "backtest_eval_reports", "backtest_publish_records")) {
            isolation.update("INSERT INTO " + targetSchema + "." + table
                    + " SELECT * FROM " + schema + "." + table);
        }
        isolation.execute("SELECT setval('" + targetSchema + ".accounts_account_id_seq',"
                + "(SELECT max(account_id) FROM " + targetSchema + ".accounts))");
        assertEquals(1, isolation.queryForObject("SELECT count(*) FROM " + targetSchema
                + ".backtest_publish_records", Integer.class));
        assertEquals(0, isolation.queryForObject("SELECT count(*) FROM " + targetSchema
                + ".strategy_sim_decisions", Integer.class));
        assertEquals(0, isolation.queryForObject("SELECT count(*) FROM " + targetSchema
                + ".orders", Integer.class));
        assertEquals(0, isolation.queryForObject("SELECT count(*) FROM " + targetSchema
                + ".trades", Integer.class));
        assertEquals(0, isolation.queryForObject("SELECT count(*) FROM " + targetSchema
                + ".ledger_entries", Integer.class));
        return targetSchema;
    }

    private ReplayResult runIndependentReplay(String publishId, int barCount, String targetSchema) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NexusQuantApplication.class)
                .profiles("ci-app-smoke").web(WebApplicationType.NONE)
                .initializers(new NqAppContextPostgresSmokeTest.NoOutboundInitializer())
                .properties(Map.ofEntries(
                        Map.entry("spring.datasource.url", baseUrl + "?currentSchema=" + targetSchema),
                        Map.entry("spring.datasource.username", databaseUser),
                        Map.entry("spring.datasource.password", databasePassword),
                        Map.entry("spring.flyway.enabled", "false"),
                        Map.entry("spring.sql.init.mode", "never"),
                        Map.entry("spring.task.scheduling.enabled", "false"),
                        Map.entry("nq.runtime.trading-components.enabled", "true"),
                        Map.entry("nq.strategy-sim.enabled", "true"),
                        Map.entry("nq.auth.bootstrap-admin.enabled", "false"),
                        Map.entry("nq.instrument.catalog-sync.enabled", "false"),
                        Map.entry("nq.okx.recovery.enabled", "false"),
                        Map.entry("nq.okx.ws.enabled", "false"),
                        Map.entry("nq.binance.ws.enabled", "false"),
                        Map.entry("nq.account.credentials.verification-mode", "STRUCTURAL"),
                        Map.entry("nq.account.credentials.master-key", "strategy-sim-synthetic-master-key-123456789"),
                        Map.entry("nq.security.issuer", "nexus-quant-strategy-sim-synthetic"),
                        Map.entry("nq.security.secret", "strategy-sim-synthetic-secret-123456789"),
                        Map.entry("nq.security.access-token-ttl", "PT30M")))
                .run("--spring.datasource.url=" + baseUrl + "?currentSchema=" + targetSchema,
                        "--spring.datasource.username=" + databaseUser,
                        "--spring.datasource.password=" + databasePassword)) {
            JdbcTemplate isolated = context.getBean(JdbcTemplate.class);
            StrategySimRunService isolatedSim = context.getBean(StrategySimRunService.class);
            PaperTradingRunService isolatedRuns = context.getBean(PaperTradingRunService.class);
            PaperMatchingService isolatedMatching = context.getBean(PaperMatchingService.class);
            assertEquals(targetSchema, isolated.queryForObject("SELECT current_schema()", String.class));
            assertEquals(1, isolated.queryForObject("""
                    SELECT count(*) FROM backtest_publish_records WHERE publish_record_id=?
                    """, Integer.class, publishId));
            isolated.update("""
                    UPDATE kill_switch_states SET status='DISENGAGED', version=version+1,
                        reason_code='PUBLIC_REPLAY_TEST_ONLY', source='STRATEGY_SIM_TEST',
                        updated_at=now(), updated_by='test', trace_id='public-replay-test'
                    WHERE scope='GLOBAL_TRADING'
                    """);
            var created = isolatedSim.create(publishId, new BigDecimal("100"),
                    "captured-public-replay-test");
            isolatedRuns.start(created.paperRunId());
            List<String> trajectory = new ArrayList<>();
            for (int i = 0; i < barCount; i++) {
                var decision = isolatedSim.advance(created.paperRunId());
                if (i < 2) {
                    assertEquals("NO_SIGNAL", decision.status());
                    assertEquals("INSUFFICIENT_HISTORY", decision.reason());
                    assertEquals(0, isolatedSim.facts(created.paperRunId()).orders().size());
                }
                if ("ACCEPTED".equals(decision.status())) {
                    assertEquals(1, isolatedMatching.matchOnce(100));
                }
                trajectory.add(trajectoryRow(i, decision, isolatedSim.facts(created.paperRunId())));
            }
            assertPublicBarAccounting(isolatedSim, isolated, created.paperRunId(), barCount);
            String last = isolatedSim.decisions(created.paperRunId()).getFirst().decisionId();
            assertEquals(last, isolatedSim.advance(created.paperRunId()).decisionId());
            assertEquals(0, isolatedMatching.matchOnce(100));
            var facts = isolatedSim.facts(created.paperRunId());
            assertTradeAccounting(isolated, created.paperRunId(), facts.trades().size(),
                    facts.ledgerEntries().size());
            return new ReplayResult(created, facts, stablePublicDecisions(isolatedSim, created.paperRunId()),
                    List.copyOf(trajectory), canonicalSemanticRows(isolated, created.paperRunId()),
                    ledgerSemanticRows(isolated, created.paperRunId()),
                    orderSemanticRows(isolated, created.paperRunId()),
                    tradeSemanticRows(isolated, created.paperRunId()),
                    List.copyOf(isolatedSim.decisions(created.paperRunId()).reversed()));
        }
    }

    private record ReplayResult(StrategySimRunService.RunView created,
            StrategySimRunService.FactsView facts, List<String> decisions,
            List<String> trajectory, List<String> canonical, List<String> ledger,
            List<String> orders, List<String> trades,
            List<com.guidinglight.nexusquant.scheduler.paper.StrategySimDecisionRepository.DecisionView> rawDecisions) { }

    private String trajectoryRow(int index,
            com.guidinglight.nexusquant.scheduler.paper.StrategySimDecisionRepository.DecisionView decision,
            StrategySimRunService.FactsView facts) {
        Instant eventTime = decision.executionOpenTime() == null
                ? decision.signalAvailableAt() : decision.executionOpenTime();
        return String.join("|", String.valueOf(index), eventTime.toString(),
                decision.signalOpenTime().toString(),
                decision.strategyChecksum(), numeric(decision.targetExposure()), decision.status(),
                decision.reason(), String.valueOf(decision.side()), numeric(decision.quantity()),
                numeric(decision.executionPrice()), numeric(facts.cash()),
                numeric(facts.positionQuantity()), numeric(facts.pnl()),
                String.valueOf(facts.orders().size()), String.valueOf(facts.trades().size()),
                String.valueOf(facts.ledgerEntries().size()));
    }

    private List<String> canonicalSemanticRows(String paperRunId) {
        return canonicalSemanticRows(jdbc, paperRunId);
    }

    private List<String> canonicalSemanticRows(JdbcTemplate database, String paperRunId) {
        var rows = database.queryForList("""
                SELECT d.signal_open_time,o.symbol,o.side,o.type,o.qty,o.price,o.status,
                       t.price AS trade_price,t.qty AS trade_qty,t.fee,t.fee_currency,t.ts
                FROM strategy_sim_decisions d JOIN orders o ON o.order_id=d.order_id
                LEFT JOIN trades t ON t.order_id=o.order_id
                WHERE d.paper_run_id=? ORDER BY d.signal_open_time
                """, paperRunId);
        return rows.stream().map(row -> String.join("|",
                String.valueOf(row.get("signal_open_time")), String.valueOf(row.get("symbol")),
                String.valueOf(row.get("side")), String.valueOf(row.get("type")),
                numeric((BigDecimal) row.get("qty")), numeric((BigDecimal) row.get("price")),
                String.valueOf(row.get("status")), numeric((BigDecimal) row.get("trade_price")),
                numeric((BigDecimal) row.get("trade_qty")), numeric((BigDecimal) row.get("fee")),
                String.valueOf(row.get("fee_currency")), String.valueOf(row.get("ts"))))
                .toList();
    }

    private List<String> orderSemanticRows(JdbcTemplate database, String paperRunId) {
        var rows = database.queryForList("""
                SELECT d.signal_open_time,o.venue,o.trade_env,o.symbol,o.side,o.type,
                       o.qty,o.price,o.status,d.execution_price
                FROM strategy_sim_decisions d JOIN orders o ON o.order_id=d.order_id
                WHERE d.paper_run_id=? ORDER BY d.signal_open_time
                """, paperRunId);
        return rows.stream().map(row -> String.join("|", String.valueOf(row.get("signal_open_time")),
                String.valueOf(row.get("venue")), String.valueOf(row.get("trade_env")),
                String.valueOf(row.get("symbol")), String.valueOf(row.get("side")),
                String.valueOf(row.get("type")), numeric((BigDecimal) row.get("qty")),
                numeric((BigDecimal) row.get("price")),
                numeric((BigDecimal) row.get("execution_price")),
                String.valueOf(row.get("status")))).toList();
    }

    private List<String> tradeSemanticRows(JdbcTemplate database, String paperRunId) {
        var rows = database.queryForList("""
                SELECT d.signal_open_time,t.exchange,t.trade_env,t.symbol,t.price,t.qty,
                       t.fee,t.fee_currency,t.ts
                FROM strategy_sim_decisions d JOIN trades t ON t.order_id=d.order_id
                WHERE d.paper_run_id=? ORDER BY d.signal_open_time,t.ts
                """, paperRunId);
        return rows.stream().map(row -> String.join("|", String.valueOf(row.get("signal_open_time")),
                String.valueOf(row.get("exchange")), String.valueOf(row.get("trade_env")),
                String.valueOf(row.get("symbol")), numeric((BigDecimal) row.get("price")),
                numeric((BigDecimal) row.get("qty")), numeric((BigDecimal) row.get("fee")),
                String.valueOf(row.get("fee_currency")), String.valueOf(row.get("ts"))))
                .toList();
    }

    private List<String> ledgerSemanticRows(String paperRunId) {
        return ledgerSemanticRows(jdbc, paperRunId);
    }

    private List<String> ledgerSemanticRows(JdbcTemplate database, String paperRunId) {
        var rows = database.queryForList("""
                SELECT e.currency,e.delta,e.balance_after,e.direction,e.ref_type,e.ts,
                       d.signal_open_time,
                       CASE WHEN e.ref_type='TRADE' THEN split_part(e.idempotency_key,':LEDGER:',2)
                            ELSE split_part(e.idempotency_key,':SIM_FUND:',2) END AS economic_leg
                FROM ledger_entries e JOIN paper_trading_runs p ON p.canonical_account_id=e.account_id
                LEFT JOIN trades t ON e.ref_type='TRADE' AND e.ref_id=t.trade_id
                LEFT JOIN strategy_sim_decisions d ON d.order_id=t.order_id
                WHERE p.paper_run_id=? ORDER BY CASE WHEN e.ref_type='TRADE' THEN 1 ELSE 0 END,
                    d.signal_open_time, CASE split_part(e.idempotency_key,':LEDGER:',2)
                    WHEN '1' THEN 1 WHEN '2' THEN 2 WHEN 'FEE_1' THEN 3 WHEN 'FEE_2' THEN 4 ELSE 0 END,
                    e.ref_type
                """, paperRunId);
        return rows.stream().map(row -> String.join("|",
                String.valueOf(row.get("signal_open_time")),
                String.valueOf(row.get("economic_leg")),
                String.valueOf(row.get("currency")), numeric((BigDecimal) row.get("delta")),
                numeric((BigDecimal) row.get("balance_after")),
                String.valueOf(row.get("direction")), String.valueOf(row.get("ref_type")),
                "TRADE".equals(row.get("ref_type")) ? String.valueOf(row.get("ts")) : "FUNDING"))
                .toList();
    }

    private void assertTradeAccounting(String paperRunId, int tradeCount, int ledgerCount) {
        assertTradeAccounting(jdbc, paperRunId, tradeCount, ledgerCount);
    }

    private void assertTradeAccounting(JdbcTemplate database, String paperRunId,
            int tradeCount, int ledgerCount) {
        assertEquals(2 + 4 * tradeCount, ledgerCount);
        var rows = database.queryForList("""
                SELECT t.fee,count(e.entry_id) AS entries,sum(e.delta) AS balance,
                       sum(CASE WHEN e.idempotency_key LIKE '%:LEDGER:FEE_1' THEN e.delta ELSE 0 END) AS fee_debit,
                       sum(CASE WHEN e.idempotency_key LIKE '%:LEDGER:FEE_2' THEN e.delta ELSE 0 END) AS fee_credit
                FROM trades t JOIN paper_trading_runs p ON p.canonical_account_id=t.account_id
                JOIN ledger_entries e ON e.ref_type='TRADE' AND e.ref_id=t.trade_id
                WHERE p.paper_run_id=? GROUP BY t.trade_id,t.fee
                """, paperRunId);
        assertEquals(tradeCount, rows.size());
        for (var row : rows) {
            assertEquals(4L, row.get("entries"));
            assertEquals(0, ((BigDecimal) row.get("balance")).compareTo(BigDecimal.ZERO));
            assertEquals(0, ((BigDecimal) row.get("fee_debit"))
                    .compareTo(((BigDecimal) row.get("fee")).negate()));
            assertEquals(0, ((BigDecimal) row.get("fee_credit"))
                    .compareTo((BigDecimal) row.get("fee")));
        }
    }

    private static String digest(List<String> rows) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            rows.forEach(row -> sha.update((row + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(sha.digest());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<String> stablePublicDecisions(String paperRunId) {
        return stablePublicDecisions(sim, paperRunId);
    }

    private List<String> stablePublicDecisions(StrategySimRunService service, String paperRunId) {
        return service.decisions(paperRunId).reversed().stream().map(decision -> String.join("|",
                String.valueOf(decision.strategyVersionId()), String.valueOf(decision.strategyChecksum()),
                numeric(decision.targetExposure()), String.valueOf(decision.inputSha256()),
                String.valueOf(decision.executionBarSha256()),
                String.valueOf(decision.signalOpenTime()), String.valueOf(decision.signalAvailableAt()),
                String.valueOf(decision.executionOpenTime()), String.valueOf(decision.status()),
                String.valueOf(decision.reason()), String.valueOf(decision.side()),
                numeric(decision.quantity()), numeric(decision.executionPrice()),
                numeric(decision.feeRate()), numeric(decision.slippageBps()))).toList();
    }

    private static String numeric(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    @Test
    void formalServicesBindDatasetVersionBacktestEvaluationPublishAndCanonicalSim() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        List<HistoricalBar> bars = List.of(bar(0, "100"), bar(1, "101"),
                bar(2, "102"), bar(3, "103"), bar(4, "104"));
        marketdataBars.upsertBars(bars, "FIXTURE_SYNC", Instant.now());
        var dataset = datasets.createDataset(new CreateMarketdataDatasetCommand(
                "Synthetic OKX BTC-USDT " + suffix, "OKX", "SPOT", "BTC-USDT", "1m",
                bars.getFirst().openTime(), bars.getLast().closeTime(), "synthetic-test"));
        assertEquals("READY", dataset.status().name());
        String datasetSnapshot = datasets.buildDatasetSnapshot(dataset.datasetId());
        Long sourceAccount = jdbc.queryForObject("""
                INSERT INTO accounts(account_code,venue,status)
                VALUES (?,'OKX','ACTIVE') RETURNING account_id
                """, Long.class, "SIM-SOURCE-" + suffix);
        String strategyId = "str-sim-" + suffix;
        String strategyCode = "sim-sma-" + suffix;
        Instant now = Instant.now();
        definitions.insert(new StrategyDefinition(strategyId, strategyCode, "Synthetic SMA",
                SpotSmaTargetStrategy.EXECUTABLE, "OKX", sourceAccount, "SIM", true,
                "{}", 1, now, now));
        var version = versions.create(new StrategyVersionCreateRequest(strategyCode, "v1", "ACTIVE",
                "{\"window\":3,\"investedExposure\":\"1\"}", "{}",
                "{\"executable\":\"SPOT_SMA_TARGET_V1\"}", "synthetic-test"));
        var research = researchConfigs.create(new ResearchConfigCreateRequest(strategyId,
                "Synthetic SMA research", "synthetic", "{}", "{}", datasetSnapshot));
        String cost = """
                {"quantityStep":"0.0001","priceTick":"0.01","minimumQuantity":"0.0001",
                 "minimumNotional":"5","feeRate":"0.001","slippageBps":"10"}
                """;
        var config = backtestConfigs.create(new BacktestConfigCreateRequest(
                research.researchConfigId(), "Synthetic SMA backtest", "synthetic",
                bars.getFirst().openTime(), bars.getLast().closeTime(), new BigDecimal("100"),
                cost, "{}"));
        backtestConfigs.bindDataset(config.backtestConfigId(), dataset.datasetId().toString(), datasetSnapshot);
        backtestConfigs.bindStrategyVersion(config.backtestConfigId(), version.strategyVersionId());
        var backtest = backtestRuns.create(new BacktestRunStartRequest(config.backtestConfigId()));
        assertEquals("SUCCEEDED", backtestExecution.startRun(backtest.backtestRunId()).resultStatus().name());
        assertEquals("SUCCEEDED", evaluations.evaluate(backtest.backtestRunId()).evaluationStatus().name());
        var publish = publishing.publish(new BacktestPublishRequest(backtest.backtestRunId(),
                "Synthetic SMA publish", version.strategyVersionId()));
        assertEquals("SUCCEEDED", publish.publishStatus().name());
        jdbc.update("""
                UPDATE kill_switch_states SET status='DISENGAGED', version=version+1,
                    reason_code='SYNTHETIC_TEST_ONLY', source='STRATEGY_SIM_TEST',
                    updated_at=now(), updated_by='test', trace_id='sim-test'
                WHERE scope='GLOBAL_TRADING'
                """);
        var created = sim.create(publish.publishRecordId(), new BigDecimal("100"), "synthetic-test");
        assertEquals(version.strategyVersionId(), created.strategyVersionId());
        runs.start(created.paperRunId());
        sim.advance(created.paperRunId());
        sim.advance(created.paperRunId());
        assertEquals("ACCEPTED", sim.advance(created.paperRunId()).status());
        matching.matchOnce(100);
        var facts = sim.facts(created.paperRunId());
        assertEquals(1, facts.orders().size());
        assertEquals(1, facts.trades().size());
        assertTrue(facts.positionQuantity().signum() > 0);
        assertTrue(facts.cash().signum() >= 0);
    }

    private Fixture seed() {
        return seed(List.of(bar(0, "100"), bar(1, "101"), bar(2, "102"),
                bar(3, "103"), bar(4, "104")));
    }

    private Fixture seed(List<HistoricalBar> bars) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        String strategyId = "str-gz-" + id;
        String strategyCode = "gz-" + id;
        String versionId = "sv-gz-" + id;
        String researchId = "rc-gz-" + id;
        String configId = "bc-gz-" + id;
        String backtestId = "br-gz-" + id;
        String publishId = "pub-gz-" + id;
        String checksum = "a".repeat(64);
        String params = "{\"window\":3,\"investedExposure\":\"1\"}";
        String source = "{\"executable\":\"SPOT_SMA_TARGET_V1\"}";
        String backtestVersion = "{\"strategyVersionId\":\"" + versionId
                + "\",\"checksum\":\"" + checksum + "\",\"paramSnapshotJson\":"
                + params + ",\"sourceSnapshotJson\":" + source + "}";
        String publishVersion = "{\"strategyVersionId\":\"" + versionId
                + "\",\"checksum\":\"" + checksum + "\",\"paramSnapshot\":"
                + params + ",\"sourceSnapshot\":" + source + "}";
        Long account = jdbc.queryForObject("""
                INSERT INTO accounts(account_code,venue,status) VALUES (?,'OKX','ACTIVE') RETURNING account_id
                """, Long.class, "STRATEGY-SIM-TEST-SOURCE-" + id);
        jdbc.update("""
                INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,
                    exchange_code,account_id,trade_env,enabled,config_snapshot)
                VALUES (?,?,?,'SPOT_SMA_TARGET_V1','OKX',?,'SIM',true,'{}'::jsonb)
                """, strategyId, strategyCode, "Strategy SIM synthetic", account);
        jdbc.update("""
                INSERT INTO strategy_versions(strategy_version_id,strategy_code,version,version_name,status,
                    param_snapshot_json,source_snapshot_json,checksum)
                VALUES (?,?,1,'v1','ACTIVE',?::jsonb,?::jsonb,?)
                """, versionId, strategyCode, params, source, checksum);
        jdbc.update("""
                INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)
                VALUES (?,?,?,'{}'::jsonb)
                """, researchId, strategyId, "Strategy SIM synthetic");
        jdbc.update("""
                INSERT INTO backtest_configs(backtest_config_id,research_config_id,name,
                    strategy_version_id,strategy_version_snapshot_json,param_snapshot_json)
                VALUES (?,?,?, ?,?::jsonb,?::jsonb)
                """, configId, researchId, "Strategy SIM synthetic", versionId, backtestVersion, params);
        var identity = SpotBarIdentity.capture(bars, mapper);
        ObjectNode summary = mapper.createObjectNode();
        summary.put("strategyVersionId", versionId);
        summary.put("exchangeCode", "OKX");
        summary.put("symbol", "BTC-USDT");
        summary.put("interval", "1m");
        summary.put("barCount", bars.size());
        summary.put("barContentSha256", identity.sha256());
        summary.set("consumedBars", read(identity.canonicalJson()));
        summary.set("costAndRuleAssumptions", read("""
                {"quantityStep":"0.0001","priceTick":"0.01","minimumQuantity":"0.0001",
                 "minimumNotional":"5","feeRate":"0.001","slippageBps":"10"}
                """));
        String dataset = "{\"provider\":\"synthetic\",\"datasetId\":\"gz-" + id
                + "\",\"exchangeCode\":\"OKX\",\"symbol\":\"BTC-USDT\",\"interval\":\"1m\"}";
        jdbc.update("""
                INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,
                    source_strategy_id,status,strategy_snapshot,backtest_config_snapshot,summary_json,
                    requested_at,strategy_version_id,strategy_version_snapshot_json,param_snapshot_json,
                    dataset_snapshot_json)
                VALUES (?,?,?,?,'SUCCEEDED','{}'::jsonb,'{}'::jsonb,?::jsonb,now(),?,?::jsonb,
                    ?::jsonb,?::jsonb)
                """, backtestId, configId, researchId, strategyId, summary.toString(),
                versionId, backtestVersion, params, dataset);
        jdbc.update("""
                INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,
                    backtest_config_id,source_strategy_id,target_strategy_definition_id,publish_status,
                    publish_name,publish_snapshot_json,strategy_version_id,version_snapshot_json)
                VALUES (?,?,?,?,?,?,'SUCCEEDED',?,?::jsonb,?,?::jsonb)
                """, publishId, backtestId, researchId, configId, strategyId, strategyId,
                "Strategy SIM synthetic", "{\"strategyCode\":\"" + strategyCode + "\"}", versionId, publishVersion);
        return new Fixture(publishId, versionId, identity.sha256());
    }

    private HistoricalBar bar(int minute, String price) {
        Instant start = Instant.parse("2026-09-25T00:00:00Z").plusSeconds(minute * 60L);
        BigDecimal value = new BigDecimal(price);
        return new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_MINUTE,
                start, start.plusSeconds(59), value, value, value, value, BigDecimal.ONE,
                null, null, "OK", "{}", start.plusSeconds(59));
    }

    private HistoricalBar availableAt(HistoricalBar bar, String timestamp) {
        return new HistoricalBar(bar.exchangeCode(), bar.marketType(), bar.symbol(), bar.interval(),
                bar.openTime(), bar.closeTime(), bar.openPrice(), bar.highPrice(), bar.lowPrice(),
                bar.closePrice(), bar.volume(), bar.quoteVolume(), bar.tradeCount(),
                bar.qualityStatus(), bar.rawPayloadJson(), Instant.parse(timestamp));
    }

    private com.fasterxml.jackson.databind.JsonNode read(String text) {
        try { return mapper.readTree(text); }
        catch (Exception ex) { throw new IllegalArgumentException(ex); }
    }

    private record Fixture(String publishId, String versionId, String digest) { }
}
