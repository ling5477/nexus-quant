package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.app.NexusQuantApplication;
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
import com.guidinglight.nexusquant.scheduler.paper.PaperMatchingService;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimDecisionRepository;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimRunService;
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
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** 合成已闭盘bar经正式研究、评估、发布与Risk/Execution进入canonical SIM；不联网，不读真实凭据。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@SpringBootTest(
        classes = {
            NexusQuantApplication.class,
            StrategySimFeeMigrationPostgresIntegrationTest.LegacyConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = NqAppContextPostgresSmokeTest.NoOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = StrategySimFeeMigrationPostgresIntegrationTest.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.sql.init.mode=never",
            "spring.task.scheduling.enabled=false",
            "nq.runtime.trading-components.enabled=true",
            "nq.strategy-sim.enabled=true",
            "nq.continuous-sim.enabled=false",
            "nq.public-marketdata.outbound.enabled=false",
            "nq.auth.bootstrap-admin.enabled=false",
            "nq.instrument.catalog-sync.enabled=false",
            "nq.okx.recovery.enabled=false",
            "nq.okx.ws.enabled=false",
            "nq.binance.ws.enabled=false",
            "nq.account.credentials.verification-mode=STRUCTURAL",
            "nq.account.performance.sampling-enabled=false",
            "nq.account.credentials.master-key=signed-fee-synthetic-master-key-123456789",
            "nq.security.issuer=nexus-quant-signed-fee-synthetic",
            "nq.security.secret=signed-fee-synthetic-secret-123456789"
        })
