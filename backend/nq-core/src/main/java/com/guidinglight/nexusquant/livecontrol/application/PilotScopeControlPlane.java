package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopePreflightResult;

/**
 * GateY-6D authenticated control-plane boundary。实现只物化 prerequisite facts，不得创建执行意图。
 */
public interface PilotScopeControlPlane {

    PilotScopeMaterializationResult materialize(
            AuthenticatedLiveControlActor actor,
            PilotScopeMaterializationCommand command
    );

    default PilotScopeMaterializationResult materializeMinimal(
            AuthenticatedLiveControlActor actor,
            MinimalPilotMaterializationCommand command
    ) {
        throw new UnsupportedOperationException("minimal pilot materialization is not implemented");
    }

    OperatorApproval approve(AuthenticatedLiveControlActor actor, PilotScopeApprovalCommand command);

    PilotScopePreflightResult preflight(AuthenticatedLiveControlActor actor, java.util.UUID sessionId);
}
