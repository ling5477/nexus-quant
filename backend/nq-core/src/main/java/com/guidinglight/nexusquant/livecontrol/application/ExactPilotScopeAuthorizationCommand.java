package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.ExactPilotBinding;

import java.time.Instant;
import java.util.Objects;

/** Root/operator adapter 提交的独立 exact scope authorization correlations；不包含 secret。 */
public record ExactPilotScopeAuthorizationCommand(
        ExactPilotBinding.Correlation creatorCorrelation,
        ExactPilotBinding.Correlation approverCorrelation,
        String approvalReason,
        Instant approvedAt,
        Instant expiresAt
) {
    public static final String REQUIRED_REASON = "APPROVED_FOR_EXACT_PILOT_MATERIALIZATION";

    public ExactPilotScopeAuthorizationCommand {
        Objects.requireNonNull(creatorCorrelation, "creatorCorrelation must not be null");
        Objects.requireNonNull(approverCorrelation, "approverCorrelation must not be null");
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!REQUIRED_REASON.equals(approvalReason) || !expiresAt.isAfter(approvedAt)
                || creatorCorrelation.idempotencyKey().equals(approverCorrelation.idempotencyKey())) {
            throw new IllegalArgumentException("exact scope approval contract is invalid");
        }
    }
}
