package com.guidinglight.nexusquant.backtest.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.backtest.model.BacktestExecutionContext;
import com.guidinglight.nexusquant.backtest.model.BacktestExecutionRequest;
import com.guidinglight.nexusquant.backtest.model.BacktestExecutionResult;
import com.guidinglight.nexusquant.backtest.model.BarInterval;
import com.guidinglight.nexusquant.backtest.model.HistoricalBar;
import com.guidinglight.nexusquant.backtest.model.HistoricalDatasetSpec;
import com.guidinglight.nexusquant.backtest.model.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.backtest.model.SignalIntent;
import com.guidinglight.nexusquant.backtest.model.SignalIntentType;
import com.guidinglight.nexusquant.backtest.model.SimOrder;
import com.guidinglight.nexusquant.backtest.model.SimOrderStatus;
import com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot;
import com.guidinglight.nexusquant.backtest.model.SimPosition;
import com.guidinglight.nexusquant.backtest.model.SimTrade;
import com.guidinglight.nexusquant.backtest.port.HistoricalMarketDataPort;
import com.guidinglight.nexusquant.backtest.port.SimOrderRepository;
import com.guidinglight.nexusquant.backtest.port.SimPnlSnapshotRepository;
import com.guidinglight.nexusquant.backtest.port.SimPositionRepository;
import com.guidinglight.nexusquant.backtest.port.SimTradeRepository;
import com.guidinglight.nexusquant.research.model.BacktestConfig;
import com.guidinglight.nexusquant.research.model.BacktestRun;
import com.guidinglight.nexusquant.research.model.BacktestRunStatus;
import com.guidinglight.nexusquant.research.model.ResearchConfig;
import com.guidinglight.nexusquant.research.port.BacktestRunRepository;
import com.guidinglight.nexusquant.research.service.BacktestConfigService;
import com.guidinglight.nexusquant.research.service.BacktestRunService;
import com.guidinglight.nexusquant.research.service.ResearchConfigService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * BacktestExecutionService 提供 GateF-3 的模拟执行事实链主链。
 * <p>
 * Why:
 * GateF-3 不再满足于“遍历历史 bars 并写摘要”，而是必须形成独立的 sim_order / sim_trade / sim_position / sim_pnl 事实链，
 * 同时继续与实盘执行域完全隔离。
 */
@Service
public class BacktestExecutionService {

    private static final String DEFAULT_RESOURCE_PATH = "backtest/fixtures/btcusdt_1m_sample.csv";

    private final HistoricalMarketDataPort historicalMarketDataPort;
    private final BacktestRunService backtestRunService;
    private final BacktestConfigService backtestConfigService;
    private final ResearchConfigService researchConfigService;
    private final BacktestRunRepository backtestRunRepository;
    private final SimOrderRepository simOrderRepository;
    private final SimTradeRepository simTradeRepository;
    private final SimPositionRepository simPositionRepository;
    private final SimPnlSnapshotRepository simPnlSnapshotRepository;
    private final BacktestSignalPolicy backtestSignalPolicy;
    private final ExecutionPricingPolicy executionPricingPolicy;
    private final FeeModel feeModel;
    private final SlippageModel slippageModel;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BacktestExecutionService(
            HistoricalMarketDataPort historicalMarketDataPort,
            BacktestRunService backtestRunService,
            BacktestConfigService backtestConfigService,
            ResearchConfigService researchConfigService,
            BacktestRunRepository backtestRunRepository,
            SimOrderRepository simOrderRepository,
            SimTradeRepository simTradeRepository,
            SimPositionRepository simPositionRepository,
            SimPnlSnapshotRepository simPnlSnapshotRepository,
            BacktestSignalPolicy backtestSignalPolicy,
            ExecutionPricingPolicy executionPricingPolicy,
            FeeModel feeModel,
            SlippageModel slippageModel,
            ObjectMapper objectMapper
    ) {
        this(
                historicalMarketDataPort,
                backtestRunService,
                backtestConfigService,
                researchConfigService,
                backtestRunRepository,
                simOrderRepository,
                simTradeRepository,
                simPositionRepository,
                simPnlSnapshotRepository,
                backtestSignalPolicy,
                executionPricingPolicy,
                feeModel,
                slippageModel,
                objectMapper,
                Clock.systemUTC()
        );
    }

