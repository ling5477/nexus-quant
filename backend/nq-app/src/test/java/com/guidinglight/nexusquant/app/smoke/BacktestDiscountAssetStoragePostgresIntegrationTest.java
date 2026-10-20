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
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** V15正式标量研究父链升级当前V18；优惠行是有界存储夹具，只证明约束/重读/回滚，不冒充正式优惠定价证明。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@SpringBootTest(
        classes = {
            NexusQuantApplication.class,
            BacktestDiscountAssetStoragePostgresIntegrationTest.LegacyStores.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = NqAppContextPostgresSmokeTest.NoOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestExecutionListeners(
        listeners = BacktestDiscountAssetStoragePostgresIntegrationTest.Cleanup.class,
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
            "nq.account.credentials.master-key=discount-storage-synthetic-master-key-123456789",
            "nq.security.issuer=discount-storage-synthetic",
            "nq.security.secret=discount-storage-synthetic-jwt-secret-123456789"
        })
class BacktestDiscountAssetStoragePostgresIntegrationTest {
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
        database = ReleaseBaselineTestDatabase.create();
        migration("15").migrate();
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
                                "discount-storage-owner",
                                passwords.encode("synthetic-storage-password"),
                                List.of("OPERATOR", "VIEWER"),
                                true)));
        long owner =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='discount-storage-owner'", Long.class);
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
                code = "discount-storage-" + UUID.randomUUID(),
                strategy = "discount-storage-strategy-" + UUID.randomUUID();
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
        // 先保留V16落点与原币资料不完整拒绝，再升级当前JDBC所需的V18；不把两个版本职责混为一次旧证明。
        assertEquals(1, migration("16").migrate().migrationsExecuted);
        migration("16").validate();
        assertEquals(0, migration("16").migrate().migrationsExecuted);
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.columns WHERE table_name='sim_trades' AND column_name='execution_sequence'",
                        Long.class));
        assertThrows(
                DataAccessException.class,
                () ->
                        jdbc.update(
                                "UPDATE sim_trades SET fee_currency='BNB',fee_native_amount=0.1,fee_payment_status='DISCOUNT_ASSET_USED',fee_valuation_scale=8 WHERE sim_trade_id=?",
                                originalTrade.simTradeId()));
        assertEquals(2, migration("18").migrate().migrationsExecuted);
        migration("18").validate();
        assertEquals(0, migration("18").migrate().migrationsExecuted);
        currentTrades = new JdbcSimTradeRepository(jdbc);
        currentPnl = new JdbcSimPnlSnapshotRepository(jdbc);
        assertEquals(beforeTrades, currentTrades.listByBacktestRunId(runId));
        assertEquals(beforePnl, currentPnl.listByBacktestRunId(runId));
    }

    @Test
    void signedFeesAndZeroBalancePnlRoundTripOriginalSourceText() {
        for (String fee : List.of("0.1", "0", "-0.1")) {
            SimTrade trade = discountTrade("roundtrip-" + UUID.randomUUID(), fee);
            currentTrades.insert(trade);
            assertEquals(
                    trade.feePriceSourceJson(),
                    jdbc.queryForObject(
                            "SELECT fee_price_source_json FROM sim_trades WHERE sim_trade_id=?",
                            String.class,
                            trade.simTradeId()));
            SimTrade reread =
                    currentTrades.listByBacktestRunId(runId).stream()
                            .filter(row -> row.simTradeId().equals(trade.simTradeId()))
                            .findFirst()
                            .orElseThrow();
            equal(trade.feeNativeAmount(), reread.feeNativeAmount());
            equal(trade.feeAmount(), reread.feeAmount());
            assertEquals(trade.feePriceSourceSha256(), reread.feePriceSourceSha256());
            assertEquals(trade.feePriceSourceJson(), reread.feePriceSourceJson());
        }
        for (String balance : List.of("0", "1.9", "2.1")) {
            var snapshot = discountPnl("pnl-roundtrip-" + UUID.randomUUID(), balance);
            currentPnl.insert(snapshot);
            var reread =
                    currentPnl.listByBacktestRunId(runId).stream()
                            .filter(
                                    row ->
                                            row.simPnlSnapshotId()
                                                    .equals(snapshot.simPnlSnapshotId()))
                            .findFirst()
                            .orElseThrow();
            equal(snapshot.discountAssetBalance(), reread.discountAssetBalance());
            equal(snapshot.equity(), reread.equity());
            assertEquals(snapshot.discountPriceSourceJson(), reread.discountPriceSourceJson());
            assertEquals(snapshot.discountPriceSourceSha256(), reread.discountPriceSourceSha256());
        }
        for (String table : List.of("orders", "trades", "ledger_entries"))
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class));
    }

    @Test
    void databaseRejectsIncompleteMismatchedNonfiniteAndFutureFacts() {
        var trade = discountTrade("negative-fixture-" + UUID.randomUUID(), "0.1");
        currentTrades.insert(trade);
        for (String change :
                List.of(
                        "fee_price_source_sha256='" + "a".repeat(64) + "'",
                        "fee_price_source_json='{}'",
                        "fee_price_observed_at=NULL",
                        "fee_price_visible_at=traded_at+interval '1 second'",
                        "fee_currency='BTC'",
                        "fee_amount=fee_amount+1",
                        "fee_conversion_price='NaN'::numeric"))
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "UPDATE sim_trades SET " + change + " WHERE sim_trade_id=?",
                                    trade.simTradeId()));
        var snapshot = discountPnl("pnl-negative-fixture-" + UUID.randomUUID(), "1.9");
        currentPnl.insert(snapshot);
        for (String change :
                List.of(
                        "discount_asset_balance=-1",
                        "discount_price_source_json='{}'",
                        "discount_price_source_sha256='" + "b".repeat(64) + "'",
                        "discount_price_visible_at=snapshot_time+interval '1 second'",
                        "discount_asset_quote_value=discount_asset_quote_value+1",
                        "equity=equity+1",
                        "cash_balance='NaN'::numeric"))
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "UPDATE sim_pnl_snapshots SET "
                                            + change
                                            + " WHERE sim_pnl_snapshot_id=?",
                                    snapshot.simPnlSnapshotId()));
    }

    @Test
    void secondFactFailureRollsBackBothRepositoryWrites() {
        var trade = discountTrade("rollback-" + UUID.randomUUID(), "-0.1");
        var snapshot = discountPnl(originalPnl.simPnlSnapshotId(), "2.1");
        assertThrows(
                DataAccessException.class,
                () ->
                        new TransactionTemplate(transactionManager)
                                .executeWithoutResult(
                                        status -> {
                                            currentTrades.insert(trade);
                                            currentPnl.insert(snapshot);
                                        }));
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM sim_trades WHERE sim_trade_id=?",
                        Long.class,
                        trade.simTradeId()));
    }

    private SimTrade discountTrade(String id, String fee) {
        Instant at = originalTrade.tradedAt();
        String source = point(at);
        return new SimTrade(
                id,
                originalTrade.simOrderId(),
                runId,
                "BTC-USDT",
                originalTrade.side(),
                originalTrade.quantity(),
                originalTrade.tradePrice(),
                new BigDecimal(fee).multiply(new BigDecimal("20")).setScale(8),
                BigDecimal.ZERO,
                at,
                at,
                at,
                "BNB",
                new BigDecimal(fee),
                "DISCOUNT_ASSET_USED",
                8,
                new BigDecimal("20"),
                at.minusSeconds(1),
                at,
                DiscountAssetPriceSnapshot.sha256(source),
                source);
    }

    private SimPnlSnapshot discountPnl(String id, String balance) {
        Instant at = originalPnl.snapshotTime();
        String source = point(at);
        BigDecimal units = new BigDecimal(balance),
                value = units.multiply(new BigDecimal("20")).setScale(8);
        return new SimPnlSnapshot(
                id,
                runId,
                at,
                originalPnl.cashBalance(),
                originalPnl.positionMarketValue(),
                originalPnl.realizedPnl(),
                originalPnl.unrealizedPnl(),
                originalPnl.totalFee(),
                originalPnl.totalSlippage(),
                originalPnl.cashBalance().add(originalPnl.positionMarketValue()).add(value),
                originalPnl.netPnl().add(value),
                at,
                "BNB",
                units,
                value,
                new BigDecimal("20"),
                at.minusSeconds(1),
                at,
                DiscountAssetPriceSnapshot.sha256(source),
                source);
    }

    private static String point(Instant at) {
        return "{\"symbol\":\"BNB-USDT\",\"marketType\":\"SPOT\",\"closePrice\":\"20\",\"observedAt\":\""
                + at.minusSeconds(1)
                + "\",\"visibleAt\":\""
                + at
                + "\"}";
    }

    private static void equal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }

    @TestConfiguration
    static class LegacyStores {
        @Bean
        @Primary
        BacktestNativeFeeMigrationPostgresIntegrationTest.LegacyTradeStore v15TradeStore(
                JdbcTemplate jdbc) {
            return new BacktestNativeFeeMigrationPostgresIntegrationTest.LegacyTradeStore(jdbc);
        }

        @Bean
        @Primary
        LegacyPnlStore v15PnlStore(JdbcTemplate jdbc) {
            return new LegacyPnlStore(jdbc);
        }
    }

    /** 只在旧schema资格夹具使用十二列SQL；不让当前代码查询尚未执行的V16列。 */
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
