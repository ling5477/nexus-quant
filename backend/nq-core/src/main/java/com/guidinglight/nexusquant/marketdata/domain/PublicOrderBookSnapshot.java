package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.List;

/** 原始报价和累计数量以十进制文本返回；时间明确区分供应方快照与本机接收。 */
public record PublicOrderBookSnapshot(
        String exchangeCode,
        String marketType,
        String symbol,
        String status,
        Instant sourceTime,
        Instant receivedAt,
        Instant checkedAt,
        String source,
        int maxLevels,
        String errorCode,
        List<Level> bids,
        List<Level> asks) {
    public PublicOrderBookSnapshot {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }

    public record Level(String price, String quantity, String cumulativeQuantity, int orderCount) {}
}
