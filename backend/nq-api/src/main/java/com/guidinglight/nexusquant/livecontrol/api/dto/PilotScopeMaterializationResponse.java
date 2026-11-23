package com.guidinglight.nexusquant.livecontrol.api.dto;

import com.guidinglight.nexusquant.livecontrol.application.model.PilotScopeMaterializationResult;

import java.util.UUID;

/** materialization safe response；显式保持 execution/mutation/authorization 为 false/0。 */
public record PilotScopeMaterializationResponse(
        UUID sessionId,
        UUID pilotScopeId,
        UUID observationSetId,
        String pilotScopeHash,
        int executionIntentCount,
        int exchangeMutationCount,
        boolean liveAuthorized,
        boolean firstRealOrderAuthorized
) {
    public static PilotScopeMaterializationResponse from(PilotScopeMaterializationResult value) {
        return new PilotScopeMaterializationResponse(
                value.sessionId(), value.pilotScopeId(), value.observationSetId(), value.pilotScopeHash(),
                0, 0, false, false);
    }
}
