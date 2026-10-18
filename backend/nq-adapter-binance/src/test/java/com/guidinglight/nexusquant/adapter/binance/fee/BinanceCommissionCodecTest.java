package com.guidinglight.nexusquant.adapter.binance.fee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

/** 官方字段结构的明确合成fixture；校验金额比例和完整来源，不能当作真实账户费率或实际成交费用。 */
class BinanceCommissionCodecTest {
    private final BinanceCommissionCodec codec = new BinanceCommissionCodec();
    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    private static final String DATE = "Wed, 14 Oct 2026 00:00:00 GMT";

    static String payload() {
        return """
                {"symbol":"BTCUSDT",
                 "standardCommission":{"maker":"0.00000010","taker":"0.00000020","buyer":"0.00000030","seller":"0.00000040"},
                 "taxCommission":{"maker":"0.00000112","taker":"0.00000114","buyer":"0.00000118","seller":"0.00000116"},
                 "specialCommission":{"maker":"0.01000000","taker":"0.02000000","buyer":"0.03000000","seller":"0.04000000"},
                 "discount":{"enabledForAccount":true,"enabledForSymbol":true,"discountAsset":"BNB","discount":"0.25000000"}}
                """;
    }

    @Test
    void keepsFullRateComponentsAndSeparateBuySellRatios() {
        var result = codec.decode(payload(), "BTCUSDT", NOW, DATE);
        assertEquals(
                new BigDecimal("0.05000282"),
                result.costRate(
                        BinanceCommissionSnapshot.Side.BUY,
                        BinanceCommissionSnapshot.LiquidityRole.TAKER,
                        BinanceCommissionSnapshot.PaymentCondition.RECEIVED_ASSET));
        assertEquals(
                new BigDecimal("0.06000290"),
                result.costRate(
                        BinanceCommissionSnapshot.Side.SELL,
                        BinanceCommissionSnapshot.LiquidityRole.TAKER,
                        BinanceCommissionSnapshot.PaymentCondition.RECEIVED_ASSET));
        assertEquals(NOW, result.providerResponseAt());
        assertEquals("HTTP_RESPONSE_DATE", result.providerTimeBasis());
        assertEquals(64, result.responseSha256().length());
        assertEquals(new BigDecimal("0.00000010"), result.standard().maker());
    }

    @Test
    void discountOnlyAffectsStandardAndRequiresAnExplicitCondition() {
        var result = codec.decode(payload(), "BTCUSDT", NOW, DATE);
        assertEquals(
                0,
                new BigDecimal("0.06000245")
                        .compareTo(
                                result.costRate(
                                        BinanceCommissionSnapshot.Side.SELL,
                                        BinanceCommissionSnapshot.LiquidityRole.TAKER,
                                        BinanceCommissionSnapshot.PaymentCondition
                                                .DISCOUNT_ASSET_SUFFICIENT)));
        assertEquals(
                0,
                new BigDecimal("0.050002445")
                        .compareTo(
                                result.costRate(
                                        BinanceCommissionSnapshot.Side.BUY,
                                        BinanceCommissionSnapshot.LiquidityRole.TAKER,
                                        BinanceCommissionSnapshot.PaymentCondition
                                                .DISCOUNT_ASSET_SUFFICIENT)));
        // 读取开关不代表实际余额足额，未选择优惠条件时仍计算收到资产支付的完整比例。
        assertEquals(
                0,
                new BigDecimal("0.06000290")
                        .compareTo(
                                result.costRate(
                                        BinanceCommissionSnapshot.Side.SELL,
                                        BinanceCommissionSnapshot.LiquidityRole.TAKER,
                                        BinanceCommissionSnapshot.PaymentCondition
                                                .RECEIVED_ASSET)));
    }

    @Test
    void missingSupplierDateRemainsMissing() {
        var result = codec.decode(payload(), "BTCUSDT", NOW, null);
        assertNull(result.providerResponseAt());
        assertEquals("NOT_PROVIDED", result.providerTimeBasis());
        assertEquals(NOW, result.observedAt());
    }

