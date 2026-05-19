package com.guidinglight.nexusquant.research.domain.eval;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * EvaluationSummary 表示 run detail / run list 使用的最小评估摘要。
 */
public record EvaluationSummary(
        EvaluationStatus evaluationStatus,
        Instant evaluatedAt,
        BigDecimal finalEquity,
        BigDecimal netPnl,
        BigDecimal totalReturnRate,
        BigDecimal totalReturn,
        BigDecimal annualizedReturn,
        BigDecimal maxDrawdownRate,
        BigDecimal winRate,
        BigDecimal profitLossRatio,
        BigDecimal sharpeRatio,
        Integer tradeCount,
        Integer orderCount
) {
}

