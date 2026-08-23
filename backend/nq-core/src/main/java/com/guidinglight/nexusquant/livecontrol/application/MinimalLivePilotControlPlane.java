package com.guidinglight.nexusquant.livecontrol.application;

/** Root/operator single-purpose minimal pilot boundary。 */
public interface MinimalLivePilotControlPlane {

    MinimalLivePilotPermit prepare(MinimalLivePilotCommand command);
}
