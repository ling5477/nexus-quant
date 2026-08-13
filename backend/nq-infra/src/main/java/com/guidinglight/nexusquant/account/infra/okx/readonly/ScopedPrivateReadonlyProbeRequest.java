package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.livecontrol.deployment.ScopedCredentialCapability;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** 人工、非默认 private read-only diagnostic request。 */
public record ScopedPrivateReadonlyProbeRequest(
        Long ownerId,
        Long exchangeAccountId,
        Long credentialReference,
        String credentialType,
        ScopedCredentialCapability capability,
        OkxPrivateEnvironment environment,
        Collection<String> currencies,
        String expectedIp
) {
    public ScopedPrivateReadonlyProbeRequest {
        Objects.requireNonNull(capability, "capability must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        currencies = List.copyOf(currencies == null ? List.of() : currencies);
    }
}
