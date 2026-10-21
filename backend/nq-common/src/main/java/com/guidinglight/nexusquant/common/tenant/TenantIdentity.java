package com.guidinglight.nexusquant.common.tenant;

import java.util.Objects;

/** 仅承载认证解析器已验证的归属；不是客户端可提交的租户切换命令。 */
public record TenantIdentity(TenantId tenantId, String tenantCode, TenantKind kind, long userId) {
    public TenantIdentity {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(kind, "kind");
        if (userId <= 0 || tenantCode == null || !tenantCode.matches("[A-Z][A-Z0-9_]{0,63}"))
            throw new IllegalArgumentException("invalid tenant identity");
        if ((kind == TenantKind.LOCAL) != (TenantId.LOCAL.equals(tenantId) && "NQ_LOCAL".equals(tenantCode))
                || (kind == TenantKind.DEMO && (TenantId.LOCAL.equals(tenantId) || "NQ_LOCAL".equals(tenantCode))))
            throw new IllegalArgumentException("tenant kind mismatch");
    }
}
