package com.guidinglight.nexusquant.account.application;

/**
 * ExchangeAccountNotFoundException 表示当前用户下找不到目标账户。
 */
public class ExchangeAccountNotFoundException extends RuntimeException {

    public ExchangeAccountNotFoundException(Long exchangeAccountId) {
        super("exchange account not found: " + exchangeAccountId);
    }
}
