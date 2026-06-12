package com.guidinglight.nexusquant.app.integration0.support;

import java.util.List;

/**
 * Int0ValidationResult 是 test-only contract validation 结果。
 *
 * <p>{@code statusCode} 使用冻结的拒绝码：200 接受 / 400 schema·字段 / 401 认证 / 403 权限·tenant·source·能力 /
 * 409 幂等·replay / 413 payload 超限。{@code details} 不得包含敏感值。
 */
public record Int0ValidationResult(
        boolean accepted, int statusCode, String errorCategory, List<String> details, Int0AuditEvent auditEvent) {

    public static Int0ValidationResult accept(Int0AuditEvent auditEvent) {
        return new Int0ValidationResult(true, 200, "OK", List.of(), auditEvent);
    }

    public static Int0ValidationResult reject(
            int statusCode, String errorCategory, List<String> details, Int0AuditEvent auditEvent) {
        return new Int0ValidationResult(false, statusCode, errorCategory, List.copyOf(details), auditEvent);
    }
}
