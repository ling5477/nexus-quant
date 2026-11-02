package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.strategy.application.exception.StrategyScheduleCreationConflictException;
import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreation;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreationSpec;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleCreationRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 编号锁不等待未知写入；完成回执或审计失败时，由同一事务回滚刚创建的正式计划。 */
@Repository
public class JdbcStrategyScheduleCreationRepository implements StrategyScheduleCreationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditLogRepository audit;

    public JdbcStrategyScheduleCreationRepository(
            JdbcTemplate jdbc, ObjectMapper mapper, AuditLogRepository audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    public void lock(UUID id) {
        requireWriteTransaction();
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT pg_try_advisory_xact_lock(hashtextextended(?,74116))",
                        Boolean.class,
                        "strategy-schedule-creation:" + id)))
            throw new StrategyScheduleCreationConflictException("STRATEGY_SCHEDULE_CREATION_BUSY");
    }

    public Optional<Identity> identity(UUID id) {
        requireWriteTransaction();
        return jdbc
                .query(
                        "SELECT owner_user_id,input_sha256 FROM strategy_schedule_creations WHERE creation_id=?",
                        (rs, row) ->
                                new Identity(
                                        rs.getLong("owner_user_id"), rs.getString("input_sha256")),
                        id)
                .stream()
                .findFirst();
    }

    public Optional<StrategyScheduleCreation> findOwned(UUID id, long owner) {
        return jdbc
                .query(
                        "SELECT * FROM strategy_schedule_creations WHERE creation_id=? AND owner_user_id=?",
                        (rs, row) ->
                                new StrategyScheduleCreation(
                                        rs.getObject("creation_id", UUID.class),
                                        rs.getLong("owner_user_id"),
                                        rs.getString("actor"),
                                        rs.getInt("request_version"),
                                        rs.getString("input_sha256"),
                                        decode(
                                                rs.getString("request_json"),
                                                StrategyScheduleCreationSpec.class),
                                        decode(rs.getString("result_json"), StrategySchedule.class),
                                        rs.getString("trace_id"),
                                        rs.getTimestamp("completed_at").toInstant()),
                        id,
                        owner)
                .stream()
                .findFirst();
    }

    public void complete(StrategyScheduleCreation creation) {
        requireWriteTransaction();
        jdbc.update(
                """
            INSERT INTO strategy_schedule_creations(creation_id,owner_user_id,actor,request_version,input_sha256,
                request_json,schedule_job_id,result_json,trace_id,completed_at)
            VALUES(?,?,?,?,?,?::jsonb,?,?::jsonb,?,?)
            """,
                creation.creationId(),
                creation.ownerUserId(),
                creation.actor(),
                creation.requestVersion(),
                creation.inputSha256(),
                encode(creation.request()),
                creation.result().scheduleJobId(),
                encode(creation.result()),
                creation.traceId(),
                Timestamp.from(creation.completedAt()));
        // 审计只记录业务身份，原窗口留在受owner约束的回执中，不写入审计或日志明文。
        audit.append(
                "STRATEGY",
                "STRATEGY_SCHEDULE_CREATION_COMPLETED",
                Long.toString(creation.ownerUserId()),
                creation.traceId(),
                Map.of(
                        "creationId",
                        creation.creationId().toString(),
                        "scheduleId",
                        creation.result().scheduleJobId(),
                        "strategyId",
                        creation.result().strategyId()));
    }

    private static void requireWriteTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("schedule creation write transaction required");
    }

    private String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("schedule creation encoding failed", failure);
        }
    }

    private <T> T decode(String value, Class<T> type) {
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("schedule creation decoding failed", failure);
        }
    }
}
