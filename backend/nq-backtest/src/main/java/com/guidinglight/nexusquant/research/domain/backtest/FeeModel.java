package com.guidinglight.nexusquant.research.domain.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * FeeModel 提供 GateF-3 的最小手续费计算。
 * <p>
 * Why:
 * 手续费必须成为独立口径对象，后续费率模型扩展时才不需要回头拆模拟执行主链。
 */
public class FeeModel {

    public BigDecimal calculate(BigDecimal tradeNotional, BigDecimal feeRate) {
        return tradeNotional.multiply(feeRate).setScale(18, RoundingMode.HALF_UP);
    }
}

