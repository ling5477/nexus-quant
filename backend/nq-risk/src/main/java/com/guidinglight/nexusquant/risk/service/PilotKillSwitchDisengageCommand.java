package com.guidinglight.nexusquant.risk.service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Exact active pilot lease 绑定的唯一短时 kill disengage command。 */
public record PilotKillSwitchDisengageCommand(
        KillSwitchScope scope,
        long expectedVersion,
        UUID leaseId,
        Instant leaseExpiresAt,
        String updatedBy,
        String traceId,
        Instant occurredAt
) {
    public PilotKillSwitchDisengageCommand {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(leaseId, "leaseId must not be null");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (expectedVersion <= 0 || !leaseExpiresAt.isAfter(occurredAt)) {
            throw new IllegalArgumentException("valid expectedVersion and future lease expiry are required");
        }
        updatedBy = requireText(updatedBy, "updatedBy");
        traceId = requireText(traceId, "traceId");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
