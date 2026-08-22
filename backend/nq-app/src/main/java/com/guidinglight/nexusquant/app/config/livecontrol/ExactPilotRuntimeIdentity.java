package com.guidinglight.nexusquant.app.config.livecontrol;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.util.Objects;

/** Exact binding 使用的 server-owned deployed release/runtime identity。 */
public record ExactPilotRuntimeIdentity(ExactPilotBinding.DeploymentIdentity deployment) {

    public ExactPilotRuntimeIdentity {
        Objects.requireNonNull(deployment, "deployment must not be null");
    }

    public static ExactPilotRuntimeIdentity from(
            ReadOnlyProviderObservationRuntimeIdentity runtime,
            String manifestSha256,
            String serverIdentity,
            String runtimeProfile
    ) {
        Objects.requireNonNull(runtime, "runtime must not be null");
        return new ExactPilotRuntimeIdentity(new ExactPilotBinding.DeploymentIdentity(
                runtime.sourceCommit(), runtime.releaseId(), manifestSha256,
                serverIdentity, runtimeProfile
        ));
    }
}
