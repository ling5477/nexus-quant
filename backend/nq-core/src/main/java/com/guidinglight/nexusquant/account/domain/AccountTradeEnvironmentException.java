package com.guidinglight.nexusquant.account.domain;

/** 已登记账户环境不存在或与声明不一致时，阻止创建交易事实。 */
public final class AccountTradeEnvironmentException extends IllegalArgumentException {
    private final String code;

    public AccountTradeEnvironmentException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
