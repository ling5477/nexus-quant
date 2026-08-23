package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.math.BigDecimal;
import java.util.Objects;

/** Operator必须显式提供的七项最小真实pilot输入。 */
public record MinimalLivePilotCommand(
        long exchangeAccountId,
        long credentialReferenceId,
        String instrument,
        ExactPilotBinding.Side side,
        BigDecimal limitPrice,
        BigDecimal quantity,
        BigDecimal configuredPilotMaxNotional
) {
    public MinimalLivePilotCommand {
        if (exchangeAccountId <= 0 || credentialReferenceId <= 0) {
            throw new IllegalArgumentException("account and credential references must be positive");
        }
        if (instrument == null || !instrument.matches("[A-Z0-9]{2,20}-USDT")) {
            throw new IllegalArgumentException("one exact OKX Spot instrument is required");
        }
        Objects.requireNonNull(side, "side must not be null");
        requirePositive(limitPrice, "limitPrice");
        requirePositive(quantity, "quantity");
        requirePositive(configuredPilotMaxNotional, "configuredPilotMaxNotional");
        if (limitPrice.multiply(quantity).compareTo(configuredPilotMaxNotional) > 0) {
            throw new IllegalArgumentException("operator notional exceeds configuredPilotMaxNotional");
        }
    }

    public BigDecimal notional() {
        return limitPrice.multiply(quantity);
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
