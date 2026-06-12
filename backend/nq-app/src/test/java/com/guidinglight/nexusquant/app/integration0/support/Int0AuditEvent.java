package com.guidinglight.nexusquant.app.integration0.support;

/**
 * Int0AuditEvent 是 test-only 审计事件形状（INT0-T13）。
 *
 * <p>只保存可追踪字段，禁止保存 secret / 签名原材料 / raw payload / prompt / context。
 * 本类不写真实审计日志、不写数据库；仅在测试中断言 audit event shape。
 */
public record Int0AuditEvent(
        String eventType,
        String source,
        String tenantId,
        String requestId,
        String traceId,
        String errorCode,
        boolean accepted) {

    public static final String RECEIVED = "REQUEST_RECEIVED";
    public static final String REJECTED = "REQUEST_REJECTED";
    public static final String SIGNATURE_FAILED = "SIGNATURE_FAILED";
    public static final String REPLAY_REJECTED = "REPLAY_REJECTED";
    public static final String TENANT_MISMATCH = "TENANT_MISMATCH";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String FORBIDDEN_FIELD_REJECTED = "FORBIDDEN_FIELD_REJECTED";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    public static Int0AuditEvent received(String source, String tenantId, String requestId, String traceId) {
        return new Int0AuditEvent(RECEIVED, source, tenantId, requestId, traceId, null, true);
    }

    public static Int0AuditEvent rejected(
            String eventType, String source, String tenantId, String requestId, String traceId, String errorCode) {
        return new Int0AuditEvent(eventType, source, tenantId, requestId, traceId, errorCode, false);
    }
}
