package com.guidinglight.nexusquant.adapter.api.model;

import java.time.Instant;

/**
 * HistoricalKlineRequest 是交易所历史 K 线 adapter 的标准请求。
 * <p>
 * Why:
 * GateH-2 的 OKX / Binance adapter 需要共享交易所、市场类型、系统 symbol、周期和时间范围字段，
 * 但不能依赖 core domain 类型，避免 adapter 模块反向定义平台业务语义。
 */
public record HistoricalKlineRequest(
        String exchangeCode,
        String marketType,
        String symbol,
        String interval,
        Instant startTime,
        Instant endTime,
        int limit
) {
}
