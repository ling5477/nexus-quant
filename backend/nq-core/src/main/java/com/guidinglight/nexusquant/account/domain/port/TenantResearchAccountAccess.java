package com.guidinglight.nexusquant.account.domain.port;

/** 研究定义只验证同租户 PAPER 归属，不授予账户交易环境或执行资格。 */
public interface TenantResearchAccountAccess {
    void requirePaperResearchAccount(long accountId);
}
