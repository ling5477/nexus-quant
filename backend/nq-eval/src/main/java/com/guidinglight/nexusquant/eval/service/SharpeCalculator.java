package com.guidinglight.nexusquant.eval.service;

import com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * SharpeCalculator 负责计算非年化 SharpeRatio。
 */
@Component
public class SharpeCalculator {

    private static final MathContext MATH_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);

    public BigDecimal calculate(List<SimPnlSnapshot> simPnlSnapshots) {
        if (simPnlSnapshots.size() < 2) {
            return BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        }
        List<BigDecimal> returns = new ArrayList<>();
        for (int index = 1; index < simPnlSnapshots.size(); index++) {
            BigDecimal previousEquity = simPnlSnapshots.get(index - 1).equity();
            BigDecimal currentEquity = simPnlSnapshots.get(index).equity();
            if (previousEquity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            returns.add(
                    currentEquity.subtract(previousEquity)
                            .divide(previousEquity, 18, RoundingMode.HALF_UP)
            );
        }
        if (returns.size() < 2) {
            return BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        }
        BigDecimal mean = returns.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 18, RoundingMode.HALF_UP);
        BigDecimal variance = returns.stream()
                .map(value -> value.subtract(mean).pow(2, MATH_CONTEXT))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 18, RoundingMode.HALF_UP);
        if (variance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        }
        BigDecimal stddev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(18, RoundingMode.HALF_UP);
        if (stddev.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(18, RoundingMode.HALF_UP);
        }
        return mean.divide(stddev, 18, RoundingMode.HALF_UP);
    }
}
