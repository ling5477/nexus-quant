package com.guidinglight.nexusquant.livecontrol.execution.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * V39 execution_intents 的不可变快照。它只描述外部动作意图，不拥有 order/trade/position 事实。
 */
public record ExecutionIntent(
        UUID intentId,
        UUID sessionId,
        long sequence,
        ExecutionIntentAction action,
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        String payloadHashSchemaVersion,
        String payloadHash,
        String clientOrderId,
        String localOrderId,
        ExecutionIntentState state,
        long version,
        String claimedBy,
        UUID claimToken,
        Instant claimedAt,
        Instant leaseExpiresAt,
        Instant sendStartedAt,
        Instant createdAt
) {
    public ExecutionIntent {
        Objects.requireNonNull(intentId, "intentId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(payloadHashSchemaVersion, "payloadHashSchemaVersion must not be null");
        Objects.requireNonNull(payloadHash, "payloadHash must not be null");
        Objects.requireNonNull(clientOrderId, "clientOrderId must not be null");
        Objects.requireNonNull(localOrderId, "localOrderId must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (sequence <= 0 || version <= 0) {
            throw new IllegalArgumentException("sequence and version must be positive");
        }
    }

    public boolean samePayload(String expectedHash) {
        return payloadHash.equals(expectedHash);
    }

    public boolean mutationMayRun() {
        return state == ExecutionIntentState.SEND_STARTED;
    }
}
