package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Operator 明确选择的单次 exact binding 输入；digest 只能由服务端权威事实计算。 */
public record ExactPilotBindingCommand(
        UUID bindingId,
        UUID sessionId,
        UUID pilotScopeId,
        UUID observationSetId,
        ExactPilotBinding.OrderEnvelope order,
        Instant pilotWindowStart,
        Instant pilotWindowEnd,
        ExactPilotBinding.Correlation correlation,
        Instant bindingExpiresAt
) {
    public ExactPilotBindingCommand {
        Objects.requireNonNull(bindingId, "bindingId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(pilotScopeId, "pilotScopeId must not be null");
        Objects.requireNonNull(observationSetId, "observationSetId must not be null");
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(pilotWindowStart, "pilotWindowStart must not be null");
        Objects.requireNonNull(pilotWindowEnd, "pilotWindowEnd must not be null");
        Objects.requireNonNull(correlation, "correlation must not be null");
        Objects.requireNonNull(bindingExpiresAt, "bindingExpiresAt must not be null");
        if (!pilotWindowEnd.isAfter(pilotWindowStart) || bindingExpiresAt.isAfter(pilotWindowEnd)) {
            throw new IllegalArgumentException("binding window is invalid");
        }
    }
}
