package com.guidinglight.nexusquant.marketdata.application;

import java.time.Instant;

/**
 * MarketdataFixtureIngestionResult 描述一次 fixture ingest 的最小统计摘要。
 * <p>
 * Why:
 * RC1-5 要求 ingest 同时返回 rowsRead / rowsInserted / rowsUpdated 和 requestedRange，
 * 便于本地脚本、测试和人工核验都围绕同一份统计口径工作。
 */
public record MarketdataFixtureIngestionResult(
        String fixtureId,
        String exchangeCode,
        String symbol,
        String interval,
        Instant requestedStartTime,
        Instant requestedEndTime,
        int rowsRead,
        int rowsInserted,
        int rowsUpdated,
        Instant startedAt,
        Instant finishedAt
) {
}
