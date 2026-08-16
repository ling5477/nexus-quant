package com.guidinglight.nexusquant.adapter.okx.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 四类 OKX prerequisite facts 的完整、typed、credential-free collection snapshot。
 */
public record OkxPilotPrerequisiteSnapshot(
        List<InstrumentFact> instruments,
        List<FeeFact> fees,
        BigDecimal availableUsdtBalance,
        Instant okxServerTime,
        Instant localClockMidpoint,
        long observedSkewMs
) {
    public OkxPilotPrerequisiteSnapshot {
        instruments = List.copyOf(Objects.requireNonNull(instruments, "instruments must not be null"));
        fees = List.copyOf(Objects.requireNonNull(fees, "fees must not be null"));
        Objects.requireNonNull(availableUsdtBalance, "availableUsdtBalance must not be null");
        Objects.requireNonNull(okxServerTime, "okxServerTime must not be null");
        Objects.requireNonNull(localClockMidpoint, "localClockMidpoint must not be null");
        if (instruments.size() < 1 || instruments.size() > 2 || fees.size() != instruments.size()) {
            throw new IllegalArgumentException("prerequisite collection is incomplete");
        }
    }

    public record InstrumentFact(
            String instrument,
            String state,
            String feeGroupId,
            BigDecimal tickSize,
            BigDecimal lotSize,
            BigDecimal minimumOrderSize
    ) {
        public InstrumentFact {
            requireText(instrument, "instrument");
            requireText(state, "state");
            requireText(feeGroupId, "feeGroupId");
            requirePositive(tickSize, "tickSize");
            requirePositive(lotSize, "lotSize");
            requirePositive(minimumOrderSize, "minimumOrderSize");
        }
    }

    public record FeeFact(
            String instrument,
            String level,
            String groupId,
            BigDecimal makerRate,
            BigDecimal takerRate,
            Instant providerTimestamp
    ) {
        public FeeFact {
            requireText(instrument, "instrument");
            requireText(level, "level");
            requireText(groupId, "groupId");
            requireRate(makerRate, "makerRate");
            requireRate(takerRate, "takerRate");
            Objects.requireNonNull(providerTimestamp, "providerTimestamp must not be null");
        }

        public String tierIdentity() {
            return level + "/" + groupId;
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 64
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() <= 0 || value.scale() > 18) {
            throw new IllegalArgumentException(name + " must be positive and bounded");
        }
    }

    private static void requireRate(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.compareTo(BigDecimal.ONE.negate()) < 0 || value.compareTo(BigDecimal.ONE) > 0
                || value.scale() > 12) {
            throw new IllegalArgumentException(name + " is outside the supported range");
        }
    }
}
