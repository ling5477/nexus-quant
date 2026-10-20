package com.guidinglight.nexusquant.research.application.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.domain.StrategyVersionSnapshotView;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

class BacktestConfigSnapshotPrecisionTest {
    private static final String RAW =
            "{\"number\":9007199254740993.123456789012345678,\"small\":1.234567890123456789e-40,\"negative\":-0.000000000000000000123456789012345678,\"large\":123456789012345678901234567890,\"list\":[1.000000000000000001,\"1.000000000000000001\",true,null]}";

    @Test
    void bindsAllLifecycleStatesWithoutRoundingAnySnapshotOrParameter() throws Exception {
        for (String status : List.of("DRAFT", "ACTIVE", "ARCHIVED")) {
            var repository = mock(BacktestConfigRepository.class);
            var original = mock(BacktestConfig.class);
            when(repository.findByBacktestConfigId("bcf")).thenReturn(Optional.of(original));
            when(repository.bindStrategyVersion(
                    eq("bcf"), eq("sv"), anyString(), anyString(), any()))
                    .thenReturn(true);
            var shared = new ObjectMapper();
            var snapshot =
                    new StrategyVersionSnapshotView(
                            "sv", "parent", 1, "version", status, RAW, RAW, RAW, "digest");
            var service =
                    new BacktestConfigService(
                            repository,
                            id -> Optional.of(snapshot),
                            mock(ResearchConfigService.class),
                            shared,
                            Clock.systemUTC());
            assertEquals(original, service.bindStrategyVersion("bcf", "sv"));
            var full = ArgumentCaptor.forClass(String.class);
            var parameters = ArgumentCaptor.forClass(String.class);
            verify(repository)
                    .bindStrategyVersion(
                            eq("bcf"), eq("sv"), full.capture(), parameters.capture(), any());
            var reader = shared.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
            JsonNode sealed = reader.readTree(full.getValue());
            for (String key :
                    List.of("paramSnapshotJson", "configSnapshotJson", "sourceSnapshotJson"))
                assertExact(sealed.get(key));
            assertExact(reader.readTree(parameters.getValue()));
            assertEquals("digest", sealed.get("checksum").asText());
            assertEquals(status, sealed.get("status").asText());
            assertTrue(shared.readTree("1.234567890123456789").isDouble());
        }
    }

    @Test
    void preservesConfigurationExecutionAndEvaluationSnapshotsAtCreation() throws Exception {
        var repository = mock(BacktestConfigRepository.class);
        var researchService = mock(ResearchConfigService.class);
        var research =
                new ResearchConfig(
                        "rcf",
                        "str",
                        "{}",
                        "research",
                        null,
                        "{}",
                        "{}",
                        "{}",
                        Instant.EPOCH,
                        Instant.EPOCH,
                        "ACTIVE",
                        null,
                        null,
                        null);
        when(researchService.getByResearchConfigId("rcf")).thenReturn(research);
        var mapper = new ObjectMapper();
        var service =
                new BacktestConfigService(repository, researchService, mapper, Clock.systemUTC());
        Instant start = Instant.parse("2026-10-01T00:00:00Z");
        var result =
                service.create(
                        new BacktestConfigCreateRequest(
                                "rcf",
                                "precision",
                                null,
                                start,
                                start.plusSeconds(60),
                                new BigDecimal("100.123456789012345678"),
                                RAW,
                                RAW));
        var reader = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        assertExact(reader.readTree(result.executionSpec()));
        assertExact(reader.readTree(result.evaluationSpec()));
        for (String json : List.of(result.configSnapshot(), result.configSnapshotJson())) {
            JsonNode snapshot = reader.readTree(json);
            assertExact(snapshot.get("executionSpec"));
            assertExact(snapshot.get("evaluationSpec"));
        }
        assertEquals(new BigDecimal("100.123456789012345678"), result.initialCapital());
        verify(repository).insert(result);
    }

    @Test
    void rejectsInvalidSnapshotBeforeBindingAndRetainsTheOriginalConfig() {
        var repository = mock(BacktestConfigRepository.class);
        when(repository.findByBacktestConfigId("bcf"))
                .thenReturn(Optional.of(mock(BacktestConfig.class)));
        var snapshot =
                new StrategyVersionSnapshotView(
                        "sv", "parent", 1, "version", "DRAFT", "{bad-json", "{}", "{}", "digest");
        var service =
                new BacktestConfigService(
                        repository,
                        id -> Optional.of(snapshot),
                        mock(ResearchConfigService.class),
                        new ObjectMapper(),
                        Clock.systemUTC());
        assertThrows(
                IllegalArgumentException.class, () -> service.bindStrategyVersion("bcf", "sv"));
        verify(repository, never())
                .bindStrategyVersion(anyString(), anyString(), anyString(), anyString(), any());
    }

    private static void assertExact(JsonNode node) {
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
}
