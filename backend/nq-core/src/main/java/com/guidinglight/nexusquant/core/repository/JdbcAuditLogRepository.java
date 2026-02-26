package com.guidinglight.nexusquant.core.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;

import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcAuditLogRepository 提供审计日志落库实现。
 * <p>
 * Why:
 * Gate B 要求关键决策点可复盘，审计日志必须结构化落库并携带 trace_id。
 */
@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @param jdbcTemplate JDBC 执行器
     * @param objectMapper JSON 序列化器
     */
    public JdbcAuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void append(String domain, String action, String actorId, String traceId, Map<String, Object> detail) {
        String detailJson = serializeDetail(detail);
        jdbcTemplate.update(
                """
                        INSERT INTO audit_logs (domain, action, actor_id, trace_id, detail_json)
                        VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                        """,
                domain,
                action,
                actorId,
                traceId,
                detailJson
        );
    }

    private String serializeDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize audit detail", ex);
        }
    }
}
