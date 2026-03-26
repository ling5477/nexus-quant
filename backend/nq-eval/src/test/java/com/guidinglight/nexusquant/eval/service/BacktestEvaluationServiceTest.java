package com.guidinglight.nexusquant.eval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.backtest.model.SimOrder;
import com.guidinglight.nexusquant.backtest.model.SimOrderStatus;
import com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot;
import com.guidinglight.nexusquant.backtest.model.SimPosition;
import com.guidinglight.nexusquant.backtest.model.SimTrade;
import com.guidinglight.nexusquant.eval.model.BacktestEvaluationReport;
import com.guidinglight.nexusquant.eval.model.EvaluationStatus;
import com.guidinglight.nexusquant.eval.port.BacktestEvaluationReportRepository;
import com.guidinglight.nexusquant.eval.port.SimOrderQueryRepository;
import com.guidinglight.nexusquant.eval.port.SimPnlSnapshotQueryRepository;
import com.guidinglight.nexusquant.eval.port.SimPositionQueryRepository;
import com.guidinglight.nexusquant.eval.port.SimTradeQueryRepository;
import com.guidinglight.nexusquant.research.model.BacktestConfig;
import com.guidinglight.nexusquant.research.model.BacktestRun;
import com.guidinglight.nexusquant.research.model.BacktestRunStatus;
import com.guidinglight.nexusquant.research.model.ResearchConfig;
import com.guidinglight.nexusquant.research.model.SourceStrategySnapshot;
import com.guidinglight.nexusquant.research.port.BacktestConfigRepository;
import com.guidinglight.nexusquant.research.port.BacktestRunRepository;
import com.guidinglight.nexusquant.research.port.ResearchConfigRepository;
import com.guidinglight.nexusquant.research.port.SourceStrategySnapshotRepository;
import com.guidinglight.nexusquant.research.service.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.service.BacktestConfigService;
import com.guidinglight.nexusquant.research.service.BacktestRunService;
import com.guidinglight.nexusquant.research.service.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.service.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.service.ResearchConfigService;

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

class BacktestEvaluationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-24T20:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldEvaluateSucceededRunAndUpsertReport() {
        Scenario scenario = createScenario(BacktestRunStatus.SUCCEEDED, new BigDecimal("100000"), true);

        BacktestEvaluationReport first = scenario.service.evaluate(scenario.run.backtestRunId());
        BacktestEvaluationReport second = scenario.service.evaluate(scenario.run.backtestRunId());

