package com.guidinglight.nexusquant.strategy.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** 精确记录策略实际消费的 bar 值；digest 与保存的 JSON 使用同一字节序列。 */
public final class SpotBarIdentity {
    private SpotBarIdentity() { }

    public static Snapshot capture(List<HistoricalBar> bars, ObjectMapper mapper) {
        Objects.requireNonNull(bars, "bars");
        Objects.requireNonNull(mapper, "mapper");
        if (bars.isEmpty() || bars.size() > 500) {
            throw new IllegalArgumentException("bar snapshot must contain 1 to 500 bars");
        }
        ArrayNode snapshot = mapper.createArrayNode();
        for (HistoricalBar bar : bars) {
            Objects.requireNonNull(bar, "bar");
            ObjectNode node = snapshot.addObject();
            node.put("exchangeCode", bar.exchangeCode());
            node.put("marketType", bar.marketType());
            node.put("symbol", bar.symbol());
            node.put("interval", bar.interval().wireValue());
            node.put("openTime", bar.openTime().toString());
            node.put("closeTime", bar.closeTime().toString());
            node.put("availableAt", bar.availableAt().toString());
            node.put("openPrice", decimal(bar.openPrice()));
            node.put("highPrice", decimal(bar.highPrice()));
            node.put("lowPrice", decimal(bar.lowPrice()));
            node.put("closePrice", decimal(bar.closePrice()));
            node.put("volume", decimal(bar.volume()));
            if (bar.quoteVolume() == null) node.putNull("quoteVolume");
            else node.put("quoteVolume", decimal(bar.quoteVolume()));
            if (bar.tradeCount() == null) node.putNull("tradeCount");
            else node.put("tradeCount", bar.tradeCount());
            node.put("qualityStatus", bar.qualityStatus());
            node.put("rawPayloadJson", bar.rawPayloadJson());
        }
        String canonicalJson = snapshot.toString();
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson.getBytes(StandardCharsets.UTF_8)));
            return new Snapshot(digest, canonicalJson);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String decimal(BigDecimal value) {
        return Objects.requireNonNull(value, "bar decimal").stripTrailingZeros().toPlainString();
    }

    public record Snapshot(String sha256, String canonicalJson) { }
}
