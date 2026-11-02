package com.guidinglight.nexusquant.audit.domain.port;

import java.util.Map;

/**
 * AuditLogRepository 定义平台审计日志的稳定落库端口。
 */
public interface AuditLogRepository {

    /**
     * 记录一条审计日志。
     *
     * @param domain 审计域，例如 ORDER
     * @param action 审计动作，例如 PLACE_ORDER
     * @param actorId 执行主体，可空
     * @param traceId 链路追踪 ID
     * @param detail 结构化详情，可空
     */
    void append(String domain, String action, String actorId, String traceId, Map<String, Object> detail);
}
