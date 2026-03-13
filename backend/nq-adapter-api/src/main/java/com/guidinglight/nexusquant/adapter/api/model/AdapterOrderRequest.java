package com.guidinglight.nexusquant.adapter.api.model;

import java.math.BigDecimal;

/**
 * AdapterOrderRequest 描述发送到交易所适配层的统一下单请求。
 * <p>
 * Why:
 * GateD 要把 `nq-core -> nq-adapter-*` 的字段名冻结成同一套语义，
 * 否则 adapter 层仍会继续消费 `qty/type` 这类历史命名，导致 contracts/core/adapter
 * 三层口径再次漂移。
 */
public record AdapterOrderRequest(
        String requestId,
        String orderId,
        Long accountId,
        String venue,
        String symbol,
        String clientOrderId,
        String idempotencyKey,
        String side,
        String orderType,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal quoteQuantity,
        String timeInForce,
        String source,
        String strategyRunId,
        String traceId
) {

    public AdapterOrderRequest {
        traceId = requireText(traceId, "traceId");
        requestId = normalizeText(requestId, traceId);
        venue = requireText(venue, "venue");
        symbol = requireText(symbol, "symbol");
        clientOrderId = requireText(clientOrderId, "clientOrderId");
        idempotencyKey = normalizeText(idempotencyKey, buildDefaultIdempotencyKey(accountId, clientOrderId));
        orderType = requireText(orderType, "orderType");
        timeInForce = normalizeText(timeInForce, defaultTimeInForce(orderType));
        source = normalizeText(source, defaultSource(strategyRunId));
    }

    private static String buildDefaultIdempotencyKey(Long accountId, String clientOrderId) {
        return accountId + ":" + clientOrderId;
    }

    private static String defaultTimeInForce(String orderType) {
        return "MARKET".equalsIgnoreCase(orderType) ? "IOC" : "GTC";
    }

    private static String defaultSource(String strategyRunId) {
        return strategyRunId == null || strategyRunId.isBlank() ? "manual" : "strategy";
    }

    private static String requireText(String value, String fieldName) {
        String normalized = normalizeText(value, null);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeText(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
