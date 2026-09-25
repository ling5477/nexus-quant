package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
    private static String baseUrl;
    private static DriverManagerDataSource admin;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        baseUrl = System.getProperty("nq.strategy-sim.pg.url", "");
        if (!baseUrl.startsWith("jdbc:postgresql://127.0.0.1:"))
            throw new IllegalArgumentException("disposable loopback PostgreSQL URL required");
        schema = "strategy_sim_sim_" + UUID.randomUUID().toString().replace("-", "");
        admin = new DriverManagerDataSource(baseUrl, "postgres", "disposable");
        JdbcTemplate jdbc = new JdbcTemplate(admin);
        jdbc.execute("CREATE SCHEMA " + schema);
        Flyway flyway = Flyway.configure().dataSource(admin).schemas(schema)
                .locations("classpath:db/migration").load();
        flyway.migrate();
        flyway.validate();
        registry.add("spring.datasource.url", () -> baseUrl + "?currentSchema=" + schema);
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "disposable");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll
    static void cleanup() {
        if (admin != null && schema != null) {
            new JdbcTemplate(admin).execute("DROP SCHEMA " + schema + " CASCADE");
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
            builder.environment().put("SPRING_DATASOURCE_USERNAME", "postgres");
            builder.environment().put("SPRING_DATASOURCE_PASSWORD", "disposable");
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
