package com.guidinglight.nexusquant.livecontrol.application.port;

import com.guidinglight.nexusquant.livecontrol.application.command.ExactPilotBindingCommand;
import com.guidinglight.nexusquant.livecontrol.application.command.ExactPilotBindingConsumptionCommand;
import com.guidinglight.nexusquant.livecontrol.application.model.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.model.ExactPilotBindingConsumption;
import com.guidinglight.nexusquant.livecontrol.application.model.ExactPilotBindingValidation;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.util.UUID;

/** Exact binding admission boundary；不暴露 provider 或交易执行操作。 */
public interface ExactPilotBindingControlPlane {

    ExactPilotBinding create(AuthenticatedLiveControlActor actor, ExactPilotBindingCommand command);

    ExactPilotBindingValidation validate(AuthenticatedLiveControlActor actor, UUID sessionId, UUID bindingId);

    ExactPilotBindingConsumption consume(
            AuthenticatedLiveControlActor actor,
            ExactPilotBindingConsumptionCommand command
    );
}
