package com.guidinglight.nexusquant.livecontrol.application;

import java.util.UUID;

/** 仅返回 materialization fact identity；不表达 LIVE、worker 或交易授权。 */
public record PilotScopeMaterializationResult(
        UUID sessionId,
        UUID pilotScopeId,
        UUID observationSetId,
        String pilotScopeHash
) {
}
