package com.guidinglight.nexusquant.marketdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.marketdata.application.command.FixtureMarketdataIngestionCommand;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataBarUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

class MarketdataBarIngestServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-04-06T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldIngestRegisteredFixtureAndReturnStableStats() {
        RecordingMarketdataBarRepository marketdataBarRepository = new RecordingMarketdataBarRepository();
        MarketdataBarIngestService service = new MarketdataBarIngestService(
                new FixtureMarketdataRegistry(),
                marketdataBarRepository,
                fixedClock
        );

        var result = service.ingestFixture(new FixtureMarketdataIngestionCommand(
                FixtureMarketdataRegistry.BINANCE_BTCUSDT_1M_SAMPLE,
                "BINANCE",
                "BTCUSDT",
                "1m",
                Instant.parse("2025-01-01T00:01:00Z"),
                Instant.parse("2025-01-01T00:04:59Z")
        ));

        assertEquals(4, result.rowsRead());
        assertEquals(4, result.rowsInserted());
        assertEquals(0, result.rowsUpdated());
        assertEquals("BINANCE", marketdataBarRepository.lastBars.getFirst().exchangeCode());
        assertEquals("FIXTURE_SYNC", marketdataBarRepository.lastSource);
        assertEquals(Instant.parse("2026-04-06T08:00:00Z"), marketdataBarRepository.lastIngestedAt);
    }

    @Test
    void shouldRejectFixtureScopeMismatch() {
        MarketdataBarIngestService service = new MarketdataBarIngestService(
                new FixtureMarketdataRegistry(),
                new RecordingMarketdataBarRepository(),
                fixedClock
        );

        assertThrows(IllegalArgumentException.class, () -> service.ingestFixture(new FixtureMarketdataIngestionCommand(
                FixtureMarketdataRegistry.BINANCE_BTCUSDT_1M_SAMPLE,
                "OKX",
                "BTCUSDT",
                "1m",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:05:59Z")
        )));
    }

    private static final class RecordingMarketdataBarRepository implements MarketdataBarRepository {
        private List<HistoricalBar> lastBars = List.of();
        private String lastSource;
        private Instant lastIngestedAt;

        @Override
        public MarketdataBarUpsertStats upsertBars(List<HistoricalBar> bars, String source, Instant ingestedAt) {
            this.lastBars = bars;
            this.lastSource = source;
            this.lastIngestedAt = ingestedAt;
            return new MarketdataBarUpsertStats(bars.size(), 0);
        }
    }
}
