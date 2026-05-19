package com.guidinglight.nexusquant.research.domain.eval;

import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;


/**
 * TradeOutcomeCalculator 负责基于已闭合成交重建最小 trade outcomes。
 */
public class TradeOutcomeCalculator {

    public Result calculate(List<SimTrade> simTrades) {
        BigDecimal openQuantity = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        BigDecimal averageEntryPrice = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        BigDecimal openEntryCosts = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        int winning = 0;
        int losing = 0;
        int flat = 0;
        BigDecimal grossProfit = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        BigDecimal grossLoss = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);

        for (SimTrade simTrade : simTrades) {
            BigDecimal tradeCosts = simTrade.feeAmount().add(simTrade.slippageAmount()).setScale(18, RoundingMode.HALF_UP);
            if ("BUY".equalsIgnoreCase(simTrade.side())) {
                BigDecimal nextQuantity = openQuantity.add(simTrade.quantity()).setScale(18, RoundingMode.HALF_UP);
                averageEntryPrice = nextQuantity.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP)
                        : averageEntryPrice.multiply(openQuantity)
                        .add(simTrade.tradePrice().multiply(simTrade.quantity()))
                        .divide(nextQuantity, 18, RoundingMode.HALF_UP);
                openEntryCosts = openEntryCosts.add(tradeCosts).setScale(18, RoundingMode.HALF_UP);
                openQuantity = nextQuantity;
                continue;
            }

            if (openQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal quantityToClose = simTrade.quantity().min(openQuantity).setScale(18, RoundingMode.HALF_UP);
            BigDecimal proportion = quantityToClose.divide(openQuantity, 18, RoundingMode.HALF_UP);
            BigDecimal allocatedEntryCost = openEntryCosts.multiply(proportion).setScale(18, RoundingMode.HALF_UP);
            BigDecimal grossPnl = simTrade.tradePrice().subtract(averageEntryPrice)
                    .multiply(quantityToClose)
                    .setScale(18, RoundingMode.HALF_UP);
            BigDecimal netOutcome = grossPnl.subtract(allocatedEntryCost).subtract(tradeCosts).setScale(18, RoundingMode.HALF_UP);
            if (netOutcome.compareTo(BigDecimal.ZERO) > 0) {
                winning++;
                grossProfit = grossProfit.add(netOutcome).setScale(18, RoundingMode.HALF_UP);
            } else if (netOutcome.compareTo(BigDecimal.ZERO) < 0) {
                losing++;
                grossLoss = grossLoss.add(netOutcome.abs()).setScale(18, RoundingMode.HALF_UP);
            } else {
                flat++;
            }
            openQuantity = openQuantity.subtract(quantityToClose).setScale(18, RoundingMode.HALF_UP);
            openEntryCosts = openEntryCosts.subtract(allocatedEntryCost).setScale(18, RoundingMode.HALF_UP);
            if (openQuantity.compareTo(BigDecimal.ZERO) == 0) {
                averageEntryPrice = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
                openEntryCosts = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
            }
        }
        return new Result(winning, losing, flat, grossProfit, grossLoss);
    }

    public record Result(
            int winningTradeCount,
            int losingTradeCount,
            int flatTradeCount,
            BigDecimal grossProfit,
            BigDecimal grossLoss
    ) {
        public BigDecimal winRate() {
            int total = winningTradeCount + losingTradeCount + flatTradeCount;
            if (total == 0) {
                return BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(winningTradeCount)
                    .divide(BigDecimal.valueOf(total), 18, RoundingMode.HALF_UP);
        }

        /**
         * 计算盈亏比。
         * Why:
         * GateI-2 需要评估报告直接展示 profit/loss ratio；亏损总额为 0 时返回 0，
         * 避免把无亏损样本误表达成无限大并影响前端数值展示。
         */
        public BigDecimal profitLossRatio() {
            if (grossLoss.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
            }
            return grossProfit.divide(grossLoss, 18, RoundingMode.HALF_UP);
        }
    }
}


