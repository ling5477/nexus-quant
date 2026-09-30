package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.AccountTradeEnvironmentException;

/** 从已登记账户或受约束的隔离运行事实读取交易环境；请求字段只可作断言。 */
public interface AccountTradeEnvironmentAuthority {

    String resolve(Long tradingAccountId, String venue);

    default String requireMatching(Long tradingAccountId, String venue, String declaredEnvironment) {
        String canonical = resolve(tradingAccountId, venue);
        if (declaredEnvironment != null && !canonical.equalsIgnoreCase(declaredEnvironment)) {
            throw new AccountTradeEnvironmentException("ACCOUNT_TRADE_ENVIRONMENT_MISMATCH");
        }
        return canonical;
    }
}
