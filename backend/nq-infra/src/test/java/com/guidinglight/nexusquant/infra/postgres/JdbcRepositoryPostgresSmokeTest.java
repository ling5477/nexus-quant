package com.guidinglight.nexusquant.infra.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.audit.infra.jdbc.JdbcAuditLogRepository;
import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcMarketdataBarRepository;
import com.guidinglight.nexusquant.risk.infra.jdbc.JdbcRiskEventRepository;

import java.math.BigDecimal;
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
        TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        transactionTemplate.executeWithoutResult(status -> {
            String smokeRunId = "ci-repo-smoke-" + UUID.randomUUID();

            assertAuditLogRepository(jdbcTemplate, smokeRunId);
            assertRiskEventRepository(jdbcTemplate, smokeRunId);
            assertMarketdataBarRepository(jdbcTemplate, smokeRunId);

            status.setRollbackOnly();
        });
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
