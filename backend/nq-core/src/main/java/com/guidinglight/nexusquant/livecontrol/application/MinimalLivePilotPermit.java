package com.guidinglight.nexusquant.livecontrol.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Sanitized one-time permit identity；不包含credential material或provider payload。 */
public record MinimalLivePilotPermit(
        long ownerId,
        UUID sessionId,
        UUID bindingId,
        String bindingDigest,
        UUID leaseId,
        UUID placeIntentId,
        String clientOrderId,
        String requestId,
        String traceId,
        BigDecimal limitPrice,
        BigDecimal quantity,
        BigDecimal notional
) {
    public MinimalLivePilotPermit {
        if (ownerId <= 0) throw new IllegalArgumentException("ownerId must be positive");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(bindingId, "bindingId must not be null");
        Objects.requireNonNull(bindingDigest, "bindingDigest must not be null");
        Objects.requireNonNull(leaseId, "leaseId must not be null");
        Objects.requireNonNull(placeIntentId, "placeIntentId must not be null");
        Objects.requireNonNull(clientOrderId, "clientOrderId must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        requirePositive(limitPrice, "limitPrice");
        requirePositive(quantity, "quantity");
        requirePositive(notional, "notional");
        if (limitPrice.multiply(quantity).compareTo(notional) != 0) {
            throw new IllegalArgumentException("notional must equal limitPrice multiplied by quantity");
        }
    }

    private static void requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
