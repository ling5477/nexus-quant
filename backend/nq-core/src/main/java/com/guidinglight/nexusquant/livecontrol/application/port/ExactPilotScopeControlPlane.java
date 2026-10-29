package com.guidinglight.nexusquant.livecontrol.application.port;

import com.guidinglight.nexusquant.livecontrol.application.command.ExactPilotScopeControlCommand;
import com.guidinglight.nexusquant.livecontrol.application.model.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.model.ExactPilotScopeControlResult;

/** Root/operator-only adapter 使用的 single-purpose materialize-and-bind boundary；没有 consume 方法。 */
public interface ExactPilotScopeControlPlane {

    ExactPilotScopeControlResult materializeAndBind(
            AuthenticatedLiveControlActor creator,
            AuthenticatedLiveControlActor approver,
            ExactPilotScopeControlCommand command
    );
}
