package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Observation set 由 trusted collection 生成前的 explicit exact binding input；不接受 digest。 */
public record ExactPilotBindingDraft(
        UUID bindingId,
        ExactPilotBinding.OrderEnvelope order,
        Instant pilotWindowStart,
        Instant pilotWindowEnd,
        ExactPilotBinding.Correlation correlation,
        Instant bindingExpiresAt
) {
    public ExactPilotBindingDraft {
        Objects.requireNonNull(bindingId, "bindingId must not be null");
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(pilotWindowStart, "pilotWindowStart must not be null");
        Objects.requireNonNull(pilotWindowEnd, "pilotWindowEnd must not be null");
        Objects.requireNonNull(correlation, "correlation must not be null");
        Objects.requireNonNull(bindingExpiresAt, "bindingExpiresAt must not be null");
        if (!pilotWindowEnd.isAfter(pilotWindowStart) || bindingExpiresAt.isAfter(pilotWindowEnd)) {
            throw new IllegalArgumentException("exact binding draft window is invalid");
        }
    }

    public ExactPilotBindingCommand toCommand(
            UUID sessionId,
            UUID pilotScopeId,
            UUID observationSetId
    ) {
        return new ExactPilotBindingCommand(
                bindingId, sessionId, pilotScopeId, observationSetId, order,
                pilotWindowStart, pilotWindowEnd, correlation, bindingExpiresAt);
    }
}
