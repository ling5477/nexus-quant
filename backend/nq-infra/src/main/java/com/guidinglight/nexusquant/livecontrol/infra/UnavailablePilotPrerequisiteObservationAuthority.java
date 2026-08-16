package com.guidinglight.nexusquant.livecontrol.infra;

import com.guidinglight.nexusquant.livecontrol.application.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;

import java.time.Instant;

import org.springframework.stereotype.Component;

/** GateY-6D production 默认边界：真实 trusted observation source 未实现时永久 fail closed。 */
@Component
public final class UnavailablePilotPrerequisiteObservationAuthority
        implements PilotPrerequisiteObservationAuthority {

    @Override
    public PilotObservationSet resolveTrustedObservationSet(
            LiveSession session,
            PilotScopeBinding scope,
            Instant resolvedAt
    ) {
        throw new LiveControlException(
                "TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE",
                "trusted prerequisite observation authority is unavailable"
        );
    }
}
