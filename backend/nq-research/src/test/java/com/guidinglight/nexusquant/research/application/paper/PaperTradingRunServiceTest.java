package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingOrderRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingPositionRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingRunRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingTradeRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PaperTradingRunServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-19T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldCreateRunFromPublishAndFixateSnapshots() {
        var publishRepo = new InMemoryPublishRepo();
        var runRepo = new InMemoryRunRepo();
        var backtestRunRepo = new InMemoryBacktestRunRepo();
        publishRepo.records.add(samplePublish());
        backtestRunRepo.runs.add(sampleBacktestRun());

        var service = new PaperTradingRunService(
                runRepo, new InMemoryOrderRepo(), new InMemoryTradeRepo(), new InMemoryPositionRepo(),
                publishRepo, backtestRunRepo, fixedClock
        );

        PaperTradingRun run = service.create(new PaperTradingRunCreateCommand(
                "pub-001", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", "{\"feeRate\":\"0.001\"}", "tester"
        ));

        assertNotNull(run.paperRunId());
        assertEquals("pub-001", run.publishId());
        assertEquals("sv-001", run.strategyVersionId());
        assertEquals(PaperTradingRunStatus.CREATED, run.status());
        assertEquals("{\"publish\":\"snapshot\"}", run.publishSnapshotJson());
        assertEquals("{\"version\":\"snapshot\"}", run.strategyVersionSnapshotJson());
        assertEquals("{\"dataset\":\"snapshot\"}", run.datasetSnapshotJson());
        assertEquals("{\"param\":\"snapshot\"}", run.paramSnapshotJson());
        assertEquals("{\"feeRate\":\"0.001\"}", run.configSnapshotJson());
    }

    @Test
    void shouldStartAndStopRun() {
        var publishRepo = new InMemoryPublishRepo();
        var runRepo = new InMemoryRunRepo();
        var backtestRunRepo = new InMemoryBacktestRunRepo();
        publishRepo.records.add(samplePublish());
        backtestRunRepo.runs.add(sampleBacktestRun());

        var service = new PaperTradingRunService(
                runRepo, new InMemoryOrderRepo(), new InMemoryTradeRepo(), new InMemoryPositionRepo(),
                publishRepo, backtestRunRepo, fixedClock
        );

        PaperTradingRun created = service.create(new PaperTradingRunCreateCommand(
                "pub-001", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", null, "tester"
        ));

        PaperTradingRun started = service.start(created.paperRunId());
        assertEquals(PaperTradingRunStatus.RUNNING, started.status());
        assertNotNull(started.startedAt());

        PaperTradingRun stopped = service.stop(created.paperRunId());
        assertEquals(PaperTradingRunStatus.STOPPED, stopped.status());
        assertNotNull(stopped.stoppedAt());
    }

    @Test
    void shouldRejectStartFromNonCreatedState() {
        var publishRepo = new InMemoryPublishRepo();
        var runRepo = new InMemoryRunRepo();
        var backtestRunRepo = new InMemoryBacktestRunRepo();
        publishRepo.records.add(samplePublish());
        backtestRunRepo.runs.add(sampleBacktestRun());

        var service = new PaperTradingRunService(
                runRepo, new InMemoryOrderRepo(), new InMemoryTradeRepo(), new InMemoryPositionRepo(),
                publishRepo, backtestRunRepo, fixedClock
        );

        PaperTradingRun created = service.create(new PaperTradingRunCreateCommand(
                "pub-001", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", null, "tester"
        ));
        service.start(created.paperRunId());
        assertThrows(IllegalStateException.class, () -> service.start(created.paperRunId()));
    }

    @Test
    void shouldRejectMissingPublish() {
        var service = new PaperTradingRunService(
                new InMemoryRunRepo(), new InMemoryOrderRepo(), new InMemoryTradeRepo(), new InMemoryPositionRepo(),
                new InMemoryPublishRepo(), new InMemoryBacktestRunRepo(), fixedClock
        );
        assertThrows(IllegalArgumentException.class, () -> service.create(new PaperTradingRunCreateCommand(
                "missing", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", null, "tester"
        )));
    }

    private BacktestPublishRecord samplePublish() {
        return new BacktestPublishRecord(
                "pub-001", "brn-001", "rc-001", "bc-001", "strat-001", "eval-001",
                null, "sv-001", PublishStatus.SUCCEEDED, "Test Publish",
                "{\"publish\":\"snapshot\"}", "{\"version\":\"snapshot\"}", "{\"eval\":\"summary\"}",
                null, null, Instant.parse("2026-05-19T09:00:00Z"),
                Instant.parse("2026-05-19T09:00:00Z"), Instant.parse("2026-05-19T09:00:00Z")
        );
    }

    private BacktestRun sampleBacktestRun() {
        return new BacktestRun(
                "brn-001", "bc-001", "rc-001", "strat-001", "{}", "sv-001",
                "{\"version\":\"snapshot\"}", "{\"param\":\"snapshot\"}", "{}",
                "{\"config\":\"snapshot\"}", "{\"dataset\":\"snapshot\"}",
                BacktestRunStatus.SUCCEEDED,
                Instant.parse("2026-05-19T08:00:00Z"), Instant.parse("2026-05-19T08:00:00Z"),
                Instant.parse("2026-05-19T08:30:00Z"), null, null, "{}",
                Instant.parse("2026-05-19T08:00:00Z"), Instant.parse("2026-05-19T08:30:00Z")
        );
    }

    static class InMemoryRunRepo implements PaperTradingRunRepository {
        final List<PaperTradingRun> runs = new ArrayList<>();

        @Override
        public void insert(PaperTradingRun run) { runs.add(run); }

        @Override
        public Optional<PaperTradingRun> findById(String paperRunId) {
            return runs.stream().filter(r -> r.paperRunId().equals(paperRunId)).findFirst();
        }

        @Override
        public List<PaperTradingRun> list(String publishId, String status) { return List.copyOf(runs); }

        @Override
        public boolean updateStatus(String paperRunId, PaperTradingRunStatus status, Instant startedAt, Instant stoppedAt, Instant updatedAt) {
            for (int i = 0; i < runs.size(); i++) {
                PaperTradingRun r = runs.get(i);
                if (r.paperRunId().equals(paperRunId)) {
                    runs.set(i, new PaperTradingRun(
                            r.paperRunId(), r.publishId(), r.strategyVersionId(), status,
                            r.tradeEnv(), r.exchangeCode(), r.marketType(), r.symbol(), r.intervalCode(),
                            startedAt != null ? startedAt : r.startedAt(),
                            stoppedAt != null ? stoppedAt : r.stoppedAt(),
                            r.publishSnapshotJson(), r.strategyVersionSnapshotJson(), r.datasetSnapshotJson(),
                            r.paramSnapshotJson(), r.configSnapshotJson(),
                            r.createdBy(), r.createdAt(), updatedAt
                    ));
                    return true;
                }
            }
            return false;
        }
    }

    static class InMemoryOrderRepo implements PaperTradingOrderRepository {
        @Override public void insert(PaperTradingOrder order) {}
        @Override public List<PaperTradingOrder> listByRunId(String paperRunId) { return List.of(); }
    }

    static class InMemoryTradeRepo implements PaperTradingTradeRepository {
        @Override public void insert(PaperTradingTrade trade) {}
        @Override public List<PaperTradingTrade> listByRunId(String paperRunId) { return List.of(); }
    }

    static class InMemoryPositionRepo implements PaperTradingPositionRepository {
        @Override public void insert(PaperTradingPosition position) {}
        @Override public void upsert(PaperTradingPosition position) {}
        @Override public List<PaperTradingPosition> listByRunId(String paperRunId) { return List.of(); }
    }

    static class InMemoryPublishRepo implements BacktestPublishRecordRepository {
        final List<BacktestPublishRecord> records = new ArrayList<>();
        @Override public void upsert(BacktestPublishRecord record) { records.add(record); }
        @Override public Optional<BacktestPublishRecord> findByBacktestRunId(String backtestRunId) {
            return records.stream().filter(r -> r.backtestRunId().equals(backtestRunId)).findFirst();
        }
        @Override public List<BacktestPublishRecord> listAll() { return List.copyOf(records); }
        @Override public Optional<BacktestPublishRecord> findByPublishRecordId(String publishRecordId) {
            return records.stream().filter(r -> r.publishRecordId().equals(publishRecordId)).findFirst();
        }
    }

    static class InMemoryBacktestRunRepo implements BacktestRunRepository {
        final List<BacktestRun> runs = new ArrayList<>();
        @Override public void insert(BacktestRun backtestRun) { runs.add(backtestRun); }
        @Override public Optional<BacktestRun> findByBacktestRunId(String backtestRunId) {
            return runs.stream().filter(r -> r.backtestRunId().equals(backtestRunId)).findFirst();
        }
        @Override public List<BacktestRun> list(String researchConfigId, String backtestConfigId) { return List.copyOf(runs); }
        @Override public boolean updateExecution(String backtestRunId, BacktestRunStatus status, Instant startedAt, Instant finishedAt, String failureCode, String failureMessage, String summaryJson, Instant updatedAt) { return true; }
    }
}
