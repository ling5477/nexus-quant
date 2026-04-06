package com.guidinglight.nexusquant.research.domain.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * SlippageModel 提供 GateF-3 的最小滑点金额计算。
 */
public class SlippageModel {

    public BigDecimal calculate(BigDecimal referencePrice, BigDecimal quantity, BigDecimal slippageBps) {
        BigDecimal bpsFactor = slippageBps.divide(new BigDecimal("10000"), 18, RoundingMode.HALF_UP);
        return referencePrice.multiply(quantity).multiply(bpsFactor).setScale(18, RoundingMode.HALF_UP);
    }
}

