package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;

import java.time.Instant;

/** 为正式 SIM 账户物化既有交易事实链使用的兼容账户身份。 */
public interface SimAccountIdentityBridge {
    long resolveOrCreateSim(ExchangeAccountSummary account, String traceId, Instant occurredAt);
}
