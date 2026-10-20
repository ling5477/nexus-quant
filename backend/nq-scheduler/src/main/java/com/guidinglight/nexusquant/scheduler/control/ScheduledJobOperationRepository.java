package com.guidinglight.nexusquant.scheduler.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 回执与控制写入共用数据库事务；原身份和终态不可改写，查询只返回调用者自己的操作。
 */
@Repository
public class ScheduledJobOperationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditLogRepository audit;
    private final RowMapper<ScheduledJobOperation> rows;

    public ScheduledJobOperationRepository(
            JdbcTemplate jdbc, ObjectMapper mapper, AuditLogRepository audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
        this.rows =
                (rs, ignored) ->
                        new ScheduledJobOperation(
                                rs.getObject("operation_id", UUID.class),
                                rs.getLong("owner_user_id"),
                                rs.getString("actor"),
                                ScheduledJobOperation.Type.valueOf(rs.getString("operation_type")),
                                rs.getString("job_key"),
                                rs.getString("input_sha256"),
                                rs.getString("trace_id"),
                                ScheduledJobOperation.Status.valueOf(rs.getString("status")),
                                control(rs.getString("control_json")),
                                outcome(rs.getString("execution_result")),
                                rs.getString("reason_code"),
                                instant(rs.getTimestamp("created_at")),
                                instant(rs.getTimestamp("started_at")),
                                instant(rs.getTimestamp("completed_at")));
    }

    public Optional<ScheduledJobOperation> find(UUID id) {
        return jdbc
                .query("SELECT * FROM scheduled_job_operations WHERE operation_id=?", rows, id)
                .stream()
                .findFirst();
    }

    public boolean create(
            ScheduledJobOperationRequest request,
            long owner,
            String actor,
            String hash,
            String traceId,
            Instant now) {
        int inserted =
                jdbc.update(
                        """
                                INSERT INTO scheduled_job_operations(operation_id, owner_user_id, actor,
                                    operation_type, job_key, input_sha256, trace_id, status, created_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, 'STARTING', ?)
                                ON CONFLICT (operation_id) DO NOTHING
                                """,
                        request.operationId(),
                        owner,
                        actor,
                        request.type().name(),
                        request.jobKey(),
                        hash,
                        traceId,
                        Timestamp.from(now));
        if (inserted == 1) {
            audit.append(
                    "SCHEDULER",
                    "OPERATION_ACCEPTED",
                    actor,
                    traceId,
                    Map.of(
                            "operationId",
                            request.operationId().toString(),
                            "jobKey",
                            request.jobKey(),
                            "type",
                            request.type().name(),
                            "result",
                            "ACCEPTED"));
            if (request.type() == ScheduledJobOperation.Type.RUN_ONCE) {
                audit.append(
                        "SCHEDULER",
                        "RUN_ONCE_REQUESTED",
                        actor,
                        traceId,
                        Map.of(
                                "operationId",
                                request.operationId().toString(),
                                "jobKey",
                                request.jobKey(),
                                "result",
                                "REQUESTED"));
            }
        }
        return inserted == 1;
    }

    /**
     * 此方法必须与对应任务的claim在同一事务调用；失败时不能留下已领取任务而没有运行身份。
     */
    public boolean start(UUID id, String key, Instant now) {
        return jdbc.update(
                """
                        UPDATE scheduled_job_operations SET status='RUNNING', started_at=?
                        WHERE operation_id=? AND job_key=? AND operation_type='RUN_ONCE' AND status='STARTING'
                        """,
                Timestamp.from(now),
                id,
                key)
                == 1;
    }

    public void completeControl(UUID id, ScheduledJobControl value, Instant now) {
        int changed =
                jdbc.update(
                        """
                                UPDATE scheduled_job_operations SET status='COMPLETED', control_json=?::jsonb,
                                    completed_at=? WHERE operation_id=? AND operation_type='UPDATE_CONTROL' AND status='STARTING'
                                """,
                        json(value),
                        Timestamp.from(now),
                        id);
        if (changed != 1) throw new IllegalStateException("SCHEDULER_OPERATION_STATE_CONFLICT");
    }

    /**
     * 运行结果、最近任务状态与完成审计在同一事务落库，重启后可查询原结果而不再运行。
     */
    public void completeRun(UUID id, String key, String result, String reasonCode, Instant now) {
        int changed =
                jdbc.update(
                        """
                                UPDATE scheduled_job_operations SET status='COMPLETED', execution_result=?, reason_code=?,
                                    completed_at=? WHERE operation_id=? AND job_key=? AND operation_type='RUN_ONCE'
                                    AND status IN ('STARTING','RUNNING','OUTCOME_UNKNOWN')
                                """,
                        result,
                        reasonCode,
                        Timestamp.from(now),
                        id,
                        key);
        if (changed != 1) throw new IllegalStateException("SCHEDULER_OPERATION_STATE_CONFLICT");
        ScheduledJobOperation value = find(id).orElseThrow();
        audit.append(
                "SCHEDULER",
                "RUN_ONCE",
                value.actor(),
                value.traceId(),
                Map.of("operationId", id.toString(), "jobKey", key, "result", result));
    }

    public void reject(UUID id, String reason, Instant now) {
        int changed =
                jdbc.update(
                        """
                                UPDATE scheduled_job_operations SET status='REJECTED', reason_code=?, completed_at=?
                                WHERE operation_id=? AND status='STARTING'
                                """,
                        reason,
                        Timestamp.from(now),
                        id);
        if (changed == 1) {
            ScheduledJobOperation value = find(id).orElseThrow();
            audit.append(
                    "SCHEDULER",
                    "RUN_ONCE",
                    value.actor(),
                    value.traceId(),
                    Map.of(
                            "operationId",
                            id.toString(),
                            "jobKey",
                            value.jobKey(),
                            "result",
                            "REJECTED",
                            "reasonCode",
                            reason));
        }
    }

    public void markUnknown(UUID id) {
        // 没有完整结束事实时保留未知；迟到的同一执行可完成，其他请求不能重放副作用。
        jdbc.update(
                """
                        UPDATE scheduled_job_operations SET status='OUTCOME_UNKNOWN', reason_code='SCHEDULER_EXECUTION_OUTCOME_UNKNOWN'
                        WHERE operation_id=? AND operation_type='RUN_ONCE' AND status IN ('STARTING','RUNNING')
                        """,
                id);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("SCHEDULER_OPERATION_ENCODING_FAILED", failure);
        }
    }

    private ScheduledJobControl control(String value) {
        if (value == null) return null;
        try {
            return mapper.readValue(value, ScheduledJobControl.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("SCHEDULER_OPERATION_DECODING_FAILED", failure);
        }
    }

    private static SchedulerDispatcher.ExecutionOutcome outcome(String value) {
        return value == null ? null : SchedulerDispatcher.ExecutionOutcome.valueOf(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
