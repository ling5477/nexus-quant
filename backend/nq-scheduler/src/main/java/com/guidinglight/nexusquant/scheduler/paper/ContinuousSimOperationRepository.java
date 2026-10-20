package com.guidinglight.nexusquant.scheduler.paper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 回执与原canonical业务修改共用事务；并发同编号只能在原事务提交后读取原结果。 */
@Repository
public class ContinuousSimOperationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditLogRepository audit;

    public ContinuousSimOperationRepository(
            JdbcTemplate jdbc, ObjectMapper mapper, AuditLogRepository audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    public void lock(UUID operationId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("CONTINUOUS_SIM_OPERATION_TRANSACTION_REQUIRED");
        Boolean acquired =
                jdbc.queryForObject(
                        "SELECT pg_try_advisory_xact_lock(hashtextextended(?,9254))",
                        Boolean.class,
                        "continuous-sim-operation:" + operationId);
        if (!Boolean.TRUE.equals(acquired))
            throw new IllegalStateException("CONTINUOUS_SIM_OPERATION_BUSY");
    }

    public Optional<ContinuousSimOperation> find(UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM continuous_sim_operations WHERE operation_id=?",
                        (rs, row) ->
                                new ContinuousSimOperation(
                                        rs.getObject("operation_id", UUID.class),
                                        rs.getLong("owner_user_id"),
                                        rs.getString("actor"),
                                        ContinuousSimOperationRequest.Type.valueOf(
                                                rs.getString("operation_type")),
                                        rs.getString("requested_publish_id"),
                                        rs.getBigDecimal("requested_budget"),
                                        rs.getString("paper_run_id"),
                                        rs.getString("input_sha256"),
                                        rs.getString("trace_id"),
                                        decode(rs.getString("result_json")),
                                        rs.getTimestamp("completed_at").toInstant()),
                        id)
                .stream()
                .findFirst();
    }

    /** 插入失败必须让调用者事务回滚，不能留下一次业务修改而缺少可查询的原回执。 */
    public void complete(
            ContinuousSimOperationRequest request,
            long owner,
            String actor,
            String inputSha,
            String traceId,
            ContinuousSimRunService.Status result,
            Instant now) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("CONTINUOUS_SIM_OPERATION_TRANSACTION_REQUIRED");
        jdbc.update(
                """
                INSERT INTO continuous_sim_operations(operation_id,owner_user_id,actor,operation_type,
                    requested_publish_id,requested_budget,paper_run_id,input_sha256,trace_id,result_json,completed_at)
                VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?)
                """,
                request.operationId(),
                owner,
                actor,
                request.type().name(),
                request.publishId(),
                request.budget(),
                result.paperRunId(),
                inputSha,
                traceId,
                encode(result),
                Timestamp.from(now));
        audit.append(
                "PAPER_TRADING",
                "CONTINUOUS_SIM_OPERATION_COMPLETED",
                actor,
                traceId,
                Map.of(
                        "operationId",
                        request.operationId().toString(),
                        "operationType",
                        request.type().name(),
                        "paperRunId",
                        result.paperRunId(),
                        "result",
                        "COMPLETED"));
    }

    private String encode(ContinuousSimRunService.Status result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("CONTINUOUS_SIM_OPERATION_ENCODING_FAILED", failure);
        }
    }

    private ContinuousSimRunService.Status decode(String json) {
        try {
            return mapper.readValue(json, ContinuousSimRunService.Status.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("CONTINUOUS_SIM_OPERATION_DECODING_FAILED", failure);
        }
    }
}
