package com.guidinglight.nexusquant.research.application.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.research.domain.backtest.SignalIntentType;
import com.guidinglight.nexusquant.research.domain.backtest.BuiltinFixtureSignalPolicy;
import com.guidinglight.nexusquant.research.domain.backtest.ExecutionPricingPolicy;
import com.guidinglight.nexusquant.research.domain.backtest.FeeModel;
import com.guidinglight.nexusquant.research.domain.backtest.SlippageModel;
import com.guidinglight.nexusquant.research.domain.backtest.SimOrder;
import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.SimPosition;
import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalMarketDataPort;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimOrderRepository;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimPnlSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimPositionRepository;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimTradeRepository;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.domain.SourceStrategySnapshot;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigRepository;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;
import com.guidinglight.nexusquant.research.domain.port.ResearchConfigRepository;
import com.guidinglight.nexusquant.research.domain.port.SourceStrategySnapshotRepository;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.backtest.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;

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

class BacktestExecutionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldProduceSimulatedFactsForBuyAndHoldFixture() {
        Scenario scenario = createScenario(query -> List.of(
                bar("2025-01-01T00:00:00Z", "2025-01-01T00:00:59Z", "43000", "43010", "10"),
                bar("2025-01-01T00:01:00Z", "2025-01-01T00:01:59Z", "43010", "43110", "11")
        ));

        BacktestRun createdRun = scenario.createRun("""
                {"provider":"fixture","datasetId":"btc-sample","resourcePath":"backtest/fixtures/btcusdt_1m_sample.csv","symbol":"BTCUSDT","interval":"1m"}
                """);
        var result = scenario.backtestExecutionService.startRun(createdRun.backtestRunId());
        BacktestRun updatedRun = scenario.backtestRunService.getByBacktestRunId(createdRun.backtestRunId());
        List<SimOrder> orders = scenario.simOrderRepository.listByBacktestRunId(createdRun.backtestRunId());
        List<SimTrade> trades = scenario.simTradeRepository.listByBacktestRunId(createdRun.backtestRunId());
        List<SimPosition> positions = scenario.simPositionRepository.listByBacktestRunId(createdRun.backtestRunId());
        List<SimPnlSnapshot> pnlSnapshots = scenario.simPnlSnapshotRepository.listByBacktestRunId(createdRun.backtestRunId());

