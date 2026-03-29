package com.guidinglight.nexusquant.research.domain.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * ExecutionPricingPolicy 提供 GateF-3 的最小成交定价规则。
 * <p>
 * Why:
 * 成交口径必须在代码里固定，本批统一采用“当前 bar close 成交”，避免测试和文档各说各话。
 */
@Component
public class ExecutionPricingPolicy {

    public BigDecimal executionPrice(BigDecimal barClosePrice) {
        return barClosePrice.setScale(18, RoundingMode.HALF_UP);
    }
}

