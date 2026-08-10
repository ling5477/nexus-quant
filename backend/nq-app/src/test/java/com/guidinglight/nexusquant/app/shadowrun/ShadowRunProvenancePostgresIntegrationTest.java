package com.guidinglight.nexusquant.app.shadowrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunIdempotencyConflictException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunIllegalTransitionAuditWriter;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * GateX-2 provenance migration 在显式本地 disposable PostgreSQL 上的 fresh/upgrade 回归。
 *
 * <p>测试只接受 localhost/127.0.0.1/::1，并只创建、删除随机 {@code gatex2_*} schema；未提供
 * properties 时普通 Maven 回归跳过。显式 focused run 必须将 required 设为 true，防止把未执行误报为通过。
 */
class ShadowRunProvenancePostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.shadow-provenance.postgres.required";
    private static final String URL_PROPERTY = "nq.shadow-provenance.postgres.url";
    private static final String USER_PROPERTY = "nq.shadow-provenance.postgres.user";
    private static final String PASSWORD_PROPERTY = "nq.shadow-provenance.postgres.password";
    private static final String DIGEST = "a".repeat(64);
    private static final Instant START = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void freshDatabaseShouldMigrateFromV1ToV36AndPreserveProvenanceAcrossLifecycleUpdates() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("fresh");
        try {
            migrate(config, schema, null);
            JdbcTemplate jdbc = jdbc(config, schema);
            assertEquals("36", currentFlywayVersion(jdbc));
            assertSchemaContract(jdbc);

            Fixture fixture = seedFixture(jdbc, "fresh");
            JdbcShadowRunFactRepository repository = repository(jdbc);
            ShadowRun stopped = repository.create(releaseBoundRun(fixture, "stopped"));
            assertBinding(stopped, fixture.publishId(), DIGEST, ShadowRunReleaseBindingMode.RELEASE_BOUND);
            ShadowRun conflicting = copyWithProvenance(stopped, fixture.publishId(), "b".repeat(64));
            assertThrows(ShadowRunIdempotencyConflictException.class, () -> repository.create(conflicting));
            assertBinding(
                    repository.findById(stopped.id()).orElseThrow(),
                    fixture.publishId(),
                    DIGEST,
                    ShadowRunReleaseBindingMode.RELEASE_BOUND
            );
            transitionAndAssert(repository, stopped, List.of(
                    ShadowRunStatus.PRECHECKING,
                    ShadowRunStatus.READY,
                    ShadowRunStatus.RUNNING,
                    ShadowRunStatus.STOP_REQUESTED,
                    ShadowRunStatus.STOPPED
            ));

            ShadowRun completed = repository.create(releaseBoundRun(fixture, "completed"));
            transitionAndAssert(repository, completed, List.of(
                    ShadowRunStatus.PRECHECKING,
                    ShadowRunStatus.READY,
                    ShadowRunStatus.RUNNING,
                    ShadowRunStatus.COMPLETED
            ));
            assertConstraintRejections(jdbc, fixture);
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    void existingV35DatabaseShouldUpgradeWithoutBackfillAndReadLegacyBindingModes() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("upgrade");
        try {
            migrate(config, schema, MigrationVersion.fromVersion("35"));
            JdbcTemplate before = jdbc(config, schema);
            Fixture fixture = seedFixture(before, "upgrade");
            UUID unboundId = insertLegacyRun(before, fixture, null, "legacy-unbound");
            UUID publishOnlyId = insertLegacyRun(before, fixture, fixture.publishId(), "legacy-publish");

            migrate(config, schema, null);
            JdbcTemplate upgraded = jdbc(config, schema);
            assertEquals("36", currentFlywayVersion(upgraded));
            assertSchemaContract(upgraded);
            assertEquals(2, upgraded.queryForObject(
                    "SELECT COUNT(*) FROM shadow_runs WHERE artifact_digest IS NULL",
                    Integer.class
            ));

            JdbcShadowRunFactRepository repository = repository(upgraded);
            ShadowRun unbound = repository.findById(unboundId).orElseThrow();
            ShadowRun publishOnly = repository.findById(publishOnlyId).orElseThrow();
            assertBinding(unbound, null, null, ShadowRunReleaseBindingMode.LEGACY_UNBOUND);
            assertBinding(publishOnly, fixture.publishId(), null, ShadowRunReleaseBindingMode.LEGACY_PUBLISH_ONLY);
        } finally {
            dropSchema(config, schema);
        }
    }

    private static void assertSchemaContract(JdbcTemplate jdbc) {
        assertEquals("YES", jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = 'shadow_runs' "
                        + "AND column_name = 'artifact_digest'",
                String.class
        ));
        assertEquals(64, jdbc.queryForObject(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = 'shadow_runs' "
                        + "AND column_name = 'artifact_digest'",
                Integer.class
        ));
        for (String constraint : List.of(
                "chk_shadow_runs_artifact_digest_sha256",
                "chk_shadow_runs_artifact_requires_publish"
        )) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint "
                            + "WHERE conrelid = 'shadow_runs'::regclass AND conname = ? AND convalidated",
                    Integer.class,
                    constraint
            ));
        }
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT col_description(to_regclass('shadow_runs'), ordinal_position) IS NOT NULL "
                        + "FROM information_schema.columns WHERE table_schema = current_schema() "
                        + "AND table_name = 'shadow_runs' AND column_name = 'artifact_digest'",
                Boolean.class
        ));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() "
                        + "AND tablename = 'shadow_runs' AND indexdef ILIKE '%publish_id%' "
                        + "AND indexdef ILIKE '%artifact_digest%'",
                Integer.class
        ));
    }

    private static void assertConstraintRejections(JdbcTemplate jdbc, Fixture fixture) {
        for (String invalid : List.of(
                "a".repeat(63),
                "a".repeat(65),
                "A".repeat(64),
                "g".repeat(64),
                ""
        )) {
            assertThrows(DataAccessException.class,
                    () -> insertDirectRun(jdbc, fixture, fixture.publishId(), invalid, "invalid-" + UUID.randomUUID()));
        }
        assertThrows(DataAccessException.class,
                () -> insertDirectRun(jdbc, fixture, null, DIGEST, "missing-publish-" + UUID.randomUUID()));
    }

    private static void transitionAndAssert(
            JdbcShadowRunFactRepository repository,
            ShadowRun initial,
            List<ShadowRunStatus> transitions
    ) {
        long version = initial.version();
        for (ShadowRunStatus target : transitions) {
            repository.updateStatus(
                    initial.id(),
                    target,
                    version,
                    "GATEX2_POSTGRES_REGRESSION",
                    "local disposable PostgreSQL lifecycle regression",
                    "request-gatex2",
                    "trace-gatex2"
            );
            version++;
            ShadowRun reloaded = repository.findById(initial.id()).orElseThrow();
            assertEquals(target, reloaded.status());
            assertEquals(version, reloaded.version());
            assertBinding(reloaded, initial.publishId(), initial.artifactDigest(), initial.releaseBindingMode());
        }
    }

    private static void assertBinding(
            ShadowRun run,
            String publishId,
            String artifactDigest,
            ShadowRunReleaseBindingMode mode
    ) {
        assertEquals(publishId, run.publishId());
        assertEquals(artifactDigest, run.artifactDigest());
        assertEquals(mode, run.releaseBindingMode());
    }

    private static ShadowRun releaseBoundRun(Fixture fixture, String suffix) {
        return new ShadowRun(
                UUID.randomUUID(),
                fixture.strategyVersionId(),
                fixture.datasetId(),
                null,
                fixture.publishId(),
                DIGEST,
                null,
                ShadowRunStatus.CREATED,
                START,
                START.plusSeconds(60),
                JsonNodeFactory.instance.objectNode().put("mode", "NO_SIDE_EFFECT_LOCAL_ONLY"),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                "request-" + suffix,
                "gatex2-idempotency-" + suffix + "-" + UUID.randomUUID(),
                "trace-gatex2",
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode().add("manual-review"),
                0,
                START,
                START,
                null,
                null,
                null
        );
    }

    private static ShadowRun copyWithProvenance(ShadowRun source, String publishId, String artifactDigest) {
        return new ShadowRun(
                UUID.randomUUID(),
                source.strategyVersionId(),
                source.datasetId(),
                source.evaluationId(),
                publishId,
                artifactDigest,
                source.paperRunId(),
                source.status(),
                source.windowStart(),
                source.windowEnd(),
                source.sideEffectPolicy(),
                source.noOrderSubmission(),
                source.noCredentialAccess(),
                source.noPrivateEndpoint(),
                source.noLedgerMutation(),
                source.noAccountMutation(),
                source.noExternalPrivateIo(),
                source.authorizationBoundary(),
                source.requestId(),
                source.idempotencyKey(),
                source.traceId(),
                source.blockers(),
                source.warnings(),
                source.nextSteps(),
                source.version(),
                source.createdAt(),
                source.updatedAt(),
                source.startedAt(),
                source.stoppedAt(),
                source.completedAt()
        );
    }

    private static UUID insertLegacyRun(JdbcTemplate jdbc, Fixture fixture, String publishId, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO shadow_runs (
                            id, strategy_version_id, dataset_id, publish_id,
                            status, idempotency_key, trace_id
                        ) VALUES (?, ?, ?, ?, 'CREATED', ?, ?)
                        """,
                id,
                fixture.strategyVersionId(),
                fixture.datasetId(),
                publishId,
                "gatex2-direct-" + suffix,
                "trace-gatex2"
        );
        return id;
    }

    private static UUID insertDirectRun(
            JdbcTemplate jdbc,
            Fixture fixture,
            String publishId,
            String artifactDigest,
            String suffix
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO shadow_runs (
                            id, strategy_version_id, dataset_id, publish_id, artifact_digest,
                            status, idempotency_key, trace_id
                        ) VALUES (?, ?, ?, ?, ?, 'CREATED', ?, ?)
                        """,
                id,
                fixture.strategyVersionId(),
                fixture.datasetId(),
                publishId,
                artifactDigest,
                "gatex2-direct-" + suffix,
                "trace-gatex2"
        );
        return id;
    }

    private static Fixture seedFixture(JdbcTemplate jdbc, String suffix) {
        String unique = suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        Long accountId = jdbc.queryForObject(
                "INSERT INTO accounts (account_code, venue, status) VALUES (?, 'PAPER', 'ACTIVE') RETURNING account_id",
                Long.class,
                "gatex2-account-" + unique
        );
        assertTrue(accountId != null && accountId > 0);
        String strategyId = "gatex2-strategy-" + unique;
        String strategyCode = "GATEX2_" + unique.replace('-', '_').toUpperCase();
        String strategyVersionId = "gatex2-version-" + unique;
        String researchId = "gatex2-research-" + unique;
        String backtestConfigId = "gatex2-backtest-config-" + unique;
        String backtestRunId = "gatex2-backtest-run-" + unique;
        String publishId = "gatex2-publish-" + unique;
        UUID datasetId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO strategy_definitions (strategy_id, strategy_code, strategy_name, strategy_type, "
                        + "exchange_code, account_id, trade_env, enabled, config_snapshot, version) "
                        + "VALUES (?, ?, 'GateX2 PostgreSQL fixture', 'SHADOW_PROVENANCE', 'PAPER', ?, 'SIM', "
                        + "FALSE, '{}'::jsonb, 1)",
                strategyId,
                strategyCode,
                accountId
        );
        jdbc.update(
                "INSERT INTO research_configs (research_config_id, source_strategy_id, name, strategy_snapshot) "
                        + "VALUES (?, ?, 'GateX2 fixture', '{}'::jsonb)",
                researchId,
                strategyId
        );
        jdbc.update(
                "INSERT INTO backtest_configs (backtest_config_id, research_config_id, name) VALUES (?, ?, ?)",
                backtestConfigId,
                researchId,
                "GateX2 fixture"
        );
        jdbc.update(
                "INSERT INTO backtest_runs (backtest_run_id, backtest_config_id, research_config_id, "
                        + "source_strategy_id, status, strategy_snapshot, backtest_config_snapshot, requested_at) "
                        + "VALUES (?, ?, ?, ?, 'SUCCEEDED', '{}'::jsonb, '{}'::jsonb, ?)",
                backtestRunId,
                backtestConfigId,
                researchId,
                strategyId,
                java.sql.Timestamp.from(START)
        );
        jdbc.update(
                "INSERT INTO strategy_versions (strategy_version_id, strategy_code, version, version_name, status, "
                        + "checksum, created_by) VALUES (?, ?, 1, 'GateX2 fixture', 'ACTIVE', ?, 'gatex2-test')",
                strategyVersionId,
                strategyCode,
                DIGEST
        );
        jdbc.update(
                "INSERT INTO backtest_publish_records (publish_record_id, backtest_run_id, research_config_id, "
                        + "backtest_config_id, source_strategy_id, publish_status, publish_name, strategy_version_id) "
                        + "VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', 'GateX2 fixture', ?)",
                publishId,
                backtestRunId,
                researchId,
                backtestConfigId,
                strategyId,
                strategyVersionId
        );
        jdbc.update(
                "INSERT INTO marketdata_datasets (dataset_id, dataset_name, exchange_code, market_type, symbol, "
                        + "\"interval\", start_time, end_time, status, quality_status, source, created_by, request_json) "
                        + "VALUES (?, ?, 'OKX', 'SPOT', 'BTC-USDT', '1m', ?, ?, 'READY', 'OK', "
                        + "'GATEX2_TEST', 'gatex2-test', '{}'::jsonb)",
                datasetId,
                "gatex2-dataset-" + unique,
                java.sql.Timestamp.from(START),
                java.sql.Timestamp.from(START.plusSeconds(60))
        );
        return new Fixture(strategyVersionId, datasetId, publishId);
    }

    private static JdbcShadowRunFactRepository repository(JdbcTemplate jdbc) {
        DriverManagerDataSource dataSource = (DriverManagerDataSource) jdbc.getDataSource();
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        ObjectMapper objectMapper = new ObjectMapper();
        return new JdbcShadowRunFactRepository(
                jdbc,
                objectMapper,
                new JdbcShadowRunIllegalTransitionAuditWriter(jdbc, objectMapper, transactionManager)
        );
    }

    private static void migrate(PostgresConfig config, String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(config.url(), config.user(), config.password())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true);
        if (target != null) {
            configuration.target(target);
        }
        Flyway flyway = configuration.load();
        flyway.migrate();
        flyway.validate();
    }

    private static JdbcTemplate jdbc(PostgresConfig config, String schema) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(withCurrentSchema(config.url(), schema));
        dataSource.setUsername(config.user());
        dataSource.setPassword(config.password());
        return new JdbcTemplate(dataSource);
    }

    private static String withCurrentSchema(String url, String schema) {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    private static String currentFlywayVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class
        );
    }

    private static String randomSchema(String suffix) {
        return "gatex2_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void dropSchema(PostgresConfig config, String schema) {
        if (schema == null || !schema.matches("gatex2_(fresh|upgrade)_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("refusing to drop non-GateX2 schema");
        }
        jdbc(config, "public").execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
    }

    private static PostgresConfig requireLocalDisposableConfig() {
        PostgresConfig config = PostgresConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "local disposable PostgreSQL properties are not configured");
        }
        assertTrue(config.configured(), "missing required local disposable PostgreSQL properties");
        assertTrue(config.localhost(), "GateX-2 PostgreSQL test refuses non-local database URLs");
        return config;
    }

    private record Fixture(String strategyVersionId, UUID datasetId, String publishId) {
    }

    private record PostgresConfig(String url, String user, String password, boolean required) {

        private static PostgresConfig fromSystemProperties() {
            return new PostgresConfig(
                    property(URL_PROPERTY),
                    property(USER_PROPERTY),
                    property(PASSWORD_PROPERTY),
                    Boolean.parseBoolean(property(REQUIRED_PROPERTY))
            );
        }

        private boolean configured() {
            return !url.isBlank() && !user.isBlank() && !password.isBlank();
        }

        private boolean localhost() {
            try {
                String normalized = url.replaceFirst("^jdbc:", "");
                String host = URI.create(normalized).getHost();
                return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }

        private static String property(String name) {
            return System.getProperty(name, "").trim();
        }
    }
}