        assertEquals(BacktestRunStatus.SUCCEEDED, result.resultStatus());
        assertEquals(2, orders.size());
        assertEquals(2, trades.size());
        assertEquals(1, positions.size());
        assertEquals(new BigDecimal("0E-18"), positions.getFirst().quantity());
        assertEquals(2, pnlSnapshots.size());
        assertEquals(BacktestRunStatus.SUCCEEDED, updatedRun.status());
        assertTrue(updatedRun.summaryJson().contains("\"sourceStrategyId\":\"str-demo-3\""));
        assertTrue(updatedRun.summaryJson().contains("\"orderCount\":2"));
        assertTrue(updatedRun.summaryJson().contains("\"tradeCount\":2"));
        assertTrue(updatedRun.summaryJson().contains("\"finalEquity\":\"99927.760000000000000000\""));
        assertTrue(updatedRun.summaryJson().contains("\"totalFee\":\"86.120000000000000000\""));
        assertTrue(updatedRun.summaryJson().contains("\"totalSlippage\":\"86.120000000000000000\""));
        assertEquals(new BigDecimal("43.010000000000000000"), trades.getFirst().feeAmount());
        assertEquals(new BigDecimal("43.010000000000000000"), trades.getFirst().slippageAmount());
        assertEquals(SignalIntentType.BUY.name(), orders.getFirst().side());
    }

    @Test
    void shouldMarkFailedWhenNoBarsReturned() {
        Scenario scenario = createScenario(query -> List.of());
        BacktestRun createdRun = scenario.createRun("""
                {"provider":"fixture","datasetId":"btc-empty","resourcePath":"backtest/fixtures/btcusdt_1m_sample.csv","symbol":"BTCUSDT","interval":"1m"}
                """);

        assertThrows(IllegalStateException.class, () -> scenario.backtestExecutionService.startRun(createdRun.backtestRunId()));

        BacktestRun updatedRun = scenario.backtestRunService.getByBacktestRunId(createdRun.backtestRunId());
        assertEquals(BacktestRunStatus.FAILED, updatedRun.status());
        assertEquals("BACKTEST_EXECUTION_FAILED", updatedRun.failureCode());
    }

    @Test
    void shouldMarkFailedWhenDatasetSpecIsInvalid() {
        Scenario scenario = createScenario(query -> List.of());
        BacktestRun createdRun = scenario.createRun("""
                {"provider":"fixture","datasetId":"btc-invalid","resourcePath":"backtest/fixtures/btcusdt_1m_sample.csv","interval":"1m"}
                """);

        assertThrows(IllegalStateException.class, () -> scenario.backtestExecutionService.startRun(createdRun.backtestRunId()));

        BacktestRun updatedRun = scenario.backtestRunService.getByBacktestRunId(createdRun.backtestRunId());
        assertEquals(BacktestRunStatus.FAILED, updatedRun.status());
        assertEquals("BACKTEST_EXECUTION_FAILED", updatedRun.failureCode());
    }

    @Test
    void shouldMarkRunFailedWithoutPersistingFactsWhenSuccessPersistenceFails() {
        InMemoryResearchConfigRepository researchConfigRepository = new InMemoryResearchConfigRepository();
        InMemoryBacktestConfigRepository backtestConfigRepository = new InMemoryBacktestConfigRepository();
        InMemoryBacktestRunRepository backtestRunRepository = new InMemoryBacktestRunRepository();
        InMemorySimOrderRepository simOrderRepository = new InMemorySimOrderRepository();
        InMemorySimTradeRepository simTradeRepository = new InMemorySimTradeRepository();
        InMemorySimPositionRepository simPositionRepository = new InMemorySimPositionRepository();
        InMemorySimPnlSnapshotRepository simPnlSnapshotRepository = new InMemorySimPnlSnapshotRepository();
        SourceStrategySnapshotRepository sourceStrategySnapshotRepository = strategyId -> Optional.of(new SourceStrategySnapshot(
                strategyId,
                "buy-hold-fixture",
                "Buy Hold Fixture",
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
        BacktestExecutionPersistenceService failingPersistenceService = new FailingBacktestExecutionPersistenceService(
                backtestRunRepository,
                simOrderRepository,
                simTradeRepository,
                simPositionRepository,
                simPnlSnapshotRepository
        );
        BacktestExecutionService backtestExecutionService = new BacktestExecutionService(
                query -> List.of(
                        bar("2025-01-01T00:00:00Z", "2025-01-01T00:00:59Z", "43000", "43010", "10"),
                        bar("2025-01-01T00:01:00Z", "2025-01-01T00:01:59Z", "43010", "43110", "11")
                ),
                backtestRunService,
                backtestConfigService,
                researchConfigService,
                failingPersistenceService,
                new BuiltinFixtureSignalPolicy(),
                new ExecutionPricingPolicy(),
                new FeeModel(),
                new SlippageModel(),
                objectMapper,
                fixedClock
        );

        ResearchConfig researchConfig = researchConfigService.create(new ResearchConfigCreateRequest(
                "str-demo-3",
                "Buy Hold Research",
                null,
                "{}",
                "{}",
                """
                {"provider":"fixture","datasetId":"btc-sample","resourcePath":"backtest/fixtures/btcusdt_1m_sample.csv","symbol":"BTCUSDT","interval":"1m"}
                """
        ));
        BacktestConfig backtestConfig = backtestConfigService.create(new BacktestConfigCreateRequest(
                researchConfig.researchConfigId(),
                "Buy Hold Backtest",
                null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:01:59Z"),
                new BigDecimal("100000"),
                "{\"mode\":\"bar\",\"feeRate\":\"0.001\",\"slippageBps\":\"10\",\"orderQuantity\":\"1\"}",
                "{}"
        ));
        BacktestRun createdRun = backtestRunService.create(new BacktestRunStartRequest(backtestConfig.backtestConfigId()));

        assertThrows(IllegalStateException.class, () -> backtestExecutionService.startRun(createdRun.backtestRunId()));

        BacktestRun updatedRun = backtestRunService.getByBacktestRunId(createdRun.backtestRunId());
        assertEquals(BacktestRunStatus.FAILED, updatedRun.status());
        assertEquals(0, simOrderRepository.listByBacktestRunId(createdRun.backtestRunId()).size());
        assertEquals(0, simTradeRepository.listByBacktestRunId(createdRun.backtestRunId()).size());
        assertEquals(0, simPositionRepository.listByBacktestRunId(createdRun.backtestRunId()).size());
        assertEquals(0, simPnlSnapshotRepository.listByBacktestRunId(createdRun.backtestRunId()).size());
    }

    private Scenario createScenario(HistoricalMarketDataPort historicalMarketDataPort) {
        InMemoryResearchConfigRepository researchConfigRepository = new InMemoryResearchConfigRepository();
        InMemoryBacktestConfigRepository backtestConfigRepository = new InMemoryBacktestConfigRepository();
        InMemoryBacktestRunRepository backtestRunRepository = new InMemoryBacktestRunRepository();
        InMemorySimOrderRepository simOrderRepository = new InMemorySimOrderRepository();
        InMemorySimTradeRepository simTradeRepository = new InMemorySimTradeRepository();
        InMemorySimPositionRepository simPositionRepository = new InMemorySimPositionRepository();
        InMemorySimPnlSnapshotRepository simPnlSnapshotRepository = new InMemorySimPnlSnapshotRepository();
        SourceStrategySnapshotRepository sourceStrategySnapshotRepository = strategyId -> Optional.of(new SourceStrategySnapshot(
                strategyId,
                "buy-hold-fixture",
                "Buy Hold Fixture",
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
        BacktestExecutionPersistenceService backtestExecutionPersistenceService = new BacktestExecutionPersistenceService(
                backtestRunRepository,
                simOrderRepository,
                simTradeRepository,
                simPositionRepository,
                simPnlSnapshotRepository
        );
        BacktestExecutionService backtestExecutionService = new BacktestExecutionService(
                historicalMarketDataPort,
                backtestRunService,
                backtestConfigService,
                researchConfigService,
                backtestExecutionPersistenceService,
                new BuiltinFixtureSignalPolicy(),
                new ExecutionPricingPolicy(),
                new FeeModel(),
                new SlippageModel(),
                objectMapper,
                fixedClock
        );
        return new Scenario(
                researchConfigService,
                backtestConfigService,
                backtestRunService,
                backtestExecutionService,
                simOrderRepository,
                simTradeRepository,
                simPositionRepository,
                simPnlSnapshotRepository
        );
    }

    private HistoricalBar bar(String openTime, String closeTime, String openPrice, String closePrice, String volume) {
        return new HistoricalBar(
                "BTCUSDT",
                BarInterval.ONE_MINUTE,
                Instant.parse(openTime),
                Instant.parse(closeTime),
                new BigDecimal(openPrice),
                new BigDecimal(openPrice),
                new BigDecimal(openPrice),
                new BigDecimal(closePrice),
                new BigDecimal(volume)
        );
    }

    private record Scenario(
            ResearchConfigService researchConfigService,
            BacktestConfigService backtestConfigService,
            BacktestRunService backtestRunService,
            BacktestExecutionService backtestExecutionService,
            InMemorySimOrderRepository simOrderRepository,
            InMemorySimTradeRepository simTradeRepository,
            InMemorySimPositionRepository simPositionRepository,
            InMemorySimPnlSnapshotRepository simPnlSnapshotRepository
    ) {
        BacktestRun createRun(String datasetSpec) {
            ResearchConfig researchConfig = researchConfigService.create(new ResearchConfigCreateRequest(
                    "str-demo-3",
                    "Buy Hold Research",
                    null,
                    "{}",
                    "{}",
                    datasetSpec
            ));
            BacktestConfig backtestConfig = backtestConfigService.create(new BacktestConfigCreateRequest(
                    researchConfig.researchConfigId(),
                    "Buy Hold Backtest",
                    null,
                    Instant.parse("2025-01-01T00:00:00Z"),
                    Instant.parse("2025-01-01T00:01:59Z"),
                    new BigDecimal("100000"),
                    "{\"mode\":\"bar\",\"feeRate\":\"0.001\",\"slippageBps\":\"10\",\"orderQuantity\":\"1\"}",
                    "{}"
            ));
            return backtestRunService.create(new BacktestRunStartRequest(backtestConfig.backtestConfigId()));
        }
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

    private static final class InMemorySimOrderRepository implements SimOrderRepository {

        private final List<SimOrder> storage = new ArrayList<>();

        @Override
        public void insert(SimOrder simOrder) {
            storage.add(simOrder);
        }

        @Override
        public List<SimOrder> listByBacktestRunId(String backtestRunId) {
            return storage.stream().filter(item -> item.backtestRunId().equals(backtestRunId)).toList();
        }
    }

    private static final class InMemorySimTradeRepository implements SimTradeRepository {

        private final List<SimTrade> storage = new ArrayList<>();

        @Override
        public void insert(SimTrade simTrade) {
            storage.add(simTrade);
        }

        @Override
        public List<SimTrade> listByBacktestRunId(String backtestRunId) {
            return storage.stream().filter(item -> item.backtestRunId().equals(backtestRunId)).toList();
        }
    }

    private static final class InMemorySimPositionRepository implements SimPositionRepository {

        private final Map<String, SimPosition> storage = new LinkedHashMap<>();

        @Override
        public void upsert(SimPosition simPosition) {
            storage.put(simPosition.backtestRunId() + ":" + simPosition.symbol(), simPosition);
        }

        @Override
        public Optional<SimPosition> findByBacktestRunIdAndSymbol(String backtestRunId, String symbol) {
            return Optional.ofNullable(storage.get(backtestRunId + ":" + symbol));
        }

        @Override
        public List<SimPosition> listByBacktestRunId(String backtestRunId) {
            return storage.values().stream()
                    .filter(item -> item.backtestRunId().equals(backtestRunId))
                    .toList();
        }
    }

    private static final class InMemorySimPnlSnapshotRepository implements SimPnlSnapshotRepository {

        private final List<SimPnlSnapshot> storage = new ArrayList<>();

        @Override
        public void insert(SimPnlSnapshot simPnlSnapshot) {
            storage.add(simPnlSnapshot);
        }

        @Override
        public List<SimPnlSnapshot> listByBacktestRunId(String backtestRunId) {
            return storage.stream().filter(item -> item.backtestRunId().equals(backtestRunId)).toList();
        }
    }

    private static final class FailingBacktestExecutionPersistenceService extends BacktestExecutionPersistenceService {

        private FailingBacktestExecutionPersistenceService(
                BacktestRunRepository backtestRunRepository,
                SimOrderRepository simOrderRepository,
                SimTradeRepository simTradeRepository,
                SimPositionRepository simPositionRepository,
                SimPnlSnapshotRepository simPnlSnapshotRepository
        ) {
            super(
                    backtestRunRepository,
                    simOrderRepository,
                    simTradeRepository,
                    simPositionRepository,
                    simPnlSnapshotRepository
            );
        }

        @Override
        public void persistSuccess(
                String backtestRunId,
                Instant executionStartedAt,
                Instant executionFinishedAt,
                List<SimOrder> simOrders,
                List<SimTrade> simTrades,
                List<SimPosition> simPositions,
                List<SimPnlSnapshot> simPnlSnapshots,
                String summaryJson
        ) {
            throw new IllegalStateException("simulated success persistence failure");
        }
    }
}




