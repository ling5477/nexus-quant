package com.guidinglight.nexusquant.account.application.command;

/**
 * ExchangeAccountCreateCommand 表示创建交易账户的最小输入。
 */
public record ExchangeAccountCreateCommand(
        String exchangeCode,
        String tradeEnv,
        String accountAlias,
        String externalAccountRef
) {
}
