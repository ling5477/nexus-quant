package com.guidinglight.nexusquant.account.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeObservation;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeSourceIdentity;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 条件算术与规范身份证明；合成分量不冒充实际账户费率或已发生的优惠费用。 */
class SpotCommissionTermsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00.123456Z");

    private SpotCommissionTerms terms() {
        return new SpotCommissionTerms(
                "BTCUSDT",
                rates("0.00000040", "0.00000050", "0.00000010", "0.00000020"),
                rates("0.00000128", "0.00000130", "0.00000132", "0.00000140"),
                rates("0.01000000", "0.02000000", "0.03000000", "0.04000000"),
                new SpotCommissionTerms.Discount(true, true, "BNB", new BigDecimal("0.25")),
                "a".repeat(64));
    }

    private static SpotCommissionTerms.Rates rates(
            String maker, String taker, String buyer, String seller) {
        return new SpotCommissionTerms.Rates(
                new BigDecimal(maker),
                new BigDecimal(taker),
                new BigDecimal(buyer),
                new BigDecimal(seller));
    }

    @Test
    void includesDirectionAndDiscountsOnlyStandardCommission() {
        var terms = terms();
        assertEquals(
                0,
                new BigDecimal("0.05000322")
                        .compareTo(
                                terms.costRate(
                                        SpotCommissionTerms.Side.BUY,
                                        SpotCommissionTerms.LiquidityRole.TAKER,
                                        SpotCommissionTerms.PaymentCondition.RECEIVED_ASSET)));
        assertEquals(
                0,
                new BigDecimal("0.06000340")
                        .compareTo(
                                terms.costRate(
                                        SpotCommissionTerms.Side.SELL,
                                        SpotCommissionTerms.LiquidityRole.TAKER,
                                        SpotCommissionTerms.PaymentCondition.RECEIVED_ASSET)));
        assertEquals(
                0,
                new BigDecimal("0.05000277")
                        .compareTo(
                                terms.costRate(
                                        SpotCommissionTerms.Side.BUY,
                                        SpotCommissionTerms.LiquidityRole.TAKER,
                                        SpotCommissionTerms.PaymentCondition
                                                .DISCOUNT_ASSET_SUFFICIENT)));
    }

    @Test
    void canonicalSnapshotRoundTripsAllTwelveRatesAndMissingMetadataIsNotZero() {
        var quote = AccountFeeQuote.binance(terms(), null, NOW);
        assertNull(quote.makerProviderRate());
        assertNull(quote.takerCostRate());
        assertNull(quote.tier());
        assertNull(quote.providerAt());
        assertEquals("NOT_PROVIDED", quote.providerTimeBasis());
        assertEquals(terms(), SpotCommissionTerms.fromSnapshot(terms().snapshot(mapper)));
        var changed = terms().snapshot(mapper);
        changed.withObject("tax").remove("seller");
        assertThrows(
                IllegalArgumentException.class, () -> SpotCommissionTerms.fromSnapshot(changed));
    }

    @Test
    void signedZeroNegativeAndTinyComponentsRemainExact() {
        var nativeRates = rates("-0.000000000001", "0", "0", "0");
        var terms =
                new SpotCommissionTerms(
                        "BTCUSDT",
                        nativeRates,
                        rates("0", "0", "0", "0"),
                        rates("0", "0", "0", "0"),
                        new SpotCommissionTerms.Discount(false, false, "BNB", BigDecimal.ONE),
                        "b".repeat(64));
        assertEquals(
                new BigDecimal("-0.000000000001"),
                terms.costRate(
                        SpotCommissionTerms.Side.BUY,
                        SpotCommissionTerms.LiquidityRole.MAKER,
                        SpotCommissionTerms.PaymentCondition.RECEIVED_ASSET));
        assertEquals(
                0,
                terms.costRate(
                                SpotCommissionTerms.Side.SELL,
                                SpotCommissionTerms.LiquidityRole.TAKER,
                                SpotCommissionTerms.PaymentCondition.RECEIVED_ASSET)
                        .signum());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        terms.costRate(
                                SpotCommissionTerms.Side.BUY,
                                SpotCommissionTerms.LiquidityRole.TAKER,
                                SpotCommissionTerms.PaymentCondition.DISCOUNT_ASSET_SUFFICIENT));
    }

    @Test
    void legacyDigestEncodingStaysVersionOneWhileNativeSourceBindsCompleteTerms() {
        var legacy =
                new AccountFeeQuote(
                        new BigDecimal("0.0002"), new BigDecimal("-0.001"), "Lv1", NOW, NOW);
        var row = row("OKX", legacy);
        assertEquals(
                AccountFeeSourceIdentity.sha256(
                        "NQ/ACCOUNT_FEE_OBSERVATION/1|1|2|3|OKX|LIVE|BTC-USDT|OKX_PRIVATE_SPOT_FEE_V5|OKX_NEGATIVE_COST_V1|0.0002|-0.001|Lv1|"
                                + NOW
                                + "|"
                                + NOW),
                AccountFeeSourceIdentity.digest(row));
        var nativeRow = row("BINANCE", AccountFeeQuote.binance(terms(), null, NOW));
        assertNotEquals(
                AccountFeeSourceIdentity.digest(nativeRow),
                AccountFeeSourceIdentity.digest(
                        row("BINANCE", AccountFeeQuote.binance(terms(), NOW, NOW))));
    }

    private static AccountFeeObservation row(String exchange, AccountFeeQuote quote) {
        return new AccountFeeObservation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                2,
                3,
                exchange,
                "LIVE",
                "BTC-USDT",
                "a".repeat(64),
                quote,
                NOW.plusSeconds(300),
                "");
    }
}
