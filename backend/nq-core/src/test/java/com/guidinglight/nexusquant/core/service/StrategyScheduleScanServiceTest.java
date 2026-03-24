package com.guidinglight.nexusquant.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.core.model.StrategyDefinition;
import com.guidinglight.nexusquant.core.model.StrategySchedule;
import com.guidinglight.nexusquant.core.service.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.core.service.port.StrategyScheduleRepository;
import com.guidinglight.nexusquant.core.service.port.StrategyTriggerGateway;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class StrategyScheduleScanServiceTest {

    @Test
    void shouldTriggerRunForDueEnabledScheduleAndUpdateLastTriggeredAt() {
        InMemoryStrategyDefinitionRepository definitionRepository = new InMemoryStrategyDefinitionRepository();
        InMemoryStrategyScheduleRepository scheduleRepository = new InMemoryStrategyScheduleRepository();
        StrategyScheduleService scheduleService = new StrategyScheduleService(definitionRepository, scheduleRepository);
        CapturingStrategyTriggerGateway triggerGateway = new CapturingStrategyTriggerGateway();
        StrategyScheduleScanService scanService = new StrategyScheduleScanService(
                scheduleService,
                definitionRepository,
                triggerGateway,
                new ObjectMapper()
        );
        definitionRepository.insert(enabledDefinition("str-1"));
        StrategySchedule schedule = new StrategySchedule(
                "sch-1",
                "str-1",
                "CRON",
                "0 * * * * *",
                "UTC",
                true,
                "{}",
                "SCHEDULE_WINDOW",
                "BINANCE",
                1001L,
                "SIM",
                Instant.parse("2026-03-23T09:00:00Z"),
                Instant.parse("2026-03-23T09:00:00Z"),
                Instant.parse("2026-03-23T09:00:00Z")
        );
        scheduleRepository.insert(schedule);

        List<StrategyScheduleScanResult> results = scanService.scanOnce("trc-schedule-scan-1");

        assertEquals(1, results.size());
        assertTrue(results.getFirst().triggered());
        assertNotNull(results.getFirst().strategyRunId());
        assertEquals("str-1", triggerGateway.lastRequest.strategyId());
        assertTrue(scheduleRepository.findByScheduleJobId("sch-1").orElseThrow().lastTriggeredAt().isAfter(schedule.lastTriggeredAt()));
    }

    @Test
    void shouldNotTriggerWhenScheduleNotDueOrDefinitionDisabled() {
        InMemoryStrategyDefinitionRepository definitionRepository = new InMemoryStrategyDefinitionRepository();
        InMemoryStrategyScheduleRepository scheduleRepository = new InMemoryStrategyScheduleRepository();
        StrategyScheduleService scheduleService = new StrategyScheduleService(definitionRepository, scheduleRepository);
        CapturingStrategyTriggerGateway triggerGateway = new CapturingStrategyTriggerGateway();
        StrategyScheduleScanService scanService = new StrategyScheduleScanService(
                scheduleService,
                definitionRepository,
                triggerGateway,
                new ObjectMapper()
        );
        definitionRepository.insert(disabledDefinition("str-2"));
        scheduleRepository.insert(new StrategySchedule(
                "sch-2",
                "str-2",
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

        List<StrategyScheduleScanResult> results = scanService.scanOnce("trc-schedule-scan-2");

        assertEquals(1, results.size());
        assertFalse(results.getFirst().triggered());
        assertEquals("strategy_definition_disabled", results.getFirst().reason());
    }

    private StrategyDefinition enabledDefinition(String strategyId) {
        return new StrategyDefinition(
                strategyId,
                "demo-grid",
                "Demo Strategy",
                "GRID",
                "BINANCE",
                1001L,
                "SIM",
                true,
                "{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"quantity\":\"0.01\",\"price\":\"100.00\"}",
                1,
                Instant.parse("2026-03-23T09:00:00Z"),
                Instant.parse("2026-03-23T09:00:00Z")
        );
    }

    private StrategyDefinition disabledDefinition(String strategyId) {
        return new StrategyDefinition(
                strategyId,
                "demo-grid-disabled",
                "Disabled Strategy",
                "GRID",
                "BINANCE",
                1001L,
                "SIM",
                false,
                "{\"symbol\":\"BTC-USDT\",\"side\":\"BUY\",\"orderType\":\"LIMIT\",\"quantity\":\"0.01\",\"price\":\"100.00\"}",
                1,
                Instant.parse("2026-03-24T09:00:00Z"),
                Instant.parse("2026-03-24T09:00:00Z")
        );
    }

    private static final class CapturingStrategyTriggerGateway implements StrategyTriggerGateway {
        private StrategyManualTriggerRequest lastRequest;

        @Override
        public StrategyManualTriggerResult trigger(StrategyManualTriggerRequest request) {
            this.lastRequest = request;
            return new StrategyManualTriggerResult(
                    request.strategyId(),
                    "run-schedule-1",
                    request.requestId(),
                    "ord-schedule-1",
                    com.guidinglight.nexusquant.contracts.model.OrderStatus.ACCEPTED,
                    com.guidinglight.nexusquant.core.model.StrategyRunStatus.RUNNING,
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
            return false;
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
}
