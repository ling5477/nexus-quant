package com.guidinglight.nexusquant.livecontrol.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stored-fact-only preflight result；不触发 provider、worker 或 exchange mutation。 */
public record PilotScopePreflightResult(
        boolean eligible,
        UUID pilotScopeId,
        UUID observationSetId,
        Instant decisionAt,
        List<Violation> violations,
        List<UUID> observationIds
) {
    public PilotScopePreflightResult {
        violations = List.copyOf(violations);
        observationIds = List.copyOf(observationIds);
    }

    public enum Violation {
        SCOPE_NOT_MATERIALIZED,
        APPROVAL_MISSING_OR_EXPIRED,
        OBSERVATION_SET_MISSING,
        INSTRUMENT_STALE,
        FEE_STALE,
        BALANCE_STALE,
        CLOCK_STALE,
        INSTRUMENT_NOT_LIVE,
        FEE_NOT_OBSERVED_PRIVATE,
        BALANCE_INSUFFICIENT,
        CLOCK_SKEW_EXCEEDED,
        SCOPE_FACT_MISMATCH
    }
}
