package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.eval.model.BacktestEvaluationReport;
import com.guidinglight.nexusquant.eval.model.EvaluationSummary;

import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * BacktestEvaluationResponse 描述接口响应体。
 */
@Schema(name = "BacktestEvaluationResponse", description = "接口响应体")
public record BacktestEvaluationResponse(
        @Schema(description = "evalReportId")
        String evalReportId,
        @Schema(description = "backtestRunId")
        String backtestRunId,
        @Schema(description = "evaluationStatus")
        String evaluationStatus,
        @Schema(description = "evaluatedAt")
        Instant evaluatedAt,
        @Schema(description = "initialCapital")
        BigDecimal initialCapital,
        @Schema(description = "finalCashBalance")
        BigDecimal finalCashBalance,
        @Schema(description = "finalPositionMarketValue")
        BigDecimal finalPositionMarketValue,
        @Schema(description = "finalEquity")
        BigDecimal finalEquity,
        @Schema(description = "realizedPnl")
        BigDecimal realizedPnl,
        @Schema(description = "unrealizedPnl")
        BigDecimal unrealizedPnl,
        @Schema(description = "netPnl")
        BigDecimal netPnl,
        @Schema(description = "totalReturnRate")
        BigDecimal totalReturnRate,
        @Schema(description = "totalFee")
        BigDecimal totalFee,
        @Schema(description = "totalSlippage")
        BigDecimal totalSlippage,
        @Schema(description = "orderCount")
        Integer orderCount,
        @Schema(description = "tradeCount")
        Integer tradeCount,
        @Schema(description = "winningTradeCount")
        Integer winningTradeCount,
        @Schema(description = "losingTradeCount")
        Integer losingTradeCount,
        @Schema(description = "flatTradeCount")
        Integer flatTradeCount,
        @Schema(description = "winRate")
        BigDecimal winRate,
        @Schema(description = "maxDrawdown")
        BigDecimal maxDrawdown,
        @Schema(description = "maxDrawdownRate")
        BigDecimal maxDrawdownRate,
        @Schema(description = "sharpeRatio")
        BigDecimal sharpeRatio,
        @Schema(description = "reportJson")
        String reportJson,
        @Schema(description = "failureCode")
        String failureCode,
        @Schema(description = "failureMessage")
        String failureMessage
) {
    public static BacktestEvaluationResponse from(BacktestEvaluationReport report) {
        return new BacktestEvaluationResponse(
                report.evalReportId(),
                report.backtestRunId(),
                report.evaluationStatus().name(),
                report.evaluatedAt(),
                report.initialCapital(),
                report.finalCashBalance(),
                report.finalPositionMarketValue(),
                report.finalEquity(),
                report.realizedPnl(),
                report.unrealizedPnl(),
                report.netPnl(),
                report.totalReturnRate(),
                report.totalFee(),
                report.totalSlippage(),
                report.orderCount(),
                report.tradeCount(),
                report.winningTradeCount(),
                report.losingTradeCount(),
                report.flatTradeCount(),
                report.winRate(),
                report.maxDrawdown(),
                report.maxDrawdownRate(),
                report.sharpeRatio(),
                report.reportJson(),
                report.failureCode(),
                report.failureMessage()
        );
    }

    public static EvaluationSummary summary(BacktestEvaluationReport report) {
        return report == null ? null : report.toSummary();
    }
}
