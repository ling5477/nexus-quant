package com.guidinglight.nexusquant.strategy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.application.command.StrategyVersionCreateRequest;
import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersion;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionRepository;

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

class StrategyVersionServiceTest {

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-05-18T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void selectedActivationPreservesIdentityAndIsIdempotent() {
        var definitions = new InMemoryStrategyDefinitionRepository();
        var repository = new InMemoryStrategyVersionRepository();
        definitions.insert(defaultDefinition());
        var service = new StrategyVersionService(definitions, repository, new ObjectMapper(), fixedClock);
        var draft = service.create(new StrategyVersionCreateRequest("demo-strategy", "Selected", "DRAFT",
                "{\"window\":4}", "{}", "{}", "owner"));
        var activated = service.activateSelectedVersion(draft);
        assertTrue(activated.changed());
        assertEquals(StrategyVersionStatus.ACTIVE, activated.version().status());
        assertEquals(new StrategyVersion(draft.strategyVersionId(), draft.strategyCode(), draft.version(),
                draft.versionName(), StrategyVersionStatus.ACTIVE, draft.paramSnapshotJson(), draft.configSnapshotJson(),
                draft.sourceSnapshotJson(), draft.checksum(), draft.createdBy(), draft.createdAt(), draft.updatedAt()), activated.version());
        var repeated = service.activateSelectedVersion(draft);
        assertFalse(repeated.changed());
        assertEquals(activated.version(), repeated.version());
        var archived = service.create(new StrategyVersionCreateRequest("demo-strategy", "Old", "ARCHIVED", "{}", "{}", "{}", "owner"));
        assertThrows(IllegalStateException.class, () -> service.activateSelectedVersion(archived));
        assertEquals(archived, repository.findById(archived.strategyVersionId()).orElseThrow());
        var forged = new StrategyVersion(draft.strategyVersionId(), draft.strategyCode(), draft.version(), draft.versionName(),
                draft.status(), "{\"window\":8}", draft.configSnapshotJson(), draft.sourceSnapshotJson(),
                draft.checksum(), draft.createdBy(), draft.createdAt(), draft.updatedAt());
        assertThrows(IllegalStateException.class, () -> service.activateSelectedVersion(forged));
    }

    @Test
    void shouldCreateVersionFromExistingStrategyAndNormalizeSnapshots() {
        InMemoryStrategyDefinitionRepository definitionRepository =
                new InMemoryStrategyDefinitionRepository();
        InMemoryStrategyVersionRepository versionRepository =
                new InMemoryStrategyVersionRepository();
        definitionRepository.insert(defaultDefinition());
        StrategyVersionService service =
                new StrategyVersionService(
                        definitionRepository, versionRepository, new ObjectMapper(), fixedClock);

        StrategyVersion version =
                service.create(
                        new StrategyVersionCreateRequest(
                                "demo-strategy",
                                "V1 Baseline",
                                "ACTIVE",
                                "{\"threshold\": 1}",
                                null,
                                "{\"source\":\"manual\"}",
                                "tester"));

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
        StrategyVersionService service =
                new StrategyVersionService(
                        new InMemoryStrategyDefinitionRepository(),
                        new InMemoryStrategyVersionRepository(),
                        new ObjectMapper(),
                        fixedClock);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.create(
                                new StrategyVersionCreateRequest(
                                        "missing", "V1", "DRAFT", "{}", "{}", "{}", "tester")));

