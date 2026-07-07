package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.LatestEventFact;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.LatestSnapshotFact;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.SnapshotFacts;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownQueryPort;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcPaperShadowConsistencyDrilldownQueryRepository 是 GateS-2 drilldown 的 JDBC read adapter。
 *
 * <p>职责：围绕单个 `shadowRunId` 只读查询 Shadow Run 主事实、latest consistency report、
 * snapshot summary 和 event summary。所有 SQL 都是 SELECT；该 adapter 没有 create/update/delete 方法，
 * 不调用 runner/scheduler/adapter/credential/order/account/ledger，也不读取 Paper/Strategy/MarketData/Risk
 * 等深层表，只保留 id anchor。
 */
@Repository
public class JdbcPaperShadowConsistencyDrilldownQueryRepository implements PaperShadowConsistencyDrilldownQueryPort {

    private static final String RUN_SELECT = """
            SELECT id, strategy_version_id, dataset_id, evaluation_id, publish_id, paper_run_id,
                   status, window_start, window_end, side_effect_policy::text AS side_effect_policy,
                   no_order_submission, no_credential_access, no_private_endpoint, no_ledger_mutation,
                   no_account_mutation, no_external_private_io, authorization_boundary, request_id,
                   idempotency_key, trace_id, blockers::text AS blockers, warnings::text AS warnings,
                   next_steps::text AS next_steps, version, created_at, updated_at, started_at,
                   stopped_at, completed_at
            FROM shadow_runs
            """;

