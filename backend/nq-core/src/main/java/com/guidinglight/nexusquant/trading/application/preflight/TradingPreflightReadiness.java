package com.guidinglight.nexusquant.trading.application.preflight;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * TradingPreflightReadiness 是 GateP Batch 4 的只读账户权限与风险前置基线 read model。
 *
 * <p>Why: 该 read model 聚合当前本地事实，解释为什么真实交易仍被阻断。它不包含
 * tradingReady、liveReady、authorizedForTrading 等授权字段，也不触发 adapter、RiskGate、订单、
 * credential 解密或外部网络 IO。
 */
public record TradingPreflightReadiness(
        TradingPreflightScope scope,
        String exchangeCode,
        Long accountId,
        String marketType,
        String symbol,
        String liveStatus,
        String realProviderStatus,
        String privateTradingStatus,
        String permissionProbeStatus,
        boolean credentialConfigured,
        String credentialStatus,
        List<TradingPreflightCredentialTypeSummary> credentialTypeSummary,
        boolean accountConfigured,
        String accountStatus,
        String dataQualityStatus,
        String riskPreflightStatus,
        List<TradingPreflightReason> blockers,
        List<TradingPreflightReason> warnings,
        List<String> requiredNextSteps,
        Instant generatedAt
) {
    public TradingPreflightReadiness {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        exchangeCode = Objects.requireNonNull(exchangeCode, "exchangeCode must not be null");
        marketType = Objects.requireNonNull(marketType, "marketType must not be null");
        liveStatus = Objects.requireNonNull(liveStatus, "liveStatus must not be null");
        realProviderStatus = Objects.requireNonNull(realProviderStatus, "realProviderStatus must not be null");
        privateTradingStatus = Objects.requireNonNull(privateTradingStatus, "privateTradingStatus must not be null");
        permissionProbeStatus = Objects.requireNonNull(permissionProbeStatus, "permissionProbeStatus must not be null");
        credentialStatus = Objects.requireNonNull(credentialStatus, "credentialStatus must not be null");
        accountStatus = Objects.requireNonNull(accountStatus, "accountStatus must not be null");
        dataQualityStatus = Objects.requireNonNull(dataQualityStatus, "dataQualityStatus must not be null");
        riskPreflightStatus = Objects.requireNonNull(riskPreflightStatus, "riskPreflightStatus must not be null");
        credentialTypeSummary = credentialTypeSummary == null ? List.of() : List.copyOf(credentialTypeSummary);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        requiredNextSteps = requiredNextSteps == null ? List.of() : List.copyOf(requiredNextSteps);
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }
}