        InMemoryStrategyDefinitionRepository definitionRepository =
                new InMemoryStrategyDefinitionRepository();
        definitionRepository.insert(defaultDefinition());
        StrategyVersionService jsonService =
                new StrategyVersionService(
                        definitionRepository,
                        new InMemoryStrategyVersionRepository(),
                        new ObjectMapper(),
                        fixedClock);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        jsonService.create(
                                new StrategyVersionCreateRequest(
                                        "demo-strategy",
                                        "V1",
                                        "DRAFT",
                                        "{bad-json",
                                        "{}",
                                        "{}",
                                        "tester")));
    }

    @Test
    void strategyVersionStatusMustNotAuthorizeLiveTrading() {
        for (StrategyVersionStatus status : StrategyVersionStatus.values()) {
            assertFalse(status.authorizesLiveTrading(), status + " must not enable LIVE trading");
        }
    }

    @Test
    void preservesExactNumbersInAllSnapshotsWithoutChangingTheSharedMapper() throws Exception {
        var definitions = new InMemoryStrategyDefinitionRepository();
        definitions.insert(defaultDefinition());
        var shared = new ObjectMapper();
        var service =
                new StrategyVersionService(
                        definitions, new InMemoryStrategyVersionRepository(), shared, fixedClock);
        String raw =
                "{\"number\":9007199254740993.123456789012345678,\"small\":1.234567890123456789e-40,\"negative\":-0.000000000000000000123456789012345678,\"large\":123456789012345678901234567890,\"list\":[1.000000000000000001,\"1.000000000000000001\",true,null]}";
        var created =
                service.create(
                        new StrategyVersionCreateRequest(
                                "demo-strategy", "precision", "DRAFT", raw, raw, raw, "tester"));
        var reader =
                new ObjectMapper().reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        for (String snapshot :
                List.of(
                        created.paramSnapshotJson(),
                        created.configSnapshotJson(),
                        created.sourceSnapshotJson())) {
            var node = reader.readTree(snapshot);
            assertEquals(
                    0,
                    new BigDecimal("9007199254740993.123456789012345678")
                            .compareTo(node.get("number").decimalValue()));
            assertEquals(
                    0,
                    new BigDecimal("1.234567890123456789e-40")
                            .compareTo(node.get("small").decimalValue()));
            assertEquals(
                    0,
                    new BigDecimal("-0.000000000000000000123456789012345678")
                            .compareTo(node.get("negative").decimalValue()));
            assertEquals("123456789012345678901234567890", node.get("large").asText());
            assertEquals(
                    0,
                    new BigDecimal("1.000000000000000001")
                            .compareTo(node.get("list").get(0).decimalValue()));
            assertEquals("1.000000000000000001", node.get("list").get(1).textValue());
            assertTrue(node.get("list").get(2).booleanValue());
            assertTrue(node.get("list").get(3).isNull());
        }
        assertTrue(shared.readTree("1.234567890123456789").isDouble());
        var same =
                new StrategyVersionService(
                                definitions,
                                new InMemoryStrategyVersionRepository(),
                                shared,
                                fixedClock)
                        .create(
                                new StrategyVersionCreateRequest(
                                        "demo-strategy",
                                        "precision",
                                        "DRAFT",
                                        raw,
                                        raw,
                                        raw,
                                        "tester"));
        assertEquals(created.checksum(), same.checksum());
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
                fixedClock.instant());
    }

    private static final class InMemoryStrategyDefinitionRepository
            implements StrategyDefinitionRepository {
        private final Map<String, StrategyDefinition> byCode = new LinkedHashMap<>();

        @Override
        public void insert(StrategyDefinition definition) {
            byCode.put(definition.strategyCode(), definition);
        }

        @Override
        public Optional<StrategyDefinition> findByStrategyId(String strategyId) {
            return byCode.values().stream()
                    .filter(item -> item.strategyId().equals(strategyId))
                    .findFirst();
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

    private static final class InMemoryStrategyVersionRepository
            implements StrategyVersionRepository {
        private final Map<String, StrategyVersion> byId = new LinkedHashMap<>();

        @Override
        public void insert(StrategyVersion strategyVersion) {
            byId.put(strategyVersion.strategyVersionId(), strategyVersion);
        }

        @Override
        public boolean activateDraft(StrategyVersion expected, Instant updatedAt) {
            StrategyVersion current = byId.get(expected.strategyVersionId());
            if (current == null || current.status() != StrategyVersionStatus.DRAFT) return false;
            byId.put(current.strategyVersionId(), new StrategyVersion(current.strategyVersionId(),
                    current.strategyCode(), current.version(), current.versionName(), StrategyVersionStatus.ACTIVE,
                    current.paramSnapshotJson(), current.configSnapshotJson(), current.sourceSnapshotJson(),
                    current.checksum(), current.createdBy(), current.createdAt(), updatedAt));
            return true;
        }

        @Override
        public List<StrategyVersion> listByStrategyCode(String strategyCode) {
            return byId.values().stream()
                    .filter(item -> item.strategyCode().equals(strategyCode))
                    .toList();
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
