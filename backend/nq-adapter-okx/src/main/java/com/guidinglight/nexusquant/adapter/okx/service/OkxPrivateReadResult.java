package com.guidinglight.nexusquant.adapter.okx.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** transport 已脱敏解析结果；不含 raw response、header、UID 或余额数值。 */
public record OkxPrivateReadResult(
        OkxPrivateReadOperation operation,
        Set<String> normalizedPermissions,
        int assetCount,
        boolean complete,
        List<OkxPrivateOrderSnapshot> orders,
        List<OkxPrivateFillSnapshot> fills,
        Instant observedAt
) {
    public OkxPrivateReadResult {
        Objects.requireNonNull(operation, "operation must not be null");
        normalizedPermissions = Set.copyOf(normalizedPermissions == null ? Set.of() : normalizedPermissions);
        orders = List.copyOf(orders == null ? List.of() : orders);
        fills = List.copyOf(fills == null ? List.of() : fills);
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (assetCount < 0) throw new IllegalArgumentException("assetCount must not be negative");
    }

    /** GateW-2 compatibility constructor. */
    public OkxPrivateReadResult(
            OkxPrivateReadOperation operation,
            Set<String> normalizedPermissions,
            int assetCount,
            boolean complete
    ) {
        this(operation, normalizedPermissions, assetCount, complete, List.of(), List.of(), Instant.EPOCH);
    }
}
