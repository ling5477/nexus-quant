package com.guidinglight.nexusquant.adapter.binance.fee;

import java.math.BigDecimal;
import java.util.Objects;

/** 优惠开关只说明可采用的条件，不证明优惠资产余额足额或某笔成交实际使用了优惠。 */
public record BinanceCommissionDiscount(
        boolean enabledForAccount, boolean enabledForSymbol, String asset, BigDecimal multiplier) {
    public BinanceCommissionDiscount {
        Objects.requireNonNull(asset);
        Objects.requireNonNull(multiplier);
        if (!asset.matches("[A-Z0-9]{1,20}")
                || multiplier.scale() > 12
                || multiplier.signum() < 0
                || multiplier.compareTo(BigDecimal.ONE) > 0) {
            throw new BinanceFeeReadException(BinanceFeeReadException.Reason.INVALID_DISCOUNT);
        }
    }

    public boolean enabled() {
        return enabledForAccount && enabledForSymbol;
    }
}
