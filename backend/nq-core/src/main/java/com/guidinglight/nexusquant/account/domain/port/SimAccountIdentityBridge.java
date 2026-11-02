package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;

import java.time.Instant;

/** 为正式 SIM 账户物化既有交易事实链使用的兼容账户身份。 */
public interface SimAccountIdentityBridge {
    long resolveOrCreateSim(ExchangeAccountSummary account, String traceId, Instant occurredAt);

    /** 仅注册编排可用的演示 PAPER 身份；普通交易所账户入口不得获得此能力。 */
    default long resolveOrCreateDemoSim(ExchangeAccountSummary account, String traceId, Instant occurredAt) {
        throw new IllegalStateException("DEMO_SIM_ACCOUNT_BRIDGE_UNAVAILABLE");
    }
}
