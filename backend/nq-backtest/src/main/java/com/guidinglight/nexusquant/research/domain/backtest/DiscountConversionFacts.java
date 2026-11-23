package com.guidinglight.nexusquant.research.domain.backtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;

/** 保存逐事件原文使SHA可重算；字段复核不能替代正式来源服务的所有者和历史版本准入。 */
final class DiscountConversionFacts {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DiscountConversionFacts() {}

    static boolean matches(
            String currency,
            String quote,
            BigDecimal price,
            Instant observed,
            Instant visible,
            String sha,
            String source) {
        if (source == null
                || source.getBytes(StandardCharsets.UTF_8).length > 32_768
                || !DiscountAssetPriceSnapshot.sha256(source).equals(sha)) return false;
        try {
            JsonNode node = MAPPER.readTree(source);
            return node != null
                    && node.isObject()
                    && (currency + "-" + quote).equals(node.path("symbol").asText())
                    && "SPOT".equals(node.path("marketType").asText())
                    && new BigDecimal(node.path("closePrice").asText()).compareTo(price) == 0
                    && observed.equals(Instant.parse(node.path("observedAt").asText()))
                    && visible.equals(Instant.parse(node.path("visibleAt").asText()));
        } catch (IOException | IllegalArgumentException | DateTimeException failure) {
            return false;
        }
    }
}
