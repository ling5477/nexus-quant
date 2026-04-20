package com.guidinglight.nexusquant.account.domain;

/**
 * ExchangeAccountSummary 描述账户上下文与账户管理页需要的最小账户摘要。
 */
public record ExchangeAccountSummary(
        Long exchangeAccountId,
        /*
         * 过渡兼容字段：用于把正式 exchangeAccountId 映射到历史 trading account_id。
         * 它不是正式账户上下文主键；新增链路必须优先使用 exchangeAccountId。
         */
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