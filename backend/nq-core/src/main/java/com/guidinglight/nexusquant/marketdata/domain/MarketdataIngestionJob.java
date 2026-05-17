package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataIngestionJob 表示一次历史 K 线接入任务的持久化事实。
 * <p>
 * Why:
 * GateH-2 要支持 run-once 和断点续拉，任务维度必须独立于运行记录保存，不能只把请求参数临时放在 controller。
 */
public record MarketdataIngestionJob(
        UUID jobId,
        String exchangeCode,
        String marketType,
        String symbol,
        BarInterval interval,
        Instant startTime,
        Instant endTime,
        MarketdataIngestionStatus status,
        String source,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        String requestJson
) {
}
