package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunListQuery;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunIdempotencyConflictException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunOptimisticLockException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshotType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateMachine;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateTransitionException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatusUpdateResult;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shadow Run 本地事实 JDBC adapter。
 *
 * <p>本 adapter 只写入 GateR-2 Shadow Run 本地 fact model。它不调用外部交易所、不读取
 * credential material、不写真实账户/资金/订单/ledger，也不启动 Shadow runner。
 */
@Repository
public class JdbcShadowRunFactRepository implements ShadowRunFactRepository {

    private static final String RUN_SELECT = """
            SELECT id, strategy_version_id, dataset_id, evaluation_id, publish_id, artifact_digest, paper_run_id,
                   status, window_start, window_end, side_effect_policy::text AS side_effect_policy,
                   no_order_submission, no_credential_access, no_private_endpoint, no_ledger_mutation,
                   no_account_mutation, no_external_private_io, authorization_boundary, request_id,
                   idempotency_key, trace_id, blockers::text AS blockers, warnings::text AS warnings,
                   next_steps::text AS next_steps, version, created_at, updated_at, started_at,
                   stopped_at, completed_at
            FROM shadow_runs
            """;

    private static final String EVENT_SELECT = """
            SELECT id, shadow_run_id, event_type, from_status, to_status, reason_code, message,
                   metadata::text AS metadata, request_id, trace_id, created_at
            FROM shadow_run_events
            """;

    private static final String SNAPSHOT_SELECT = """
            SELECT id, shadow_run_id, snapshot_type, sequence_no, source, schema_version, checksum,
                   payload::text AS payload, captured_at, trace_id, created_at
            FROM shadow_run_snapshots
            """;

