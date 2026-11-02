package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunSchedule;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunScheduleRepository;

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
public class JdbcPaperRunScheduleRepository implements PaperRunScheduleRepository {

    private static final RowMapper<PaperRunSchedule> ROW_MAPPER = JdbcPaperRunScheduleRepository::mapRow;
    private static final String BASE_SELECT = """
            SELECT schedule_id, paper_run_id, schedule_name, cron_expr, status, timezone,
                   next_fire_time, last_fire_time, created_by, created_at, updated_at,
                   request_json::text AS request_json
            FROM paper_run_schedules
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperRunScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(PaperRunSchedule schedule) {
        jdbcTemplate.update("""
                INSERT INTO paper_run_schedules (
                    schedule_id, paper_run_id, schedule_name, cron_expr, status, timezone,
                    next_fire_time, last_fire_time, created_by, created_at, updated_at, request_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
                """,
                schedule.scheduleId(), schedule.paperRunId(), schedule.scheduleName(),
                schedule.cronExpr(), schedule.status().name(), schedule.timezone(),
                toTimestamp(schedule.nextFireTime()), toTimestamp(schedule.lastFireTime()),
                schedule.createdBy(), Timestamp.from(schedule.createdAt()),
                Timestamp.from(schedule.updatedAt()), schedule.requestJson());
    }

    @Override
    public Optional<PaperRunSchedule> findById(String scheduleId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    BASE_SELECT + " WHERE schedule_id = ?", ROW_MAPPER, scheduleId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<PaperRunSchedule> list(String paperRunId, String status) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (paperRunId != null && !paperRunId.isBlank()) {
            sql.append(" AND paper_run_id = ?");
            params.add(paperRunId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    @Override
    public boolean updateStatus(String scheduleId, PaperRunScheduleStatus status, Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE paper_run_schedules SET status = ?, updated_at = ? WHERE schedule_id = ?
                """, status.name(), Timestamp.from(updatedAt), scheduleId);
        return updated > 0;
    }

    @Override
    public boolean updateLastFireTime(String scheduleId, Instant lastFireTime, Instant updatedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE paper_run_schedules SET last_fire_time = ?, updated_at = ? WHERE schedule_id = ?
                """, Timestamp.from(lastFireTime), Timestamp.from(updatedAt), scheduleId);
        return updated > 0;
    }

    private static PaperRunSchedule mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp nextFireTime = rs.getTimestamp("next_fire_time");
        Timestamp lastFireTime = rs.getTimestamp("last_fire_time");
        return new PaperRunSchedule(
                rs.getString("schedule_id"),
                rs.getString("paper_run_id"),
                rs.getString("schedule_name"),
                rs.getString("cron_expr"),
                PaperRunScheduleStatus.valueOf(rs.getString("status")),
                rs.getString("timezone"),
                nextFireTime == null ? null : nextFireTime.toInstant(),
                lastFireTime == null ? null : lastFireTime.toInstant(),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getString("request_json"));
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
