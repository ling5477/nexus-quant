package com.guidinglight.nexusquant.trading.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 原始意图之外的一次有效提交决定；拒绝也是可恢复事实，不能在重启时改算另一笔订单。 */
public record EffectiveOrderParameters(BigDecimal quantity, BigDecimal price, String rejectionCode) {
    public EffectiveOrderParameters {
        if (rejectionCode != null) {
            if (!rejectionCode.matches("[A-Z0-9_]{1,128}") || quantity != null || price != null) {
                throw new IllegalArgumentException("invalid normalization rejection");
            }
        } else {
            if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("effective quantity must be positive");
            quantity = exact(quantity);
            price = price == null ? null : exact(price);
            if (price != null && price.signum() <= 0) throw new IllegalArgumentException("effective price must be positive");
        }
    }

    private static BigDecimal exact(BigDecimal value) {
        BigDecimal result = value.setScale(8, RoundingMode.UNNECESSARY);
        if (result.precision() > 38) throw new IllegalArgumentException("effective parameter exceeds NUMERIC(38,8)");
        return result;
    }
}
