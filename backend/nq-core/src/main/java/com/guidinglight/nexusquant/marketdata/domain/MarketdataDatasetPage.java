package com.guidinglight.nexusquant.marketdata.domain;

import java.util.List;

/** 完整总数与页记录共用读取快照，拒绝把损坏或截断窗口表现为完整目录。 */
public record MarketdataDatasetPage(
        List<MarketdataDataset> items, long total, int limit, int offset) {
    public MarketdataDatasetPage {
        items = List.copyOf(items);
        if (total < 0
                || limit < 1
                || limit > 100
                || offset < 0
                || items.size() != Math.min(limit, Math.max(0L, total - offset))) {
            throw new IllegalArgumentException("invalid marketdata dataset page result");
        }
    }
}
