package com.guidinglight.nexusquant.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.core.model.StrategyDefinition;
import com.guidinglight.nexusquant.core.model.StrategyRun;
import com.guidinglight.nexusquant.core.model.StrategyRunStatus;
import com.guidinglight.nexusquant.core.service.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.core.service.port.StrategyExecutionGateway;
import com.guidinglight.nexusquant.core.service.port.StrategyRunRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class StrategyManualTriggerServiceTest {

    @Test
    void shouldTriggerEnabledStrategyAndCreateRunningStrategyRun() {
        InMemoryStrategyDefinitionRepository definitionRepository = new InMemoryStrategyDefinitionRepository();
        InMemoryStrategyRunRepository runRepository = new InMemoryStrategyRunRepository();
        CapturingStrategyExecutionGateway executionGateway = new CapturingStrategyExecutionGateway(
                new PlaceOrderResult("ord-trigger-1", OrderStatus.ACCEPTED, false)
        );
        StrategyManualTriggerService service = new StrategyManualTriggerService(
                definitionRepository,
                runRepository,
                executionGateway
        );

        StrategyDefinition definition = enabledDefinition("str-1", "demo-grid");
        definitionRepository.insert(definition);

        StrategyManualTriggerResult result = service.trigger(new StrategyManualTriggerRequest(
                definition.strategyId(),
                "req-trigger-1",
                "BTC-USDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new java.math.BigDecimal("0.01"),
                new java.math.BigDecimal("100.00"),
                "trc-trigger-1"
        ));

        assertEquals(definition.strategyId(), result.strategyId());
        assertEquals("req-trigger-1", result.requestId());
        assertEquals(OrderStatus.ACCEPTED, result.orderStatus());
        assertEquals(StrategyRunStatus.RUNNING, result.strategyRunStatus());
        assertNotNull(result.strategyRunId());
        assertTrue(runRepository.findByStrategyRunId(result.strategyRunId()).isPresent());
        assertEquals(result.strategyRunId(), executionGateway.lastRequest.strategyRunId());
        assertEquals("req-trigger-1", executionGateway.lastRequest.requestId());
    }

    @Test
    void shouldRejectDisabledStrategyDefinition() {
        InMemoryStrategyDefinitionRepository definitionRepository = new InMemoryStrategyDefinitionRepository();
        InMemoryStrategyRunRepository runRepository = new InMemoryStrategyRunRepository();
        StrategyManualTriggerService service = new StrategyManualTriggerService(
                definitionRepository,
                runRepository,
                request -> new PlaceOrderResult("ord-trigger-2", OrderStatus.ACCEPTED, false)
        );
        definitionRepository.insert(disabledDefinition("str-2", "disabled-grid"));

        assertThrows(IllegalStateException.class, () -> service.trigger(new StrategyManualTriggerRequest(
                "str-2",
                "req-trigger-2",
                "BTC-USDT",
                OrderSide.BUY,
                OrderType.MARKET,
                new java.math.BigDecimal("0.01"),
                null,
                "trc-trigger-2"
        )));
    }

    @Test
    void shouldRejectMissingStrategyDefinition() {
        StrategyManualTriggerService service = new StrategyManualTriggerService(
                new InMemoryStrategyDefinitionRepository(),
                new InMemoryStrategyRunRepository(),
                request -> new PlaceOrderResult("ord-trigger-3", OrderStatus.ACCEPTED, false)
        );

        assertThrows(IllegalArgumentException.class, () -> service.trigger(new StrategyManualTriggerRequest(
                "missing",
                "req-trigger-3",
                "BTC-USDT",
                OrderSide.BUY,
                OrderType.MARKET,
                new java.math.BigDecimal("0.01"),
                null,
                "trc-trigger-3"
        )));
    }

    @Test
    void shouldMarkStrategyRunFailedWhenOrderSubmissionFails() {
        InMemoryStrategyDefinitionRepository definitionRepository = new InMemoryStrategyDefinitionRepository();
        InMemoryStrategyRunRepository runRepository = new InMemoryStrategyRunRepository();
        StrategyManualTriggerService service = new StrategyManualTriggerService(
                definitionRepository,
                runRepository,
                request -> new PlaceOrderResult("ord-trigger-4", OrderStatus.REJECTED, false)
        );
        StrategyDefinition definition = enabledDefinition("str-4", "failing-grid");
        definitionRepository.insert(definition);

        StrategyManualTriggerResult result = service.trigger(new StrategyManualTriggerRequest(
                definition.strategyId(),
                "req-trigger-4",
                "BTC-USDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new java.math.BigDecimal("0.01"),
                new java.math.BigDecimal("100.00"),
                "trc-trigger-4"
        ));

        assertEquals(StrategyRunStatus.FAILED, result.strategyRunStatus());
        StrategyRun stored = runRepository.findByStrategyRunId(result.strategyRunId()).orElseThrow();
        assertEquals(StrategyRunStatus.FAILED, stored.status());
        assertTrue(stored.errorMessage().contains("REJECTED"));
        assertNotNull(stored.finishedAt());
    }

    private StrategyDefinition enabledDefinition(String strategyId, String strategyCode) {
        return new StrategyDefinition(
                strategyId,
                strategyCode,
                "Demo Strategy",
                "GRID",
                "BINANCE",
                1001L,
                "SIM",
                true,
                "{}",
                1,
                Instant.parse("2026-03-23T12:00:00Z"),
                Instant.parse("2026-03-23T12:00:00Z")
        );
    }

    private StrategyDefinition disabledDefinition(String strategyId, String strategyCode) {
        return new StrategyDefinition(
                strategyId,
                strategyCode,
                "Disabled Strategy",
                "GRID",
                "BINANCE",
                1001L,
                "SIM",
                false,
                "{}",
                1,
                Instant.parse("2026-03-23T12:00:00Z"),
                Instant.parse("2026-03-23T12:00:00Z")
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
        public java.util.List<StrategyDefinition> listAll() {
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
            return storage.values().stream().filter(item -> requestId.equals(item.requestId())).findFirst();
        }

        @Override
        public boolean existsActiveRunByStrategyId(String strategyId) {
            return storage.values().stream()
                    .anyMatch(item -> item.strategyId().equals(strategyId) && item.status() != StrategyRunStatus.FAILED);
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

    private static final class CapturingStrategyExecutionGateway implements StrategyExecutionGateway {

        private final PlaceOrderResult placeOrderResult;
        private PlaceOrderRequest lastRequest;

        private CapturingStrategyExecutionGateway(PlaceOrderResult placeOrderResult) {
            this.placeOrderResult = placeOrderResult;
        }

        @Override
        public PlaceOrderResult placeOrder(PlaceOrderRequest request) {
            this.lastRequest = request;
            return placeOrderResult;
        }
    }
}
