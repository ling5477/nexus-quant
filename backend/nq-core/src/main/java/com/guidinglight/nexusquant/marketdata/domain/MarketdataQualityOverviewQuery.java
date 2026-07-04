package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * MarketdataQualityOverviewQuery 定义 Data Quality Center overview 的只读筛选条件。
 * <p>
 * Why:
 * GateP Batch 2 需要跨 symbol / interval / source / dataset 聚合现有事实，但不能因为筛选为空而
 * 回退到真实 provider 或 credential。该 query 只表达本地 DB 读取边界。
 */
public record MarketdataQualityOverviewQuery(
        String exchangeCode,
        String marketType,
        String symbol,
        BarInterval interval,
        String sourceType,
        String dataOrigin,
        UUID datasetId,
        Instant from,
        Instant to
) {
    public MarketdataQualityOverviewQuery {
        exchangeCode = normalizeUpper(exchangeCode);
        marketType = marketType == null || marketType.isBlank()
                ? "SPOT"
                : marketType.trim().toUpperCase(Locale.ROOT);
        symbol = normalizeUpper(symbol);
        sourceType = normalizeUpper(sourceType);
        dataOrigin = normalizeUpper(dataOrigin);
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
    }

    private static String normalizeUpper(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
