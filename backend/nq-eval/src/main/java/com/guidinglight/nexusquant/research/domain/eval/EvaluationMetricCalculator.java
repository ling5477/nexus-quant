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
            TradeOutcomeCalculator tradeOutcomeCalculator) {
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
            Instant evaluatedAt) {
        return calculate(
                backtestRunId,
                initialCapital,
                simOrders,
                simTrades,
                simPositions,
                simPnlSnapshots,
                evaluatedAt,
                false);
    }

    /** 成交价是否已消费滑点由运行执行合同明确传入；不能以滑点非零或存在原币字段推断。 */
    public BacktestEvaluationReport calculate(
            String backtestRunId,
            BigDecimal initialCapital,
            List<SimOrder> simOrders,
            List<SimTrade> simTrades,
            List<SimPosition> simPositions,
            List<SimPnlSnapshot> simPnlSnapshots,
            Instant evaluatedAt,
            boolean slippageIncludedInFillPrice) {
        SimPnlSnapshot finalSnapshot = simPnlSnapshots.getLast();
        SimPosition finalPosition = simPositions.isEmpty() ? null : simPositions.getLast();
        DrawdownCalculator.Result drawdown = drawdownCalculator.calculate(simPnlSnapshots);
        BigDecimal sharpeRatio = sharpeCalculator.calculate(simPnlSnapshots);
        TradeOutcomeCalculator.Result tradeOutcome =
                tradeOutcomeCalculator.calculate(simTrades, slippageIncludedInFillPrice);
        BigDecimal totalReturnRate =
                finalSnapshot.netPnl().divide(initialCapital, 18, RoundingMode.HALF_UP);
        AnnualizedReturnCalculator.Result annualized =
                AnnualizedReturnCalculator.calculate(
                        totalReturnRate,
                        simPnlSnapshots.getFirst().snapshotTime(),
                        finalSnapshot.snapshotTime());
        BigDecimal annualizedReturn = annualized.value();
        BigDecimal profitLossRatio = tradeOutcome.profitLossRatio();
        String metricsJson =
                """
                {"totalReturn":"%s","annualizedReturn":"%s","annualizedReturnStatus":"%s","maxDrawdown":"%s","maxDrawdownRate":"%s","winRate":"%s","profitLossRatio":"%s","tradeCount":%d,"sharpeRatio":"%s"}
                """
                        .formatted(
                                totalReturnRate.toPlainString(),
                                annualizedReturn == null ? "" : annualizedReturn.toPlainString(),
                                annualized.status().name(),
                                drawdown.maxDrawdown().toPlainString(),
                                drawdown.maxDrawdownRate().toPlainString(),
                                tradeOutcome.winRate().toPlainString(),
                                profitLossRatio.toPlainString(),
                                simTrades.size(),
                                sharpeRatio.toPlainString())
                        .replace("\n", "")
                        .trim();
        String reportJson =
                """
                {"backtestRunId":"%s","evaluationStatus":"SUCCEEDED","finalEquity":"%s","netPnl":"%s","maxDrawdownRate":"%s","winRate":"%s","profitLossRatio":"%s","sharpeRatio":"%s"}
                """
                        .formatted(
                                backtestRunId,
                                finalSnapshot.equity().toPlainString(),
                                finalSnapshot.netPnl().toPlainString(),
                                drawdown.maxDrawdownRate().toPlainString(),
                                tradeOutcome.winRate().toPlainString(),
                                profitLossRatio.toPlainString(),
                                sharpeRatio.toPlainString())
                        .replace("\n", "")
                        .trim();
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
                totalReturnRate,
                annualizedReturn,
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
                profitLossRatio,
                sharpeRatio,
                reportJson,
                metricsJson,
                null,
                null,
                evaluatedAt,
                evaluatedAt,
                evaluatedAt);
    }
}
