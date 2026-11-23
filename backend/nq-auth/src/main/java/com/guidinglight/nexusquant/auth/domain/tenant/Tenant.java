package com.guidinglight.nexusquant.auth.domain.tenant;

import com.guidinglight.nexusquant.common.tenant.TenantId;
import com.guidinglight.nexusquant.common.tenant.TenantIdentity;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import java.time.Instant;
import java.util.Objects;

/** 租户根身份；禁用仅影响访问资格，不重写已有业务事实。 */
public record Tenant(TenantId id, String code, String name, TenantKind kind, Status status,
                     Instant createdAt, Instant updatedAt) {
    public enum Status { ACTIVE, DISABLED }

    public Tenant {
        new TenantIdentity(id, code, kind, 1);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (name == null || name.isBlank() || name.length() > 128 || updatedAt.isBefore(createdAt))
            throw new IllegalArgumentException("invalid tenant metadata");
    }
}
