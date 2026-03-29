package com.guidinglight.nexusquant.strategy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyTriggerGateway;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class StrategyScheduleScanServiceTest {

    @Test
    void shouldTriggerWhenDueAndWithinWindow() {
        Fixture fixture = new Fixture();
        fixture.definitionRepository.insert(enabledDefinition("str-1", true));
        StrategySchedule schedule = schedule(
                "sch-1",
                "str-1",
                true,
                "{\"startTime\":\"00:00\",\"endTime\":\"23:59:59\",\"timezone\":\"UTC\"}",
                "SCHEDULE_WINDOW",
                Instant.parse("2026-03-23T09:00:00Z")
        );
        fixture.scheduleRepository.insert(schedule);

        StrategyScheduleScanBatchResult batchResult = fixture.scanService.scanOnce("trc-schedule-scan-1");

        assertEquals(1, batchResult.scannedCount());
        assertEquals(1, batchResult.triggeredCount());
        StrategyScheduleScanResult result = batchResult.results().getFirst();
        assertEquals(StrategyScheduleScanOutcome.TRIGGERED, result.outcome());
        assertNotNull(result.requestId());
        assertEquals("run-schedule-1", result.strategyRunId());
        assertEquals(result.requestId(), fixture.triggerGateway.lastRequest.requestId());
        assertTrue(
                fixture.scheduleRepository.findByScheduleJobId("sch-1").orElseThrow().lastTriggeredAt().isAfter(schedule.lastTriggeredAt())
        );
    }

    @Test
    void shouldSkipWhenOutOfWindow() {
        Fixture fixture = new Fixture();
        fixture.definitionRepository.insert(enabledDefinition("str-2", true));
        fixture.scheduleRepository.insert(schedule(
                "sch-2",
                "str-2",
                true,
                "{\"startTime\":\"00:00\",\"endTime\":\"00:00:01\",\"timezone\":\"UTC\"}",
                "SCHEDULE_WINDOW",
                Instant.parse("2026-03-23T09:00:00Z")
        ));

        StrategyScheduleScanResult result = fixture.scanService.scanOnce("trc-schedule-scan-2").results().getFirst();

        assertEquals(StrategyScheduleScanOutcome.SKIPPED_WINDOW, result.outcome());
        assertNull(result.strategyRunId());
        assertNull(result.requestId());
        assertEquals(0, fixture.triggerGateway.invocationCount);
        assertEquals(
                Instant.parse("2026-03-23T09:00:00Z"),
                fixture.scheduleRepository.findByScheduleJobId("sch-2").orElseThrow().lastTriggeredAt()
        );
    }

    @Test
    void shouldSkipWhenDedupHitExists() {
        Fixture fixture = new Fixture();
        fixture.definitionRepository.insert(enabledDefinition("str-3", true));
        fixture.scheduleRepository.insert(schedule(
                "sch-3",
                "str-3",
                true,
                "{\"startTime\":\"00:00\",\"endTime\":\"23:59:59\",\"timezone\":\"UTC\"}",
                "SCHEDULE_WINDOW",
                Instant.parse("2026-03-23T09:00:00Z")
        ));
        fixture.runRepository.insert(new StrategyRun(
                "run-existing",
                "str-3",
                1001L,
                "BINANCE",
                "SIM",
                "SCHEDULER",
                StrategyRunStatus.FAILED,
                "{}",
                "req-schedule-sch-3-window-1774256460000",
                Instant.parse("2026-03-23T09:01:00Z"),
                Instant.parse("2026-03-23T09:01:05Z"),
                "already triggered",
                "trc-existing"
        ));

        StrategyScheduleScanResult result = fixture.scanService.scanOnce("trc-schedule-scan-3").results().getFirst();

        assertEquals(StrategyScheduleScanOutcome.SKIPPED_DEDUP, result.outcome());
        assertEquals("req-schedule-sch-3-window-1774256460000", result.requestId());
        assertEquals(0, fixture.triggerGateway.invocationCount);
    }

    @Test
    void shouldSkipWhenStrategyHasActiveRun() {
        Fixture fixture = new Fixture();
        fixture.definitionRepository.insert(enabledDefinition("str-4", true));
        fixture.scheduleRepository.insert(schedule(
                "sch-4",
                "str-4",
                true,
                "{\"startTime\":\"00:00\",\"endTime\":\"23:59:59\",\"timezone\":\"UTC\"}",
                "STRATEGY",
                Instant.parse("2026-03-23T09:00:00Z")
        ));
        fixture.runRepository.insert(new StrategyRun(
                "run-busy",
                "str-4",
                1001L,
                "BINANCE",
                "SIM",
                "SCHEDULER",
                StrategyRunStatus.RUNNING,
                "{}",
                "req-busy",
                Instant.parse("2026-03-23T09:01:00Z"),
                null,
                null,
                "trc-busy"
        ));

        StrategyScheduleScanResult result = fixture.scanService.scanOnce("trc-schedule-scan-4").results().getFirst();

        assertEquals(StrategyScheduleScanOutcome.SKIPPED_BUSY, result.outcome());
        assertEquals("strategy_run_active", result.detail());
        assertEquals(0, fixture.triggerGateway.invocationCount);
    }

    @Test
    void shouldSkipBusyWhenConcurrentScanHitsSameSchedule() throws Exception {
        InMemoryStrategyDefinitionRepository definitionRepository = new InMemoryStrategyDefinitionRepository();
        InMemoryStrategyScheduleRepository scheduleRepository = new InMemoryStrategyScheduleRepository();
        InMemoryStrategyRunRepository runRepository = new InMemoryStrategyRunRepository();
        BlockingStrategyTriggerGateway triggerGateway = new BlockingStrategyTriggerGateway();
        StrategyScheduleService scheduleService = new StrategyScheduleService(definitionRepository, scheduleRepository);
        StrategyScheduleScanService scanService = new StrategyScheduleScanService(
                scheduleService,
                definitionRepository,
                runRepository,
                triggerGateway,
                new ObjectMapper()
        );
        definitionRepository.insert(enabledDefinition("str-busy", true));
        scheduleRepository.insert(schedule(
                "sch-busy",
                "str-busy",
                true,
                "{\"startTime\":\"00:00\",\"endTime\":\"23:59:59\",\"timezone\":\"UTC\"}",
                "SCHEDULE_WINDOW",
                Instant.parse("2026-03-23T09:00:00Z")
        ));

        AtomicReference<StrategyScheduleScanBatchResult> firstResult = new AtomicReference<>();
        Thread firstScan = new Thread(() -> firstResult.set(scanService.scanOnce("trc-scan-busy-1")));
        firstScan.start();
        assertTrue(triggerGateway.entered.await(5, TimeUnit.SECONDS));

        StrategyScheduleScanBatchResult secondResult = scanService.scanOnce("trc-scan-busy-2");

        triggerGateway.release.countDown();
        firstScan.join(5000);

        assertEquals(StrategyScheduleScanOutcome.SKIPPED_BUSY, secondResult.results().getFirst().outcome());
        assertEquals("schedule_busy", secondResult.results().getFirst().detail());
        assertEquals(1, firstResult.get().triggeredCount());
    }

    @Test
    void shouldSkipWhenScheduleDisabledDefinitionDisabledOrNotDue() {
        Fixture fixture = new Fixture();
        fixture.definitionRepository.insert(enabledDefinition("str-5", false));
        fixture.definitionRepository.insert(enabledDefinition("str-6", true));
        fixture.scheduleRepository.insert(schedule(
                "sch-disabled",
                "str-6",
                false,
                "{}",
                "SCHEDULE_WINDOW",
                Instant.parse("2026-03-23T09:00:00Z")
        ));
        fixture.scheduleRepository.insert(schedule(
                "sch-strategy-disabled",
                "str-5",
                true,
                "{}",
                "SCHEDULE_WINDOW",
                Instant.parse("2026-03-23T09:00:00Z")
        ));
        fixture.scheduleRepository.insert(new StrategySchedule(
                "sch-not-due",
                "str-6",
                "CRON",
                "0 * * * * *",
                "UTC",
                true,
                "{}",
                "SCHEDULE_WINDOW",
                "BINANCE",
                1001L,
                "SIM",
                null,
                Instant.parse("2099-03-24T09:00:00Z"),
                Instant.parse("2099-03-24T09:00:00Z")
        ));

        StrategyScheduleScanBatchResult batchResult = fixture.scanService.scanOnce("trc-schedule-scan-5");

        assertEquals(3, batchResult.scannedCount());
        assertEquals(1, batchResult.skippedDisabledCount());
        assertEquals(1, batchResult.skippedStrategyDisabledCount());
        assertEquals(1, batchResult.skippedNotDueCount());
    }

    private static StrategyDefinition enabledDefinition(String strategyId, boolean enabled) {
        return new StrategyDefinition(
                strategyId,
                "code-" + strategyId,
                "Demo Strategy " + strategyId,
                "GRID",
                "BINANCE",
                1001L,
                "SIM",
                enabled,
                "{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"quantity\":\"0.01\",\"price\":\"100.00\"}",
                1,
                Instant.parse("2026-03-23T09:00:00Z"),
                Instant.parse("2026-03-23T09:00:00Z")
        );
    }

    private static StrategySchedule schedule(
            String scheduleJobId,
            String strategyId,
            boolean enabled,
            String windowConfig,
            String dedupScope,
            Instant lastTriggeredAt
    ) {
        return new StrategySchedule(
                scheduleJobId,
                strategyId,
                "CRON",
                "0 * * * * *",
                "UTC",
                enabled,
                windowConfig,
                dedupScope,
                "BINANCE",
                1001L,
                "SIM",
                lastTriggeredAt,
                Instant.parse("2026-03-23T09:00:00Z"),
                Instant.parse("2026-03-23T09:00:00Z")
        );
    }

    private static final class Fixture {
        private final InMemoryStrategyDefinitionRepository definitionRepository = new InMemoryStrategyDefinitionRepository();
        private final InMemoryStrategyScheduleRepository scheduleRepository = new InMemoryStrategyScheduleRepository();
        private final InMemoryStrategyRunRepository runRepository = new InMemoryStrategyRunRepository();
        private final CapturingStrategyTriggerGateway triggerGateway = new CapturingStrategyTriggerGateway();
        private final StrategyScheduleService scheduleService = new StrategyScheduleService(definitionRepository, scheduleRepository);
        private final StrategyScheduleScanService scanService = new StrategyScheduleScanService(
                scheduleService,
                definitionRepository,
                runRepository,
                triggerGateway,
                new ObjectMapper()
        );
    }

    private static final class CapturingStrategyTriggerGateway implements StrategyTriggerGateway {
        private StrategyManualTriggerRequest lastRequest;
        private int invocationCount;

        @Override
        public StrategyManualTriggerResult trigger(StrategyManualTriggerRequest request) {
            this.lastRequest = request;
            this.invocationCount++;
            return new StrategyManualTriggerResult(
                    request.strategyId(),
                    "run-schedule-1",
                    request.requestId(),
                    "ord-schedule-1",
                    OrderStatus.ACCEPTED,
                    StrategyRunStatus.RUNNING,
                    false
            );
        }
    }

    private static final class BlockingStrategyTriggerGateway implements StrategyTriggerGateway {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public StrategyManualTriggerResult trigger(StrategyManualTriggerRequest request) {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting to release trigger", ex);
            }
            return new StrategyManualTriggerResult(
                    request.strategyId(),
                    "run-blocking",
                    request.requestId(),
                    "ord-blocking",
                    OrderStatus.ACCEPTED,
                    StrategyRunStatus.RUNNING,
                    false
            );
        }
    }

    private static final class InMemoryStrategyDefinitionRepository implements StrategyDefinitionRepository {
        private final Map<String, StrategyDefinition> storage = new LinkedHashMap<>();

        @Override
        public void insert(StrategyDefinition definition) {
            storage.put(definition.strategyId(), definition);
        }

        @Override
        public Optional<StrategyDefinition> findByStrategyId(String strategyId) {
            return Optional.ofNullable(storage.get(strategyId));
        }

        @Override
        public Optional<StrategyDefinition> findByStrategyCode(String strategyCode) {
            return storage.values().stream().filter(item -> item.strategyCode().equals(strategyCode)).findFirst();
        }

        @Override
        public List<StrategyDefinition> listAll() {
            return storage.values().stream().toList();
        }

        @Override
        public boolean updateEnabled(String strategyId, boolean enabled, Instant updatedAt) {
            StrategyDefinition current = storage.get(strategyId);
            if (current == null) {
                return false;
            }
            storage.put(strategyId, current.withEnabled(enabled, updatedAt));
            return true;
        }
    }

    private static final class InMemoryStrategyScheduleRepository implements StrategyScheduleRepository {
        private final Map<String, StrategySchedule> storage = new LinkedHashMap<>();

        @Override
        public void insert(StrategySchedule schedule) {
            storage.put(schedule.scheduleJobId(), schedule);
        }

        @Override
        public Optional<StrategySchedule> findByScheduleJobId(String scheduleJobId) {
            return Optional.ofNullable(storage.get(scheduleJobId));
        }

        @Override
        public List<StrategySchedule> listByStrategyId(String strategyId) {
            return storage.values().stream().filter(item -> item.strategyId().equals(strategyId)).toList();
        }

        @Override
        public List<StrategySchedule> listAll() {
            return storage.values().stream().toList();
        }

        @Override
        public List<StrategySchedule> listEnabledSchedules() {
            return storage.values().stream().filter(StrategySchedule::enabled).toList();
        }

        @Override
        public boolean updateEnabled(String scheduleJobId, boolean enabled, Instant updatedAt) {
            StrategySchedule current = storage.get(scheduleJobId);
            if (current == null) {
                return false;
            }
            storage.put(scheduleJobId, current.withEnabled(enabled, updatedAt));
            return true;
        }

        @Override
        public boolean updateLastTriggeredAt(String scheduleJobId, Instant lastTriggeredAt, Instant updatedAt) {
            StrategySchedule current = storage.get(scheduleJobId);
            if (current == null) {
                return false;
            }
            storage.put(scheduleJobId, current.withLastTriggeredAt(lastTriggeredAt, updatedAt));
            return true;
        }
    }

    private static final class InMemoryStrategyRunRepository implements StrategyRunRepository {
        private final Map<String, StrategyRun> storage = new LinkedHashMap<>();

        @Override
        public void insert(StrategyRun strategyRun) {
            storage.put(strategyRun.strategyRunId(), strategyRun);
        }

        @Override
        public Optional<StrategyRun> findByStrategyRunId(String strategyRunId) {
            return Optional.ofNullable(storage.get(strategyRunId));
        }

        @Override
        public Optional<StrategyRun> findLatestByRequestId(String requestId) {
            return storage.values().stream()
                    .filter(item -> requestId.equals(item.requestId()))
                    .findFirst();
        }

        @Override
        public boolean existsActiveRunByStrategyId(String strategyId) {
            return storage.values().stream()
                    .anyMatch(item -> item.strategyId().equals(strategyId)
                            && item.status() != StrategyRunStatus.FAILED);
        }

        @Override
        public boolean updateStatus(String strategyRunId, StrategyRunStatus status, Instant finishedAt, String errorMessage) {
            StrategyRun current = storage.get(strategyRunId);
            if (current == null) {
                return false;
            }
            storage.put(strategyRunId, new StrategyRun(
                    current.strategyRunId(),
                    current.strategyId(),
                    current.accountId(),
                    current.exchangeCode(),
                    current.tradeEnv(),
                    current.triggerType(),
                    status,
                    current.configSnapshot(),
                    current.requestId(),
                    current.startedAt(),
                    finishedAt,
                    errorMessage,
                    current.traceId()
            ));
            return true;
        }
    }
}

