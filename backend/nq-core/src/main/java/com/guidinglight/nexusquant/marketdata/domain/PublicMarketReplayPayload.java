package com.guidinglight.nexusquant.marketdata.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.SpotBarIdentity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** 同一解析规则用于公开响应捕获与冻结数据读取，防止原始响应和消费内容各自自洽却互不关联。 */
public final class PublicMarketReplayPayload {
    private static final Duration HOUR = Duration.ofHours(1);

    private PublicMarketReplayPayload() { }

    public static Snapshot parse(byte[] response, Instant start, Instant end,
            Instant observedAt, ObjectMapper mapper) {
        try {
            long count = Duration.between(start, end).toHours();
            if (start.toEpochMilli() % HOUR.toMillis() != 0
                    || end.toEpochMilli() % HOUR.toMillis() != 0
                    || count < 1 || count > 500 || !end.equals(start.plus(HOUR.multipliedBy(count)))
                    || observedAt.isBefore(end)) {
                throw new IllegalStateException("PUBLIC_CAPTURE_WINDOW_INVALID");
            }
            JsonNode payload = mapper.readTree(response);
            JsonNode data = payload.path("data");
            if (!"0".equals(payload.path("code").asText()) || !data.isArray() || data.size() != count)
                throw new IllegalStateException("PUBLIC_CAPTURE_GAP_OR_RESPONSE_INVALID");
            List<HistoricalBar> bars = new ArrayList<>((int) count);
            ArrayNode normalized = mapper.createArrayNode();
            for (int index = 0; index < count; index++) {
                JsonNode row = data.get((int) count - index - 1);
                if (!row.isArray() || row.size() < 9 || !"1".equals(row.get(8).asText()))
                    throw new IllegalStateException("PUBLIC_CAPTURE_UNCLOSED_OR_SHAPE_INVALID");
                Instant open = Instant.ofEpochMilli(Long.parseLong(row.get(0).asText()));
                if (!open.equals(start.plus(HOUR.multipliedBy(index))))
                    throw new IllegalStateException("PUBLIC_CAPTURE_DUPLICATE_ORDER_OR_GAP");
                BigDecimal o = positive(row.get(1));
                BigDecimal h = positive(row.get(2));
                BigDecimal l = positive(row.get(3));
                BigDecimal c = positive(row.get(4));
                BigDecimal v = positive(row.get(5));
                BigDecimal q = positive(row.get(7));
                if (h.compareTo(o.max(c).max(l)) < 0 || l.compareTo(o.min(c).min(h)) > 0)
                    throw new IllegalStateException("PUBLIC_CAPTURE_INVALID_OHLC");
                ObjectNode item = normalized.addObject();
                item.put("openTime", open.toString());
                item.put("open", decimal(o)); item.put("high", decimal(h));
                item.put("low", decimal(l)); item.put("close", decimal(c));
                item.put("volume", decimal(v)); item.put("quoteVolume", decimal(q));
                // 收盘边界是版本化回放假设，不表示历史上真实观察到该 bar。
                bars.add(new HistoricalBar("OKX", "SPOT", "BTC-USDT", BarInterval.ONE_HOUR,
                        open, open.plus(HOUR).minusMillis(1), o, h, l, c, v, q,
                        null, "OK", row.toString(), open.plus(HOUR)));
            }
            return new Snapshot(sha256(response),
                    sha256(normalized.toString().getBytes(StandardCharsets.UTF_8)),
                    SpotBarIdentity.capture(bars, mapper));
        } catch (java.io.IOException | NumberFormatException ex) {
            throw new IllegalStateException("PUBLIC_CAPTURE_INVALID_RESPONSE", ex);
        }
    }

    private static BigDecimal positive(JsonNode value) {
        BigDecimal result = new BigDecimal(value.asText());
        if (result.signum() <= 0) throw new IllegalStateException("PUBLIC_CAPTURE_NON_POSITIVE_VALUE");
        return result;
    }

    private static String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public record Snapshot(String rawSha256, String normalizedSha256,
            SpotBarIdentity.Snapshot consumed) { }
}
