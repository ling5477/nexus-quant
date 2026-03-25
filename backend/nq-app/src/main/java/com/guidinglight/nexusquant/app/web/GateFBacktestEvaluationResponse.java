package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.eval.model.BacktestEvaluationReport;
import com.guidinglight.nexusquant.eval.model.EvaluationSummary;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GateFBacktestEvaluationResponse 是回测评估响应体。
 */
public record GateFBacktestEvaluationResponse(
        String evalReportId,
        String backtestRunId,
        String evaluationStatus,
        Instant evaluatedAt,
        BigDecimal initialCapital,
        BigDecimal finalCashBalance,
        BigDecimal finalPositionMarketValue,
        BigDecimal finalEquity,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal netPnl,
        BigDecimal totalReturnRate,
        BigDecimal totalFee,
        BigDecimal totalSlippage,
        Integer orderCount,
        Integer tradeCount,
        Integer winningTradeCount,
        Integer losingTradeCount,
        Integer flatTradeCount,
        BigDecimal winRate,
        BigDecimal maxDrawdown,
        BigDecimal maxDrawdownRate,
        BigDecimal sharpeRatio,
        String reportJson,
        String failureCode,
        String failureMessage
) {
    public static GateFBacktestEvaluationResponse from(BacktestEvaluationReport report) {
        return new GateFBacktestEvaluationResponse(
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
