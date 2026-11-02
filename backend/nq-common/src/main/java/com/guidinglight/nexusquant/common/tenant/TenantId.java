package com.guidinglight.nexusquant.common.tenant;

import java.util.Objects;
import java.util.UUID;

/** 独立租户身份；本地常量与用户编号无关，零 UUID 不能表达有效归属。 */
public record TenantId(UUID value) {
    public static final TenantId LOCAL = new TenantId(UUID.fromString("00000000-0000-4000-8000-000000000001"));

    public TenantId {
        Objects.requireNonNull(value, "tenantId");
        if (value.equals(new UUID(0, 0))) throw new IllegalArgumentException("invalid tenant identity");
    }

    public static TenantId parse(String text) {
        if (text == null) throw new IllegalArgumentException("tenant identity required");
        UUID value = UUID.fromString(text);
        if (!value.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException("canonical UUID required");
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
