package com.guidinglight.nexusquant.adapter.binance.fee;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** 严格解析现货账户commission响应；缺字段、重复字段或未来费用类型不能被忽略并伪装为完整配置。 */
public final class BinanceCommissionCodec {
    public static final int MAX_BYTES = 65536;
    private final ObjectMapper mapper =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(8)
                                                    .maxStringLength(128)
                                                    .maxNumberLength(48)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** 原文仅用于即时解析和摘要，不对外返回；原生symbol与已选标的必须精确匹配。 */
    public BinanceCommissionSnapshot decode(
            String raw, String expectedSymbol, Instant observedAt, String httpDate) {
        if (raw == null
                || observedAt == null
                || !BinanceCommissionSnapshot.validNativeSymbol(expectedSymbol))
            throw error(BinanceFeeReadException.Reason.INVALID_SOURCE);
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw error(BinanceFeeReadException.Reason.RESPONSE_TOO_LARGE);
        try {
            JsonNode root = mapper.readTree(bytes);
            keys(
                    root,
                    Set.of(
                            "symbol",
                            "standardCommission",
                            "taxCommission",
                            "specialCommission",
                            "discount"));
            if (!root.path("symbol").isTextual()
                    || !expectedSymbol.equals(root.path("symbol").textValue()))
                throw error(BinanceFeeReadException.Reason.SYMBOL_MISMATCH);
            JsonNode discount = root.path("discount");
            keys(
                    discount,
                    Set.of("enabledForAccount", "enabledForSymbol", "discountAsset", "discount"));
            if (!discount.path("enabledForAccount").isBoolean()
                    || !discount.path("enabledForSymbol").isBoolean()
                    || !discount.path("discountAsset").isTextual())
                throw error(BinanceFeeReadException.Reason.INVALID_DISCOUNT);
            Instant providerAt = date(httpDate);
            return new BinanceCommissionSnapshot(
                    expectedSymbol,
                    rates(root.path("standardCommission")),
                    rates(root.path("taxCommission")),
                    rates(root.path("specialCommission")),
                    new BinanceCommissionDiscount(
                            discount.path("enabledForAccount").booleanValue(),
                            discount.path("enabledForSymbol").booleanValue(),
                            discount.path("discountAsset").textValue(),
                            decimal(discount.path("discount"))),
                    providerAt,
                    observedAt,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (BinanceFeeReadException failure) {
            throw failure;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        } catch (Exception failure) {
            throw error(BinanceFeeReadException.Reason.INVALID_JSON);
        }
    }

    private static BinanceCommissionRates rates(JsonNode node) {
        keys(node, Set.of("maker", "taker", "buyer", "seller"));
        return new BinanceCommissionRates(
                decimal(node.path("maker")),
                decimal(node.path("taker")),
                decimal(node.path("buyer")),
                decimal(node.path("seller")));
    }

    /** API的DECIMAL为字符串；拒绝浮点JSON、科学计数和超精度值，不通过asDouble或舍入伪造精确原值。 */
    private static BigDecimal decimal(JsonNode node) {
        if (!node.isTextual() || !node.textValue().matches("-?[0-9]{1,3}(\\.[0-9]{1,12})?"))
            throw error(BinanceFeeReadException.Reason.INVALID_RATE);
        return new BigDecimal(node.textValue());
    }

    private static void keys(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject())
            throw error(BinanceFeeReadException.Reason.UNSUPPORTED_SCHEMA);
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected))
            throw error(BinanceFeeReadException.Reason.UNSUPPORTED_SCHEMA);
    }

    private static Instant date(String text) {
        if (text == null || text.isBlank()) return null;
        if (text.length() > 64) throw error(BinanceFeeReadException.Reason.INVALID_PROVIDER_TIME);
        try {
            return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException failure) {
            throw error(BinanceFeeReadException.Reason.INVALID_PROVIDER_TIME);
        }
    }

    private static BinanceFeeReadException error(BinanceFeeReadException.Reason reason) {
        return new BinanceFeeReadException(reason);
    }
}
