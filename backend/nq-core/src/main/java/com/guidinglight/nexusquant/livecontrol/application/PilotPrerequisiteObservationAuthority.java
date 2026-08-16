package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;

import java.time.Instant;

/**
 * Server-owned prerequisite observation authority。调用方只能提交 immutable scope，不能提交 observation value。
 */
public interface PilotPrerequisiteObservationAuthority {

    PilotObservationSet resolveTrustedObservationSet(
            LiveSession session,
            PilotScopeBinding scope,
            Instant resolvedAt
    );
}
