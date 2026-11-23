package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.research.domain.paper.PaperAlertOperation;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperAlertOperationRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 回执和审计任何一处失败都回滚原告警变更；不可变历史不能被当前告警状态覆盖。 */
@Repository
public class JdbcPaperAlertOperationRepository implements PaperAlertOperationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditLogRepository audit;

    public JdbcPaperAlertOperationRepository(
            JdbcTemplate jdbc, ObjectMapper mapper, AuditLogRepository audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public void lock(UUID id) {
        requireTransaction();
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT pg_try_advisory_xact_lock(hashtextextended(?,9255))",
                        Boolean.class,
                        "paper-alert-operation:" + id)))
            throw new IllegalStateException("PAPER_ALERT_OPERATION_BUSY");
    }

    @Override
    public Optional<PaperAlertOperation> find(UUID id) {
        return jdbc
                .query(
                        "SELECT * FROM paper_alert_operations WHERE operation_id=?",
                        (rs, row) ->
                                new PaperAlertOperation(
                                        rs.getObject("operation_id", UUID.class),
                                        rs.getLong("owner_user_id"),
                                        rs.getString("actor"),
                                        rs.getString("paper_run_id"),
                                        rs.getString("operation_type"),
                                        rs.getString("input_sha256"),
                                        decode(rs.getString("result_json")),
                                        rs.getTimestamp("completed_at").toInstant()),
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public void complete(PaperAlertOperation operation, String traceId) {
        requireTransaction();
        jdbc.update(
                """
                INSERT INTO paper_alert_operations(operation_id,owner_user_id,actor,paper_run_id,
                    operation_type,alert_id,input_sha256,trace_id,result_json,completed_at)
                VALUES (?,?,?,?,?,?,?,?,?::jsonb,?)
                """,
                operation.operationId(),
                operation.ownerUserId(),
                operation.actor(),
                operation.paperRunId(),
                operation.type(),
                operation.result().alertId(),
                operation.inputSha256(),
                traceId,
                encode(operation.result()),
                Timestamp.from(operation.completedAt()));
        audit.append(
                "PAPER_TRADING",
                "PAPER_ALERT_OPERATION_COMPLETED",
                operation.actor(),
                traceId,
                Map.of(
                        "operationId",
                        operation.operationId().toString(),
                        "paperRunId",
                        operation.paperRunId(),
                        "alertId",
                        operation.result().alertId(),
                        "operationType",
                        operation.type()));
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("PAPER_ALERT_OPERATION_TRANSACTION_REQUIRED");
    }

    private String encode(PaperRunAlert value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("PAPER_ALERT_OPERATION_ENCODING_FAILED", failure);
        }
    }

    private PaperRunAlert decode(String value) {
        try {
            return mapper.readValue(value, PaperRunAlert.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("PAPER_ALERT_OPERATION_DECODING_FAILED", failure);
        }
    }
}
