package com.guidinglight.nexusquant.adapter.okx.privateread.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 经固定 OKX balance schema 解析的账户币种事实；不含原始响应。 */
public record OkxPrivateBalanceFact(
        String currency,
        BigDecimal total,
        BigDecimal available,
        BigDecimal frozen,
        Instant providerUpdatedAt
) {
    public OkxPrivateBalanceFact {
        Objects.requireNonNull(currency);
        Objects.requireNonNull(total);
        Objects.requireNonNull(available);
        Objects.requireNonNull(frozen);
        Objects.requireNonNull(providerUpdatedAt);
        if (total.signum() < 0 || available.signum() < 0 || frozen.signum() < 0
                || total.scale() > 18 || available.scale() > 18 || frozen.scale() > 18
                || available.add(frozen).compareTo(total) > 0) {
            throw new IllegalArgumentException("invalid OKX balance fact");
        }
    }

    @Override
    public String toString() {
        return "OkxPrivateBalanceFact[REDACTED]";
    }
}
