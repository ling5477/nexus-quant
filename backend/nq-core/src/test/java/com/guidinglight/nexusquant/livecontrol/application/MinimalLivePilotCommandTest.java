package com.guidinglight.nexusquant.livecontrol.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MinimalLivePilotCommandTest {

    @Test
    void acceptsOnlyFixedBuyBtcScopeWithinHardCap() {
        MinimalLivePilotCommand command = new MinimalLivePilotCommand(
                1, 2, "BTC-USDT", ExactPilotBinding.Side.BUY,
                new BigDecimal("10.00000000"));

        assertEquals(0, command.configuredPilotMaxNotional().compareTo(new BigDecimal("10.00000000")));
    }

    @Test
    void rejectsMissingInvalidOrOverCapOperatorValues() {
        assertThrows(IllegalArgumentException.class, () -> new MinimalLivePilotCommand(
                0, 2, "BTC-USDT", ExactPilotBinding.Side.BUY,
                BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new MinimalLivePilotCommand(
                1, 2, "*", ExactPilotBinding.Side.BUY,
                BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new MinimalLivePilotCommand(
                1, 2, "BTC-USDT", null,
                BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new MinimalLivePilotCommand(
                1, 2, "BTC-USDT", ExactPilotBinding.Side.SELL,
                BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new MinimalLivePilotCommand(
                1, 2, "BTC-USDT", ExactPilotBinding.Side.BUY,
                new BigDecimal("10.00000001")));
    }
}
