package com.guidinglight.nexusquant.trading.application.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 不含写端口或 provider payload 的本地订单事实投影。 */
public record LocalOrderSnapshot(
        String localOrderReference,
        String clientOrderId,
        String exchangeOrderId,
        String symbol,
        String side,
        String orderType,
        BigDecimal price,
        BigDecimal originalQuantity,
        BigDecimal filledQuantity,
        String localStatus,
        Instant updatedAt
) {
    public LocalOrderSnapshot {
        localOrderReference = required(localOrderReference, "localOrderReference");
        symbol = required(symbol, "symbol");
        side = required(side, "side");
        orderType = required(orderType, "orderType");
        price = optionalNonNegative(price, "price");
        originalQuantity = nonNegative(originalQuantity, "originalQuantity");
        filledQuantity = nonNegative(filledQuantity, "filledQuantity");
        localStatus = required(localStatus, "localStatus");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        clientOrderId = blankToNull(clientOrderId);
        exchangeOrderId = blankToNull(exchangeOrderId);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.signum() < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }

    private static BigDecimal optionalNonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }
}
