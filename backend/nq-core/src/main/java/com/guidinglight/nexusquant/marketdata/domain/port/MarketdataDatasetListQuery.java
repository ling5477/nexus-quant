package com.guidinglight.nexusquant.marketdata.domain.port;

import java.util.Locale;
import java.util.UUID;

/** 名称按字面子串查找，其余维度精确过滤；质量筛选只描述目录，不授予回测执行资格。 */
public record MarketdataDatasetListQuery(
        UUID datasetId,
        String datasetName,
        String exchangeCode,
        String marketType,
        String symbol,
        String interval,
        String status,
        String qualityStatus,
        String source,
        Long maximumGapCount,
        int limit,
        int offset) {
    public MarketdataDatasetListQuery {
        datasetName = normalize(datasetName, 255, false);
        exchangeCode = normalize(exchangeCode, 64, true);
        marketType = normalize(marketType, 64, true);
        symbol = normalize(symbol, 64, true);
        interval = normalize(interval, 32, false);
        status = normalize(status, 64, true);
        qualityStatus = normalize(qualityStatus, 64, true);
        source = normalize(source, 128, false);
        if (limit < 1
                || limit > 100
                || offset < 0
                || (maximumGapCount != null && maximumGapCount < 0)) {
            throw new IllegalArgumentException(
                    "invalid marketdata dataset page window or gap bound");
        }
    }

    private static String normalize(String value, int maximum, boolean upper) {
        if (value == null) return null;
        // 先拒绝控制字符，避免把畸形筛选静默变成无过滤的目录查询。
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid marketdata dataset filter");
        }
        if (value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException("invalid marketdata dataset filter");
        }
        return upper ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }
}
