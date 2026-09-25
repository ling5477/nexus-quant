package com.guidinglight.nexusquant.adapter.okx.privateread.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 私有账户返回的指定 SPOT instrument 实际费率。 */
public record OkxPrivateFeeFact(
        String instrumentId,
        BigDecimal makerRate,
        BigDecimal takerRate,
        String tier,
        Instant providerTimestamp
) {
    public OkxPrivateFeeFact {
        Objects.requireNonNull(instrumentId);
        Objects.requireNonNull(makerRate);
        Objects.requireNonNull(takerRate);
        Objects.requireNonNull(tier);
        Objects.requireNonNull(providerTimestamp);
        if (makerRate.abs().compareTo(BigDecimal.ONE) > 0
                || takerRate.abs().compareTo(BigDecimal.ONE) > 0
                || makerRate.scale() > 12 || takerRate.scale() > 12) {
            throw new IllegalArgumentException("invalid OKX fee fact");
        }
    }

    @Override
    public String toString() {
        return "OkxPrivateFeeFact[REDACTED]";
    }
}
