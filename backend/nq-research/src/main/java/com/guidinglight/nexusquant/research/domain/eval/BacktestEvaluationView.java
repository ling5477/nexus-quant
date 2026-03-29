package com.guidinglight.nexusquant.research.domain.eval;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * BacktestEvaluationView 是研究域消费的最小评估投影。
 * <p>
 * Why:
 * nq-research 需要读取评估结果做 publish 校验，但不能依赖 nq-eval 模块本身，避免形成模块环。
 */
public record BacktestEvaluationView(
        String evalReportId,
        String backtestRunId,
        String evaluationStatus,
        Instant evaluatedAt,
        BigDecimal finalEquity,
        BigDecimal netPnl,
        BigDecimal totalReturnRate,
        BigDecimal maxDrawdownRate,
        BigDecimal winRate,
        BigDecimal sharpeRatio,
        Integer tradeCount,
        Integer orderCount,
        String reportJson,
        String failureCode,
        String failureMessage
) {
}

