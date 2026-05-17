package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataDataset 表示 GateH-3 可绑定到回测配置的历史 K 线数据集。
 * <p>
 * Why:
 * GateH-2 的 `marketdata_bars` 是原始 K 线事实，回测配置需要绑定一个稳定、可审计、可复算质量的数据集视图，
 * 否则历史回测无法解释当时使用了哪个交易所、交易对、周期和时间范围。
 */
public record MarketdataDataset(
        UUID datasetId,
        String datasetName,
        String exchangeCode,
        String marketType,
        String symbol,
        BarInterval interval,
        Instant startTime,
        Instant endTime,
        MarketdataDatasetStatus status,
        MarketdataQualityStatus qualityStatus,
        long barCount,
        long gapCount,
        String source,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        String requestJson
) {
}
