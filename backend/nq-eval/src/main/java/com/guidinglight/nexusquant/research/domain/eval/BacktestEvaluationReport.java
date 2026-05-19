package com.guidinglight.nexusquant.research.domain.eval;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * BacktestEvaluationReport 表示 GateF-4 的 run 级评估报告。
 */
public record BacktestEvaluationReport(
        String evalReportId,
        String backtestRunId,
        EvaluationStatus evaluationStatus,
        BigDecimal initialCapital,
        BigDecimal finalCashBalance,
        BigDecimal finalPositionMarketValue,
        BigDecimal finalEquity,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal netPnl,
        BigDecimal totalReturnRate,
        BigDecimal totalReturn,
        BigDecimal annualizedReturn,
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
        BigDecimal profitLossRatio,
        BigDecimal sharpeRatio,
        String reportJson,
        String metricsJson,
        String failureCode,
        String failureMessage,
        Instant evaluatedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public BacktestEvaluationReport(
            String evalReportId,
            String backtestRunId,
            EvaluationStatus evaluationStatus,
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
            String failureMessage,
            Instant evaluatedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                evalReportId,
                backtestRunId,
                evaluationStatus,
                initialCapital,
                finalCashBalance,
                finalPositionMarketValue,
                finalEquity,
                realizedPnl,
                unrealizedPnl,
                netPnl,
                totalReturnRate,
                totalReturnRate,
                null,
                totalFee,
                totalSlippage,
                orderCount,
                tradeCount,
                winningTradeCount,
                losingTradeCount,
                flatTradeCount,
                winRate,
                maxDrawdown,
                maxDrawdownRate,
                null,
                sharpeRatio,
                reportJson,
                "{}",
                failureCode,
                failureMessage,
                evaluatedAt,
                createdAt,
                updatedAt
        );
    }

    public EvaluationSummary toSummary() {
        return new EvaluationSummary(
                evaluationStatus,
                evaluatedAt,
                finalEquity,
                netPnl,
                totalReturnRate,
                totalReturn,
                annualizedReturn,
                maxDrawdownRate,
                winRate,
                profitLossRatio,
                sharpeRatio,
                tradeCount,
                orderCount
        );
    }
}

