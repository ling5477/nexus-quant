package com.guidinglight.nexusquant.marketdata.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityBarScopeFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityDatasetCoverageFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityIngestionFacts;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityMetricStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityOverviewQuery;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatusSummary;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessSourceHealth;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataReadinessStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataQualityOverviewRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MarketdataQualityOverviewServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-07-04T00:10:00Z"), ZoneOffset.UTC);

    @Test
    void shouldReturnNoDataOverviewWhenNoLocalFactsExist() {
        MarketdataQualityOverviewService service = service(
                List.of(),
                MarketdataQualityDatasetCoverageFacts.empty(),
                MarketdataQualityIngestionFacts.empty()
        );

        var overview = service.summarize(query(null, null, null, null, null));

        assertEquals(0, overview.totalBars());
        assertNull(overview.expectedBars());
        assertNull(overview.gapCount());
        assertEquals(MarketdataReadinessStatus.NO_DATA, overview.freshnessStatus());
        assertEquals(MarketdataReadinessSourceHealth.UNKNOWN, overview.sourceHealth());
        assertEquals(MarketdataQualityStatus.INCOMPLETE, overview.qualityStatus());
        assertEquals(MarketdataQualityMetricStatus.NOT_AVAILABLE, overview.duplicateCount().status());
        assertEquals(MarketdataQualityMetricStatus.NOT_AVAILABLE, overview.outOfOrderCount().status());
        assertEquals(MarketdataQualityMetricStatus.UNKNOWN, overview.staleCount().status());
        assertEquals("NO_DATA", overview.topIssues().getFirst().code());
    }

    @Test
    void shouldReturnNoDataForUnknownExchangeAndSymbolWithoutProviderFallback() {
        MarketdataQualityOverviewService service = service(
                List.of(),
                MarketdataQualityDatasetCoverageFacts.empty(),
                MarketdataQualityIngestionFacts.empty()
        );

        var overview = service.summarize(query(
                "unknown_exchange",
                "missing-usdt",
                BarInterval.ONE_MINUTE,
                null,
                null
        ));

        assertEquals("UNKNOWN_EXCHANGE", overview.scope().exchangeCode());
        assertEquals("MISSING-USDT", overview.scope().symbol());
        assertEquals(0, overview.totalBars());
        assertEquals(MarketdataReadinessStatus.NO_DATA, overview.freshnessStatus());
        assertEquals(MarketdataReadinessSourceHealth.UNKNOWN, overview.sourceHealth());
        assertEquals("NO_DATA", overview.topIssues().getFirst().code());
    }

    @Test
    void shouldAggregateDatasetCoverageExpectedActualAndGapFacts() {
        UUID datasetId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        MarketdataQualityOverviewService service = service(
                List.of(scope("BINANCE", "BTC-USDT", "1m", "marketdata_bars", 5, "2026-07-04T00:00:00Z", "2026-07-04T00:04:00Z")),
                new MarketdataQualityDatasetCoverageFacts(
                        1,
                        6L,
                        5L,
                        1L,
                        0L,
                        0L,
                        datasetId,
                        Instant.parse("2026-07-04T00:05:00Z")
                ),
                new MarketdataQualityIngestionFacts(
                        Instant.parse("2026-07-04T00:05:10Z"),
                        null,
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "SUCCEEDED"
                )
        );

        var overview = service.summarize(query("BINANCE", "BTC-USDT", BarInterval.ONE_MINUTE, datasetId, "LOCAL_DB"));

        assertEquals(5, overview.totalBars());
        assertEquals(6L, overview.expectedBars());
        assertEquals(1L, overview.gapCount());
        assertEquals(MarketdataQualityMetricStatus.AVAILABLE, overview.duplicateCount().status());
        assertEquals(0L, overview.duplicateCount().value());
        assertEquals(MarketdataQualityStatus.GAP_DETECTED, overview.qualityStatus());
        assertEquals(MarketdataReadinessSourceHealth.DEGRADED, overview.sourceHealth());
        assertEquals(1, overview.datasetCoverageSummary().datasetCount());
        assertEquals(5L, overview.datasetCoverageSummary().actualBars());
        assertEquals(datasetId, overview.datasetCoverageSummary().latestDatasetId());
        assertEquals("GAP_DETECTED", overview.topIssues().getFirst().code());
    }

    @Test
    void shouldReflectLatestIngestionFailureAndKeepLastRunId() {
        UUID runId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        MarketdataQualityOverviewService service = service(
                List.of(scope("BINANCE", "BTC-USDT", "1m", "marketdata_bars", 1, "2026-07-04T00:09:00Z", "2026-07-04T00:09:00Z")),
                MarketdataQualityDatasetCoverageFacts.empty(),
                new MarketdataQualityIngestionFacts(
                        Instant.parse("2026-07-04T00:08:00Z"),
                        Instant.parse("2026-07-04T00:09:30Z"),
                        runId,
                        "FAILED"
                )
        );

        var overview = service.summarize(query("BINANCE", "BTC-USDT", BarInterval.ONE_MINUTE, null, null));

        assertEquals(Instant.parse("2026-07-04T00:08:00Z"), overview.lastSuccessAt());
        assertEquals(Instant.parse("2026-07-04T00:09:30Z"), overview.lastFailureAt());
        assertEquals(runId, overview.lastIngestionRunId());
        assertEquals(MarketdataReadinessSourceHealth.ERROR, overview.sourceHealth());
        assertEquals("INGESTION_FAILURE", overview.topIssues().getFirst().code());
    }

    @Test
    void shouldAggregateMultipleSymbolsAndMarkStaleScope() {
        MarketdataQualityOverviewService service = service(
                List.of(
                        scope("BINANCE", "BTC-USDT", "1m", "FIXTURE_SYNC", 3, "2026-07-04T00:00:00Z", "2026-07-04T00:02:00Z"),
                        scope("OKX", "ETH-USDT", "5m", "marketdata_bars", 2, "2026-07-04T00:05:00Z", "2026-07-04T00:05:00Z")
                ),
                MarketdataQualityDatasetCoverageFacts.empty(),
                MarketdataQualityIngestionFacts.empty()
        );

        var overview = service.summarize(query(null, null, null, null, "PUBLIC_OUTBOUND"));

        assertEquals(5, overview.totalBars());
        assertEquals(4L, overview.expectedBars());
        assertEquals(0L, overview.gapCount());
        assertEquals(MarketdataReadinessStatus.STALE, overview.freshnessStatus());
        assertEquals(MarketdataReadinessSourceHealth.DEGRADED, overview.sourceHealth());
        assertEquals(1L, overview.staleCount().value());
        assertEquals("PUBLIC_OUTBOUND", overview.dataOriginSummary().requestedDataOrigin());
        assertEquals("LOCAL_DB", overview.dataOriginSummary().effectiveDataOrigin());
        assertEquals(2, overview.dataOriginSummary().localDbBars());
        assertEquals(3, overview.dataOriginSummary().fixtureBars());
        assertEquals("LOCAL_DB_ONLY_READ_MODEL", overview.dataOriginSummary().supportLevel());
    }

    private MarketdataQualityOverviewService service(
            List<MarketdataQualityBarScopeFacts> barScopes,
            MarketdataQualityDatasetCoverageFacts coverageFacts,
            MarketdataQualityIngestionFacts ingestionFacts
    ) {
        return new MarketdataQualityOverviewService(
                new StubOverviewRepository(barScopes, coverageFacts, ingestionFacts),
                fixedClock
        );
    }

    private MarketdataQualityOverviewQuery query(
            String exchangeCode,
            String symbol,
            BarInterval interval,
            UUID datasetId,
            String dataOrigin
    ) {
        return new MarketdataQualityOverviewQuery(
                exchangeCode,
                "SPOT",
                symbol,
                interval,
                null,
                dataOrigin,
                datasetId,
                null,
                null
        );
    }

    private MarketdataQualityBarScopeFacts scope(
            String exchangeCode,
            String symbol,
            String interval,
            String source,
            long barCount,
            String firstOpenTime,
            String lastOpenTime
    ) {
        return new MarketdataQualityBarScopeFacts(
                exchangeCode,
                "SPOT",
                symbol,
                BarInterval.fromWireValue(interval),
                source,
                barCount,
                Instant.parse(firstOpenTime),
                Instant.parse(lastOpenTime),
                Instant.parse(lastOpenTime).plus(BarInterval.fromWireValue(interval).duration()).minusSeconds(1),
                new MarketdataQualityStatusSummary(barCount, 0, 0, 0, Map.of("OK", barCount))
        );
    }

    private record StubOverviewRepository(
            List<MarketdataQualityBarScopeFacts> barScopes,
            MarketdataQualityDatasetCoverageFacts coverageFacts,
            MarketdataQualityIngestionFacts ingestionFacts
    ) implements MarketdataQualityOverviewRepository {
        @Override
        public List<MarketdataQualityBarScopeFacts> loadBarScopeFacts(MarketdataQualityOverviewQuery query) {
            return barScopes;
        }

        @Override
        public MarketdataQualityIngestionFacts loadIngestionFacts(MarketdataQualityOverviewQuery query) {
            return ingestionFacts;
        }

        @Override
        public MarketdataQualityDatasetCoverageFacts loadDatasetCoverageFacts(MarketdataQualityOverviewQuery query) {
            return coverageFacts;
        }
    }
}
