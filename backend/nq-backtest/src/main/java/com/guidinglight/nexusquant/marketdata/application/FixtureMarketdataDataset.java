package com.guidinglight.nexusquant.marketdata.application;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;

/**
 * FixtureMarketdataDataset 描述一条允许被正式 ingest 的注册 fixture 数据集。
 * <p>
 * Why:
 * RC1-5-A 首版明确只允许一个受控 fixture 数据集进入 ingest 链路，避免“最小闭环”膨胀成
 * 半个通用 CSV 平台。
 */
public record FixtureMarketdataDataset(
        String fixtureId,
        String exchangeCode,
        String symbol,
        BarInterval interval,
        String resourcePath
) {
}
