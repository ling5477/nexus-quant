package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.account.domain.fee.FrozenDirectionalFeeModel;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Discount;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Rates;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

/** 模型消费只证明数学与预留，正式来源及canonical账务另由真实PG链证明。 */
class StrategySimFeeTermsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Instant AT = Instant.parse("2026-09-25T00:00:00Z");

    @Test
    void scalarQuoteChargeAndRebateKeepCashReserveContract() {
        for (String rate : new String[] {"0.001", "0", "-0.001"}) {
            var terms = StrategySimFeeTerms.from(d(rate), null, mapper);
            var fee = terms.charge("BTC-USDT", "BUY", d("1"), d("100"), AT);
            assertEquals("USDT", fee.currency());
            equal(d(rate).multiply(d("100")), fee.amount());
            equal(
                    d("100").add(d(rate).max(BigDecimal.ZERO).multiply(d("100"))),
                    terms.quoteReserve(d("1"), d("100")));
            equal(d("1"), terms.pendingBuyExposure("BTC-USDT", d("1"), d("100"), AT));
        }
    }

    @Test
    void nativeBuyFeesUseNetBaseWithoutChargingQuoteValuationTwice() {
        for (String rate : new String[] {"0.001", "0", "-0.001"}) {
            var terms = StrategySimFeeTerms.from(null, nativeSpec(rate), mapper);
            var buy = terms.charge("BTC-USDT", "BUY", d("1"), d("100"), AT);
            assertEquals("BTC", buy.currency());
            equal(d(rate), buy.amount());
            equal(d(rate).multiply(d("100")), buy.quoteValue());
            equal(d("100"), terms.quoteReserve(d("1"), d("100")));
            equal(
                    d("1").subtract(d(rate)),
                    terms.pendingBuyExposure("BTC-USDT", d("1"), d("100"), AT));
        }
    }

    @Test
    void nativeSellFeesKeepTheirQuoteCurrencyAndSign() {
        for (String rate : new String[] {"0.001", "0", "-0.001"}) {
            var terms = StrategySimFeeTerms.from(null, nativeSpec(rate), mapper);
            var sell = terms.charge("BTC-USDT", "SELL", d("1"), d("100"), AT);
            assertEquals("USDT", sell.currency());
            equal(d(rate).multiply(d("100")), sell.amount());
        }
    }

    @Test
    void projectedPendingRebateDoesNotCreateSellablePosition() {
        var terms = StrategySimFeeTerms.from(null, nativeSpec("-0.001"), mapper);
        BigDecimal exposure = terms.pendingBuyExposure("BTC-USDT", d("1"), d("100"), AT);
        var state =
                new SpotTargetSizer.State(
                        d("100"), BigDecimal.ZERO, exposure, BigDecimal.ZERO, d("100"), d("100"));
        var result =
                SpotTargetSizer.size(
                        BigDecimal.ZERO,
                        state,
                        new SpotTargetSizer.Rules(
                                d("0.0001"),
                                d("0.01"),
                                d("0.0001"),
                                d("5"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));
        assertEquals("POSITION_UNAVAILABLE", result.reason());
    }

    @Test
    void missingDualUnknownAndUnboundedModelsFailClosed() {
        assertThrows(
                IllegalStateException.class, () -> StrategySimFeeTerms.from(null, null, mapper));
        assertThrows(
                IllegalStateException.class,
                () -> StrategySimFeeTerms.from(d("0"), nativeSpec("0"), mapper));
        assertThrows(
                IllegalStateException.class, () -> StrategySimFeeTerms.from(null, "{}", mapper));
        assertThrows(
                IllegalStateException.class,
                () -> StrategySimFeeTerms.from(null, "x".repeat(65_537), mapper));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        StrategySimFeeTerms.from(null, nativeSpec("0.001"), mapper)
                                .charge("ETH-USDT", "BUY", d("1"), d("100"), AT));
    }

    @Test
    void tamperedSnapshotDigestCannotBecomeZeroScalarFallback() throws Exception {
        var spec = mapper.readTree(nativeSpec("0.001"));
        ((ObjectNode) spec).put("feeSourceSnapshotSha256", "b".repeat(64));
        assertThrows(
                IllegalArgumentException.class,
                () -> StrategySimFeeTerms.from(null, spec.toString(), mapper));
    }

    /** 仅提供消费器需要的模型fixture，不冒充持久观察；正式来源owner/账户/版本核验在上游。 */
    private String nativeSpec(String rate) {
        var zero = new Rates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var terms =
                new SpotCommissionTerms(
                        "BTCUSDT",
                        new Rates(BigDecimal.ZERO, BigDecimal.ZERO, d(rate), d(rate)),
                        zero,
                        zero,
                        new Discount(true, true, "BNB", d("0.25")),
                        "a".repeat(64));
        var frozen =
                mapper.createObjectNode()
                        .put("schemaVersion", FrozenDirectionalFeeModel.VERSION)
                        .put("symbol", "BTC-USDT")
                        .put("liquidityRole", "TAKER")
                        .put("paymentPolicy", "RECEIVED_ASSET")
                        .put("baseFeeScale", "8")
                        .put("quoteFeeScale", "8")
                        .put("discountAssetFeeScale", "8");
        frozen.set("commissionTerms", terms.snapshot(mapper));
        return mapper.createObjectNode()
                .put("feeAssumptionVersion", FrozenDirectionalFeeModel.VERSION)
                .put("costSource", "CURRENT_ACCOUNT_OBSERVATION")
                .put("feeExchangeCode", "BINANCE")
                .put("feePaymentPolicy", "RECEIVED_ASSET")
                .put("feeSymbol", "BTC-USDT")
                .put("feeSourceSnapshotJson", frozen.toString())
                .put("feeSourceSnapshotSha256", FrozenDirectionalFeeModel.digest(frozen))
                .toString();
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void equal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}
