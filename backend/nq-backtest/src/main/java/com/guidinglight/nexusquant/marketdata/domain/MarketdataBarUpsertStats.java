package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataBarUpsertStats 记录一次 bars ingest 的插入/更新统计。
 * <p>
 * Why:
 * RC1-5 首版 ingest 需要把幂等结果直接暴露给调用方，便于验证“首次写入”和“重复重放”
 * 是否落在同一套唯一键语义上。
 */
public record MarketdataBarUpsertStats(
        int insertedCount,
        int updatedCount
) {
}
