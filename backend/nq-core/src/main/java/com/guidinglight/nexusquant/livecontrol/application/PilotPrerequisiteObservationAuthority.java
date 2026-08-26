package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-owned prerequisite observation authority。调用方只能提交 immutable scope，不能提交 observation value。
 */
public interface PilotPrerequisiteObservationAuthority {

    PilotObservationSet resolveTrustedObservationSet(
            LiveSession session,
            PilotScopeBinding scope,
            Instant resolvedAt
    );

    /**
     * OPERATOR_PILOT 专用 bootstrap：trusted authority 必须从同一次只读采集同时产生 scope 与 observations。
     */
    default TrustedOperatorPilotBootstrap bootstrapTrustedOperatorPilotScope(
            LiveSession session,
            UUID pilotScopeId,
            long createdBy,
            Instant resolvedAt
    ) {
        throw new com.guidinglight.nexusquant.livecontrol.domain.LiveControlException(
                "TRUSTED_OPERATOR_PILOT_SCOPE_BOOTSTRAP_UNAVAILABLE",
                "trusted operator pilot scope bootstrap is unavailable"
        );
    }

    record TrustedOperatorPilotBootstrap(
            PilotScopeBinding scopeBinding,
            PilotObservationSet observationSet
    ) {
        public TrustedOperatorPilotBootstrap {
            Objects.requireNonNull(scopeBinding, "scopeBinding must not be null");
            Objects.requireNonNull(observationSet, "observationSet must not be null");
            if (!scopeBinding.id().equals(observationSet.pilotScopeId())) {
                throw new IllegalArgumentException("bootstrap scope and observation identity mismatch");
            }
        }
    }
}
