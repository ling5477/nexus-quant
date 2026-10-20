package com.guidinglight.nexusquant.ledger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.ledger.contracts.model.SimAssetFundingPlan;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/** 独立数值 oracle 检查预算守恒与精度；持久化和事务由真实 PG 证明。 */
class SimAssetFundingPlanTest {
    private static final Instant AT = Instant.parse("2026-10-14T00:00:00Z");
    private static final String SOURCE = "{\"source\":\"SYNTHETIC_ISOLATED_FUNDING_TEST\"}";

    @Test
    void roundsAssetDownAndAllocatesRemainderToCash() {
        var plan = plan("100", "0.1", "3");
        equal("3.33333333", plan.assetQuantity());
        equal("9.99999999", plan.assetInitialQuoteValue());
        equal("90.00000001", plan.cashBudget());
        equal("100", plan.cashBudget().add(plan.assetInitialQuoteValue()));
    }

    @Test
    void midpointQuoteRoundingUsesHalfUpWithoutGrantingExtraCapital() {
        var plan = plan("1", "0.5", "0.000000015");
        equal("0.5", plan.assetInitialQuoteValue());
        equal("1", plan.cashBudget().add(plan.assetInitialQuoteValue()));
        assertTrue(
                plan.assetQuantity()
                                .multiply(plan.conversionPrice())
                                .compareTo(plan.totalBudget().multiply(plan.assetBudgetFraction()))
                        <= 0);
    }

    @Test
    void allBudgetAndPriceEdgesRemainConservedAtEightDecimals() {
        int cases = 0;
        for (String budget :
                new String[] {"0.00000001", "0.00000003", "1", "99999.99999999", "100000"}) {
            for (String fraction :
                    new String[] {
                        "0.000000000000000001",
                        "0.01",
                        "0.333333333333333333",
                        "0.5",
                        "0.999999999999999999"
                    }) {
                for (String price :
                        new String[] {
                            "0.000000000000000001",
                            "0.000000015",
                            "0.123456789123456789",
                            "3",
                            "600",
                            "99999999999999999999"
                        }) {
                    try {
                        var plan = plan(budget, fraction, price);
                        equal(budget, plan.cashBudget().add(plan.assetInitialQuoteValue()));
                        assertTrue(plan.cashBudget().signum() >= 0);
                        assertEquals(8, plan.assetQuantity().scale());
                        assertTrue(
                                plan.assetQuantity()
                                                .multiply(plan.conversionPrice())
                                                .compareTo(
                                                        plan.totalBudget()
                                                                .multiply(
                                                                        plan.assetBudgetFraction()))
                                        <= 0);
                        cases++;
                    } catch (IllegalArgumentException precision) {
                        assertEquals("SIM_ASSET_FUNDING_BELOW_PRECISION", precision.getMessage());
                    }
                }
            }
        }
        assertTrue(cases > 70);
    }

    @Test
    void rejectsInvalidBudgetFractionPriceAndUnusableAllocation() {
        for (String budget : new String[] {"0", "-1", "100000.00000001", "1.000000001"}) {
            assertThrows(IllegalArgumentException.class, () -> plan(budget, "0.1", "3"));
        }
        for (String fraction : new String[] {"0", "-0.1", "1", "0.0000000000000000001"}) {
            assertThrows(IllegalArgumentException.class, () -> plan("100", fraction, "3"));
        }
        for (String price :
                new String[] {"0", "-1", "0.0000000000000000001", "100000000000000000000"}) {
            assertThrows(IllegalArgumentException.class, () -> plan("100", "0.1", price));
        }
        assertThrows(IllegalArgumentException.class, () -> plan("0.00000001", "0.1", "600"));
    }

    @Test
    void rejectsInvertedUnitsSameCurrencyAndFutureVisibility() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SimAssetFundingPlan(
                                d("100"),
                                "USDT",
                                "USDT",
                                d("0.1"),
                                d("3"),
                                "USDT_PER_USDT",
                                AT,
                                AT,
                                AT,
                                sha(SOURCE),
                                SOURCE));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SimAssetFundingPlan(
                                d("100"),
                                "USDT",
                                "BNB",
                                d("0.1"),
                                d("3"),
                                "BNB_PER_USDT",
                                AT,
                                AT,
                                AT,
                                sha(SOURCE),
                                SOURCE));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SimAssetFundingPlan(
                                d("100"),
                                "USDT",
                                "BNB",
                                d("0.1"),
                                d("3"),
                                "USDT_PER_BNB",
                                AT,
                                AT.plusSeconds(1),
                                AT,
                                sha(SOURCE),
                                SOURCE));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SimAssetFundingPlan(
                                d("100"),
                                "USDT",
                                "BNB",
                                d("0.1"),
                                d("3"),
                                "USDT_PER_BNB",
                                AT.plusNanos(1),
                                AT.plusNanos(1),
                                AT.plusNanos(1),
                                sha(SOURCE),
                                SOURCE));
    }

    @Test
    void sourceIdentityUsesExactUtf8TextAndRequiresLowercaseHash() {
        var plan = plan("100", "0.1", "3");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SimAssetFundingPlan(
                                plan.totalBudget(),
                                "USDT",
                                "BNB",
                                plan.assetBudgetFraction(),
                                plan.conversionPrice(),
                                "USDT_PER_BNB",
                                AT,
                                AT,
                                AT,
                                plan.sourceSha256(),
                                SOURCE + " "));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SimAssetFundingPlan(
                                plan.totalBudget(),
                                "USDT",
                                "BNB",
                                plan.assetBudgetFraction(),
                                plan.conversionPrice(),
                                "USDT_PER_BNB",
                                AT,
                                AT,
                                AT,
                                plan.sourceSha256().toUpperCase(),
                                SOURCE));
        assertEquals(plan, plan("100.00000000", "0.100000000000000000", "3.000000000000000000"));
    }

    private static SimAssetFundingPlan plan(String budget, String fraction, String price) {
        return new SimAssetFundingPlan(
                d(budget),
                "USDT",
                "BNB",
                d(fraction),
                d(price),
                "USDT_PER_BNB",
                AT,
                AT,
                AT,
                sha(SOURCE),
                SOURCE);
    }

    private static String sha(String source) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual));
    }
}
