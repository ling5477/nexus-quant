package com.guidinglight.nexusquant.monitoring.infra.jdbc;

import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewFacts;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewFacts.ReviewEvidenceFact;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewQueryPort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcIncidentReplayReviewOverviewQueryRepository 是 GateT-3 Incident / Replay Review overview 的 JDBC read adapter。
 *
 * <p>职责：只通过 SELECT 读取 shadow_run_events、shadow_consistency_reports、paper_run_alerts、
 * paper_run_recovery_events 和 trade_replay_records 的本地事实，并最小 join shadow_runs 取得 run anchor。
 * 该 adapter 不提供 create/update/delete/review/acknowledge/escalation/closeout 方法，不读取
 * credential/account/live order/ledger/private trading 表，不读取 raw JSONB payload，也不调用 runner、
 * scheduler 或交易所 adapter。
 */
@Repository
public class JdbcIncidentReplayReviewOverviewQueryRepository implements IncidentReplayReviewOverviewQueryPort {

    private static final String REVIEW_EVIDENCE_SQL = """
            SELECT source_type,
                   source_id,
                   source_status,
                   source_severity,
                   incident_evidence_id,
                   replay_record_id,
                   shadow_run_id,
                   paper_run_id,
                   consistency_report_id,
                   summary,
                   occurred_at,
                   trace_id
            FROM (
                SELECT 'SHADOW_EVENT' AS source_type,
                       e.id::text AS source_id,
                       e.event_type AS source_status,
                       CASE
                           WHEN e.event_type IN ('FAILED', 'ILLEGAL_STATE_TRANSITION_ATTEMPT') THEN 'HIGH'
                           WHEN e.event_type IN ('PRECHECK_BLOCKED', 'CANCELLED') THEN 'MEDIUM'
                           ELSE 'INFO'
                       END AS source_severity,
                       CONCAT('shadow-event:', e.id::text) AS incident_evidence_id,
                       NULL AS replay_record_id,
                       e.shadow_run_id::text AS shadow_run_id,
                       sr.paper_run_id AS paper_run_id,
                       NULL AS consistency_report_id,
                       COALESCE(e.reason_code, e.event_type) AS summary,
                       e.created_at AS occurred_at,
                       e.trace_id AS trace_id
                FROM shadow_run_events e
                LEFT JOIN shadow_runs sr ON sr.id = e.shadow_run_id
                UNION ALL
                SELECT 'CONSISTENCY_DIVERGENCE' AS source_type,
                       c.id::text AS source_id,
                       c.comparison_status AS source_status,
                       CASE
                           WHEN c.comparison_status = 'FAILED' THEN 'CRITICAL'
                           WHEN c.comparison_status = 'DIVERGED' THEN 'HIGH'
                           WHEN c.comparison_status IN ('PARTIAL', 'NOT_COMPARABLE') THEN 'MEDIUM'
                           ELSE 'INFO'
                       END AS source_severity,
                       CONCAT('consistency-report:', c.id::text) AS incident_evidence_id,
                       NULL AS replay_record_id,
                       c.shadow_run_id::text AS shadow_run_id,
                       COALESCE(c.paper_run_id, sr.paper_run_id) AS paper_run_id,
                       c.id::text AS consistency_report_id,
                       CONCAT(
                           'Consistency status ', c.comparison_status,
                           '; divergenceReasons=', CASE
                               WHEN jsonb_typeof(c.divergence_reasons) = 'array' THEN jsonb_array_length(c.divergence_reasons)
                               ELSE 0
                           END,
                           '; limitations=', CASE
                               WHEN jsonb_typeof(c.limitations) = 'array' THEN jsonb_array_length(c.limitations)
                               ELSE 0
                           END
                       ) AS summary,
                       c.generated_at AS occurred_at,
                       c.trace_id AS trace_id
                FROM shadow_consistency_reports c
                LEFT JOIN shadow_runs sr ON sr.id = c.shadow_run_id
                WHERE c.comparison_status IN ('DIVERGED', 'FAILED', 'PARTIAL', 'NOT_COMPARABLE')
                UNION ALL
                SELECT 'PAPER_ALERT' AS source_type,
                       a.alert_id AS source_id,
                       a.status AS source_status,
                       a.severity AS source_severity,
                       CONCAT('paper-alert:', a.alert_id) AS incident_evidence_id,
                       NULL AS replay_record_id,
                       NULL AS shadow_run_id,
                       a.paper_run_id AS paper_run_id,
                       NULL AS consistency_report_id,
                       COALESCE(a.title, a.alert_type) AS summary,
                       a.created_at AS occurred_at,
                       NULL AS trace_id
                FROM paper_run_alerts a
                UNION ALL
                SELECT 'RECOVERY_EVENT' AS source_type,
                       r.recovery_event_id AS source_id,
                       r.status AS source_status,
                       CASE
                           WHEN r.status = 'FAILED' THEN 'HIGH'
                           WHEN r.status = 'SKIPPED' THEN 'MEDIUM'
                           ELSE 'INFO'
                       END AS source_severity,
                       CONCAT('recovery-event:', r.recovery_event_id) AS incident_evidence_id,
                       NULL AS replay_record_id,
                       NULL AS shadow_run_id,
                       r.paper_run_id AS paper_run_id,
                       NULL AS consistency_report_id,
                       COALESCE(r.recovery_type, r.status) AS summary,
                       r.created_at AS occurred_at,
                       NULL AS trace_id
                FROM paper_run_recovery_events r
                UNION ALL
                SELECT 'TRADE_REPLAY' AS source_type,
                       tr.replay_record_id AS source_id,
                       tr.event_type AS source_status,
                       'INFO' AS source_severity,
                       CONCAT('trade-replay:', tr.replay_record_id) AS incident_evidence_id,
                       tr.replay_record_id AS replay_record_id,
                       NULL AS shadow_run_id,
                       tr.paper_run_id AS paper_run_id,
                       NULL AS consistency_report_id,
                       COALESCE(tr.reason, tr.event_type) AS summary,
                       tr.replay_time AS occurred_at,
                       NULL AS trace_id
                FROM trade_replay_records tr
            ) evidence
            ORDER BY occurred_at DESC NULLS LAST, source_id DESC
            LIMIT 50
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcIncidentReplayReviewOverviewQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * 加载 bounded incident replay review facts。
     *
     * <p>查询策略：按 evidence 发生时间倒序读取最多 50 条本地诊断事实；只读取 id、状态、severity、
     * summary 和 trace anchor，不读取 JSONB payload，不读取 credential/account/order/ledger/private provider 表，
     * 不执行写 SQL。
     *
     * @return SELECT-only overview facts；没有本地 facts 时返回空集合
     */
    @Override
    public IncidentReplayReviewOverviewFacts loadOverviewFacts() {
        List<ReviewEvidenceFact> evidence = jdbcTemplate.query(REVIEW_EVIDENCE_SQL, this::mapEvidence, new Object[0]);
        return new IncidentReplayReviewOverviewFacts(evidence);
    }

    private ReviewEvidenceFact mapEvidence(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewEvidenceFact(
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("source_status"),
                rs.getString("source_severity"),
                rs.getString("incident_evidence_id"),
                rs.getString("replay_record_id"),
                rs.getString("shadow_run_id"),
                rs.getString("paper_run_id"),
                rs.getString("consistency_report_id"),
                rs.getString("summary"),
                toInstant(rs.getTimestamp("occurred_at")),
                rs.getString("trace_id")
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
