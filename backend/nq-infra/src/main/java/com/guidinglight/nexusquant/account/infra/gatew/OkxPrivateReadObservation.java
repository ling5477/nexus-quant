package com.guidinglight.nexusquant.account.infra.gatew;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 非持久化、脱敏的 OKX private read-only observation。
 */
public record OkxPrivateReadObservation(
        OkxPrivateProbeStatus probeStatus,
        Instant observedAt,
        String source,
        Set<String> normalizedPermissions,
        boolean ipAllowlistConfigured,
        Integer assetCount,
        String dataCompleteness,
        List<String> blockers,
        List<String> warnings,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean tradingAuthorization,
        boolean liveDisabled,
        boolean orderSubmitted
) {
    public OkxPrivateReadObservation {
        Objects.requireNonNull(probeStatus, "probeStatus must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        source = "OKX_PRIVATE_READONLY";
        normalizedPermissions = Set.copyOf(normalizedPermissions == null ? Set.of() : normalizedPermissions);
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        diagnosticOnly = true;
        noSideEffect = true;
        notTradingAuthorization = true;
        tradingAuthorization = false;
        liveDisabled = true;
        orderSubmitted = false;
    }

    /** 兼容既有调用方；未显式提供 account-config 事实时按未配置 fail-closed。 */
    public OkxPrivateReadObservation(
            OkxPrivateProbeStatus probeStatus,
            Instant observedAt,
            String source,
            Set<String> normalizedPermissions,
            Integer assetCount,
            String dataCompleteness,
            List<String> blockers,
            List<String> warnings,
            boolean diagnosticOnly,
            boolean noSideEffect,
            boolean notTradingAuthorization,
            boolean tradingAuthorization,
            boolean liveDisabled,
            boolean orderSubmitted
    ) {
        this(
                probeStatus,
                observedAt,
                source,
                normalizedPermissions,
                false,
                assetCount,
                dataCompleteness,
                blockers,
                warnings,
                diagnosticOnly,
                noSideEffect,
                notTradingAuthorization,
                tradingAuthorization,
                liveDisabled,
                orderSubmitted
        );
    }
}
