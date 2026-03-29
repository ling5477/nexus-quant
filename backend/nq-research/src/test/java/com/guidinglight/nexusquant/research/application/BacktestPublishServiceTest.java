package com.guidinglight.nexusquant.research.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationView;
import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.application.command.BacktestPublishRequest;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.ExecutionStrategyDefinitionDraft;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.domain.SourceStrategySnapshot;
import com.guidinglight.nexusquant.research.application.backtest.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.domain.eval.port.BacktestEvaluationQueryPort;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;
import com.guidinglight.nexusquant.research.domain.port.ExecutionStrategyDefinitionWriter;
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

class BacktestPublishServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-25T01:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldPublishSucceededRunAndRemainIdempotent() {
        Scenario scenario = createScenario(BacktestRunStatus.SUCCEEDED, succeededEvaluation());

        BacktestPublishRecord first = scenario.service.publish(new BacktestPublishRequest(scenario.run.backtestRunId(), "Published Demo"));
        BacktestPublishRecord second = scenario.service.publish(new BacktestPublishRequest(scenario.run.backtestRunId(), "Ignored"));

        assertEquals(PublishStatus.SUCCEEDED, first.publishStatus());
        assertNotNull(first.targetStrategyDefinitionId());
        assertEquals(first.publishRecordId(), second.publishRecordId());
        assertEquals(1, scenario.publishRecordRepository.storage.size());
        assertEquals(1, scenario.executionWriter.insertCount);
    }

    @Test
    void shouldFailWhenRunOrEvaluationNotEligible() {
        Scenario failedRunScenario = createScenario(BacktestRunStatus.FAILED, succeededEvaluation());
        assertThrows(IllegalStateException.class, () -> failedRunScenario.service.publish(
                new BacktestPublishRequest(failedRunScenario.run.backtestRunId(), null)
        ));
        assertEquals(PublishStatus.FAILED, failedRunScenario.publishRecordRepository.storage.values().iterator().next().publishStatus());

        Scenario missingEvaluationScenario = createScenario(BacktestRunStatus.SUCCEEDED, null);
        assertThrows(IllegalStateException.class, () -> missingEvaluationScenario.service.publish(
                new BacktestPublishRequest(missingEvaluationScenario.run.backtestRunId(), null)
        ));

        Scenario failedEvaluationScenario = createScenario(BacktestRunStatus.SUCCEEDED, failedEvaluation());
        assertThrows(IllegalStateException.class, () -> failedEvaluationScenario.service.publish(
                new BacktestPublishRequest(failedEvaluationScenario.run.backtestRunId(), null)
        ));
    }

    private Scenario createScenario(BacktestRunStatus runStatus, BacktestEvaluationView evaluationView) {
        InMemoryResearchConfigRepository researchConfigRepository = new InMemoryResearchConfigRepository();
        InMemoryBacktestConfigRepository backtestConfigRepository = new InMemoryBacktestConfigRepository();
        InMemoryBacktestRunRepository backtestRunRepository = new InMemoryBacktestRunRepository();
        InMemoryBacktestPublishRecordRepository publishRecordRepository = new InMemoryBacktestPublishRecordRepository();
        InMemoryExecutionStrategyDefinitionWriter executionWriter = new InMemoryExecutionStrategyDefinitionWriter();

        SourceStrategySnapshotRepository sourceStrategySnapshotRepository = strategyId -> Optional.of(new SourceStrategySnapshot(
                strategyId,
                "demo-publish",
                "Demo Publish",
                "BUY_AND_HOLD_FIXTURE",
                "BINANCE",
                1001L,
                "SIM",
                true,
                "{\"strategy\":\"fixture\"}",
                1
        ));
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
                "str-publish-1",
                "Publish Research",
                null,
                "{}",
                "{}",
                "{\"symbol\":\"BTCUSDT\",\"interval\":\"1m\"}"
        ));
        BacktestConfig backtestConfig = backtestConfigService.create(new BacktestConfigCreateRequest(
                researchConfig.researchConfigId(),
                "Publish Backtest",
                null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:59Z"),
                new BigDecimal("100000"),
                "{}",
                "{}"
        ));
        BacktestRun run = backtestRunService.create(new BacktestRunStartRequest(backtestConfig.backtestConfigId()));
        backtestRunRepository.updateExecution(
                run.backtestRunId(),
                runStatus,
                fixedClock.instant(),
                fixedClock.instant(),
                null,
                null,
                "{\"barCount\":2}",
                fixedClock.instant()
        );

        BacktestPublishService service = new BacktestPublishService(
                backtestRunService,
                researchConfigService,
                backtestConfigService,
                backtestRunId -> Optional.ofNullable(evaluationView),
                publishRecordRepository,
                new ResearchToExecutionMapper(objectMapper),
                executionWriter,
                objectMapper,
                fixedClock
        );
        return new Scenario(service, publishRecordRepository, executionWriter, run);
    }

    private BacktestEvaluationView succeededEvaluation() {
        return new BacktestEvaluationView(
                "eval-1",
                "brn-1",
                "SUCCEEDED",
                fixedClock.instant(),
                new BigDecimal("99927.76"),
                new BigDecimal("-72.24"),
                new BigDecimal("-0.0007224"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2,
                2,
                "{\"evaluationStatus\":\"SUCCEEDED\"}",
                null,
                null
        );
    }

    private BacktestEvaluationView failedEvaluation() {
        return new BacktestEvaluationView(
                "eval-2",
                "brn-2",
                "FAILED",
                fixedClock.instant(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "{}",
                "EVALUATION_FAILED",
                "failed"
        );
    }

    private record Scenario(
            BacktestPublishService service,
            InMemoryBacktestPublishRecordRepository publishRecordRepository,
            InMemoryExecutionStrategyDefinitionWriter executionWriter,
            BacktestRun run
    ) {
    }

    private static final class InMemoryResearchConfigRepository implements ResearchConfigRepository {
        private final Map<String, ResearchConfig> storage = new LinkedHashMap<>();
        @Override public void insert(ResearchConfig researchConfig) { storage.put(researchConfig.researchConfigId(), researchConfig); }
        @Override public Optional<ResearchConfig> findByResearchConfigId(String researchConfigId) { return Optional.ofNullable(storage.get(researchConfigId)); }
        @Override public List<ResearchConfig> listAll() { return new ArrayList<>(storage.values()); }
    }

    private static final class InMemoryBacktestConfigRepository implements BacktestConfigRepository {
        private final Map<String, BacktestConfig> storage = new LinkedHashMap<>();
        @Override public void insert(BacktestConfig backtestConfig) { storage.put(backtestConfig.backtestConfigId(), backtestConfig); }
        @Override public Optional<BacktestConfig> findByBacktestConfigId(String backtestConfigId) { return Optional.ofNullable(storage.get(backtestConfigId)); }
        @Override public List<BacktestConfig> listAll() { return new ArrayList<>(storage.values()); }
        @Override public List<BacktestConfig> listByResearchConfigId(String researchConfigId) { return storage.values().stream().filter(item -> item.researchConfigId().equals(researchConfigId)).toList(); }
    }

    private static final class InMemoryBacktestRunRepository implements BacktestRunRepository {
        private final Map<String, BacktestRun> storage = new LinkedHashMap<>();
        @Override public void insert(BacktestRun backtestRun) { storage.put(backtestRun.backtestRunId(), backtestRun); }
        @Override public Optional<BacktestRun> findByBacktestRunId(String backtestRunId) { return Optional.ofNullable(storage.get(backtestRunId)); }
        @Override public List<BacktestRun> list(String researchConfigId, String backtestConfigId) { return storage.values().stream().toList(); }
        @Override public boolean updateExecution(String backtestRunId, BacktestRunStatus status, Instant startedAt, Instant finishedAt, String failureCode, String failureMessage, String summaryJson, Instant updatedAt) {
            BacktestRun current = storage.get(backtestRunId);
            if (current == null) { return false; }
            storage.put(backtestRunId, new BacktestRun(
                    current.backtestRunId(), current.backtestConfigId(), current.researchConfigId(), current.sourceStrategyId(),
                    current.strategySnapshot(), current.backtestConfigSnapshot(), status, current.requestedAt(), startedAt, finishedAt,
                    failureCode, failureMessage, summaryJson, current.createdAt(), updatedAt
            ));
            return true;
        }
    }

    private static final class InMemoryBacktestPublishRecordRepository implements BacktestPublishRecordRepository {
        private final Map<String, BacktestPublishRecord> storage = new LinkedHashMap<>();
        @Override public void upsert(BacktestPublishRecord record) { storage.put(record.backtestRunId(), record); }
        @Override public Optional<BacktestPublishRecord> findByBacktestRunId(String backtestRunId) { return Optional.ofNullable(storage.get(backtestRunId)); }
    }

    private static final class InMemoryExecutionStrategyDefinitionWriter implements ExecutionStrategyDefinitionWriter {
        private int insertCount = 0;
        @Override public String publish(ExecutionStrategyDefinitionDraft draft) { insertCount++; return draft.targetStrategyDefinitionId(); }
    }
}



