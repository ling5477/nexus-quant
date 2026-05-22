package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFire;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFireStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunScheduleFireRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperRunScheduleFireRepository implements PaperRunScheduleFireRepository {

    private static final RowMapper<PaperRunScheduleFire> ROW_MAPPER = JdbcPaperRunScheduleFireRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperRunScheduleFireRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(PaperRunScheduleFire fire) {
        jdbcTemplate.update("""
                INSERT INTO paper_run_schedule_fires (
                    fire_id, schedule_id, paper_run_id, status, fired_at, finished_at,
                    duration_ms, result_json, error_message, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?)
                """,
                fire.fireId(), fire.scheduleId(), fire.paperRunId(), fire.status().name(),
                Timestamp.from(fire.firedAt()), toTimestamp(fire.finishedAt()),
                fire.durationMs(), fire.resultJson(), fire.errorMessage(),
                Timestamp.from(fire.createdAt()));
    }

    @Override
    public List<PaperRunScheduleFire> listByScheduleId(String scheduleId) {
        return jdbcTemplate.query("""
                SELECT fire_id, schedule_id, paper_run_id, status, fired_at, finished_at,
                       duration_ms, result_json::text AS result_json, error_message, created_at
                FROM paper_run_schedule_fires WHERE schedule_id = ? ORDER BY fired_at DESC
                """, ROW_MAPPER, scheduleId);
    }

    @Override
    public List<PaperRunScheduleFire> listByRunIdAndStatus(String paperRunId, String status, Instant start, Instant end) {
        return jdbcTemplate.query("""
                SELECT fire_id, schedule_id, paper_run_id, status, fired_at, finished_at,
                       duration_ms, result_json::text AS result_json, error_message, created_at
                FROM paper_run_schedule_fires
                WHERE paper_run_id = ? AND status = ? AND fired_at >= ? AND fired_at < ?
                ORDER BY fired_at DESC
                """, ROW_MAPPER, paperRunId, status, Timestamp.from(start), Timestamp.from(end));
    }

    @Override
    public int countByRunIdAndStatusAndDateRange(String paperRunId, String status, Instant start, Instant end) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM paper_run_schedule_fires
                WHERE paper_run_id = ? AND status = ? AND fired_at >= ? AND fired_at < ?
                """, Integer.class, paperRunId, status, Timestamp.from(start), Timestamp.from(end));
        return count != null ? count : 0;
    }

    private static PaperRunScheduleFire mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp finishedAt = rs.getTimestamp("finished_at");
        long durationMs = rs.getLong("duration_ms");
        return new PaperRunScheduleFire(
                rs.getString("fire_id"),
                rs.getString("schedule_id"),
                rs.getString("paper_run_id"),
                PaperRunScheduleFireStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("fired_at").toInstant(),
                finishedAt == null ? null : finishedAt.toInstant(),
                rs.wasNull() ? null : durationMs,
                rs.getString("result_json"),
                rs.getString("error_message"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
