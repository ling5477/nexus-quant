package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionMutationCoordinationException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionMutationCoordinator;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionState;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionStateRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.VerifiedStrategyReleaseIdentity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PostgreSQL Strategy Release admission state persistence and first-binding boundary. */
@Repository
public class JdbcStrategyReleaseAdmissionStateRepository implements StrategyReleaseAdmissionStateRepository {

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionMutationCoordinator mutationCoordinator;

    public JdbcStrategyReleaseAdmissionStateRepository(
            JdbcTemplate jdbcTemplate,
            AdmissionMutationCoordinator mutationCoordinator
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.mutationCoordinator = Objects.requireNonNull(
                mutationCoordinator,
                "mutationCoordinator must not be null"
        );
    }

    @Override
    public StrategyReleaseAdmissionState loadByPublishRecordId(String publishRecordId) {
        if (publishRecordId == null || publishRecordId.isBlank()) {
            throw new AdmissionMutationCoordinationException("strategy release admission state id is missing");
        }
        return loadState(publishRecordId.trim());
    }

    @Override
    public StrategyReleaseAdmissionState bindVerifiedReleaseIdentity(VerifiedStrategyReleaseIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        return mutationCoordinator.withLockedAdmissionStates(
                List.of(identity.publishRecordId()),
                () -> bindUnderStateLock(identity)
        );
    }

    private StrategyReleaseAdmissionState bindUnderStateLock(VerifiedStrategyReleaseIdentity identity) {
        ReleaseFacts facts = loadReleaseFacts(identity.publishRecordId());
        if (!"SUCCEEDED".equals(facts.publishStatus())
                || facts.artifactStorageKey() == null
                || facts.manifestStorageKey() == null
                || !identity.strategyVersionId().equals(facts.strategyVersionId())
                || !identity.evaluationId().equals(facts.evaluationId())
                || !identity.datasetId().toString().equals(facts.datasetId())) {
            throw new AdmissionMutationCoordinationException("verified release facts changed before identity binding");
        }

        StrategyReleaseAdmissionState current = loadState(identity.publishRecordId());
        if (current.releaseArtifactDigest() != null) {
            throw new AdmissionMutationCoordinationException("strategy release identity is already bound");
        }

        Instant boundAt = Instant.now();
        int updated = jdbcTemplate.update(
                """
                        UPDATE strategy_release_admission_state
                        SET release_artifact_digest = ?,
                            manifest_fingerprint = ?,
                            manifest_schema_version = ?,
                            identity_bound_at = ?,
                            updated_at = ?
                        WHERE publish_record_id = ?
                          AND release_artifact_digest IS NULL
                          AND manifest_fingerprint IS NULL
                          AND manifest_schema_version IS NULL
                          AND identity_bound_at IS NULL
                        """,
                identity.releaseArtifactDigest(),
                identity.manifestFingerprint(),
                identity.manifestSchemaVersion(),
                Timestamp.from(boundAt),
                Timestamp.from(boundAt),
                identity.publishRecordId()
        );
        if (updated != 1) {
            throw new AdmissionMutationCoordinationException("strategy release identity first-binding failed");
        }
        return loadState(identity.publishRecordId());
    }

    private ReleaseFacts loadReleaseFacts(String publishRecordId) {
        List<ReleaseFacts> rows = jdbcTemplate.query(
                """
                        SELECT p.publish_status,
                               p.strategy_version_id,
                               p.eval_report_id,
                               r.dataset_snapshot_json ->> 'datasetId' AS dataset_id,
                               p.artifact_storage_key,
                               p.manifest_storage_key
                        FROM backtest_publish_records p
                        JOIN backtest_runs r ON r.backtest_run_id = p.backtest_run_id
                        WHERE p.publish_record_id = ?
                        """,
                (resultSet, rowNum) -> new ReleaseFacts(
                        resultSet.getString("publish_status"),
                        resultSet.getString("strategy_version_id"),
                        resultSet.getString("eval_report_id"),
                        resultSet.getString("dataset_id"),
                        resultSet.getString("artifact_storage_key"),
                        resultSet.getString("manifest_storage_key")
                ),
                publishRecordId
        );
        if (rows.size() != 1) {
            throw new AdmissionMutationCoordinationException("strategy release facts are missing");
        }
        return rows.getFirst();
    }

    private StrategyReleaseAdmissionState loadState(String publishRecordId) {
        List<StrategyReleaseAdmissionState> rows = jdbcTemplate.query(
                """
                        SELECT publish_record_id, admission_revision, guard_schema_version,
                               release_artifact_digest, manifest_fingerprint, manifest_schema_version,
                               identity_bound_at, created_at, updated_at
                        FROM strategy_release_admission_state
                        WHERE publish_record_id = ?
                        """,
                JdbcStrategyReleaseAdmissionStateRepository::mapState,
                publishRecordId
        );
        if (rows.size() != 1) {
            throw new AdmissionMutationCoordinationException("strategy release admission state is missing");
        }
        return rows.getFirst();
    }

    private static StrategyReleaseAdmissionState mapState(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp boundAt = resultSet.getTimestamp("identity_bound_at");
        return new StrategyReleaseAdmissionState(
                resultSet.getString("publish_record_id"),
                resultSet.getLong("admission_revision"),
                resultSet.getInt("guard_schema_version"),
                resultSet.getString("release_artifact_digest"),
                resultSet.getString("manifest_fingerprint"),
                resultSet.getString("manifest_schema_version"),
                boundAt == null ? null : boundAt.toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private record ReleaseFacts(
            String publishStatus,
            String strategyVersionId,
            String evaluationId,
            String datasetId,
            String artifactStorageKey,
            String manifestStorageKey
    ) {
    }
}
