package com.guidinglight.nexusquant.strategy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class SpotTargetSizerTest {
    private final SpotTargetSizer.Rules rules = new SpotTargetSizer.Rules(d("0.0001"), d("0.01"),
            d("0.0001"), d("10"), d("0.001"), d("10"));

    @Test
    void budgetsHaveExecutableQuantityOrStableReason() {
        var ten = size("10", "0", "0", "0", "0", "100");
        assertFalse(ten.executable());
        assertEquals("MIN_NOTIONAL", ten.reason());
        for (String budget : new String[]{"100", "1000"}) {
            var result = size(budget, "0", "0", "0", "0", "100");
            assertTrue(result.executable());
            assertTrue(result.quantity().signum() > 0);
            assertTrue(result.remainingCash().signum() >= 0);
            assertTrue(result.fillPrice().multiply(result.quantity()).add(result.fee())
                    .compareTo(d(budget)) <= 0);
        }
    }

    @Test
    void positionAndPendingExposureReduceIncrementalOrder() {
        var empty = size("100", "0", "0", "0", "0", "100");
        var owned = size("0", "1", "0", "0", "0", "100");
        assertEquals("ALREADY_AT_TARGET", owned.reason());
        var pending = size("100", "0", "0.5", "0", "50", "100");
        assertTrue(pending.executable());
        assertTrue(pending.quantity().compareTo(empty.quantity()) < 0);
        var sell = SpotTargetSizer.size(BigDecimal.ZERO,
                new SpotTargetSizer.State(d("0"), d("1"), d("0"), d("0.5"), d("0"), d("100")), rules);
        assertEquals("SELL", sell.side());
        assertEquals(0, d("0.5").compareTo(sell.quantity()));
    }

    @Test
    void feeReserveRoundingAndDustCannotOverspend() {
        assertEquals("MIN_NOTIONAL", size("10", "0", "0", "0", "0", "100").reason());
        var dust = SpotTargetSizer.size(d("0.001"),
                new SpotTargetSizer.State(d("0.01"), d("0"), d("0"), d("0"), d("0"), d("100")), rules);
        assertEquals("DUST", dust.reason());
        var reserved = size("100", "0", "0.9999", "0", "99.99", "100");
        assertFalse(reserved.executable());
    }

    private SpotTargetSizer.Result size(String cash, String position, String pendingBuy,
                                         String pendingSell, String pendingCost, String price) {
        return SpotTargetSizer.size(BigDecimal.ONE,
                new SpotTargetSizer.State(d(cash), d(position), d(pendingBuy), d(pendingSell),
                        d(pendingCost), d(price)), rules);
    }

    private static BigDecimal d(String value) { return new BigDecimal(value); }
}
