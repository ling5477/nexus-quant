package com.guidinglight.nexusquant.scheduler.control;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 只读固定任务行，并以条件更新控制配置和单次执行状态。 */
@Repository
public class ScheduledJobControlRepository {
    private static final RowMapper<ScheduledJobControl> MAPPER =
            (rs, rowNum) ->
                    new ScheduledJobControl(
                            rs.getString("job_key"),
                            rs.getBoolean("enabled"),
                            rs.getLong("fixed_delay_ms"),
                            instant(rs.getTimestamp("next_run_at")),
                            instant(rs.getTimestamp("last_started_at")),
                            instant(rs.getTimestamp("last_finished_at")),
                            rs.getString("last_status"),
                            rs.getString("last_error_code"),
                            rs.getInt("consecutive_failures"),
                            rs.getLong("version"),
                            rs.getObject("active_run_id", UUID.class),
                            rs.getString("updated_by"),
                            instant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;
    private final ScheduledJobOperationRepository operations;

    public ScheduledJobControlRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.operations = null;
    }

    @Autowired
    public ScheduledJobControlRepository(
            JdbcTemplate jdbc, ScheduledJobOperationRepository operations) {
        this.jdbc = jdbc;
        this.operations = operations;
    }

    public List<ScheduledJobControl> all() {
        return jdbc.query("SELECT * FROM scheduled_job_controls ORDER BY job_key", MAPPER);
    }

    public Optional<ScheduledJobControl> find(String key) {
        return jdbc
                .query("SELECT * FROM scheduled_job_controls WHERE job_key=?", MAPPER, key)
                .stream()
                .findFirst();
    }

    public List<String> due(Instant now, int limit) {
        return jdbc.queryForList(
                """
                SELECT job_key FROM scheduled_job_controls
                WHERE enabled AND next_run_at <= ? ORDER BY next_run_at, job_key LIMIT ?
                """,
                String.class,
                Timestamp.from(now),
                limit);
    }

    public void materialize(String key, long delayMs) {
        materialize(key, delayMs, false);
    }

    public void materialize(String key, long delayMs, boolean enabled) {
        jdbc.update(
                """
                INSERT INTO scheduled_job_controls(job_key, fixed_delay_ms, enabled, next_run_at)
                VALUES (?, ?, ?, CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                ON CONFLICT (job_key) DO NOTHING
                """,
                key,
                delayMs,
                enabled,
                enabled);
    }

    public boolean patch(
            String key,
            long expectedVersion,
            boolean enabled,
            long delayMs,
            String actor,
            Instant now) {
        return jdbc.update(
                        """
                UPDATE scheduled_job_controls
                SET enabled=?, fixed_delay_ms=?, next_run_at=CASE WHEN ? THEN ?::timestamptz ELSE NULL END,
                    version=version+1, updated_by=?, updated_at=?
                WHERE job_key=? AND version=?
                """,
                        enabled,
                        delayMs,
                        enabled,
                        enabled ? Timestamp.from(now) : null,
                        actor,
                        Timestamp.from(now),
                        key,
                        expectedVersion)
                == 1;
    }

    public boolean claim(String key, UUID runId, Instant now, boolean manual) {
        return claim(key, runId, now, manual, null);
    }