    private static final String REPORT_SELECT = """
            SELECT id, shadow_run_id, paper_run_id, comparison_status, metric_delta::text AS metric_delta,
                   divergence_reasons::text AS divergence_reasons, limitations::text AS limitations,
                   generated_at, trace_id, created_at
            FROM shadow_consistency_reports
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ShadowRunStateMachine stateMachine;
    private final JdbcShadowRunIllegalTransitionAuditWriter illegalTransitionAuditWriter;

    @Autowired
    public JdbcShadowRunFactRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            JdbcShadowRunIllegalTransitionAuditWriter illegalTransitionAuditWriter
    ) {
        this(jdbcTemplate, objectMapper, new ShadowRunStateMachine(), illegalTransitionAuditWriter);
    }

    JdbcShadowRunFactRepository(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ShadowRunStateMachine stateMachine,
            JdbcShadowRunIllegalTransitionAuditWriter illegalTransitionAuditWriter
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine must not be null");
        this.illegalTransitionAuditWriter = Objects.requireNonNull(
                illegalTransitionAuditWriter,
                "illegalTransitionAuditWriter must not be null"
        );
    }

    @Override
    public ShadowRun create(ShadowRun run) {
        Objects.requireNonNull(run, "run must not be null");
        jdbcTemplate.update(
                """
                        INSERT INTO shadow_runs (
                            id, strategy_version_id, dataset_id, evaluation_id, publish_id, artifact_digest, paper_run_id,
                            status, window_start, window_end, side_effect_policy,
                            no_order_submission, no_credential_access, no_private_endpoint,
                            no_ledger_mutation, no_account_mutation, no_external_private_io,
                            authorization_boundary, request_id, idempotency_key, trace_id,
                            blockers, warnings, next_steps, version,
                            created_at, updated_at, started_at, stopped_at, completed_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB),
                                  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                                  CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), ?,
                                  ?, ?, ?, ?, ?)
                        ON CONFLICT (idempotency_key) DO NOTHING
                        """,
                run.id(),
                run.strategyVersionId(),
                run.datasetId(),
                run.evaluationId(),
                run.publishId(),
                run.artifactDigest(),
                run.paperRunId(),
                run.status().name(),
                toTimestamp(run.windowStart()),
                toTimestamp(run.windowEnd()),
                writeJson(run.sideEffectPolicy()),
                run.noOrderSubmission(),
                run.noCredentialAccess(),
                run.noPrivateEndpoint(),
                run.noLedgerMutation(),
                run.noAccountMutation(),
                run.noExternalPrivateIo(),
                run.authorizationBoundary().name(),
                run.requestId(),
                run.idempotencyKey(),
                run.traceId(),
                writeJson(run.blockers()),
                writeJson(run.warnings()),
                writeJson(run.nextSteps()),
                run.version(),
                Timestamp.from(run.createdAt()),
                Timestamp.from(run.updatedAt()),
                toTimestamp(run.startedAt()),
                toTimestamp(run.stoppedAt()),
                toTimestamp(run.completedAt())
        );
        ShadowRun persisted = findByIdempotencyKey(run.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("failed to create or load shadow run"));
        requireSameReleaseProvenance(run, persisted);
        return persisted;
    }

    @Override
    public Optional<ShadowRun> findById(UUID shadowRunId) {
        List<ShadowRun> rows = jdbcTemplate.query(
                RUN_SELECT + " WHERE id = ?",
                runRowMapper(),
                shadowRunId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<ShadowRun> findByIdempotencyKey(String idempotencyKey) {
        List<ShadowRun> rows = jdbcTemplate.query(
                RUN_SELECT + " WHERE idempotency_key = ?",
                runRowMapper(),
                idempotencyKey
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<ShadowRun> listRuns(ShadowRunListQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        SqlWhereClause whereClause = runWhereClause(query);
        List<Object> args = new ArrayList<>(whereClause.args());
        args.add(query.limit());
        args.add(query.offset());
        return jdbcTemplate.query(
                RUN_SELECT
                        + whereClause.sql()
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                runRowMapper(),
                args.toArray()
        );
    }

    @Override
    public long countRuns(ShadowRunListQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        SqlWhereClause whereClause = runWhereClause(query);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shadow_runs" + whereClause.sql(),
                Long.class,
                whereClause.args().toArray()
        );
        return total == null ? 0L : total;
    }

    @Override
    public void appendEvent(ShadowRunEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        jdbcTemplate.update(
                """
                        INSERT INTO shadow_run_events (
                            id, shadow_run_id, event_type, from_status, to_status, reason_code, message,
                            metadata, request_id, trace_id, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?)
                        """,
                event.id(),
                event.shadowRunId(),
                event.eventType().name(),
                event.fromStatus() == null ? null : event.fromStatus().name(),
                event.toStatus() == null ? null : event.toStatus().name(),
                event.reasonCode(),
                event.message(),
                writeJson(event.metadata()),
                event.requestId(),
                event.traceId(),
                Timestamp.from(event.createdAt())
        );
    }

    @Override
    public void appendSnapshot(ShadowRunSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        jdbcTemplate.update(
                """
                        INSERT INTO shadow_run_snapshots (
                            id, shadow_run_id, snapshot_type, sequence_no, source, schema_version,
                            checksum, payload, captured_at, trace_id, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?)
                        """,
                snapshot.id(),
                snapshot.shadowRunId(),
                snapshot.snapshotType().name(),
                snapshot.sequenceNo(),
                snapshot.source(),
                snapshot.schemaVersion(),
                snapshot.checksum(),
                writeJson(snapshot.payload()),
                Timestamp.from(snapshot.capturedAt()),
                snapshot.traceId(),
                Timestamp.from(snapshot.createdAt())
        );
    }

    @Override
    public ShadowConsistencyReport createConsistencyReport(ShadowConsistencyReport report) {
        Objects.requireNonNull(report, "report must not be null");
        jdbcTemplate.update(
                """
                        INSERT INTO shadow_consistency_reports (
                            id, shadow_run_id, paper_run_id, comparison_status, metric_delta,
                            divergence_reasons, limitations, generated_at, trace_id, created_at
                        ) VALUES (?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?)
                        """,
                report.id(),
                report.shadowRunId(),
                report.paperRunId(),
                report.comparisonStatus().name(),
                writeJson(report.metricDelta()),
                writeJson(report.divergenceReasons()),
                writeJson(report.limitations()),
                Timestamp.from(report.generatedAt()),
                report.traceId(),
                Timestamp.from(report.createdAt())
        );
        return report;
    }

    @Transactional
    @Override
    public ShadowRunStatusUpdateResult updateStatus(
            UUID shadowRunId,
            ShadowRunStatus toStatus,
            long expectedVersion,
            String reasonCode,
            String message,
            String requestId,
            String traceId
    ) {
        ShadowRun current = findById(shadowRunId)
                .orElseThrow(() -> new IllegalArgumentException("shadow run not found: " + shadowRunId));
        if (current.version() != expectedVersion) {
            throw new ShadowRunOptimisticLockException(shadowRunId, expectedVersion);
        }
        try {
            stateMachine.transition(current.status(), toStatus);
        } catch (ShadowRunStateTransitionException ex) {
            illegalTransitionAuditWriter.writeIllegalTransitionAttempt(current, toStatus, ex, message, requestId, traceId);
            throw ex;
        }

        Instant updatedAt = Instant.now();
        int updated = jdbcTemplate.update(
                """
                        UPDATE shadow_runs
                        SET status = ?,
                            version = version + 1,
                            updated_at = ?,
                            started_at = CASE WHEN ? = 'RUNNING' AND started_at IS NULL THEN ? ELSE started_at END,
                            stopped_at = CASE WHEN ? IN ('STOPPED', 'CANCELLED') AND stopped_at IS NULL THEN ? ELSE stopped_at END,
                            completed_at = CASE WHEN ? IN ('COMPLETED', 'BLOCKED', 'FAILED') AND completed_at IS NULL THEN ? ELSE completed_at END
                        WHERE id = ?
                          AND status = ?
                          AND version = ?
                        """,
                toStatus.name(),
                Timestamp.from(updatedAt),
                toStatus.name(),
                Timestamp.from(updatedAt),
                toStatus.name(),
                Timestamp.from(updatedAt),
                toStatus.name(),
                Timestamp.from(updatedAt),
                shadowRunId,
                current.status().name(),
                expectedVersion
        );
        if (updated != 1) {
            throw new ShadowRunOptimisticLockException(shadowRunId, expectedVersion);
        }
        appendEvent(new ShadowRunEvent(
                UUID.randomUUID(),
                shadowRunId,
                eventTypeFor(toStatus),
                current.status(),
                toStatus,
                reasonCode,
                message,
                objectMapper.createObjectNode(),
                requestId,
                traceId,
                updatedAt
        ));
        return new ShadowRunStatusUpdateResult(shadowRunId, current.status(), toStatus, expectedVersion, expectedVersion + 1);
    }

    @Override
    public List<ShadowRunEvent> listEvents(UUID shadowRunId) {
        return jdbcTemplate.query(
                EVENT_SELECT + " WHERE shadow_run_id = ? ORDER BY created_at ASC, id ASC",
                eventRowMapper(),
                shadowRunId
        );
    }

    @Override
    public List<ShadowRunSnapshot> listSnapshots(UUID shadowRunId) {
        return jdbcTemplate.query(
                SNAPSHOT_SELECT + " WHERE shadow_run_id = ? ORDER BY snapshot_type ASC, sequence_no ASC",
                snapshotRowMapper(),
                shadowRunId
        );
    }

    @Override
    public Optional<ShadowConsistencyReport> findLatestReport(UUID shadowRunId) {
        List<ShadowConsistencyReport> rows = jdbcTemplate.query(
                REPORT_SELECT + " WHERE shadow_run_id = ? ORDER BY generated_at DESC, created_at DESC, id DESC LIMIT 1",
                reportRowMapper(),
                shadowRunId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private ShadowRunEventType eventTypeFor(ShadowRunStatus toStatus) {
        return switch (toStatus) {
            case CREATED -> ShadowRunEventType.CREATED;
            case PRECHECKING -> ShadowRunEventType.PRECHECK_STARTED;
            case READY -> ShadowRunEventType.PRECHECK_PASSED;
            case RUNNING -> ShadowRunEventType.RUN_STARTED;
            case STOP_REQUESTED -> ShadowRunEventType.STOP_REQUESTED;
            case STOPPED -> ShadowRunEventType.STOPPED;
            case COMPLETED -> ShadowRunEventType.COMPLETED;
            case BLOCKED -> ShadowRunEventType.PRECHECK_BLOCKED;
            case FAILED -> ShadowRunEventType.FAILED;
            case CANCELLED -> ShadowRunEventType.CANCELLED;
        };
    }

    /**
     * 幂等键只允许复用同一 release provenance；否则静默返回旧行会让调用方误认错误绑定已创建。
     */
    private static void requireSameReleaseProvenance(ShadowRun requested, ShadowRun persisted) {
        if (!Objects.equals(requested.publishId(), persisted.publishId())
                || !Objects.equals(requested.artifactDigest(), persisted.artifactDigest())) {
            throw new ShadowRunIdempotencyConflictException();
        }
    }

    private RowMapper<ShadowRun> runRowMapper() {
        return this::mapRun;
    }

    private RowMapper<ShadowRunEvent> eventRowMapper() {
        return this::mapEvent;
    }

    private RowMapper<ShadowRunSnapshot> snapshotRowMapper() {
        return this::mapSnapshot;
    }

    private RowMapper<ShadowConsistencyReport> reportRowMapper() {
        return this::mapReport;
    }

    private SqlWhereClause runWhereClause(ShadowRunListQuery query) {
        List<String> predicates = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (query.status() != null) {
            predicates.add("status = ?");
            args.add(query.status().name());
        }
        if (query.strategyVersionId() != null) {
            predicates.add("strategy_version_id = ?");
            args.add(query.strategyVersionId());
        }
        if (query.datasetId() != null) {
            predicates.add("dataset_id = ?");
            args.add(query.datasetId());
        }
        if (query.paperRunId() != null) {
            predicates.add("paper_run_id = ?");
            args.add(query.paperRunId());
        }
        String sql = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
        return new SqlWhereClause(sql, args);
    }

    private ShadowRun mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new ShadowRun(
                rs.getObject("id", UUID.class),
                rs.getString("strategy_version_id"),
                rs.getObject("dataset_id", UUID.class),
                rs.getString("evaluation_id"),
                rs.getString("publish_id"),
                rs.getString("artifact_digest"),
                rs.getString("paper_run_id"),
                ShadowRunStatus.fromDatabase(rs.getString("status")),
                toInstant(rs.getTimestamp("window_start")),
                toInstant(rs.getTimestamp("window_end")),
                readJson(rs.getString("side_effect_policy")),
                rs.getBoolean("no_order_submission"),
                rs.getBoolean("no_credential_access"),
                rs.getBoolean("no_private_endpoint"),
                rs.getBoolean("no_ledger_mutation"),
                rs.getBoolean("no_account_mutation"),
                rs.getBoolean("no_external_private_io"),
                ShadowRunAuthorizationBoundary.valueOf(rs.getString("authorization_boundary")),
                rs.getString("request_id"),
                rs.getString("idempotency_key"),
                rs.getString("trace_id"),
                readJson(rs.getString("blockers")),
                readJson(rs.getString("warnings")),
                readJson(rs.getString("next_steps")),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("stopped_at")),
                toInstant(rs.getTimestamp("completed_at"))
        );
    }

    private ShadowRunEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        String fromStatus = rs.getString("from_status");
        String toStatus = rs.getString("to_status");
        return new ShadowRunEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("shadow_run_id", UUID.class),
                ShadowRunEventType.valueOf(rs.getString("event_type")),
                fromStatus == null ? null : ShadowRunStatus.fromDatabase(fromStatus),
                toStatus == null ? null : ShadowRunStatus.fromDatabase(toStatus),
                rs.getString("reason_code"),
                rs.getString("message"),
                readJson(rs.getString("metadata")),
                rs.getString("request_id"),
                rs.getString("trace_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private ShadowRunSnapshot mapSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new ShadowRunSnapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("shadow_run_id", UUID.class),
                ShadowRunSnapshotType.valueOf(rs.getString("snapshot_type")),
                rs.getInt("sequence_no"),
                rs.getString("source"),
                rs.getString("schema_version"),
                rs.getString("checksum"),
                readJson(rs.getString("payload")),
                rs.getTimestamp("captured_at").toInstant(),
                rs.getString("trace_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private ShadowConsistencyReport mapReport(ResultSet rs, int rowNum) throws SQLException {
        return new ShadowConsistencyReport(
                rs.getObject("id", UUID.class),
                rs.getObject("shadow_run_id", UUID.class),
                rs.getString("paper_run_id"),
                ShadowConsistencyComparisonStatus.valueOf(rs.getString("comparison_status")),
                readJson(rs.getString("metric_delta")),
                readJson(rs.getString("divergence_reasons")),
                readJson(rs.getString("limitations")),
                rs.getTimestamp("generated_at").toInstant(),
                rs.getString("trace_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse shadow run JSONB field", ex);
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize shadow run JSONB field", ex);
        }
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record SqlWhereClause(String sql, List<Object> args) {
    }
}
