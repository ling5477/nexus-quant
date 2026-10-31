package com.guidinglight.nexusquant.research.domain.eval;

import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;


/**
 * DrawdownCalculator 负责基于 equity 序列计算最大回撤。
 */
public class DrawdownCalculator {

    public Result calculate(List<SimPnlSnapshot> simPnlSnapshots) {
        BigDecimal peak = simPnlSnapshots.getFirst().equity();
        BigDecimal maxDrawdown = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        BigDecimal maxDrawdownRate = BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        for (SimPnlSnapshot simPnlSnapshot : simPnlSnapshots) {
            if (simPnlSnapshot.equity().compareTo(peak) > 0) {
                peak = simPnlSnapshot.equity();
            }
            BigDecimal drawdown = peak.subtract(simPnlSnapshot.equity()).setScale(18, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
                maxDrawdownRate = peak.compareTo(BigDecimal.ZERO) <= 0
                        ? BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP)
                        : drawdown.divide(peak, 18, RoundingMode.HALF_UP);
            }
        }
        return new Result(maxDrawdown, maxDrawdownRate);
    }

    public record Result(BigDecimal maxDrawdown, BigDecimal maxDrawdownRate) {
    }
}


