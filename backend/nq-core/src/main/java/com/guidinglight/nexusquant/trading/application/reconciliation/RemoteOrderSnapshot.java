package com.guidinglight.nexusquant.trading.application.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** 已规范化的 OKX 订单快照；禁止携带 raw JSON、header、signature 或 credential。 */
public record RemoteOrderSnapshot(
        String exchangeOrderId,
        String clientOrderId,
        String symbol,
        String side,
        String orderType,
        BigDecimal price,
        BigDecimal originalQuantity,
        BigDecimal filledQuantity,
        String remoteStatus,
        Instant observedAt,
        String sourceOperation
) {
    public RemoteOrderSnapshot {
        exchangeOrderId = blankToNull(exchangeOrderId);
        clientOrderId = blankToNull(clientOrderId);
        symbol = required(symbol, "symbol");
        side = required(side, "side");
        orderType = required(orderType, "orderType");
        price = optionalNonNegative(price, "price");
        originalQuantity = nonNegative(originalQuantity, "originalQuantity");
        filledQuantity = nonNegative(filledQuantity, "filledQuantity");
        remoteStatus = required(remoteStatus, "remoteStatus");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        sourceOperation = required(sourceOperation, "sourceOperation");
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
