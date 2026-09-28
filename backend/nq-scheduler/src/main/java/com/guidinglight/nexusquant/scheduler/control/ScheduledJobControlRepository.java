package com.guidinglight.nexusquant.scheduler.control;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** 只读固定任务行，并以条件更新控制配置和单次执行状态。 */
@Repository
public class ScheduledJobControlRepository {
    private static final RowMapper<ScheduledJobControl> MAPPER = (rs, rowNum) -> new ScheduledJobControl(
            rs.getString("job_key"), rs.getBoolean("enabled"), rs.getLong("fixed_delay_ms"),
            instant(rs.getTimestamp("next_run_at")), instant(rs.getTimestamp("last_started_at")),
            instant(rs.getTimestamp("last_finished_at")), rs.getString("last_status"),
            rs.getString("last_error_code"), rs.getInt("consecutive_failures"),
            rs.getLong("version"), rs.getObject("active_run_id", UUID.class),
            rs.getString("updated_by"), instant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public ScheduledJobControlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScheduledJobControl> all() {
        return jdbc.query("SELECT * FROM scheduled_job_controls ORDER BY job_key", MAPPER);
    }

    public Optional<ScheduledJobControl> find(String key) {
        return jdbc.query("SELECT * FROM scheduled_job_controls WHERE job_key=?", MAPPER, key)
                .stream().findFirst();
    }

    public List<String> due(Instant now, int limit) {
        return jdbc.queryForList("""
                SELECT job_key FROM scheduled_job_controls
                WHERE enabled AND next_run_at <= ? ORDER BY next_run_at, job_key LIMIT ?
                """, String.class, Timestamp.from(now), limit);
    }

    public void materialize(String key, long delayMs) {
        jdbc.update("""
                INSERT INTO scheduled_job_controls(job_key, fixed_delay_ms)
                VALUES (?, ?) ON CONFLICT (job_key) DO NOTHING
                """, key, delayMs);
    }

    public boolean patch(String key, long expectedVersion, boolean enabled, long delayMs,
            String actor, Instant now) {
        return jdbc.update("""
                UPDATE scheduled_job_controls
                SET enabled=?, fixed_delay_ms=?, next_run_at=CASE WHEN ? THEN ?::timestamptz ELSE NULL END,
                    version=version+1, updated_by=?, updated_at=?
                WHERE job_key=? AND version=?
                """, enabled, delayMs, enabled, enabled ? Timestamp.from(now) : null,
                actor, Timestamp.from(now), key, expectedVersion) == 1;
    }

    public boolean claim(String key, UUID runId, Instant now, boolean manual) {
        String predicate = manual
                ? "(active_run_id IS NULL OR last_started_at <= ?::timestamptz - interval '5 minutes')"
                : "enabled AND next_run_at <= ? AND (active_run_id IS NULL OR last_started_at <= ?::timestamptz - interval '5 minutes')";
        String sql = """
                UPDATE scheduled_job_controls
                SET active_run_id=?, last_started_at=?, last_status='RUNNING', last_error_code=NULL,
                    next_run_at=CASE WHEN enabled THEN ?::timestamptz + interval '5 minutes' ELSE NULL END
                WHERE job_key=? AND (""" + predicate + ")";
        Timestamp timestamp = Timestamp.from(now);
        return manual
                ? jdbc.update(sql, runId, timestamp, timestamp, key, timestamp) == 1
                : jdbc.update(sql, runId, timestamp, timestamp, key, timestamp, timestamp) == 1;
    }

    /** 静态能力暂不可用时推进游标，避免同一禁用能力每秒占据 due 队列。 */
    public void skipUnavailable(String key, Instant now) {
        jdbc.update("""
                UPDATE scheduled_job_controls
                SET active_run_id=NULL, last_status='SKIPPED', last_error_code='CAPABILITY_UNAVAILABLE',
                    last_finished_at=?, next_run_at=?::timestamptz
                        + fixed_delay_ms * interval '1 millisecond'
                WHERE job_key=? AND enabled AND next_run_at <= ?
                    AND (active_run_id IS NULL OR last_started_at <= ?::timestamptz - interval '5 minutes')
                """, Timestamp.from(now), Timestamp.from(now), key, Timestamp.from(now), Timestamp.from(now));
    }

    public void complete(String key, UUID runId, Instant finishedAt, String status, String errorCode) {
        int updated = jdbc.update("""
                UPDATE scheduled_job_controls
                SET active_run_id=NULL, last_finished_at=?, last_status=?, last_error_code=?,
                    consecutive_failures=CASE WHEN ?='SUCCESS' THEN 0
                        WHEN ?='FAILED' THEN consecutive_failures+1 ELSE consecutive_failures END,
                    next_run_at=CASE WHEN enabled THEN ?::timestamptz
                        + fixed_delay_ms * interval '1 millisecond' ELSE NULL END
                WHERE job_key=? AND active_run_id=?
                """, Timestamp.from(finishedAt), status, errorCode, status, status,
                Timestamp.from(finishedAt), key, runId);
        if (updated != 1) throw new IllegalStateException("SCHEDULER_EXECUTION_STATE_CONFLICT");
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
