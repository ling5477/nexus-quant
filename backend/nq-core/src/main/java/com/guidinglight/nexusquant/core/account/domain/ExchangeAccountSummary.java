package com.guidinglight.nexusquant.core.account.domain;

/**
 * ExchangeAccountSummary 描述账户上下文与账户管理页需要的最小账户摘要。
 */
public record ExchangeAccountSummary(
        Long exchangeAccountId,
        Long legacyAccountId,
        Long ownerUserId,
        String exchangeCode,
        String tradeEnv,
        String accountAlias,
        String externalAccountRef,
        boolean isDefault,
        String status
) {
}
