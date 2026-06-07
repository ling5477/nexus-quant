package com.guidinglight.nexusquant.research.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.domain.SourceStrategySnapshot;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;
import com.guidinglight.nexusquant.research.domain.port.ResearchConfigRepository;
import com.guidinglight.nexusquant.research.domain.port.SourceStrategySnapshotRepository;

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

    @Test
    void shouldHideArchivedConfigsFromDefaultListsButKeepIdLookup() {
        InMemoryResearchConfigRepository researchConfigRepository = new InMemoryResearchConfigRepository();
        InMemoryBacktestConfigRepository backtestConfigRepository = new InMemoryBacktestConfigRepository();
        ResearchConfigService researchConfigService = new ResearchConfigService(
                researchConfigRepository,
                new StubSourceStrategySnapshotRepository(),
                objectMapper,
                fixedClock
        );
        BacktestConfigService backtestConfigService = new BacktestConfigService(
                backtestConfigRepository,
                researchConfigService,
                objectMapper,
                fixedClock
        );

        ResearchConfig activeResearch = researchConfigService.create(new ResearchConfigCreateRequest(
                "str-demo-1",
                "Active Research",
                null,
                "{}",
                "{}",
                "{}"
        ));
        ResearchConfig disabledResearch = researchConfig(
                "rcf-disabled",
                ResearchConfig.STATUS_DISABLED,
                null
        );
        ResearchConfig archivedResearch = researchConfig(
                "rcf-archived",
                ResearchConfig.STATUS_ARCHIVED,
                fixedClock.instant()
        );
        researchConfigRepository.insert(disabledResearch);
        researchConfigRepository.insert(archivedResearch);

        List<String> visibleResearchIds = researchConfigService.listAll().stream()
                .map(ResearchConfig::researchConfigId)
                .toList();
        assertTrue(visibleResearchIds.contains(activeResearch.researchConfigId()));
        assertTrue(visibleResearchIds.contains(disabledResearch.researchConfigId()));
        assertFalse(visibleResearchIds.contains(archivedResearch.researchConfigId()));
        assertEquals(
                archivedResearch.researchConfigId(),
                researchConfigService.getByResearchConfigId(archivedResearch.researchConfigId()).researchConfigId()
        );
        assertEquals(3, researchConfigRepository.list(null, true).size());

        BacktestConfig activeBacktest = backtestConfigService.create(new BacktestConfigCreateRequest(
                activeResearch.researchConfigId(),
                "Active Backtest",
                null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-31T00:00:00Z"),
                new BigDecimal("100000"),
                "{}",
                "{}"
        ));
        BacktestConfig disabledBacktest = backtestConfig(
                "bcf-disabled",
                activeResearch.researchConfigId(),
                BacktestConfig.STATUS_DISABLED,
                null
        );
        BacktestConfig archivedBacktest = backtestConfig(
                "bcf-archived",
                activeResearch.researchConfigId(),
                BacktestConfig.STATUS_ARCHIVED,
                fixedClock.instant()
        );
        backtestConfigRepository.insert(disabledBacktest);
        backtestConfigRepository.insert(archivedBacktest);

        List<String> visibleBacktestIds = backtestConfigService.list(null).stream()
                .map(BacktestConfig::backtestConfigId)
                .toList();
        assertTrue(visibleBacktestIds.contains(activeBacktest.backtestConfigId()));
        assertTrue(visibleBacktestIds.contains(disabledBacktest.backtestConfigId()));
        assertFalse(visibleBacktestIds.contains(archivedBacktest.backtestConfigId()));
        assertEquals(
                archivedBacktest.backtestConfigId(),
                backtestConfigService.getByBacktestConfigId(archivedBacktest.backtestConfigId()).backtestConfigId()
        );
        assertEquals(3, backtestConfigRepository.list(null, true).size());
    }

    @Test
    void shouldRejectNonActiveConfigsWhenCreatingNewBacktestRun() {
        InMemoryResearchConfigRepository researchConfigRepository = new InMemoryResearchConfigRepository();
        InMemoryBacktestConfigRepository backtestConfigRepository = new InMemoryBacktestConfigRepository();
        InMemoryBacktestRunRepository backtestRunRepository = new InMemoryBacktestRunRepository();
        ResearchConfigService researchConfigService = new ResearchConfigService(
                researchConfigRepository,
                new StubSourceStrategySnapshotRepository(),
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
        ResearchConfig activeResearch = researchConfigService.create(new ResearchConfigCreateRequest(
                "str-demo-1",
                "Active Research",
                null,
                "{}",
                "{}",
                "{}"
        ));
        ResearchConfig disabledResearch = researchConfig(
                "rcf-disabled-run-source",
                ResearchConfig.STATUS_DISABLED,
                null
        );
        researchConfigRepository.insert(disabledResearch);
        BacktestConfig disabledBacktest = backtestConfig(
                "bcf-disabled-run",
                activeResearch.researchConfigId(),
                BacktestConfig.STATUS_DISABLED,
                null
        );
        BacktestConfig archivedBacktest = backtestConfig(
                "bcf-archived-run",
                activeResearch.researchConfigId(),
                BacktestConfig.STATUS_ARCHIVED,
                fixedClock.instant()
        );
        BacktestConfig activeBacktestWithDisabledResearch = backtestConfig(
                "bcf-disabled-research-run",
                disabledResearch.researchConfigId(),
                BacktestConfig.STATUS_ACTIVE,
                null
        );
        backtestConfigRepository.insert(disabledBacktest);
        backtestConfigRepository.insert(archivedBacktest);
        backtestConfigRepository.insert(activeBacktestWithDisabledResearch);

        assertThrows(IllegalStateException.class, () -> backtestRunService.create(new BacktestRunStartRequest(
                disabledBacktest.backtestConfigId()
        )));
        assertThrows(IllegalStateException.class, () -> backtestRunService.create(new BacktestRunStartRequest(
                archivedBacktest.backtestConfigId()
        )));
        assertThrows(IllegalStateException.class, () -> backtestRunService.create(new BacktestRunStartRequest(
                activeBacktestWithDisabledResearch.backtestConfigId()
        )));
    }

    @Test
    void shouldRejectNonActiveResearchConfigWhenCreatingBacktestConfig() {
        InMemoryResearchConfigRepository researchConfigRepository = new InMemoryResearchConfigRepository();
        ResearchConfigService researchConfigService = new ResearchConfigService(
                researchConfigRepository,
                new StubSourceStrategySnapshotRepository(),
                objectMapper,
                fixedClock
        );
        BacktestConfigService backtestConfigService = new BacktestConfigService(
                new InMemoryBacktestConfigRepository(),
                researchConfigService,
                objectMapper,
                fixedClock
        );
        ResearchConfig archivedResearch = researchConfig(
                "rcf-archived-create-source",
                ResearchConfig.STATUS_ARCHIVED,
                fixedClock.instant()
        );
        researchConfigRepository.insert(archivedResearch);

        assertThrows(IllegalStateException.class, () -> backtestConfigService.create(new BacktestConfigCreateRequest(
                archivedResearch.researchConfigId(),
                "Blocked Backtest",
                null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-31T00:00:00Z"),
                new BigDecimal("100000"),
                "{}",
                "{}"
        )));
    }

    @Test
    void shouldArchiveConfigsAndKeepHistoricalRunTraceability() {
        InMemoryResearchConfigRepository researchConfigRepository = new InMemoryResearchConfigRepository();
        InMemoryBacktestConfigRepository backtestConfigRepository = new InMemoryBacktestConfigRepository();
        InMemoryBacktestRunRepository backtestRunRepository = new InMemoryBacktestRunRepository();
        ResearchConfigService researchConfigService = new ResearchConfigService(
                researchConfigRepository,
                new StubSourceStrategySnapshotRepository(),
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
                "Archive Research",
                null,
                "{}",
                "{}",
                "{}"
        ));
        BacktestConfig backtestConfig = backtestConfigService.create(new BacktestConfigCreateRequest(
                researchConfig.researchConfigId(),
                "Archive Backtest",
                null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-31T00:00:00Z"),
                new BigDecimal("100000"),
                "{}",
                "{}"
        ));
        BacktestRun historicalRun = backtestRunService.create(new BacktestRunStartRequest(
                backtestConfig.backtestConfigId()
        ));

        BacktestConfig archivedBacktest = backtestConfigService.archive(
                backtestConfig.backtestConfigId(),
                "operator-1",
                "retired after benchmark update"
        );
        BacktestConfig repeatedBacktestArchive = backtestConfigService.archive(
                backtestConfig.backtestConfigId(),
                "operator-2",
                "second archive should be idempotent"
        );
        ResearchConfig archivedResearch = researchConfigService.archive(
                researchConfig.researchConfigId(),
                "operator-1",
                "retired after benchmark update"
        );

        assertEquals(BacktestConfig.STATUS_ARCHIVED, archivedBacktest.status());
        assertEquals(fixedClock.instant(), archivedBacktest.archivedAt());
        assertEquals("operator-1", archivedBacktest.archivedBy());
        assertEquals("retired after benchmark update", archivedBacktest.archiveReason());
        assertEquals(archivedBacktest.archivedBy(), repeatedBacktestArchive.archivedBy());
        assertEquals(archivedBacktest.archiveReason(), repeatedBacktestArchive.archiveReason());
        assertEquals(ResearchConfig.STATUS_ARCHIVED, archivedResearch.status());
        assertFalse(backtestConfigService.list(null).stream()
                .anyMatch(item -> item.backtestConfigId().equals(backtestConfig.backtestConfigId())));
        assertFalse(researchConfigService.listAll().stream()
                .anyMatch(item -> item.researchConfigId().equals(researchConfig.researchConfigId())));
        assertEquals(
                backtestConfig.backtestConfigId(),
                backtestConfigService.getByBacktestConfigId(backtestConfig.backtestConfigId()).backtestConfigId()
        );
        assertEquals(
                researchConfig.researchConfigId(),
                researchConfigService.getByResearchConfigId(researchConfig.researchConfigId()).researchConfigId()
        );
        assertEquals(historicalRun.backtestRunId(), backtestRunService.getByBacktestRunId(
                historicalRun.backtestRunId()
        ).backtestRunId());
        assertThrows(IllegalStateException.class, () -> backtestRunService.create(new BacktestRunStartRequest(
                backtestConfig.backtestConfigId()
        )));
    }

    @Test
    void shouldRejectSensitiveArchiveReason() {
        ResearchConfigService researchConfigService = new ResearchConfigService(
                new InMemoryResearchConfigRepository(),
                new StubSourceStrategySnapshotRepository(),
                objectMapper,
                fixedClock
        );
        ResearchConfig researchConfig = researchConfigService.create(new ResearchConfigCreateRequest(
                "str-demo-1",
                "Sensitive Reason Research",
                null,
                "{}",
                "{}",
                "{}"
        ));

        assertThrows(IllegalArgumentException.class, () -> researchConfigService.archive(
                researchConfig.researchConfigId(),
                "operator-1",
                "contains api secret"
        ));
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
        public boolean archive(String researchConfigId, Instant archivedAt, String archivedBy, String archiveReason) {
            ResearchConfig current = storage.get(researchConfigId);
            if (current == null || current.isArchived()) {
                return false;
            }
            storage.put(researchConfigId, current.archive(archivedAt, archivedBy, archiveReason));
            return true;
        }

        @Override
        public List<ResearchConfig> listAll() {
            return storage.values().stream()
                    .filter(item -> !item.isArchived())
                    .toList();
        }

        @Override
        public List<ResearchConfig> listAllIncludingArchived() {
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
        public boolean archive(String backtestConfigId, Instant archivedAt, String archivedBy, String archiveReason) {
            BacktestConfig current = storage.get(backtestConfigId);
            if (current == null || current.isArchived()) {
                return false;
            }
            storage.put(backtestConfigId, current.archive(archivedAt, archivedBy, archiveReason));
            return true;
        }

        @Override
        public List<BacktestConfig> listAll() {
            return storage.values().stream()
                    .filter(item -> !item.isArchived())
                    .toList();
        }

        @Override
        public List<BacktestConfig> listAllIncludingArchived() {
            return new ArrayList<>(storage.values());
        }

        @Override
        public List<BacktestConfig> listByResearchConfigId(String researchConfigId) {
            return storage.values().stream()
                    .filter(item -> item.researchConfigId().equals(researchConfigId))
                    .filter(item -> !item.isArchived())
                    .toList();
        }

        @Override
        public List<BacktestConfig> listByResearchConfigIdIncludingArchived(String researchConfigId) {
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

    private ResearchConfig researchConfig(String researchConfigId, String status, Instant archivedAt) {
        return new ResearchConfig(
                researchConfigId,
                "str-demo-1",
                "{\"strategyType\":\"BUY_AND_HOLD_FIXTURE\"}",
                researchConfigId,
                null,
                "{}",
                "{}",
                "{}",
                fixedClock.instant(),
                fixedClock.instant(),
                status,
                archivedAt,
                archivedAt == null ? null : "test-operator",
                archivedAt == null ? null : "retired from default selection"
        );
    }

    private BacktestConfig backtestConfig(
            String backtestConfigId,
            String researchConfigId,
            String status,
            Instant archivedAt
    ) {
        return new BacktestConfig(
                backtestConfigId,
                researchConfigId,
                backtestConfigId,
                null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-31T00:00:00Z"),
                new BigDecimal("100000"),
                "{}",
                "{}",
                null,
                "{}",
                "{}",
                "{\"startTime\":\"2025-01-01T00:00:00Z\",\"endTime\":\"2025-01-31T00:00:00Z\",\"initialCapital\":\"100000\",\"executionSpec\":{}}",
                null,
                "{}",
                "{\"startTime\":\"2025-01-01T00:00:00Z\",\"endTime\":\"2025-01-31T00:00:00Z\",\"initialCapital\":\"100000\",\"executionSpec\":{}}",
                fixedClock.instant(),
                fixedClock.instant(),
                status,
                archivedAt,
                archivedAt == null ? null : "test-operator",
                archivedAt == null ? null : "retired from default selection"
        );
    }
}



