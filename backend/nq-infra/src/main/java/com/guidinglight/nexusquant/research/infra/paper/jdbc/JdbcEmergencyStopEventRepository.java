package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopEvent;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopStatus;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopTriggerType;
import com.guidinglight.nexusquant.research.domain.paper.port.EmergencyStopEventRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEmergencyStopEventRepository implements EmergencyStopEventRepository {

    private static final RowMapper<EmergencyStopEvent> ROW_MAPPER = JdbcEmergencyStopEventRepository::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public JdbcEmergencyStopEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(EmergencyStopEvent event) {
        jdbcTemplate.update("""
                INSERT INTO emergency_stop_events (
                    emergency_stop_id, paper_run_id, trigger_type, status, reason, triggered_by,
                    triggered_at, resolved_at, request_json, result_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?)
                """,
                event.emergencyStopId(), event.paperRunId(), event.triggerType().name(),
                event.status().name(), event.reason(), event.triggeredBy(),
                Timestamp.from(event.triggeredAt()), toTimestamp(event.resolvedAt()),
                event.requestJson(), event.resultJson(), Timestamp.from(event.createdAt()));
    }

    @Override
    public List<EmergencyStopEvent> listByRunId(String paperRunId) {
        return jdbcTemplate.query("""
                SELECT emergency_stop_id, paper_run_id, trigger_type, status, reason, triggered_by,
                       triggered_at, resolved_at, request_json::text AS request_json,
                       result_json::text AS result_json, created_at
                FROM emergency_stop_events WHERE paper_run_id = ? ORDER BY triggered_at DESC
                """, ROW_MAPPER, paperRunId);
    }

    @Override
    public boolean updateStatus(String emergencyStopId, EmergencyStopStatus status, Instant resolvedAt, String resultJson) {
        int updated = jdbcTemplate.update("""
                UPDATE emergency_stop_events SET status = ?, resolved_at = ?, result_json = CAST(? AS JSONB)
                WHERE emergency_stop_id = ?
                """, status.name(), toTimestamp(resolvedAt), resultJson, emergencyStopId);
        return updated > 0;
    }

    private static EmergencyStopEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        return new EmergencyStopEvent(
                rs.getString("emergency_stop_id"), rs.getString("paper_run_id"),
                EmergencyStopTriggerType.valueOf(rs.getString("trigger_type")),
                EmergencyStopStatus.valueOf(rs.getString("status")),
                rs.getString("reason"), rs.getString("triggered_by"),
                rs.getTimestamp("triggered_at").toInstant(),
                resolvedAt == null ? null : resolvedAt.toInstant(),
                rs.getString("request_json"), rs.getString("result_json"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
