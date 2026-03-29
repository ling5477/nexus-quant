package com.guidinglight.nexusquant.marketdata.domain;

/**
 * HistoricalDatasetSpec 描述 GateF-2 的历史数据集输入边界。
 * <p>
 * Why:
 * 历史回测输入与实时订阅是两种不同语义，必须显式表达数据来源、符号、周期和资源位置，
 * 不能用实时 adapter 的字段集合硬凑。
 */
public record HistoricalDatasetSpec(
        String provider,
        String datasetId,
        String symbol,
        BarInterval interval,
        String resourcePath
) {
}

