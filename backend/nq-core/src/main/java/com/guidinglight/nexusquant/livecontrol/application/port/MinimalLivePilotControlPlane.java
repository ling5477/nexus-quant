package com.guidinglight.nexusquant.livecontrol.application.port;

import com.guidinglight.nexusquant.livecontrol.application.command.MinimalLivePilotCommand;
import com.guidinglight.nexusquant.livecontrol.application.model.MinimalLivePilotPermit;

/** Root/operator single-purpose minimal pilot boundary。 */
public interface MinimalLivePilotControlPlane {

    MinimalLivePilotPermit prepare(MinimalLivePilotCommand command);
}
