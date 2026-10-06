package com.guidinglight.nexusquant.app.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.application.service.FixtureMarketdataRegistry;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.research.api.dto.BacktestEvaluationResponse;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionPersistenceService;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionService;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.domain.*;
import com.guidinglight.nexusquant.research.domain.backtest.*;
import com.guidinglight.nexusquant.research.domain.eval.*;
import com.guidinglight.nexusquant.research.infra.backtest.jdbc.*;
import com.guidinglight.nexusquant.research.infra.eval.jdbc.JdbcBacktestEvaluationReportRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcAdmissionMutationCoordinator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** 隔离 PG16/current Flyway 的真实回测、权益、评估和 JSONB 读回；不访问交易所。 */
class ResearchValidityPostgresIntegrationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final String DATASET = "{\"provider\":\"fixture\",\"datasetId\":\"research-fixture\",\"exchangeCode\":\"OKX\",\"symbol\":\"BTC-USDT\",\"interval\":\"1m\",\"resourcePath\":\"synthetic\"}";
    private static final String PARAMS = "{\"window\":2,\"investedExposure\":\"1\"}";
    private static final String VERSION = "{\"strategyVersionId\":\"version\",\"checksum\":\"research-checksum\",\"paramSnapshotJson\":" + PARAMS + ",\"sourceSnapshotJson\":{\"executable\":\"SPOT_SMA_TARGET_V1\"}}";
    private static final String CONFIG = "{\"startTime\":\"2026-01-01T00:00:00Z\",\"endTime\":\"2026-01-01T00:09:59Z\",\"initialCapital\":\"100\",\"executionSpec\":{\"quantityStep\":\"0.0001\",\"priceTick\":\"0.01\",\"minimumQuantity\":\"0.0001\",\"minimumNotional\":\"1\",\"feeRate\":\"0.001\",\"slippageBps\":\"10\"}}";

    @Test
    @Timeout(120)
    void frozenRunReevaluatesFromIdenticalFactsAndFutureMutationPreservesEarlierOosAndIs() throws Exception {
        String url = System.getProperty("nq.research-validity.pg.url");
        if (url == null && "true".equals(System.getenv("CI"))) url = System.getenv("NQ_DB_URL");
        boolean required = Boolean.getBoolean("nq.research-validity.pg.required") || "true".equals(System.getenv("CI"));
        if (url == null) {
            assertFalse(required, "explicit disposable PG16 URL required");
            assumeTrue(false, "isolated PostgreSQL not configured");
        }
        url = url.replace("//localhost:", "//127.0.0.1:");
        assertTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+"), "disposable loopback URL required");
        String user = System.getProperty("nq.research-validity.pg.user", System.getenv().getOrDefault("NQ_DB_USER", "postgres"));
        String password = System.getProperty("nq.research-validity.pg.password", System.getenv().getOrDefault("NQ_DB_PASSWORD", ""));
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String schema = "research_validity_" + UUID.randomUUID().toString().replace("-", "");
        assertEquals(16, admin.queryForObject("SELECT current_setting('server_version_num')::int / 10000", Integer.class));
        try {
            var flyway = Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema)
                    .locations("classpath:db/migration").cleanDisabled(true).load();
            flyway.migrate();
            flyway.validate();
            assertEquals("59", flyway.info().current().getVersion().getVersion());
            var ds = new DriverManagerDataSource(url + "?currentSchema=" + schema, user, password);
            var jdbc = new JdbcTemplate(ds);
            seed(jdbc);
            var coordinator = new JdbcAdmissionMutationCoordinator(jdbc, new DataSourceTransactionManager(ds), 256);
            var runs = new JdbcBacktestRunRepository(jdbc, coordinator);
            var orders = new JdbcSimOrderRepository(jdbc);
            var trades = new JdbcSimTradeRepository(jdbc);
            var positions = new JdbcSimPositionRepository(jdbc);
            var snapshots = new JdbcSimPnlSnapshotRepository(jdbc);
            var reports = new JdbcBacktestEvaluationReportRepository(jdbc, MAPPER, coordinator);
            var runService = mock(BacktestRunService.class);
            when(runService.getByBacktestRunId(anyString())).thenAnswer(invocation -> runs.findByBacktestRunId(invocation.getArgument(0)).orElseThrow());
            var configs = mock(BacktestConfigService.class);
            when(configs.getByBacktestConfigId(anyString())).thenReturn(mock(BacktestConfig.class));
            var research = mock(ResearchConfigService.class);
            when(research.getByResearchConfigId(anyString())).thenReturn(mock(ResearchConfig.class));
            var persistence = new BacktestExecutionPersistenceService(runs, orders, trades, positions, snapshots);
            var proxy = new ProxyFactory(persistence);
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
            var transactionalPersistence = (BacktestExecutionPersistenceService) proxy.getProxy();
            var calculator = new EvaluationMetricCalculator(new DrawdownCalculator(), new SharpeCalculator(), new TradeOutcomeCalculator());
            var evaluations = new BacktestEvaluationService(runService, configs, orders, trades, positions, snapshots, calculator, reports);

            List<HistoricalBar> originalBars = bars(false);
            for (String id : List.of("run-a", "run-b", "run-future", "run-short")) {
                runs.insert(run(id));
                var input = id.equals("run-future") ? bars(true) : id.equals("run-short") ? originalBars.subList(0, 1) : originalBars;
                var execution = new BacktestExecutionService(query -> input, new FixtureMarketdataRegistry(),
                        runService, configs, research, transactionalPersistence, new BuiltinFixtureSignalPolicy(),
                        new ExecutionPricingPolicy(), new FeeModel(), new SlippageModel(), MAPPER);
                execution.startRun(id);
            }
            var a = evaluations.evaluate("run-a");
            var second = evaluations.evaluate("run-a");
            assertEquals(a.totalReturn(), second.totalReturn());
            assertEquals(a.researchValidity().full(), second.researchValidity().full());
            assertEquals(a.researchValidity().inSample(), second.researchValidity().inSample());
            assertEquals(a.researchValidity().outOfSample(), second.researchValidity().outOfSample());
            assertEquals(a.researchValidity().benchmark(), second.researchValidity().benchmark());
            verify(configs, times(4)).getByBacktestConfigId("config");
            var stored = reports.findByBacktestRunId("run-a").orElseThrow();
            assertEquals(second.researchValidity(), stored.researchValidity());
            assertEquals(stored.researchValidity(), reports.findByEvalReportId(stored.evalReportId()).orElseThrow().researchValidity());
            assertEquals(stored.researchValidity(), reports.listAll().stream().filter(report -> report.backtestRunId().equals("run-a")).findFirst().orElseThrow().researchValidity());
            var response = BacktestEvaluationResponse.from(stored);
            assertEquals("AVAILABLE", response.researchValidity().validationStatus());
            assertEquals("version", response.researchValidity().identity().strategyVersionId());
            assertEquals("research-fixture", response.researchValidity().identity().datasetId());
            assertEquals(0, a.totalReturn().compareTo(a.researchValidity().full().strategyReturn()));
            assertEquals(7, a.researchValidity().inSample().barCount());
            assertEquals(3, a.researchValidity().outOfSample().barCount());
            assertTrue(Instant.parse(a.researchValidity().inSample().endTime()).isBefore(Instant.parse(a.researchValidity().outOfSample().startTime())));
            assertTrue(a.totalFee().signum() > 0);
            assertTrue(a.totalSlippage().signum() > 0);
            var b = evaluations.evaluate("run-b");
            assertEquals(a.researchValidity().full(), b.researchValidity().full());
            assertEquals(a.researchValidity().inSample(), b.researchValidity().inSample());
            assertEquals(a.researchValidity().outOfSample(), b.researchValidity().outOfSample());
            assertEquals(a.researchValidity().benchmark(), b.researchValidity().benchmark());
            var future = evaluations.evaluate("run-future");
            assertEquals(a.researchValidity().inSample(), future.researchValidity().inSample());
            var prefixA = MAPPER.valueToTree(snapshots.listByBacktestRunId("run-a").subList(0, 9));
            var prefixFuture = MAPPER.valueToTree(snapshots.listByBacktestRunId("run-future").subList(0, 9));
            for (var tree : List.of(prefixA, prefixFuture)) tree.forEach(node -> { ((ObjectNode) node).remove("simPnlSnapshotId"); ((ObjectNode) node).remove("backtestRunId"); });
            assertEquals(prefixA, prefixFuture);
            assertNotEquals(a.researchValidity().outOfSample().strategyReturn(), future.researchValidity().outOfSample().strategyReturn());
            assertNotEquals(a.researchValidity().identity().barContentSha256(), future.researchValidity().identity().barContentSha256());
            BacktestRun completedRun = runs.findByBacktestRunId("run-a").orElseThrow();
            for (String field : List.of("backtestRunId", "strategyVersionId", "datasetId", "barContentSha256",
                    "strategyChecksum", "symbol", "interval")) {
                var summary = (ObjectNode) MAPPER.readTree(completedRun.summaryJson());
                ((ObjectNode) summary.path("researchFacts")).put(field, "wrong-identity");
                assertThrows(IllegalStateException.class, () -> ResearchValidityCalculator.facts(withSummary(completedRun, summary.toString())), field);
            }
            for (String field : List.of("initialCapital", "feeRate", "slippageBps")) {
                var summary = (ObjectNode) MAPPER.readTree(completedRun.summaryJson());
                ((ObjectNode) summary.path("researchFacts").path("assumptions")).put(field, new BigDecimal("0.123456789012345678"));
                assertThrows(IllegalStateException.class, () -> ResearchValidityCalculator.facts(withSummary(completedRun, summary.toString())), field);
            }
            var shortReport = evaluations.evaluate("run-short");
            assertEquals("INSUFFICIENT_DATA", shortReport.researchValidity().validationStatus());
            assertNull(shortReport.researchValidity().outOfSample());
            assertNull(shortReport.researchValidity().benchmark().benchmarkReturn());
            reports.upsert(second.withReportJson("{}"));
            assertEquals("NOT_AVAILABLE", BacktestEvaluationResponse.from(reports.findByBacktestRunId("run-a").orElseThrow()).researchValidity().validationStatus());
            reports.upsert(second);
            assertEquals(second.researchValidity(), reports.findByBacktestRunId("run-a").orElseThrow().researchValidity());
            for (String field : List.of("initialCapital", "feeRate", "slippageBps")) {
                var highConfig = (ObjectNode) MAPPER.readTree(CONFIG);
                BigDecimal precise = new BigDecimal(switch (field) {
                    case "initialCapital" -> "100.0000000000000000001";
                    case "feeRate" -> "0.0010000000000000001";
                    default -> "10.0000000000000000001";
                });
                if (field.equals("initialCapital")) highConfig.put(field, precise.toPlainString());
                else ((ObjectNode) highConfig.path("executionSpec")).put(field, precise.toPlainString());
                String id = "run-precise-" + field;
                var base = run(id);
                runs.insert(new BacktestRun(id, base.backtestConfigId(), base.researchConfigId(), base.sourceStrategyId(),
                        base.strategySnapshot(), base.strategyVersionId(), base.strategyVersionSnapshotJson(), base.paramSnapshotJson(),
                        highConfig.toString(), highConfig.toString(), base.datasetSnapshotJson(), base.status(),
                        base.requestedAt(), null, null, null, null, "{}", base.createdAt(), base.updatedAt()));
                var execution = new BacktestExecutionService(query -> originalBars, new FixtureMarketdataRegistry(),
                        runService, configs, research, transactionalPersistence, new BuiltinFixtureSignalPolicy(),
                        new ExecutionPricingPolicy(), new FeeModel(), new SlippageModel(), MAPPER);
                execution.startRun(id);
                var high = evaluations.evaluate(id);
                var highAgain = evaluations.evaluate(id);
                assertEquals(high.researchValidity().full(), highAgain.researchValidity().full());
                assertEquals(highAgain.researchValidity(), reports.findByBacktestRunId(id).orElseThrow().researchValidity());
                var assumptions = high.researchValidity().assumptions();
                assertEquals(0, precise.compareTo(switch (field) {
                    case "initialCapital" -> assumptions.requestedInitialCapital();
                    case "feeRate" -> assumptions.feeRate();
                    default -> assumptions.slippageBps();
                }));
                assertEquals(assumptions.initialCapital(), high.researchValidity().benchmark().initialCapital());
                assertEquals(assumptions.initialCapital(), high.researchValidity().full().startingEquity());
            }
            System.out.println("RESEARCH_VALIDITY_PG16_PROVEN " + MAPPER.writeValueAsString(response.researchValidity()));
        } finally {
            // 仅删除本测试创建的随机 schema，保留其他运行和数据库。
            admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static BacktestRun run(String id) {
        return new BacktestRun(id, "config", "research", "strategy", "{}", "version", VERSION, PARAMS,
                CONFIG, CONFIG, DATASET, BacktestRunStatus.CREATED, START, null, null, null, null, "{}", START, START);
    }
    private static BacktestRun withSummary(BacktestRun run, String summary) {
        return new BacktestRun(run.backtestRunId(), run.backtestConfigId(), run.researchConfigId(), run.sourceStrategyId(),
                run.strategySnapshot(), run.strategyVersionId(), run.strategyVersionSnapshotJson(), run.paramSnapshotJson(),
                run.backtestConfigSnapshot(), run.configSnapshotJson(), run.datasetSnapshotJson(), run.status(),
                run.requestedAt(), run.startedAt(), run.finishedAt(), run.failureCode(), run.failureMessage(), summary,
                run.createdAt(), run.updatedAt());
    }
    private static List<HistoricalBar> bars(boolean mutateFuture) {
        var result = new ArrayList<HistoricalBar>();
        for (int i = 0; i < 10; i++) {
            var open = START.plusSeconds(i * 60L);
            var price = BigDecimal.valueOf(100 + i);
            var close = mutateFuture && i == 9 ? new BigDecimal("10") : price.add(BigDecimal.ONE);
            result.add(new HistoricalBar("OKX", "BTC-USDT", BarInterval.ONE_MINUTE, open, open.plusSeconds(59),
                    price, price.max(close), price.min(close), close, BigDecimal.ONE));
        }
        return result;
    }
    private static void seed(JdbcTemplate jdbc) {
        Long account = jdbc.queryForObject("INSERT INTO accounts(account_code,venue) VALUES('research','OKX') RETURNING account_id", Long.class);
        jdbc.update("INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env) VALUES('strategy','research','research','SPOT_SMA_TARGET_V1','OKX',?,'SIM')", account);
        jdbc.update("INSERT INTO strategy_versions(strategy_version_id,strategy_code,version,version_name,status,checksum) VALUES('version','research',1,'research','ACTIVE','research-checksum')");
        jdbc.update("INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot) VALUES('research','strategy','research','{}')");
        jdbc.update("INSERT INTO backtest_configs(backtest_config_id,research_config_id,name,config_json) VALUES('config','research','research',CAST(? AS jsonb))", CONFIG);
    }
}
