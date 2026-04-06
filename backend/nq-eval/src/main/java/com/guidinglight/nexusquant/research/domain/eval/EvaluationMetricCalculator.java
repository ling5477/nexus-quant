package com.guidinglight.nexusquant.research.domain.eval;

import com.guidinglight.nexusquant.research.domain.backtest.SimOrder;
import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.SimPosition;
import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;
import com.guidinglight.nexusquant.research.domain.eval.EvaluationStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


/**
 * EvaluationMetricCalculator 负责基于 sim_* 事实计算 run 级评估报告。
 */
public class EvaluationMetricCalculator {

    private final DrawdownCalculator drawdownCalculator;
    private final SharpeCalculator sharpeCalculator;
    private final TradeOutcomeCalculator tradeOutcomeCalculator;

    public EvaluationMetricCalculator(
            DrawdownCalculator drawdownCalculator,
            SharpeCalculator sharpeCalculator,
            TradeOutcomeCalculator tradeOutcomeCalculator
    ) {
        this.drawdownCalculator = drawdownCalculator;
        this.sharpeCalculator = sharpeCalculator;
        this.tradeOutcomeCalculator = tradeOutcomeCalculator;
    }

    public BacktestEvaluationReport calculate(
            String backtestRunId,
            BigDecimal initialCapital,
            List<SimOrder> simOrders,
            List<SimTrade> simTrades,
            List<SimPosition> simPositions,
            List<SimPnlSnapshot> simPnlSnapshots,
            Instant evaluatedAt
    ) {
        SimPnlSnapshot finalSnapshot = simPnlSnapshots.getLast();
        SimPosition finalPosition = simPositions.isEmpty() ? null : simPositions.getLast();
        DrawdownCalculator.Result drawdown = drawdownCalculator.calculate(simPnlSnapshots);
        BigDecimal sharpeRatio = sharpeCalculator.calculate(simPnlSnapshots);
        TradeOutcomeCalculator.Result tradeOutcome = tradeOutcomeCalculator.calculate(simTrades);
        BigDecimal totalReturnRate = finalSnapshot.netPnl().divide(initialCapital, 18, RoundingMode.HALF_UP);
        String reportJson = """
                {"backtestRunId":"%s","evaluationStatus":"SUCCEEDED","finalEquity":"%s","netPnl":"%s","maxDrawdownRate":"%s","winRate":"%s","sharpeRatio":"%s"}
                """.formatted(
                backtestRunId,
                finalSnapshot.equity().toPlainString(),
                finalSnapshot.netPnl().toPlainString(),
                drawdown.maxDrawdownRate().toPlainString(),
                tradeOutcome.winRate().toPlainString(),
                sharpeRatio.toPlainString()
        ).replace("\n", "").trim();
        return new BacktestEvaluationReport(
                "eval-" + UUID.randomUUID(),
                backtestRunId,
                EvaluationStatus.SUCCEEDED,
                initialCapital,
                finalSnapshot.cashBalance(),
                finalSnapshot.positionMarketValue(),
                finalSnapshot.equity(),
                finalSnapshot.realizedPnl(),
                finalSnapshot.unrealizedPnl(),
                finalSnapshot.netPnl(),
                totalReturnRate,
                finalSnapshot.totalFee(),
                finalSnapshot.totalSlippage(),
                simOrders.size(),
                simTrades.size(),
                tradeOutcome.winningTradeCount(),
                tradeOutcome.losingTradeCount(),
                tradeOutcome.flatTradeCount(),
                tradeOutcome.winRate(),
                drawdown.maxDrawdown(),
                drawdown.maxDrawdownRate(),
                sharpeRatio,
                reportJson,
                null,
                null,
                evaluatedAt,
                evaluatedAt,
                evaluatedAt
        );
    }
}


