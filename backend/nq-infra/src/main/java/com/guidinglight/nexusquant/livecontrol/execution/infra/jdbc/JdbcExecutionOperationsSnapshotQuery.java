package com.guidinglight.nexusquant.livecontrol.execution.infra.jdbc;

import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionOperationsSnapshot;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionOperationsSnapshotQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;


/**
 * 从既有 V38/V39 facts 读取一行脱敏运维快照；不创建事实源，不读取 credential material。
 */
@Repository
public class JdbcExecutionOperationsSnapshotQuery implements ExecutionOperationsSnapshotQuery {

    private final JdbcTemplate jdbc;

    public JdbcExecutionOperationsSnapshotQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ExecutionOperationsSnapshot currentSnapshot() {
        return jdbc.queryForObject("""
                WITH latest_session AS (
                    SELECT * FROM live_sessions ORDER BY updated_at DESC, session_id DESC LIMIT 1
                ), latest_approval AS (
                    SELECT a.* FROM operator_approvals a
                    JOIN latest_session s ON s.session_id=a.session_id
                    ORDER BY a.approved_at DESC, a.approval_id DESC LIMIT 1
                ), latest_intent AS (
                    SELECT i.* FROM execution_intents i
                    JOIN latest_session s ON s.session_id=i.session_id
                    ORDER BY i.created_at DESC, i.intent_id DESC LIMIT 1
                ), latest_receipt AS (
                    SELECT r.* FROM execution_receipts r
                    JOIN latest_intent i ON i.intent_id=r.intent_id
                    ORDER BY r.attempt_no DESC, r.receipt_id DESC LIMIT 1
                )
                SELECT CURRENT_TIMESTAMP observed_at,
                       COALESCE((SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'),'MISSING') kill_state,
                       COALESCE((SELECT session_id::text FROM latest_session),'-') session_id,
                       COALESCE((SELECT state FROM latest_session),'NOT_OBSERVED') session_state,
                       COALESCE((SELECT decision FROM latest_approval),'NOT_OBSERVED') approval_state,
                       COALESCE((SELECT risk_limit_set_digest FROM latest_session),'-') risk_digest,
                       CASE WHEN EXISTS(SELECT 1 FROM latest_intent WHERE claimed_by IS NOT NULL)
                            THEN 'OBSERVED_FROM_INTENT' ELSE 'NOT_OBSERVED' END worker_health,
                       COALESCE((SELECT claimed_by FROM latest_intent),'-') worker_identity,
                       'NOT_RECORDED' release_identity,
                       'NOT_RECORDED' release_digest,
                       COALESCE((SELECT intent_id::text FROM latest_intent),'-') intent_id,
                       COALESCE((SELECT state FROM latest_intent),'NOT_OBSERVED') intent_state,
                       COALESCE((SELECT outcome FROM latest_receipt),'NOT_OBSERVED') receipt_state
                """, JdbcExecutionOperationsSnapshotQuery::map);
    }

    private static ExecutionOperationsSnapshot map(ResultSet row, int ignored) throws SQLException {
        return new ExecutionOperationsSnapshot(
                row.getObject("observed_at", java.time.OffsetDateTime.class).toInstant(),
                row.getString("kill_state"), row.getString("session_id"), row.getString("session_state"),
                row.getString("approval_state"), row.getString("risk_digest"), row.getString("worker_health"),
                row.getString("worker_identity"), row.getString("release_identity"), row.getString("release_digest"),
                row.getString("intent_id"), row.getString("intent_state"), row.getString("receipt_state"));
    }
}
