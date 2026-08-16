package com.guidinglight.nexusquant.livecontrol.application;

import java.time.Instant;
import java.util.UUID;

/** exact pilot-scope approval command；approver identity 只能来自认证上下文。 */
public record PilotScopeApprovalCommand(
        UUID approvalId,
        UUID sessionId,
        UUID pilotScopeId,
        String expectedPilotScopeHash,
        String reason,
        Instant approvedAt,
        Instant expiresAt
) {
}
