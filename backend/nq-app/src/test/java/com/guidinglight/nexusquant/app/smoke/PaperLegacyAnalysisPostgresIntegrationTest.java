package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView;
import com.guidinglight.nexusquant.research.infra.ResearchFeeReadScope;
import com.guidinglight.nexusquant.research.infra.eval.jdbc.JdbcBacktestEvaluationQueryPort;
import com.guidinglight.nexusquant.research.infra.jdbc.JdbcBacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.infra.paper.jdbc.JdbcPaperTradingRunRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.port.AdmissionMutationCoordinator;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import javax.sql.DataSource;

/** 真实PG验证来源/费用范围先于窗口、稳定顺序及批量发布范围；不是交易执行或资金结算证明。 */
class PaperLegacyAnalysisPostgresIntegrationTest {
    @Test
    void sourceAndFeeScopeApplyBeforeLimitWithoutChangingLegacyOrCanonicalFacts() throws Exception {
        String maintenance = System.getenv("TASK_LEGACY_ANALYSIS_MAINTENANCE_URL");
        assumeTrue(maintenance != null, "isolated PostgreSQL proof is opt-in");
        assertEquals("jdbc:postgresql://127.0.0.1:15442/postgres", maintenance);
        String user = System.getenv("TASK_LEGACY_ANALYSIS_DB_USER");
        String password = System.getenv("TASK_LEGACY_ANALYSIS_DB_PASSWORD");
        Path resultPath = Path.of(System.getenv("TASK_LEGACY_ANALYSIS_PG_RESULT")).toAbsolutePath();
        assertTrue(
                resultPath.startsWith(Path.of("E:/Project/nq-production-reconstruction-output")));
        assertTrue(Files.isDirectory(resultPath.getParent()) && !Files.exists(resultPath));
        String database = "legacy_analysis_" + UUID.randomUUID().toString().replace("-", "");
        String url = "jdbc:postgresql://127.0.0.1:15442/" + database;
        boolean created = false, passed = false;
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            try (Connection control = DriverManager.getConnection(maintenance, user, password)) {
                control.createStatement().execute("CREATE DATABASE " + database);
                created = true;
            }
            var flyway =
                    Flyway.configure()
                            .dataSource(url, user, password)
                            .locations("classpath:db/migration")
                            .load();
            flyway.migrate();
            flyway.validate();
            var source = new DriverManagerDataSource(url, user, password);
            var jdbc = new JdbcTemplate(source);
            jdbc.setQueryTimeout(10);
            seed(jdbc);
            seedEvaluationInputs(jdbc);
            String before = fingerprint(jdbc);
            var coordinator = mock(AdmissionMutationCoordinator.class);
            var runs = new JdbcPaperTradingRunRepository(jdbc, coordinator);
            var publishes = new JdbcBacktestPublishRecordRepository(jdbc, coordinator);
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            transaction.setReadOnly(true);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            transaction.setTimeout(10);
            transaction.executeWithoutResult(
                    status -> {
                        assertEquals(
                                "repeatable read",
                                jdbc.queryForObject("SHOW transaction_isolation", String.class));
                        assertEquals(
                                "on",
                                jdbc.queryForObject("SHOW transaction_read_only", String.class));
                        var ownerRows = runs.listLegacyForAnalysis(7, 201);
                        assertEquals(201, ownerRows.size());
                        assertEquals("legacy-owned", ownerRows.getFirst().paperRunId());
                        assertEquals("legacy-204", ownerRows.get(1).paperRunId());
                        assertEquals("legacy-005", ownerRows.getLast().paperRunId());
                        assertTrue(
                                ownerRows.stream()
                                        .allMatch(
                                                row ->
                                                        row.canonicalAccountId() == null
                                                                && row.tradeEnv().equals("SIM")));
                        assertFalse(
                                ownerRows.stream()
                                        .anyMatch(
                                                row ->
                                                        row.paperRunId().startsWith("foreign")
                                                                || row.paperRunId()
                                                                        .equals(
                                                                                "legacy-tampered")));
                        assertEquals(
                                List.of("legacy-owned"),
                                runs.listLegacyForAnalysis(7, 1).stream()
                                        .map(row -> row.paperRunId())
                                        .toList());
                        var otherRows = runs.listLegacyForAnalysis(9, 201);
                        assertEquals(201, otherRows.size());
                        assertTrue(
                                otherRows.stream()
                                        .allMatch(row -> row.paperRunId().startsWith("foreign-")));
                        assertEquals(
                                List.of("pub-owned", "pub-public"),
                                publishes
                                        .listVisibleByIds(
                                                7,
                                                List.of(
                                                        "pub-other",
                                                        "pub-owned",
                                                        "pub-public",
                                                        "pub-owned",
                                                        "pub-tampered"))
                                        .stream()
                                        .map(row -> row.publishRecordId())
                                        .toList());
                        assertTrue(publishes.listVisibleByIds(7, List.of()).isEmpty());
                        verifyEvaluationBatch(source, result);
                    });
            assertThrows(IllegalArgumentException.class, () -> runs.listLegacyForAnalysis(0, 20));
            assertThrows(IllegalArgumentException.class, () -> runs.listLegacyForAnalysis(7, 0));
            assertThrows(IllegalArgumentException.class, () -> runs.listLegacyForAnalysis(7, 202));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> publishes.listVisibleByIds(0, List.of("pub-public")));
            assertEquals(before, fingerprint(jdbc));
            assertEquals(
                    "24",
                    jdbc.queryForObject(
                            "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                            String.class));
            result.put("ownerWindow", 201);
            result.put("foreignRowsExcludedBeforeLimit", 300);
            result.put("sourceAndLiveRowsExcludedBeforeLimit", 3);
            result.put("tamperedFeeModeRejected", true);
            result.put("boundedPublishIdsAndOwner", true);
            result.put("readOnlyRepeatableRead", true);
            result.put("factsBeforeAfterEqual", true);
            passed = true;
        } finally {
            if (passed)
                try (Connection control =
                        DriverManager.getConnection(maintenance, user, password)) {
                    control.createStatement().execute("DROP DATABASE " + database);
                }
            result.put(
                    "status", passed ? "PASS_SCOPED_LEGACY_ANALYSIS_SOURCE_PG" : "FAILED_RETAINED");
            result.put("database", database);
            result.put(
                    "cleanup",
                    passed ? "DROPPED_AFTER_SUCCESS" : created ? "RETAINED" : "NOT_CREATED");
            result.put("actualHttpAppContext", false);
            Files.writeString(
                    resultPath,
                    new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result)
                            + "\n");
        }
    }

    /** 仅给读取反例提供隔离目录与合成历史记录；不插入canonical订单、成交或账务。 */
    static void seed(JdbcTemplate jdbc) {
        jdbc.execute(
                """
                INSERT INTO accounts(account_code,venue,status) VALUES('legacy-fixture-source','BINANCE','ACTIVE');
                INSERT INTO strategy_definitions(strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,trade_env,enabled,config_snapshot,version)
                    VALUES('legacy-source','legacy-source','Legacy source','SPOT_SMA_TARGET_V1','BINANCE',
                        (SELECT account_id FROM accounts WHERE account_code='legacy-fixture-source'),'SIM',false,'{}',1);
                INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot)
                    VALUES('legacy-research','legacy-source','Legacy research','{}');
                INSERT INTO backtest_configs(backtest_config_id,research_config_id,name)
                    VALUES('legacy-config','legacy-research','Legacy configuration');
                INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,
                    strategy_snapshot,backtest_config_snapshot,requested_at,config_snapshot_json)
                SELECT 'br-'||kind,'legacy-config','legacy-research','legacy-source','SUCCEEDED','{}','{}',now(),
                    CASE WHEN kind='public' THEN '{}'::jsonb ELSE jsonb_build_object('executionSpec',jsonb_build_object(
                        'costSource',CASE WHEN kind='tampered' THEN 'EXPERIMENT_ASSUMPTION' ELSE 'CURRENT_ACCOUNT_OBSERVATION' END,
                        'feeSourceOwnerUserId',CASE WHEN kind='other' THEN '9' ELSE '7' END,'feeObservationId','synthetic-private-marker')) END
                FROM unnest(ARRAY['public','owned','other','tampered']) kind;
                INSERT INTO backtest_publish_records(publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,publish_status,publish_name)
                SELECT 'pub-'||kind,'br-'||kind,'legacy-research','legacy-config','legacy-source','SUCCEEDED','Legacy '||kind
                FROM unnest(ARRAY['public','owned','other','tampered']) kind;
                INSERT INTO paper_trading_runs(paper_run_id,publish_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at)
                SELECT 'legacy-'||lpad(n::text,3,'0'),'pub-public','CREATED','SIM','BINANCE','SPOT','BTC-USDT','1h','synthetic',
                    '2026-10-01'::timestamptz,'2026-10-01'::timestamptz FROM generate_series(0,204) n;
                INSERT INTO paper_trading_runs(paper_run_id,publish_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at)
                SELECT 'foreign-'||lpad(n::text,3,'0'),'pub-other','CREATED','SIM','BINANCE','SPOT','BTC-USDT','1h','synthetic',
                    '2026-11-01'::timestamptz,'2026-11-01'::timestamptz FROM generate_series(0,299) n;
                INSERT INTO paper_trading_runs(paper_run_id,publish_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at)
                VALUES('legacy-owned','pub-owned','CREATED','SIM','BINANCE','SPOT','BTC-USDT','1h','synthetic','2026-11-02','2026-11-02'),
                    ('legacy-tampered','pub-tampered','CREATED','SIM','BINANCE','SPOT','BTC-USDT','1h','synthetic','2026-12-01','2026-12-01'),
                    ('legacy-live','pub-public','CREATED','LIVE','BINANCE','SPOT','BTC-USDT','1h','synthetic','2026-12-01','2026-12-01');
                INSERT INTO accounts(account_code,venue,status) VALUES('legacy-fixture-canonical-1','PAPER','ACTIVE'),('legacy-fixture-canonical-2','PAPER','ACTIVE');
                INSERT INTO paper_trading_runs(paper_run_id,publish_id,status,trade_env,exchange_code,market_type,symbol,interval_code,created_by,created_at,updated_at,canonical_account_id)
                SELECT 'canonical-'||n,'pub-public','CREATED','SIM','BINANCE','SPOT','BTC-USDT','1h','synthetic','2026-12-01','2026-12-01',
                    (SELECT account_id FROM accounts WHERE account_code='legacy-fixture-canonical-'||n)
                FROM generate_series(1,2) n;
                """);
    }

    private static String fingerprint(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                """
                SELECT md5(string_agg(value,'|' ORDER BY value)) FROM (
                    SELECT row_to_json(p)::text value FROM paper_trading_runs p
                    UNION ALL SELECT row_to_json(b)::text FROM backtest_runs b
                    UNION ALL SELECT row_to_json(p)::text FROM backtest_publish_records p
                    UNION ALL SELECT row_to_json(e)::text FROM backtest_eval_reports e
                ) facts
                """,
                String.class);
    }

    /** 只生成评估读取样本；不调用真实策略、外部账户或资金写入路径。 */
    private static void seedEvaluationInputs(JdbcTemplate jdbc) {
        jdbc.execute(
                """
                INSERT INTO backtest_runs(backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,
                    strategy_snapshot,backtest_config_snapshot,requested_at,config_snapshot_json)
                SELECT 'batch-run-'||n,'legacy-config','legacy-research','legacy-source','SUCCEEDED','{}','{}',now(),'{}'
                FROM generate_series(0,199) n;
                INSERT INTO backtest_eval_reports(eval_report_id,backtest_run_id,evaluation_status,total_return_rate,max_drawdown_rate)
                SELECT 'batch-eval-'||n,'batch-run-'||n,'SUCCEEDED',0.100000000000000001,-0.200000000000000001
                FROM generate_series(0,199) n;
                INSERT INTO backtest_eval_reports(eval_report_id,backtest_run_id,evaluation_status,total_return_rate)
                SELECT 'eval-'||kind,'br-'||kind,'SUCCEEDED',0.15
                FROM unnest(ARRAY['public','owned','other','tampered']) kind;
                """);
    }

    /** 真PG同时证明200来源单SQL、与单项投影等价及原有费用范围，不能仅凭方法名声明批量。 */
    private static void verifyEvaluationBatch(DataSource source, Map<String, Object> proof) {
        var counted = new CountingJdbc(source);
        var repository = new JdbcBacktestEvaluationQueryPort(counted);
        var fees = mock(ResearchFeeSourcePort.class);
        when(fees.currentReadOwner()).thenReturn(7L);
        ReflectionTestUtils.setField(repository, "feeScope", new ResearchFeeReadScope(fees));
        var ids = IntStream.range(0, 200).mapToObj(index -> "batch-run-" + index).toList();
        Map<String, BacktestEvaluationView> batch = repository.findByBacktestRunIds(ids);
        int batchQueries = counted.queries;
        assertEquals(1, batchQueries);
        assertEquals(200, batch.size());
        Map<String, BacktestEvaluationView> singles = new LinkedHashMap<>();
        for (String id : ids)
            repository.findByBacktestRunId(id).ifPresent(value -> singles.put(id, value));
        assertEquals(201, counted.queries);
        assertEquals(singles, batch);
        assertEquals(
                new BigDecimal("0.100000000000000001"),
                batch.get(ids.getFirst()).totalReturnRate());
        var visible =
                repository.findByBacktestRunIds(
                        List.of(
                                "br-public",
                                "br-owned",
                                "br-other",
                                "br-tampered",
                                "id') OR TRUE --"));
        assertEquals(Set.of("br-public", "br-owned"), visible.keySet());
        when(fees.currentReadOwner()).thenReturn(9L);
        assertEquals(
                Set.of("br-public", "br-other"),
                repository
                        .findByBacktestRunIds(
                                List.of("br-public", "br-owned", "br-other", "br-tampered"))
                        .keySet());
        when(fees.currentReadOwner()).thenReturn(-1L);
        assertEquals(
                Set.of("br-public"),
                repository
                        .findByBacktestRunIds(
                                List.of("br-public", "br-owned", "br-other", "br-tampered"))
                        .keySet());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        repository.findByBacktestRunIds(
                                IntStream.range(0, 201)
                                        .mapToObj(index -> "excess-" + index)
                                        .toList()));
        proof.put("evaluation200SourceQueries", batchQueries);
        proof.put("evaluationSingleProjectionEquality", true);
        proof.put("evaluationFeeOwnerNegativeAndTamperedScope", true);
    }

    private static final class CountingJdbc extends JdbcTemplate {
        private int queries;

        CountingJdbc(DataSource source) {
            super(source);
            setQueryTimeout(10);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... arguments) {
            queries++;
            return super.query(sql, mapper, arguments);
        }
    }
}
