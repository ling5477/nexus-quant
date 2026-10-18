package com.guidinglight.nexusquant.marketdata.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataIngestionJobCommand;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** 保留接入时间边界和断点反例；数据库并发幂等性由真实事务测试证明。 */
class MarketdataIngestionOperationTest {
    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(START.plusSeconds(1000), ZoneOffset.UTC);

    @Test
    void resumeStartsAtNextBarOpenWithoutSkippingAnInterval() {
        var jobs = new Jobs();
        var requested = new ArrayList<Instant>();
        var service =
                new MarketdataIngestionService(
                        jobs,
                        (bars, source, at) -> new MarketdataBarUpsertStats(bars.size(), 0),
                        (job, from, to) -> {
                            requested.add(from);
                            Instant open = START.plusSeconds((requested.size() - 1L) * 60);
                            return List.of(bar(open));
                        },
                        CLOCK);
        var job = service.createJob(command(START.plusSeconds(180).minusMillis(1)));
        service.runOnce(job.jobId());
        service.runOnce(job.jobId());
        assertEquals(List.of(START, START.plusSeconds(60)), requested);
    }

    @Test
    void emptyTimeWindowIsRejectedBeforeCreatingAJob() {
        var jobs = new Jobs();
        var service =
                new MarketdataIngestionService(
                        jobs,
                        (bars, source, at) -> new MarketdataBarUpsertStats(0, 0),
                        (job, from, to) -> List.of(),
                        CLOCK);
        assertThrows(IllegalArgumentException.class, () -> service.createJob(command(START)));
        assertEquals(0, jobs.jobs.size());
    }

    @Test
    void sameJobIdentityReturnsTheOriginalButConflictingInputOrCreatorCannotReplaceIt() {
        var jobs = new Jobs();
        var service = service(jobs, new AtomicInteger(), false);
        UUID id = UUID.randomUUID();
        var input = command(START.plusSeconds(180));
        var first = service.createJob(id, input);
        assertEquals(first, service.createJob(id, input));
        assertThrows(
                IllegalStateException.class,
                () -> service.createJob(id, command(START.plusSeconds(181))));
        var foreign =
                new CreateMarketdataIngestionJobCommand(
                        "OKX",
                        "SPOT",
                        "BTC-USDT",
                        "1m",
                        START,
                        START.plusSeconds(180),
                        "other-operator");
        assertThrows(IllegalStateException.class, () -> service.createJob(id, foreign));
        assertEquals(first, jobs.jobs.get(id));
        assertEquals(1, jobs.jobs.size());
    }

    @Test
    void successfulRunReplaysItsOriginalResultWithoutAnotherProviderCall() {
        var jobs = new Jobs();
        var calls = new AtomicInteger();
        var service = service(jobs, calls, false);
        var job = service.createJob(command(START.plusSeconds(180)));
        UUID runId = UUID.randomUUID();
        var result = service.runOnce(job.jobId(), runId);
        assertEquals(result, service.runOnce(job.jobId(), runId));
        assertEquals(Optional.of(result), service.findRun(job.jobId(), runId));
        assertEquals(Optional.empty(), service.findRun(UUID.randomUUID(), runId));
        assertEquals(1, calls.get());
        assertEquals(1, jobs.runs.size());
    }

    @Test
    void failedRunAlsoReplaysWithoutAutomaticallyCallingTheProviderAgain() {
        var jobs = new Jobs();
        var calls = new AtomicInteger();
        var service = service(jobs, calls, true);
        var job = service.createJob(command(START.plusSeconds(180)));
        UUID runId = UUID.randomUUID();
        var result = service.runOnce(job.jobId(), runId);
        assertEquals(MarketdataIngestionStatus.FAILED, result.status());
        assertEquals(result, service.runOnce(job.jobId(), runId));
        assertEquals(1, calls.get());
        service.runOnce(job.jobId(), UUID.randomUUID());
        assertEquals(2, calls.get());
    }

    @Test
    void aRunIdentityCannotBeReusedForAnotherJob() {
        var jobs = new Jobs();
        var calls = new AtomicInteger();
        var service = service(jobs, calls, false);
        var first = service.createJob(command(START.plusSeconds(180)));
        var second = service.createJob(command(START.plusSeconds(240)));
        UUID runId = UUID.randomUUID();
        var result = service.runOnce(first.jobId(), runId);
        assertThrows(IllegalStateException.class, () -> service.runOnce(second.jobId(), runId));
        assertEquals(result, jobs.runs.get(runId));
        assertEquals(1, calls.get());
    }

    private static MarketdataIngestionService service(
            Jobs jobs, AtomicInteger calls, boolean fail) {
        return new MarketdataIngestionService(
                jobs,
                (bars, source, at) -> new MarketdataBarUpsertStats(bars.size(), 0),
                (job, from, to) -> {
                    calls.incrementAndGet();
                    if (fail) throw new IllegalStateException("controlled public source failure");
                    return List.of(bar(START));
                },
                CLOCK);
    }

    private static CreateMarketdataIngestionJobCommand command(Instant end) {
        return new CreateMarketdataIngestionJobCommand(
                "OKX", "SPOT", "BTC-USDT", "1m", START, end, "owner");
    }

    private static HistoricalBar bar(Instant open) {
        return new HistoricalBar(
                "OKX",
                "BTC-USDT",
                BarInterval.ONE_MINUTE,
                open,
                open.plusSeconds(60).minusMillis(1),
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ONE);
    }

    private static final class Jobs implements MarketdataIngestionJobRepository {
        private final Map<UUID, MarketdataIngestionJob> jobs = new LinkedHashMap<>();
        private final Map<UUID, MarketdataIngestionRun> runs = new LinkedHashMap<>();

        @Override
        public MarketdataIngestionJob createJob(MarketdataIngestionJob job) {
            jobs.putIfAbsent(job.jobId(), job);
            return jobs.get(job.jobId());
        }

        @Override
        public List<MarketdataIngestionJob> listJobs() {
            return List.copyOf(jobs.values());
        }

        @Override
        public Optional<MarketdataIngestionJob> findJob(UUID id) {
            return Optional.ofNullable(jobs.get(id));
        }

        @Override
        public Optional<MarketdataIngestionJob> lockJob(UUID id) {
            return findJob(id);
        }

        @Override
        public Optional<MarketdataIngestionRun> findRun(UUID id) {
            return Optional.ofNullable(runs.get(id));
        }

        @Override
        public Optional<Instant> findLatestSuccessfulActualEnd(UUID id) {
            return runs.values().stream()
                    .filter(r -> r.jobId().equals(id))
                    .filter(
                            r ->
                                    r.status() == MarketdataIngestionStatus.SUCCEEDED
                                            || r.status() == MarketdataIngestionStatus.PARTIAL)
                    .map(MarketdataIngestionRun::actualEndTime)
                    .filter(v -> v != null)
                    .max(Instant::compareTo);
        }

        @Override
        public void updateJobStatus(UUID id, MarketdataIngestionStatus status, Instant at) {}

        @Override
        public MarketdataIngestionRun createRun(MarketdataIngestionRun run) {
            runs.putIfAbsent(run.runId(), run);
            return runs.get(run.runId());
        }

        @Override
        public MarketdataIngestionRun finishRun(MarketdataIngestionRun run) {
            runs.put(run.runId(), run);
            return run;
        }

        @Override
        public List<MarketdataIngestionRun> listRuns(UUID id) {
            return runs.values().stream().filter(r -> r.jobId().equals(id)).toList();
        }
    }
}
