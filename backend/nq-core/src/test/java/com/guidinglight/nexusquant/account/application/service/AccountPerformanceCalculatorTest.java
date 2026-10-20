package com.guidinglight.nexusquant.account.application.service;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.account.domain.performance.AccountEquitySample;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 以已知权益/净注资序列核验收益与回撤；覆盖部分数据、零期初及大金额微小差额，不能替代真实账户来源。 */
class AccountPerformanceCalculatorTest {
    private final Instant from = Instant.parse("2026-10-01T00:00:00Z"),
            to = Instant.parse("2026-10-01T03:00:00Z");
    private final AccountPerformanceCalculator calculator = new AccountPerformanceCalculator();

    private AccountEquitySample sample(int hour, String equity, String flow) {
        return new AccountEquitySample(
                UUID.randomUUID(),
                1,
                "USDT",
                from.plusSeconds(hour * 3600L),
                new BigDecimal(equity),
                new BigDecimal(flow),
                "AVAILABLE",
                "{}");
    }

    @Test
    void newFundingIsNotProfitAndDoesNotEraseDrawdown() {
        var result =
                calculator.summarize(
                        1,
                        "USDT",
                        from,
                        to,
                        List.of(
                                sample(0, "1000", "1000"),
                                sample(1, "1100", "1000"),
                                sample(2, "2050", "2000")));
        assertEquals(new BigDecimal("50"), result.pnl());
        assertEquals(new BigDecimal("0.05"), result.returnRatio());
        assertEquals(new BigDecimal("50"), result.maxDrawdown());
        assertTrue(
                result.maxDrawdownRatio()
                                .subtract(
                                        new BigDecimal("50")
                                                .divide(
                                                        new BigDecimal("1100"),
                                                        MathContext.DECIMAL128))
                                .abs()
                                .compareTo(new BigDecimal("1e-30"))
                        < 0);
        assertEquals(from.plusSeconds(7200), result.coverageEnd());
    }

    @Test
    void missingValuationOrSingleSampleIsNotZeroReturn() {
        var single = calculator.summarize(1, "USDT", from, to, List.of(sample(0, "1000", "1000")));
        assertNull(single.returnRatio());
        assertNull(single.pnl());
        assertEquals("INSUFFICIENT_OR_PARTIAL", single.status());
        var missing =
                new AccountEquitySample(
                        UUID.randomUUID(),
                        1,
                        "USDT",
                        from.plusSeconds(3600),
                        null,
                        null,
                        "MISSING_PRICE",
                        "{}");
        assertNull(
                calculator
                        .summarize(
                                1,
                                "USDT",
                                from,
                                to,
                                List.of(
                                        sample(0, "1000", "1000"),
                                        missing,
                                        sample(2, "1100", "1000")))
                        .pnl());
        assertEquals("NO_DATA", calculator.summarize(1, "USDT", from, to, List.of()).status());
    }

    @Test
    void scopeMismatchCannotMixIndependentSimOrCurrency() {
        var foreign =
                new AccountEquitySample(
                        UUID.randomUUID(),
                        2,
                        "USDT",
                        from,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        "AVAILABLE",
                        "{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.summarize(1, "USDT", from, to, List.of(foreign)));
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.summarize(1, "BTC", from, to, List.of(sample(0, "1", "0"))));
    }

    @Test
    void exactSmallPnlAndZeroInitialEquityStayDistinct() {
        var result =
                calculator.summarize(
                        1,
                        "USDT",
                        from,
                        to,
                        List.of(
                                sample(0, "999999999999999999.00000000", "1"),
                                sample(1, "999999999999999999.00000001", "1")));
        assertEquals(new BigDecimal("0.00000001"), result.pnl());
        var zero =
                calculator.summarize(
                        1, "USDT", from, to, List.of(sample(0, "0", "0"), sample(1, "0", "0")));
        assertEquals(BigDecimal.ZERO, zero.pnl());
        assertNull(zero.returnRatio());
    }
}
