package com.guidinglight.nexusquant.marketdata.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataBarUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionJob;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionRun;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionStatus;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataIngestionJobRepository;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** 控制外部读取前后时钟，证明正式采集不能提前发布来源可见时间。 */
class MarketdataIngestionVisibilityTest {
    @Test
    void availabilityBeginsAfterProviderResponseRatherThanRequestStart() {
        var clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
        var receivedAt = clock.instant().plusSeconds(5);
        var open = Instant.parse("2026-10-01T10:00:00Z");
        var bar =
                new HistoricalBar(
                        "OKX",
                        "BTC-USDT",
                        BarInterval.ONE_HOUR,
                        open,
                        open.plusSeconds(3599),
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.TEN,
                        BigDecimal.ONE);
        var job =
                new MarketdataIngestionJob(
                        UUID.randomUUID(),
                        "OKX",
                        "SPOT",
                        "BTC-USDT",
                        BarInterval.ONE_HOUR,
                        open,
                        open.plusSeconds(3599),
                        MarketdataIngestionStatus.CREATED,
                        "EXCHANGE_HISTORICAL",
                        "test",
                        clock.instant(),
                        clock.instant(),
                        "{}");
        var recordedAt = new AtomicReference<Instant>();
        var service =
                new MarketdataIngestionService(
                        new Jobs(job),
                        (bars, source, at) -> {
                            recordedAt.set(at);
                            return new MarketdataBarUpsertStats(1, 0);
                        },
                        (request, from, to) -> {
                            clock.at = receivedAt;
                            return List.of(bar);
                        },
                        clock);
        var result = service.runOnce(job.jobId());
        assertEquals(MarketdataIngestionStatus.SUCCEEDED, result.status());
        assertEquals(receivedAt, recordedAt.get());
        assertTrue(recordedAt.get().isAfter(result.startedAt()));
        assertEquals(open, bar.openTime());
    }

    private static final class MutableClock extends Clock {
        private Instant at;

        MutableClock(Instant at) {
            this.at = at;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return at;
        }
    }

    private record Jobs(MarketdataIngestionJob job) implements MarketdataIngestionJobRepository {
        @Override
        public MarketdataIngestionJob createJob(MarketdataIngestionJob value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MarketdataIngestionJob> listJobs() {
            return List.of(job);
        }

        @Override
        public Optional<MarketdataIngestionJob> findJob(UUID id) {
            return id.equals(job.jobId()) ? Optional.of(job) : Optional.empty();
        }

        @Override
        public Optional<MarketdataIngestionJob> lockJob(UUID id) {
            return findJob(id);
        }

        @Override
        public Optional<MarketdataIngestionRun> findRun(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<Instant> findLatestSuccessfulActualEnd(UUID id) {
            return Optional.empty();
        }

        @Override
        public void updateJobStatus(UUID id, MarketdataIngestionStatus status, Instant at) {}

        @Override
        public MarketdataIngestionRun createRun(MarketdataIngestionRun run) {
            return run;
        }

        @Override
        public MarketdataIngestionRun finishRun(MarketdataIngestionRun run) {
            return run;
        }

        @Override
        public List<MarketdataIngestionRun> listRuns(UUID id) {
            return List.of();
        }
    }
}
