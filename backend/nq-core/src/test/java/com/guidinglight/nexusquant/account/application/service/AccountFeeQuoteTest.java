package com.guidinglight.nexusquant.account.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 用确定性时点验证收费、返佣、零费率和精度边界；这是数值合同测试，不证明真实供应方费率。 */
class AccountFeeQuoteTest {
    private static final Instant NOW = Instant.parse("2026-10-13T04:00:00.123456789Z");

    @Test
    void preservesProviderChargeAndRebateAsSeparateSignedCost() {
        var quote =
                new AccountFeeQuote(
                        new BigDecimal("0.0002"), new BigDecimal("-0.001"), "Lv1", NOW, NOW);
        assertEquals(0, new BigDecimal("-0.0002").compareTo(quote.makerCostRate()));
        assertEquals(0, new BigDecimal("0.001").compareTo(quote.takerCostRate()));
        assertEquals(12, quote.makerProviderRate().scale());
        assertEquals(NOW.truncatedTo(ChronoUnit.MICROS), quote.observedAt());
        assertFalse(quote.toString().contains("0.0002"));
    }

    @Test
    void rejectsIncompleteStaleFutureAndUnrepresentableFacts() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AccountFeeQuote(
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                "Lv1",
                                NOW.minusSeconds(3601),
                                NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AccountFeeQuote(
                                BigDecimal.ZERO, BigDecimal.ZERO, "Lv1", NOW.plusSeconds(1), NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AccountFeeQuote(
                                new BigDecimal("1.01"), BigDecimal.ZERO, "Lv1", NOW, NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AccountFeeQuote(
                                new BigDecimal("0.0000000000001"),
                                BigDecimal.ZERO,
                                "Lv1",
                                NOW,
                                NOW));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AccountFeeQuote(
                                BigDecimal.ZERO, BigDecimal.ZERO, "untrusted field", NOW, NOW));
    }

    @Test
    void zeroFeesAreObservedFactsRatherThanMissingValues() {
        var quote = new AccountFeeQuote(BigDecimal.ZERO, BigDecimal.ZERO, "Lv0", NOW, NOW);
        assertEquals(0, quote.makerCostRate().signum());
        assertEquals(0, quote.takerCostRate().signum());
    }
}
