package com.guidinglight.nexusquant.livecontrol.application;

import java.util.Objects;

/** Single-purpose control surface command；所有 pilot/order values 必须由 operator 显式提供。 */
public record ExactPilotScopeControlCommand(
        PilotScopeMaterializationCommand materialization,
        PilotScopeApprovalCommand pilotApproval,
        ExactPilotBindingDraft binding,
        ExactPilotScopeAuthorizationCommand exactScopeApproval
) {
    public ExactPilotScopeControlCommand {
        Objects.requireNonNull(materialization, "materialization must not be null");
        Objects.requireNonNull(pilotApproval, "pilotApproval must not be null");
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(exactScopeApproval, "exactScopeApproval must not be null");
        if (!materialization.sessionId().equals(pilotApproval.sessionId())
                || !materialization.pilotScopeId().equals(pilotApproval.pilotScopeId())
                || !materialization.expectedPilotScopeHash().equals(pilotApproval.expectedPilotScopeHash())
                || !materialization.executionWindowStart().equals(binding.pilotWindowStart())
                || !materialization.executionWindowEnd().equals(binding.pilotWindowEnd())) {
            throw new IllegalArgumentException("pilot scope, approval and exact binding inputs disagree");
        }
    }
}
