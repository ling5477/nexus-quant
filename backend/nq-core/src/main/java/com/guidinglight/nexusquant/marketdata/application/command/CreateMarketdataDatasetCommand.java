package com.guidinglight.nexusquant.marketdata.application.command;

import java.time.Instant;

/**
 * CreateMarketdataDatasetCommand 是历史行情接入创建数据集的应用层输入。
 *
 * @param datasetName 数据集名称
 * @param exchangeCode 交易所代码，历史行情接入仅允许 OKX / BINANCE
 * @param marketType 市场类型，历史行情接入仅允许 SPOT
 * @param symbol 系统内部交易对，历史行情接入仅允许 BTC-USDT / ETH-USDT / SOL-USDT
 * @param interval K 线周期，历史行情接入固定支持 1m / 5m / 15m / 1h / 4h / 1d
 * @param startTime 覆盖范围起始时间
 * @param endTime 覆盖范围结束时间
 * @param createdBy 创建主体，用于审计
 */
public record CreateMarketdataDatasetCommand(
        String datasetName,
        String exchangeCode,
        String marketType,
        String symbol,
        String interval,
        Instant startTime,
        Instant endTime,
        String createdBy
) {
}
