package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.UUID;

/** 四价时点只来自明确的来源记录；区间边界、收盘可见时间均不能代替实际发生时点。 */
public record PriceEventTimes(
        String status,
        UUID sourceId,
        String sourceName,
        String documentSha256,
        Instant openPriceAt,
        Instant highPriceAt,
        Instant lowPriceAt,
        Instant closePriceAt,
        Instant recordedAt) {
    public static PriceEventTimes unavailable(String status) {
        return new PriceEventTimes(status, null, null, null, null, null, null, null, null);
    }
}
