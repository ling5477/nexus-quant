package com.guidinglight.nexusquant.common.currency;

/** 稳定的原币标识；格式校验不等于注册、估值支持或交易权限。 */
public record CurrencyCode(String value) {
    public CurrencyCode {
        if (value == null || !value.matches("[A-Z0-9]{2,32}")) {
            throw new IllegalArgumentException("INVALID_CURRENCY_CODE");
        }
    }

    public static String require(String value) {
        return new CurrencyCode(value).value();
    }
}
