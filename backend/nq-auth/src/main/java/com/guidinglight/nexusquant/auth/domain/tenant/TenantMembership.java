package com.guidinglight.nexusquant.auth.domain.tenant;

import com.guidinglight.nexusquant.common.tenant.TenantKind;
import java.time.Instant;
import java.util.Objects;

/** 成员权限与平台角色分离；本地成员标记不替代原用户的多个角色。 */
public record TenantMembership(Tenant tenant, long userId, Role role, Status status,
                               Instant createdAt, Instant updatedAt) {
    public enum Role { LOCAL_MEMBER, DEMO_USER }
    public enum Status { ACTIVE, DISABLED }

    public TenantMembership {
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (userId <= 0 || updatedAt.isBefore(createdAt)
                || (tenant.kind() == TenantKind.LOCAL) != (role == Role.LOCAL_MEMBER))
            throw new IllegalArgumentException("invalid tenant membership");
    }
}
