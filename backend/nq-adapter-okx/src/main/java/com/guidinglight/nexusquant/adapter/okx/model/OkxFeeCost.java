package com.guidinglight.nexusquant.adapter.okx.model;

import com.guidinglight.nexusquant.common.numeric.NumericPolicy;
import com.guidinglight.nexusquant.common.numeric.NumericType;

import java.math.BigDecimal;

/** 将OKX成交原费用转为canonical成本金额；原值负数扣费、正数返佣，不能取绝对值。 */
public final class OkxFeeCost {
    private OkxFeeCost() {}

    /** 与正式Trade/Fee八位HALF_UP一致，重放比较同一持久化精度；缺失金额不能伪装成真实零。 */
    public static BigDecimal amount(BigDecimal providerAmount) {
        if (providerAmount == null) throw new IllegalArgumentException("OKX_FILL_FEE_MISSING");
        return NumericPolicy.normalize(NumericType.FEE, providerAmount.negate());
    }
}
