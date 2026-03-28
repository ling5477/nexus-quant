package com.guidinglight.nexusquant.ledger.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.ledger.service.port.LedgerRiskAuditRepository;

import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcLedgerRiskAuditRepository 提供 ledger 相关 risk/audit 落库能力。
 */
@Repository
public class JdbcLedgerRiskAuditRepository implements LedgerRiskAuditRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcLedgerRiskAuditRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void appendRiskEvent(
            String scope,
            String scopeId,
            String decision,
            String reason,
            String severity,
            String traceId
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO risk_events (risk_event_id, rule_id, scope, scope_id, decision, reason, severity, trace_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "rsk-" + UUID.randomUUID(),
                reason,
                scope,
                scopeId,
                decision,
                reason,
                severity,
                traceId
        );
    }

    @Override
    public void appendAudit(String domain, String action, String actorId, String traceId, Map<String, Object> detail) {
        jdbcTemplate.update(
                """
                        INSERT INTO audit_logs (domain, action, actor_id, trace_id, detail_json)
                        VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                        """,
                domain,
                action,
                actorId,
                traceId,
                serializeDetail(detail)
        );
    }

    private String serializeDetail(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize detail json", ex);
        }
    }
}
