package com.guidinglight.nexusquant.adapter.okx.privateread.model;

import com.guidinglight.nexusquant.adapter.okx.auth.OkxIpAllowlistStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * transport 已脱敏解析结果；仅保留所需业务事实，不含原始响应、认证头或 UID。
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
        Instant observedAt,
        String accountMode,
        List<OkxPrivateBalanceFact> balances,
        OkxPrivateFeeFact fee,
        List<OkxPrivatePositionFact> positions
) {
    public OkxPrivateReadResult {
        Objects.requireNonNull(operation, "operation must not be null");
        normalizedPermissions = Set.copyOf(normalizedPermissions == null ? Set.of() : normalizedPermissions);
        orders = List.copyOf(orders == null ? List.of() : orders);
        fills = List.copyOf(fills == null ? List.of() : fills);
        balances = List.copyOf(balances == null ? List.of() : balances);
        positions = List.copyOf(positions == null ? List.of() : positions);
        Objects.requireNonNull(ipAllowlistStatus, "ipAllowlistStatus must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (assetCount < 0) throw new IllegalArgumentException("assetCount must not be negative");
        if (positions.size() > 100 || (!complete && !positions.isEmpty())
                || (operation != OkxPrivateReadOperation.OKX_ACCOUNT_POSITIONS_READ && !positions.isEmpty())) {
            throw new IllegalArgumentException("invalid OKX position result");
        }
    }

    /** 既有账户事实构造器保持兼容；仓位只能由独立的完整只读结果提供。 */
    public OkxPrivateReadResult(
            OkxPrivateReadOperation operation,
            Set<String> normalizedPermissions,
            int assetCount,
            boolean complete,
            List<OkxPrivateOrderSnapshot> orders,
            List<OkxPrivateFillSnapshot> fills,
            boolean ipAllowlistConfigured,
            OkxIpAllowlistStatus ipAllowlistStatus,
            Instant observedAt,
            String accountMode,
            List<OkxPrivateBalanceFact> balances,
            OkxPrivateFeeFact fee
    ) {
        this(operation, normalizedPermissions, assetCount, complete, orders, fills,
                ipAllowlistConfigured, ipAllowlistStatus, observedAt, accountMode, balances, fee, List.of());
    }

    public OkxPrivateReadResult(
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
        this(operation, normalizedPermissions, assetCount, complete, orders, fills,
                ipAllowlistConfigured, ipAllowlistStatus, observedAt, null, List.of(), null);
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
     * 兼容既有只读诊断调用方的构造器。
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

    @Override
    public String toString() {
        return "OkxPrivateReadResult[REDACTED]";
    }
}
