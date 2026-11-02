package com.guidinglight.nexusquant.trading.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** 查询前的原订单快照与已验证最终取消累计量；版本不得在查询后刷新。 */
public record OrderCancelFinality(OrderRecord observedOrder, BigDecimal executedQuantity) {
    public OrderCancelFinality {
        Objects.requireNonNull(observedOrder);
        Objects.requireNonNull(executedQuantity);
        if (observedOrder.strategyRunId() == null || executedQuantity.signum() < 0
                || observedOrder.qty() == null || executedQuantity.compareTo(observedOrder.qty()) >= 0) {
            throw new IllegalArgumentException("invalid strategy order cancel finality");
        }
    }
}
