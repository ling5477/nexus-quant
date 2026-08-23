package com.guidinglight.nexusquant.livecontrol.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Server-derived release/risk下的单symbol最小pilot materialization command。 */
public record MinimalPilotMaterializationCommand(
        UUID sessionId,
        UUID pilotScopeId,
        long exchangeAccountId,
        long credentialReferenceId,
        String instrument,
        BigDecimal configuredPilotMaxNotional,
        Instant executionWindowStart,
        Instant executionWindowEnd,
        String idempotencyKey,
        String requestId,
        String traceId
) {
    public MinimalPilotMaterializationCommand {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(pilotScopeId, "pilotScopeId must not be null");
        if (exchangeAccountId <= 0 || credentialReferenceId <= 0) {
            throw new IllegalArgumentException("account and credential references must be positive");
        }
        if (instrument == null || !instrument.matches("[A-Z0-9]{2,20}-USDT")) {
            throw new IllegalArgumentException("one exact OKX Spot instrument is required");
        }
        Objects.requireNonNull(configuredPilotMaxNotional, "configuredPilotMaxNotional must not be null");
        if (configuredPilotMaxNotional.signum() <= 0) {
            throw new IllegalArgumentException("configuredPilotMaxNotional must be positive");
        }
        Objects.requireNonNull(executionWindowStart, "executionWindowStart must not be null");
        Objects.requireNonNull(executionWindowEnd, "executionWindowEnd must not be null");
        if (!executionWindowEnd.isAfter(executionWindowStart)) {
            throw new IllegalArgumentException("execution window must be non-empty");
        }
        requireText(idempotencyKey, "idempotencyKey");
        requireText(requestId, "requestId");
        requireText(traceId, "traceId");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }
}
