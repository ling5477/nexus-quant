package com.guidinglight.nexusquant.ledger.service.port;

import java.util.Map;

/**
 * LedgerRiskAuditRepository 定义 ledger 失败场景的审计与风险记录能力。
 */
public interface LedgerRiskAuditRepository {

    /**
     * 记录 risk_events。
     *
     * @param scope 风险域
     * @param scopeId 风险对象 ID
     * @param decision 判定结果
     * @param reason 判定原因
     * @param severity 风险级别
     * @param traceId 链路追踪 ID
     */
    void appendRiskEvent(
            String scope,
            String scopeId,
            String decision,
            String reason,
            String severity,
            String traceId
    );

    /**
     * 写审计日志。
     *
     * @param domain 审计域
     * @param action 审计动作
     * @param actorId 操作主体
     * @param traceId 链路追踪 ID
     * @param detail 审计详情
     */
    void appendAudit(String domain, String action, String actorId, String traceId, Map<String, Object> detail);
}
