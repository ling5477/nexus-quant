package com.guidinglight.nexusquant.research.domain.eval;

import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * TradeOutcomeCalculator 负责基于已闭合成交重建最小 trade outcomes。
 */
@Component
public class TradeOutcomeCalculator {

    public Result calculate(List<SimTrade> simTrades) {
        BigDecimal openQuantity = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        BigDecimal averageEntryPrice = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        BigDecimal openEntryCosts = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        int winning = 0;
        int losing = 0;
        int flat = 0;

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
            } else if (netOutcome.compareTo(BigDecimal.ZERO) < 0) {
                losing++;
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
        return new Result(winning, losing, flat);
    }

    public record Result(int winningTradeCount, int losingTradeCount, int flatTradeCount) {
        public BigDecimal winRate() {
            int total = winningTradeCount + losingTradeCount + flatTradeCount;
            if (total == 0) {
                return BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(winningTradeCount)
                    .divide(BigDecimal.valueOf(total), 18, RoundingMode.HALF_UP);
        }
    }
}


