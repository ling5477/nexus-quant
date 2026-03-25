package com.guidinglight.nexusquant.app.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.eval.model.EvaluationSummary;
import com.guidinglight.nexusquant.research.model.PublishSummary;
import com.guidinglight.nexusquant.research.model.BacktestRun;
import com.guidinglight.nexusquant.research.model.BacktestRunStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GateFBacktestRunResponse 是回测运行响应体。
 */
public record GateFBacktestRunResponse(
        String backtestRunId,
        String backtestConfigId,
        String researchConfigId,
        String sourceStrategyId,
        String strategySnapshot,
        String backtestConfigSnapshot,
        BacktestRunStatus status,
        Instant requestedAt,
        Instant startedAt,
        Instant finishedAt,
        String failureCode,
        String failureMessage,
        String summaryJson,
        Integer orderCount,
        Integer tradeCount,
        BigDecimal finalPositionQuantity,
        BigDecimal finalCashBalance,
        BigDecimal finalEquity,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal netPnl,
        BigDecimal totalFee,
        BigDecimal totalSlippage,
        String evaluationStatus,
        Instant evaluatedAt,
        BigDecimal totalReturnRate,
        BigDecimal maxDrawdownRate,
        BigDecimal winRate,
        BigDecimal sharpeRatio,
        String publishStatus,
        Instant publishedAt,
        String targetStrategyDefinitionId,
        String publishName,
        Instant createdAt,
        Instant updatedAt
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static GateFBacktestRunResponse from(
            BacktestRun backtestRun,
            EvaluationSummary evaluationSummary,
            PublishSummary publishSummary
    ) {
        JsonNode summaryNode = parseSummary(backtestRun.summaryJson());
        return new GateFBacktestRunResponse(
                backtestRun.backtestRunId(),
                backtestRun.backtestConfigId(),
                backtestRun.researchConfigId(),
                backtestRun.sourceStrategyId(),
                backtestRun.strategySnapshot(),
                backtestRun.backtestConfigSnapshot(),
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
                evaluationSummary == null ? null : evaluationSummary.maxDrawdownRate(),
                evaluationSummary == null ? null : evaluationSummary.winRate(),
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
