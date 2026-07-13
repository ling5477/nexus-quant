package com.guidinglight.nexusquant.adapter.okx.service;

import java.util.Objects;
import java.util.Set;

/** transport 已脱敏解析结果；不含 raw response、UID 或余额数值。 */
public record OkxPrivateReadResult(
        OkxPrivateReadOperation operation,
        Set<String> normalizedPermissions,
        int assetCount,
        boolean complete
) {
    public OkxPrivateReadResult {
        Objects.requireNonNull(operation, "operation must not be null");
        normalizedPermissions = Set.copyOf(normalizedPermissions == null ? Set.of() : normalizedPermissions);
        if (assetCount < 0) {
            throw new IllegalArgumentException("assetCount must not be negative");
        }
    }
}
