package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperRunAlertRepository implements PaperRunAlertRepository {

    private static final RowMapper<PaperRunAlert> ROW_MAPPER = JdbcPaperRunAlertRepository::mapRow;
    private static final String BASE_SELECT = """
            SELECT alert_id, paper_run_id, alert_type, severity, status, title, message, source,
                   event_snapshot_json::text AS event_snapshot_json, acknowledged_by,
                   acknowledged_at, resolved_at, created_at, updated_at
            FROM paper_run_alerts
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperRunAlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(PaperRunAlert alert) {
        jdbcTemplate.update("""
                INSERT INTO paper_run_alerts (
                    alert_id, paper_run_id, alert_type, severity, status, title, message, source,
                    event_snapshot_json, acknowledged_by, acknowledged_at, resolved_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, ?)
                """,
                alert.alertId(), alert.paperRunId(), alert.alertType(),
                alert.severity().name(), alert.status().name(), alert.title(),
                alert.message(), alert.source(), alert.eventSnapshotJson(),
                alert.acknowledgedBy(), toTimestamp(alert.acknowledgedAt()),
                toTimestamp(alert.resolvedAt()), Timestamp.from(alert.createdAt()),
                Timestamp.from(alert.updatedAt()));
    }

    @Override
    public Optional<PaperRunAlert> findById(String alertId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    BASE_SELECT + " WHERE alert_id = ?", ROW_MAPPER, alertId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<PaperRunAlert> listByRunId(String paperRunId, String status, String severity) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE paper_run_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(paperRunId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (severity != null && !severity.isBlank()) {
            sql.append(" AND severity = ?");
            params.add(severity);
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    @Override
    public boolean updateStatus(String alertId, PaperRunAlertStatus status, String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt, Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE paper_run_alerts SET status = ?, acknowledged_by = COALESCE(?, acknowledged_by),
                    acknowledged_at = COALESCE(?, acknowledged_at), resolved_at = COALESCE(?, resolved_at),
                    updated_at = ?
                WHERE alert_id = ?
                """, status.name(), acknowledgedBy, toTimestamp(acknowledgedAt),
                toTimestamp(resolvedAt), Timestamp.from(updatedAt), alertId);
        return updated > 0;
    }

    @Override
    public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM paper_run_alerts WHERE paper_run_id = ? AND created_at >= ? AND created_at < ?
                """, Integer.class, paperRunId, Timestamp.from(start), Timestamp.from(end));
        return count != null ? count : 0;
    }

    private static PaperRunAlert mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp acknowledgedAt = rs.getTimestamp("acknowledged_at");
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        return new PaperRunAlert(
                rs.getString("alert_id"), rs.getString("paper_run_id"),
                rs.getString("alert_type"),
                PaperRunAlertSeverity.valueOf(rs.getString("severity")),
                PaperRunAlertStatus.valueOf(rs.getString("status")),
                rs.getString("title"), rs.getString("message"), rs.getString("source"),
                rs.getString("event_snapshot_json"), rs.getString("acknowledged_by"),
                acknowledgedAt == null ? null : acknowledgedAt.toInstant(),
                resolvedAt == null ? null : resolvedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
