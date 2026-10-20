package com.guidinglight.nexusquant.strategy.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** 现货买单只预留本金及正费用；返佣必须等正式成交到账，不能提前提供购买力。 */
public final class SpotFeeReserve {
    private SpotFeeReserve() {}

    /** 收费和返佣都限制在名义金额的一倍以内；零费率有效，不替换成默认费用。 */
    public static void validateRate(BigDecimal rate) {
        Objects.requireNonNull(rate, "feeRate");
        if (rate.abs().compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("signed spot fee rate must be within (-1, 1)");
        }
    }

    /** 每笔本金按正式AMOUNT八位四舍五入，正费用上取整；负费用不抵减本金或其他在途预留。 */
    public static BigDecimal buyCost(BigDecimal notional, BigDecimal rate) {
        Objects.requireNonNull(notional, "notional");
        validateRate(rate);
        if (notional.signum() < 0)
            throw new IllegalArgumentException("notional must not be negative");
        // 本金必须逐单舍入，不能先相加再舍入；否则两笔半单位边界会比实际现金扣款少预留一单位。
        BigDecimal principal = notional.setScale(8, RoundingMode.HALF_UP);
        return principal.add(
                notional.multiply(rate.max(BigDecimal.ZERO)).setScale(8, RoundingMode.CEILING));
    }
}