    private static final String REPORT_SELECT = """
            SELECT id, shadow_run_id, paper_run_id, comparison_status, metric_delta::text AS metric_delta,
                   divergence_reasons::text AS divergence_reasons, limitations::text AS limitations,
                   generated_at, trace_id, created_at
            FROM shadow_consistency_reports
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdbcPaperShadowConsistencyDrilldownQueryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 加载单 run drilldown facts。
     *
     * <p>查询策略：先按 `shadow_runs.id` 定位主事实；run 不存在时不继续查其他表，返回 missingRun。
     * run 存在时分别读取该 run 的 latest consistency、snapshot 类型计数、latest snapshot types、
     * event 总数、latest event 和 latest snapshot anchor。所有查询都带 `shadow_run_id = ?` 约束。
     *
     * @param shadowRunId 本地 Shadow Run id
     * @return drilldown facts；run 不存在时由 service 映射为 404
     */
    @Override
    public PaperShadowConsistencyDrilldownFacts loadDrilldownFacts(UUID shadowRunId) {
        Objects.requireNonNull(shadowRunId, "shadowRunId must not be null");
        Optional<ShadowRun> run = queryOptional(
                RUN_SELECT + " WHERE id = ?",
                this::mapRun,
                shadowRunId
        );
        if (run.isEmpty()) {
            return PaperShadowConsistencyDrilldownFacts.missingRun();
        }

        Optional<ShadowConsistencyReport> latestReport = queryOptional(
                REPORT_SELECT + " WHERE shadow_run_id = ? ORDER BY generated_at DESC, created_at DESC, id DESC LIMIT 1",
                this::mapReport,
                shadowRunId
        );
        SnapshotFacts snapshotFacts = loadSnapshotFacts(shadowRunId);
        long totalEvents = count("SELECT COUNT(*) FROM shadow_run_events WHERE shadow_run_id = ?", shadowRunId);
        Optional<LatestEventFact> latestEvent = queryOptional("""
                        SELECT id::text AS event_id, event_type, reason_code, created_at
                        FROM shadow_run_events
                        WHERE shadow_run_id = ?
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """,
                this::mapLatestEvent,
                shadowRunId
        );
        Optional<LatestSnapshotFact> latestSnapshot = queryOptional("""
                        SELECT id::text AS snapshot_id, snapshot_type, schema_version, captured_at, checksum
                        FROM shadow_run_snapshots
                        WHERE shadow_run_id = ?
                        ORDER BY captured_at DESC, created_at DESC, id DESC
                        LIMIT 1
                        """,
                this::mapLatestSnapshot,
                shadowRunId
        );

        return new PaperShadowConsistencyDrilldownFacts(
                run,
                latestReport,
                snapshotFacts,
                totalEvents,
                latestEvent,
                latestSnapshot
        );
    }

    private SnapshotFacts loadSnapshotFacts(UUID shadowRunId) {
        Optional<SnapshotFacts> aggregate = queryOptional("""
                        SELECT COUNT(*) AS total_snapshots,
                               COALESCE(SUM(CASE WHEN snapshot_type = 'INPUT_MARKETDATA' THEN 1 ELSE 0 END), 0) AS input_marketdata_snapshots,
                               COALESCE(SUM(CASE WHEN snapshot_type = 'STRATEGY_DECISION' THEN 1 ELSE 0 END), 0) AS strategy_decision_snapshots,
                               COALESCE(SUM(CASE WHEN snapshot_type = 'RISK_PREFLIGHT' THEN 1 ELSE 0 END), 0) AS risk_preflight_snapshots,
                               COALESCE(SUM(CASE WHEN snapshot_type = 'ORDER_INTENT_PREVIEW' THEN 1 ELSE 0 END), 0) AS order_intent_preview_snapshots,
                               MAX(captured_at) AS latest_snapshot_at
                        FROM shadow_run_snapshots
                        WHERE shadow_run_id = ?
                        """,
                (rs, rowNum) -> new SnapshotFacts(
                        rs.getLong("total_snapshots"),
                        rs.getLong("input_marketdata_snapshots"),
                        rs.getLong("strategy_decision_snapshots"),
                        rs.getLong("risk_preflight_snapshots"),
                        rs.getLong("order_intent_preview_snapshots"),
                        toInstant(rs.getTimestamp("latest_snapshot_at")),
                        List.of()
                ),
                shadowRunId
        );
        List<String> latestTypes = jdbcTemplate.query("""
                        SELECT DISTINCT snapshot_type
                        FROM shadow_run_snapshots
                        WHERE shadow_run_id = ?
                          AND captured_at = (
                              SELECT MAX(captured_at)
                              FROM shadow_run_snapshots
                              WHERE shadow_run_id = ?
                          )
                        ORDER BY snapshot_type ASC
                        """,
                (rs, rowNum) -> rs.getString("snapshot_type"),
                shadowRunId,
                shadowRunId
        );
        SnapshotFacts value = aggregate.orElseGet(SnapshotFacts::empty);
        return new SnapshotFacts(
                value.totalSnapshots(),
                value.inputMarketdataSnapshots(),
                value.strategyDecisionSnapshots(),
                value.riskPreflightSnapshots(),
                value.orderIntentPreviewSnapshots(),
                value.latestSnapshotAt(),
                latestTypes
        );
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private <T> Optional<T> queryOptional(String sql, RowMapper<T> rowMapper, Object... args) {
        List<T> rows = jdbcTemplate.query(sql, rowMapper, args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private ShadowRun mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new ShadowRun(
                rs.getObject("id", UUID.class),
                rs.getString("strategy_version_id"),
                rs.getObject("dataset_id", UUID.class),
                rs.getString("evaluation_id"),
                rs.getString("publish_id"),
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

    private LatestEventFact mapLatestEvent(ResultSet rs, int rowNum) throws SQLException {
        return new LatestEventFact(
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("reason_code"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private LatestSnapshotFact mapLatestSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new LatestSnapshotFact(
                rs.getString("snapshot_id"),
                rs.getString("snapshot_type"),
                rs.getString("schema_version"),
                rs.getTimestamp("captured_at").toInstant(),
                rs.getString("checksum")
        );
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse paper shadow consistency drilldown JSONB field", ex);
        }
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
