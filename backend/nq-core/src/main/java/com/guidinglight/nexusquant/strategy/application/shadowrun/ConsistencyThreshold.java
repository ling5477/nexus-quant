package com.guidinglight.nexusquant.strategy.application.shadowrun;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Shadow consistency report 的最小比较阈值。
 *
 * <p>职责：集中表达 GateR-5 skeleton 当前支持的 count 绝对差阈值和后续 decimal 指标阈值。
 * Why：阈值由调用方显式传入，service 不读取外部配置、不查询真实交易系统，也不把阈值解释为
 * 风控放行或交易授权。
 *
 * @param countTolerance   count 类指标允许的绝对差值，必须大于等于 0
 * @param decimalTolerance decimal 类指标预留容忍度，必须大于等于 0；GateR-5 第一版仅持久化该设置
 */
public record ConsistencyThreshold(int countTolerance, BigDecimal decimalTolerance) {

    public ConsistencyThreshold {
        if (countTolerance < 0) {
            throw new IllegalArgumentException("countTolerance must not be negative");
        }
        decimalTolerance = Objects.requireNonNullElse(decimalTolerance, BigDecimal.ZERO);
        if (decimalTolerance.signum() < 0) {
            throw new IllegalArgumentException("decimalTolerance must not be negative");
        }
    }

    /**
     * 返回严格阈值；count 和 decimal 指标均要求完全一致。
     */
    public static ConsistencyThreshold strict() {
        return new ConsistencyThreshold(0, BigDecimal.ZERO);
    }
}