    @Test
    void disabledDiscountCannotBeSelected() {
        var result =
                codec.decode(
                        payload()
                                .replace("\"enabledForSymbol\":true", "\"enabledForSymbol\":false"),
                        "BTCUSDT",
                        NOW,
                        DATE);
        assertEquals(
                BinanceFeeReadException.Reason.DISCOUNT_NOT_AVAILABLE,
                assertThrows(
                                BinanceFeeReadException.class,
                                () ->
                                        result.costRate(
                                                BinanceCommissionSnapshot.Side.BUY,
                                                BinanceCommissionSnapshot.LiquidityRole.TAKER,
                                                BinanceCommissionSnapshot.PaymentCondition
                                                        .DISCOUNT_ASSET_SUFFICIENT))
                        .reason());
    }

    @Test
    void retainsZeroAndSignedNativeRatesWithoutNegationOrAbsoluteValue() throws Exception {
        ObjectNode root = (ObjectNode) new ObjectMapper().readTree(payload());
        for (String name :
                new String[] {"standardCommission", "taxCommission", "specialCommission"}) {
            ObjectNode rates = (ObjectNode) root.path(name);
            for (String field : new String[] {"maker", "taker", "buyer", "seller"})
                rates.put(field, "0");
        }
        ((ObjectNode) root.path("standardCommission")).put("maker", "-0.000123456789");
        var result = codec.decode(root.toString(), "BTCUSDT", NOW, DATE);
        assertEquals(
                0,
                new BigDecimal("-0.000123456789")
                        .compareTo(
                                result.costRate(
                                        BinanceCommissionSnapshot.Side.BUY,
                                        BinanceCommissionSnapshot.LiquidityRole.MAKER,
                                        BinanceCommissionSnapshot.PaymentCondition
                                                .RECEIVED_ASSET)));
        assertEquals(
                0,
                result.costRate(
                                BinanceCommissionSnapshot.Side.SELL,
                                BinanceCommissionSnapshot.LiquidityRole.TAKER,
                                BinanceCommissionSnapshot.PaymentCondition.RECEIVED_ASSET)
                        .signum());
    }

    @Test
    void rejectsIncompleteUnknownAndDuplicateFields() {
        for (String raw :
                new String[] {
                    payload().replace("\"buyer\":\"0.00000030\",", ""),
                    payload()
                            .replace(
                                    "\"symbol\":\"BTCUSDT\",",
                                    "\"symbol\":\"BTCUSDT\",\"unknownCommission\":{},"),
                    payload()
                            .replace(
                                    "\"symbol\":\"BTCUSDT\",",
                                    "\"symbol\":\"BTCUSDT\",\"symbol\":\"ETHUSDT\","),
                    payload() + "{}"
                }) {
            var failure =
                    assertThrows(
                            BinanceFeeReadException.class,
                            () -> codec.decode(raw, "BTCUSDT", NOW, DATE));
            assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains("ETHUSDT"));
        }
    }

    @Test
    void rejectsNumericScientificOverprecisionAndOutOfRangeRates() {
        for (String replacement :
                new String[] {"0.00000010", "\"1e-4\"", "\"0.0001234567891\"", "\"1.1\""})
            assertThrows(
                    BinanceFeeReadException.class,
                    () ->
                            codec.decode(
                                    payload().replace("\"0.00000010\"", replacement),
                                    "BTCUSDT",
                                    NOW,
                                    DATE));
    }

    @Test
    void rejectsWrongSymbolInvalidDateAndOversizedBody() {
        assertEquals(
                BinanceFeeReadException.Reason.SYMBOL_MISMATCH,
                assertThrows(
                                BinanceFeeReadException.class,
                                () -> codec.decode(payload(), "ETHUSDT", NOW, DATE))
                        .reason());
        assertEquals(
                BinanceFeeReadException.Reason.INVALID_PROVIDER_TIME,
                assertThrows(
                                BinanceFeeReadException.class,
                                () -> codec.decode(payload(), "BTCUSDT", NOW, "not-a-date"))
                        .reason());
        assertEquals(
                BinanceFeeReadException.Reason.RESPONSE_TOO_LARGE,
                assertThrows(
                                BinanceFeeReadException.class,
                                () ->
                                        codec.decode(
                                                payload() + " ".repeat(65536),
                                                "BTCUSDT",
                                                NOW,
                                                DATE))
                        .reason());
    }
}
