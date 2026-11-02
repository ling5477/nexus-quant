package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryEvent;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryType;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunRecoveryEventRepository;

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
public class JdbcPaperRunRecoveryEventRepository implements PaperRunRecoveryEventRepository {

    private static final RowMapper<PaperRunRecoveryEvent> ROW_MAPPER = JdbcPaperRunRecoveryEventRepository::mapRow;
    private static final String BASE_SELECT = """
            SELECT recovery_event_id, paper_run_id, recovery_type, status, reason,
                   request_json::text AS request_json, result_json::text AS result_json,
                   started_at, finished_at, created_at
            FROM paper_run_recovery_events
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperRunRecoveryEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(PaperRunRecoveryEvent event) {
        jdbcTemplate.update("""
                INSERT INTO paper_run_recovery_events (
                    recovery_event_id, paper_run_id, recovery_type, status, reason,
                    request_json, result_json, started_at, finished_at, created_at
                ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?)
                """,
                event.recoveryEventId(), event.paperRunId(),
                event.recoveryType().name(), event.status().name(),
                event.reason(), event.requestJson(), event.resultJson(),
                Timestamp.from(event.startedAt()), toTimestamp(event.finishedAt()),
                Timestamp.from(event.createdAt()));
    }

    @Override
    public Optional<PaperRunRecoveryEvent> findById(String recoveryEventId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    BASE_SELECT + " WHERE recovery_event_id = ?", ROW_MAPPER, recoveryEventId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<PaperRunRecoveryEvent> listByRunId(String paperRunId, String recoveryType, String status) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE paper_run_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(paperRunId);
        if (recoveryType != null && !recoveryType.isBlank()) {
            sql.append(" AND recovery_type = ?");
            params.add(recoveryType);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    @Override
    public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM paper_run_recovery_events
                WHERE paper_run_id = ? AND created_at >= ? AND created_at < ?
                """, Integer.class, paperRunId, Timestamp.from(start), Timestamp.from(end));
        return count != null ? count : 0;
    }

    private static PaperRunRecoveryEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp finishedAt = rs.getTimestamp("finished_at");
        return new PaperRunRecoveryEvent(
                rs.getString("recovery_event_id"),
                rs.getString("paper_run_id"),
                PaperRunRecoveryType.valueOf(rs.getString("recovery_type")),
                PaperRunRecoveryStatus.valueOf(rs.getString("status")),
                rs.getString("reason"),
                rs.getString("request_json"),
                rs.getString("result_json"),
                rs.getTimestamp("started_at").toInstant(),
                finishedAt == null ? null : finishedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
