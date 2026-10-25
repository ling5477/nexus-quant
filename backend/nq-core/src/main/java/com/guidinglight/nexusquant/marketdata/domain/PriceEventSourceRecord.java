package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.UUID;

/** 不可变来源记录关联具体K线版本；不修改原始行情，也不进入策略、回测或账务的权威输入。 */
public record PriceEventSourceRecord(
        UUID sourceId,
        long barId,
        String barIdentitySha256,
        String sourceName,
        String documentSha256,
        String documentJson,
        String barSnapshotJson,
        Instant openPriceAt,
        Instant highPriceAt,
        Instant lowPriceAt,
        Instant closePriceAt,
        long createdByUserId,
        Instant createdAt) {
    public PriceEventTimes times() {
        return new PriceEventTimes(
                "SOURCE_PROVIDED",
                sourceId,
                sourceName,
                documentSha256,
                openPriceAt,
                highPriceAt,
                lowPriceAt,
                closePriceAt,
                createdAt);
    }
}
