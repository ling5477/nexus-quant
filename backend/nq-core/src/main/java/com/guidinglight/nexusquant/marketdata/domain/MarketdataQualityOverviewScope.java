package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataQualityOverviewScope 回显 overview 的只读筛选边界。
 * <p>
 * Why:
 * 调用方需要知道本次聚合是否是全局、多 symbol，还是被 dataset / source / time range 限定；
 * 回显 scope 也能避免把 broad diagnostic 误解成某个真实 provider 的授权状态。
 */
public record MarketdataQualityOverviewScope(
        String exchangeCode,
        String marketType,
        String symbol,
        String interval,
        String sourceType,
        String dataOrigin,
        UUID datasetId,
        Instant from,
        Instant to
) {
}
