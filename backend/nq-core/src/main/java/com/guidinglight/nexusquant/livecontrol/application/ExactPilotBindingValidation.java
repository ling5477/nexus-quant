package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Binding 的 stored/current-fact 验证结果；无论有效与否都不构成交易授权。 */
public record ExactPilotBindingValidation(
        UUID bindingId,
        ExactPilotBinding.Lifecycle lifecycle,
        Instant decisionAt,
        List<Violation> violations,
        boolean tradingAuthorized
) {
    public ExactPilotBindingValidation {
        Objects.requireNonNull(bindingId, "bindingId must not be null");
        Objects.requireNonNull(lifecycle, "lifecycle must not be null");
        Objects.requireNonNull(decisionAt, "decisionAt must not be null");
        violations = List.copyOf(violations);
        if (tradingAuthorized) {
            throw new IllegalArgumentException("exact binding validation cannot authorize trading");
        }
    }

    public enum Violation {
        BINDING_ALREADY_CONSUMED,
        BINDING_EXPIRED,
        BINDING_DIGEST_MISMATCH,
        AUTHORITATIVE_FACT_DRIFT
    }
}
