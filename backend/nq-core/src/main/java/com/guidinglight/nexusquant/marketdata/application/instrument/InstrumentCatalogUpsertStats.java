package com.guidinglight.nexusquant.marketdata.application.instrument;

/**
 * InstrumentCatalogUpsertStats 描述 instrument catalog 本次同步写入统计。
 * <p>
 * Why:
 * PRE-2 需要把“读到了多少条 instrument、真正写入多少条、更新了多少条”作为稳定审计口径，
 * 避免 symbol sync 仍停留在只看日志、不看事实结果的状态。
 *
 * @param insertedCount 新增条数
 * @param updatedCount  更新条数
 */
public record InstrumentCatalogUpsertStats(
        int insertedCount,
        int updatedCount
) {
}
