package com.guidinglight.nexusquant.livecontrol.application.port;

import com.guidinglight.nexusquant.livecontrol.application.command.MinimalPilotMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.application.command.PilotScopeApprovalCommand;
import com.guidinglight.nexusquant.livecontrol.application.command.PilotScopeMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.application.model.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.model.PilotScopeMaterializationResult;

import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopePreflightResult;
import java.util.UUID;

/**
 * 受控实盘执行 authenticated control-plane boundary。实现只物化 prerequisite facts，不得创建执行意图。
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

    PilotScopePreflightResult preflight(AuthenticatedLiveControlActor actor, UUID sessionId);
}
