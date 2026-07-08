package com.guidinglight.nexusquant.monitoring.infra.jdbc;

import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayOverviewFacts;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayOverviewFacts.LatestEvidenceFact;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayOverviewQueryPort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcIncidentReplayOverviewQueryRepository 是 GateS-6 Incident / Replay overview 的 JDBC read adapter。
 *
 * <p>职责：只通过 SELECT 聚合 shadow_run_events、shadow_consistency_reports、paper_run_alerts、
 * paper_run_recovery_events 和 trade_replay_records 本地事实。该 adapter 不提供 create/update/delete，
 * 不读取 credential/account/live order/ledger/private trading 表，不调用 runner、scheduler 或 adapter。
 */
@Repository
public class JdbcIncidentReplayOverviewQueryRepository implements IncidentReplayOverviewQueryPort {

    private static final String COUNTS_SQL = """
            SELECT
                (SELECT COUNT(*) FROM shadow_run_events) AS shadow_event_count,
                (SELECT COUNT(*) FROM shadow_consistency_reports WHERE comparison_status = 'DIVERGED') AS consistency_divergence_count,
                (SELECT COUNT(*) FROM paper_run_alerts) AS paper_alert_count,
                (SELECT COUNT(*) FROM paper_run_alerts WHERE severity = 'CRITICAL') AS critical_paper_alert_count,
                (SELECT COUNT(*) FROM paper_run_alerts WHERE severity = 'HIGH') AS high_paper_alert_count,
                (SELECT COUNT(*) FROM paper_run_recovery_events) AS recovery_event_count,
                (SELECT COUNT(*) FROM trade_replay_records) AS replay_event_count
            """;

    private static final String LATEST_EVIDENCE_SQL = """
            SELECT evidence_type,
                   source_id,
                   source_status,
                   summary,
                   occurred_at,
                   trace_id
            FROM (
                SELECT 'SHADOW_EVENT' AS evidence_type,
                       e.id::text AS source_id,
                       e.event_type AS source_status,
                       COALESCE(e.message, e.reason_code, e.event_type) AS summary,
                       e.created_at AS occurred_at,
                       e.trace_id AS trace_id
                FROM shadow_run_events e
                UNION ALL
                SELECT 'CONSISTENCY_DIVERGENCE' AS evidence_type,
                       c.id::text AS source_id,
                       c.comparison_status AS source_status,
                       CASE
                           WHEN jsonb_typeof(c.divergence_reasons) = 'array'
                               THEN CONCAT('Divergence reasons count: ', jsonb_array_length(c.divergence_reasons))
                           ELSE 'Divergence report exists'
                       END AS summary,
                       c.generated_at AS occurred_at,
                       c.trace_id AS trace_id
                FROM shadow_consistency_reports c
                WHERE c.comparison_status = 'DIVERGED'
                UNION ALL
                SELECT 'PAPER_ALERT' AS evidence_type,
                       a.alert_id AS source_id,
                       CONCAT(a.severity, ':', a.status) AS source_status,
                       COALESCE(a.title, a.alert_type) AS summary,
                       a.created_at AS occurred_at,
                       NULL AS trace_id
                FROM paper_run_alerts a
                UNION ALL
                SELECT 'RECOVERY_EVENT' AS evidence_type,
                       r.recovery_event_id AS source_id,
                       r.status AS source_status,
                       COALESCE(r.recovery_type, r.reason, r.status) AS summary,
                       r.created_at AS occurred_at,
                       NULL AS trace_id
                FROM paper_run_recovery_events r
                UNION ALL
                SELECT 'TRADE_REPLAY' AS evidence_type,
                       tr.replay_record_id AS source_id,
                       tr.event_type AS source_status,
                       COALESCE(tr.reason, tr.event_type) AS summary,
                       tr.replay_time AS occurred_at,
                       NULL AS trace_id
                FROM trade_replay_records tr
            ) evidence
            ORDER BY occurred_at DESC NULLS LAST, source_id DESC
            LIMIT 8
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcIncidentReplayOverviewQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * 加载 GateS-6 Incident / Replay overview facts。
     *
     * <p>所有 SQL 均为 SELECT：counts 只做 bounded 聚合，latest evidence 只取最近 8 条脱敏事实摘要。
     * 不读取 credential、account、live order、ledger 或 private provider 表。
     */
    @Override
    public IncidentReplayOverviewFacts loadOverviewFacts() {
        List<IncidentReplayOverviewFacts> countRows = jdbcTemplate.query(COUNTS_SQL, this::mapCounts, new Object[0]);
        IncidentReplayOverviewFacts counts = countRows.isEmpty()
                ? IncidentReplayOverviewFacts.empty()
                : countRows.getFirst();
        List<LatestEvidenceFact> latestEvidence = jdbcTemplate.query(
                LATEST_EVIDENCE_SQL,
                this::mapLatestEvidence,
                new Object[0]
        );
        return new IncidentReplayOverviewFacts(
                counts.shadowEventCount(),
                counts.consistencyDivergenceCount(),
                counts.paperAlertCount(),
                counts.criticalPaperAlertCount(),
                counts.highPaperAlertCount(),
                counts.recoveryEventCount(),
                counts.replayEventCount(),
                latestEvidence
        );
    }

    private IncidentReplayOverviewFacts mapCounts(ResultSet rs, int rowNum) throws SQLException {
        return new IncidentReplayOverviewFacts(
                rs.getLong("shadow_event_count"),
                rs.getLong("consistency_divergence_count"),
                rs.getLong("paper_alert_count"),
                rs.getLong("critical_paper_alert_count"),
                rs.getLong("high_paper_alert_count"),
                rs.getLong("recovery_event_count"),
                rs.getLong("replay_event_count"),
                List.of()
        );
    }

    private LatestEvidenceFact mapLatestEvidence(ResultSet rs, int rowNum) throws SQLException {
        return new LatestEvidenceFact(
                rs.getString("evidence_type"),
                rs.getString("source_id"),
                rs.getString("source_status"),
                rs.getString("summary"),
                toInstant(rs.getTimestamp("occurred_at")),
                rs.getString("trace_id")
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
