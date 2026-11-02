package com.guidinglight.nexusquant.livecontrol.execution.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** 创建前的 canonical intent facts；sequence 与 DB 时间由 repository 在短事务内分配。 */
public record ExecutionIntentDraft(
        UUID intentId,
        UUID sessionId,
        ExecutionIntentAction action,
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        String localOrderId,
        String clientOrderId,
        String payloadHash
) {
    public static final String PAYLOAD_SCHEMA = "execution-intent-payload.v1";

    public ExecutionIntentDraft {
        Objects.requireNonNull(intentId, "intentId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        symbol = requireText(symbol, "symbol");
        localOrderId = requireText(localOrderId, "localOrderId");
        clientOrderId = requireText(clientOrderId, "clientOrderId");
        if (clientOrderId.length() > 128) {
            throw new IllegalArgumentException("clientOrderId exceeds V39 limit");
        }
        if (payloadHash == null || !payloadHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadHash must be lowercase SHA-256");
        }
        if (action == ExecutionIntentAction.PLACE) {
            if (!("BUY".equals(side) || "SELL".equals(side)) || !"LIMIT".equals(orderType)
                    || quantity == null || quantity.signum() <= 0
                    || limitPrice == null || limitPrice.signum() <= 0) {
                throw new IllegalArgumentException("PLACE requires BUY/SELL LIMIT with positive quantity and price");
            }
        } else if (side != null || orderType != null || quantity != null || limitPrice != null) {
            throw new IllegalArgumentException("CANCEL side/orderType/quantity/limitPrice must be null");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
