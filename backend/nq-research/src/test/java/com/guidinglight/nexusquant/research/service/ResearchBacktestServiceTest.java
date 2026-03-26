package com.guidinglight.nexusquant.research.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.research.model.BacktestConfig;
import com.guidinglight.nexusquant.research.model.BacktestRun;
import com.guidinglight.nexusquant.research.model.BacktestRunStatus;
import com.guidinglight.nexusquant.research.model.ResearchConfig;
import com.guidinglight.nexusquant.research.model.SourceStrategySnapshot;
import com.guidinglight.nexusquant.research.port.BacktestConfigRepository;
import com.guidinglight.nexusquant.research.port.BacktestRunRepository;
import com.guidinglight.nexusquant.research.port.ResearchConfigRepository;
import com.guidinglight.nexusquant.research.port.SourceStrategySnapshotRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ResearchBacktestServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-24T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void shouldCreateResearchBacktestConfigAndRunWithSnapshots() {
        InMemoryResearchConfigRepository researchConfigRepository = new InMemoryResearchConfigRepository();
        InMemoryBacktestConfigRepository backtestConfigRepository = new InMemoryBacktestConfigRepository();
        InMemoryBacktestRunRepository backtestRunRepository = new InMemoryBacktestRunRepository();
        StubSourceStrategySnapshotRepository sourceStrategySnapshotRepository = new StubSourceStrategySnapshotRepository();

        ResearchConfigService researchConfigService = new ResearchConfigService(
                researchConfigRepository,
                sourceStrategySnapshotRepository,
                objectMapper,
                fixedClock
        );
        BacktestConfigService backtestConfigService = new BacktestConfigService(
                backtestConfigRepository,
                researchConfigService,
                objectMapper,
                fixedClock
        );
        BacktestRunService backtestRunService = new BacktestRunService(
                backtestRunRepository,
                backtestConfigService,
                researchConfigService,
                fixedClock
        );

        ResearchConfig researchConfig = researchConfigService.create(new ResearchConfigCreateRequest(
                "str-demo-1",
                "Demo Research",
                "用于验证 GateF-1 最小骨架",
                "{\"type\":\"object\"}",
                "{\"window\":20}",
                "{\"venue\":\"BINANCE\",\"symbol\":\"BTCUSDT\"}"
        ));
        BacktestConfig backtestConfig = backtestConfigService.create(new BacktestConfigCreateRequest(
                researchConfig.researchConfigId(),
                "BTC Backtest",
                "验证最小创建链路",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-12-31T00:00:00Z"),
                new BigDecimal("100000"),
                "{\"slippageBps\":5}",
                "{\"benchmark\":\"BTC\"}"
        ));
        BacktestRun backtestRun = backtestRunService.create(new BacktestRunStartRequest(
                backtestConfig.backtestConfigId()
        ));

        assertNotNull(researchConfig.researchConfigId());
        assertEquals("str-demo-1", researchConfig.sourceStrategyId());
        assertEquals(researchConfig.researchConfigId(), backtestConfig.researchConfigId());
        assertEquals(backtestConfig.backtestConfigId(), backtestRun.backtestConfigId());
        assertEquals(researchConfig.researchConfigId(), backtestRun.researchConfigId());
        assertEquals(BacktestRunStatus.CREATED, backtestRun.status());
        assertEquals("{}", backtestRun.summaryJson());
        assertNotNull(backtestRun.strategySnapshot());
        assertNotNull(backtestRun.backtestConfigSnapshot());
        assertEquals(1, backtestRunService.list(researchConfig.researchConfigId(), null).size());
    }

    @Test
    void shouldRejectMissingSourceStrategy() {
        ResearchConfigService researchConfigService = new ResearchConfigService(
                new InMemoryResearchConfigRepository(),
                strategyId -> Optional.empty(),
                objectMapper,
                fixedClock
        );

        assertThrows(IllegalArgumentException.class, () -> researchConfigService.create(new ResearchConfigCreateRequest(
                "missing",
                "Missing Strategy",
                null,
                "{}",
                "{}",
                "{}"
        )));
    }

    @Test
    void shouldRejectInvalidBacktestWindow() {
        ResearchConfigService researchConfigService = new ResearchConfigService(
                new InMemoryResearchConfigRepository(),
                new StubSourceStrategySnapshotRepository(),
                objectMapper,
                fixedClock
        );
        ResearchConfig researchConfig = researchConfigService.create(new ResearchConfigCreateRequest(
                "str-demo-1",
                "Demo Research",
                null,
                "{}",
                "{}",
                "{}"
        ));
        BacktestConfigService backtestConfigService = new BacktestConfigService(
                new InMemoryBacktestConfigRepository(),
                researchConfigService,
                objectMapper,
                fixedClock
        );

        assertThrows(IllegalArgumentException.class, () -> backtestConfigService.create(new BacktestConfigCreateRequest(
                researchConfig.researchConfigId(),
                "Invalid Window",
                null,
                Instant.parse("2025-01-02T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"),
                new BigDecimal("1000"),
                "{}",
                "{}"
        )));
    }

    private static final class InMemoryResearchConfigRepository implements ResearchConfigRepository {

        private final Map<String, ResearchConfig> storage = new LinkedHashMap<>();

        @Override
        public void insert(ResearchConfig researchConfig) {
            storage.put(researchConfig.researchConfigId(), researchConfig);
        }

        @Override
        public Optional<ResearchConfig> findByResearchConfigId(String researchConfigId) {
            return Optional.ofNullable(storage.get(researchConfigId));
        }

        @Override
        public List<ResearchConfig> listAll() {
            return new ArrayList<>(storage.values());
        }
    }

    private static final class InMemoryBacktestConfigRepository implements BacktestConfigRepository {

        private final Map<String, BacktestConfig> storage = new LinkedHashMap<>();

        @Override
        public void insert(BacktestConfig backtestConfig) {
            storage.put(backtestConfig.backtestConfigId(), backtestConfig);
        }

        @Override
        public Optional<BacktestConfig> findByBacktestConfigId(String backtestConfigId) {
            return Optional.ofNullable(storage.get(backtestConfigId));
        }

        @Override
        public List<BacktestConfig> listAll() {
            return new ArrayList<>(storage.values());
        }

        @Override
        public List<BacktestConfig> listByResearchConfigId(String researchConfigId) {
            return storage.values().stream()
                    .filter(item -> item.researchConfigId().equals(researchConfigId))
                    .toList();
        }
    }

    private static final class InMemoryBacktestRunRepository implements BacktestRunRepository {

        private final Map<String, BacktestRun> storage = new LinkedHashMap<>();

        @Override
        public void insert(BacktestRun backtestRun) {
            storage.put(backtestRun.backtestRunId(), backtestRun);
        }

        @Override
        public Optional<BacktestRun> findByBacktestRunId(String backtestRunId) {
            return Optional.ofNullable(storage.get(backtestRunId));
        }

        @Override
        public List<BacktestRun> list(String researchConfigId, String backtestConfigId) {
            return storage.values().stream()
                    .filter(item -> researchConfigId == null || item.researchConfigId().equals(researchConfigId))
                    .filter(item -> backtestConfigId == null || item.backtestConfigId().equals(backtestConfigId))
                    .toList();
        }

        @Override
        public boolean updateExecution(
                String backtestRunId,
                BacktestRunStatus status,
                Instant startedAt,
                Instant finishedAt,
                String failureCode,
                String failureMessage,
                String summaryJson,
                Instant updatedAt
        ) {
            BacktestRun current = storage.get(backtestRunId);
            if (current == null) {
                return false;
            }
            storage.put(backtestRunId, new BacktestRun(
                    current.backtestRunId(),
                    current.backtestConfigId(),
                    current.researchConfigId(),
                    current.sourceStrategyId(),
                    current.strategySnapshot(),
                    current.backtestConfigSnapshot(),
                    status,
                    current.requestedAt(),
                    startedAt,
                    finishedAt,
                    failureCode,
                    failureMessage,
                    summaryJson == null ? current.summaryJson() : summaryJson,
                    current.createdAt(),
                    updatedAt
            ));
            return true;
        }
    }

    private static final class StubSourceStrategySnapshotRepository implements SourceStrategySnapshotRepository {

        @Override
        public Optional<SourceStrategySnapshot> findByStrategyId(String strategyId) {
            if (!"str-demo-1".equals(strategyId)) {
                return Optional.empty();
            }
            return Optional.of(new SourceStrategySnapshot(
                    "str-demo-1",
                    "demo-grid",
                    "Demo Grid",
                    "GRID",
                    "BINANCE",
                    1001L,
                    "SIM",
                    true,
                    "{\"grid\":\"narrow\"}",
                    3
            ));
        }
    }
}
