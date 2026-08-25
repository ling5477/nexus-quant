package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.math.BigDecimal;
import java.util.Objects;

/** Operator 固定的五项最小真实 pilot 输入；price/quantity 只能从本次 prerequisite facts 计算。 */
public record MinimalLivePilotCommand(
        long exchangeAccountId,
        long credentialReferenceId,
        String instrument,
        ExactPilotBinding.Side side,
        BigDecimal configuredPilotMaxNotional
) {
    public static final String REQUIRED_INSTRUMENT = "BTC-USDT";
    public static final BigDecimal HARD_CAP = new BigDecimal("10.00000000");

    public MinimalLivePilotCommand {
        if (exchangeAccountId <= 0 || credentialReferenceId <= 0) {
            throw new IllegalArgumentException("account and credential references must be positive");
        }
        if (!REQUIRED_INSTRUMENT.equals(instrument)) {
            throw new IllegalArgumentException("BTC-USDT is the only authorized pilot instrument");
        }
        Objects.requireNonNull(side, "side must not be null");
        if (side != ExactPilotBinding.Side.BUY) {
            throw new IllegalArgumentException("BUY is the only authorized pilot side");
        }
        requirePositive(configuredPilotMaxNotional, "configuredPilotMaxNotional");
        if (configuredPilotMaxNotional.compareTo(HARD_CAP) > 0) {
            throw new IllegalArgumentException("configuredPilotMaxNotional exceeds the 10 USDT hard cap");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
