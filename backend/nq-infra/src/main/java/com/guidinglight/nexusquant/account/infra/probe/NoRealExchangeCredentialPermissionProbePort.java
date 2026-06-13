package com.guidinglight.nexusquant.account.infra.probe;

import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeRequest;
import com.guidinglight.nexusquant.account.domain.ExchangeCredentialPermissionProbeResult;
import com.guidinglight.nexusquant.account.domain.port.ExchangeCredentialPermissionProbePort;

import java.time.Instant;

/**
 * NoRealExchangeCredentialPermissionProbePort 是本轮默认 no-real-exchange port 实现。
 *
 * <p>Why: GateJ completed / GateK-PLAN 阶段允许先落地 Service/API/test 边界，但禁止真实交易所
 * HTTP 探活。本实现不创建 HTTP client、不访问 OKX/Binance、不下单、不撤单、不转账、不提现，
 * 仅返回脱敏 SKIPPED，供生产默认装配和测试隔离使用。</p>
 */
public class NoRealExchangeCredentialPermissionProbePort implements ExchangeCredentialPermissionProbePort {

    @Override
    public ExchangeCredentialPermissionProbeResult probe(ExchangeCredentialPermissionProbeRequest request) {
        Instant now = Instant.now();
        return ExchangeCredentialPermissionProbeResult.skipped(
                request.exchange(),
                request.credentialType(),
                "REAL_EXCHANGE_PROBE_DISABLED",
                request.traceId(),
                request.traceId(),
                now,
                now
        );
    }
}
