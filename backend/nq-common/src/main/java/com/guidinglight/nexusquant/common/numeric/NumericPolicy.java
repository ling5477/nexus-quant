package com.guidinglight.nexusquant.common.numeric;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * NumericPolicy 固化 Gate A 默认数值精度策略。
 *
 * Why:
 * docs/NUMERIC_POLICY.md 要求所有价格/数量/金额统一归一化，防止跨模块结果不一致。
 *
 * Edge:
 * 该策略仅为平台默认值；交易所特定 tickSize/lotSize 约束由 adapter 层二次处理。
 */
public final class NumericPolicy {

    private static final int DEFAULT_SCALE = 8;

    private NumericPolicy() {
        // 工具类不允许实例化。
    }

    /**
     * 归一化输入数值。
     *
     * @param type 数值业务类型，决定舍入模式
     * @param value 待归一化数值
     * @return 统一 scale=8 的 BigDecimal
     */
    public static BigDecimal normalize(NumericType type, BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }

        RoundingMode roundingMode = switch (type) {
            case PRICE, QTY -> RoundingMode.DOWN;
            case AMOUNT, FEE, PNL -> RoundingMode.HALF_UP;
        };
        return value.setScale(DEFAULT_SCALE, roundingMode);
    }

    /**
     * 可空版本归一化，便于处理市价单 price 为空等场景。
     */
    public static BigDecimal normalizeOrNull(NumericType type, BigDecimal value) {
        if (value == null) {
            return null;
        }
        return normalize(type, value);
    }
}
