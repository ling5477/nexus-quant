package com.guidinglight.nexusquant.adapter.okx.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * transport 已脱敏解析结果；不含 raw response、header、UID 或余额数值。
 */
public record OkxPrivateReadResult(
        OkxPrivateReadOperation operation,
        Set<String> normalizedPermissions,
        int assetCount,
        boolean complete,
        List<OkxPrivateOrderSnapshot> orders,
        List<OkxPrivateFillSnapshot> fills,
        boolean ipAllowlistConfigured,
        OkxIpAllowlistStatus ipAllowlistStatus,
        Instant observedAt
) {
    public OkxPrivateReadResult {
        Objects.requireNonNull(operation, "operation must not be null");
        normalizedPermissions = Set.copyOf(normalizedPermissions == null ? Set.of() : normalizedPermissions);
        orders = List.copyOf(orders == null ? List.of() : orders);
        fills = List.copyOf(fills == null ? List.of() : fills);
        Objects.requireNonNull(ipAllowlistStatus, "ipAllowlistStatus must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (assetCount < 0) throw new IllegalArgumentException("assetCount must not be negative");
    }

    /**
     * 兼容旧 account-config 构造；未要求预期 IP 比对时状态保持 NOT_CHECKED。
     */
    public OkxPrivateReadResult(
            OkxPrivateReadOperation operation,
            Set<String> normalizedPermissions,
            int assetCount,
            boolean complete,
            List<OkxPrivateOrderSnapshot> orders,
            List<OkxPrivateFillSnapshot> fills,
            boolean ipAllowlistConfigured,
            Instant observedAt
    ) {
        this(operation, normalizedPermissions, assetCount, complete, orders, fills,
                ipAllowlistConfigured, OkxIpAllowlistStatus.NOT_CHECKED, observedAt);
    }

    /**
     * 兼容非 account-config 调用；这些结果不携带 IP allowlist 配置事实。
     */
    public OkxPrivateReadResult(
            OkxPrivateReadOperation operation,
            Set<String> normalizedPermissions,
            int assetCount,
            boolean complete,
            List<OkxPrivateOrderSnapshot> orders,
            List<OkxPrivateFillSnapshot> fills,
            Instant observedAt
    ) {
        this(operation, normalizedPermissions, assetCount, complete, orders, fills,
                false, OkxIpAllowlistStatus.NOT_CHECKED, observedAt);
    }

    /**
     * GateW-2 compatibility constructor.
     */
    public OkxPrivateReadResult(
            OkxPrivateReadOperation operation,
            Set<String> normalizedPermissions,
            int assetCount,
            boolean complete
    ) {
        this(operation, normalizedPermissions, assetCount, complete, List.of(), List.of(),
                false, OkxIpAllowlistStatus.NOT_CHECKED, Instant.EPOCH);
    }
}
