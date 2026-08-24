package com.guidinglight.nexusquant.livecontrol.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MinimalLivePilotCommandTest {

    @Test
    void acceptsOnlyExplicitLimitEnvelopeWithinOperatorMaximum() {
        MinimalLivePilotCommand command = new MinimalLivePilotCommand(
                1, 2, "BTC-USDT", ExactPilotBinding.Side.BUY,
                new BigDecimal("100.00000000"), new BigDecimal("0.01000000"),
                new BigDecimal("1.00000000"));

        assertEquals(0, command.notional().compareTo(new BigDecimal("1.0000000000000000")));
    }

    @Test
    void rejectsMissingInvalidOrOverCapOperatorValues() {
        assertThrows(IllegalArgumentException.class, () -> new MinimalLivePilotCommand(
                0, 2, "BTC-USDT", ExactPilotBinding.Side.BUY,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new MinimalLivePilotCommand(
                1, 2, "*", ExactPilotBinding.Side.BUY,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new MinimalLivePilotCommand(
                1, 2, "BTC-USDT", null,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new MinimalLivePilotCommand(
                1, 2, "BTC-USDT", ExactPilotBinding.Side.SELL,
                new BigDecimal("2"), BigDecimal.ONE, BigDecimal.ONE));
    }
}
