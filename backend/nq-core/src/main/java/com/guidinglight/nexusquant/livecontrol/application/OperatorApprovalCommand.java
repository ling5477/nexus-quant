package com.guidinglight.nexusquant.livecontrol.application;

import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;

import java.time.Instant;
import java.util.UUID;

/** 人工审批写命令；审批 identity/role 只能来自独立认证上下文与实时 RBAC。 */
public record OperatorApprovalCommand(
        UUID approvalId,
        UUID sessionId,
        long expectedSessionVersion,
        String expectedScopeHash,
        OperatorApproval.Decision decision,
        String reason,
        Instant occurredAt,
        Instant expiresAt,
        String requestId,
        String traceId,
        String idempotencyKey,
        String commandPayloadHash
) {
}
