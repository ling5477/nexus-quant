package com.guidinglight.nexusquant.app.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.infra.jdbc.JdbcBacktestPublishRecordRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcAdmissionMutationCoordinator;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * GateX-4B locator migration 在显式 localhost disposable PostgreSQL 上的 fresh/upgrade 回归。
 *
 * <p>测试只创建、删除随机 {@code gatex4b_*} schema；默认 Maven 回归未配置时跳过，focused run 必须设置
 * required=true，避免把未执行的 PostgreSQL 验证误报为通过。
 */
class BacktestPublishArtifactLocatorPostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.artifact-locator.postgres.required";
    private static final String URL_PROPERTY = "nq.artifact-locator.postgres.url";
    private static final String USER_PROPERTY = "nq.artifact-locator.postgres.user";
    private static final String PASSWORD_PROPERTY = "nq.artifact-locator.postgres.password";
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void freshDatabaseShouldMigrateFromV1ToV38AndEnforceLocatorContract() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("fresh");
        try {
            migrate(config, schema, null);
            JdbcTemplate jdbc = jdbc(config, schema);
            assertEquals("38", currentFlywayVersion(jdbc));
            assertSchemaContract(jdbc);

            JdbcBacktestPublishRecordRepository repository = repository(jdbc);
            Fixture first = seedFixture(jdbc, "fresh-first");
            BacktestPublishRecord stored = publishRecord(
                    first,
                    PublishStatus.SUCCEEDED,
                    "artifact_release_01",
                    "manifest_release_01.json"
            );
            repository.upsert(stored);
            assertBound(repository.findByPublishRecordId(first.publishId()).orElseThrow(),
                    "artifact_release_01", "manifest_release_01.json");

            repository.upsert(copyWithName(stored, "idempotent replay"));
            assertEquals("idempotent replay", repository.findByPublishRecordId(first.publishId()).orElseThrow().publishName());
            assertThrows(IllegalStateException.class, () -> repository.upsert(publishRecord(
                    first,
                    PublishStatus.SUCCEEDED,
                    "artifact_release_conflict",
                    "manifest_release_conflict.json"
            )));

            Fixture duplicateAllowed = seedFixture(jdbc, "fresh-duplicate");
            repository.upsert(publishRecord(
                    duplicateAllowed,
                    PublishStatus.SUCCEEDED,
                    "artifact_release_01",
                    "manifest_release_01.json"
            ));
            assertEquals(2, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM backtest_publish_records WHERE artifact_storage_key = ?",
                    Integer.class,
                    "artifact_release_01"
            ));

            assertConstraintRejections(jdbc);
            assertImmutabilityRejections(jdbc, first);
            printCapacityAndLockAssessment(jdbc, "fresh");
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    void existingV36DatabaseShouldUpgradeWithoutBackfillAndAllowOnlyFailedToSucceededFirstBinding() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("upgrade");
        try {
            migrate(config, schema, MigrationVersion.fromVersion("36"));
            JdbcTemplate before = jdbc(config, schema);
            Fixture legacySucceeded = seedFixture(before, "upgrade-succeeded");
            Fixture failedRetry = seedFixture(before, "upgrade-failed");
            insertLegacyPublish(before, legacySucceeded, "SUCCEEDED");
            insertLegacyPublish(before, failedRetry, "FAILED");

            migrate(config, schema, null);
            JdbcTemplate upgraded = jdbc(config, schema);
            assertEquals("38", currentFlywayVersion(upgraded));
            assertSchemaContract(upgraded);
            assertEquals(2, upgraded.queryForObject(
                    "SELECT COUNT(*) FROM backtest_publish_records "
                            + "WHERE artifact_storage_key IS NULL AND manifest_storage_key IS NULL",
                    Integer.class
            ));

            JdbcBacktestPublishRecordRepository repository = repository(upgraded);
            BacktestPublishRecord legacy = repository.findByPublishRecordId(legacySucceeded.publishId()).orElseThrow();
            assertEquals(
                    BacktestPublishRecord.ArtifactLocatorBindingStatus.LEGACY_ARTIFACT_UNBOUND,
                    legacy.artifactLocatorBindingStatus()
            );
            assertThrows(DataAccessException.class, () -> upgraded.update(
                    "UPDATE backtest_publish_records SET artifact_storage_key = ?, manifest_storage_key = ? "
                            + "WHERE publish_record_id = ?",
                    "late_artifact",
                    "late_manifest",
                    legacySucceeded.publishId()
            ));

            repository.upsert(publishRecord(
                    failedRetry,
                    PublishStatus.SUCCEEDED,
                    "retry_artifact",
                    "retry_manifest"
            ));
            assertBound(repository.findByPublishRecordId(failedRetry.publishId()).orElseThrow(),
                    "retry_artifact", "retry_manifest");
            printCapacityAndLockAssessment(upgraded, "upgrade");
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void concurrentFirstBindingShouldRejectDifferentPairAndAcceptSamePair() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("concurrency");
        try {
            migrate(config, schema, null);
            JdbcTemplate control = jdbc(config, schema);

            Fixture competing = seedFixture(control, "concurrent-competing");
            insertLegacyPublish(control, competing, "FAILED");
            List<BindingAttempt> competingAttempts = runConcurrentBindings(
                    config,
                    schema,
                    publishRecord(competing, PublishStatus.SUCCEEDED, "artifact_competing_a", "manifest_competing_a"),
                    publishRecord(competing, PublishStatus.SUCCEEDED, "artifact_competing_b", "manifest_competing_b")
            );
            assertEquals(1, competingAttempts.stream().filter(BindingAttempt.SUCCESS::equals).count());
            assertEquals(1, competingAttempts.stream().filter(BindingAttempt.CONFLICT::equals).count());
            BacktestPublishRecord competingFinal = repository(control)
                    .findByPublishRecordId(competing.publishId())
                    .orElseThrow();
            boolean pairA = "artifact_competing_a".equals(competingFinal.artifactStorageKey())
                    && "manifest_competing_a".equals(competingFinal.manifestStorageKey());
            boolean pairB = "artifact_competing_b".equals(competingFinal.artifactStorageKey())
                    && "manifest_competing_b".equals(competingFinal.manifestStorageKey());
            assertTrue(pairA || pairB, "final locator must be one complete competing pair");

            Fixture samePair = seedFixture(control, "concurrent-same");
            insertLegacyPublish(control, samePair, "FAILED");
            BacktestPublishRecord sameRecord = publishRecord(
                    samePair,
                    PublishStatus.SUCCEEDED,
                    "artifact_same_pair",
                    "manifest_same_pair"
            );
            List<BindingAttempt> samePairAttempts = runConcurrentBindings(
                    config,
                    schema,
                    sameRecord,
                    sameRecord
            );
            assertEquals(2, samePairAttempts.stream().filter(BindingAttempt.SUCCESS::equals).count());
            assertBound(
                    repository(control)
                            .findByPublishRecordId(samePair.publishId())
                            .orElseThrow(),
                    "artifact_same_pair",
                    "manifest_same_pair"
            );
            printCapacityAndLockAssessment(control, "concurrency");
        } finally {
            dropSchema(config, schema);
        }
    }

    private static void assertSchemaContract(JdbcTemplate jdbc) {
        for (String column : List.of("artifact_storage_key", "manifest_storage_key")) {
            assertEquals("YES", jdbc.queryForObject(
                    "SELECT is_nullable FROM information_schema.columns "
                            + "WHERE table_schema = current_schema() AND table_name = 'backtest_publish_records' "
                            + "AND column_name = ?",
                    String.class,
                    column
            ));
            assertEquals(128, jdbc.queryForObject(
                    "SELECT character_maximum_length FROM information_schema.columns "
                            + "WHERE table_schema = current_schema() AND table_name = 'backtest_publish_records' "
                            + "AND column_name = ?",
                    Integer.class,
                    column
            ));
            assertEquals(Boolean.TRUE, jdbc.queryForObject(
                    "SELECT col_description(to_regclass('backtest_publish_records'), ordinal_position) IS NOT NULL "
                            + "FROM information_schema.columns WHERE table_schema = current_schema() "
                            + "AND table_name = 'backtest_publish_records' AND column_name = ?",
                    Boolean.class,
                    column
            ));
        }
        for (String constraint : List.of(
                "chk_backtest_publish_artifact_keys_pair",
                "chk_backtest_publish_artifact_storage_key",
                "chk_backtest_publish_manifest_storage_key"
        )) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'backtest_publish_records'::regclass "
                            + "AND conname = ? AND convalidated",
                    Integer.class,
                    constraint
            ));
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_trigger WHERE tgrelid = 'backtest_publish_records'::regclass "
                        + "AND tgname = 'trg_backtest_publish_artifact_locator_immutable' AND NOT tgisinternal",
                Integer.class
        ));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() "
                        + "AND tablename = 'backtest_publish_records' "
                        + "AND indexdef ILIKE '%UNIQUE%' "
                        + "AND (indexdef ILIKE '%artifact_storage_key%' OR indexdef ILIKE '%manifest_storage_key%')",
                Integer.class
        ));
    }

    private static void assertConstraintRejections(JdbcTemplate jdbc) {
        Fixture partial = seedFixture(jdbc, "invalid-partial");
        assertThrows(DataAccessException.class,
                () -> insertDirectPublish(jdbc, partial, "artifact_valid", null, "SUCCEEDED"));

        List<String> invalidKeys = List.of(
                "",
                "bad/key",
                "bad\\key",
                "bad:key",
                "a..b",
                "C_drive",
                "https://storage.example/object",
                " leading",
                "trailing ",
                "control\u0001key",
                "a".repeat(129)
        );
        for (int index = 0; index < invalidKeys.size(); index++) {
            Fixture fixture = seedFixture(jdbc, "invalid-" + index);
            String invalid = invalidKeys.get(index);
            if ("C_drive".equals(invalid)) {
                invalid = "C:\\absolute";
            }
            String rejected = invalid;
            assertThrows(DataAccessException.class,
                    () -> insertDirectPublish(jdbc, fixture, rejected, rejected, "SUCCEEDED"));
        }
    }

    private static void assertImmutabilityRejections(JdbcTemplate jdbc, Fixture fixture) {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE backtest_publish_records SET artifact_storage_key = ?, manifest_storage_key = ? "
                        + "WHERE publish_record_id = ?",
                "rebound_artifact",
                "rebound_manifest",
                fixture.publishId()
        ));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE backtest_publish_records SET artifact_storage_key = NULL, manifest_storage_key = NULL "
                        + "WHERE publish_record_id = ?",
                fixture.publishId()
        ));
    }

    private static BacktestPublishRecord publishRecord(
            Fixture fixture,
            PublishStatus status,
            String artifactStorageKey,
            String manifestStorageKey
    ) {
        return new BacktestPublishRecord(
                fixture.publishId(),
                fixture.backtestRunId(),
                fixture.researchConfigId(),
                fixture.backtestConfigId(),
                fixture.sourceStrategyId(),
                null,
                status == PublishStatus.SUCCEEDED ? "target-" + fixture.publishId() : null,
                null,
                status,
                "GateX-4B PostgreSQL fixture",
                "{}",
                "{}",
                "{}",
                status == PublishStatus.FAILED ? "FIXTURE_FAILED" : null,
                status == PublishStatus.FAILED ? "fixture failed" : null,
                status == PublishStatus.SUCCEEDED ? NOW : null,
                NOW,
                NOW,
                artifactStorageKey,
                manifestStorageKey
        );
    }

    private static BacktestPublishRecord copyWithName(BacktestPublishRecord source, String name) {
        return new BacktestPublishRecord(
                source.publishRecordId(),
                source.backtestRunId(),
                source.researchConfigId(),
                source.backtestConfigId(),
                source.sourceStrategyId(),
                source.evalReportId(),
                source.targetStrategyDefinitionId(),
                source.strategyVersionId(),
                source.publishStatus(),
                name,
                source.publishSnapshotJson(),
                source.versionSnapshotJson(),
                source.evaluationSummaryJson(),
                source.failureCode(),
                source.failureMessage(),
                source.publishedAt(),
                source.createdAt(),
                source.updatedAt(),
                source.artifactStorageKey(),
                source.manifestStorageKey()
        );
    }

    private static void assertBound(BacktestPublishRecord record, String artifactKey, String manifestKey) {
        assertEquals(artifactKey, record.artifactStorageKey());
        assertEquals(manifestKey, record.manifestStorageKey());
        assertEquals(
                BacktestPublishRecord.ArtifactLocatorBindingStatus.PERSISTENT_ARTIFACT_BOUND,
                record.artifactLocatorBindingStatus()
        );
    }

    private static Fixture seedFixture(JdbcTemplate jdbc, String suffix) {
        String unique = suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        Long accountId = jdbc.queryForObject(
                "INSERT INTO accounts (account_code, venue, status) VALUES (?, 'PAPER', 'ACTIVE') RETURNING account_id",
                Long.class,
                "gatex4b-account-" + unique
        );
        assertTrue(accountId != null && accountId > 0);
        String strategyId = "gatex4b-strategy-" + unique;
        String strategyCode = "GATEX4B_" + unique.replace('-', '_').toUpperCase();
        String researchId = "gatex4b-research-" + unique;
        String backtestConfigId = "gatex4b-backtest-config-" + unique;
        String backtestRunId = "gatex4b-backtest-run-" + unique;
        String publishId = "gatex4b-publish-" + unique;

        jdbc.update(
                "INSERT INTO strategy_definitions (strategy_id, strategy_code, strategy_name, strategy_type, "
                        + "exchange_code, account_id, trade_env, enabled, config_snapshot, version) "
                        + "VALUES (?, ?, 'GateX-4B PostgreSQL fixture', 'ARTIFACT_LOCATOR', 'PAPER', ?, 'SIM', "
                        + "FALSE, '{}'::jsonb, 1)",
                strategyId,
                strategyCode,
                accountId
        );
        jdbc.update(
                "INSERT INTO research_configs (research_config_id, source_strategy_id, name, strategy_snapshot) "
                        + "VALUES (?, ?, 'GateX-4B fixture', '{}'::jsonb)",
                researchId,
                strategyId
        );
        jdbc.update(
                "INSERT INTO backtest_configs (backtest_config_id, research_config_id, name) VALUES (?, ?, ?)",
                backtestConfigId,
                researchId,
                "GateX-4B fixture"
        );
        jdbc.update(
                "INSERT INTO backtest_runs (backtest_run_id, backtest_config_id, research_config_id, "
                        + "source_strategy_id, status, strategy_snapshot, backtest_config_snapshot, requested_at) "
                        + "VALUES (?, ?, ?, ?, 'SUCCEEDED', '{}'::jsonb, '{}'::jsonb, ?)",
                backtestRunId,
                backtestConfigId,
                researchId,
                strategyId,
                java.sql.Timestamp.from(NOW)
        );
        return new Fixture(publishId, backtestRunId, researchId, backtestConfigId, strategyId);
    }

    private static void insertLegacyPublish(JdbcTemplate jdbc, Fixture fixture, String status) {
        jdbc.update(
                "INSERT INTO backtest_publish_records (publish_record_id, backtest_run_id, research_config_id, "
                        + "backtest_config_id, source_strategy_id, publish_status, publish_name, published_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'GateX-4B legacy fixture', ?)",
                fixture.publishId(),
                fixture.backtestRunId(),
                fixture.researchConfigId(),
                fixture.backtestConfigId(),
                fixture.sourceStrategyId(),
                status,
                "SUCCEEDED".equals(status) ? java.sql.Timestamp.from(NOW) : null
        );
    }

    private static void insertDirectPublish(
            JdbcTemplate jdbc,
            Fixture fixture,
            String artifactStorageKey,
            String manifestStorageKey,
            String status
    ) {
        jdbc.update(
                "INSERT INTO backtest_publish_records (publish_record_id, backtest_run_id, research_config_id, "
                        + "backtest_config_id, source_strategy_id, publish_status, publish_name, "
                        + "artifact_storage_key, manifest_storage_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                fixture.publishId(),
                fixture.backtestRunId(),
                fixture.researchConfigId(),
                fixture.backtestConfigId(),
                fixture.sourceStrategyId(),
                status,
                "GateX-4B direct fixture",
                artifactStorageKey,
                manifestStorageKey
        );
    }

    private static List<BindingAttempt> runConcurrentBindings(
            PostgresConfig config,
            String schema,
            BacktestPublishRecord firstRecord,
            BacktestPublishRecord secondRecord
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<BindingAttempt> first = executor.submit(
                    () -> bindAfterBarrier(config, schema, firstRecord, ready, start)
            );
            Future<BindingAttempt> second = executor.submit(
                    () -> bindAfterBarrier(config, schema, secondRecord, ready, start)
            );
            assertTrue(ready.await(5, TimeUnit.SECONDS), "concurrent bind workers did not become ready");
            start.countDown();
            return List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("concurrent locator binding was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("concurrent locator binding did not finish safely", exception);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static BindingAttempt bindAfterBarrier(
            PostgresConfig config,
            String schema,
            BacktestPublishRecord record,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("concurrent locator binding start barrier timed out");
        }
        JdbcTemplate worker = jdbc(config, schema);
        worker.setQueryTimeout(5);
        try {
            repository(worker).upsert(record);
            return BindingAttempt.SUCCESS;
        } catch (IllegalStateException exception) {
            assertEquals("backtest publish artifact locator conflict", exception.getMessage());
            return BindingAttempt.CONFLICT;
        }
    }

    private static void printCapacityAndLockAssessment(JdbcTemplate jdbc, String phase) {
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM backtest_publish_records", Integer.class);
        Long relationBytes = jdbc.queryForObject(
                "SELECT pg_relation_size('backtest_publish_records')",
                Long.class
        );
        Long indexBytes = jdbc.queryForObject("SELECT pg_indexes_size('backtest_publish_records')", Long.class);
        Integer longTransactions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() "
                        + "AND pid <> pg_backend_pid() AND xact_start IS NOT NULL "
                        + "AND xact_start < clock_timestamp() - INTERVAL '1 minute'",
                Integer.class
        );
        Integer lockWaits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() "
                        + "AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'",
                Integer.class
        );
        System.out.printf(
                "GATEX4B_POSTGRES_METRICS phase=%s rows=%d relation_bytes=%d index_bytes=%d "
                        + "long_transactions=%d lock_waits=%d%n",
                phase,
                rows,
                relationBytes,
                indexBytes,
                longTransactions,
                lockWaits
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

    private static JdbcBacktestPublishRecordRepository repository(JdbcTemplate jdbc) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(jdbc.getDataSource());
        return new JdbcBacktestPublishRecordRepository(
                jdbc,
                new JdbcAdmissionMutationCoordinator(jdbc, transactionManager, 256)
        );
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
        return "gatex4b_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void dropSchema(PostgresConfig config, String schema) {
        if (schema == null || !schema.matches("gatex4b_(fresh|upgrade|concurrency)_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("refusing to drop non-GateX-4B schema");
        }
        jdbc(config, "public").execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
    }

    private static PostgresConfig requireLocalDisposableConfig() {
        PostgresConfig config = PostgresConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "local disposable PostgreSQL properties are not configured");
        }
        assertTrue(config.configured(), "missing required local disposable PostgreSQL properties");
        assertTrue(config.localhost(), "GateX-4B PostgreSQL test refuses non-local database URLs");
        return config;
    }

    private record Fixture(
            String publishId,
            String backtestRunId,
            String researchConfigId,
            String backtestConfigId,
            String sourceStrategyId
    ) {
    }

    private enum BindingAttempt {
        SUCCESS,
        CONFLICT
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
            return !url.isBlank() && !user.isBlank();
        }

        private boolean localhost() {
            try {
                String normalized = url.replaceFirst("^jdbc:", "");
                String host = URI.create(normalized).getHost();
                return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private static String property(String name) {
            return System.getProperty(name, "").trim();
        }
    }
}