class StrategySimFeeMigrationPostgresIntegrationTest {
    @Autowired LegacyDecisionStore legacyDecisions;
    private static ReleaseBaselineTestDatabase database;
    @Autowired JdbcTemplate jdbc;
    @Autowired MarketdataBarRepository bars;
    @Autowired MarketdataDatasetService datasets;
    @Autowired StrategyDefinitionRepository definitions;
    @Autowired StrategyVersionService versions;
    @Autowired ResearchConfigService research;
    @Autowired BacktestConfigService configs;
    @Autowired BacktestRunService backtests;
    @Autowired BacktestExecutionService execution;
    @Autowired BacktestEvaluationService evaluations;
    @Autowired BacktestPublishService publishes;
    @Autowired StrategySimRunService sim;
    @Autowired PaperTradingRunService runs;
    @Autowired PaperMatchingService matching;
    @Autowired SimCashFundingPort funding;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        database = ReleaseBaselineTestDatabase.create();
        Flyway.configure()
                .dataSource(database.source())
                .locations("classpath:db/migration")
                // 研究成交的新原币列只在本随机测试库升级，原标量及canonical费用断言保持不变。
                .cleanDisabled(true)
                .target("11")
                .load()
                .migrate();
        registry.add("spring.datasource.url", database::url);
        registry.add(
                "spring.datasource.username", () -> System.getProperty("nq.postgres.smoke.user"));
        registry.add(
                "spring.datasource.password",
                () -> System.getProperty("nq.postgres.smoke.password"));
    }

    @Test
    void actualLegacyCanonicalFactsSurviveV11ToV12AndLegacyWriterRemainsCompatible() {
        assertEquals(
                "ENGAGED",
                jdbc.queryForObject(
                        "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                        String.class));
        for (String rate : List.of("0.001", "0", "-0.001")) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            List<HistoricalBar> input =
                    List.of(
                            bar(0, "100"),
                            bar(1, "101"),
                            bar(2, "102"),
                            bar(3, "103"),
                            bar(4, "104"));
            bars.upsertBars(input, "FIXTURE_SYNC", Instant.now());
            var dataset =
                    datasets.createDataset(
                            new CreateMarketdataDatasetCommand(
                                    "Signed fee bars " + suffix,
                                    "OKX",
                                    "SPOT",
                                    "BTC-USDT",
                                    "1m",
                                    input.getFirst().openTime(),
                                    input.getLast().closeTime(),
                                    "synthetic-test"));
            assertEquals("READY", dataset.status().name());
            String snapshot = datasets.buildDatasetSnapshot(dataset.datasetId());
            long source =
                    jdbc.queryForObject(
                            "INSERT INTO accounts(account_code,venue,status) VALUES (?,'OKX','ACTIVE') RETURNING account_id",
                            Long.class,
                            "signed-fee-source-" + suffix);
            String strategyId = "str-signed-fee-" + suffix, code = "signed-fee-sma-" + suffix;
            Instant now = Instant.now();
            definitions.insert(
                    new StrategyDefinition(
                            strategyId,
                            code,
                            "Signed fee synthetic SMA",
                            SpotSmaTargetStrategy.EXECUTABLE,
                            "OKX",
                            source,
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
                                    strategyId,
                                    "Signed fee research " + suffix,
                                    "synthetic",
                                    "{}",
                                    "{}",
                                    snapshot));
            String costs =
                    "{\"quantityStep\":\"0.0001\",\"priceTick\":\"0.01\",\"minimumQuantity\":\"0.0001\",\"minimumNotional\":\"5\",\"feeRate\":\""
                            + rate
                            + "\",\"slippageBps\":\"10\",\"costSource\":\"EXPERIMENT_ASSUMPTION\",\"feeAssumptionVersion\":\"signed-fee-test-v1\"}";
            var config =
                    configs.create(
                            new BacktestConfigCreateRequest(
                                    experiment.researchConfigId(),
                                    "Signed fee backtest " + suffix,
                                    "synthetic",
                                    input.getFirst().openTime(),
                                    input.getLast().closeTime(),
                                    d("100"),
                                    costs,
                                    "{}"));
            configs.bindDataset(
                    config.backtestConfigId(), dataset.datasetId().toString(), snapshot);
            configs.bindStrategyVersion(config.backtestConfigId(), version.strategyVersionId());
            var backtest = backtests.create(new BacktestRunStartRequest(config.backtestConfigId()));
            assertEquals(
                    "SUCCEEDED",
                    execution.startRun(backtest.backtestRunId()).resultStatus().name());
            assertEquals(
                    0,
                    d(rate).compareTo(
                                    jdbc.queryForObject(
                                            "SELECT (summary_json->'costAndRuleAssumptions'->>'feeRate')::numeric FROM backtest_runs WHERE backtest_run_id=?",
                                            BigDecimal.class,
                                            backtest.backtestRunId())));
            BigDecimal researchFee =
                    jdbc.queryForObject(
                            "SELECT (summary_json->>'totalFee')::numeric FROM backtest_runs WHERE backtest_run_id=?",
                            BigDecimal.class,
                            backtest.backtestRunId());
            assertEquals(d(rate).signum(), researchFee.signum());
            assertEquals(
                    "SUCCEEDED",
                    evaluations.evaluate(backtest.backtestRunId()).evaluationStatus().name());
            var publish =
                    publishes.publish(
                            new BacktestPublishRequest(
                                    backtest.backtestRunId(),
                                    "Signed fee publish " + suffix,
                                    version.strategyVersionId()));
            assertEquals("SUCCEEDED", publish.publishStatus().name());
            var created = sim.create(publish.publishRecordId(), d("100"), "synthetic-test");
            runs.start(created.paperRunId());
            sim.advance(created.paperRunId());
            sim.advance(created.paperRunId());
            var decision = sim.advance(created.paperRunId());
            assertEquals("ACCEPTED", decision.status());
            BigDecimal notional = decision.quantity().multiply(decision.executionPrice());
            assertTrue(
                    notional.add(
                                            notional.multiply(d(rate).max(BigDecimal.ZERO))
                                                    .setScale(8, RoundingMode.CEILING))
                                    .compareTo(d("100"))
                            <= 0);
            // 下一根bar在前单未成交时只能使用扣除本金预留后的现金，负费率不会让资金复用。
            sim.advance(created.paperRunId());
            assertEquals(1, sim.facts(created.paperRunId()).orders().size());
            matching.matchOnce(100);
            var facts = sim.facts(created.paperRunId());
            assertEquals(1, facts.trades().size());
            BigDecimal fee =
                    jdbc.queryForObject(
                            "SELECT fee FROM trades WHERE account_id=?",
                            BigDecimal.class,
                            created.canonicalAccountId());
            assertEquals(d(rate).signum(), fee.signum());
            assertEquals(
                    0,
                    d("100").subtract(notional)
                            .subtract(fee)
                            .compareTo(funding.cashBalance(created.canonicalAccountId())));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM (SELECT currency FROM ledger_entries WHERE account_id=? GROUP BY currency HAVING sum(delta)<>0) invalid",
                            Integer.class,
                            created.canonicalAccountId()));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM risk_events WHERE scope='ORDER' AND scope_id=? AND decision='ALLOW'",
                            Integer.class,
                            decision.orderId()));
            String frozen =
                    jdbc.queryForObject(
                            "SELECT config_snapshot_json::text FROM paper_trading_runs WHERE paper_run_id=?",
                            String.class,
                            created.paperRunId());
            matching.matchOnce(100);
            assertEquals(1, sim.facts(created.paperRunId()).trades().size());
            assertEquals(
                    frozen,
                    jdbc.queryForObject(
                            "SELECT config_snapshot_json::text FROM paper_trading_runs WHERE paper_run_id=?",
                            String.class,
                            created.paperRunId()));
        }
        // V11旧列writer通过正式业务路径形成经济事实，升级不能改变任何已有值、状态或关联。
        String before = fingerprint();
        var beforeDecisions =
                jdbc
                        .queryForList(
                                "SELECT paper_run_id FROM paper_trading_runs ORDER BY paper_run_id")
                        .stream()
                        .flatMap(
                                row ->
                                        legacyDecisions
                                                .listByRun((String) row.get("paper_run_id"))
                                                .stream())
                        .toList();
        var current =
                Flyway.configure()
                        .dataSource(database.source())
                        .locations("classpath:db/migration")
                        .cleanDisabled(true)
                        .target("12")
                        .load();
        assertEquals(1, current.migrate().migrationsExecuted);
        current.validate();
        assertEquals(0, current.migrate().migrationsExecuted);
        assertEquals(before, fingerprint());
        var newReader = new StrategySimDecisionRepository(jdbc);
        var afterDecisions =
                jdbc
                        .queryForList(
                                "SELECT paper_run_id FROM paper_trading_runs ORDER BY paper_run_id")
                        .stream()
                        .flatMap(
                                row ->
                                        newReader
                                                .listByRun((String) row.get("paper_run_id"))
                                                .stream())
                        .toList();
        assertEquals(beforeDecisions, afterDecisions);
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM strategy_sim_decisions WHERE fee_rate IS NULL OR fee_model_json IS NOT NULL",
                        Integer.class));
        // 同一旧writer在V12新列存在时继续创建旧标量决策；不需要虚构方向模型或改写历史回执。
        var anchor = beforeDecisions.getFirst();
        legacyDecisions.insert(
                new StrategySimDecisionRepository.DecisionWrite(
                        "a".repeat(64),
                        anchor.paperRunId(),
                        jdbc.queryForObject(
                                "SELECT canonical_account_id FROM paper_trading_runs WHERE paper_run_id=?",
                                Long.class,
                                anchor.paperRunId()),
                        anchor.strategyVersionId(),
                        anchor.strategyChecksum(),
                        Instant.parse("2026-09-26T00:00:00Z"),
                        Instant.parse("2026-09-26T00:00:59Z"),
                        null,
                        "b".repeat(64),
                        null,
                        "{}",
                        BigDecimal.ZERO,
                        "NO_SIGNAL",
                        null,
                        null,
                        null,
                        d("-0.001"),
                        BigDecimal.ZERO),
                "NO_SIGNAL",
                "NO_LATER_TRADABLE_EVENT");
        var reread =
                newReader
                        .findByWindow(anchor.paperRunId(), Instant.parse("2026-09-26T00:00:00Z"))
                        .orElseThrow();
        assertEquals(0, reread.feeRate().compareTo(d("-0.001")));
        assertEquals(null, reread.feeModelJson());
        String beforePrecision = fingerprintWithoutFeeRate();
        var ratesBefore = normalizedRates();
        var precision =
                Flyway.configure()
                        .dataSource(database.source())
                        .locations("classpath:db/migration")
                        .cleanDisabled(true)
                        .target("13")
                        .load();
        assertEquals(1, precision.migrate().migrationsExecuted);
        precision.validate();
        assertEquals(0, precision.migrate().migrationsExecuted);
        // 列扩大后尾零表示会变化；逐项核对费率数学值，其余金融/身份字段按原文本严格比较。
        assertEquals(ratesBefore, normalizedRates());
        assertEquals(beforePrecision, fingerprintWithoutFeeRate());
        legacyDecisions.insert(
                new StrategySimDecisionRepository.DecisionWrite(
                        "c".repeat(64),
                        anchor.paperRunId(),
                        jdbc.queryForObject(
                                "SELECT canonical_account_id FROM paper_trading_runs WHERE paper_run_id=?",
                                Long.class,
                                anchor.paperRunId()),
                        anchor.strategyVersionId(),
                        anchor.strategyChecksum(),
                        Instant.parse("2026-09-26T00:02:00Z"),
                        Instant.parse("2026-09-26T00:02:59Z"),
                        null,
                        "d".repeat(64),
                        null,
                        "{}",
                        BigDecimal.ZERO,
                        "NO_SIGNAL",
                        null,
                        null,
                        null,
                        d("0.000000001"),
                        BigDecimal.ZERO),
                "NO_SIGNAL",
                "NO_LATER_TRADABLE_EVENT");
        assertEquals(
                0,
                newReader
                        .findByWindow(anchor.paperRunId(), Instant.parse("2026-09-26T00:02:00Z"))
                        .orElseThrow()
                        .feeRate()
                        .compareTo(d("0.000000001")));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM orders WHERE trade_env='LIVE'", Integer.class));
        assertEquals(
                "ENGAGED",
                jdbc.queryForObject(
                        "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                        String.class));
    }

    private static HistoricalBar bar(int minute, String price) {
        Instant start = Instant.parse("2026-09-25T00:00:00Z").plusSeconds(minute * 60L);
        BigDecimal value = d(price);
        return new HistoricalBar(
                "OKX",
                "SPOT",
                "BTC-USDT",
                BarInterval.ONE_MINUTE,
                start,
                start.plusSeconds(59),
                value,
                value,
                value,
                value,
                BigDecimal.ONE,
                null,
                null,
                "OK",
                "{}",
                start.plusSeconds(59));
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /** Spring完成上下文关闭后只销毁本夹具创建的数据库，失败场景也不遗留临时库。 */
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

    private String fingerprint() {
        return jdbc.queryForObject(
                """
                SELECT jsonb_build_object(
                    'orders',(SELECT jsonb_agg(to_jsonb(o) ORDER BY order_id) FROM orders o),
                    'trades',(SELECT jsonb_agg(to_jsonb(t) ORDER BY trade_id) FROM trades t),
                    'ledger',(SELECT jsonb_agg(to_jsonb(e) ORDER BY entry_id) FROM ledger_entries e),
                    'positions',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM positions p),
                    'decisions',(SELECT jsonb_agg(to_jsonb(d)-'fee_model_json' ORDER BY decision_id) FROM strategy_sim_decisions d),
                    'runs',(SELECT jsonb_agg(to_jsonb(r) ORDER BY paper_run_id) FROM paper_trading_runs r),
                    'snapshots',(SELECT jsonb_agg(to_jsonb(s) ORDER BY snapshot_id) FROM account_snapshots s))::text
                """,
                String.class);
    }

    private String fingerprintWithoutFeeRate() {
        return jdbc.queryForObject(
                """
                SELECT jsonb_build_object(
                    'orders',(SELECT jsonb_agg(to_jsonb(o) ORDER BY order_id) FROM orders o),
                    'trades',(SELECT jsonb_agg(to_jsonb(t) ORDER BY trade_id) FROM trades t),
                    'ledger',(SELECT jsonb_agg(to_jsonb(e) ORDER BY entry_id) FROM ledger_entries e),
                    'positions',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM positions p),
                    'decisions',(SELECT jsonb_agg(to_jsonb(d)-'fee_rate' ORDER BY decision_id) FROM strategy_sim_decisions d),
                    'runs',(SELECT jsonb_agg(to_jsonb(r) ORDER BY paper_run_id) FROM paper_trading_runs r),
                    'snapshots',(SELECT jsonb_agg(to_jsonb(s) ORDER BY snapshot_id) FROM account_snapshots s))::text
                """,
                String.class);
    }

    private List<String> normalizedRates() {
        return jdbc.query(
                "SELECT decision_id,fee_rate FROM strategy_sim_decisions ORDER BY decision_id",
                (rs, n) ->
                        rs.getString(1)
                                + ":"
                                + (rs.getBigDecimal(2) == null
                                        ? "NULL"
                                        : rs.getBigDecimal(2)
                                                .stripTrailingZeros()
                                                .toPlainString()));
    }

    @TestConfiguration
    static class LegacyConfiguration {
        @Bean
        @Primary
        LegacyDecisionStore legacyDecisionStore(JdbcTemplate jdbc) {
            return new LegacyDecisionStore(jdbc);
        }
    }

    /** 固定V11列集的test-only writer/reader；只用于验证既有调用方式，不进入正式应用。 */
    static class LegacyDecisionStore extends StrategySimDecisionRepository {
        private final JdbcTemplate jdbc;

        LegacyDecisionStore(JdbcTemplate jdbc) {
            super(jdbc);
            this.jdbc = jdbc;
        }

        /** V11没有新增模型列；test-only旧查询只返回明确旧标量，不能用运行时捕获缺列异常回退。 */
        @Override
        public List<PendingOrder> pendingOrders(long accountId) {
            return jdbc.query(
                    """
                SELECT o.side,o.qty,o.symbol,d.execution_price,d.fee_rate,d.execution_open_time
                FROM orders o LEFT JOIN strategy_sim_decisions d ON d.order_id=o.order_id
                WHERE o.account_id=? AND o.status NOT IN ('FILLED','REJECTED','RISK_REJECTED','CANCELLED','FAILED')
                ORDER BY o.order_id LIMIT 501
                """,
                    (rs, n) ->
                            new PendingOrder(
                                    rs.getString("side"),
                                    rs.getBigDecimal("qty"),
                                    rs.getBigDecimal("execution_price"),
                                    rs.getBigDecimal("fee_rate"),
                                    null,
                                    rs.getString("symbol"),
                                    rs.getTimestamp("execution_open_time") == null
                                            ? null
                                            : rs.getTimestamp("execution_open_time").toInstant()),
                    accountId);
        }

        public void lockAccount(long accountId) {
            jdbc.queryForObject(
                    "SELECT account_id FROM accounts WHERE account_id=? FOR NO KEY UPDATE",
                    Long.class,
                    accountId);
        }

        public <T> T withAccountMutex(long accountId, Supplier<T> action) {
            // 独立连接只持有事务级 advisory 锁，不持有 FK 行锁；canonical 准入会开独立事务。
            try (Connection connection = jdbc.getDataSource().getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement lock =
                        connection.prepareStatement(
                                "SELECT pg_advisory_xact_lock(hashtextextended(?, 9251))")) {
                    lock.setQueryTimeout(10);
                    lock.setString(1, "strategy-sim-account:" + accountId);
                    lock.execute();
                }
                try {
                    T result = action.get();
                    connection.commit();
                    return result;
                } catch (RuntimeException | Error failure) {
                    connection.rollback();
                    throw failure;
                }
            } catch (SQLException failure) {
                throw new IllegalStateException("SIM_ACCOUNT_MUTEX_UNAVAILABLE", failure);
            }
        }

        public Optional<DecisionView> findByWindow(String paperRunId, Instant signalOpenTime) {
            List<DecisionView> rows =
                    jdbc.query(
                            """
                SELECT decision_id,paper_run_id,strategy_version_id,strategy_checksum,target_exposure,
                       input_sha256,execution_bar_sha256,
                       signal_open_time,signal_available_at,execution_open_time,status,reason,
                       side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id
                FROM strategy_sim_decisions WHERE paper_run_id=? AND signal_open_time=?
                """,
                            (rs, n) -> map(rs),
                            paperRunId,
                            Timestamp.from(signalOpenTime));
            return rows.stream().findFirst();
        }

        public Optional<DecisionView> findByOrderId(String orderId) {
            List<DecisionView> rows =
                    jdbc.query(
                            """
                SELECT decision_id,paper_run_id,strategy_version_id,strategy_checksum,target_exposure,
                       input_sha256,execution_bar_sha256,
                       signal_open_time,signal_available_at,execution_open_time,status,reason,
                       side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id
                FROM strategy_sim_decisions WHERE order_id=?
                """,
                            (rs, n) -> map(rs),
                            orderId);
            return rows.stream().findFirst();
        }

        public List<DecisionView> listByRun(String paperRunId) {
            return jdbc.query(
                    """
                SELECT decision_id,paper_run_id,strategy_version_id,strategy_checksum,target_exposure,
                       input_sha256,execution_bar_sha256,
                       signal_open_time,signal_available_at,execution_open_time,status,reason,
                       side,quantity,execution_price,fee_rate,slippage_bps,strategy_run_id,order_id
                FROM strategy_sim_decisions WHERE paper_run_id=? ORDER BY signal_open_time DESC LIMIT 500
                """,
                    (rs, n) -> map(rs),
                    paperRunId);
        }

        public void insert(DecisionWrite value, String status, String reason) {
            jdbc.update(
                    """
                INSERT INTO strategy_sim_decisions(decision_id,paper_run_id,canonical_account_id,
                    strategy_version_id,strategy_checksum,signal_open_time,signal_available_at,
                    execution_open_time,input_sha256,execution_bar_sha256,input_snapshot_json,
                    target_exposure,status,reason,side,quantity,execution_price,fee_rate,slippage_bps)
                VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?)
                """,
                    value.decisionId(),
                    value.paperRunId(),
                    value.accountId(),
                    value.strategyVersionId(),
                    value.strategyChecksum(),
                    Timestamp.from(value.signalOpenTime()),
                    Timestamp.from(value.signalAvailableAt()),
                    value.executionOpenTime() == null
                            ? null
                            : Timestamp.from(value.executionOpenTime()),
                    value.inputSha256(),
                    value.executionBarSha256(),
                    value.inputSnapshotJson(),
                    value.targetExposure(),
                    status,
                    reason,
                    value.side(),
                    value.quantity(),
                    value.executionPrice(),
                    value.feeRate(),
                    value.slippageBps());
        }

        public void complete(
                String decisionId,
                String status,
                String reason,
                String strategyRunId,
                String orderId) {
            if (jdbc.update(
                            """
                UPDATE strategy_sim_decisions SET status=?,reason=?,strategy_run_id=?,order_id=?
                WHERE decision_id=? AND status='NOT_TRADABLE' AND reason='DECIDING'
                """,
                            status,
                            reason,
                            strategyRunId,
                            orderId,
                            decisionId)
                    != 1) {
                throw new IllegalStateException("SIM_DECISION_COMPLETION_CONFLICT");
            }
        }

        private static DecisionView map(ResultSet rs) throws SQLException {
            return new DecisionView(
                    rs.getString("decision_id"),
                    rs.getString("paper_run_id"),
                    rs.getString("strategy_version_id"),
                    rs.getString("strategy_checksum"),
                    rs.getBigDecimal("target_exposure"),
                    rs.getString("input_sha256"),
                    rs.getString("execution_bar_sha256"),
                    rs.getTimestamp("signal_open_time").toInstant(),
                    rs.getTimestamp("signal_available_at").toInstant(),
                    rs.getTimestamp("execution_open_time") == null
                            ? null
                            : rs.getTimestamp("execution_open_time").toInstant(),
                    rs.getString("status"),
                    rs.getString("reason"),
                    rs.getString("side"),
                    rs.getBigDecimal("quantity"),
                    rs.getBigDecimal("execution_price"),
                    rs.getBigDecimal("fee_rate"),
                    rs.getBigDecimal("slippage_bps"),
                    rs.getString("strategy_run_id"),
                    rs.getString("order_id"));
        }
    }
}
