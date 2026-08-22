package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.util.Objects;
import java.util.UUID;

/** Control surface 的 sanitized result；固定不授权交易、不消费 binding。 */
public record ExactPilotScopeControlResult(
        UUID sessionId,
        UUID pilotScopeId,
        UUID observationSetId,
        String pilotScopeHash,
        String exactScopeDigest,
        UUID bindingId,
        String bindingDigest,
        ExactPilotBinding.Lifecycle lifecycle,
        boolean bindingConsumed,
        boolean tradingAuthorized,
        boolean exchangeMutation
) {
    public ExactPilotScopeControlResult {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(pilotScopeId, "pilotScopeId must not be null");
        Objects.requireNonNull(observationSetId, "observationSetId must not be null");
        Objects.requireNonNull(pilotScopeHash, "pilotScopeHash must not be null");
        Objects.requireNonNull(exactScopeDigest, "exactScopeDigest must not be null");
        Objects.requireNonNull(bindingId, "bindingId must not be null");
        Objects.requireNonNull(bindingDigest, "bindingDigest must not be null");
        if (lifecycle != ExactPilotBinding.Lifecycle.VERIFIED
                || bindingConsumed || tradingAuthorized || exchangeMutation) {
            throw new IllegalArgumentException("control surface result must be verified and non-trading");
        }
    }
}
