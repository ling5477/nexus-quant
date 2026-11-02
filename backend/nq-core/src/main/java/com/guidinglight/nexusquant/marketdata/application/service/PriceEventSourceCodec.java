package com.guidinglight.nexusquant.marketdata.application.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.PriceEventSourceRecord;
import com.guidinglight.nexusquant.marketdata.domain.StoredBarIdentity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** 只接收明确给出的四价时点；匹配范围与价格后规范化，不从OHLCV推断，也不改变研究输入。 */
public final class PriceEventSourceCodec {
    private static final List<String> TIME_KEYS =
            List.of("openPriceAt", "highPriceAt", "lowPriceAt", "closePriceAt");
    private static final List<String> BAR_KEYS =
            List.of(
                    "exchangeCode",
                    "marketType",
                    "symbol",
                    "interval",
                    "openTime",
                    "closeTime",
                    "openPrice",
                    "highPrice",
                    "lowPrice",
                    "closePrice");
    private static final Set<String> KEYS =
            Set.of(
                    "exchangeCode",
                    "marketType",
                    "symbol",
                    "interval",
                    "openTime",
                    "closeTime",
                    "openPrice",
                    "highPrice",
                    "lowPrice",
                    "closePrice",
                    "openPriceAt",
                    "highPriceAt",
                    "lowPriceAt",
                    "closePriceAt");
    private final ObjectMapper mapper;

    public PriceEventSourceCodec(ObjectMapper mapper) {
        // 独立parser限制深度/长度并拒绝重复字段，不能修改全局JSON合同。
        this.mapper =
                mapper.copy()
                        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.mapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.mapper
                .getFactory()
                .setStreamReadConstraints(
                        StreamReadConstraints.builder()
                                .maxNestingDepth(4)
                                .maxStringLength(160)
                                .maxNumberLength(80)
                                .build());
    }

    public String barSnapshot(StoredBarIdentity stored) {
        ObjectNode node = barFields(stored.bar());
        node.put("barId", Long.toString(stored.barId()));
        node.put("source", stored.source());
        node.put("qualityStatus", stored.bar().qualityStatus());
        return node.toString();
    }

    public String barSha256(StoredBarIdentity stored) {
        return sha256(barSnapshot(stored));
    }

    /** 微秒以外精度须明确拒绝，避免PG写入舍入后改变事件顺序或请求身份。 */
    public Parsed parse(String input, StoredBarIdentity stored) {
        return normalize(
                input, barFields(stored.bar()), stored.bar().openTime(), stored.bar().closeTime());
    }

    /** 恢复比较使用已保存版本的边界与价格，当前行情更新不会把旧请求误认成另一个版本。 */
    public boolean matches(String input, PriceEventSourceRecord existing) {
        try {
            JsonNode saved = mapper.readTree(existing.documentJson());
            ObjectNode base = mapper.createObjectNode();
            BAR_KEYS.forEach(key -> base.put(key, saved.path(key).asText()));
            Instant from = time(base, "openTime"), to = time(base, "closeTime");
            return normalize(existing.documentJson(), base.deepCopy(), from, to)
                            .documentSha256()
                            .equals(existing.documentSha256())
                    && normalize(input, base, from, to)
                            .documentSha256()
                            .equals(existing.documentSha256());
        } catch (JsonProcessingException | IllegalArgumentException | DateTimeException ex) {
            return false;
        }
    }

    private Parsed normalize(
            String input, ObjectNode canonical, Instant frameStart, Instant frameEnd) {
        if (input == null || input.getBytes(StandardCharsets.UTF_8).length > 32768) throw invalid();
        try {
            JsonNode node = mapper.readTree(input);
            if (node == null || !node.isObject()) throw invalid();
            Set<String> keys = new HashSet<>();
            node.fieldNames().forEachRemaining(keys::add);
            if (!KEYS.equals(keys)) throw invalid();
            for (String key : List.of("exchangeCode", "marketType", "symbol", "interval")) {
                if (!node.path(key).isTextual()
                        || !canonical.path(key).asText().equals(node.path(key).asText()))
                    throw invalid();
            }
            if (!frameStart.equals(time(node, "openTime"))
                    || !frameEnd.equals(time(node, "closeTime"))) throw invalid();
            for (String key : List.of("openPrice", "highPrice", "lowPrice", "closePrice")) {
                if (decimal(node.path(key)).compareTo(new BigDecimal(canonical.path(key).asText()))
                        != 0) throw invalid();
            }
            var times = TIME_KEYS.stream().map(key -> time(node, key)).toList();
            Instant first = times.get(0), last = times.get(3);
            if (first.isBefore(frameStart)
                    || last.isAfter(frameEnd)
                    || first.isAfter(last)
                    || times.get(1).isBefore(first)
                    || times.get(1).isAfter(last)
                    || times.get(2).isBefore(first)
                    || times.get(2).isAfter(last)) throw invalid();
            for (int index = 0; index < 4; index++)
                canonical.put(TIME_KEYS.get(index), times.get(index).toString());
            return new Parsed(
                    canonical.toString(),
                    sha256(canonical.toString()),
                    first,
                    times.get(1),
                    times.get(2),
                    last);
        } catch (JsonProcessingException | IllegalArgumentException | DateTimeException ex) {
            throw invalid();
        }
    }

    private ObjectNode barFields(HistoricalBar bar) {
        ObjectNode node = mapper.createObjectNode();
        node.put("exchangeCode", bar.exchangeCode());
        node.put("marketType", bar.marketType());
        node.put("symbol", bar.symbol());
        node.put("interval", bar.interval().wireValue());
        node.put("openTime", bar.openTime().toString());
        node.put("closeTime", bar.closeTime().toString());
        node.put("openPrice", bar.openPrice().stripTrailingZeros().toPlainString());
        node.put("highPrice", bar.highPrice().stripTrailingZeros().toPlainString());
        node.put("lowPrice", bar.lowPrice().stripTrailingZeros().toPlainString());
        node.put("closePrice", bar.closePrice().stripTrailingZeros().toPlainString());
        return node;
    }

    private Instant time(JsonNode node, String key) {
        if (!node.path(key).isTextual()) throw invalid();
        Instant value = Instant.parse(node.path(key).asText());
        if (value.getNano() % 1000 != 0) throw invalid();
        return value;
    }

    private BigDecimal decimal(JsonNode value) {
        if (!value.isTextual() && !value.isNumber()) throw invalid();
        BigDecimal number = new BigDecimal(value.asText()).stripTrailingZeros();
        long integerDigits = (long) number.precision() - number.scale();
        if (number.signum() <= 0
                || number.precision() > 38
                || number.scale() > 8
                || integerDigits > 30
                || integerDigits < -8) throw invalid();
        return number;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("INVALID_PRICE_EVENT_SOURCE_DOCUMENT");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record Parsed(
            String documentJson,
            String documentSha256,
            Instant openPriceAt,
            Instant highPriceAt,
            Instant lowPriceAt,
            Instant closePriceAt) {}
}
