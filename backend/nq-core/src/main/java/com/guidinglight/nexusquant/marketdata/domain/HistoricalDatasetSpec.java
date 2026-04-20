package com.guidinglight.nexusquant.marketdata.domain;

/**
 * HistoricalDatasetSpec 描述 RC1 历史数据集输入边界。
 * <p>
 * Why:
 * RC1-5 需要把 marketdata query 与 research/backtest 执行统一到同一份 canonical dataset 语义，
 * 因此这里必须显式表达 provider、exchangeCode、symbol、interval 和资源位置，避免留下
 * “默认 BINANCE” 之类的隐式兜底。
 */
public record HistoricalDatasetSpec(
        String provider,
        String datasetId,
        String exchangeCode,
        String symbol,
        BarInterval interval,
        String resourcePath
) {
}
