package com.guidinglight.nexusquant.risk.infra.jdbc;

import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;
import com.guidinglight.nexusquant.core.service.port.RiskEventRepository;

import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcRiskEventRepository 提供 risk_events 表写入实现。
 */
@Repository
public class JdbcRiskEventRepository implements RiskEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRiskEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public void append(
            String scope,
            String scopeId,
            RiskDecision decision,
            String reason,
            RiskSeverity severity,
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
                decision.name(),
                reason,
                severity.name(),
                traceId
        );
    }
}

