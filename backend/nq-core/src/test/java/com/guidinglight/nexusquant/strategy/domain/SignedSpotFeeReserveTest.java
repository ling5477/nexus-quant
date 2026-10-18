package com.guidinglight.nexusquant.strategy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

/** 用可负担边界证明收费、零费率及返佣；重点防止用未到账返佣购买超过现金的现货。 */
class SignedSpotFeeReserveTest {
    @Test
    void rebateNeverProvidesUpfrontBuyingPower() {
        // 已有持仓使目标增量高于现金购买力，能揭示原1+负费率公式的超买，而非只检查零仓位。
        var state = new SpotTargetSizer.State(d("100"), d("1"), d("0"), d("0"), d("0"), d("100"));
        var result = SpotTargetSizer.size(BigDecimal.ONE, state, rules("-0.1"));
        assertTrue(result.executable());
        assertEquals(0, d("1").compareTo(result.quantity()));
        assertEquals(0, d("-10").compareTo(result.fee()));
        assertEquals(0, d("10").compareTo(result.remainingCash()));
        assertTrue(result.quantity().multiply(result.fillPrice()).compareTo(state.cash()) <= 0);
    }

    @Test
    void pendingPrincipalCannotBeOffsetByRebate() {
        var state =
                new SpotTargetSizer.State(
                        d("100"),
                        d("1"),
                        d("0.5"),
                        d("0"),
                        SpotFeeReserve.buyCost(d("50"), d("-0.1")),
                        d("100"));
        var result = SpotTargetSizer.size(BigDecimal.ONE, state, rules("-0.1"));
        assertTrue(result.executable());
        assertEquals(0, d("0.5").compareTo(result.quantity()));
        assertTrue(
                SpotFeeReserve.buyCost(result.fillPrice().multiply(result.quantity()), d("-0.1"))
                                .add(state.pendingBuyCost())
                                .compareTo(state.cash())
                        <= 0);
    }

    @Test
    void chargeZeroAndRebateKeepTheirMeaning() {
        assertEquals(0, d("110").compareTo(SpotFeeReserve.buyCost(d("100"), d("0.1"))));
        assertEquals(0, d("100").compareTo(SpotFeeReserve.buyCost(d("100"), d("0"))));
        assertEquals(0, d("100").compareTo(SpotFeeReserve.buyCost(d("100"), d("-0.1"))));
        assertEquals(
                0,
                d("100.00000001").compareTo(SpotFeeReserve.buyCost(d("100"), d("0.000000000001"))));
        var charged =
                SpotTargetSizer.size(
                        BigDecimal.ONE,
                        new SpotTargetSizer.State(
                                d("100"), d("1"), d("0"), d("0"), d("0"), d("100")),
                        rules("0.1"));
        assertTrue(charged.executable());
        assertTrue(charged.quantity().compareTo(BigDecimal.ONE) < 0);
    }

    @Test
    void invalidRatesAndNoFreeCapitalFailClosed() {
        for (String rate : new String[] {"-1", "1", "-2", "2"}) {
            assertThrows(IllegalArgumentException.class, () -> rules(rate));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SpotFeeReserve.buyCost(BigDecimal.ONE, d(rate)));
        }
        var result =
                SpotTargetSizer.size(
                        BigDecimal.ONE,
                        new SpotTargetSizer.State(
                                d("100"), d("1"), d("0.1"), d("0"), d("100"), d("100")),
                        rules("-0.1"));
        assertFalse(result.executable());
        assertEquals("INSUFFICIENT_CASH", result.reason());
    }

    /** 两笔本金各自舍入后不能多花一分钱的最小单位；舍入为零的返佣也不能掩盖超额。 */
    @Test
    void separatePrincipalRoundingMustMatchCanonicalCashDebit() {
        BigDecimal first = d("999.99999999").multiply(d("0.5"));
        BigDecimal second = d("1000.00000001").multiply(d("0.5"));
        for (String value : new String[] {"0", "-0.000000000001"}) {
            BigDecimal rate = d(value), pending = SpotFeeReserve.buyCost(first, rate);
            assertEquals(0, d("500").compareTo(pending));
            assertEquals(0, d("500.00000001").compareTo(SpotFeeReserve.buyCost(second, rate)));
            assertTrue(pending.add(SpotFeeReserve.buyCost(second, rate)).compareTo(d("1000")) > 0);
            var result =
                    SpotTargetSizer.size(
                            BigDecimal.ONE,
                            new SpotTargetSizer.State(
                                    d("1000"),
                                    d("1"),
                                    d("0.5"),
                                    BigDecimal.ZERO,
                                    pending,
                                    d("1000")),
                            new SpotTargetSizer.Rules(
                                    d("0.5"),
                                    d("0.00000001"),
                                    d("0.5"),
                                    BigDecimal.ONE,
                                    rate,
                                    d("0.0000000001")));
            assertFalse(result.executable());
        }
    }

    private static SpotTargetSizer.Rules rules(String rate) {
        return new SpotTargetSizer.Rules(
                d("0.0001"), d("0.01"), d("0.0001"), d("1"), d(rate), BigDecimal.ZERO);
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }
}