    BacktestExecutionService(
            HistoricalMarketDataPort historicalMarketDataPort,
            BacktestRunService backtestRunService,
            BacktestConfigService backtestConfigService,
            ResearchConfigService researchConfigService,
            BacktestRunRepository backtestRunRepository,
            SimOrderRepository simOrderRepository,
            SimTradeRepository simTradeRepository,
            SimPositionRepository simPositionRepository,
            SimPnlSnapshotRepository simPnlSnapshotRepository,
            BacktestSignalPolicy backtestSignalPolicy,
            ExecutionPricingPolicy executionPricingPolicy,
            FeeModel feeModel,
            SlippageModel slippageModel,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.historicalMarketDataPort = Objects.requireNonNull(
                historicalMarketDataPort,
                "historicalMarketDataPort must not be null"
        );
        this.backtestRunService = Objects.requireNonNull(backtestRunService, "backtestRunService must not be null");
        this.backtestConfigService = Objects.requireNonNull(
                backtestConfigService,
                "backtestConfigService must not be null"
        );
        this.researchConfigService = Objects.requireNonNull(
                researchConfigService,
                "researchConfigService must not be null"
        );
        this.backtestRunRepository = Objects.requireNonNull(
                backtestRunRepository,
                "backtestRunRepository must not be null"
        );
        this.simOrderRepository = Objects.requireNonNull(simOrderRepository, "simOrderRepository must not be null");
        this.simTradeRepository = Objects.requireNonNull(simTradeRepository, "simTradeRepository must not be null");
        this.simPositionRepository = Objects.requireNonNull(
                simPositionRepository,
                "simPositionRepository must not be null"
        );
        this.simPnlSnapshotRepository = Objects.requireNonNull(
                simPnlSnapshotRepository,
                "simPnlSnapshotRepository must not be null"
        );
        this.backtestSignalPolicy = Objects.requireNonNull(
                backtestSignalPolicy,
                "backtestSignalPolicy must not be null"
        );
        this.executionPricingPolicy = Objects.requireNonNull(
                executionPricingPolicy,
                "executionPricingPolicy must not be null"
        );
        this.feeModel = Objects.requireNonNull(feeModel, "feeModel must not be null");
        this.slippageModel = Objects.requireNonNull(slippageModel, "slippageModel must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 显式启动回测运行。
     * Why:
     * GateF-2 已经冻结“创建”和“执行”分离，本方法继续沿用该边界，但把执行内容升级成模拟事实链闭环。
     */
    public BacktestExecutionResult startRun(String backtestRunId) {
        BacktestRun currentRun = backtestRunService.getByBacktestRunId(backtestRunId);
        if (currentRun.status() != BacktestRunStatus.CREATED) {
            throw new IllegalStateException("backtest run is not in CREATED status: " + currentRun.status());
        }
        BacktestConfig backtestConfig = backtestConfigService.getByBacktestConfigId(currentRun.backtestConfigId());
        ResearchConfig researchConfig = researchConfigService.getByResearchConfigId(currentRun.researchConfigId());
        Instant executionStartedAt = Instant.now(clock);

        backtestRunRepository.updateExecution(
                currentRun.backtestRunId(),
                BacktestRunStatus.PREPARING,
                executionStartedAt,
                null,
                null,
                null,
                "{}",
                executionStartedAt
        );

        BacktestExecutionRequest executionRequest = null;
        BacktestExecutionContext executionContext = null;
        try {
            executionRequest = buildExecutionRequest(currentRun, backtestConfig, researchConfig);
            executionContext = new BacktestExecutionContext(
                    currentRun.backtestRunId(),
                    executionRequest.datasetSpec().symbol(),
                    backtestConfig.initialCapital()
            );
            backtestRunRepository.updateExecution(
                    currentRun.backtestRunId(),
                    BacktestRunStatus.RUNNING,
                    executionStartedAt,
                    null,
                    null,
                    null,
                    "{}",
                    Instant.now(clock)
            );

            List<HistoricalBar> bars = historicalMarketDataPort.loadBars(new HistoricalMarketDataQuery(
                    executionRequest.datasetSpec(),
                    executionRequest.datasetSpec().symbol(),
                    executionRequest.datasetSpec().interval(),
                    executionRequest.startTime(),
                    executionRequest.endTime()
            ));
            if (bars.isEmpty()) {
                throw new IllegalStateException("no historical bars found for requested window");
            }

            BigDecimal feeRate = executionSpecDecimal(executionRequest.executionSpecJson(), "feeRate", BigDecimal.ZERO);
            BigDecimal slippageBps = executionSpecDecimal(
                    executionRequest.executionSpecJson(),
                    "slippageBps",
                    BigDecimal.ZERO
            );
            BigDecimal defaultOrderQuantity = executionSpecDecimal(
                    executionRequest.executionSpecJson(),
                    "orderQuantity",
                    BigDecimal.ONE
            );

            int orderCount = 0;
            int tradeCount = 0;
            for (int barIndex = 0; barIndex < bars.size(); barIndex++) {
                HistoricalBar historicalBar = bars.get(barIndex);
                SignalIntent signalIntent = backtestSignalPolicy.evaluate(
                        executionRequest.sourceStrategyType(),
                        historicalBar,
                        barIndex,
                        bars.size(),
                        executionContext
                );

                if (signalIntent.signalIntentType() == SignalIntentType.HOLD) {
                    persistPnlSnapshot(executionContext, historicalBar.closeTime(), historicalBar.closePrice());
                    continue;
                }

                BigDecimal requestedQuantity = signalIntent.quantity().compareTo(BigDecimal.ZERO) > 0
                        ? normalize(signalIntent.quantity())
                        : normalize(defaultOrderQuantity);
                BigDecimal barClosePrice = executionPricingPolicy.executionPrice(historicalBar.closePrice());
                String side = toOrderSide(signalIntent.signalIntentType());
                SimOrder simOrder = createFilledOrder(
                        currentRun.backtestRunId(),
                        historicalBar.symbol(),
                        side,
                        requestedQuantity,
                        barClosePrice,
                        historicalBar.closeTime()
                );

                if (signalIntent.signalIntentType() == SignalIntentType.BUY) {
                    validateBuyQuantity(executionContext, barClosePrice, requestedQuantity, feeRate, slippageBps);
                } else {
                    validateSellQuantity(executionContext, requestedQuantity);
                }

                simOrderRepository.insert(simOrder);
                orderCount++;

                BigDecimal tradePrice = barClosePrice;
                BigDecimal slippageAmount = slippageModel.calculate(tradePrice, requestedQuantity, slippageBps);
                BigDecimal feeAmount = feeModel.calculate(tradePrice.multiply(requestedQuantity), feeRate);
                SimTrade simTrade = new SimTrade(
                        "st-" + UUID.randomUUID(),
                        simOrder.simOrderId(),
                        currentRun.backtestRunId(),
                        historicalBar.symbol(),
                        side,
                        requestedQuantity,
                        tradePrice,
                        feeAmount,
                        slippageAmount,
                        historicalBar.closeTime(),
                        historicalBar.closeTime(),
                        historicalBar.closeTime()
                );
                simTradeRepository.insert(simTrade);
                tradeCount++;

                SimPosition updatedPosition = updatePosition(executionContext, simTrade, historicalBar.closeTime());
                simPositionRepository.upsert(updatedPosition);
                if ("BUY".equals(side)) {
                    executionContext.applyBuy(simTrade, updatedPosition);
                } else {
                    executionContext.applySell(simTrade, updatedPosition);
                }
                persistPnlSnapshot(executionContext, historicalBar.closeTime(), historicalBar.closePrice());
            }

            Instant executionFinishedAt = Instant.now(clock);
            String summaryJson = buildSuccessSummary(
                    executionRequest,
                    executionContext,
                    bars,
                    executionStartedAt,
                    executionFinishedAt,
                    orderCount,
                    tradeCount
            );
            backtestRunRepository.updateExecution(
                    currentRun.backtestRunId(),
                    BacktestRunStatus.SUCCEEDED,
                    executionStartedAt,
                    executionFinishedAt,
                    null,
                    null,
                    summaryJson,
                    executionFinishedAt
            );
            return new BacktestExecutionResult(
                    currentRun.backtestRunId(),
                    BacktestRunStatus.SUCCEEDED,
                    bars.size(),
                    executionStartedAt,
                    executionFinishedAt,
                    bars.getFirst().openTime(),
                    bars.getLast().closeTime(),
                    summaryJson
            );
        } catch (RuntimeException ex) {
            Instant executionFinishedAt = Instant.now(clock);
            String summaryJson = buildFailureSummary(
                    executionRequest,
                    currentRun,
                    researchConfig,
                    executionContext,
                    executionStartedAt,
                    executionFinishedAt,
                    ex
            );
            backtestRunRepository.updateExecution(
                    currentRun.backtestRunId(),
                    BacktestRunStatus.FAILED,
                    executionStartedAt,
                    executionFinishedAt,
                    "BACKTEST_EXECUTION_FAILED",
                    safeMessage(ex),
                    summaryJson,
                    executionFinishedAt
            );
            throw new IllegalStateException("backtest execution failed: " + safeMessage(ex), ex);
        }
    }

    private BacktestExecutionRequest buildExecutionRequest(
            BacktestRun backtestRun,
            BacktestConfig backtestConfig,
            ResearchConfig researchConfig
    ) {
        HistoricalDatasetSpec datasetSpec = parseDatasetSpec(researchConfig.datasetSpec());
        return new BacktestExecutionRequest(
                backtestRun.backtestRunId(),
                researchConfig.researchConfigId(),
                backtestConfig.backtestConfigId(),
                researchConfig.sourceStrategyId(),
                parseStrategyType(researchConfig.strategySnapshot()),
                researchConfig.strategySnapshot(),
                datasetSpec,
                backtestConfig.startTime(),
                backtestConfig.endTime(),
                backtestConfig.initialCapital(),
                backtestConfig.executionSpec()
        );
    }

    private HistoricalDatasetSpec parseDatasetSpec(String datasetSpecJson) {
        JsonNode jsonNode = readJson(datasetSpecJson);
        String symbol = requiredText(jsonNode, "symbol", "instrument");
        String interval = requiredText(jsonNode, "interval", "granularity");
        String provider = optionalText(jsonNode, "provider", "fixture");
        String datasetId = optionalText(jsonNode, "datasetId", symbol + "-" + interval);
        String resourcePath = optionalText(jsonNode, "resourcePath", DEFAULT_RESOURCE_PATH);
        return new HistoricalDatasetSpec(
                provider,
                datasetId,
                symbol,
                BarInterval.fromWireValue(interval),
                resourcePath
        );
    }

    private String parseStrategyType(String strategySnapshotJson) {
        JsonNode strategySnapshot = readJson(strategySnapshotJson);
        JsonNode strategyType = strategySnapshot.get("strategyType");
        if (strategyType == null || strategyType.asText().isBlank()) {
            throw new IllegalArgumentException("strategyType must be present in strategySnapshot");
        }
        return strategyType.asText().trim();
    }

    private BigDecimal executionSpecDecimal(String executionSpecJson, String fieldName, BigDecimal defaultValue) {
        JsonNode jsonNode = readJson(executionSpecJson);
        JsonNode field = jsonNode.get(fieldName);
        if (field == null || field.isNull() || field.asText().isBlank()) {
            return normalize(defaultValue);
        }
        return normalize(new BigDecimal(field.asText().trim()));
    }

    private SimOrder createFilledOrder(
            String backtestRunId,
            String symbol,
            String side,
            BigDecimal requestedQuantity,
            BigDecimal requestedPrice,
            Instant tradedAt
    ) {
        return new SimOrder(
                "so-" + UUID.randomUUID(),
                backtestRunId,
                symbol,
                side,
                "MARKET",
                normalize(requestedQuantity),
                normalize(requestedPrice),
                SimOrderStatus.FILLED,
                tradedAt,
                tradedAt,
                null,
                tradedAt
        );
    }

    private void validateBuyQuantity(
            BacktestExecutionContext executionContext,
            BigDecimal tradePrice,
            BigDecimal quantity,
            BigDecimal feeRate,
            BigDecimal slippageBps
    ) {
        BigDecimal notional = tradePrice.multiply(quantity);
        BigDecimal feeAmount = feeModel.calculate(notional, feeRate);
        BigDecimal slippageAmount = slippageModel.calculate(tradePrice, quantity, slippageBps);
        if (executionContext.cashBalance().compareTo(notional.add(feeAmount).add(slippageAmount)) < 0) {
            throw new IllegalStateException("insufficient cash balance for simulated buy");
        }
    }

    private void validateSellQuantity(BacktestExecutionContext executionContext, BigDecimal quantity) {
        SimPosition currentPosition = executionContext.currentPosition();
        if (currentPosition == null || currentPosition.quantity().compareTo(quantity) < 0) {
            throw new IllegalStateException("insufficient position quantity for simulated sell");
        }
    }

    private SimPosition updatePosition(BacktestExecutionContext executionContext, SimTrade simTrade, Instant updatedAt) {
        Optional<SimPosition> existingPosition = simPositionRepository.findByBacktestRunIdAndSymbol(
                executionContext.backtestRunId(),
                executionContext.symbol()
        );
        SimPosition currentPosition = existingPosition.orElse(new SimPosition(
                "sp-" + UUID.randomUUID(),
                executionContext.backtestRunId(),
                executionContext.symbol(),
                BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP),
                updatedAt,
                updatedAt
        ));
        if ("BUY".equals(simTrade.side())) {
            BigDecimal nextQuantity = normalize(currentPosition.quantity().add(simTrade.quantity()));
            BigDecimal nextAverageEntryPrice = nextQuantity.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP)
                    : normalize(
                    currentPosition.averageEntryPrice().multiply(currentPosition.quantity())
                            .add(simTrade.tradePrice().multiply(simTrade.quantity()))
                            .divide(nextQuantity, 18, RoundingMode.HALF_UP)
            );
            return new SimPosition(
                    currentPosition.simPositionId(),
                    currentPosition.backtestRunId(),
                    currentPosition.symbol(),
                    nextQuantity,
                    nextAverageEntryPrice,
                    currentPosition.realizedPnl(),
                    currentPosition.createdAt(),
                    updatedAt
            );
        }

        BigDecimal remainingQuantity = normalize(currentPosition.quantity().subtract(simTrade.quantity()));
        BigDecimal realizedIncrement = normalize(
                simTrade.tradePrice().subtract(currentPosition.averageEntryPrice()).multiply(simTrade.quantity())
        );
        return new SimPosition(
                currentPosition.simPositionId(),
                currentPosition.backtestRunId(),
                currentPosition.symbol(),
                remainingQuantity,
                remainingQuantity.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP)
                        : currentPosition.averageEntryPrice(),
                normalize(currentPosition.realizedPnl().add(realizedIncrement)),
                currentPosition.createdAt(),
                updatedAt
        );
    }

    private void persistPnlSnapshot(
            BacktestExecutionContext executionContext,
            Instant snapshotTime,
            BigDecimal referenceClosePrice
    ) {
        SimPosition currentPosition = executionContext.currentPosition();
        BigDecimal quantity = currentPosition == null ? BigDecimal.ZERO : currentPosition.quantity();
        BigDecimal averageEntryPrice = currentPosition == null
                ? BigDecimal.ZERO
                : currentPosition.averageEntryPrice();
        BigDecimal positionMarketValue = normalize(quantity.multiply(referenceClosePrice));
        BigDecimal unrealizedPnl = quantity.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP)
                : normalize(referenceClosePrice.subtract(averageEntryPrice).multiply(quantity));
        BigDecimal realizedPnl = currentPosition == null
                ? BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP)
                : normalize(currentPosition.realizedPnl());
        BigDecimal equity = normalize(executionContext.cashBalance().add(positionMarketValue));
        BigDecimal netPnl = normalize(equity.subtract(executionContext.initialCapital()));
        simPnlSnapshotRepository.insert(new SimPnlSnapshot(
                "pnl-" + UUID.randomUUID(),
                executionContext.backtestRunId(),
                snapshotTime,
                executionContext.cashBalance(),
                positionMarketValue,
                realizedPnl,
                unrealizedPnl,
                executionContext.totalFee(),
                executionContext.totalSlippage(),
                equity,
                netPnl,
                snapshotTime
        ));
    }

    private String buildSuccessSummary(
            BacktestExecutionRequest executionRequest,
            BacktestExecutionContext executionContext,
            List<HistoricalBar> bars,
            Instant executionStartedAt,
            Instant executionFinishedAt,
            int orderCount,
            int tradeCount
    ) {
        List<SimPnlSnapshot> pnlSnapshots = simPnlSnapshotRepository.listByBacktestRunId(executionContext.backtestRunId());
        SimPnlSnapshot finalSnapshot = pnlSnapshots.getLast();
        SimPosition finalPosition = executionContext.currentPosition();
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("sourceStrategyId", executionRequest.sourceStrategyId());
        summary.put("symbol", executionRequest.datasetSpec().symbol());
        summary.put("interval", executionRequest.datasetSpec().interval().wireValue());
        summary.put("barCount", bars.size());
        summary.put("orderCount", orderCount);
        summary.put("tradeCount", tradeCount);
        summary.put("finalPositionQuantity", finalPosition == null ? "0" : finalPosition.quantity().toPlainString());
        summary.put("finalCashBalance", finalSnapshot.cashBalance().toPlainString());
        summary.put("finalEquity", finalSnapshot.equity().toPlainString());
        summary.put("realizedPnl", finalSnapshot.realizedPnl().toPlainString());
        summary.put("unrealizedPnl", finalSnapshot.unrealizedPnl().toPlainString());
        summary.put("netPnl", finalSnapshot.netPnl().toPlainString());
        summary.put("totalFee", finalSnapshot.totalFee().toPlainString());
        summary.put("totalSlippage", finalSnapshot.totalSlippage().toPlainString());
        summary.put("resultStatus", BacktestRunStatus.SUCCEEDED.name());
        summary.put("executionStartedAt", executionStartedAt.toString());
        summary.put("executionFinishedAt", executionFinishedAt.toString());
        return summary.toString();
    }

    private String buildFailureSummary(
            BacktestExecutionRequest executionRequest,
            BacktestRun backtestRun,
            ResearchConfig researchConfig,
            BacktestExecutionContext executionContext,
            Instant executionStartedAt,
            Instant executionFinishedAt,
            RuntimeException exception
    ) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("sourceStrategyId", researchConfig.sourceStrategyId());
        if (executionRequest != null) {
            summary.put("symbol", executionRequest.datasetSpec().symbol());
            summary.put("interval", executionRequest.datasetSpec().interval().wireValue());
        } else {
            summary.putNull("symbol");
            summary.putNull("interval");
        }
        summary.put("barCount", executionContext == null
                ? 0
                : simPnlSnapshotRepository.listByBacktestRunId(backtestRun.backtestRunId()).size());
        summary.put("orderCount", simOrderRepository.listByBacktestRunId(backtestRun.backtestRunId()).size());
        summary.put("tradeCount", simTradeRepository.listByBacktestRunId(backtestRun.backtestRunId()).size());
        summary.put("resultStatus", BacktestRunStatus.FAILED.name());
        summary.put("failureMessage", safeMessage(exception));
        summary.put("executionStartedAt", executionStartedAt.toString());
        summary.put("executionFinishedAt", executionFinishedAt.toString());
        return summary.toString();
    }

    private String toOrderSide(SignalIntentType signalIntentType) {
        return switch (signalIntentType) {
            case BUY -> "BUY";
            case SELL, CLOSE -> "SELL";
            case HOLD -> throw new IllegalArgumentException("HOLD should not produce sim order");
        };
    }

    private JsonNode readJson(String rawJson) {
        try {
            return objectMapper.readTree(rawJson == null || rawJson.isBlank() ? "{}" : rawJson);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid json payload", ex);
        }
    }

    private String requiredText(JsonNode jsonNode, String primaryFieldName, String fallbackFieldName) {
        JsonNode primaryField = jsonNode.get(primaryFieldName);
        if (primaryField != null && !primaryField.asText().isBlank()) {
            return primaryField.asText().trim();
        }
        JsonNode fallbackField = jsonNode.get(fallbackFieldName);
        if (fallbackField != null && !fallbackField.asText().isBlank()) {
            return fallbackField.asText().trim();
        }
        throw new IllegalArgumentException(primaryFieldName + " must be present");
    }

    private String optionalText(JsonNode jsonNode, String fieldName, String defaultValue) {
        JsonNode field = jsonNode.get(fieldName);
        if (field == null || field.isNull() || field.asText().isBlank()) {
            return defaultValue;
        }
        return field.asText().trim();
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(18, RoundingMode.HALF_UP);
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