        assertEquals(EvaluationStatus.SUCCEEDED, first.evaluationStatus());
        assertEquals(new BigDecimal("99927.760000000000000000"), first.finalEquity());
        assertEquals(new BigDecimal("-72.240000000000000000"), first.netPnl());
        assertEquals(new BigDecimal("-0.000722400000000000"), first.totalReturnRate());
        assertEquals(new BigDecimal("86.120000000000000000"), first.totalFee());
        assertEquals(new BigDecimal("86.120000000000000000"), first.totalSlippage());
        assertEquals(2, first.orderCount());
        assertEquals(2, first.tradeCount());
        assertEquals(0, first.winningTradeCount());
        assertEquals(1, first.losingTradeCount());
        assertEquals(new BigDecimal("0E-18"), first.winRate());
        assertEquals(1, scenario.reportRepository.storage.size());
        assertEquals(second.backtestRunId(), scenario.run.backtestRunId());
    }

    @Test
    void shouldRejectEvaluateForNonSucceededRun() {
        Scenario scenario = createScenario(BacktestRunStatus.FAILED, new BigDecimal("100000"), true);

        assertThrows(IllegalStateException.class, () -> scenario.service.evaluate(scenario.run.backtestRunId()));
        assertEquals(EvaluationStatus.FAILED, scenario.reportRepository.findByBacktestRunId(scenario.run.backtestRunId()).orElseThrow().evaluationStatus());
    }

    @Test
    void shouldRejectWhenSnapshotsMissingOrInitialCapitalInvalid() {
        Scenario missingSnapshotScenario = createScenario(BacktestRunStatus.SUCCEEDED, new BigDecimal("100000"), false);
        assertThrows(IllegalStateException.class, () -> missingSnapshotScenario.service.evaluate(missingSnapshotScenario.run.backtestRunId()));

        Scenario invalidCapitalScenario = createScenario(BacktestRunStatus.SUCCEEDED, BigDecimal.ZERO, true);
        assertThrows(IllegalStateException.class, () -> invalidCapitalScenario.service.evaluate(invalidCapitalScenario.run.backtestRunId()));
    }

    private Scenario createScenario(BacktestRunStatus runStatus, BigDecimal initialCapital, boolean withSnapshots) {
        InMemoryResearchConfigRepository researchConfigRepository = new InMemoryResearchConfigRepository();
        InMemoryBacktestConfigRepository backtestConfigRepository = new InMemoryBacktestConfigRepository();
        InMemoryBacktestRunRepository backtestRunRepository = new InMemoryBacktestRunRepository();
        SourceStrategySnapshotRepository sourceStrategySnapshotRepository = strategyId -> Optional.of(new SourceStrategySnapshot(
                strategyId, "buy-hold-fixture", "Buy Hold Fixture", "BUY_AND_HOLD_FIXTURE", "BINANCE", 1001L, "SIM", true, "{}", 1
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
                "str-eval-1", "Eval Research", null, "{}", "{}", "{\"symbol\":\"BTCUSDT\",\"interval\":\"1m\"}"
        ));
        BacktestConfig backtestConfig;
        if (initialCapital.compareTo(BigDecimal.ZERO) > 0) {
            backtestConfig = backtestConfigService.create(new BacktestConfigCreateRequest(
                    researchConfig.researchConfigId(),
                    "Eval Backtest",
                    null,
                    Instant.parse("2025-01-01T00:00:00Z"),
                    Instant.parse("2025-01-01T00:01:59Z"),
                    initialCapital,
                    "{}",
                    "{}"
            ));
        } else {
            backtestConfig = new BacktestConfig(
                    "bcf-invalid-capital",
                    researchConfig.researchConfigId(),
                    "Eval Backtest",
                    null,
                    Instant.parse("2025-01-01T00:00:00Z"),
                    Instant.parse("2025-01-01T00:01:59Z"),
                    initialCapital,
                    "{}",
                    "{}",
                    "{\"startTime\":\"2025-01-01T00:00:00Z\",\"endTime\":\"2025-01-01T00:01:59Z\",\"initialCapital\":\"0\",\"executionSpec\":{},\"evaluationSpec\":{}}",
                    fixedClock.instant(),
                    fixedClock.instant()
            );
            backtestConfigRepository.insert(backtestConfig);
        }
        BacktestRun createdRun = backtestRunService.create(new BacktestRunStartRequest(backtestConfig.backtestConfigId()));
        backtestRunRepository.updateExecution(
                createdRun.backtestRunId(),
                runStatus,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:59Z"),
                null,
                null,
                "{}",
                Instant.parse("2025-01-01T00:01:59Z")
        );

        InMemorySimOrderQueryRepository simOrderQueryRepository = new InMemorySimOrderQueryRepository();
        InMemorySimTradeQueryRepository simTradeQueryRepository = new InMemorySimTradeQueryRepository();
        InMemorySimPositionQueryRepository simPositionQueryRepository = new InMemorySimPositionQueryRepository();
        InMemorySimPnlSnapshotQueryRepository simPnlSnapshotQueryRepository = new InMemorySimPnlSnapshotQueryRepository();
        populateFacts(createdRun.backtestRunId(), simOrderQueryRepository, simTradeQueryRepository, simPositionQueryRepository, simPnlSnapshotQueryRepository, withSnapshots);

        InMemoryBacktestEvaluationReportRepository reportRepository = new InMemoryBacktestEvaluationReportRepository();
        BacktestEvaluationService service = new BacktestEvaluationService(
                backtestRunService,
                backtestConfigService,
                simOrderQueryRepository,
                simTradeQueryRepository,
                simPositionQueryRepository,
                simPnlSnapshotQueryRepository,
                new EvaluationMetricCalculator(new DrawdownCalculator(), new SharpeCalculator(), new TradeOutcomeCalculator()),
                reportRepository,
                fixedClock
        );
        return new Scenario(service, reportRepository, createdRun);
    }

    private void populateFacts(
            String backtestRunId,
            InMemorySimOrderQueryRepository simOrderQueryRepository,
            InMemorySimTradeQueryRepository simTradeQueryRepository,
            InMemorySimPositionQueryRepository simPositionQueryRepository,
            InMemorySimPnlSnapshotQueryRepository simPnlSnapshotQueryRepository,
            boolean withSnapshots
    ) {
        simOrderQueryRepository.storage.add(new SimOrder(
                "so-1", backtestRunId, "BTCUSDT", "BUY", "MARKET",
                BigDecimal.ONE, new BigDecimal("43010"),
                SimOrderStatus.FILLED, Instant.parse("2025-01-01T00:00:59Z"),
                Instant.parse("2025-01-01T00:00:59Z"), null, Instant.parse("2025-01-01T00:00:59Z")
        ));
        simOrderQueryRepository.storage.add(new SimOrder(
                "so-2", backtestRunId, "BTCUSDT", "SELL", "MARKET",
                BigDecimal.ONE, new BigDecimal("43110"),
                SimOrderStatus.FILLED, Instant.parse("2025-01-01T00:01:59Z"),
                Instant.parse("2025-01-01T00:01:59Z"), null, Instant.parse("2025-01-01T00:01:59Z")
        ));
        simTradeQueryRepository.storage.add(new SimTrade(
                "st-1", "so-1", backtestRunId, "BTCUSDT", "BUY", BigDecimal.ONE,
                new BigDecimal("43010"), new BigDecimal("43.01"), new BigDecimal("43.01"),
                Instant.parse("2025-01-01T00:00:59Z"), Instant.parse("2025-01-01T00:00:59Z"), Instant.parse("2025-01-01T00:00:59Z")
        ));
        simTradeQueryRepository.storage.add(new SimTrade(
                "st-2", "so-2", backtestRunId, "BTCUSDT", "SELL", BigDecimal.ONE,
                new BigDecimal("43110"), new BigDecimal("43.11"), new BigDecimal("43.11"),
                Instant.parse("2025-01-01T00:01:59Z"), Instant.parse("2025-01-01T00:01:59Z"), Instant.parse("2025-01-01T00:01:59Z")
        ));
        simPositionQueryRepository.storage.add(new SimPosition(
                "sp-1", backtestRunId, "BTCUSDT", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"),
                Instant.parse("2025-01-01T00:00:59Z"), Instant.parse("2025-01-01T00:01:59Z")
        ));
        if (withSnapshots) {
            simPnlSnapshotQueryRepository.storage.add(new SimPnlSnapshot(
                    "pnl-1", backtestRunId, Instant.parse("2025-01-01T00:00:59Z"),
                    new BigDecimal("56903.980000000000000000"),
                    new BigDecimal("43010.000000000000000000"),
                    BigDecimal.ZERO.setScale(18),
                    BigDecimal.ZERO.setScale(18),
                    new BigDecimal("43.010000000000000000"),
                    new BigDecimal("43.010000000000000000"),
                    new BigDecimal("99913.980000000000000000"),
                    new BigDecimal("-86.020000000000000000"),
                    Instant.parse("2025-01-01T00:00:59Z")
            ));
            simPnlSnapshotQueryRepository.storage.add(new SimPnlSnapshot(
                    "pnl-2", backtestRunId, Instant.parse("2025-01-01T00:01:59Z"),
                    new BigDecimal("99927.760000000000000000"),
                    BigDecimal.ZERO.setScale(18),
                    new BigDecimal("100.000000000000000000"),
                    BigDecimal.ZERO.setScale(18),
                    new BigDecimal("86.120000000000000000"),
                    new BigDecimal("86.120000000000000000"),
                    new BigDecimal("99927.760000000000000000"),
                    new BigDecimal("-72.240000000000000000"),
                    Instant.parse("2025-01-01T00:01:59Z")
            ));
        }
    }

    private record Scenario(
            BacktestEvaluationService service,
            InMemoryBacktestEvaluationReportRepository reportRepository,
            BacktestRun run
    ) {
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
            return storage.values().stream().filter(item -> item.researchConfigId().equals(researchConfigId)).toList();
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
            return storage.values().stream().toList();
        }

        @Override
        public boolean updateExecution(String backtestRunId, BacktestRunStatus status, Instant startedAt, Instant finishedAt, String failureCode, String failureMessage, String summaryJson, Instant updatedAt) {
            BacktestRun current = storage.get(backtestRunId);
            if (current == null) {
                return false;
            }
            storage.put(backtestRunId, new BacktestRun(
                    current.backtestRunId(), current.backtestConfigId(), current.researchConfigId(), current.sourceStrategyId(),
                    current.strategySnapshot(), current.backtestConfigSnapshot(), status, current.requestedAt(), startedAt, finishedAt,
                    failureCode, failureMessage, summaryJson, current.createdAt(), updatedAt
            ));
            return true;
        }
    }

    private static final class InMemoryBacktestEvaluationReportRepository implements BacktestEvaluationReportRepository {
        private final Map<String, BacktestEvaluationReport> storage = new LinkedHashMap<>();

        @Override
        public void upsert(BacktestEvaluationReport report) {
            storage.put(report.backtestRunId(), report);
        }

        @Override
        public Optional<BacktestEvaluationReport> findByBacktestRunId(String backtestRunId) {
            return Optional.ofNullable(storage.get(backtestRunId));
        }
    }

    private static final class InMemorySimOrderQueryRepository implements SimOrderQueryRepository {
        private final List<SimOrder> storage = new ArrayList<>();

        @Override
        public List<SimOrder> listByBacktestRunId(String backtestRunId) {
            return storage.stream().filter(item -> item.backtestRunId().equals(backtestRunId)).toList();
        }
    }

    private static final class InMemorySimTradeQueryRepository implements SimTradeQueryRepository {
        private final List<SimTrade> storage = new ArrayList<>();

        @Override
        public List<SimTrade> listByBacktestRunId(String backtestRunId) {
            return storage.stream().filter(item -> item.backtestRunId().equals(backtestRunId)).toList();
        }
    }

    private static final class InMemorySimPositionQueryRepository implements SimPositionQueryRepository {
        private final List<SimPosition> storage = new ArrayList<>();

        @Override
        public List<SimPosition> listByBacktestRunId(String backtestRunId) {
            return storage.stream().filter(item -> item.backtestRunId().equals(backtestRunId)).toList();
        }
    }

    private static final class InMemorySimPnlSnapshotQueryRepository implements SimPnlSnapshotQueryRepository {
        private final List<SimPnlSnapshot> storage = new ArrayList<>();

        @Override
        public List<SimPnlSnapshot> listByBacktestRunId(String backtestRunId) {
            return storage.stream().filter(item -> item.backtestRunId().equals(backtestRunId)).toList();
        }
    }
}
