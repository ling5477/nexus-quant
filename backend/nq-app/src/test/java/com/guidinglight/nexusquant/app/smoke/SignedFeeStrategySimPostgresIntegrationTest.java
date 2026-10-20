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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 合成已闭盘bar经正式研究、评估、发布与Risk/Execution进入canonical SIM；不联网，不读真实凭据。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@SpringBootTest(
        classes = NexusQuantApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ContextConfiguration(initializers = NqAppContextPostgresSmokeTest.NoOutboundInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = SignedFeeStrategySimPostgresIntegrationTest.Cleanup.class,
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
            "nq.security.secret=FAKE-PLACEHOLDER-CONTROLLED-JWT-SIGNING-KEY"
        })
class SignedFeeStrategySimPostgresIntegrationTest {
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
                // 本夹具使用当前完整schema；旧标量经济断言不改变，不替换历史验收库。
                .cleanDisabled(true)
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

    @Test
    void signedAssumptionsFlowFromFrozenBacktestThroughRiskExecutionAndLedger() {
        assertEquals(
                "ENGAGED",
                jdbc.queryForObject(
                        "SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'",
                        String.class));
        for (String rate : List.of("0.001", "0", "-0.001", "0.000000001")) {
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
            // 冻结费率不能在决策落库时悄悄舍入为零；成交费用仍按正式八位金额精度消费。
            assertEquals(
                    0,
                    d(rate).compareTo(decision.feeRate()),
                    "Frozen scalar fee precision: requested="
                            + rate
                            + ", persisted="
                            + decision.feeRate());
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
}
