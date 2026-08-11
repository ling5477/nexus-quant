package com.guidinglight.nexusquant.app.admission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcAdmissionMutationCoordinator;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyReleaseAdmissionStateRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionMutationCoordinationException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionMutationCoordinator;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionState;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.VerifiedStrategyReleaseIdentity;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GateX-5A V38 consistency infrastructure 的真实 localhost disposable PostgreSQL 回归。
 *
 * <p>测试只创建/删除随机 {@code gatex5a_*} schema；focused run 必须显式 required=true，
 * 防止将未执行的 PostgreSQL regression 写成通过。
 */
class AdmissionMaterializationGuardPostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.admission-guard.postgres.required";
    private static final String URL_PROPERTY = "nq.admission-guard.postgres.url";
    private static final String USER_PROPERTY = "nq.admission-guard.postgres.user";
    private static final String PASSWORD_PROPERTY = "nq.admission-guard.postgres.password";
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final String MANIFEST_SCHEMA = "strategy-release-manifest.v1";
    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

    @Test
    void freshAndV37UpgradeShouldInitializeOnlyUnboundStateAndValidateFlyway() {
        PostgresConfig config = requireLocalDisposableConfig();
        String freshSchema = randomSchema("fresh");
        String upgradeSchema = randomSchema("upgrade");
        try {
            Flyway freshFlyway = migrate(config, freshSchema, null);
            JdbcTemplate fresh = jdbc(config, freshSchema);
            assertEquals("38", currentFlywayVersion(fresh));
            freshFlyway.validate();
            assertEquals(39, fresh.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history",
                    Integer.class
            ));
            assertEquals(1, fresh.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '38' AND success = TRUE",
                    Integer.class
            ));
            assertDatasetReverseIndexUsable(fresh);

            // V12 将 pgcrypto 安装到 currentSchema；同一 disposable database 的第二次全量重放前
            // 必须移除首个临时 schema/extension，避免 IF NOT EXISTS 造成跨 schema search_path 污染。
            dropSchema(config, freshSchema);
            jdbc(config, "public").execute("DROP EXTENSION IF EXISTS pgcrypto CASCADE");

            migrate(config, upgradeSchema, MigrationVersion.fromVersion("37"));
            JdbcTemplate before = jdbc(config, upgradeSchema);
            Fixture legacy = seedFixture(before, "legacy", false);
            assertFalse(tableExists(before, "strategy_release_admission_state"));

            Flyway upgradedFlyway = migrate(config, upgradeSchema, null);
            JdbcTemplate upgraded = jdbc(config, upgradeSchema);
            assertEquals("38", currentFlywayVersion(upgraded));
            upgradedFlyway.validate();
            assertUnboundState(upgraded, legacy.publishId(), 0L);

            Fixture future = seedAdditionalPublish(upgraded, legacy, "future");
            assertUnboundState(upgraded, future.publishId(), 0L);
            printCapacityAndLockEvidence(upgraded, "upgrade");
        } finally {
            dropSchema(config, freshSchema);
            dropSchema(config, upgradeSchema);
        }
    }

    @Test
    void rawSqlMutationsShouldCoverRevisionPhantomsReorderAndFanOut() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("coverage");
        try {
            migrate(config, schema, null);
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture first = seedFixture(jdbc, "coverage", true);
            Fixture second = seedAdditionalPublish(jdbc, first, "fanout");
            assertUnboundState(jdbc, first.publishId(), 0L);
            assertUnboundState(jdbc, second.publishId(), 0L);

            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "UPDATE backtest_publish_records SET publish_name = ?, updated_at = ? WHERE publish_record_id = ?",
                    "publish-updated",
                    Timestamp.from(NOW.plusSeconds(1)),
                    first.publishId()
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "UPDATE backtest_eval_reports SET report_json = '{\"changed\":true}'::jsonb, updated_at = ? "
                            + "WHERE eval_report_id = ?",
                    Timestamp.from(NOW.plusSeconds(2)),
                    first.evaluationId()
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "DELETE FROM backtest_eval_reports WHERE eval_report_id = ?",
                    first.evaluationId()
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "INSERT INTO backtest_eval_reports (eval_report_id, backtest_run_id, evaluation_status, evaluated_at) "
                            + "VALUES (?, ?, 'SUCCEEDED', ?)",
                    first.evaluationId(),
                    first.backtestRunId(),
                    Timestamp.from(NOW.plusSeconds(2))
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "INSERT INTO backtest_eval_reports (eval_report_id, backtest_run_id, evaluation_status, evaluated_at) "
                            + "VALUES (?, ?, 'SUCCEEDED', ?) ON CONFLICT (backtest_run_id) DO UPDATE "
                            + "SET report_json = '{\"upserted\":true}'::jsonb, updated_at = EXCLUDED.evaluated_at",
                    first.evaluationId(),
                    first.backtestRunId(),
                    Timestamp.from(NOW.plusSeconds(3))
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "UPDATE backtest_runs SET summary_json = '{\"changed\":true}'::jsonb, updated_at = ? "
                            + "WHERE backtest_run_id = ?",
                    Timestamp.from(NOW.plusSeconds(3)),
                    first.backtestRunId()
            ));

            String paperRunId = "paper-" + shortId();
            assertChanged(jdbc, first.publishId(), () -> insertPaper(jdbc, first, paperRunId, NOW));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "UPDATE paper_trading_runs SET status = 'RUNNING', updated_at = ? WHERE paper_run_id = ?",
                    Timestamp.from(NOW.plusSeconds(4)),
                    paperRunId
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "UPDATE paper_trading_runs SET updated_at = ? WHERE paper_run_id = ?",
                    Timestamp.from(NOW.plusSeconds(40)),
                    paperRunId
            ));
            String latestPaperRunId = "paper-" + shortId();
            assertChanged(jdbc, first.publishId(), () -> insertPaper(
                    jdbc,
                    first,
                    latestPaperRunId,
                    NOW.plusSeconds(41)
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "UPDATE paper_trading_runs SET status = 'STOPPED', updated_at = ? WHERE paper_run_id = ?",
                    Timestamp.from(NOW.plusSeconds(42)),
                    paperRunId
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "DELETE FROM paper_trading_runs WHERE paper_run_id = ?",
                    latestPaperRunId
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "DELETE FROM paper_trading_runs WHERE paper_run_id = ?",
                    paperRunId
            ));

            UUID shadowRunId = UUID.randomUUID();
            long beforeShadow = revision(jdbc, first.publishId());
            insertShadow(jdbc, first, shadowRunId, NOW);
            assertEquals(beforeShadow + 1, revision(jdbc, first.publishId()), "Shadow CREATED must bump exactly once");

            long beforeEvent = revision(jdbc, first.publishId());
            jdbc.update(
                    "INSERT INTO shadow_run_events (id, shadow_run_id, event_type, to_status, trace_id) "
                            + "VALUES (?, ?, 'CREATED', 'CREATED', 'gatex5a-test')",
                    UUID.randomUUID(),
                    shadowRunId
            );
            assertEquals(beforeEvent, revision(jdbc, first.publishId()), "CREATED event must not bump");

            long beforePrechecking = revision(jdbc, first.publishId());
            jdbc.update(
                    "UPDATE shadow_runs SET status = 'PRECHECKING', version = version + 1, updated_at = ? WHERE id = ?",
                    Timestamp.from(NOW.plusSeconds(5)),
                    shadowRunId
            );
            assertEquals(beforePrechecking + 1, revision(jdbc, first.publishId()));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "UPDATE shadow_runs SET version = version + 1, updated_at = ? WHERE id = ?",
                    Timestamp.from(NOW.plusSeconds(50)),
                    shadowRunId
            ));

            UUID reportId = UUID.randomUUID();
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "INSERT INTO shadow_consistency_reports "
                            + "(id, shadow_run_id, comparison_status, generated_at, trace_id) "
                            + "VALUES (?, ?, 'PARTIAL', ?, 'gatex5a-test')",
                    reportId,
                    shadowRunId,
                    Timestamp.from(NOW.plusSeconds(6))
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "UPDATE shadow_consistency_reports SET generated_at = ?, comparison_status = 'CONSISTENT' WHERE id = ?",
                    Timestamp.from(NOW.plusSeconds(60)),
                    reportId
            ));
            UUID latestReportId = UUID.randomUUID();
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "INSERT INTO shadow_consistency_reports "
                            + "(id, shadow_run_id, comparison_status, generated_at, trace_id) "
                            + "VALUES (?, ?, 'PARTIAL', ?, 'gatex5a-test')",
                    latestReportId,
                    shadowRunId,
                    Timestamp.from(NOW.plusSeconds(61))
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "DELETE FROM shadow_consistency_reports WHERE id = ?",
                    latestReportId
            ));
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "DELETE FROM shadow_consistency_reports WHERE id = ?",
                    reportId
            ));
            jdbc.update("DELETE FROM shadow_run_events WHERE shadow_run_id = ?", shadowRunId);
            assertChanged(jdbc, first.publishId(), () -> jdbc.update(
                    "DELETE FROM shadow_runs WHERE id = ?",
                    shadowRunId
            ));

            long firstBeforeStrategy = revision(jdbc, first.publishId());
            long secondBeforeStrategy = revision(jdbc, second.publishId());
            jdbc.update(
                    "UPDATE strategy_versions SET status = 'ARCHIVED', updated_at = ? WHERE strategy_version_id = ?",
                    Timestamp.from(NOW.plusSeconds(7)),
                    first.strategyVersionId()
            );
            assertTrue(revision(jdbc, first.publishId()) > firstBeforeStrategy);
            assertTrue(revision(jdbc, second.publishId()) > secondBeforeStrategy);

            long firstBeforeDataset = revision(jdbc, first.publishId());
            long secondBeforeDataset = revision(jdbc, second.publishId());
            jdbc.update(
                    "UPDATE marketdata_datasets SET quality_status = 'GAP_DETECTED', gap_count = 1, updated_at = ? "
                            + "WHERE dataset_id = ?",
                    Timestamp.from(NOW.plusSeconds(8)),
                    first.datasetId()
            );
            assertTrue(revision(jdbc, first.publishId()) > firstBeforeDataset);
            assertTrue(revision(jdbc, second.publishId()) > secondBeforeDataset);
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE backtest_publish_records SET publish_record_id = ? WHERE publish_record_id = ?",
                    "rekey-forbidden-" + shortId(),
                    first.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "DELETE FROM backtest_publish_records WHERE publish_record_id = ?",
                    first.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.queryForObject(
                    "SELECT bump_strategy_release_admission_revisions("
                            + "ARRAY(SELECT 'missing-' || value FROM generate_series(1, 257) AS value))",
                    Object.class
            ));
            printCapacityAndLockEvidence(jdbc, "coverage");
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    void excessiveFanOutShouldFailClosedBeforeMutationOrPartialBump() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("coverage");
        try {
            migrate(config, schema, null);
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture first = seedFixture(jdbc, "fanout-limit", true);
            Fixture last = first;
            List<String> publishIds = new ArrayList<>();
            publishIds.add(first.publishId());
            for (int index = 0; index < AdmissionMutationCoordinator.HARD_MAX_FAN_OUT; index++) {
                last = seedAdditionalPublish(jdbc, first, "fanout-limit-" + index);
                publishIds.add(last.publishId());
            }
            assertEquals(AdmissionMutationCoordinator.HARD_MAX_FAN_OUT + 1, publishIds.size());

            long firstRevision = revision(jdbc, first.publishId());
            long lastRevision = revision(jdbc, last.publishId());
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_versions SET status = 'ARCHIVED' WHERE strategy_version_id = ?",
                    first.strategyVersionId()
            ));
            assertEquals("ACTIVE", jdbc.queryForObject(
                    "SELECT status FROM strategy_versions WHERE strategy_version_id = ?",
                    String.class,
                    first.strategyVersionId()
            ));
            assertEquals(firstRevision, revision(jdbc, first.publishId()));
            assertEquals(lastRevision, revision(jdbc, last.publishId()));

            int[] mutationCalls = {0};
            assertThrows(
                    AdmissionMutationCoordinationException.class,
                    () -> coordinator(jdbc).withLockedAdmissionStates(publishIds, () -> mutationCalls[0]++)
            );
            assertEquals(0, mutationCalls[0]);
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    @Timeout(20)
    void coordinatorShouldLinearizeReverseOrderingAndRollbackTriggerFailures() throws Exception {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("locking");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            migrate(config, schema, null);
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture first = seedFixture(jdbc, "locking", true);
            Fixture second = seedAdditionalPublish(jdbc, first, "locking-second");
            String paperRunId = "paper-" + shortId();
            insertPaper(jdbc, first, paperRunId, NOW);

            JdbcAdmissionMutationCoordinator coordinator = coordinator(jdbc);
            CountDownLatch firstLocked = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            Future<?> transactionA = executor.submit(() -> coordinator.withLockedAdmissionStates(
                    List.of(first.publishId()),
                    () -> {
                        firstLocked.countDown();
                        await(releaseFirst);
                        jdbc.update(
                                "UPDATE paper_trading_runs SET updated_at = ? WHERE paper_run_id = ?",
                                Timestamp.from(NOW.plusSeconds(10)),
                                paperRunId
                        );
                    }
            ));
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS));
            Future<?> rawWriter = executor.submit(() -> jdbc.update(
                    "UPDATE paper_trading_runs SET updated_at = ? WHERE paper_run_id = ?",
                    Timestamp.from(NOW.plusSeconds(9)),
                    paperRunId
            ));
            assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> rawWriter.get(2, TimeUnit.SECONDS),
                    "raw source-first writer must fail immediately when admission-state is locked"
            );
            Future<?> transactionB = executor.submit(() -> coordinator.withLockedAdmissionStates(
                    List.of(first.publishId()),
                    () -> jdbc.update(
                            "UPDATE paper_trading_runs SET updated_at = ? WHERE paper_run_id = ?",
                            Timestamp.from(NOW.plusSeconds(11)),
                            paperRunId
                    )
            ));
            Thread.sleep(200);
            assertFalse(transactionB.isDone(), "second state-first writer must wait for admission-state lock");
            releaseFirst.countDown();
            transactionA.get(5, TimeUnit.SECONDS);
            transactionB.get(5, TimeUnit.SECONDS);

            CountDownLatch startTogether = new CountDownLatch(1);
            Future<?> ordered = executor.submit(() -> {
                await(startTogether);
                coordinator.withLockedAdmissionStates(
                        List.of(first.publishId(), second.publishId()),
                        () -> jdbc.update(
                                "UPDATE backtest_publish_records SET updated_at = ? WHERE publish_record_id = ?",
                                Timestamp.from(NOW.plusSeconds(12)),
                                first.publishId()
                        )
                );
            });
            Future<?> reversed = executor.submit(() -> {
                await(startTogether);
                coordinator.withLockedAdmissionStates(
                        List.of(second.publishId(), first.publishId()),
                        () -> jdbc.update(
                                "UPDATE backtest_publish_records SET updated_at = ? WHERE publish_record_id = ?",
                                Timestamp.from(NOW.plusSeconds(13)),
                                second.publishId()
                        )
                );
            });
            startTogether.countDown();
            ordered.get(5, TimeUnit.SECONDS);
            reversed.get(5, TimeUnit.SECONDS);

            String originalName = jdbc.queryForObject(
                    "SELECT publish_name FROM backtest_publish_records WHERE publish_record_id = ?",
                    String.class,
                    first.publishId()
            );
            long originalRevision = revision(jdbc, first.publishId());
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager(jdbc));
            assertThrows(DataAccessException.class, () -> transactionTemplate.executeWithoutResult(status -> {
                jdbc.update(
                        "DELETE FROM strategy_release_admission_state WHERE publish_record_id = ?",
                        first.publishId()
                );
                jdbc.update(
                        "UPDATE backtest_publish_records SET publish_name = 'must-rollback' WHERE publish_record_id = ?",
                        first.publishId()
                );
            }));
            assertEquals(originalName, jdbc.queryForObject(
                    "SELECT publish_name FROM backtest_publish_records WHERE publish_record_id = ?",
                    String.class,
                    first.publishId()
            ));
            assertEquals(originalRevision, revision(jdbc, first.publishId()));

            JdbcStrategyReleaseAdmissionStateRepository stateRepository =
                    new JdbcStrategyReleaseAdmissionStateRepository(jdbc, coordinator);
            long beforeBind = revision(jdbc, first.publishId());
            StrategyReleaseAdmissionState bound = stateRepository.bindVerifiedReleaseIdentity(identity(first));
            assertEquals(beforeBind + 1, bound.admissionRevision());
            assertEquals(DIGEST_A, bound.releaseArtifactDigest());
            assertThrows(
                    AdmissionMutationCoordinationException.class,
                    () -> stateRepository.bindVerifiedReleaseIdentity(identity(first))
            );
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET release_artifact_digest = ? WHERE publish_record_id = ?",
                    DIGEST_B,
                    first.publishId()
            ));
            assertEquals(DIGEST_A, jdbc.queryForObject(
                    "SELECT release_artifact_digest FROM strategy_release_admission_state WHERE publish_record_id = ?",
                    String.class,
                    first.publishId()
            ));

            long secondBeforeFailedBind = revision(jdbc, second.publishId());
            VerifiedStrategyReleaseIdentity mismatched = new VerifiedStrategyReleaseIdentity(
                    second.publishId(),
                    "different-strategy-version",
                    second.datasetId(),
                    second.evaluationId(),
                    DIGEST_A,
                    DIGEST_B,
                    MANIFEST_SCHEMA
            );
            assertThrows(
                    AdmissionMutationCoordinationException.class,
                    () -> stateRepository.bindVerifiedReleaseIdentity(mismatched)
            );
            assertUnboundState(jdbc, second.publishId(), secondBeforeFailedBind);
        } finally {
            releaseQuietly(executor);
            dropSchema(config, schema);
        }
    }

    @Test
    void directRevisionAndIdentityRewritesShouldFailClosed() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("coverage");
        try {
            migrate(config, schema, null);
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture fixture = seedFixture(jdbc, "direct-guard-attacks", true);

            Long bumped = jdbc.queryForObject(
                    "SELECT bump_strategy_release_admission_revision(?)",
                    Long.class,
                    fixture.publishId()
            );
            assertNotNull(bumped);
            assertEquals(1L, bumped);
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET admission_revision = 0 WHERE publish_record_id = ?",
                    fixture.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET admission_revision = admission_revision "
                            + "WHERE publish_record_id = ?",
                    fixture.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET admission_revision = admission_revision + 2 "
                            + "WHERE publish_record_id = ?",
                    fixture.publishId()
            ));
            assertEquals(1L, revision(jdbc, fixture.publishId()));

            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET release_artifact_digest = ? "
                            + "WHERE publish_record_id = ?",
                    DIGEST_A,
                    fixture.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET manifest_fingerprint = ? "
                            + "WHERE publish_record_id = ?",
                    DIGEST_B,
                    fixture.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state "
                            + "SET release_artifact_digest = ?, manifest_fingerprint = ? "
                            + "WHERE publish_record_id = ?",
                    DIGEST_A,
                    DIGEST_B,
                    fixture.publishId()
            ));
            assertUnboundState(jdbc, fixture.publishId(), 1L);

            JdbcAdmissionMutationCoordinator coordinator = coordinator(jdbc);
            JdbcStrategyReleaseAdmissionStateRepository stateRepository =
                    new JdbcStrategyReleaseAdmissionStateRepository(jdbc, coordinator);
            StrategyReleaseAdmissionState bound = stateRepository.bindVerifiedReleaseIdentity(identity(fixture));
            assertEquals(2L, bound.admissionRevision());

            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET release_artifact_digest = ? "
                            + "WHERE publish_record_id = ?",
                    DIGEST_B,
                    fixture.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET manifest_fingerprint = ? "
                            + "WHERE publish_record_id = ?",
                    DIGEST_A,
                    fixture.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET manifest_schema_version = 'strategy-release-manifest.v2' "
                            + "WHERE publish_record_id = ?",
                    fixture.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET identity_bound_at = identity_bound_at + interval '1 second' "
                            + "WHERE publish_record_id = ?",
                    fixture.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state "
                            + "SET release_artifact_digest = NULL, manifest_fingerprint = NULL, "
                            + "manifest_schema_version = NULL, identity_bound_at = NULL "
                            + "WHERE publish_record_id = ?",
                    fixture.publishId()
            ));
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "UPDATE strategy_release_admission_state SET manifest_fingerprint = NULL "
                            + "WHERE publish_record_id = ?",
                    fixture.publishId()
            ));

            StateRow persisted = state(jdbc, fixture.publishId());
            assertEquals(2L, persisted.revision());
            assertEquals(DIGEST_A, persisted.artifactDigest());
            assertEquals(DIGEST_B, persisted.manifestFingerprint());
            assertEquals(MANIFEST_SCHEMA, persisted.manifestSchemaVersion());
            assertNotNull(persisted.identityBoundAt());
        } finally {
            dropSchema(config, schema);
        }
    }

    private static Fixture seedFixture(JdbcTemplate jdbc, String suffix, boolean v38Present) {
        String unique = suffix + "-" + shortId();
        Long accountId = jdbc.queryForObject(
                "INSERT INTO accounts (account_code, venue, status) VALUES (?, 'PAPER', 'ACTIVE') RETURNING account_id",
                Long.class,
                "gatex5a-account-" + unique
        );
        assertNotNull(accountId);
        String strategyId = "gatex5a-strategy-" + unique;
        String strategyCode = "GATEX5A_" + unique.replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        String strategyVersionId = "gatex5a-version-" + unique;
        String researchId = "gatex5a-research-" + unique;
        String configId = "gatex5a-config-" + unique;
        String runId = "gatex5a-run-" + unique;
        String evaluationId = "gatex5a-eval-" + unique;
        String publishId = "gatex5a-publish-" + unique;
        UUID datasetId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO strategy_definitions (strategy_id, strategy_code, strategy_name, strategy_type, "
                        + "exchange_code, account_id, trade_env, enabled, config_snapshot, version) "
                        + "VALUES (?, ?, 'GateX5A fixture', 'ADMISSION_GUARD', 'PAPER', ?, 'SIM', FALSE, '{}'::jsonb, 1)",
                strategyId,
                strategyCode,
                accountId
        );
        jdbc.update(
                "INSERT INTO research_configs (research_config_id, source_strategy_id, name, strategy_snapshot) "
                        + "VALUES (?, ?, 'GateX5A fixture', '{}'::jsonb)",
                researchId,
                strategyId
        );
        jdbc.update(
                "INSERT INTO backtest_configs (backtest_config_id, research_config_id, name) VALUES (?, ?, 'GateX5A fixture')",
                configId,
                researchId
        );
        jdbc.update(
                "INSERT INTO marketdata_datasets (dataset_id, dataset_name, exchange_code, market_type, symbol, "
                        + "\"interval\", start_time, end_time, status, quality_status, source, created_by) "
                        + "VALUES (?, ?, 'OKX', 'SPOT', 'BTC-USDT', '1m', ?, ?, 'READY', 'OK', 'GATEX5A_TEST', 'test')",
                datasetId,
                "gatex5a-dataset-" + unique,
                Timestamp.from(NOW),
                Timestamp.from(NOW.plusSeconds(60))
        );
        jdbc.update(
                "INSERT INTO strategy_versions (strategy_version_id, strategy_code, version, version_name, status, "
                        + "checksum, created_by) VALUES (?, ?, 1, 'GateX5A fixture', 'ACTIVE', ?, 'test')",
                strategyVersionId,
                strategyCode,
                DIGEST_A
        );
        jdbc.update(
                "INSERT INTO backtest_runs (backtest_run_id, backtest_config_id, research_config_id, "
                        + "source_strategy_id, status, strategy_snapshot, strategy_version_id, "
                        + "backtest_config_snapshot, dataset_snapshot_json, requested_at) "
                        + "VALUES (?, ?, ?, ?, 'SUCCEEDED', '{}'::jsonb, ?, '{}'::jsonb, "
                        + "jsonb_build_object('datasetId', ?), ?)",
                runId,
                configId,
                researchId,
                strategyId,
                strategyVersionId,
                datasetId.toString(),
                Timestamp.from(NOW)
        );
        jdbc.update(
                "INSERT INTO backtest_eval_reports (eval_report_id, backtest_run_id, evaluation_status, evaluated_at) "
                        + "VALUES (?, ?, 'SUCCEEDED', ?)",
                evaluationId,
                runId,
                Timestamp.from(NOW)
        );
        jdbc.update(
                "INSERT INTO backtest_publish_records (publish_record_id, backtest_run_id, research_config_id, "
                        + "backtest_config_id, source_strategy_id, eval_report_id, strategy_version_id, "
                        + "publish_status, publish_name, artifact_storage_key, manifest_storage_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'SUCCEEDED', 'GateX5A fixture', ?, ?)",
                publishId,
                runId,
                researchId,
                configId,
                strategyId,
                evaluationId,
                strategyVersionId,
                "artifact_" + unique,
                "manifest_" + unique
        );
        if (v38Present) {
            assertUnboundState(jdbc, publishId, 0L);
        }
        return new Fixture(
                strategyId,
                strategyVersionId,
                researchId,
                configId,
                runId,
                evaluationId,
                publishId,
                datasetId
        );
    }

    private static Fixture seedAdditionalPublish(JdbcTemplate jdbc, Fixture base, String suffix) {
        String unique = suffix + "-" + shortId();
        String researchId = "gatex5a-research-" + unique;
        String configId = "gatex5a-config-" + unique;
        String runId = "gatex5a-run-" + unique;
        String evaluationId = "gatex5a-eval-" + unique;
        String publishId = "gatex5a-publish-" + unique;
        jdbc.update(
                "INSERT INTO research_configs (research_config_id, source_strategy_id, name, strategy_snapshot) "
                        + "VALUES (?, ?, 'GateX5A additional', '{}'::jsonb)",
                researchId,
                base.strategyId()
        );
        jdbc.update(
                "INSERT INTO backtest_configs (backtest_config_id, research_config_id, name) VALUES (?, ?, 'GateX5A additional')",
                configId,
                researchId
        );
        jdbc.update(
                "INSERT INTO backtest_runs (backtest_run_id, backtest_config_id, research_config_id, source_strategy_id, "
                        + "status, strategy_snapshot, strategy_version_id, backtest_config_snapshot, "
                        + "dataset_snapshot_json, requested_at) VALUES (?, ?, ?, ?, 'SUCCEEDED', '{}'::jsonb, ?, "
                        + "'{}'::jsonb, jsonb_build_object('datasetId', ?), ?)",
                runId,
                configId,
                researchId,
                base.strategyId(),
                base.strategyVersionId(),
                base.datasetId().toString(),
                Timestamp.from(NOW)
        );
        jdbc.update(
                "INSERT INTO backtest_eval_reports (eval_report_id, backtest_run_id, evaluation_status, evaluated_at) "
                        + "VALUES (?, ?, 'SUCCEEDED', ?)",
                evaluationId,
                runId,
                Timestamp.from(NOW)
        );
        jdbc.update(
                "INSERT INTO backtest_publish_records (publish_record_id, backtest_run_id, research_config_id, "
                        + "backtest_config_id, source_strategy_id, eval_report_id, strategy_version_id, "
                        + "publish_status, publish_name, artifact_storage_key, manifest_storage_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'SUCCEEDED', 'GateX5A additional', ?, ?)",
                publishId,
                runId,
                researchId,
                configId,
                base.strategyId(),
                evaluationId,
                base.strategyVersionId(),
                "artifact_" + unique,
                "manifest_" + unique
        );
        return new Fixture(
                base.strategyId(),
                base.strategyVersionId(),
                researchId,
                configId,
                runId,
                evaluationId,
                publishId,
                base.datasetId()
        );
    }

    private static void insertPaper(JdbcTemplate jdbc, Fixture fixture, String paperRunId, Instant time) {
        jdbc.update(
                "INSERT INTO paper_trading_runs (paper_run_id, publish_id, strategy_version_id, status, trade_env, "
                        + "exchange_code, market_type, symbol, interval_code, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'CREATED', 'SIM', 'OKX', 'SPOT', 'BTC-USDT', '1m', 'test', ?, ?)",
                paperRunId,
                fixture.publishId(),
                fixture.strategyVersionId(),
                Timestamp.from(time),
                Timestamp.from(time)
        );
    }

    private static void insertShadow(JdbcTemplate jdbc, Fixture fixture, UUID shadowRunId, Instant time) {
        jdbc.update(
                "INSERT INTO shadow_runs (id, strategy_version_id, dataset_id, evaluation_id, publish_id, "
                        + "artifact_digest, status, idempotency_key, trace_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'CREATED', ?, 'gatex5a-test', ?, ?)",
                shadowRunId,
                fixture.strategyVersionId(),
                fixture.datasetId(),
                fixture.evaluationId(),
                fixture.publishId(),
                DIGEST_A,
                "gatex5a-shadow-" + shortId(),
                Timestamp.from(time),
                Timestamp.from(time)
        );
    }

    private static VerifiedStrategyReleaseIdentity identity(Fixture fixture) {
        return new VerifiedStrategyReleaseIdentity(
                fixture.publishId(),
                fixture.strategyVersionId(),
                fixture.datasetId(),
                fixture.evaluationId(),
                DIGEST_A,
                DIGEST_B,
                MANIFEST_SCHEMA
        );
    }

    private static void assertChanged(JdbcTemplate jdbc, String publishId, Runnable mutation) {
        long before = revision(jdbc, publishId);
        mutation.run();
        assertTrue(revision(jdbc, publishId) > before, "admission revision must change");
    }

    private static void assertUnboundState(JdbcTemplate jdbc, String publishId, long expectedRevision) {
        StateRow state = state(jdbc, publishId);
        assertEquals(expectedRevision, state.revision());
        assertEquals(1, state.guardSchemaVersion());
        assertEquals(null, state.artifactDigest());
        assertEquals(null, state.manifestFingerprint());
        assertEquals(null, state.manifestSchemaVersion());
        assertEquals(null, state.identityBoundAt());
    }

    private static StateRow state(JdbcTemplate jdbc, String publishId) {
        StateRow state = jdbc.queryForObject(
                """
                        SELECT admission_revision, guard_schema_version,
                               release_artifact_digest, manifest_fingerprint,
                               manifest_schema_version, identity_bound_at
                        FROM strategy_release_admission_state
                        WHERE publish_record_id = ?
                        """,
                (resultSet, rowNum) -> new StateRow(
                        resultSet.getLong("admission_revision"),
                        resultSet.getInt("guard_schema_version"),
                        resultSet.getString("release_artifact_digest"),
                        resultSet.getString("manifest_fingerprint"),
                        resultSet.getString("manifest_schema_version"),
                        resultSet.getTimestamp("identity_bound_at")
                ),
                publishId
        );
        assertNotNull(state);
        return state;
    }

    private static long revision(JdbcTemplate jdbc, String publishId) {
        Long value = jdbc.queryForObject(
                "SELECT admission_revision FROM strategy_release_admission_state WHERE publish_record_id = ?",
                Long.class,
                publishId
        );
        assertNotNull(value);
        return value;
    }

    private static void assertDatasetReverseIndexUsable(JdbcTemplate jdbc) {
        jdbc.execute("SET enable_seqscan = off");
        List<String> plan = jdbc.query(
                "EXPLAIN SELECT backtest_run_id FROM backtest_runs "
                        + "WHERE dataset_snapshot_json ->> 'datasetId' = ?",
                (resultSet, rowNum) -> resultSet.getString(1),
                UUID.randomUUID().toString()
        );
        jdbc.execute("RESET enable_seqscan");
        assertTrue(
                plan.stream().anyMatch(line -> line.contains("idx_backtest_runs_dataset_snapshot_id")),
                () -> "dataset reverse index missing from plan: " + plan
        );
    }

    private static void printCapacityAndLockEvidence(JdbcTemplate jdbc, String label) {
        Long publishRows = jdbc.queryForObject("SELECT COUNT(*) FROM backtest_publish_records", Long.class);
        Long backtestRows = jdbc.queryForObject("SELECT COUNT(*) FROM backtest_runs", Long.class);
        Long paperRows = jdbc.queryForObject("SELECT COUNT(*) FROM paper_trading_runs", Long.class);
        Long shadowRows = jdbc.queryForObject("SELECT COUNT(*) FROM shadow_runs", Long.class);
        Long consistencyRows = jdbc.queryForObject("SELECT COUNT(*) FROM shadow_consistency_reports", Long.class);
        Long stateSize = jdbc.queryForObject(
                "SELECT pg_total_relation_size('strategy_release_admission_state'::regclass)",
                Long.class
        );
        Long datasetIndexSize = jdbc.queryForObject(
                "SELECT pg_relation_size('idx_backtest_runs_dataset_snapshot_id'::regclass)",
                Long.class
        );
        Long longTransactions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_stat_activity WHERE xact_start < now() - interval '30 seconds'",
                Long.class
        );
        Long lockWaits = jdbc.queryForObject("SELECT COUNT(*) FROM pg_locks WHERE NOT granted", Long.class);
        System.out.printf(
                "GATEX5A_CAPACITY[%s] publish=%d backtest=%d paper=%d shadow=%d consistency=%d "
                        + "stateBytes=%d datasetIndexBytes=%d longTransactions=%d lockWaits=%d%n",
                label,
                publishRows,
                backtestRows,
                paperRows,
                shadowRows,
                consistencyRows,
                stateSize,
                datasetIndexSize,
                longTransactions,
                lockWaits
        );
    }

    private static JdbcAdmissionMutationCoordinator coordinator(JdbcTemplate jdbc) {
        return new JdbcAdmissionMutationCoordinator(jdbc, transactionManager(jdbc), 256);
    }

    private static DataSourceTransactionManager transactionManager(JdbcTemplate jdbc) {
        return new DataSourceTransactionManager(jdbc.getDataSource());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for test latch", exception);
        }
    }

    private static void releaseQuietly(ExecutorService executor) {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static Flyway migrate(PostgresConfig config, String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(withCurrentSchema(config.url(), schema), config.user(), config.password())
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
        return flyway;
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

    private static boolean tableExists(JdbcTemplate jdbc, String tableName) {
        Boolean value = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, tableName);
        return Boolean.TRUE.equals(value);
    }

    private static String randomSchema(String suffix) {
        return "gatex5a_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static void dropSchema(PostgresConfig config, String schema) {
        if (schema == null || !schema.matches("gatex5a_(fresh|upgrade|coverage|locking)_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("refusing to drop non-GateX5A schema");
        }
        jdbc(config, "public").execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
    }

    private static PostgresConfig requireLocalDisposableConfig() {
        PostgresConfig config = PostgresConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "local disposable PostgreSQL properties are not configured");
        }
        assertTrue(config.configured(), "missing required local disposable PostgreSQL properties");
        assertTrue(config.localhost(), "GateX-5A PostgreSQL test refuses non-local database URLs");
        return config;
    }

    private record Fixture(
            String strategyId,
            String strategyVersionId,
            String researchId,
            String backtestConfigId,
            String backtestRunId,
            String evaluationId,
            String publishId,
            UUID datasetId
    ) {
    }

    private record StateRow(
            long revision,
            int guardSchemaVersion,
            String artifactDigest,
            String manifestFingerprint,
            String manifestSchemaVersion,
            Timestamp identityBoundAt
    ) {
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
                return "localhost".equalsIgnoreCase(host)
                        || "127.0.0.1".equals(host)
                        || "::1".equals(host);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private static String property(String name) {
            return System.getProperty(name, "").trim();
        }
    }
}
