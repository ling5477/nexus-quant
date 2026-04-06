package com.guidinglight.nexusquant.marketdata.application;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * FixtureMarketdataRegistry 持有 RC1-5 首版允许 ingest 的注册数据集。
 * <p>
 * Why:
 * 本批明确只允许 `BINANCE_BTCUSDT_1M_SAMPLE` 进入正式 ingest 链路，注册表把该约束收口成
 * 单一事实源，避免 controller 和 service 各自散落一份硬编码。
 */
@Component
public class FixtureMarketdataRegistry {

    public static final String BINANCE_BTCUSDT_1M_SAMPLE = "BINANCE_BTCUSDT_1M_SAMPLE";

    private final Map<String, FixtureMarketdataDataset> datasets = Map.of(
            BINANCE_BTCUSDT_1M_SAMPLE,
            new FixtureMarketdataDataset(
                    BINANCE_BTCUSDT_1M_SAMPLE,
                    "BINANCE",
                    "BTCUSDT",
                    BarInterval.ONE_MINUTE,
                    "backtest/fixtures/btcusdt_1m_sample.csv"
            )
    );

    /**
     * 读取注册 fixture；不存在时直接拒绝。
     */
    public FixtureMarketdataDataset require(String fixtureId) {
        FixtureMarketdataDataset dataset = datasets.get(fixtureId);
        if (dataset == null) {
            throw new IllegalArgumentException("unsupported fixtureId: " + fixtureId);
        }
        return dataset;
    }
}
