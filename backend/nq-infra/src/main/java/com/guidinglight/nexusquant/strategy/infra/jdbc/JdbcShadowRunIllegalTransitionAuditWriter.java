package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateTransitionException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shadow Run 非法状态流转审计写入器。
 *
 * <p>该组件专门处理 `ILLEGAL_STATE_TRANSITION_ATTEMPT`（非法状态流转尝试）事件。
 * 它使用 `PROPAGATION_REQUIRES_NEW`（独立新事务）提交审计事件，避免 repository 外层事务
 * 因重新抛出状态机异常而把审计事实一并回滚。该组件只写本地审计表，不调用交易所、
 * 不读取 credential material，也不修改真实账户、资金、订单或 ledger。
 */
@Component
public class JdbcShadowRunIllegalTransitionAuditWriter {

    private static final String INSERT_EVENT_SQL = """
            INSERT INTO shadow_run_events (
                id, shadow_run_id, event_type, from_status, to_status, reason_code, message,
                metadata, request_id, trace_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public JdbcShadowRunIllegalTransitionAuditWriter(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this(jdbcTemplate, objectMapper, requiresNewTemplate(transactionManager));
    }

    JdbcShadowRunIllegalTransitionAuditWriter(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
    }

    /**
     * 记录非法状态流转尝试；审计写失败时异常会向外传播，调用方不得把失败误报为已审计。
     */
    void writeIllegalTransitionAttempt(
            ShadowRun current,
            ShadowRunStatus toStatus,
            ShadowRunStateTransitionException transitionException,
            String message,
            String requestId,
            String traceId
    ) {
        Objects.requireNonNull(current, "current must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");
        Objects.requireNonNull(transitionException, "transitionException must not be null");

        ShadowRunEvent event = new ShadowRunEvent(
                UUID.randomUUID(),
                current.id(),
                ShadowRunEventType.ILLEGAL_STATE_TRANSITION_ATTEMPT,
                current.status(),
                toStatus,
                transitionException.reasonCode(),
                message,
                objectMapper.createObjectNode().put("rejectedTransition", current.status() + "->" + toStatus),
                requestId,
                traceId,
                Instant.now()
        );

        transactionTemplate.executeWithoutResult(status -> insert(event));
    }

    int propagationBehavior() {
        return transactionTemplate.getPropagationBehavior();
    }

    private void insert(ShadowRunEvent event) {
        jdbcTemplate.update(
                INSERT_EVENT_SQL,
                event.id(),
                event.shadowRunId(),
                event.eventType().name(),
                event.fromStatus() == null ? null : event.fromStatus().name(),
                event.toStatus() == null ? null : event.toStatus().name(),
                event.reasonCode(),
                event.message(),
                writeJson(event.metadata()),
                event.requestId(),
                event.traceId(),
                Timestamp.from(event.createdAt())
        );
    }

    private String writeJson(com.fasterxml.jackson.databind.JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize shadow run audit metadata", ex);
        }
    }

    private static TransactionTemplate requiresNewTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null")
        );
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
