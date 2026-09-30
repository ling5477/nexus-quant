package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.application.command.StrategyDefinitionCreateRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class StrategyDefinitionServiceTest {

    @Test
    void registeredEnvironmentBindsStrategyBeforePersistence() {
        InMemoryStrategyDefinitionRepository repository = new InMemoryStrategyDefinitionRepository();
        StrategyDefinitionService service = new StrategyDefinitionService(repository,
                (accountId, venue) -> accountId == 1001L ? "SIM" : "LIVE");
        assertEquals("SIM", service.create(createRequest("sim", 1001L, "SIM")).tradeEnv());
        assertEquals("LIVE", service.create(createRequest("live", 2002L, "LIVE")).tradeEnv());
        assertEquals("ACCOUNT_TRADE_ENVIRONMENT_MISMATCH", assertThrows(IllegalArgumentException.class,
                () -> service.create(createRequest("unsafe-live", 2002L, "SIM"))).getMessage());
        assertEquals("ACCOUNT_TRADE_ENVIRONMENT_MISMATCH", assertThrows(IllegalArgumentException.class,
                () -> service.create(createRequest("unsafe-sim", 1001L, "LIVE"))).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> service.create(createRequest("missing", 2002L, null)));
        assertEquals(2, repository.listAll().size());
    }

    @Test
    void enablingDefinitionRechecksCurrentRegisteredEnvironment() {
        InMemoryStrategyDefinitionRepository repository = new InMemoryStrategyDefinitionRepository();
        AtomicReference<String> environment = new AtomicReference<>("SIM");
        StrategyDefinitionService service = new StrategyDefinitionService(repository,
                (accountId, venue) -> environment.get());
        StrategyDefinition created = service.create(createRequest("stale-env", 1001L, "SIM"));
        environment.set("LIVE");
        assertEquals("ACCOUNT_TRADE_ENVIRONMENT_MISMATCH", assertThrows(IllegalArgumentException.class,
                () -> service.enable(created.strategyId())).getMessage());
        assertFalse(service.getByStrategyId(created.strategyId()).enabled());
    }

    private static StrategyDefinitionCreateRequest createRequest(String code, Long accountId, String environment) {
        return new StrategyDefinitionCreateRequest(code, code, "GRID", "OKX", accountId, environment, "{}");
    }

    @Test
    void shouldCreateListGetEnableAndDisableStrategyDefinition() {
        InMemoryStrategyDefinitionRepository repository = new InMemoryStrategyDefinitionRepository();
        StrategyDefinitionService service = new StrategyDefinitionService(repository, (accountId, venue) -> "SIM");

        StrategyDefinition created = service.create(new StrategyDefinitionCreateRequest(
                "demo-grid",
                "Demo Grid",
                "GRID",
                "BINANCE",
                1001L,
                "SIM",
                "{\"grid\":\"narrow\"}"
        ));

        assertNotNull(created.strategyId());
        assertFalse(created.enabled());
        assertEquals(1, service.listAll().size());
        assertEquals(created.strategyId(), service.getByStrategyId(created.strategyId()).strategyId());

        StrategyDefinition enabled = service.enable(created.strategyId());
        assertTrue(enabled.enabled());

        StrategyDefinition disabled = service.disable(created.strategyId());
        assertFalse(disabled.enabled());
    }

    @Test
    void shouldRejectDuplicateStrategyCode() {
        InMemoryStrategyDefinitionRepository repository = new InMemoryStrategyDefinitionRepository();
        StrategyDefinitionService service = new StrategyDefinitionService(repository, (accountId, venue) -> "SIM");

        service.create(new StrategyDefinitionCreateRequest(
                "demo-grid",
                "Demo Grid",
                "GRID",
                "BINANCE",
                1001L,
                "SIM",
                "{}"
        ));

        assertThrows(IllegalStateException.class, () -> service.create(new StrategyDefinitionCreateRequest(
                "demo-grid",
                "Demo Grid 2",
                "GRID",
                "BINANCE",
                1001L,
                "SIM",
                "{}"
        )));
    }

    @Test
    void shouldFailWhenStrategyDefinitionDoesNotExist() {
        StrategyDefinitionService service = new StrategyDefinitionService(new InMemoryStrategyDefinitionRepository(),
                (accountId, venue) -> "SIM");

        assertThrows(IllegalArgumentException.class, () -> service.getByStrategyId("missing"));
        assertThrows(IllegalArgumentException.class, () -> service.enable("missing"));
        assertThrows(IllegalArgumentException.class, () -> service.disable("missing"));
    }

    private static final class InMemoryStrategyDefinitionRepository implements StrategyDefinitionRepository {

        private final Map<String, StrategyDefinition> storage = new LinkedHashMap<>();

        @Override
        public void insert(StrategyDefinition definition) {
            if (findByStrategyCode(definition.strategyCode()).isPresent()) {
                throw new DuplicateKeyException("duplicate strategy_code");
            }
            storage.put(definition.strategyId(), definition);
        }

        @Override
        public Optional<StrategyDefinition> findByStrategyId(String strategyId) {
            return Optional.ofNullable(storage.get(strategyId));
        }

        @Override
        public Optional<StrategyDefinition> findByStrategyCode(String strategyCode) {
            return storage.values().stream()
                    .filter(item -> item.strategyCode().equals(strategyCode))
                    .findFirst();
        }

        @Override
        public List<StrategyDefinition> listAll() {
            return new ArrayList<>(storage.values());
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
}
