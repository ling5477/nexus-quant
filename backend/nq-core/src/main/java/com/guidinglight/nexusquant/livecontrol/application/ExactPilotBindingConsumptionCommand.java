package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.util.Objects;
import java.util.UUID;

/** 后续 attempt 占用 exact binding 的输入；仍不表示 PLACE 或 LIVE 获授权。 */
public record ExactPilotBindingConsumptionCommand(
        UUID sessionId,
        UUID bindingId,
        ExactPilotBinding.OrderEnvelope order,
        ExactPilotBinding.Correlation correlation
) {
    public ExactPilotBindingConsumptionCommand {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(bindingId, "bindingId must not be null");
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(correlation, "correlation must not be null");
    }
}
