package com.guidinglight.nexusquant.adapter.api.model;

import static com.guidinglight.nexusquant.common.text.NullableText.firstNonBlank;

import java.math.BigDecimal;

/**
 * AdapterOrderRequest 描述发送到交易所适配层的统一下单请求。
 * <p>
 * Why:
 * 统一交易契约要把 `nq-core -> nq-adapter-*`的字段名冻结成同一套语义，
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
        requestId = firstNonBlank(requestId, traceId);
        venue = requireText(venue, "venue");
        symbol = requireText(symbol, "symbol");
        clientOrderId = requireText(clientOrderId, "clientOrderId");
        idempotencyKey = firstNonBlank(idempotencyKey, buildDefaultIdempotencyKey(accountId, clientOrderId));
        orderType = requireText(orderType, "orderType");
        timeInForce = firstNonBlank(timeInForce, defaultTimeInForce(orderType));
        source = firstNonBlank(source, defaultSource(strategyRunId));
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
        String normalized = firstNonBlank(value, null);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

}
