package com.guidinglight.nexusquant.research.api.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.domain.eval.EvaluationSummary;
import com.guidinglight.nexusquant.research.domain.PublishSummary;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;

import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * BacktestRunResponse 描述接口响应体。
 */
@Schema(name = "BacktestRunResponse", description = "接口响应体")
public record BacktestRunResponse(
        @Schema(description = "backtestRunId")
        String backtestRunId,
        @Schema(description = "backtestConfigId")
        String backtestConfigId,
        @Schema(description = "researchConfigId")
        String researchConfigId,
        @Schema(description = "sourceStrategyId")
        String sourceStrategyId,
        @Schema(description = "strategySnapshot")
        String strategySnapshot,
        @Schema(description = "strategyVersionId")
        String strategyVersionId,
        @Schema(description = "strategyVersionSnapshotJson")
        String strategyVersionSnapshotJson,
        @Schema(description = "paramSnapshotJson")
        String paramSnapshotJson,
        @Schema(description = "backtestConfigSnapshot")
        String backtestConfigSnapshot,
        @Schema(description = "configSnapshotJson")
        String configSnapshotJson,
        @Schema(description = "datasetSnapshotJson")
        String datasetSnapshotJson,
        @Schema(description = "status")
        BacktestRunStatus status,
        @Schema(description = "requestedAt")
        Instant requestedAt,
        @Schema(description = "startedAt")
        Instant startedAt,
        @Schema(description = "finishedAt")
        Instant finishedAt,
        @Schema(description = "failureCode")
        String failureCode,
        @Schema(description = "failureMessage")
        String failureMessage,
        @Schema(description = "summaryJson")
        String summaryJson,
        @Schema(description = "orderCount")
        Integer orderCount,
        @Schema(description = "tradeCount")
        Integer tradeCount,
        @Schema(description = "finalPositionQuantity")
        BigDecimal finalPositionQuantity,
        @Schema(description = "finalCashBalance")
        BigDecimal finalCashBalance,
        @Schema(description = "finalEquity")
        BigDecimal finalEquity,
        @Schema(description = "realizedPnl")
        BigDecimal realizedPnl,
        @Schema(description = "unrealizedPnl")
        BigDecimal unrealizedPnl,
        @Schema(description = "netPnl")
        BigDecimal netPnl,
        @Schema(description = "totalFee")
        BigDecimal totalFee,
        @Schema(description = "totalSlippage")
        BigDecimal totalSlippage,
        @Schema(description = "evaluationStatus")
        String evaluationStatus,
        @Schema(description = "evaluatedAt")
        Instant evaluatedAt,
        @Schema(description = "totalReturnRate")
        BigDecimal totalReturnRate,
        @Schema(description = "totalReturn")
        BigDecimal totalReturn,
        @Schema(description = "annualizedReturn")
        BigDecimal annualizedReturn,
        @Schema(description = "maxDrawdownRate")
        BigDecimal maxDrawdownRate,
        @Schema(description = "winRate")
        BigDecimal winRate,
        @Schema(description = "profitLossRatio")
        BigDecimal profitLossRatio,
        @Schema(description = "sharpeRatio")
        BigDecimal sharpeRatio,
        @Schema(description = "publishStatus")
        String publishStatus,
        @Schema(description = "publishedAt")
        Instant publishedAt,
        @Schema(description = "targetStrategyDefinitionId")
        String targetStrategyDefinitionId,
        @Schema(description = "publishName")
        String publishName,
        @Schema(description = "createdAt")
        Instant createdAt,
        @Schema(description = "updatedAt")
        Instant updatedAt
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static BacktestRunResponse from(
            BacktestRun backtestRun,
            EvaluationSummary evaluationSummary,
            PublishSummary publishSummary
    ) {
        JsonNode summaryNode = parseSummary(backtestRun.summaryJson());
        return new BacktestRunResponse(
                backtestRun.backtestRunId(),
                backtestRun.backtestConfigId(),
                backtestRun.researchConfigId(),
                backtestRun.sourceStrategyId(),
                backtestRun.strategySnapshot(),
                backtestRun.strategyVersionId(),
                backtestRun.strategyVersionSnapshotJson(),
                backtestRun.paramSnapshotJson(),
                backtestRun.backtestConfigSnapshot(),
                backtestRun.configSnapshotJson(),
                backtestRun.datasetSnapshotJson(),
                backtestRun.status(),
                backtestRun.requestedAt(),
                backtestRun.startedAt(),
                backtestRun.finishedAt(),
                backtestRun.failureCode(),
                backtestRun.failureMessage(),
                backtestRun.summaryJson(),
                intField(summaryNode, "orderCount"),
                intField(summaryNode, "tradeCount"),
                decimalField(summaryNode, "finalPositionQuantity"),
                decimalField(summaryNode, "finalCashBalance"),
                decimalField(summaryNode, "finalEquity"),
                decimalField(summaryNode, "realizedPnl"),
                decimalField(summaryNode, "unrealizedPnl"),
                decimalField(summaryNode, "netPnl"),
                decimalField(summaryNode, "totalFee"),
                decimalField(summaryNode, "totalSlippage"),
                evaluationSummary == null ? null : evaluationSummary.evaluationStatus().name(),
                evaluationSummary == null ? null : evaluationSummary.evaluatedAt(),
                evaluationSummary == null ? null : evaluationSummary.totalReturnRate(),
                evaluationSummary == null ? null : evaluationSummary.totalReturn(),
                evaluationSummary == null ? null : evaluationSummary.annualizedReturn(),
                evaluationSummary == null ? null : evaluationSummary.maxDrawdownRate(),
                evaluationSummary == null ? null : evaluationSummary.winRate(),
                evaluationSummary == null ? null : evaluationSummary.profitLossRatio(),
                evaluationSummary == null ? null : evaluationSummary.sharpeRatio(),
                publishSummary == null ? null : publishSummary.publishStatus().name(),
                publishSummary == null ? null : publishSummary.publishedAt(),
                publishSummary == null ? null : publishSummary.targetStrategyDefinitionId(),
                publishSummary == null ? null : publishSummary.publishName(),
                backtestRun.createdAt(),
                backtestRun.updatedAt()
        );
    }

    private static JsonNode parseSummary(String summaryJson) {
        try {
            return OBJECT_MAPPER.readTree(summaryJson == null || summaryJson.isBlank() ? "{}" : summaryJson);
        } catch (JsonProcessingException ex) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private static Integer intField(JsonNode summaryNode, String fieldName) {
        JsonNode field = summaryNode.get(fieldName);
        return field == null || field.isNull() ? null : field.asInt();
    }

    private static BigDecimal decimalField(JsonNode summaryNode, String fieldName) {
        JsonNode field = summaryNode.get(fieldName);
        if (field == null || field.isNull() || field.asText().isBlank()) {
            return null;
        }
        return new BigDecimal(field.asText());
    }
}


