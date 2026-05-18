package com.guidinglight.nexusquant.strategy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersion;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class StrategyVersionServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-18T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldCreateVersionFromExistingStrategyAndNormalizeSnapshots() {
        InMemoryStrategyDefinitionRepository definitionRepository = new InMemoryStrategyDefinitionRepository();
        InMemoryStrategyVersionRepository versionRepository = new InMemoryStrategyVersionRepository();
        definitionRepository.insert(defaultDefinition());
        StrategyVersionService service = new StrategyVersionService(
                definitionRepository,
                versionRepository,
                new ObjectMapper(),
                fixedClock
        );

        StrategyVersion version = service.create(new StrategyVersionCreateRequest(
                "demo-strategy",
                "V1 Baseline",
                "ACTIVE",
                "{\"threshold\": 1}",
                null,
                "{\"source\":\"manual\"}",
                "tester"
        ));

        assertEquals("demo-strategy", version.strategyCode());
        assertEquals(1, version.version());
        assertEquals(StrategyVersionStatus.ACTIVE, version.status());
        assertEquals("{\"threshold\":1}", version.paramSnapshotJson());
        assertEquals("{\"risk\":\"low\"}", version.configSnapshotJson());
        assertEquals(fixedClock.instant(), version.createdAt());
        assertEquals(64, version.checksum().length());
    }

    @Test
    void shouldRejectMissingStrategyAndInvalidJson() {
        StrategyVersionService service = new StrategyVersionService(
                new InMemoryStrategyDefinitionRepository(),
                new InMemoryStrategyVersionRepository(),
                new ObjectMapper(),
                fixedClock
        );

        assertThrows(IllegalArgumentException.class, () -> service.create(new StrategyVersionCreateRequest(
                "missing",
                "V1",
                "DRAFT",
                "{}",
                "{}",
                "{}",
                "tester"
        )));

        InMemoryStrategyDefinitionRepository definitionRepository = new InMemoryStrategyDefinitionRepository();
        definitionRepository.insert(defaultDefinition());
        StrategyVersionService jsonService = new StrategyVersionService(
                definitionRepository,
                new InMemoryStrategyVersionRepository(),
                new ObjectMapper(),
                fixedClock
        );
        assertThrows(IllegalArgumentException.class, () -> jsonService.create(new StrategyVersionCreateRequest(
                "demo-strategy",
                "V1",
                "DRAFT",
                "{bad-json",
                "{}",
                "{}",
                "tester"
        )));
    }

    private StrategyDefinition defaultDefinition() {
        return new StrategyDefinition(
                "str-1",
                "demo-strategy",
                "Demo Strategy",
                "GRID",
                "BINANCE",
                1001L,
                "SIM",
                false,
                "{\"risk\":\"low\"}",
                1,
                fixedClock.instant(),
                fixedClock.instant()
        );
    }

    private static final class InMemoryStrategyDefinitionRepository implements StrategyDefinitionRepository {
        private final Map<String, StrategyDefinition> byCode = new LinkedHashMap<>();

        @Override
        public void insert(StrategyDefinition definition) {
            byCode.put(definition.strategyCode(), definition);
        }

        @Override
        public Optional<StrategyDefinition> findByStrategyId(String strategyId) {
            return byCode.values().stream().filter(item -> item.strategyId().equals(strategyId)).findFirst();
        }

        @Override
        public Optional<StrategyDefinition> findByStrategyCode(String strategyCode) {
            return Optional.ofNullable(byCode.get(strategyCode));
        }

        @Override
        public List<StrategyDefinition> listAll() {
            return new ArrayList<>(byCode.values());
        }

        @Override
        public boolean updateEnabled(String strategyId, boolean enabled, Instant updatedAt) {
            return false;
        }
    }

    private static final class InMemoryStrategyVersionRepository implements StrategyVersionRepository {
        private final Map<String, StrategyVersion> byId = new LinkedHashMap<>();

        @Override
        public void insert(StrategyVersion strategyVersion) {
            byId.put(strategyVersion.strategyVersionId(), strategyVersion);
        }

        @Override
        public List<StrategyVersion> listByStrategyCode(String strategyCode) {
            return byId.values().stream().filter(item -> item.strategyCode().equals(strategyCode)).toList();
        }

        @Override
        public Optional<StrategyVersion> findById(String strategyVersionId) {
            return Optional.ofNullable(byId.get(strategyVersionId));
        }

        @Override
        public int maxVersion(String strategyCode) {
            return byId.values().stream()
                    .filter(item -> item.strategyCode().equals(strategyCode))
                    .mapToInt(StrategyVersion::version)
                    .max()
                    .orElse(0);
        }
    }
}
