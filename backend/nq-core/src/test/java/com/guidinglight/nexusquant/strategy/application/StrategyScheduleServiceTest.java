package com.guidinglight.nexusquant.strategy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class StrategyScheduleServiceTest {

    @Test
    void shouldCreateListDetailEnableAndDisableSchedule() {
        InMemoryStrategyDefinitionRepository definitionRepository = new InMemoryStrategyDefinitionRepository();
        InMemoryStrategyScheduleRepository scheduleRepository = new InMemoryStrategyScheduleRepository();
        StrategyScheduleService service = new StrategyScheduleService(definitionRepository, scheduleRepository);
        definitionRepository.insert(enabledDefinition("str-1"));

        StrategySchedule created = service.create(new StrategyScheduleCreateRequest(
                "str-1",
                "CRON",
                "0 * * * * *",
                "UTC",
                true,
                "{\"window\":\"default\"}",
                "SCHEDULE_WINDOW"
        ));

        assertEquals("str-1", created.strategyId());
        assertEquals(1, service.listByStrategyId("str-1").size());
        assertEquals(created.scheduleJobId(), service.getByScheduleJobId(created.scheduleJobId()).scheduleJobId());

        StrategySchedule disabled = service.disable(created.scheduleJobId());
        assertFalse(disabled.enabled());

        StrategySchedule enabled = service.enable(created.scheduleJobId());
        assertTrue(enabled.enabled());
    }

    @Test
    void shouldFailWhenStrategyDefinitionMissing() {
        StrategyScheduleService service = new StrategyScheduleService(
                new InMemoryStrategyDefinitionRepository(),
                new InMemoryStrategyScheduleRepository()
        );

        assertThrows(IllegalArgumentException.class, () -> service.create(new StrategyScheduleCreateRequest(
                "missing",
                "CRON",
                "0 * * * * *",
                "UTC",
                true,
                "{}",
                "SCHEDULE_WINDOW"
        )));
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
}

