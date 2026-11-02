package com.guidinglight.nexusquant.common.tenant;

import java.util.Objects;

/** 从持久化工作身份与执行控制恢复的单段租约；不携带用户令牌或数据库连接。 */
public record TenantExecutionIdentity(TenantId tenantId, long executionEpoch) {
    public TenantExecutionIdentity {
        Objects.requireNonNull(tenantId, "tenantId");
        if (executionEpoch < 1) throw new IllegalArgumentException("invalid tenant execution epoch");
    }
}
