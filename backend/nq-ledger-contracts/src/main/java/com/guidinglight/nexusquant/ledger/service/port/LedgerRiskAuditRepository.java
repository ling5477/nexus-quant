package com.guidinglight.nexusquant.ledger.service.port;

import java.util.Map;

/**
 * LedgerRiskAuditRepository 定义 ledger 失败场景的审计与风险记录能力。
 */
public interface LedgerRiskAuditRepository {

    void appendRiskEvent(
            String scope,
            String scopeId,
            String decision,
            String reason,
            String severity,
            String traceId
    );

    void appendAudit(String domain, String action, String actorId, String traceId, Map<String, Object> detail);
}
