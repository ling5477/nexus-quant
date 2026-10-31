package com.guidinglight.nexusquant.account.domain;

/** 只包含账户元数据命令；凭据、订单、资金和交易授权不属于本合同。 */
public record AccountCommand(
        Type type,
        Long accountId,
        String exchangeCode,
        String tradeEnv,
        String accountAlias,
        String externalAccountRef) {
    public enum Type {
        CREATE,
        UPDATE,
        ENABLE,
        DISABLE,
        SET_DEFAULT,
        RESOLVE_SIM
    }
}
