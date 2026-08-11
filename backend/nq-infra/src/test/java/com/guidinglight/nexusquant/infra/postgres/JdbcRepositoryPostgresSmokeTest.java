package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.audit.infra.jdbc.JdbcAuditLogRepository;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcMarketdataBarRepository;
import com.guidinglight.nexusquant.risk.infra.jdbc.JdbcRiskEventRepository;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateTransitionException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunIllegalTransitionAuditWriter;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Repository-only PostgreSQL smoke for Batch 2C.
 *
 * <p>Why:
 * RecordingJdbcTemplate unit tests validate SQL shape, but they cannot prove the migrated PostgreSQL
 * schema accepts JSONB casts, TIMESTAMPTZ values, unique constraints, or ON CONFLICT clauses. This
 * smoke stays in nq-infra, uses only explicit CI datasource properties, and never starts the
 * nq-app Spring Boot context or any profile runner.
 */
class JdbcRepositoryPostgresSmokeTest {

    private static final String REQUIRED_PROPERTY = "nq.postgres.smoke.required";
    private static final String URL_PROPERTY = "nq.postgres.smoke.url";
    private static final String USER_PROPERTY = "nq.postgres.smoke.user";
    private static final String PASSWORD_PROPERTY = "nq.postgres.smoke.password";

