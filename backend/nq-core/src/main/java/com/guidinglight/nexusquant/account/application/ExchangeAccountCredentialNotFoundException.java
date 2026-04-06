package com.guidinglight.nexusquant.account.application;

/**
 * ExchangeAccountCredentialNotFoundException 表示当前账户不存在 active 凭证。
 */
public class ExchangeAccountCredentialNotFoundException extends RuntimeException {

    public ExchangeAccountCredentialNotFoundException(Long exchangeAccountId) {
        super("active credential not found for exchange account: " + exchangeAccountId);
    }
}
