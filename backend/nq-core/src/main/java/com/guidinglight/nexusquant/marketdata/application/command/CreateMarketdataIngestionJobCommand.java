package com.guidinglight.nexusquant.marketdata.application.command;

import java.time.Instant;

/**
 * CreateMarketdataIngestionJobCommand 是 GateH-2 创建历史行情接入任务的应用层命令。
 * <p>
 * Why:
 * Controller 只负责 HTTP 字段映射；合法交易所、市场类型、交易对、周期和时间范围的校验集中在 application service。
 */
public record CreateMarketdataIngestionJobCommand(
        String exchangeCode,
        String marketType,
        String symbol,
        String interval,
        Instant startTime,
        Instant endTime,
        String createdBy
) {
}
