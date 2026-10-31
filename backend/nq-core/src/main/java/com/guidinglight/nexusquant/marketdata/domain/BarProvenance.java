package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;

/** 页面来源明细直接取已有bar事实，不改变研究冻结内容或可见性身份。 */
public record BarProvenance(
        Instant openTime,
        String source,
        Instant availableAt,
        Long barId,
        String barIdentitySha256,
        PriceEventTimes priceEventTimes) {
    public BarProvenance(Instant openTime, String source, Instant availableAt) {
        this(
                openTime,
                source,
                availableAt,
                null,
                null,
                PriceEventTimes.unavailable("NOT_PROVIDED"));
    }
}