    /** 控制行和原操作同时领取；未收敛的显式请求也阻止后台任务用新UUID再次执行。 */
    @Transactional(timeout = 20)
    public boolean claim(String key, UUID runId, Instant now, boolean manual, UUID operationId) {
        if (operationId != null && operations == null)
            throw new IllegalStateException("SCHEDULER_OPERATION_REPOSITORY_UNAVAILABLE");
        String predicate =
                manual
                        ? "(active_run_id IS NULL OR last_started_at <= ?::timestamptz - interval '5 minutes')"
                        : "enabled AND next_run_at <= ? AND (active_run_id IS NULL OR last_started_at <= ?::timestamptz - interval '5 minutes')";
        String sql =
                """
                UPDATE scheduled_job_controls
                SET active_run_id=?, last_started_at=?, last_status='RUNNING', last_error_code=NULL,
                    next_run_at=CASE WHEN enabled THEN ?::timestamptz + interval '5 minutes' ELSE NULL END
                WHERE job_key=? AND ("""
                        + predicate
                        + ")";
        Timestamp timestamp = Timestamp.from(now);
        List<Object> parameters =
                new ArrayList<>(List.of(runId, timestamp, timestamp, key, timestamp));
        if (!manual) parameters.add(timestamp);
        if (operations != null) {
            sql +=
                    """
                     AND NOT EXISTS (SELECT 1 FROM scheduled_job_operations p
                         WHERE p.job_key=scheduled_job_controls.job_key AND p.operation_type='RUN_ONCE'
                         AND p.status IN ('STARTING','RUNNING','OUTCOME_UNKNOWN')
                         AND (?::uuid IS NULL OR p.operation_id<>?::uuid))
                    """;
            parameters.add(operationId);
            parameters.add(operationId);
        }
        boolean claimed = jdbc.update(sql, parameters.toArray()) == 1;
        if (claimed && operationId != null && !operations.start(operationId, key, now)) {
            throw new IllegalStateException("SCHEDULER_OPERATION_STATE_CONFLICT");
        }
        return claimed;
    }

    /** 静态能力不可用时推进未占用游标；未收敛请求必须保留原领取身份，允许迟到的原执行完成。 */
    public void skipUnavailable(String key, Instant now) {
        String sql =
                """
                UPDATE scheduled_job_controls
                SET active_run_id=NULL, last_status='SKIPPED', last_error_code='CAPABILITY_UNAVAILABLE',
                    last_finished_at=?, next_run_at=?::timestamptz
                        + fixed_delay_ms * interval '1 millisecond'
                WHERE job_key=? AND enabled AND next_run_at <= ?
                    AND (active_run_id IS NULL OR last_started_at <= ?::timestamptz - interval '5 minutes')
                """;
        if (operations != null) {
            // 五分钟超时不证明原执行没有副作用；能力变化也不能解除未知结果的重复执行阻断。
            sql +=
                    """
                     AND NOT EXISTS (SELECT 1 FROM scheduled_job_operations p
                         WHERE p.job_key=scheduled_job_controls.job_key AND p.operation_type='RUN_ONCE'
                         AND p.status IN ('STARTING','RUNNING','OUTCOME_UNKNOWN'))
                    """;
        }
        jdbc.update(
                sql,
                Timestamp.from(now),
                Timestamp.from(now),
                key,
                Timestamp.from(now),
                Timestamp.from(now));
    }

    public void complete(
            String key, UUID runId, Instant finishedAt, String status, String errorCode) {
        complete(key, runId, finishedAt, status, errorCode, null);
    }

    /** 任务结束与对应回执/审计原子完成；任一写入失败均回滚结束状态，结果不能伪装为成功。 */
    @Transactional(timeout = 20)
    public void complete(
            String key,
            UUID runId,
            Instant finishedAt,
            String status,
            String errorCode,
            UUID operationId) {
        int updated =
                jdbc.update(
                        """
                UPDATE scheduled_job_controls
                SET active_run_id=NULL, last_finished_at=?, last_status=?, last_error_code=?,
                    consecutive_failures=CASE WHEN ?='SUCCESS' THEN 0
                        WHEN ?='FAILED' THEN consecutive_failures+1 ELSE consecutive_failures END,
                    next_run_at=CASE WHEN enabled THEN ?::timestamptz
                        + fixed_delay_ms * interval '1 millisecond' ELSE NULL END
                WHERE job_key=? AND active_run_id=?
                """,
                        Timestamp.from(finishedAt),
                        status,
                        errorCode,
                        status,
                        status,
                        Timestamp.from(finishedAt),
                        key,
                        runId);
        if (updated != 1) throw new IllegalStateException("SCHEDULER_EXECUTION_STATE_CONFLICT");
        if (operationId != null) {
            if (operations == null)
                throw new IllegalStateException("SCHEDULER_OPERATION_REPOSITORY_UNAVAILABLE");
            operations.completeRun(operationId, key, status, errorCode, finishedAt);
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
