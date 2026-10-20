package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimTradeRepository;
import com.guidinglight.nexusquant.research.domain.eval.port.SimTradeQueryRepository;
import com.guidinglight.nexusquant.research.infra.backtest.jdbc.JdbcSimTradeRepository;
import com.guidinglight.nexusquant.strategy.application.StrategyVersionService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import com.guidinglight.nexusquant.strategy.domain.SpotSmaTargetStrategy;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** V10真实旧列生产路径形成研究成交，再升级V11；不手写canonical订单/成交/账务，不修复Flyway历史。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@SpringBootTest(
        classes = {
            NexusQuantApplication.class,
            BacktestNativeFeeMigrationPostgresIntegrationTest.LegacyConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = NqAppContextPostgresSmokeTest.NoOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = BacktestNativeFeeMigrationPostgresIntegrationTest.Cleanup.class,
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
            "nq.account.credentials.master-key=legacy-native-fee-synthetic-master-key-123456789",
            "nq.security.issuer=legacy-native-fee-synthetic",
            "nq.security.secret=legacy-native-fee-synthetic-jwt-secret-123456789"
        })
class BacktestNativeFeeMigrationPostgresIntegrationTest {
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
    @Autowired LegacyTradeStore legacy;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        database = ReleaseBaselineTestDatabase.create();
        migration("10").migrate();
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

    @Test
    void generatedLegacyRowsKeepAllOriginalFactsAcrossV11AndOldWriterRemainsCompatible() {
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "legacy-fee-owner",
                                passwords.encode("synthetic-legacy-password"),
                                List.of("OPERATOR", "VIEWER"),
                                true)));
        long owner =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='legacy-fee-owner'", Long.class);
        var account =
                accounts.create(
                        owner,
                        new ExchangeAccountCreateCommand(
                                "OKX", "SIM", "Legacy migration synthetic", null));
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
                                            "{\"fixture\":\"legacy-fee-upgrade\"}",
                                            start.plusSeconds(59));
                                })
                        .toList();
        bars.upsertBars(input, "FIXTURE_SYNC", Instant.now());
        var dataset =
                datasets.createDataset(
                        new CreateMarketdataDatasetCommand(
                                "Legacy migration bars",
                                "OKX",
                                "SPOT",
                                "BTC-USDT",
                                "1m",
                                input.getFirst().openTime(),
                                input.getLast().closeTime(),
                                "synthetic-test"));
        String snapshot = datasets.buildDatasetSnapshot(dataset.datasetId()),
                code = "legacy-fee-" + UUID.randomUUID(),
                strategy = "legacy-strategy-" + UUID.randomUUID();
        Instant now = Instant.now();
        definitions.insert(
                new StrategyDefinition(
                        strategy,
                        code,
                        "Legacy synthetic SMA",
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
                                "Legacy migration research",
                                "synthetic",
                                "{}",
                                "{}",
                                snapshot));
        var config =
                configs.create(
                        new BacktestConfigCreateRequest(
                                experiment.researchConfigId(),
                                "Legacy migration backtest",
                                "synthetic",
                                input.getFirst().openTime(),
                                input.getLast().closeTime(),
                                new BigDecimal("100"),
                                "{\"costSource\":\"EXPERIMENT_ASSUMPTION\",\"quantityStep\":\"0.0001\",\"priceTick\":\"0.01\",\"minimumQuantity\":\"0.0001\",\"minimumNotional\":\"5\",\"slippageBps\":\"0\",\"feeRate\":\"0.001\"}",
                                "{}"));
        configs.bindDataset(config.backtestConfigId(), dataset.datasetId().toString(), snapshot);
        configs.bindStrategyVersion(config.backtestConfigId(), version.strategyVersionId());
        var run = runs.create(new BacktestRunStartRequest(config.backtestConfigId()));
        assertEquals("SUCCEEDED", execution.startRun(run.backtestRunId()).resultStatus().name());
        var before = legacy.listByBacktestRunId(run.backtestRunId());
        assertTrue(!before.isEmpty());
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM information_schema.columns WHERE table_name='sim_trades' AND column_name='fee_currency'",
                        Long.class));
        assertEquals(1, migration("11").migrate().migrationsExecuted);
        migration("11").validate();
        assertEquals(0, migration("11").migrate().migrationsExecuted);
        var after = new JdbcSimTradeRepository(jdbc).listByBacktestRunId(run.backtestRunId());
        assertEquals(before, after);
        for (var trade : after) {
            assertNull(trade.feeCurrency());
            assertNull(trade.feeNativeAmount());
            assertNull(trade.feePaymentStatus());
            assertNull(trade.feeValuationScale());
        }
        // 新schema上继续使用原十二列SQL生产第二个run，证明旧应用写入不会被新增约束拒绝。
        var second = runs.create(new BacktestRunStartRequest(config.backtestConfigId()));
        assertEquals("SUCCEEDED", execution.startRun(second.backtestRunId()).resultStatus().name());
        assertTrue(
                !new JdbcSimTradeRepository(jdbc)
                        .listByBacktestRunId(second.backtestRunId())
                        .isEmpty());
        for (String table : List.of("orders", "trades", "ledger_entries"))
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class));
    }

    @TestConfiguration
    static class LegacyConfiguration {
        @Bean
        @Primary
        LegacyTradeStore legacyTradeStore(JdbcTemplate jdbc) {
            return new LegacyTradeStore(jdbc);
        }
    }

    /** 仅测试透明复用V10的十二列存储SQL；输入由真实回测服务产生，拒绝吞掉新原币字段。 */
    static final class LegacyTradeStore implements SimTradeRepository, SimTradeQueryRepository {
        private final JdbcTemplate jdbc;

        LegacyTradeStore(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public void insert(SimTrade trade) {
            if (trade.feeCurrency() != null
                    || trade.feeNativeAmount() != null
                    || trade.feePaymentStatus() != null
                    || trade.feeValuationScale() != null)
                throw new IllegalArgumentException("legacy writer cannot drop native fee facts");
            jdbc.update(
                    "INSERT INTO sim_trades(sim_trade_id,sim_order_id,backtest_run_id,symbol,side,quantity,trade_price,fee_amount,slippage_amount,traded_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    trade.simTradeId(),
                    trade.simOrderId(),
                    trade.backtestRunId(),
                    trade.symbol(),
                    trade.side(),
                    trade.quantity(),
                    trade.tradePrice(),
                    trade.feeAmount(),
                    trade.slippageAmount(),
                    Timestamp.from(trade.tradedAt()),
                    Timestamp.from(trade.createdAt()),
                    Timestamp.from(trade.updatedAt()));
        }

        public List<SimTrade> listByBacktestRunId(String run) {
            return jdbc.query(
                    "SELECT sim_trade_id,sim_order_id,backtest_run_id,symbol,side,quantity,trade_price,fee_amount,slippage_amount,traded_at,created_at,updated_at FROM sim_trades WHERE backtest_run_id=? ORDER BY traded_at,sim_trade_id",
                    (row, index) ->
                            new SimTrade(
                                    row.getString("sim_trade_id"),
                                    row.getString("sim_order_id"),
                                    row.getString("backtest_run_id"),
                                    row.getString("symbol"),
                                    row.getString("side"),
                                    row.getBigDecimal("quantity"),
                                    row.getBigDecimal("trade_price"),
                                    row.getBigDecimal("fee_amount"),
                                    row.getBigDecimal("slippage_amount"),
                                    row.getTimestamp("traded_at").toInstant(),
                                    row.getTimestamp("created_at").toInstant(),
                                    row.getTimestamp("updated_at").toInstant()),
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
