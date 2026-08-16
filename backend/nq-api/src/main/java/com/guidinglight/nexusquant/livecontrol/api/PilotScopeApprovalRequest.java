package com.guidinglight.nexusquant.livecontrol.api;

import com.guidinglight.nexusquant.livecontrol.application.PilotScopeApprovalCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** 独立审批者的 exact pilot scope approval body；不接受 approver identity/role。 */
public record PilotScopeApprovalRequest(
        @NotNull UUID approvalId,
        @NotNull UUID pilotScopeId,
        @NotBlank @Size(min = 64, max = 64) String expectedPilotScopeHash,
        @NotBlank @Size(max = 1024) String reason,
        @NotNull Instant approvedAt,
        @NotNull Instant expiresAt
) {
    public PilotScopeApprovalCommand toCommand(UUID sessionId) {
        return new PilotScopeApprovalCommand(
                approvalId, sessionId, pilotScopeId, expectedPilotScopeHash, reason, approvedAt, expiresAt);
    }
}
