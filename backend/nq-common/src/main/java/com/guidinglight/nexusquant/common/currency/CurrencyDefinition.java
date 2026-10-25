package com.guidinglight.nexusquant.common.currency;

import java.util.Set;

/** 注册只声明币种身份；支持状态由实际估值实现决定，不能据此生成结算或交易入口。 */
public record CurrencyDefinition(String code, String kind, String name) {
    public CurrencyDefinition {
        CurrencyCode.require(code);
        if (kind == null
                || !Set.of("STABLECOIN", "DIGITAL_ASSET", "FIAT").contains(kind)
                || name == null
                || name.isBlank()
                || name.length() > 100) {
            throw new IllegalArgumentException("INVALID_CURRENCY_DEFINITION");
        }
    }
}
