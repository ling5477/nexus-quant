package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataIngestionRun 表示一次接入任务的 run-once 执行结果。
 * <p>
 * Why:
 * 同一个任务可能多次重跑或断点续拉，运行记录必须保存请求范围、实际返回范围和写入统计，便于定位外部交易所失败与幂等更新。
 */
public record MarketdataIngestionRun(
        UUID runId,
        UUID jobId,
        MarketdataIngestionStatus status,
        Instant startedAt,
        Instant finishedAt,
        Instant requestedStartTime,
        Instant requestedEndTime,
        Instant actualStartTime,
        Instant actualEndTime,
        int fetchedBars,
        int insertedBars,
        int updatedBars,
        int skippedBars,
        String errorMessage,
        String rawSummaryJson,
        Instant createdAt
) {
}
