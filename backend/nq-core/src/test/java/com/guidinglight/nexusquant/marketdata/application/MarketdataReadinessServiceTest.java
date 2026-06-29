package com.guidinglight.nexusquant.marketdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatusSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessBarFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessIngestionFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessQuery;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataReadinessRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MarketdataReadinessServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-29T00:04:00Z"), ZoneOffset.UTC);

    @Test
    void shouldReturnNoDataWhenNoBarsExist() {
        MarketdataReadinessService service = new MarketdataReadinessService(
                new StubReadinessRepository(MarketdataReadinessBarFacts.empty(), MarketdataReadinessIngestionFacts.empty()),
                fixedClock
        );

        var summary = service.summarize(query(
                Instant.parse("2026-06-29T00:00:00Z"),
                Instant.parse("2026-06-29T00:02:59Z")
        ));

        assertEquals(MarketdataReadinessStatus.NO_DATA, summary.status());
        assertEquals(MarketdataReadinessStatus.NO_DATA, summary.freshnessStatus());
        assertEquals(0, summary.barCount());
        assertEquals(3L, summary.expectedBarCount());
        assertEquals(3L, summary.gapCount());
        assertEquals("NO_MIGRATION_MVP", summary.backendSupportLevel().name());
    }

    @Test
    void shouldReturnFreshWhenBarsCoverRequestedWindow() {
        MarketdataReadinessService service = new MarketdataReadinessService(
                new StubReadinessRepository(new MarketdataReadinessBarFacts(
                        3,
                        Instant.parse("2026-06-29T00:00:00Z"),
                        Instant.parse("2026-06-29T00:02:00Z"),
                        Instant.parse("2026-06-29T00:02:59Z"),
                        new MarketdataQualityStatusSummary(3, 0, 0, 0, Map.of("OK", 3L))
                ), MarketdataReadinessIngestionFacts.empty()),
                fixedClock
        );

        var summary = service.summarize(query(
                Instant.parse("2026-06-29T00:00:00Z"),
                Instant.parse("2026-06-29T00:02:59Z")
        ));

        assertEquals(MarketdataReadinessStatus.FRESH, summary.status());
        assertEquals(MarketdataReadinessStatus.FRESH, summary.freshnessStatus());
        assertEquals(3, summary.barCount());
        assertEquals(3L, summary.expectedBarCount());
        assertEquals(0L, summary.gapCount());
        assertEquals(0, summary.unknownQualityCount());
    }

    @Test
    void shouldMarkGapWhenExpectedBarSequenceHasMissingOpenTime() {
        MarketdataReadinessService service = new MarketdataReadinessService(
                new StubReadinessRepository(new MarketdataReadinessBarFacts(
                        2,
                        Instant.parse("2026-06-29T00:00:00Z"),
                        Instant.parse("2026-06-29T00:02:00Z"),
                        Instant.parse("2026-06-29T00:02:59Z"),
                        new MarketdataQualityStatusSummary(2, 0, 0, 0, Map.of("OK", 2L))
                ), MarketdataReadinessIngestionFacts.empty()),
                fixedClock
        );

        var summary = service.summarize(query(
                Instant.parse("2026-06-29T00:00:00Z"),
                Instant.parse("2026-06-29T00:02:59Z")
        ));

        assertEquals(MarketdataReadinessStatus.GAP, summary.status());
        assertEquals(3L, summary.expectedBarCount());
        assertEquals(1L, summary.gapCount());
    }

    @Test
    void shouldSummarizeUnknownBadAndGapQualityStatusesWithoutMarkingFresh() {
        MarketdataReadinessService service = new MarketdataReadinessService(
                new StubReadinessRepository(new MarketdataReadinessBarFacts(
                        3,
                        Instant.parse("2026-06-29T00:00:00Z"),
                        Instant.parse("2026-06-29T00:02:00Z"),
                        Instant.parse("2026-06-29T00:02:59Z"),
                        new MarketdataQualityStatusSummary(
                                0,
                                1,
                                1,
                                1,
                                Map.of("GAP", 1L, "BAD", 1L, "UNKNOWN", 1L)
                        )
                ), MarketdataReadinessIngestionFacts.empty()),
                fixedClock
        );

        var summary = service.summarize(query(
                Instant.parse("2026-06-29T00:00:00Z"),
                Instant.parse("2026-06-29T00:02:59Z")
        ));

        assertEquals(MarketdataReadinessStatus.ERROR, summary.status());
        assertEquals(1, summary.qualityStatusSummary().gapSignalCount());
        assertEquals(1, summary.qualityStatusSummary().invalidCount());
        assertEquals(1, summary.qualityStatusSummary().unknownQualityCount());
        assertEquals(1, summary.unknownQualityCount());
    }

    @Test
    void shouldMarkErrorWhenLatestLocalFailureIsAfterLastSuccess() {
        MarketdataReadinessService service = new MarketdataReadinessService(
                new StubReadinessRepository(new MarketdataReadinessBarFacts(
                        1,
                        Instant.parse("2026-06-29T00:00:00Z"),
                        Instant.parse("2026-06-29T00:00:00Z"),
                        Instant.parse("2026-06-29T00:00:59Z"),
                        new MarketdataQualityStatusSummary(1, 0, 0, 0, Map.of("OK", 1L))
                ), new MarketdataReadinessIngestionFacts(
                        Instant.parse("2026-06-29T00:00:30Z"),
                        Instant.parse("2026-06-29T00:01:30Z"),
                        1_000L,
                        "FAILED"
                )),
                fixedClock
        );

        var summary = service.summarize(query(
                Instant.parse("2026-06-29T00:00:00Z"),
                Instant.parse("2026-06-29T00:00:59Z")
        ));

        assertEquals(MarketdataReadinessStatus.ERROR, summary.status());
        assertEquals(Instant.parse("2026-06-29T00:00:30Z"), summary.lastSuccessAt());
        assertEquals(Instant.parse("2026-06-29T00:01:30Z"), summary.lastFailureAt());
    }

    @Test
    void shouldRejectInvalidTimeRangeBeforeRepositoryCall() {
        StubReadinessRepository repository = new StubReadinessRepository(
                MarketdataReadinessBarFacts.empty(),
                MarketdataReadinessIngestionFacts.empty()
        );
        MarketdataReadinessService service = new MarketdataReadinessService(repository, fixedClock);

        assertThrows(IllegalArgumentException.class, () -> service.summarize(query(
                Instant.parse("2026-06-29T00:02:59Z"),
                Instant.parse("2026-06-29T00:00:00Z")
        )));
        assertNull(repository.lastQuery);
    }

    private MarketdataReadinessQuery query(Instant from, Instant to) {
        return new MarketdataReadinessQuery("BINANCE", "SPOT", "BTC-USDT", BarInterval.ONE_MINUTE, from, to);
    }

    private static final class StubReadinessRepository implements MarketdataReadinessRepository {
        private final MarketdataReadinessBarFacts barFacts;
        private final MarketdataReadinessIngestionFacts ingestionFacts;
        private MarketdataReadinessQuery lastQuery;

        private StubReadinessRepository(
                MarketdataReadinessBarFacts barFacts,
                MarketdataReadinessIngestionFacts ingestionFacts
        ) {
            this.barFacts = barFacts;
            this.ingestionFacts = ingestionFacts;
        }

        @Override
        public MarketdataReadinessBarFacts loadBarFacts(MarketdataReadinessQuery query) {
            this.lastQuery = query;
            return barFacts;
        }

        @Override
        public MarketdataReadinessIngestionFacts loadIngestionFacts(MarketdataReadinessQuery query) {
            this.lastQuery = query;
            return ingestionFacts;
        }
    }
}
