package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialCapability;
import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialReference.RemoteIpVerificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Credential-scoped private read-only probe 的脱敏结果；不包含 provider raw payload 或余额值。
 */
public record ScopedPrivateReadonlyProbeObservation(
        OkxPrivateProbeStatus probeStatus,
        Instant observedAt,
        long credentialReference,
        ScopedCredentialCapability capability,
        Set<String> normalizedPermissions,
        boolean ipAllowlistConfigured,
        RemoteIpVerificationStatus remoteIpVerificationStatus,
        Integer assetCount,
        List<String> blockers,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean tradingAuthorization,
        boolean liveDisabled,
        boolean orderSubmitted
) {
    public ScopedPrivateReadonlyProbeObservation {
        Objects.requireNonNull(probeStatus);
        Objects.requireNonNull(observedAt);
        Objects.requireNonNull(capability);
        normalizedPermissions = Set.copyOf(normalizedPermissions == null ? Set.of() : normalizedPermissions);
        Objects.requireNonNull(remoteIpVerificationStatus);
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
        diagnosticOnly = true;
        noSideEffect = true;
        notTradingAuthorization = true;
        tradingAuthorization = false;
        liveDisabled = true;
        orderSubmitted = false;
    }
}
