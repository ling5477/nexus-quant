package com.guidinglight.nexusquant.infra.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guidinglight.nexusquant.backtest.model.BarInterval;
import com.guidinglight.nexusquant.backtest.model.HistoricalDatasetSpec;
import com.guidinglight.nexusquant.backtest.model.HistoricalMarketDataQuery;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class FixtureHistoricalMarketDataPortTest {

    @Test
    void shouldLoadFixtureBarsWithinRequestedWindow() {
        FixtureHistoricalMarketDataPort fixtureHistoricalMarketDataPort = new FixtureHistoricalMarketDataPort();

        var bars = fixtureHistoricalMarketDataPort.loadBars(new HistoricalMarketDataQuery(
                new HistoricalDatasetSpec(
                        "fixture",
                        "btcusdt-1m-sample",
                        "BTCUSDT",
                        BarInterval.ONE_MINUTE,
                        "backtest/fixtures/btcusdt_1m_sample.csv"
                ),
                "BTCUSDT",
                BarInterval.ONE_MINUTE,
                Instant.parse("2025-01-01T00:01:00Z"),
                Instant.parse("2025-01-01T00:04:59Z")
        ));

        assertEquals(4, bars.size());
        assertEquals(Instant.parse("2025-01-01T00:01:00Z"), bars.getFirst().openTime());
        assertEquals(Instant.parse("2025-01-01T00:04:59Z"), bars.getLast().closeTime());
    }
}
