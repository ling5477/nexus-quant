package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
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
import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimPnlSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.eval.TradeOutcomeCalculator;
import com.guidinglight.nexusquant.research.domain.eval.port.SimPnlSnapshotQueryRepository;
import com.guidinglight.nexusquant.research.infra.backtest.jdbc.JdbcSimPnlSnapshotRepository;
import com.guidinglight.nexusquant.research.infra.backtest.jdbc.JdbcSimTradeRepository;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import com.guidinglight.nexusquant.strategy.domain.SpotSmaTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** V17真实旧写入路径升级V18；顺序行是存储夹具，正式同时间费用与评估由独立HTTP回归覆盖。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@SpringBootTest(
        classes = {
            NexusQuantApplication.class,
            ResearchEventOrderMigrationPostgresIntegrationTest.LegacyStores.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = NqAppContextPostgresSmokeTest.NoOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestExecutionListeners(
        listeners = ResearchEventOrderMigrationPostgresIntegrationTest.Cleanup.class,
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
            "nq.account.credentials.master-key=research-order-storage-synthetic-master-key-123456789",
            "nq.security.issuer=research-order-storage-synthetic",
            "nq.security.secret=research-order-storage-synthetic-jwt-secret-123456789"
        })
class ResearchEventOrderMigrationPostgresIntegrationTest {
    private static ReleaseBaselineTestDatabase database;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthSeedService seeds;
    @Autowired PasswordEncoder passwords;
    @Autowired ExchangeAccountCommandService accounts;
    @Autowired MarketdataBarRepository bars;
    @Autowired MarketdataDatasetService datasets;
    @Autowired StrategyDefinitionRepository definitions;
    @Autowired StrategyVersionService versions;
    @Autowired ResearchConfigService research;
    @Autowired BacktestConfigService configs;
    @Autowired BacktestRunService runs;
    @Autowired BacktestExecutionService execution;
    @Autowired BacktestNativeFeeMigrationPostgresIntegrationTest.LegacyTradeStore oldTrades;
    @Autowired LegacyPnlStore oldPnl;
    @Autowired PlatformTransactionManager transactionManager;
    private SimTrade originalTrade;
    private SimPnlSnapshot originalPnl;
    private JdbcSimTradeRepository currentTrades;
    private JdbcSimPnlSnapshotRepository currentPnl;
    private String runId;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        URI base =
                URI.create(
                        System.getProperty("nq.postgres.smoke.url", "").replaceFirst("^jdbc:", ""));
        if (!"127.0.0.1".equals(base.getHost())
                || base.getPort() != 54466
                || base.getUserInfo() != null
                || base.getQuery() != null)
            throw new IllegalArgumentException("owned loopback54466 PostgreSQL required");
        database = ReleaseBaselineTestDatabase.create();
        migration("17").migrate();
        registry.add("spring.datasource.url", database::url);
        registry.add(
                "spring.datasource.username", () -> System.getProperty("nq.postgres.smoke.user"));
        registry.add(
                "spring.datasource.password",
                () -> System.getProperty("nq.postgres.smoke.password"));
    }

    private static Flyway migration(String target) {
        return Flyway.configure()
                .dataSource(database.source())
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .target(target)
                .load();
    }

    @BeforeAll
    void produceRealLegacyResearchThenUpgrade() {
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "research-order-storage-owner",
                                passwords.encode("synthetic-storage-password"),
                                List.of("OPERATOR", "VIEWER"),
                                true)));
        long owner =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='research-order-storage-owner'",
                        Long.class);
        var account =
                accounts.create(
                        owner,
                        new ExchangeAccountCreateCommand(
                                "OKX", "SIM", "Discount storage synthetic", null));
        List<HistoricalBar> input =
                IntStream.range(0, 5)
                        .mapToObj(
                                index -> {
                                    Instant start =
                                            Instant.parse("2026-09-25T00:00:00Z")
                                                    .plusSeconds(index * 60L);
                                    BigDecimal price = new BigDecimal(100 + index);
                                    return new HistoricalBar(
                                            "OKX",
                                            "SPOT",
                                            "BTC-USDT",
                                            BarInterval.ONE_MINUTE,
                                            start,
                                            start.plusSeconds(59),
                                            price,
                                            price,
                                            price,
                                            price,
                                            BigDecimal.ONE,
                                            null,
                                            null,
                                            "OK",
                                            "{\"fixture\":\"discount-storage\"}",
                                            start.plusSeconds(59));
                                })
                        .toList();
        bars.upsertBars(input, "FIXTURE_SYNC", Instant.now());
        var dataset =
                datasets.createDataset(
                        new CreateMarketdataDatasetCommand(
                                "Discount storage bars",
                                "OKX",
                                "SPOT",
                                "BTC-USDT",
                                "1m",
                                input.getFirst().openTime(),
                                input.getLast().closeTime(),
                                "synthetic-test"));
        String snapshot = datasets.buildDatasetSnapshot(dataset.datasetId()),
                code = "research-order-storage-" + UUID.randomUUID(),
                strategy = "research-order-storage-strategy-" + UUID.randomUUID();
        Instant now = Instant.now();
        definitions.insert(
                new StrategyDefinition(
                        strategy,
                        code,
                        "Discount storage SMA",
                        SpotSmaTargetStrategy.EXECUTABLE,
                        "OKX",
                        account.legacyAccountId(),
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
                                "Discount storage research",
                                "synthetic",
                                "{}",
                                "{}",
                                snapshot));
        var config =
                configs.create(
                        new BacktestConfigCreateRequest(
                                experiment.researchConfigId(),
                                "Discount storage backtest",
                                "synthetic",
                                input.getFirst().openTime(),
                                input.getLast().closeTime(),
                                new BigDecimal("100"),
                                "{\"costSource\":\"EXPERIMENT_ASSUMPTION\",\"quantityStep\":\"0.0001\",\"priceTick\":\"0.01\",\"minimumQuantity\":\"0.0001\",\"minimumNotional\":\"5\",\"slippageBps\":\"0\",\"feeRate\":\"0.001\"}",
                                "{}"));
        configs.bindDataset(config.backtestConfigId(), dataset.datasetId().toString(), snapshot);
        configs.bindStrategyVersion(config.backtestConfigId(), version.strategyVersionId());
        var run = runs.create(new BacktestRunStartRequest(config.backtestConfigId()));
        runId = run.backtestRunId();
        assertEquals("SUCCEEDED", execution.startRun(runId).resultStatus().name());
        var beforeTrades = oldTrades.listByBacktestRunId(runId);
        var beforePnl = oldPnl.listByBacktestRunId(runId);
        assertFalse(beforeTrades.isEmpty());
        assertFalse(beforePnl.isEmpty());
        originalTrade = beforeTrades.getFirst();
        originalPnl = beforePnl.getFirst();
        assertEquals(1, migration("18").migrate().migrationsExecuted);
        migration("18").validate();
        assertEquals(0, migration("18").migrate().migrationsExecuted);
        currentTrades = new JdbcSimTradeRepository(jdbc);
        currentPnl = new JdbcSimPnlSnapshotRepository(jdbc);
        assertEquals(beforeTrades, currentTrades.listByBacktestRunId(runId));
        assertEquals(beforePnl, currentPnl.listByBacktestRunId(runId));
    }

    @Test
    void jdbcUsesSequenceForSameTimeAndLegacyAmbiguityRemainsUnavailable() {
        var buy =
                copyTrade("z-real-order-buy-" + UUID.randomUUID(), "BUY").withExecutionSequence(1);
        var sell =
                copyTrade("a-real-order-sell-" + UUID.randomUUID(), "SELL")
                        .withExecutionSequence(2);
        var again =
                copyTrade("b-real-order-buy-" + UUID.randomUUID(), "BUY").withExecutionSequence(3);
        currentTrades.insert(buy);
        currentTrades.insert(sell);
        currentTrades.insert(again);
        var reread =
                currentTrades.listByBacktestRunId(runId).stream()
                        .filter(row -> row.executionSequence() != null)
                        .toList();
        assertEquals(List.of("BUY", "SELL", "BUY"), reread.stream().map(SimTrade::side).toList());
        assertEquals(
                List.of(1L, 2L, 3L), reread.stream().map(SimTrade::executionSequence).toList());
        assertEquals(1, new TradeOutcomeCalculator().calculate(reread, true).flatTradeCount());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TradeOutcomeCalculator()
                                .calculate(currentTrades.listByBacktestRunId(runId), true));
        assertThrows(
                DataAccessException.class,
                () ->
                        currentTrades.insert(
                                copyTrade("duplicate-sequence-" + UUID.randomUUID(), "BUY")
                                        .withExecutionSequence(2)));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE sim_trades SET execution_sequence=0 WHERE sim_trade_id=?",
                                buy.simTradeId()));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE sim_trades SET execution_sequence=-1 WHERE sim_trade_id=?",
                                buy.simTradeId()));
        var rollback =
                copyTrade("rollback-sequence-" + UUID.randomUUID(), "BUY").withExecutionSequence(4);
        assertThrows(
                DataAccessException.class,
                () ->
                        new TransactionTemplate(transactionManager)
                                .executeWithoutResult(
                                        status -> {
                                            currentTrades.insert(rollback);
                                            currentTrades.insert(
                                                    copyTrade(
                                                                    "duplicate-in-transaction-"
                                                                            + UUID.randomUUID(),
                                                                    "BUY")
                                                            .withExecutionSequence(2));
                                        }));
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM sim_trades WHERE sim_trade_id=?",
                        Long.class,
                        rollback.simTradeId()));
    }

    @Test
    void valuationOrderingAndPositiveUniqueConstraintRoundTripWithoutChangingTime() {
        var first = copyPnl("z-valuation-" + UUID.randomUUID()).withValuationSequence(1);
        var second = copyPnl("a-valuation-" + UUID.randomUUID()).withValuationSequence(2);
        currentPnl.insert(first);
        currentPnl.insert(second);
        var rows =
                currentPnl.listByBacktestRunId(runId).stream()
                        .filter(row -> row.valuationSequence() != null)
                        .toList();
        assertEquals(
                List.of(1L, 2L), rows.stream().map(SimPnlSnapshot::valuationSequence).toList());
        assertEquals(first.snapshotTime(), rows.get(0).snapshotTime());
        assertEquals(first.snapshotTime(), rows.get(1).snapshotTime());
        assertThrows(
                DataAccessException.class,
                () ->
                        currentPnl.insert(
                                copyPnl("duplicate-valuation-" + UUID.randomUUID())
                                        .withValuationSequence(2)));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE sim_pnl_snapshots SET valuation_sequence=0 WHERE sim_pnl_snapshot_id=?",
                                first.simPnlSnapshotId()));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE sim_pnl_snapshots SET valuation_sequence=-1 WHERE sim_pnl_snapshot_id=?",
                                first.simPnlSnapshotId()));
        var rollback = copyPnl("rollback-valuation-" + UUID.randomUUID()).withValuationSequence(3);
        assertThrows(
                DataAccessException.class,
                () ->
                        new TransactionTemplate(transactionManager)
                                .executeWithoutResult(
                                        status -> {
                                            currentPnl.insert(rollback);
                                            currentPnl.insert(
                                                    copyPnl(
                                                                    "duplicate-valuation-in-transaction-"
                                                                            + UUID.randomUUID())
                                                            .withValuationSequence(2));
                                        }));
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM sim_pnl_snapshots WHERE sim_pnl_snapshot_id=?",
                        Long.class,
                        rollback.simPnlSnapshotId()));
    }

    private SimTrade copyTrade(String id, String side) {
        var row = originalTrade;
        return new SimTrade(
                id,
                row.simOrderId(),
                runId,
                row.symbol(),
                side,
                row.quantity(),
                row.tradePrice(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                row.tradedAt(),
                row.createdAt(),
                row.updatedAt());
    }

    private SimPnlSnapshot copyPnl(String id) {
        var row = originalPnl;
        return new SimPnlSnapshot(
                id,
                runId,
                row.snapshotTime(),
                row.cashBalance(),
                row.positionMarketValue(),
                row.realizedPnl(),
                row.unrealizedPnl(),
                row.totalFee(),
                row.totalSlippage(),
                row.equity(),
                row.netPnl(),
                row.createdAt());
    }

    @TestConfiguration
    static class LegacyStores {
        @Bean
        @Primary
        BacktestNativeFeeMigrationPostgresIntegrationTest.LegacyTradeStore v17TradeStore(
                JdbcTemplate jdbc) {
            return new BacktestNativeFeeMigrationPostgresIntegrationTest.LegacyTradeStore(jdbc);
        }

        @Bean
        @Primary
        LegacyPnlStore v17PnlStore(JdbcTemplate jdbc) {
            return new LegacyPnlStore(jdbc);
        }
    }

    /** 只在旧schema资格夹具使用十二列SQL；不让当前代码查询尚未执行的V18列。 */
    static final class LegacyPnlStore
            implements SimPnlSnapshotRepository, SimPnlSnapshotQueryRepository {
        private final JdbcTemplate jdbc;

        LegacyPnlStore(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public void insert(SimPnlSnapshot row) {
            if (row.discountAssetCurrency() != null)
                throw new IllegalArgumentException("legacy writer cannot drop discount facts");
            jdbc.update(
                    "INSERT INTO sim_pnl_snapshots(sim_pnl_snapshot_id,backtest_run_id,snapshot_time,cash_balance,position_market_value,realized_pnl,unrealized_pnl,total_fee,total_slippage,equity,net_pnl,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    row.simPnlSnapshotId(),
                    row.backtestRunId(),
                    Timestamp.from(row.snapshotTime()),
                    row.cashBalance(),
                    row.positionMarketValue(),
                    row.realizedPnl(),
                    row.unrealizedPnl(),
                    row.totalFee(),
                    row.totalSlippage(),
                    row.equity(),
                    row.netPnl(),
                    Timestamp.from(row.createdAt()));
        }

        public List<SimPnlSnapshot> listByBacktestRunId(String run) {
            return jdbc.query(
                    "SELECT sim_pnl_snapshot_id,backtest_run_id,snapshot_time,cash_balance,position_market_value,realized_pnl,unrealized_pnl,total_fee,total_slippage,equity,net_pnl,created_at FROM sim_pnl_snapshots WHERE backtest_run_id=? ORDER BY snapshot_time,sim_pnl_snapshot_id",
                    (row, index) ->
                            new SimPnlSnapshot(
                                    row.getString("sim_pnl_snapshot_id"),
                                    row.getString("backtest_run_id"),
                                    row.getTimestamp("snapshot_time").toInstant(),
                                    row.getBigDecimal("cash_balance"),
                                    row.getBigDecimal("position_market_value"),
                                    row.getBigDecimal("realized_pnl"),
                                    row.getBigDecimal("unrealized_pnl"),
                                    row.getBigDecimal("total_fee"),
                                    row.getBigDecimal("total_slippage"),
                                    row.getBigDecimal("equity"),
                                    row.getBigDecimal("net_pnl"),
                                    row.getTimestamp("created_at").toInstant()),
                    run);
        }
    }

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