    @Test
    void shouldInsertAndReadSelectedRepositoriesAgainstMigratedPostgresSchema() {
        SmokeConfig config = SmokeConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(),
                    "PostgreSQL repository smoke is disabled without nq.postgres.smoke.* properties");
        }
        assertTrue(config.configured(), "Missing required nq.postgres.smoke.* properties");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(config.url());
        dataSource.setUsername(config.user());
        dataSource.setPassword(config.password());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        String smokeRunId = "ci-repo-smoke-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            assertShadowRunSchema(jdbcTemplate);
            assertAuditLogRepository(jdbcTemplate, smokeRunId);
            assertRiskEventRepository(jdbcTemplate, smokeRunId);
            assertMarketdataBarRepository(jdbcTemplate, smokeRunId);

            status.setRollbackOnly();
        });

        assertShadowRunIllegalTransitionAuditRequiresNew(
                jdbcTemplate,
                transactionTemplate,
                transactionManager,
                smokeRunId
        );
    }

    private static void assertShadowRunSchema(JdbcTemplate jdbcTemplate) {
        assertTableExists(jdbcTemplate, "shadow_runs");
        assertTableExists(jdbcTemplate, "shadow_run_events");
        assertTableExists(jdbcTemplate, "shadow_run_snapshots");
        assertTableExists(jdbcTemplate, "shadow_consistency_reports");

        assertConstraintExists(jdbcTemplate, "chk_shadow_runs_status");
        assertConstraintExists(jdbcTemplate, "chk_shadow_run_events_event_type");
        assertConstraintExists(jdbcTemplate, "chk_shadow_run_snapshots_type");
        assertConstraintExists(jdbcTemplate, "chk_shadow_consistency_reports_status");
        assertConstraintExists(jdbcTemplate, "uq_shadow_run_snapshots_run_type_seq");

        assertIndexExists(jdbcTemplate, "idx_shadow_runs_idempotency_key");
        assertIndexExists(jdbcTemplate, "idx_shadow_runs_status_created_at");
        assertIndexExists(jdbcTemplate, "idx_shadow_runs_strategy_dataset");
        assertIndexExists(jdbcTemplate, "idx_shadow_runs_paper_run_id");
        assertIndexExists(jdbcTemplate, "idx_shadow_run_events_run_created_at");
        assertIndexExists(jdbcTemplate, "idx_shadow_run_snapshots_run_type_sequence");
        assertIndexExists(jdbcTemplate, "idx_shadow_consistency_reports_run_generated");
        assertIndexExists(jdbcTemplate, "idx_shadow_consistency_reports_paper_generated");

        assertTableCommentExists(jdbcTemplate, "shadow_runs");
        assertTableCommentExists(jdbcTemplate, "shadow_run_events");
        assertTableCommentExists(jdbcTemplate, "shadow_run_snapshots");
        assertTableCommentExists(jdbcTemplate, "shadow_consistency_reports");
        assertColumnCommentExists(jdbcTemplate, "shadow_runs", "status");
        assertColumnCommentExists(jdbcTemplate, "shadow_runs", "idempotency_key");
        assertColumnCommentExists(jdbcTemplate, "shadow_run_snapshots", "payload");
        assertColumnCommentExists(jdbcTemplate, "shadow_consistency_reports", "comparison_status");
    }

    private static void assertTableExists(JdbcTemplate jdbcTemplate, String tableName) {
        String regclass = jdbcTemplate.queryForObject("SELECT to_regclass(?)::text", String.class, "public." + tableName);
        assertEquals(tableName, regclass);
    }

    private static void assertConstraintExists(JdbcTemplate jdbcTemplate, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?",
                Integer.class,
                constraintName
        );
        assertEquals(1, count);
    }

    private static void assertIndexExists(JdbcTemplate jdbcTemplate, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                Integer.class,
                indexName
        );
        assertEquals(1, count);
    }

    private static void assertTableCommentExists(JdbcTemplate jdbcTemplate, String tableName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT obj_description(to_regclass(?), 'pg_class') IS NOT NULL",
                Boolean.class,
                "public." + tableName
        );
        assertEquals(Boolean.TRUE, exists);
    }

    private static void assertColumnCommentExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                        SELECT col_description(to_regclass(?), ordinal_position) IS NOT NULL
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = ?
                          AND column_name = ?
                        """,
                Boolean.class,
                "public." + tableName,
                tableName,
                columnName
        );
        assertEquals(Boolean.TRUE, exists);
    }

    private static void assertShadowRunIllegalTransitionAuditRequiresNew(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            DataSourceTransactionManager transactionManager,
            String smokeRunId
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcShadowRunFactRepository repository = new JdbcShadowRunFactRepository(
                jdbcTemplate,
                objectMapper,
                new JdbcShadowRunIllegalTransitionAuditWriter(jdbcTemplate, objectMapper, transactionManager),
                new com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcAdmissionMutationCoordinator(
                        jdbcTemplate,
                        transactionManager,
                        256
                )
        );
        String suffix = smokeRunId.substring(Math.max(0, smokeRunId.length() - 12));
        Long accountId = null;
        String strategyId = "ci-shadow-audit-strategy-" + suffix;
        String strategyCode = "CI_SHADOW_AUDIT_" + suffix;
        String strategyVersionId = "ci-shadow-audit-version-" + suffix;
        UUID datasetId = UUID.randomUUID();
        UUID shadowRunId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-07-06T00:00:00Z");
        Instant endTime = startTime.plusSeconds(60);

        try {
            accountId = jdbcTemplate.queryForObject(
                    """
                            INSERT INTO accounts (account_code, venue, status)
                            VALUES (?, ?, ?)
                            RETURNING account_id
                            """,
                    Long.class,
                    "ci-shadow-" + suffix,
                    "PAPER",
                    "ACTIVE"
            );
            jdbcTemplate.update(
                    """
                            INSERT INTO strategy_definitions (
                                strategy_id, strategy_code, strategy_name, strategy_type, exchange_code,
                                account_id, trade_env, enabled, config_snapshot, version
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, '{}'::jsonb, 1)
                            """,
                    strategyId,
                    strategyCode,
                    "CI shadow illegal transition audit",
                    "SHADOW_AUDIT_SMOKE",
                    "PAPER",
                    accountId,
                    "SIM"
            );
            jdbcTemplate.update(
                    """
                            INSERT INTO strategy_versions (
                                strategy_version_id, strategy_code, version, version_name, status,
                                param_snapshot_json, config_snapshot_json, source_snapshot_json, checksum, created_by
                            ) VALUES (?, ?, 1, ?, 'ACTIVE', '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, ?, ?)
                            """,
                    strategyVersionId,
                    strategyCode,
                    "CI shadow audit smoke",
                    "sha256-ci-shadow-audit-" + suffix,
                    "ci-repo-smoke"
            );
            jdbcTemplate.update(
                    """
                            INSERT INTO marketdata_datasets (
                                dataset_id, dataset_name, exchange_code, market_type, symbol, "interval",
                                start_time, end_time, status, quality_status, source, created_by, request_json
                            ) VALUES (?, ?, 'OKX', 'SPOT', 'BTC-USDT', '1m', ?, ?, 'READY', 'OK', ?, ?, '{}'::jsonb)
                            """,
                    datasetId,
                    "ci-shadow-audit-dataset-" + suffix,
                    Timestamp.from(startTime),
                    Timestamp.from(endTime),
                    "CI_REPO_SMOKE",
                    "ci-repo-smoke"
            );

            repository.create(new ShadowRun(
                    shadowRunId,
                    strategyVersionId,
                    datasetId,
                    null,
                    null,
                    null,
                    ShadowRunStatus.COMPLETED,
                    startTime,
                    endTime,
                    JsonNodeFactory.instance.objectNode().put("mode", "NO_SIDE_EFFECT_LOCAL_ONLY"),
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                    "req-shadow-audit-smoke",
                    "ci-shadow-audit-idem-" + suffix,
                    smokeRunId,
                    JsonNodeFactory.instance.arrayNode().add("terminal-state-fixture"),
                    JsonNodeFactory.instance.arrayNode(),
                    JsonNodeFactory.instance.arrayNode().add("manual-review"),
                    0,
                    startTime,
                    startTime,
                    null,
                    null,
                    endTime
            ));

            transactionTemplate.executeWithoutResult(status -> {
                ShadowRunStateTransitionException ex = assertThrows(
                        ShadowRunStateTransitionException.class,
                        () -> repository.updateStatus(
                                shadowRunId,
                                ShadowRunStatus.RUNNING,
                                0,
                                "RUN_REQUESTED",
                                "completed run cannot restart",
                                "req-shadow-audit-smoke",
                                smokeRunId
                        )
                );
                assertEquals("SHADOW_RUN_TERMINAL_STATE_LOCKED", ex.reasonCode());
                status.setRollbackOnly();
            });

            Map<String, Object> runRow = jdbcTemplate.queryForMap(
                    "SELECT status, version FROM shadow_runs WHERE id = ?",
                    shadowRunId
            );
            assertEquals(ShadowRunStatus.COMPLETED.name(), runRow.get("status"));
            assertEquals(0L, ((Number) runRow.get("version")).longValue());

            Map<String, Object> eventRow = jdbcTemplate.queryForMap(
                    """
                            SELECT event_type, from_status, to_status, reason_code, request_id, trace_id,
                                   metadata::text AS metadata
                            FROM shadow_run_events
                            WHERE shadow_run_id = ?
                              AND event_type = 'ILLEGAL_STATE_TRANSITION_ATTEMPT'
                            ORDER BY created_at DESC
                            LIMIT 1
                            """,
                    shadowRunId
            );
            assertEquals("ILLEGAL_STATE_TRANSITION_ATTEMPT", eventRow.get("event_type"));
            assertEquals(ShadowRunStatus.COMPLETED.name(), eventRow.get("from_status"));
            assertEquals(ShadowRunStatus.RUNNING.name(), eventRow.get("to_status"));
            assertEquals("SHADOW_RUN_TERMINAL_STATE_LOCKED", eventRow.get("reason_code"));
            assertEquals("req-shadow-audit-smoke", eventRow.get("request_id"));
            assertEquals(smokeRunId, eventRow.get("trace_id"));
            assertTrue(String.valueOf(eventRow.get("metadata")).contains("rejectedTransition"));
        } finally {
            jdbcTemplate.update("DELETE FROM shadow_run_events WHERE shadow_run_id = ?", shadowRunId);
            jdbcTemplate.update("DELETE FROM shadow_runs WHERE id = ?", shadowRunId);
            jdbcTemplate.update("DELETE FROM strategy_versions WHERE strategy_version_id = ?", strategyVersionId);
            jdbcTemplate.update("DELETE FROM marketdata_datasets WHERE dataset_id = ?", datasetId);
            jdbcTemplate.update("DELETE FROM strategy_definitions WHERE strategy_id = ?", strategyId);
            if (accountId != null) {
                jdbcTemplate.update("DELETE FROM accounts WHERE account_id = ?", accountId);
            }
        }
    }

    private static void assertAuditLogRepository(JdbcTemplate jdbcTemplate, String smokeRunId) {
        JdbcAuditLogRepository repository = new JdbcAuditLogRepository(jdbcTemplate, new ObjectMapper());

        repository.append(
                "ci-repo-smoke",
                "append",
                "ci-repo-smoke-actor",
                smokeRunId,
                Map.of("smokeRunId", smokeRunId, "fixture", "fake")
        );

        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM audit_logs
                        WHERE trace_id = ?
                          AND detail_json ->> 'smokeRunId' = ?
                        """,
                Integer.class,
                smokeRunId,
                smokeRunId
        );
        assertEquals(1, count);
    }

    private static void assertRiskEventRepository(JdbcTemplate jdbcTemplate, String smokeRunId) {
        JdbcRiskEventRepository repository = new JdbcRiskEventRepository(jdbcTemplate);

        repository.append(
                "ORDER",
                "ci-repo-smoke-order",
                RiskDecision.REJECT,
                "ci.repo.smoke",
                RiskSeverity.LOW,
                smokeRunId
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        SELECT decision, severity, reason
                        FROM risk_events
                        WHERE trace_id = ?
                        """,
                smokeRunId
        );
        assertEquals("REJECT", row.get("decision"));
        assertEquals("LOW", row.get("severity"));
        assertEquals("ci.repo.smoke", row.get("reason"));
    }

    private static void assertMarketdataBarRepository(JdbcTemplate jdbcTemplate, String smokeRunId) {
        JdbcMarketdataBarRepository repository = new JdbcMarketdataBarRepository(jdbcTemplate);
        String symbol = "BTCUSDT_" + smokeRunId.substring(smokeRunId.length() - 8);
        Instant openTime = Instant.parse("2026-06-15T00:00:00Z");
        Instant closeTime = Instant.parse("2026-06-15T00:00:59Z");
        Instant ingestedAt = Instant.parse("2026-06-15T00:01:00Z");

        var insertStats = repository.upsertBars(List.of(new HistoricalBar(
                "CI_REPO",
                "SPOT",
                symbol,
                BarInterval.ONE_MINUTE,
                openTime,
                closeTime,
                new BigDecimal("100.00000000"),
                new BigDecimal("101.00000000"),
                new BigDecimal("99.00000000"),
                new BigDecimal("100.50000000"),
                new BigDecimal("12.00000000"),
                new BigDecimal("1206.00000000"),
                4L,
                "OK",
                "{\"smokeRunId\":\"" + smokeRunId + "\"}"
        )), "CI_REPO_SMOKE", ingestedAt);
        assertEquals(1, insertStats.insertedCount());
        assertEquals(0, insertStats.updatedCount());

        var updateStats = repository.upsertBars(List.of(new HistoricalBar(
                "CI_REPO",
                "SPOT",
                symbol,
                BarInterval.ONE_MINUTE,
                openTime,
                closeTime,
                new BigDecimal("100.00000000"),
                new BigDecimal("102.00000000"),
                new BigDecimal("99.00000000"),
                new BigDecimal("101.50000000"),
                new BigDecimal("13.00000000"),
                new BigDecimal("1319.50000000"),
                5L,
                "OK",
                "{\"smokeRunId\":\"" + smokeRunId + "\"}"
        )), "CI_REPO_SMOKE", ingestedAt.plusSeconds(60));
        assertEquals(0, updateStats.insertedCount());
        assertEquals(1, updateStats.updatedCount());

        BigDecimal closePrice = jdbcTemplate.queryForObject(
                """
                        SELECT close_price
                        FROM marketdata_bars
                        WHERE exchange_code = ?
                          AND market_type = ?
                          AND symbol = ?
                          AND "interval" = ?
                          AND open_time = ?
                        """,
                BigDecimal.class,
                "CI_REPO",
                "SPOT",
                symbol,
                BarInterval.ONE_MINUTE.wireValue(),
                java.sql.Timestamp.from(openTime)
        );
        assertNotNull(closePrice);
        assertEquals(0, new BigDecimal("101.50000000").compareTo(closePrice));

        String payloadRunId = jdbcTemplate.queryForObject(
                """
                        SELECT raw_payload_json ->> 'smokeRunId'
                        FROM marketdata_bars
                        WHERE exchange_code = ?
                          AND market_type = ?
                          AND symbol = ?
                          AND "interval" = ?
                          AND open_time = ?
                        """,
                String.class,
                "CI_REPO",
                "SPOT",
                symbol,
                BarInterval.ONE_MINUTE.wireValue(),
                java.sql.Timestamp.from(openTime)
        );
        assertEquals(smokeRunId, payloadRunId);
    }

    private record SmokeConfig(String url, String user, String password, boolean required) {
        static SmokeConfig fromSystemProperties() {
            return new SmokeConfig(
                    property(URL_PROPERTY),
                    property(USER_PROPERTY),
                    property(PASSWORD_PROPERTY),
                    Boolean.parseBoolean(property(REQUIRED_PROPERTY))
            );
        }

        boolean configured() {
            return !url.isBlank() && !user.isBlank() && !password.isBlank();
        }
    }

    private static String property(String name) {
        return System.getProperty(name, "").trim();
    }
}
