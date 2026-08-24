package com.guidinglight.nexusquant.livecontrol.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 一次性占用 receipt；显式固定为无交易授权、无交易所 mutation。 */
public record ExactPilotBindingConsumption(
        UUID bindingId,
        String bindingDigest,
        Instant consumedAt,
        boolean tradingAuthorized,
        boolean exchangeMutation
) {
    public ExactPilotBindingConsumption {
        Objects.requireNonNull(bindingId, "bindingId must not be null");
        Objects.requireNonNull(bindingDigest, "bindingDigest must not be null");
        Objects.requireNonNull(consumedAt, "consumedAt must not be null");
        if (tradingAuthorized || exchangeMutation) {
            throw new IllegalArgumentException("binding consumption cannot authorize or mutate trading");
        }
    }
}
