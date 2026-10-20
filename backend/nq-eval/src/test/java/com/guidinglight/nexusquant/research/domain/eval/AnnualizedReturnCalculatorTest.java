package com.guidinglight.nexusquant.research.domain.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

/** 派生年化值的失败不能污染真实总收益；覆盖原分钟级溢出、数值极端及既有口径。 */
class AnnualizedReturnCalculatorTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void finiteMinutePeriodOverflowRemainsExplicitlyUnavailable() {
        // 六根分钟bar的0.2003%正收益产生有限但超过numeric(36,18)的年化值。
        var result = calculate("0.002003", 300);
        assertNull(result.value());
        assertEquals(AnnualizedReturnCalculator.Status.OUT_OF_RANGE, result.status());
    }

    @Test
    void infiniteAnnualizationDoesNotBecomeZeroOrCappedReturn() {
        var result = calculate("0.1", 1);
        assertNull(result.value());
        assertEquals(AnnualizedReturnCalculator.Status.OUT_OF_RANGE, result.status());
    }

    @Test
    void storageLimitIsExclusiveAndValuesInsideRangeRemainAvailable() {
        assertEquals(
                AnnualizedReturnCalculator.Status.OUT_OF_RANGE,
                calculate("1000000000000000000", 31_536_000).status());
        var inside = calculate("900000000000000000", 31_536_000);
        assertEquals(AnnualizedReturnCalculator.Status.AVAILABLE, inside.status());
        assertTrue(inside.value().abs().compareTo(new BigDecimal("1000000000000000000")) < 0);
    }

    @Test
    void oneYearUsesUnchangedReturnAndZeroIsAvailable() {
        assertEquals(0, new BigDecimal("0.2").compareTo(calculate("0.2", 31_536_000).value()));
        assertEquals(0, BigDecimal.ZERO.compareTo(calculate("0", 300).value()));
        assertEquals(AnnualizedReturnCalculator.Status.AVAILABLE, calculate("0", 300).status());
    }

    @Test
    void tinyPositiveAndNegativeReturnsDoNotDisappearDuringAnnualization() {
        assertTrue(calculate("0.000000000000000001", 60).value().signum() > 0);
        assertTrue(calculate("-0.000000000000000001", 60).value().signum() < 0);
    }

    @Test
    void negativeMinuteReturnIsRepresentableWithoutOverflow() {
        var result = calculate("-0.002003", 300);
        assertEquals(AnnualizedReturnCalculator.Status.AVAILABLE, result.status());
        assertTrue(result.value().compareTo(BigDecimal.valueOf(-1)) >= 0);
        assertTrue(result.value().signum() < 0);
    }

    @Test
    void positiveGrowthNearFullLossSurvivesTwoAndTenYearAnnualization() {
        // 独立审查反例：增长因子10^-18的两年年化为10^-9-1，不能以double(-1)代替。
        var twoYears = calculate("-0.999999999999999999", 2L * 31_536_000);
        assertEquals(AnnualizedReturnCalculator.Status.AVAILABLE, twoYears.status());
        assertEquals(0, new BigDecimal("-0.999999999000000000").compareTo(twoYears.value()));
        var tenYears = calculate("-0.999999999999999999", 10L * 31_536_000);
        assertEquals(AnnualizedReturnCalculator.Status.AVAILABLE, tenYears.status());
        assertTrue(
                tenYears.value()
                                .subtract(new BigDecimal("-0.984151068075388900"))
                                .abs()
                                .compareTo(new BigDecimal("0.000000000000001"))
                        < 0);
    }

    @Test
    void invalidDurationAndNonPositiveGrowthRetainDifferentReasons() {
        assertEquals(
                AnnualizedReturnCalculator.Status.INVALID_PERIOD, calculate("0.1", 0).status());
        assertEquals(
                AnnualizedReturnCalculator.Status.INVALID_PERIOD, calculate("0.1", -1).status());
        for (String rate : new String[] {"-1", "-2"}) {
            var result = calculate(rate, 300);
            assertNull(result.value());
            assertEquals(AnnualizedReturnCalculator.Status.NON_POSITIVE_GROWTH, result.status());
        }
    }

    private static AnnualizedReturnCalculator.Result calculate(String rate, long seconds) {
        return AnnualizedReturnCalculator.calculate(
                new BigDecimal(rate), START, START.plusSeconds(seconds));
    }
}
