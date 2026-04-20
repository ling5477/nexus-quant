package com.guidinglight.nexusquant.marketdata.application;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * FixtureMarketdataRegistry 持有平台级 marketdata fixture 注册数据集。
 * <p>
 * Why:
 * marketdata 是平台级正式子系统，不再只是 `nq-backtest` 的附属能力。
 * fixture 注册表作为测试/回测数据集的单一事实源，避免 controller、backtest service 和测试各自散落路径常量。
 */
@Component
public class FixtureMarketdataRegistry {

    public static final String BINANCE_BTCUSDT_1M_SAMPLE = "BINANCE_BTCUSDT_1M_SAMPLE";
    public static final String BINANCE_ETHUSDT_1M_SAMPLE = "BINANCE_ETHUSDT_1M_SAMPLE";

    private final Map<String, FixtureMarketdataDataset> datasets = Map.of(
            BINANCE_BTCUSDT_1M_SAMPLE,
            new FixtureMarketdataDataset(
                    BINANCE_BTCUSDT_1M_SAMPLE,
                    "BINANCE",
                    "BTCUSDT",
                    BarInterval.ONE_MINUTE,
                    "backtest/fixtures/btcusdt_1m_sample.csv"
            ),
            BINANCE_ETHUSDT_1M_SAMPLE,
            new FixtureMarketdataDataset(
                    BINANCE_ETHUSDT_1M_SAMPLE,
                    "BINANCE",
                    "ETHUSDT",
                    BarInterval.ONE_MINUTE,
                    "backtest/fixtures/ethusdt_1m_sample.csv"
            )
    );

    /**
     * 读取注册 fixture；不存在时直接拒绝。
     */
    public FixtureMarketdataDataset require(String fixtureId) {
        return find(fixtureId).orElseThrow(() -> new IllegalArgumentException("unsupported fixtureId: " + fixtureId));
    }

    /**
     * 按 fixtureId 读取注册数据集；未命中时返回 empty。
     * <p>
     * Why:
     * BacktestExecutionService 需要在未显式提供 resourcePath 时按 datasetId 回查正式 fixture 注册表，
     * 但不能把“未注册”伪装成默认 BTC 样例，因此这里提供显式 optional 查询。
     */
    public Optional<FixtureMarketdataDataset> find(String fixtureId) {
        if (fixtureId == null || fixtureId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(datasets.get(fixtureId.trim().toUpperCase()));
    }
}
